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
		boolean firstRemote = !state.remoteIssued();
		PlayerCampaignState next = copy(
				state,
				PlayerCampaignState.CampaignChapter.LECTURE_PASSED,
				PlayerCampaignState.LectureStatus.PASSED,
				null,
				true,
				true,
				false,
				null,
				null,
				null,
				state.remoteCooldownUntilGameTime(),
				state.sheetRecoverySequence(),
				state.remoteReadyNoticeForDeadlineGameTime()
		);
		List<EffectIntent> intents = new ArrayList<>();
		intents.add(new EffectIntent.CleanupEncounter(state.ownerUuid(), event.encounterUuid(), "victory"));
		if (firstRemote) {
			intents.add(new EffectIntent.GrantFirstRewards(state.ownerUuid()));
		}
		return CampaignTransition.accepted(next, "victory_accepted", intents);
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
		if (event.expectedSequence() != state.sheetRecoverySequence()) {
			return noOp(state, "stale_sheet_recovery");
		}
		if (state.sheetRecoverySequence() == Long.MAX_VALUE) {
			return noOp(state, "sheet_recovery_exhausted");
		}
		long nextSequence = state.sheetRecoverySequence() + 1L;
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
				nextSequence,
				state.remoteReadyNoticeForDeadlineGameTime()
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
		if (state.status() != PlayerCampaignState.LectureStatus.PASSED || !state.remoteIssued()) {
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
		if (!state.remoteIssued()) {
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
				remoteReadyNoticeForDeadlineGameTime
		);
	}

	private CampaignReducer() {
	}
}
