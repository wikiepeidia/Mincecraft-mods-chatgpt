package dev.developershell.campaign;

import dev.developershell.campaign.CampaignTransition.EffectIntent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure, monotonic campaign state machine. */
public final class CampaignReducer {
	public static CampaignTransition reduce(
			Optional<PlayerCampaignState> currentState,
			CampaignEvent event
	) {
		Objects.requireNonNull(currentState, "currentState");
		Objects.requireNonNull(event, "event");

		if (currentState.isPresent() && !currentState.get().ownerUuid().equals(event.ownerUuid())) {
			return CampaignTransition.noOp(currentState, "wrong_owner");
		}
		if (event instanceof CampaignEvent.Start start) {
			return reduceStart(currentState, start);
		}
		if (currentState.isEmpty()) {
			return CampaignTransition.noOp(currentState, "missing_state");
		}

		PlayerCampaignState state = currentState.get();
		if (event instanceof CampaignEvent.EncounterTerminal terminal) {
			return reduceEncounterTerminal(state, terminal);
		}
		if (event instanceof CampaignEvent.ReconcileRetake reconcileRetake) {
			return reduceRetakeReconciliation(state, reconcileRetake);
		}
		if (event instanceof CampaignEvent.RetakeFallback retakeFallback) {
			return reduceRetakeFallback(state, retakeFallback);
		}
		if (event instanceof CampaignEvent.RecoverSheet recoverSheet) {
			return reduceSheetRecovery(state, recoverSheet);
		}
		if (event instanceof CampaignEvent.ConfirmSheetProjection confirmSheet) {
			return confirmSheetProjection(state, confirmSheet);
		}
		if (event instanceof CampaignEvent.ConfirmRemoteProjection confirmRemote) {
			return confirmRemoteProjection(state, confirmRemote);
		}
		if (event instanceof CampaignEvent.ResolveLegacyRemoteAbsence resolveLegacyRemote) {
			return resolveLegacyRemoteAbsence(state, resolveLegacyRemote);
		}
		if (event instanceof CampaignEvent.RewardFallback rewardFallback) {
			return reduceRewardFallback(state, rewardFallback);
		}
		if (event instanceof CampaignEvent.StartRemoteCooldown startRemoteCooldown) {
			return reduceRemoteCooldown(state, startRemoteCooldown);
		}
		if (event instanceof CampaignEvent.RemoteReadyNotice remoteReadyNotice) {
			return reduceRemoteReadyNotice(state, remoteReadyNotice);
		}
		throw new IllegalStateException("Unhandled campaign event: " + event.getClass().getName());
	}

	private static CampaignTransition reduceStart(
			Optional<PlayerCampaignState> currentState,
			CampaignEvent.Start event
	) {
		PlayerCampaignState previous = currentState.orElse(null);
		if (previous == null) {
			if (event.expectedRetakeKey() != null) {
				return CampaignTransition.noOp(currentState, "wrong_retake");
			}
		}
		else {
			if (previous.activeEncounterRef() != null
					|| (previous.status() != PlayerCampaignState.LectureStatus.READY
						&& previous.status() != PlayerCampaignState.LectureStatus.RETAKE_READY)) {
				return CampaignTransition.noOp(currentState, "start_not_ready");
			}
			if (previous.status() == PlayerCampaignState.LectureStatus.RETAKE_READY) {
				if (event.expectedRetakeKey() == null) {
					return CampaignTransition.noOp(currentState, "missing_retake_key");
				}
				if (!previous.retakeKey().filter(event.expectedRetakeKey()::equals).isPresent()) {
					return CampaignTransition.noOp(currentState, "wrong_retake");
				}
				if (!previous.deskDimension().equals(event.deskDimension())
						|| !previous.deskPos().equals(event.deskPos())
						|| previous.deskFacing() != event.deskFacing()) {
					return CampaignTransition.noOp(currentState, "wrong_retake_desk");
				}
			}
			else if (event.expectedRetakeKey() != null) {
				return CampaignTransition.noOp(currentState, "wrong_retake");
			}
		}

		int nextAttempt = previous == null ? 1 : Math.addExact(previous.attemptCount(), 1);
		PlayerCampaignState.EncounterRef encounter = new PlayerCampaignState.EncounterRef(
				event.ownerUuid(),
				event.encounterUuid(),
				event.professorUuid(),
				nextAttempt
		);
		PlayerCampaignState next = new PlayerCampaignState(
				event.ownerUuid(),
				previous == null ? PlayerCampaignState.CampaignChapter.PRE_LECTURE : previous.chapter(),
				PlayerCampaignState.LectureStatus.ACTIVE,
				nextAttempt,
				event.deskDimension(),
				event.deskPos(),
				event.deskFacing(),
				event.retryPos(),
				encounter,
				previous != null && previous.sheetEntitled(),
				previous != null && previous.remoteIssued(),
				false,
				null,
				null,
				null,
				previous == null ? 0L : previous.remoteCooldownUntilGameTime(),
				previous == null ? 0L : previous.sheetRecoverySequence(),
				previous == null ? 0L : previous.remoteReadyNoticeForDeadlineGameTime()
		);
		return CampaignTransition.accepted(next, "start_accepted", new EffectIntent.StartEncounter(encounter));
	}

