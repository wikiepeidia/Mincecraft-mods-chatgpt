package dev.developershell.lecture;

import dev.developershell.campaign.CampaignEvent;
import dev.developershell.campaign.CampaignSavedData;
import dev.developershell.campaign.CampaignService;
import dev.developershell.campaign.CampaignTransition;
import dev.developershell.campaign.PlayerCampaignState;
import dev.developershell.item.AttendanceSheetItem;
import dev.developershell.item.InfiniteSlidesRemoteItem;
import dev.developershell.registry.ModItems;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Sole projection boundary for the persisted first-reward and Attendance Sheet entitlement.
 * Item stacks never decide progression; they only carry the current durable owner generation.
 */
public final class RewardService {
	private static final String VICTORY_KEY = "message.developers_hell.reward.victory";
	private static final String REMOTE_READY_KEY = "message.developers_hell.remote.ready";
	private static final Map<ItemEntity, LiveTransfer> LIVE_TRANSFERS = new IdentityHashMap<>();
	private static final Map<UUID, PendingDimensionTransfer> PENDING_DIMENSION_TRANSFERS =
			new java.util.HashMap<>();
	private static final Map<ItemEntity, AdmittedDimensionTransfer> ADMITTED_DIMENSION_TRANSFERS =
			new IdentityHashMap<>();
	private static final Map<ItemEntity, Boolean> SUPPRESSED_DIMENSION_UNLOADS =
			new IdentityHashMap<>();
	private static final Map<Inventory, List<RejectedDeathDrop>> REJECTED_DEATH_DROPS =
			new IdentityHashMap<>();
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
		if (sheet == ProjectionAttempt.WAITING || remote == ProjectionAttempt.WAITING) {
			return Outcome.FALLBACK_PENDING;
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
		FallbackObservation observation = observeSheetRepresentation(level.getServer(), current, currentBinding);
		if (observation.presence() == RepresentationPresence.INVENTORY
				|| observation.presence() == RepresentationPresence.FALLBACK
				|| observation.presence() == RepresentationPresence.TRACKED_UNLOADED) {
			return Outcome.ALREADY_PRESENT;
		}
		if (observation.presence() == RepresentationPresence.INVALID) {
			return Outcome.REJECTED;
		}
		if (current.sheetFallback() != null) {
			CampaignEvent.SheetProjectionKey currentKey = new CampaignEvent.SheetProjectionKey(
					ownerUuid, current.sheetRecoverySequence());
			if (!clearMissingFallback(level, currentKey, current.sheetFallback())) {
				return Outcome.REJECTED;
			}
			current = CampaignService.snapshot(level, ownerUuid).orElseThrow();
		}

		Outcome[] projection = {Outcome.REJECTED};
		boolean[] matchingIntent = {false};
		CampaignTransition transition = CampaignService.apply(
				level,
				new CampaignEvent.RecoverSheet(ownerUuid, current.sheetRecoverySequence()),
				effect -> {
					if (effect instanceof CampaignTransition.EffectIntent.RecoverAttendanceSheet recovery
							&& recovery.ownerUuid().equals(ownerUuid)) {
						matchingIntent[0] = true;
						projection[0] = reconcilePending(owner);
					}
				}
		);
		if (!transition.accepted() || !matchingIntent[0]) {
			return Outcome.REJECTED;
		}
		return switch (projection[0]) {
			case INVENTORY_ISSUED -> Outcome.SHEET_RECOVERED;
			case FALLBACK_ISSUED -> Outcome.SHEET_FALLBACK_RECOVERED;
			case FALLBACK_PENDING -> Outcome.FALLBACK_PENDING;
			case MATERIALIZATION_FAILED -> Outcome.MATERIALIZATION_FAILED;
			default -> Outcome.REJECTED;
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
		ServerEntityEvents.ENTITY_UNLOAD.register(RewardService::onEntityUnload);
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
		CampaignEvent.SheetProjectionKey key = new CampaignEvent.SheetProjectionKey(
				state.ownerUuid(), state.sheetRecoverySequence());
		AttendanceSheetItem.Binding binding = new AttendanceSheetItem.Binding(
				state.ownerUuid(), state.sheetRecoverySequence()
		);
		FallbackObservation observed = observeSheetRepresentation(level.getServer(), state, binding);
		if (observed.presence() == RepresentationPresence.INVENTORY) {
			return ensureSheetConfirmed(level, binding) ? ProjectionAttempt.OBSERVED : ProjectionAttempt.FAILED;
		}
		if (observed.presence() == RepresentationPresence.FALLBACK) {
			ItemEntity fallback = observed.entity().orElseThrow();
			fallback.setTarget(state.ownerUuid());
			return markFallbackMaterialized(
					level, key, fallback, Objects.requireNonNull(state.sheetFallback()))
					&& ensureSheetConfirmed(level, binding)
					? ProjectionAttempt.OBSERVED
					: ProjectionAttempt.FAILED;
		}
		if (observed.presence() == RepresentationPresence.TRACKED_UNLOADED) {
			return ProjectionAttempt.WAITING;
		}
		if (observed.presence() == RepresentationPresence.INVALID) {
			return ProjectionAttempt.FAILED;
		}
		if (state.sheetFallback() != null) {
			if (!clearMissingFallback(level, key, state.sheetFallback())) {
				return ProjectionAttempt.FAILED;
			}
			state = CampaignService.snapshot(level, state.ownerUuid()).orElseThrow();
		}
		ItemStack stack = AttendanceSheetItem.bound(binding);
		if (forcedFailure.test(stack)) {
			return ProjectionAttempt.FAILED;
		}
		if (owner.getInventory().add(stack)) {
			return ensureSheetConfirmed(level, binding)
					? ProjectionAttempt.INVENTORY
					: ProjectionAttempt.FAILED;
		}
		return reserveFallback(level, owner, state, key, stack);
	}

	private static ProjectionAttempt reconcileRemoteProjection(
			ServerLevel level,
			ServerPlayer owner,
			PlayerCampaignState state,
			Predicate<ItemStack> forcedFailure
	) {
		CampaignEvent.RemoteProjectionKey key = new CampaignEvent.RemoteProjectionKey(
				state.ownerUuid(), state.remoteProjectionUuid());
		InfiniteSlidesRemoteItem.Binding binding = new InfiniteSlidesRemoteItem.Binding(
				state.ownerUuid(), state.remoteProjectionUuid()
		);
		FallbackObservation observed = observeRemoteRepresentation(level.getServer(), state, binding);
		if (observed.presence() == RepresentationPresence.INVENTORY) {
			return ensureRemoteConfirmed(level, binding) ? ProjectionAttempt.OBSERVED : ProjectionAttempt.FAILED;
		}
		if (observed.presence() == RepresentationPresence.FALLBACK) {
			ItemEntity fallback = observed.entity().orElseThrow();
			fallback.setTarget(state.ownerUuid());
			return markFallbackMaterialized(
					level, key, fallback, Objects.requireNonNull(state.remoteFallback()))
					&& ensureRemoteConfirmed(level, binding)
					? ProjectionAttempt.OBSERVED
					: ProjectionAttempt.FAILED;
		}
		if (observed.presence() == RepresentationPresence.TRACKED_UNLOADED) {
			return ProjectionAttempt.WAITING;
		}
		if (observed.presence() == RepresentationPresence.INVALID) {
			return ProjectionAttempt.FAILED;
		}
		if (state.legacyRemoteAdoptionPending()) {
			for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
				ItemStack candidate = owner.getInventory().getItem(slot);
				if (!InfiniteSlidesRemoteItem.isUnboundLegacy(candidate)) {
					continue;
				}
				if (!InfiniteSlidesRemoteItem.bindLegacy(candidate, binding)) {
					return ProjectionAttempt.FAILED;
				}
				owner.getInventory().setChanged();
				return ensureRemoteConfirmed(level, binding)
						? ProjectionAttempt.OBSERVED
						: ProjectionAttempt.FAILED;
			}

			CampaignTransition resolved = CampaignService.apply(
					level,
					new CampaignEvent.ResolveLegacyRemoteAbsence(
							state.ownerUuid(), state.remoteProjectionUuid()
					),
					ignored -> {
					}
			);
			if (!resolved.accepted()) {
				return ProjectionAttempt.FAILED;
			}
			state = resolved.nextState().orElseThrow();
		}
		if (state.remoteFallback() != null) {
			if (!clearMissingFallback(level, key, state.remoteFallback())) {
				return ProjectionAttempt.FAILED;
			}
			state = CampaignService.snapshot(level, state.ownerUuid()).orElseThrow();
		}
		ItemStack stack = InfiniteSlidesRemoteItem.bound(binding);
		if (forcedFailure.test(stack)) {
			return ProjectionAttempt.FAILED;
		}
		if (owner.getInventory().add(stack)) {
			return ensureRemoteConfirmed(level, binding)
					? ProjectionAttempt.INVENTORY
					: ProjectionAttempt.FAILED;
		}
		return reserveFallback(level, owner, state, key, stack);
	}

