package dev.developershell.lecture;

import dev.developershell.campaign.CampaignEvent;
import dev.developershell.campaign.CampaignService;
import dev.developershell.campaign.CampaignTransition;
import dev.developershell.campaign.PlayerCampaignState;
import dev.developershell.item.AttendanceSheetItem;
import dev.developershell.item.InfiniteSlidesRemoteItem;
import dev.developershell.registry.ModItems;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Sole projection boundary for the persisted first-reward and Attendance Sheet entitlement.
 * Item stacks never decide progression; they only carry the current durable owner generation.
 */
public final class RewardService {
	private static final String VICTORY_KEY = "message.developers_hell.reward.victory";
	private static final String REMOTE_READY_KEY = "message.developers_hell.remote.ready";
	private static boolean sheetLifecycleRegistered;

	/**
	 * Interprets exactly one accepted, persisted manager victory. A fabricated, stale, replayed, or
	 * mismatched transition materializes nothing and presents nothing.
	 */
	public static Outcome reconcileVictory(
			ServerLevel level,
			UUID ownerUuid,
			UUID encounterUuid,
			CampaignTransition transition
	) {
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(ownerUuid, "ownerUuid");
		Objects.requireNonNull(encounterUuid, "encounterUuid");
		Objects.requireNonNull(transition, "transition");
		if (!level.getServer().isSameThread() || !isMatchingFirstVictory(ownerUuid, encounterUuid, transition)) {
			return Outcome.REJECTED;
		}

		Optional<PlayerCampaignState> persistedView = CampaignService.snapshot(level, ownerUuid);
		Optional<ServerPlayer> participant = LectureEncounterManager.participant(encounterUuid);
		if (persistedView.isEmpty()
				|| transition.nextState().filter(persistedView.get()::equals).isEmpty()
				|| participant.isEmpty()
				|| participant.get().level() != level
				|| !participant.get().getUUID().equals(ownerUuid)) {
			return Outcome.REJECTED;
		}

		PlayerCampaignState persisted = persistedView.get();
		AttendanceSheetItem.Binding binding = new AttendanceSheetItem.Binding(
				ownerUuid,
				persisted.sheetRecoverySequence()
		);
		ServerPlayer owner = participant.get();
		if (hasSheetRepresentation(level.getServer(), binding)
				|| hasRemoteRepresentation(level, owner, persisted.retryPos())) {
			return Outcome.ALREADY_PRESENT;
		}

		Projection sheet = materialize(
				level,
				owner,
				persisted.retryPos(),
				AttendanceSheetItem.bound(binding)
		);
		Projection remote = materialize(
				level,
				owner,
				persisted.retryPos(),
				new ItemStack(ModItems.INFINITE_SLIDES_REMOTE)
		);
		if (sheet == Projection.FAILED || remote == Projection.FAILED) {
			return Outcome.MATERIALIZATION_FAILED;
		}
		owner.sendSystemMessage(Component.translatable(VICTORY_KEY));
		return sheet == Projection.FALLBACK || remote == Projection.FALLBACK
				? Outcome.FALLBACK_ISSUED
				: Outcome.INVENTORY_ISSUED;
	}

	/**
	 * Advances the replay marker and restores only a genuinely missing Sheet at the saved Desk.
	 * Every non-Sheet campaign field is preserved by the reducer before materialization runs.
	 */
	public static Outcome recoverSheet(
			ServerPlayer owner,
			BlockPos clickedDesk,
			Direction clickedFacing
	) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(clickedDesk, "clickedDesk");
		Objects.requireNonNull(clickedFacing, "clickedFacing");
		ServerLevel level = owner.level();
		if (!level.getServer().isSameThread() || owner.isSpectator()) {
			return Outcome.REJECTED;
		}

		UUID ownerUuid = owner.getUUID();
		Optional<PlayerCampaignState> currentView = CampaignService.snapshot(level, ownerUuid);
		if (currentView.isEmpty()) {
			return Outcome.NOT_ENTITLED;
		}
		PlayerCampaignState current = currentView.get();
		boolean matchingAuthority = current.status() == PlayerCampaignState.LectureStatus.PASSED
				&& current.sheetEntitled()
				&& current.ownerUuid().equals(ownerUuid)
				&& current.deskDimension().equals(PlayerCampaignState.OVERWORLD_DIMENSION)
				&& level.dimension().equals(Level.OVERWORLD)
				&& current.deskPos().equals(clickedDesk)
				&& current.deskFacing() == clickedFacing;
		if (!matchingAuthority) {
			return Outcome.NOT_ENTITLED;
		}

		AttendanceSheetItem.Binding currentBinding = new AttendanceSheetItem.Binding(
				ownerUuid,
				current.sheetRecoverySequence()
		);
		if (hasSheetRepresentation(level.getServer(), currentBinding)) {
			return Outcome.ALREADY_PRESENT;
		}