	private static CampaignTransition reduceEncounterTerminal(
			PlayerCampaignState state,
			CampaignEvent.EncounterTerminal event
	) {
		Optional<String> mismatch = activeMismatch(state, event);
		if (mismatch.isPresent()) {
			return noOp(state, mismatch.get());
		}

		return switch (event) {
			case CampaignEvent.Terminal terminal -> acceptTerminal(state, terminal);
			case CampaignEvent.NormalizeReload reload -> acceptReload(state, reload);
			case CampaignEvent.Victory victory -> acceptVictory(state, victory);
		};
	}

	private static CampaignTransition acceptTerminal(
			PlayerCampaignState state,
			CampaignEvent.Terminal event
	) {
		PlayerCampaignState next = copy(
				state,
				state.chapter(),
				PlayerCampaignState.LectureStatus.RETAKE_READY,
				null,
				state.sheetEntitled(),
				state.remoteIssued(),
				true,
				event.encounterUuid(),
				null,
				null,
				state.remoteCooldownUntilGameTime(),
				state.sheetRecoverySequence(),
				state.remoteReadyNoticeForDeadlineGameTime()
		);
		return CampaignTransition.accepted(
				next,
				"terminal_" + event.reason().serializedName(),
				new EffectIntent.CleanupEncounter(state.ownerUuid(), event.encounterUuid(), event.reason().serializedName()),
				new EffectIntent.ReconcileRetake(state.ownerUuid())
		);
	}

	private static CampaignTransition acceptReload(
			PlayerCampaignState state,
			CampaignEvent.NormalizeReload event
	) {
		PlayerCampaignState next = copy(
				state,
				state.chapter(),
				PlayerCampaignState.LectureStatus.RETAKE_READY,
				null,
				state.sheetEntitled(),
				state.remoteIssued(),
				true,
				event.encounterUuid(),
				null,
				null,
				state.remoteCooldownUntilGameTime(),
				state.sheetRecoverySequence(),
				state.remoteReadyNoticeForDeadlineGameTime()
		);
		return CampaignTransition.accepted(
				next,
				"reload_normalized",
				new EffectIntent.CleanupEncounter(state.ownerUuid(), event.encounterUuid(), "reload_normalization"),
				new EffectIntent.ReconcileRetake(state.ownerUuid())
		);
	}

	private static CampaignTransition acceptVictory(
			PlayerCampaignState state,
			CampaignEvent.Victory event
	) {
		PlayerCampaignState next = new PlayerCampaignState(
				state.ownerUuid(),
				PlayerCampaignState.CampaignChapter.LECTURE_PASSED,
				PlayerCampaignState.LectureStatus.PASSED,
				state.attemptCount(),
				state.deskDimension(),
				state.deskPos(),
				state.deskFacing(),
				state.retryPos(),
				null,
				true,
				true,
				false,
				null,
				null,
				null,
				state.remoteCooldownUntilGameTime(),
				state.sheetRecoverySequence(),
				state.remoteReadyNoticeForDeadlineGameTime(),
				true,
				true,
				event.encounterUuid(),
				false,
				null,
				null
		);
		List<EffectIntent> intents = new ArrayList<>();
		intents.add(new EffectIntent.CleanupEncounter(state.ownerUuid(), event.encounterUuid(), "victory"));
		intents.add(new EffectIntent.GrantFirstRewards(state.ownerUuid()));
		return CampaignTransition.accepted(next, "victory_accepted", intents);
	}