	private static boolean ensureSheetConfirmed(
			ServerLevel level,
			AttendanceSheetItem.Binding binding
	) {
		Optional<PlayerCampaignState> current = CampaignService.snapshot(level, binding.ownerUuid());
		if (current.filter(state -> state.sheetRecoverySequence() == binding.recoverySequence()
				&& !state.sheetProjectionPending()).isPresent()) {
			return true;
		}
		CampaignTransition transition = CampaignService.apply(
				level,
				new CampaignEvent.ConfirmSheetProjection(binding.ownerUuid(), binding.recoverySequence()),
				ignored -> {
				}
		);
		return transition.accepted()
				&& transition.nextState().filter(state -> !state.sheetProjectionPending()).isPresent();
	}

	private static boolean ensureRemoteConfirmed(
			ServerLevel level,
			InfiniteSlidesRemoteItem.Binding binding
	) {
		Optional<PlayerCampaignState> current = CampaignService.snapshot(level, binding.ownerUuid());
		if (current.filter(state -> binding.projectionUuid().equals(state.remoteProjectionUuid())
				&& !state.remoteProjectionPending()).isPresent()) {
			return true;
		}
		CampaignTransition transition = CampaignService.apply(
				level,
				new CampaignEvent.ConfirmRemoteProjection(binding.ownerUuid(), binding.projectionUuid()),
				ignored -> {
				}
		);
		return transition.accepted()
				&& transition.nextState().filter(state -> !state.remoteProjectionPending()).isPresent();
	}

