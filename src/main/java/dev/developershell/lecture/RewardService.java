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
import java.util.function.Predicate;
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

		ServerPlayer owner = participant.get();
		Outcome outcome = reconcilePending(owner);
		owner.sendSystemMessage(Component.translatable(VICTORY_KEY));
		return outcome;
	}

	/**
	 * Independently retries only still-pending first-victory projections. A representation is
	 * observed by its complete owner/generation binding before its pending bit can clear.
	 */
	public static Outcome reconcilePending(ServerPlayer owner) {
		return reconcilePending(owner, ignored -> false);
	}

	/** Package-private fault seam used only by the generated GameTest source set. */
	static Outcome reconcilePending(ServerPlayer owner, Predicate<ItemStack> forcedFailure) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(forcedFailure, "forcedFailure");
		ServerLevel level = owner.level();
		if (!level.getServer().isSameThread() || owner.isSpectator()) {
			return Outcome.REJECTED;
		}
		Optional<PlayerCampaignState> stateView = CampaignService.snapshot(level, owner.getUUID());
		if (stateView.isEmpty()) {
			return Outcome.NOT_ENTITLED;
		}
		PlayerCampaignState state = stateView.get();
		if (state.status() != PlayerCampaignState.LectureStatus.PASSED
				|| !state.sheetEntitled()
				|| !state.remoteIssued()
				|| state.remoteProjectionUuid() == null) {
			return Outcome.NOT_ENTITLED;
		}
		if (!state.sheetProjectionPending() && !state.remoteProjectionPending()) {
			return Outcome.ALREADY_PRESENT;
		}

		ProjectionAttempt sheet = state.sheetProjectionPending()
				? reconcileSheetProjection(level, owner, state, forcedFailure)
				: ProjectionAttempt.SKIPPED;
		ProjectionAttempt remote = state.remoteProjectionPending()
				? reconcileRemoteProjection(level, owner, state, forcedFailure)
				: ProjectionAttempt.SKIPPED;
		if (sheet == ProjectionAttempt.FAILED || remote == ProjectionAttempt.FAILED) {
			return Outcome.MATERIALIZATION_FAILED;
		}
		if (sheet == ProjectionAttempt.FALLBACK || remote == ProjectionAttempt.FALLBACK) {
			return Outcome.FALLBACK_ISSUED;
		}
		if (sheet == ProjectionAttempt.INVENTORY || remote == ProjectionAttempt.INVENTORY) {
			return Outcome.INVENTORY_ISSUED;
		}
		return sheet == ProjectionAttempt.OBSERVED || remote == ProjectionAttempt.OBSERVED
				? Outcome.ALREADY_PRESENT
				: Outcome.REJECTED;
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
		if (stateView.isEmpty()
				|| !stateView.get().remoteIssued()
				|| stateView.get().remoteProjectionPending()
				|| stateView.get().remoteProjectionUuid() == null) {
			return 0;
		}
		Optional<ItemStack> remote = firstRemoteStack(owner, stateView.get());
		if (remote.isEmpty()) {
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
		if (!state.remoteIssued()
				|| state.remoteProjectionPending()
				|| state.remoteProjectionUuid() == null) {
			return false;
		}
		long observedGameTime = level.getGameTime();
		if (!InfiniteSlidesRemoteItem.Cooldown.readyNoticeDue(
				state.remoteCooldownUntilGameTime(),
				state.remoteReadyNoticeForDeadlineGameTime(),
				observedGameTime
		)) {
			return false;
		}
		if (readyCueDecision(firstRemoteStack(owner, state).isPresent(), criticalActionBarActive)
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
		ServerEntityEvents.ALLOW_LOAD.register(RewardService::allowRewardLoad);
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
				|| !next.remoteIssued()
				|| !next.sheetProjectionPending()
				|| !next.remoteProjectionPending()
				|| !encounterUuid.equals(next.remoteProjectionUuid())) {
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

	private static ProjectionAttempt reconcileSheetProjection(
			ServerLevel level,
			ServerPlayer owner,
			PlayerCampaignState state,
			Predicate<ItemStack> forcedFailure
	) {
		AttendanceSheetItem.Binding binding = new AttendanceSheetItem.Binding(
				state.ownerUuid(), state.sheetRecoverySequence()
		);
		if (hasSheetRepresentation(level.getServer(), binding)) {
			return confirmSheetProjection(level, state.ownerUuid(), binding.recoverySequence())
					? ProjectionAttempt.OBSERVED
					: ProjectionAttempt.FAILED;
		}
		Projection projection = materialize(
				level,
				owner,
				state.retryPos(),
				AttendanceSheetItem.bound(binding),
				forcedFailure
		);
		if (projection == Projection.FAILED
				|| !confirmSheetProjection(level, state.ownerUuid(), binding.recoverySequence())) {
			return ProjectionAttempt.FAILED;
		}
		return projection == Projection.INVENTORY
				? ProjectionAttempt.INVENTORY
				: ProjectionAttempt.FALLBACK;
	}

	private static ProjectionAttempt reconcileRemoteProjection(
			ServerLevel level,
			ServerPlayer owner,
			PlayerCampaignState state,
			Predicate<ItemStack> forcedFailure
	) {
		InfiniteSlidesRemoteItem.Binding binding = new InfiniteSlidesRemoteItem.Binding(
				state.ownerUuid(), state.remoteProjectionUuid()
		);
		if (hasRemoteRepresentation(level.getServer(), binding)) {
			return confirmRemoteProjection(level, binding)
					? ProjectionAttempt.OBSERVED
					: ProjectionAttempt.FAILED;
		}
		Projection projection = materialize(
				level,
				owner,
				state.retryPos(),
				InfiniteSlidesRemoteItem.bound(binding),
				forcedFailure
		);
		if (projection == Projection.FAILED || !confirmRemoteProjection(level, binding)) {
			return ProjectionAttempt.FAILED;
		}
		return projection == Projection.INVENTORY
				? ProjectionAttempt.INVENTORY
				: ProjectionAttempt.FALLBACK;
	}

	private static boolean confirmSheetProjection(
			ServerLevel level,
			UUID ownerUuid,
			long recoverySequence
	) {
		CampaignTransition transition = CampaignService.apply(
				level,
				new CampaignEvent.ConfirmSheetProjection(ownerUuid, recoverySequence),
				ignored -> {
				}
		);
		return transition.accepted()
				&& transition.nextState().filter(state -> !state.sheetProjectionPending()).isPresent();
	}

	private static boolean confirmRemoteProjection(
			ServerLevel level,
			InfiniteSlidesRemoteItem.Binding binding
	) {
		CampaignTransition transition = CampaignService.apply(
				level,
				new CampaignEvent.ConfirmRemoteProjection(binding.ownerUuid(), binding.projectionUuid()),
				ignored -> {
				}
		);
		return transition.accepted()
				&& transition.nextState().filter(state -> !state.remoteProjectionPending()).isPresent();
	}

	private static Projection materialize(
			ServerLevel level,
			ServerPlayer owner,
			BlockPos retryPos,
			ItemStack stack
	) {
		return materialize(level, owner, retryPos, stack, ignored -> false);
	}

	private static Projection materialize(
			ServerLevel level,
			ServerPlayer owner,
			BlockPos retryPos,
			ItemStack stack,
			Predicate<ItemStack> forcedFailure
	) {
		if (forcedFailure.test(stack)) {
			return Projection.FAILED;
		}
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
			MinecraftServer server,
			InfiniteSlidesRemoteItem.Binding binding
	) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				if (InfiniteSlidesRemoteItem.binding(player.getInventory().getItem(slot))
						.filter(binding::equals).isPresent()) {
					return true;
				}
			}
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity item
						&& !item.isRemoved()
						&& InfiniteSlidesRemoteItem.binding(item.getItem()).filter(binding::equals).isPresent()) {
					return true;
				}
			}
		}
		return false;
	}

	private static Optional<ItemStack> firstRemoteStack(
			ServerPlayer owner,
			PlayerCampaignState state
	) {
		InfiniteSlidesRemoteItem.Binding expected = new InfiniteSlidesRemoteItem.Binding(
				state.ownerUuid(), state.remoteProjectionUuid()
		);
		for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
			ItemStack stack = owner.getInventory().getItem(slot);
			if (InfiniteSlidesRemoteItem.binding(stack).filter(expected::equals).isPresent()) {
				return Optional.of(stack);
			}
		}
		return Optional.empty();
	}

	private static boolean allowRewardLoad(
			Entity entity,
			ServerLevel level,
			net.minecraft.world.entity.EntitySpawnReason spawnReason,
			boolean loadedFromDisk
	) {
		if (!(entity instanceof ItemEntity item)) {
			return true;
		}
		Optional<AttendanceSheetItem.Binding> sheetBinding = AttendanceSheetItem.binding(item.getItem());
		if (sheetBinding.isPresent()) {
			return isCurrentBinding(level, sheetBinding.get());
		}
		Optional<InfiniteSlidesRemoteItem.Binding> remoteBinding =
				InfiniteSlidesRemoteItem.binding(item.getItem());
		return remoteBinding.isEmpty() || isCurrentRemoteBinding(level, remoteBinding.get());
	}

	private static void onEntityLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof ItemEntity item)) {
			return;
		}
		AttendanceSheetItem.binding(item.getItem())
				.filter(binding -> isCurrentBinding(level, binding))
				.ifPresent(binding -> item.setTarget(binding.ownerUuid()));
		InfiniteSlidesRemoteItem.binding(item.getItem())
				.filter(binding -> isCurrentRemoteBinding(level, binding))
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

	private static boolean isCurrentRemoteBinding(
			ServerLevel level,
			InfiniteSlidesRemoteItem.Binding binding
	) {
		return CampaignService.snapshot(level, binding.ownerUuid()).filter(state ->
				state.status() == PlayerCampaignState.LectureStatus.PASSED
						&& state.remoteIssued()
						&& binding.projectionUuid().equals(state.remoteProjectionUuid())
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

	private enum ProjectionAttempt {
		SKIPPED,
		OBSERVED,
		INVENTORY,
		FALLBACK,
		FAILED
	}

	private RewardService() {
	}
}