	private static CampaignTransition confirmSheetProjection(
			PlayerCampaignState state,
			CampaignEvent.ConfirmSheetProjection event
	) {
		if (state.status() != PlayerCampaignState.LectureStatus.PASSED
				|| !state.sheetProjectionPending()) {
			return noOp(state, "sheet_projection_not_pending");
		}
		if (event.recoverySequence() != state.sheetRecoverySequence()) {
			return noOp(state, "stale_sheet_projection");
		}
		return CampaignTransition.accepted(
				copyRewardProjectionState(
						state,
						false,
						state.remoteProjectionPending(),
						state.legacyRemoteAdoptionPending()
				),
				"sheet_projection_confirmed"
		);
	}

	private static CampaignTransition confirmRemoteProjection(
			PlayerCampaignState state,
			CampaignEvent.ConfirmRemoteProjection event
	) {
		if (state.status() != PlayerCampaignState.LectureStatus.PASSED
				|| !state.remoteProjectionPending()) {
			return noOp(state, "remote_projection_not_pending");
		}
		if (!event.projectionUuid().equals(state.remoteProjectionUuid())) {
			return noOp(state, "stale_remote_projection");
		}
		return CampaignTransition.accepted(
				copyRewardProjectionState(state, state.sheetProjectionPending(), false, false),
				"remote_projection_confirmed"
		);
	}

	private static CampaignTransition resolveLegacyRemoteAbsence(
			PlayerCampaignState state,
			CampaignEvent.ResolveLegacyRemoteAbsence event
	) {
		if (!state.legacyRemoteAdoptionPending()
				|| !state.remoteProjectionPending()
				|| !event.projectionUuid().equals(state.remoteProjectionUuid())) {
			return noOp(state, "legacy_remote_not_pending");
		}
		return CampaignTransition.accepted(
				copyRewardProjectionState(
						state,
						state.sheetProjectionPending(),
						true,
						false
				),
				"legacy_remote_absence_resolved"
		);
	}