	private static ProjectionAttempt reserveFallback(
			ServerLevel level,
			ServerPlayer owner,
			PlayerCampaignState state,
			CampaignEvent.RewardProjectionKey key,
			ItemStack stack
	) {
		UUID entityUuid = UUID.randomUUID();
		MaterializationResult[] result = {MaterializationResult.NOT_DISPATCHED};
		CampaignTransition reserved = CampaignService.apply(
				level,
				new CampaignEvent.RewardFallback(
						key,
						entityUuid,
						state.deskDimension(),
						state.retryPos(),
						CampaignEvent.RewardFallbackOperation.RESERVE
				),
				effect -> {
					if (effect instanceof CampaignTransition.EffectIntent.MaterializeRewardFallback intent
							&& intent.key().equals(key)
							&& intent.fallback().entityUuid().equals(entityUuid)) {
						result[0] = materializeReservedFallback(
								level, owner, intent.key(), intent.fallback(), stack);
					}
				}
		);
		if (!reserved.accepted() || result[0] == MaterializationResult.NOT_DISPATCHED) {
			return ProjectionAttempt.FAILED;
		}
		if (result[0] == MaterializationResult.WAITING) {
			return ProjectionAttempt.WAITING;
		}
		if (result[0] == MaterializationResult.FAILED) {
			return ProjectionAttempt.FAILED;
		}
		return ensureProjectionConfirmed(level, key)
				? ProjectionAttempt.FALLBACK
				: ProjectionAttempt.FAILED;
	}

	private static MaterializationResult materializeReservedFallback(
			ServerLevel sourceLevel,
			ServerPlayer owner,
			CampaignEvent.RewardProjectionKey key,
			PlayerCampaignState.RewardFallbackRef fallbackRef,
			ItemStack stack
	) {
		Entity existing = findEntity(sourceLevel.getServer(), fallbackRef.entityUuid());
		if (existing != null) {
			if (!(existing instanceof ItemEntity item)
					|| item.isRemoved()
					|| !matchesProjection(item.getItem(), key)
					|| !loadContextAllowed(fallbackRef, item)) {
				return MaterializationResult.FAILED;
			}
			item.setTarget(owner.getUUID());
			return markFallbackMaterialized(sourceLevel, key, item, fallbackRef)
					? MaterializationResult.OBSERVED
					: MaterializationResult.FAILED;
		}
		ServerLevel targetLevel = levelForDimension(sourceLevel.getServer(), fallbackRef.dimension());
		if (targetLevel == null || !targetLevel.isLoaded(fallbackRef.position())) {
			return MaterializationResult.WAITING;
		}
		if (stack.isEmpty() || !targetLevel.getWorldBorder().isWithinBounds(fallbackRef.position())) {
			return MaterializationResult.FAILED;
		}
		ItemEntity fallback = new ItemEntity(
				targetLevel,
				fallbackRef.position().getX() + 0.5D,
				fallbackRef.position().getY() + 0.25D,
				fallbackRef.position().getZ() + 0.5D,
				stack
		);
		fallback.setUUID(fallbackRef.entityUuid());
		fallback.setTarget(owner.getUUID());
		fallback.setDefaultPickUpDelay();
		if (!targetLevel.addFreshEntity(fallback)) {
			return MaterializationResult.FAILED;
		}
		return markFallbackMaterialized(sourceLevel, key, fallback, fallbackRef)
				? MaterializationResult.SPAWNED
				: MaterializationResult.FAILED;
	}

	private static boolean clearMissingFallback(
			ServerLevel level,
			CampaignEvent.RewardProjectionKey key,
			PlayerCampaignState.RewardFallbackRef fallback
	) {
		CampaignTransition lost = CampaignService.apply(
				level,
				new CampaignEvent.RewardFallback(
						key,
						fallback.entityUuid(),
						fallback.dimension(),
						fallback.position(),
						CampaignEvent.RewardFallbackOperation.LOST,
						fallback
				),
				ignored -> {
				}
		);
		return lost.accepted() && currentFallback(level, key).isEmpty();
	}

	private static boolean markFallbackMaterialized(
			ServerLevel level,
			CampaignEvent.RewardProjectionKey key,
			ItemEntity item,
			PlayerCampaignState.RewardFallbackRef expectedPrior
	) {
		ServerLevel itemLevel = (ServerLevel) item.level();
		PlayerCampaignState.RewardFallbackRef observed = expectedPrior.at(
				dimensionId(itemLevel), item.blockPosition(), true);
		CampaignTransition materialized = CampaignService.apply(
				level,
				new CampaignEvent.RewardFallback(
						key,
						item.getUUID(),
						observed.dimension(),
						observed.position(),
						CampaignEvent.RewardFallbackOperation.MATERIALIZED,
						expectedPrior
				),
				ignored -> {
				}
		);
		if (materialized.accepted()) {
			return true;
		}
		return currentFallback(level, key).filter(observed::equals).isPresent();
	}

	private static boolean ensureProjectionConfirmed(
			ServerLevel level,
			CampaignEvent.RewardProjectionKey key
	) {
		if (key instanceof CampaignEvent.SheetProjectionKey sheet) {
			return ensureSheetConfirmed(
					level,
					new AttendanceSheetItem.Binding(sheet.ownerUuid(), sheet.recoverySequence())
			);
		}
		CampaignEvent.RemoteProjectionKey remote = (CampaignEvent.RemoteProjectionKey) key;
		return ensureRemoteConfirmed(
				level,
				new InfiniteSlidesRemoteItem.Binding(remote.ownerUuid(), remote.projectionUuid())
		);
	}

	private static Optional<PlayerCampaignState.RewardFallbackRef> currentFallback(
			ServerLevel level,
			CampaignEvent.RewardProjectionKey key
	) {
		return CampaignService.snapshot(level, key.ownerUuid()).map(state ->
				key instanceof CampaignEvent.SheetProjectionKey ? state.sheetFallback() : state.remoteFallback()
		);
	}