		Projection[] projection = {Projection.FAILED};
		boolean[] matchingIntent = {false};
		CampaignTransition transition = CampaignService.apply(
				level,
				new CampaignEvent.RecoverSheet(ownerUuid, current.sheetRecoverySequence()),
				effect -> {
					if (effect instanceof CampaignTransition.EffectIntent.RecoverAttendanceSheet recovery
							&& recovery.ownerUuid().equals(ownerUuid)) {
						matchingIntent[0] = true;
						projection[0] = materialize(
								level,
								owner,
								current.retryPos(),
								AttendanceSheetItem.bound(new AttendanceSheetItem.Binding(
										ownerUuid,
										recovery.recoverySequence()
								))
						);
					}
				}
		);
		if (!transition.accepted() || !matchingIntent[0]) {
			return Outcome.REJECTED;
		}
		return switch (projection[0]) {
			case INVENTORY -> Outcome.SHEET_RECOVERED;
			case FALLBACK -> Outcome.SHEET_FALLBACK_RECOVERED;
			case FAILED -> Outcome.MATERIALIZATION_FAILED;
		};
	}

	/**
	 * Rebuilds Minecraft's transient cooldown projection from the durable logical-server deadline.
	 * One native cooldown group covers every matching Remote stack without duplicate packets.
	 *
	 * @return the clamped native duration applied, or {@code 0} when no projection is required
	 */
	public static int restoreRemoteCooldown(ServerPlayer owner) {
		Objects.requireNonNull(owner, "owner");
		ServerLevel level = owner.level();
		if (!level.getServer().isSameThread()) {
			return 0;
		}
		Optional<PlayerCampaignState> stateView = CampaignService.snapshot(level, owner.getUUID());
		Optional<ItemStack> remote = firstRemoteStack(owner);
		if (stateView.isEmpty() || !stateView.get().remoteIssued() || remote.isEmpty()) {
			return 0;
		}
		int remainingTicks = InfiniteSlidesRemoteItem.Cooldown.restoredOverlayTicks(
				stateView.get().remoteCooldownUntilGameTime(),
				level.getGameTime()
		);
		if (remainingTicks <= 0) {
			return 0;
		}
		owner.getCooldowns().addCooldown(remote.get(), remainingTicks);
		return remainingTicks;
	}

	/**
	 * Commits and presents one Remote-ready edge only when item presence and action-bar priority
	 * permit it. The reducer's exact deadline marker makes repeated ticks replay-safe.
	 */
	public static boolean reconcileRemoteReady(
			ServerPlayer owner,
			boolean criticalActionBarActive
	) {
		Objects.requireNonNull(owner, "owner");
		ServerLevel level = owner.level();
		if (!level.getServer().isSameThread()) {
			return false;
		}
		Optional<PlayerCampaignState> stateView = CampaignService.snapshot(level, owner.getUUID());
		if (stateView.isEmpty()) {
			return false;
		}
		PlayerCampaignState state = stateView.get();
		long observedGameTime = level.getGameTime();
		if (!InfiniteSlidesRemoteItem.Cooldown.readyNoticeDue(
				state.remoteCooldownUntilGameTime(),
				state.remoteReadyNoticeForDeadlineGameTime(),
				observedGameTime
		)) {
			return false;
		}
		if (readyCueDecision(firstRemoteStack(owner).isPresent(), criticalActionBarActive)
				!= ReadyCueDecision.PRESENT) {
			return false;
		}

		long deadlineGameTime = state.remoteCooldownUntilGameTime();
		boolean[] presented = {false};
		CampaignTransition transition = CampaignService.apply(
				level,
				new CampaignEvent.RemoteReadyNotice(
						owner.getUUID(),
						deadlineGameTime,
						observedGameTime
				),
				intent -> {
					if (intent instanceof CampaignTransition.EffectIntent.NotifyRemoteReady ready
							&& ready.ownerUuid().equals(owner.getUUID())
							&& ready.deadlineGameTime() == deadlineGameTime) {
						owner.sendOverlayMessage(Component.translatable(REMOTE_READY_KEY));
						owner.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.65F, 1.35F);
						presented[0] = true;
					}
				}
		);
		return transition.accepted() && presented[0];
	}

	/** Pure arbitration used by unit tests and the server-owned ready projection. */
	public static ReadyCueDecision readyCueDecision(
			boolean remotePresent,
			boolean criticalActionBarActive
	) {
		if (!remotePresent) {
			return ReadyCueDecision.ITEM_ABSENT;
		}
		return criticalActionBarActive ? ReadyCueDecision.DEFERRED : ReadyCueDecision.PRESENT;
	}

	/** Rejects stale disk copies and restores owner targeting when a current Sheet entity loads. */
	public static synchronized void registerSheetLifecycle() {
		if (sheetLifecycleRegistered) {
			return;
		}
		ServerEntityEvents.ALLOW_LOAD.register(RewardService::allowSheetLoad);
		ServerEntityEvents.ENTITY_LOAD.register(RewardService::onEntityLoad);
		sheetLifecycleRegistered = true;
	}

	private static boolean isMatchingFirstVictory(
			UUID ownerUuid,
			UUID encounterUuid,
			CampaignTransition transition
	) {
		if (!transition.accepted() || !transition.reason().equals("victory_accepted")) {
			return false;
		}
		Optional<PlayerCampaignState> nextView = transition.nextState();
		if (nextView.isEmpty()) {
			return false;
		}
		PlayerCampaignState next = nextView.get();
		if (!next.ownerUuid().equals(ownerUuid)
				|| next.status() != PlayerCampaignState.LectureStatus.PASSED
				|| next.activeEncounterRef() != null
				|| !next.sheetEntitled()
				|| !next.remoteIssued()) {
			return false;
		}
		long cleanupCount = transition.intents().stream().filter(effect ->
				effect instanceof CampaignTransition.EffectIntent.CleanupEncounter cleanup
						&& cleanup.ownerUuid().equals(ownerUuid)
						&& cleanup.encounterUuid().equals(encounterUuid)
						&& cleanup.reason().equals("victory")
		).count();
		long grantCount = transition.intents().stream().filter(effect ->
				effect instanceof CampaignTransition.EffectIntent.GrantFirstRewards grant
						&& grant.ownerUuid().equals(ownerUuid)
		).count();
		return cleanupCount == 1L && grantCount == 1L && transition.intents().size() == 2;
	}

	private static Projection materialize(
			ServerLevel level,
			ServerPlayer owner,
			BlockPos retryPos,
			ItemStack stack
	) {
		if (owner.getInventory().add(stack)) {
			return Projection.INVENTORY;
		}
		if (stack.isEmpty()
				|| !level.isLoaded(retryPos)
				|| !level.getWorldBorder().isWithinBounds(retryPos)) {
			return Projection.FAILED;
		}
		ItemEntity fallback = new ItemEntity(
				level,
				retryPos.getX() + 0.5D,
				retryPos.getY() + 0.25D,
				retryPos.getZ() + 0.5D,
				stack
		);
		fallback.setTarget(owner.getUUID());
		fallback.setDefaultPickUpDelay();
		return level.addFreshEntity(fallback) ? Projection.FALLBACK : Projection.FAILED;
	}

	private static boolean hasSheetRepresentation(
			MinecraftServer server,
			AttendanceSheetItem.Binding binding
	) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				if (AttendanceSheetItem.binding(player.getInventory().getItem(slot))
						.filter(binding::equals).isPresent()) {
					return true;
				}
			}
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity item
						&& !item.isRemoved()
						&& AttendanceSheetItem.binding(item.getItem()).filter(binding::equals).isPresent()) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hasRemoteRepresentation(
			ServerLevel level,
			ServerPlayer owner,
			BlockPos retryPos
	) {
		for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
			if (owner.getInventory().getItem(slot).getItem() == ModItems.INFINITE_SLIDES_REMOTE) {
				return true;
			}
		}
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof ItemEntity item
					&& !item.isRemoved()
					&& item.getItem().getItem() == ModItems.INFINITE_SLIDES_REMOTE
					&& item.blockPosition().distManhattan(retryPos) <= 2) {
				return true;
			}
		}
		return false;
	}

	private static Optional<ItemStack> firstRemoteStack(ServerPlayer owner) {
		for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
			ItemStack stack = owner.getInventory().getItem(slot);
			if (!stack.isEmpty() && stack.getItem() == ModItems.INFINITE_SLIDES_REMOTE) {
				return Optional.of(stack);
			}
		}
		return Optional.empty();
	}

	private static boolean allowSheetLoad(
			Entity entity,
			ServerLevel level,
			net.minecraft.world.entity.EntitySpawnReason spawnReason,
			boolean loadedFromDisk
	) {
		if (!(entity instanceof ItemEntity item)) {
			return true;
		}
		Optional<AttendanceSheetItem.Binding> binding = AttendanceSheetItem.binding(item.getItem());
		return binding.isEmpty() || isCurrentBinding(level, binding.get());
	}

	private static void onEntityLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof ItemEntity item)) {
			return;
		}
		AttendanceSheetItem.binding(item.getItem())
				.filter(binding -> isCurrentBinding(level, binding))
				.ifPresent(binding -> item.setTarget(binding.ownerUuid()));
	}

	private static boolean isCurrentBinding(
			ServerLevel level,
			AttendanceSheetItem.Binding binding
	) {
		return CampaignService.snapshot(level, binding.ownerUuid()).filter(state ->
				state.status() == PlayerCampaignState.LectureStatus.PASSED
						&& state.sheetEntitled()
						&& state.sheetRecoverySequence() == binding.recoverySequence()
		).isPresent();
	}

	public enum Outcome {
		REJECTED,
		NOT_ENTITLED,
		ALREADY_PRESENT,
		INVENTORY_ISSUED,
		FALLBACK_ISSUED,
		SHEET_RECOVERED,
		SHEET_FALLBACK_RECOVERED,
		MATERIALIZATION_FAILED
	}

	public enum ReadyCueDecision {
		ITEM_ABSENT,
		DEFERRED,
		PRESENT
	}

	private enum Projection {
		INVENTORY,
		FALLBACK,
		FAILED
	}

	private RewardService() {
	}
}