	private static CampaignTransition reduceRewardFallback(
			PlayerCampaignState state,
			CampaignEvent.RewardFallback event
	) {
		boolean sheet = event.key() instanceof CampaignEvent.SheetProjectionKey;
		boolean currentGeneration;
		boolean projectionPending;
		PlayerCampaignState.RewardFallbackRef current;
		if (sheet) {
			CampaignEvent.SheetProjectionKey key = (CampaignEvent.SheetProjectionKey) event.key();
			currentGeneration = state.status() == PlayerCampaignState.LectureStatus.PASSED
					&& state.sheetEntitled()
					&& key.recoverySequence() == state.sheetRecoverySequence();
			projectionPending = state.sheetProjectionPending();
			current = state.sheetFallback();
		}
		else {
			CampaignEvent.RemoteProjectionKey key = (CampaignEvent.RemoteProjectionKey) event.key();
			currentGeneration = state.status() == PlayerCampaignState.LectureStatus.PASSED
					&& state.remoteIssued()
					&& key.projectionUuid().equals(state.remoteProjectionUuid());
			projectionPending = state.remoteProjectionPending();
			current = state.remoteFallback();
		}
		if (!currentGeneration) {
			return noOp(state, "stale_reward_fallback");
		}

		if (event.operation() == CampaignEvent.RewardFallbackOperation.RESERVE) {
			if (event.expectedPrior() != null) {
				return noOp(state, "stale_reward_fallback_context");
			}
			if (!projectionPending
					|| current != null
					|| (!sheet && state.legacyRemoteAdoptionPending())
					|| !event.dimension().equals(state.deskDimension())
					|| !event.position().equals(state.retryPos())) {
				return noOp(state, "reward_fallback_not_reservable");
			}
			PlayerCampaignState.RewardFallbackRef reservation = new PlayerCampaignState.RewardFallbackRef(
					event.entityUuid(), event.dimension(), event.position(), false);
			PlayerCampaignState next = copyRewardFallbackState(
					state,
					sheet ? reservation : state.sheetFallback(),
					sheet ? state.remoteFallback() : reservation
			);
			return CampaignTransition.accepted(
					next,
					"reward_fallback_reserved",
					new EffectIntent.MaterializeRewardFallback(event.key(), reservation)
			);
		}
		if (event.operation() == CampaignEvent.RewardFallbackOperation.TRANSFERRED) {
			if (event.expectedPrior() != null) {
				return noOp(state, "stale_reward_fallback_context");
			}
			if (projectionPending
					|| current != null
					|| (!sheet && state.legacyRemoteAdoptionPending())) {
				return noOp(state, "reward_fallback_not_transferable");
			}
			PlayerCampaignState.RewardFallbackRef transferred = new PlayerCampaignState.RewardFallbackRef(
					event.entityUuid(), event.dimension(), event.position(), true);
			PlayerCampaignState next = copyRewardFallbackState(
					state,
					sheet ? transferred : state.sheetFallback(),
					sheet ? state.remoteFallback() : transferred
			);
			return CampaignTransition.accepted(next, "reward_fallback_transferred");
		}

		if (current == null || !current.entityUuid().equals(event.entityUuid())) {
			return noOp(state, "wrong_reward_fallback");
		}
		if (!Objects.equals(current, event.expectedPrior())) {
			return noOp(state, "stale_reward_fallback_context");
		}
		if (event.operation() == CampaignEvent.RewardFallbackOperation.REQUEUED) {
			PlayerCampaignState next = copyRewardProjectionAndFallbackState(
					state,
					sheet || state.sheetProjectionPending(),
					!sheet || state.remoteProjectionPending(),
					sheet ? null : state.sheetFallback(),
					sheet ? state.remoteFallback() : null
			);
			return CampaignTransition.accepted(next, "reward_fallback_requeued");
		}
		PlayerCampaignState.RewardFallbackRef nextRef = switch (event.operation()) {
			case MATERIALIZED -> current.at(event.dimension(), event.position(), true);
			case RELOCATED -> current.materialized()
					? current.at(event.dimension(), event.position(), true)
					: null;
			case LOST -> null;
			case CLEARED -> null;
			case RESERVE, TRANSFERRED, REQUEUED ->
					throw new IllegalStateException("initial or requeue operations handled above");
		};
		if (event.operation() == CampaignEvent.RewardFallbackOperation.RELOCATED && nextRef == null) {
			return noOp(state, "reward_fallback_not_materialized");
		}
		if (Objects.equals(current, nextRef)) {
			return noOp(state, "reward_fallback_unchanged");
		}
		PlayerCampaignState next = copyRewardFallbackState(
				state,
				sheet ? nextRef : state.sheetFallback(),
				sheet ? state.remoteFallback() : nextRef
		);
		return CampaignTransition.accepted(
				next,
				"reward_fallback_" + event.operation().serializedName()
		);
	}

	private static CampaignTransition reduceRetakeReconciliation(
			PlayerCampaignState state,
			CampaignEvent.ReconcileRetake event
	) {
		if (state.status() != PlayerCampaignState.LectureStatus.RETAKE_READY || !state.retakeEntitled()) {
			return noOp(state, "retake_not_entitled");
		}
		if (!state.retakeKey().filter(event.retakeKey()::equals).isPresent()) {
			return noOp(state, "wrong_retake");
		}
		if (state.retakeFallbackReservationUuid() != null || state.retakeFallbackEntityUuid() != null) {
			return noOp(state, "retake_representation_exists");
		}
		PlayerCampaignState next = copy(
				state,
				state.chapter(),
				state.status(),
				state.activeEncounterRef(),
				state.sheetEntitled(),
				state.remoteIssued(),
				true,
				state.retakeEncounterUuid(),
				event.fallbackEntityUuid(),
				null,
				state.remoteCooldownUntilGameTime(),
				state.sheetRecoverySequence(),
				state.remoteReadyNoticeForDeadlineGameTime()
		);
		return CampaignTransition.accepted(
				next,
				"retake_fallback_reserved",
				new EffectIntent.MaterializeRetakeFallback(state.ownerUuid(), event.fallbackEntityUuid())
		);
	}