	private static FallbackObservation observeSheetRepresentation(
			MinecraftServer server,
			PlayerCampaignState state,
			AttendanceSheetItem.Binding binding
	) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.getUUID().equals(binding.ownerUuid())) {
				continue;
			}
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				if (AttendanceSheetItem.binding(player.getInventory().getItem(slot))
						.filter(binding::equals).isPresent()) {
					return FallbackObservation.inventory();
				}
			}
		}
		return observeFallback(server, state.sheetFallback(),
				stack -> AttendanceSheetItem.binding(stack).filter(binding::equals).isPresent());
	}

	private static FallbackObservation observeRemoteRepresentation(
			MinecraftServer server,
			PlayerCampaignState state,
			InfiniteSlidesRemoteItem.Binding binding
	) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.getUUID().equals(binding.ownerUuid())) {
				continue;
			}
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				if (InfiniteSlidesRemoteItem.binding(player.getInventory().getItem(slot))
						.filter(binding::equals).isPresent()) {
					return FallbackObservation.inventory();
				}
			}
		}
		return observeFallback(server, state.remoteFallback(),
				stack -> InfiniteSlidesRemoteItem.binding(stack).filter(binding::equals).isPresent());
	}

	private static FallbackObservation observeFallback(
			MinecraftServer server,
			PlayerCampaignState.RewardFallbackRef fallbackRef,
			Predicate<ItemStack> bindingMatcher
	) {
		if (fallbackRef == null) {
			return FallbackObservation.missing();
		}
		Entity existing = findEntity(server, fallbackRef.entityUuid());
		if (existing != null) {
			if (existing instanceof ItemEntity item
					&& !item.isRemoved()
					&& bindingMatcher.test(item.getItem())
					&& loadContextAllowed(fallbackRef, item)) {
				return FallbackObservation.fallback(item);
			}
			return FallbackObservation.invalid();
		}
		ServerLevel targetLevel = levelForDimension(server, fallbackRef.dimension());
		return targetLevel == null || !targetLevel.isLoaded(fallbackRef.position())
				? FallbackObservation.trackedUnloaded()
				: FallbackObservation.missing();
	}

	private static Entity findEntity(MinecraftServer server, UUID entityUuid) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(entityUuid);
			if (entity != null) {
				return entity;
			}
		}
		return null;
	}

	private static ServerLevel levelForDimension(MinecraftServer server, String dimension) {
		Identifier identifier = Identifier.tryParse(dimension);
		if (identifier == null) {
			return null;
		}
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, identifier);
		return server.getLevel(key);
	}

	private static String dimensionId(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

	private static boolean loadContextAllowed(
			PlayerCampaignState.RewardFallbackRef fallbackRef,
			ItemEntity item
	) {
		if (fallbackRef.materialized()) {
			return true;
		}
		return item.level() instanceof ServerLevel level
				&& dimensionId(level).equals(fallbackRef.dimension())
				&& ChunkPos.containing(item.blockPosition()).equals(ChunkPos.containing(fallbackRef.position()));
	}

	/** Package-private GameTest seam for the otherwise event-owned source-unload half of travel. */
	static synchronized void beginDimensionTransferForGameTest(ItemEntity item) {
		Objects.requireNonNull(item, "item");
		if (!(item.level() instanceof ServerLevel level) || level.getEntity(item.getUUID()) != item) {
			throw new IllegalArgumentException("GameTest transfer source must be the tracked server entity");
		}
		CampaignSavedData.RewardFallbackAuthority authority = CampaignSavedData.get(level)
				.rewardFallbackByEntityUuid(item.getUUID())
				.orElseThrow(() -> new IllegalArgumentException("GameTest transfer source lacks durable authority"));
		beginDimensionTransfer(level, authority, item);
	}

	/** Package-private GameTest seam suppressing only the staged source's synthetic discard callback. */
	static synchronized void suppressNextDimensionUnloadForGameTest(ItemEntity item) {
		Objects.requireNonNull(item, "item");
		if (!(item.level() instanceof ServerLevel level) || level.getEntity(item.getUUID()) != item) {
			throw new IllegalArgumentException("GameTest unload suppression requires the tracked source");
		}
		SUPPRESSED_DIMENSION_UNLOADS.put(item, Boolean.TRUE);
	}

	/** Package-private read-only GameTest seam proving a rejected handoff leaves no pending ticket. */
	static synchronized boolean hasPendingDimensionTransferForGameTest(UUID entityUuid) {
		return PENDING_DIMENSION_TRANSFERS.containsKey(
				Objects.requireNonNull(entityUuid, "entityUuid"));
	}

	/** Disk copies must still belong to the one durable dimension/chunk authority. */
	private static boolean durableChunkContextAllowed(
			PlayerCampaignState.RewardFallbackRef fallbackRef,
			ItemEntity item
	) {
		return item.level() instanceof ServerLevel level
				&& dimensionId(level).equals(fallbackRef.dimension())
				&& ChunkPos.containing(item.blockPosition())
						.equals(ChunkPos.containing(fallbackRef.position()));
	}

	private static boolean matchesProjection(
			ItemStack stack,
			CampaignEvent.RewardProjectionKey key
	) {
		if (key instanceof CampaignEvent.SheetProjectionKey sheet) {
			return AttendanceSheetItem.binding(stack).filter(binding ->
					binding.ownerUuid().equals(sheet.ownerUuid())
							&& binding.recoverySequence() == sheet.recoverySequence()).isPresent();
		}
		CampaignEvent.RemoteProjectionKey remote = (CampaignEvent.RemoteProjectionKey) key;
		return InfiniteSlidesRemoteItem.binding(stack).filter(binding ->
				binding.ownerUuid().equals(remote.ownerUuid())
						&& binding.projectionUuid().equals(remote.projectionUuid())).isPresent();
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

	private static synchronized boolean allowRewardLoad(
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
			AttendanceSheetItem.Binding binding = sheetBinding.get();
			CampaignEvent.SheetProjectionKey key = new CampaignEvent.SheetProjectionKey(
					binding.ownerUuid(), binding.recoverySequence());
			Optional<PlayerCampaignState.RewardFallbackRef> tracked =
					trackedSheetFallback(level, binding, item);
			if (tracked.isPresent()) {
				return allowTrackedRewardLoad(
						level, key, tracked.get(), item, spawnReason, loadedFromDisk);
			}
			return !loadedFromDisk && spawnReason == null
					&& transferAuthoritativeLiveDrop(level, key, item);
		}
		Optional<InfiniteSlidesRemoteItem.Binding> remoteBinding =
				InfiniteSlidesRemoteItem.binding(item.getItem());
		if (remoteBinding.isEmpty()) {
			return true;
		}
		InfiniteSlidesRemoteItem.Binding binding = remoteBinding.get();
		CampaignEvent.RemoteProjectionKey key = new CampaignEvent.RemoteProjectionKey(
				binding.ownerUuid(), binding.projectionUuid());
		Optional<PlayerCampaignState.RewardFallbackRef> tracked =
				trackedRemoteFallback(level, binding, item);
		if (tracked.isPresent()) {
			return allowTrackedRewardLoad(
					level, key, tracked.get(), item, spawnReason, loadedFromDisk);
		}
		return !loadedFromDisk && spawnReason == null
				&& transferAuthoritativeLiveDrop(level, key, item);
	}

	private static boolean allowTrackedRewardLoad(
			ServerLevel level,
			CampaignEvent.RewardProjectionKey key,
			PlayerCampaignState.RewardFallbackRef current,
			ItemEntity item,
			net.minecraft.world.entity.EntitySpawnReason spawnReason,
			boolean loadedFromDisk
	) {
		if (loadedFromDisk) {
			Entity liveWithDurableUuid = findEntity(level.getServer(), current.entityUuid());
			return spawnReason == net.minecraft.world.entity.EntitySpawnReason.LOAD
					&& durableChunkContextAllowed(current, item)
					&& (liveWithDurableUuid == null || liveWithDurableUuid == item);
		}
		if (spawnReason == net.minecraft.world.entity.EntitySpawnReason.DIMENSION_TRAVEL) {
			return admitDimensionTransfer(level, key, current, item);
		}
		return spawnReason == null && durableChunkContextAllowed(current, item);
	}

	private static boolean admitDimensionTransfer(
			ServerLevel targetLevel,
			CampaignEvent.RewardProjectionKey key,
			PlayerCampaignState.RewardFallbackRef current,
			ItemEntity candidate
	) {
		AdmittedDimensionTransfer existing = ADMITTED_DIMENSION_TRANSFERS.get(candidate);
		if (existing != null) {
			return existing.pending().key().equals(key)
					&& existing.targetRef().equals(current);
		}
		PendingDimensionTransfer pending = PENDING_DIMENSION_TRANSFERS.get(candidate.getUUID());
		if (pending == null
				|| !pending.key().equals(key)
				|| !pending.sourceRef().equals(current)
				|| pending.sourceLevel() == targetLevel
				|| !matchesProjection(candidate.getItem(), key)) {
			return false;
		}
		PlayerCampaignState.RewardFallbackRef targetRef = current.at(
				dimensionId(targetLevel), candidate.blockPosition(), true);
		CampaignTransition relocated = CampaignService.apply(
				targetLevel,
				new CampaignEvent.RewardFallback(
						key,
						targetRef.entityUuid(),
						targetRef.dimension(),
						targetRef.position(),
						CampaignEvent.RewardFallbackOperation.RELOCATED,
						current
				),
				ignored -> {
				}
		);
		if (!relocated.accepted()
				|| currentFallback(targetLevel, key).filter(targetRef::equals).isEmpty()) {
			PENDING_DIMENSION_TRANSFERS.remove(candidate.getUUID(), pending);
			return false;
		}
		ADMITTED_DIMENSION_TRANSFERS.put(
				candidate, new AdmittedDimensionTransfer(pending, targetRef));
		return true;
	}

	private static boolean transferAuthoritativeLiveDrop(
			ServerLevel level,
			CampaignEvent.RewardProjectionKey key,
			ItemEntity item
	) {
		Optional<PlayerCampaignState> stateView = CampaignService.snapshot(level, key.ownerUuid());
		if (stateView.isEmpty()) {
			return false;
		}
		PlayerCampaignState state = stateView.get();
		boolean currentProjection = key instanceof CampaignEvent.SheetProjectionKey sheet
				? state.status() == PlayerCampaignState.LectureStatus.PASSED
						&& state.sheetEntitled()
						&& !state.sheetProjectionPending()
						&& state.sheetFallback() == null
						&& sheet.recoverySequence() == state.sheetRecoverySequence()
				: state.status() == PlayerCampaignState.LectureStatus.PASSED
						&& state.remoteIssued()
						&& !state.remoteProjectionPending()
						&& !state.legacyRemoteAdoptionPending()
						&& state.remoteFallback() == null
						&& ((CampaignEvent.RemoteProjectionKey) key).projectionUuid()
								.equals(state.remoteProjectionUuid());
		if (!currentProjection) {
			return false;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(key.ownerUuid());
		if (owner == null || owner.level() != level) {
			return false;
		}
		Inventory inventory = owner.getInventory();
		int identitySlot = inventoryIdentitySlot(owner, item.getItem());
		int selectedSlot = inventory.getSelectedSlot();
		boolean ownerQDrop = item.getOwner() == owner && inventory.getItem(selectedSlot).isEmpty();
		boolean ownerDeathDrop = item.getOwner() == null && owner.isDeadOrDying() && identitySlot >= 0;
		if (!ownerQDrop && !ownerDeathDrop) {
			return false;
		}

		PlayerCampaignState.RewardFallbackRef transferred = new PlayerCampaignState.RewardFallbackRef(
				item.getUUID(), dimensionId(level), item.blockPosition(), true);
		CampaignTransition transition = CampaignService.apply(
				level,
				new CampaignEvent.RewardFallback(
						key,
						transferred.entityUuid(),
						transferred.dimension(),
						transferred.position(),
						CampaignEvent.RewardFallbackOperation.TRANSFERRED
				),
				ignored -> {
				}
		);
		if (!transition.accepted()
				|| currentFallback(level, key).filter(transferred::equals).isEmpty()) {
			return false;
		}
		LIVE_TRANSFERS.put(item, new LiveTransfer(
				key,
				transferred,
				inventory,
				ownerDeathDrop ? identitySlot : selectedSlot,
				ownerDeathDrop
		));
		return true;
	}

	private static int inventoryIdentitySlot(ServerPlayer owner, ItemStack expected) {
		for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
			if (owner.getInventory().getItem(slot) == expected) {
				return slot;
			}
		}
		return -1;
	}

	/** Synchronous compensation invoked after Minecraft finishes its private entity-add transaction. */
	public static synchronized void onEntityAddResult(Entity entity, boolean added) {
		if (!(entity instanceof ItemEntity item)) {
			return;
		}
		AdmittedDimensionTransfer dimensionTransfer = ADMITTED_DIMENSION_TRANSFERS.remove(item);
		if (!added) {
			if (dimensionTransfer != null) {
				rollbackRejectedDimensionTransfer(dimensionTransfer, null);
			}
			else {
				compensateRejectedPendingDimensionTransfer(item);
			}
		}
		else if (added && dimensionTransfer != null) {
			PENDING_DIMENSION_TRANSFERS.remove(
					item.getUUID(), dimensionTransfer.pending());
		}
		LiveTransfer transfer = LIVE_TRANSFERS.remove(item);
		if (added || transfer == null || !(item.level() instanceof ServerLevel level)) {
			return;
		}
		CampaignTransition rollback = CampaignService.apply(
				level,
				new CampaignEvent.RewardFallback(
						transfer.key(),
						transfer.ref().entityUuid(),
						transfer.ref().dimension(),
						transfer.ref().position(),
						CampaignEvent.RewardFallbackOperation.CLEARED,
						transfer.ref()
				),
				ignored -> {
				}
		);
		if (!rollback.accepted() || currentFallback(level, transfer.key()).isPresent()) {
			return;
		}
		if (transfer.deathDrop()) {
			REJECTED_DEATH_DROPS.computeIfAbsent(
					transfer.inventory(), ignored -> new ArrayList<>())
					.add(new RejectedDeathDrop(transfer.restoreSlot(), item.getItem()));
		}
		else {
			restoreRejectedReward(transfer.inventory(), transfer.restoreSlot(), item.getItem());
		}
	}

	private static boolean compensateRejectedPendingDimensionTransfer(ItemEntity candidate) {
		PendingDimensionTransfer pending = PENDING_DIMENSION_TRANSFERS.get(candidate.getUUID());
		if (pending == null
				|| !(candidate.level() instanceof ServerLevel targetLevel)
				|| targetLevel.getServer() != pending.sourceLevel().getServer()
				|| targetLevel == pending.sourceLevel()
				|| pending.stack().isEmpty()
				|| !matchesProjection(candidate.getItem(), pending.key())
				|| currentFallback(pending.sourceLevel(), pending.key())
						.filter(pending.sourceRef()::equals).isEmpty()) {
			return false;
		}
		PENDING_DIMENSION_TRANSFERS.remove(candidate.getUUID(), pending);
		return restoreDimensionTransferSource(pending);
	}

	private static boolean rollbackRejectedDimensionTransfer(
			AdmittedDimensionTransfer admitted,
			ItemEntity trackedTarget
	) {
		PendingDimensionTransfer pending = admitted.pending();
		if (trackedTarget != null && !trackedTarget.isRemoved()) {
			SUPPRESSED_DIMENSION_UNLOADS.put(trackedTarget, Boolean.TRUE);
			trackedTarget.discard();
		}
		PENDING_DIMENSION_TRANSFERS.remove(pending.sourceRef().entityUuid(), pending);
		CampaignTransition rollback = CampaignService.apply(
				pending.sourceLevel(),
				new CampaignEvent.RewardFallback(
						pending.key(),
						pending.sourceRef().entityUuid(),
						pending.sourceRef().dimension(),
						pending.sourceRef().position(),
						CampaignEvent.RewardFallbackOperation.RELOCATED,
						admitted.targetRef()
				),
				ignored -> {
				}
		);
		if (!rollback.accepted()
				|| currentFallback(pending.sourceLevel(), pending.key())
						.filter(pending.sourceRef()::equals).isEmpty()
				|| pending.stack().isEmpty()) {
			return false;
		}
		return restoreDimensionTransferSource(pending);
	}

	private static boolean restoreDimensionTransferSource(PendingDimensionTransfer pending) {
		PlayerCampaignState.RewardFallbackRef sourceRef = pending.sourceRef();
		Entity existing = findEntity(pending.sourceLevel().getServer(), sourceRef.entityUuid());
		if (existing != null) {
			return existing instanceof ItemEntity item
					&& !item.isRemoved()
					&& item.level() == pending.sourceLevel()
					&& matchesProjection(item.getItem(), pending.key())
					&& durableChunkContextAllowed(sourceRef, item);
		}
		ItemEntity restored = new ItemEntity(
				pending.sourceLevel(),
				sourceRef.position().getX() + 0.5D,
				sourceRef.position().getY() + 0.25D,
				sourceRef.position().getZ() + 0.5D,
				pending.stack().copy()
		);
		restored.setUUID(sourceRef.entityUuid());
		restored.setTarget(pending.key().ownerUuid());
		restored.setDefaultPickUpDelay();
		return pending.sourceLevel().addFreshEntity(restored);
	}

	/** Restores exact death-drop stacks after vanilla has finished clearing every source slot. */
	public static synchronized void onDeathInventoryDropComplete(Inventory inventory) {
		Objects.requireNonNull(inventory, "inventory");
		List<RejectedDeathDrop> rejected = REJECTED_DEATH_DROPS.remove(inventory);
		if (rejected == null) {
			return;
		}
		for (RejectedDeathDrop drop : rejected) {
			restoreRejectedReward(inventory, drop.restoreSlot(), drop.stack());
		}
	}

	private static void restoreRejectedReward(Inventory inventory, int preferredSlot, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		int target = preferredSlot >= 0
				&& preferredSlot < inventory.getContainerSize()
				&& inventory.getItem(preferredSlot).isEmpty()
				? preferredSlot
				: inventory.getFreeSlot();
		if (target < 0) {
			throw new IllegalStateException("Rejected reward add lost its reserved inventory slot");
		}
		inventory.setItem(target, stack);
	}

	private static synchronized void onEntityLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof ItemEntity item) || level.getEntity(item.getUUID()) != item) {
			return;
		}
		LIVE_TRANSFERS.remove(item);
		AdmittedDimensionTransfer dimensionTransfer = ADMITTED_DIMENSION_TRANSFERS.remove(item);
		if (dimensionTransfer != null) {
			if (!markFallbackMaterialized(
					level,
					dimensionTransfer.pending().key(),
					item,
					dimensionTransfer.targetRef())) {
				rollbackRejectedDimensionTransfer(dimensionTransfer, item);
				return;
			}
			PENDING_DIMENSION_TRANSFERS.remove(
					item.getUUID(), dimensionTransfer.pending());
			item.setTarget(dimensionTransfer.pending().key().ownerUuid());
			if (dimensionTransfer.pending().key() instanceof CampaignEvent.SheetProjectionKey sheet) {
				ensureSheetConfirmed(level, new AttendanceSheetItem.Binding(
						sheet.ownerUuid(), sheet.recoverySequence()));
			}
			else {
				CampaignEvent.RemoteProjectionKey remote =
						(CampaignEvent.RemoteProjectionKey) dimensionTransfer.pending().key();
				ensureRemoteConfirmed(level, new InfiniteSlidesRemoteItem.Binding(
						remote.ownerUuid(), remote.projectionUuid()));
			}
			return;
		}
		AttendanceSheetItem.binding(item.getItem()).ifPresent(binding ->
				trackedSheetFallback(level, binding, item).ifPresent(ref -> {
					CampaignEvent.SheetProjectionKey key = new CampaignEvent.SheetProjectionKey(
							binding.ownerUuid(), binding.recoverySequence());
					if (markFallbackMaterialized(level, key, item, ref)) {
						item.setTarget(binding.ownerUuid());
						ensureSheetConfirmed(level, binding);
					}
				}));
		InfiniteSlidesRemoteItem.binding(item.getItem()).ifPresent(binding ->
				trackedRemoteFallback(level, binding, item).ifPresent(ref -> {
					CampaignEvent.RemoteProjectionKey key = new CampaignEvent.RemoteProjectionKey(
							binding.ownerUuid(), binding.projectionUuid());
					if (markFallbackMaterialized(level, key, item, ref)) {
						item.setTarget(binding.ownerUuid());
						ensureRemoteConfirmed(level, binding);
					}
				}));
	}

	private static void onEntityUnload(Entity entity, ServerLevel level) {
		if (!(entity instanceof ItemEntity item)) {
			return;
		}
		if (SUPPRESSED_DIMENSION_UNLOADS.remove(item) != null
				|| level.getEntity(item.getUUID()) != item) {
			return;
		}
		CampaignSavedData.get(level).rewardFallbackByEntityUuid(item.getUUID())
				.filter(authority -> authority.ref().dimension().equals(dimensionId(level)))
				.ifPresent(authority -> {
					if (item.getRemovalReason() == Entity.RemovalReason.CHANGED_DIMENSION) {
						beginDimensionTransfer(level, authority, item);
					}
					else {
						recordFallbackUnload(level, authority.key(), authority.ref(), item);
					}
				});
	}

	private static void beginDimensionTransfer(
			ServerLevel sourceLevel,
			CampaignSavedData.RewardFallbackAuthority authority,
			ItemEntity item
	) {
		PlayerCampaignState.RewardFallbackRef sourceRef = authority.ref().at(
				dimensionId(sourceLevel), item.blockPosition(), true);
		CampaignTransition relocated = CampaignService.apply(
				sourceLevel,
				new CampaignEvent.RewardFallback(
						authority.key(),
						sourceRef.entityUuid(),
						sourceRef.dimension(),
						sourceRef.position(),
						CampaignEvent.RewardFallbackOperation.RELOCATED,
						authority.ref()
				),
				ignored -> {
				}
		);
		if (!relocated.accepted()
				&& currentFallback(sourceLevel, authority.key()).filter(sourceRef::equals).isEmpty()) {
			return;
		}
		PENDING_DIMENSION_TRANSFERS.put(
				item.getUUID(),
				new PendingDimensionTransfer(
						authority.key(), sourceRef, sourceLevel, item.getItem().copy()));
	}

	private static void recordFallbackUnload(
			ServerLevel level,
			CampaignEvent.RewardProjectionKey key,
			PlayerCampaignState.RewardFallbackRef expectedPrior,
			ItemEntity item
	) {
		Entity.RemovalReason reason = item.getRemovalReason();
		CampaignEvent.RewardFallbackOperation operation = reason != null && reason.shouldDestroy()
				? CampaignEvent.RewardFallbackOperation.LOST
				: CampaignEvent.RewardFallbackOperation.RELOCATED;
		CampaignService.apply(
				level,
				new CampaignEvent.RewardFallback(
						key,
						item.getUUID(),
						dimensionId(level),
						item.blockPosition(),
						operation,
						expectedPrior
				),
				ignored -> {
				}
		);
	}

	private static Optional<PlayerCampaignState.RewardFallbackRef> trackedSheetFallback(
			ServerLevel level,
			AttendanceSheetItem.Binding binding,
			ItemEntity item
	) {
		return CampaignService.snapshot(level, binding.ownerUuid()).filter(state ->
				state.status() == PlayerCampaignState.LectureStatus.PASSED
						&& state.sheetEntitled()
						&& state.sheetRecoverySequence() == binding.recoverySequence()
		).map(PlayerCampaignState::sheetFallback).filter(ref ->
				ref.entityUuid().equals(item.getUUID()));
	}

	private static Optional<PlayerCampaignState.RewardFallbackRef> trackedRemoteFallback(
			ServerLevel level,
			InfiniteSlidesRemoteItem.Binding binding,
			ItemEntity item
	) {
		return CampaignService.snapshot(level, binding.ownerUuid()).filter(state ->
				state.status() == PlayerCampaignState.LectureStatus.PASSED
						&& state.remoteIssued()
						&& binding.projectionUuid().equals(state.remoteProjectionUuid())
		).map(PlayerCampaignState::remoteFallback).filter(ref ->
				ref.entityUuid().equals(item.getUUID()));
	}

	public enum Outcome {
		REJECTED,
		NOT_ENTITLED,
		ALREADY_PRESENT,
		INVENTORY_ISSUED,
		FALLBACK_ISSUED,
		FALLBACK_PENDING,
		SHEET_RECOVERED,
		SHEET_FALLBACK_RECOVERED,
		MATERIALIZATION_FAILED
	}

	public enum ReadyCueDecision {
		ITEM_ABSENT,
		DEFERRED,
		PRESENT
	}

	private enum ProjectionAttempt {
		SKIPPED,
		OBSERVED,
		INVENTORY,
		FALLBACK,
		WAITING,
		FAILED
	}

	private record LiveTransfer(
			CampaignEvent.RewardProjectionKey key,
			PlayerCampaignState.RewardFallbackRef ref,
			Inventory inventory,
			int restoreSlot,
			boolean deathDrop
	) {
		private LiveTransfer {
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(ref, "ref");
			Objects.requireNonNull(inventory, "inventory");
			if (restoreSlot < 0 || restoreSlot >= inventory.getContainerSize()) {
				throw new IllegalArgumentException("restoreSlot outside owner inventory");
			}
		}
	}

	private record RejectedDeathDrop(int restoreSlot, ItemStack stack) {
		private RejectedDeathDrop {
			Objects.requireNonNull(stack, "stack");
		}
	}

	private record PendingDimensionTransfer(
			CampaignEvent.RewardProjectionKey key,
			PlayerCampaignState.RewardFallbackRef sourceRef,
			ServerLevel sourceLevel,
			ItemStack stack
	) {
		private PendingDimensionTransfer {
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(sourceRef, "sourceRef");
			Objects.requireNonNull(sourceLevel, "sourceLevel");
			Objects.requireNonNull(stack, "stack");
		}
	}

	private record AdmittedDimensionTransfer(
			PendingDimensionTransfer pending,
			PlayerCampaignState.RewardFallbackRef targetRef
	) {
		private AdmittedDimensionTransfer {
			Objects.requireNonNull(pending, "pending");
			Objects.requireNonNull(targetRef, "targetRef");
			if (!pending.sourceRef().entityUuid().equals(targetRef.entityUuid())) {
				throw new IllegalArgumentException("dimension transfer target identity changed");
			}
		}
	}

	private enum RepresentationPresence {
		INVENTORY,
		FALLBACK,
		TRACKED_UNLOADED,
		MISSING,
		INVALID
	}

	private enum MaterializationResult {
		NOT_DISPATCHED,
		WAITING,
		OBSERVED,
		SPAWNED,
		FAILED
	}

	private record FallbackObservation(
			RepresentationPresence presence,
			Optional<ItemEntity> entity
	) {
		private static FallbackObservation inventory() {
			return new FallbackObservation(RepresentationPresence.INVENTORY, Optional.empty());
		}

		private static FallbackObservation fallback(ItemEntity entity) {
			return new FallbackObservation(RepresentationPresence.FALLBACK, Optional.of(entity));
		}

		private static FallbackObservation trackedUnloaded() {
			return new FallbackObservation(RepresentationPresence.TRACKED_UNLOADED, Optional.empty());
		}

		private static FallbackObservation missing() {
			return new FallbackObservation(RepresentationPresence.MISSING, Optional.empty());
		}

		private static FallbackObservation invalid() {
			return new FallbackObservation(RepresentationPresence.INVALID, Optional.empty());
		}
	}

	private RewardService() {
	}
}