	private static CampaignTransition reduceRetakeFallback(
			PlayerCampaignState state,
			CampaignEvent.RetakeFallback event
	) {
		if (!state.retakeEntitled()) {
			return noOp(state, "retake_not_entitled");
		}
		if (!state.retakeKey().filter(event.retakeKey()::equals).isPresent()) {
			return noOp(state, "wrong_retake");
		}

		boolean reserved = Objects.equals(state.retakeFallbackReservationUuid(), event.fallbackEntityUuid());
		boolean materialized = Objects.equals(state.retakeFallbackEntityUuid(), event.fallbackEntityUuid());
		if ((event.operation() == CampaignEvent.FallbackOperation.MATERIALIZED
				|| event.operation() == CampaignEvent.FallbackOperation.MATERIALIZATION_FAILED)
				? !reserved : !materialized) {
			return noOp(state, "wrong_fallback");
		}
		boolean committing = event.operation() == CampaignEvent.FallbackOperation.MATERIALIZED;
		PlayerCampaignState next = copy(
				state,
				state.chapter(),
				state.status(),
				state.activeEncounterRef(),
				state.sheetEntitled(),
				state.remoteIssued(),
				true,
				state.retakeEncounterUuid(),
				null,
				committing ? event.fallbackEntityUuid() : null,
				state.remoteCooldownUntilGameTime(),
				state.sheetRecoverySequence(),
				state.remoteReadyNoticeForDeadlineGameTime()
		);
		return committing
				? CampaignTransition.accepted(
						next,
						"retake_fallback_" + event.operation().serializedName()
				)
				: CampaignTransition.accepted(
						next,
						"retake_fallback_" + event.operation().serializedName(),
						new EffectIntent.ReconcileRetake(state.ownerUuid())
				);
	}

	private static CampaignTransition reduceSheetRecovery(
			PlayerCampaignState state,
			CampaignEvent.RecoverSheet event
	) {
		if (state.status() != PlayerCampaignState.LectureStatus.PASSED || !state.sheetEntitled()) {
			return noOp(state, "sheet_not_recoverable");
		}
		if (state.sheetProjectionPending()) {
			return noOp(state, "sheet_projection_pending");
		}
		if (event.expectedSequence() != state.sheetRecoverySequence()) {
			return noOp(state, "stale_sheet_recovery");
		}
		if (state.sheetRecoverySequence() == Long.MAX_VALUE) {
			return noOp(state, "sheet_recovery_exhausted");
		}
		long nextSequence = state.sheetRecoverySequence() + 1L;
		PlayerCampaignState next = new PlayerCampaignState(
				state.ownerUuid(),
				state.chapter(),
				state.status(),
				state.attemptCount(),
				state.deskDimension(),
				state.deskPos(),
				state.deskFacing(),
				state.retryPos(),
				state.activeEncounterRef(),
				state.sheetEntitled(),
				state.remoteIssued(),
				state.retakeEntitled(),
				state.retakeEncounterUuid(),
				state.retakeFallbackReservationUuid(),
				state.retakeFallbackEntityUuid(),
				state.remoteCooldownUntilGameTime(),
				nextSequence,
				state.remoteReadyNoticeForDeadlineGameTime(),
				true,
				state.remoteProjectionPending(),
				state.remoteProjectionUuid(),
				state.legacyRemoteAdoptionPending(),
				null,
				state.remoteFallback()
		);
		return CampaignTransition.accepted(
				next,
				"sheet_recovery_accepted",
				new EffectIntent.RecoverAttendanceSheet(state.ownerUuid(), nextSequence)
		);
	}

	private static CampaignTransition reduceRemoteCooldown(
			PlayerCampaignState state,
			CampaignEvent.StartRemoteCooldown event
	) {
		if (state.status() != PlayerCampaignState.LectureStatus.PASSED
				|| !state.remoteIssued()
				|| state.remoteProjectionPending()) {
			return noOp(state, "remote_not_entitled");
		}
		if (event.observedGameTime() < state.remoteCooldownUntilGameTime()) {
			return noOp(state, "remote_on_cooldown");
		}
		if (event.cooldownDeadlineGameTime() <= state.remoteCooldownUntilGameTime()
				|| event.cooldownDeadlineGameTime() <= event.observedGameTime()) {
			return noOp(state, "cooldown_deadline_not_monotonic");
		}
		PlayerCampaignState next = copy(
				state,
				state.chapter(),
				state.status(),
				state.activeEncounterRef(),
				state.sheetEntitled(),
				state.remoteIssued(),
				state.retakeEntitled(),
				state.retakeEncounterUuid(),
				state.retakeFallbackReservationUuid(),
				state.retakeFallbackEntityUuid(),
				event.cooldownDeadlineGameTime(),
				state.sheetRecoverySequence(),
				state.remoteReadyNoticeForDeadlineGameTime()
		);
		return CampaignTransition.accepted(
				next,
				"remote_cooldown_started",
				new EffectIntent.ApplyRemoteCooldown(state.ownerUuid(), event.cooldownDeadlineGameTime())
		);
	}

	private static CampaignTransition reduceRemoteReadyNotice(
			PlayerCampaignState state,
			CampaignEvent.RemoteReadyNotice event
	) {
		if (!state.remoteIssued() || state.remoteProjectionPending()) {
			return noOp(state, "remote_not_entitled");
		}
		if (event.cooldownDeadlineGameTime() != state.remoteCooldownUntilGameTime()) {
			return noOp(state, "stale_cooldown_deadline");
		}
		if (event.observedGameTime() < event.cooldownDeadlineGameTime()) {
			return noOp(state, "remote_not_ready");
		}
		if (event.cooldownDeadlineGameTime() == 0L
				|| state.remoteReadyNoticeForDeadlineGameTime() >= event.cooldownDeadlineGameTime()) {
			return noOp(state, "remote_ready_already_noticed");
		}
		PlayerCampaignState next = copy(
				state,
				state.chapter(),
				state.status(),
				state.activeEncounterRef(),
				state.sheetEntitled(),
				state.remoteIssued(),
				state.retakeEntitled(),
				state.retakeEncounterUuid(),
				state.retakeFallbackReservationUuid(),
				state.retakeFallbackEntityUuid(),
				state.remoteCooldownUntilGameTime(),
				state.sheetRecoverySequence(),
				event.cooldownDeadlineGameTime()
		);
		return CampaignTransition.accepted(
				next,
				"remote_ready_noticed",
				new EffectIntent.NotifyRemoteReady(state.ownerUuid(), event.cooldownDeadlineGameTime())
		);
	}

	private static Optional<String> activeMismatch(
			PlayerCampaignState state,
			CampaignEvent.EncounterTerminal event
	) {
		if (state.activeEncounterRef() == null || state.status() != PlayerCampaignState.LectureStatus.ACTIVE) {
			return Optional.of("no_active_encounter");
		}
		if (!state.matchesActiveEncounter(event.ownerUuid(), event.encounterUuid())) {
			return Optional.of("wrong_encounter");
		}
		return Optional.empty();
	}

	private static CampaignTransition noOp(PlayerCampaignState state, String reason) {
		return CampaignTransition.noOp(Optional.of(state), reason);
	}

	private static PlayerCampaignState copy(
			PlayerCampaignState state,
			PlayerCampaignState.CampaignChapter chapter,
			PlayerCampaignState.LectureStatus status,
			PlayerCampaignState.EncounterRef activeEncounter,
			boolean sheetEntitled,
			boolean remoteIssued,
			boolean retakeEntitled,
			java.util.UUID retakeEncounterUuid,
			java.util.UUID fallbackReservationUuid,
			java.util.UUID fallbackUuid,
			long remoteCooldownUntilGameTime,
			long sheetRecoverySequence,
			long remoteReadyNoticeForDeadlineGameTime
	) {
		return new PlayerCampaignState(
				state.ownerUuid(),
				chapter,
				status,
				state.attemptCount(),
				state.deskDimension(),
				state.deskPos(),
				state.deskFacing(),
				state.retryPos(),
				activeEncounter,
				sheetEntitled,
				remoteIssued,
				retakeEntitled,
				retakeEncounterUuid,
				fallbackReservationUuid,
				fallbackUuid,
				remoteCooldownUntilGameTime,
				sheetRecoverySequence,
				remoteReadyNoticeForDeadlineGameTime,
				state.sheetProjectionPending(),
				state.remoteProjectionPending(),
				state.remoteProjectionUuid(),
				state.legacyRemoteAdoptionPending(),
				state.sheetFallback(),
				state.remoteFallback()
		);
	}

	private static PlayerCampaignState copyRewardProjectionState(
			PlayerCampaignState state,
			boolean sheetProjectionPending,
			boolean remoteProjectionPending,
			boolean legacyRemoteAdoptionPending
	) {
		return new PlayerCampaignState(
				state.ownerUuid(),
				state.chapter(),
				state.status(),
				state.attemptCount(),
				state.deskDimension(),
				state.deskPos(),
				state.deskFacing(),
				state.retryPos(),
				state.activeEncounterRef(),
				state.sheetEntitled(),
				state.remoteIssued(),
				state.retakeEntitled(),
				state.retakeEncounterUuid(),
				state.retakeFallbackReservationUuid(),
				state.retakeFallbackEntityUuid(),
				state.remoteCooldownUntilGameTime(),
				state.sheetRecoverySequence(),
				state.remoteReadyNoticeForDeadlineGameTime(),
				sheetProjectionPending,
				remoteProjectionPending,
				state.remoteProjectionUuid(),
				legacyRemoteAdoptionPending,
				state.sheetFallback(),
				state.remoteFallback()
		);
	}

	private static PlayerCampaignState copyRewardFallbackState(
			PlayerCampaignState state,
			PlayerCampaignState.RewardFallbackRef sheetFallback,
			PlayerCampaignState.RewardFallbackRef remoteFallback
	) {
		return new PlayerCampaignState(
				state.ownerUuid(),
				state.chapter(),
				state.status(),
				state.attemptCount(),
				state.deskDimension(),
				state.deskPos(),
				state.deskFacing(),
				state.retryPos(),
				state.activeEncounterRef(),
				state.sheetEntitled(),
				state.remoteIssued(),
				state.retakeEntitled(),
				state.retakeEncounterUuid(),
				state.retakeFallbackReservationUuid(),
				state.retakeFallbackEntityUuid(),
				state.remoteCooldownUntilGameTime(),
				state.sheetRecoverySequence(),
				state.remoteReadyNoticeForDeadlineGameTime(),
				state.sheetProjectionPending(),
				state.remoteProjectionPending(),
				state.remoteProjectionUuid(),
				state.legacyRemoteAdoptionPending(),
				sheetFallback,
				remoteFallback
		);
	}

	private static PlayerCampaignState copyRewardProjectionAndFallbackState(
			PlayerCampaignState state,
			boolean sheetProjectionPending,
			boolean remoteProjectionPending,
			PlayerCampaignState.RewardFallbackRef sheetFallback,
			PlayerCampaignState.RewardFallbackRef remoteFallback
	) {
		return new PlayerCampaignState(
				state.ownerUuid(),
				state.chapter(),
				state.status(),
				state.attemptCount(),
				state.deskDimension(),
				state.deskPos(),
				state.deskFacing(),
				state.retryPos(),
				state.activeEncounterRef(),
				state.sheetEntitled(),
				state.remoteIssued(),
				state.retakeEntitled(),
				state.retakeEncounterUuid(),
				state.retakeFallbackReservationUuid(),
				state.retakeFallbackEntityUuid(),
				state.remoteCooldownUntilGameTime(),
				state.sheetRecoverySequence(),
				state.remoteReadyNoticeForDeadlineGameTime(),
				sheetProjectionPending,
				remoteProjectionPending,
				state.remoteProjectionUuid(),
				false,
				sheetFallback,
				remoteFallback
		);
	}

	private CampaignReducer() {
	}
}
