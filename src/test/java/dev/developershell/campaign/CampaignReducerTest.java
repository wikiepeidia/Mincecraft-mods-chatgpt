package dev.developershell.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.developershell.campaign.CampaignEvent.FallbackOperation;
import dev.developershell.campaign.CampaignEvent.TerminalReason;
import dev.developershell.campaign.CampaignTransition.EffectIntent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

final class CampaignReducerTest {
	private static final UUID OWNER = UUID.fromString("c0de0000-0000-4000-8000-000000000401");
	private static final UUID OTHER_OWNER = UUID.fromString("c0de0000-0000-4000-8000-000000000402");
	private static final UUID ENCOUNTER = UUID.fromString("c0de0000-0000-4000-8000-000000000411");
	private static final UUID OTHER_ENCOUNTER = UUID.fromString("c0de0000-0000-4000-8000-000000000412");
	private static final UUID PROFESSOR = UUID.fromString("c0de0000-0000-4000-8000-000000000421");
	private static final UUID OTHER_PROFESSOR = UUID.fromString("c0de0000-0000-4000-8000-000000000422");
	private static final UUID FALLBACK = UUID.fromString("c0de0000-0000-4000-8000-000000000431");
	private static final UUID OTHER_FALLBACK = UUID.fromString("c0de0000-0000-4000-8000-000000000432");
	private static final BlockPos DESK = new BlockPos(40, 72, -12);
	private static final BlockPos RETRY = new BlockPos(40, 72, -14);
	private static final long EVENT_ORDER_SEED = 0x02_04_2026L;

	@Test
	void startAcceptsImplicitReadyAndRetakeExactlyOnce() {
		CampaignEvent.Start start = startEvent(OWNER, ENCOUNTER, PROFESSOR);

		CampaignTransition first = CampaignReducer.reduce(Optional.empty(), start);
		assertAccepted(first, "start_accepted");
		PlayerCampaignState active = first.nextState().orElseThrow();
		assertEquals(OWNER, active.ownerUuid());
		assertEquals(PlayerCampaignState.CampaignChapter.PRE_LECTURE, active.chapter());
		assertEquals(PlayerCampaignState.LectureStatus.ACTIVE, active.status());
		assertEquals(1, active.attemptCount());
		assertEquals(PlayerCampaignState.OVERWORLD_DIMENSION, active.deskDimension());
		assertEquals(DESK, active.deskPos());
		assertEquals(Direction.NORTH, active.deskFacing());
		assertEquals(RETRY, active.retryPos());
		assertEquals(new PlayerCampaignState.EncounterRef(OWNER, ENCOUNTER, PROFESSOR, 1), active.activeEncounterRef());
		assertEquals(List.of(new EffectIntent.StartEncounter(active.activeEncounterRef())), first.intents());

		assertNoOp(CampaignReducer.reduce(first.nextState(), start), active, "start_not_ready");

		PlayerCampaignState retake = state(
				OWNER,
				PlayerCampaignState.CampaignChapter.PRE_LECTURE,
				PlayerCampaignState.LectureStatus.RETAKE_READY,
				3,
				null,
				false,
				false,
				true,
				FALLBACK,
				900L,
				2L,
				800L
		);
		CampaignEvent.Start retryStart = startEvent(OWNER, OTHER_ENCOUNTER, OTHER_PROFESSOR);
		CampaignTransition retried = CampaignReducer.reduce(Optional.of(retake), retryStart);
		assertAccepted(retried, "start_accepted");
		PlayerCampaignState retriedState = retried.nextState().orElseThrow();
		assertEquals(4, retriedState.attemptCount());
		assertEquals(new PlayerCampaignState.EncounterRef(OWNER, OTHER_ENCOUNTER, OTHER_PROFESSOR, 4), retriedState.activeEncounterRef());
		assertFalse(retriedState.retakeEntitled());
		assertEquals(null, retriedState.retakeFallbackEntityUuid());
		assertEquals(900L, retriedState.remoteCooldownUntilGameTime());
		assertEquals(2L, retriedState.sheetRecoverySequence());
		assertEquals(800L, retriedState.remoteReadyNoticeForDeadlineGameTime());

		assertNoOp(
				CampaignReducer.reduce(Optional.of(retake), startEvent(OTHER_OWNER, OTHER_ENCOUNTER, OTHER_PROFESSOR)),
				retake,
				"wrong_owner"
		);
	}

	@Test
	void everyTerminalReasonMatchesOwnerAndEncounterExactlyOnce() {
		List<String> expectedNames = List.of(
				"death",
				"escape",
				"timeout",
				"dimension_change",
				"disconnect",
				"abort",
				"server_stop",
				"entity_unload"
		);
		assertEquals(expectedNames, List.of(TerminalReason.values()).stream().map(TerminalReason::serializedName).toList());

		for (TerminalReason reason : TerminalReason.values()) {
			PlayerCampaignState active = activeState(OWNER, ENCOUNTER, PROFESSOR, 4);
			CampaignEvent.Terminal matching = new CampaignEvent.Terminal(OWNER, ENCOUNTER, reason);
			CampaignTransition accepted = CampaignReducer.reduce(Optional.of(active), matching);
			assertAccepted(accepted, "terminal_" + reason.serializedName());
			PlayerCampaignState failed = accepted.nextState().orElseThrow();
			assertEquals(PlayerCampaignState.LectureStatus.RETAKE_READY, failed.status(), reason.serializedName());
			assertEquals(4, failed.attemptCount(), reason.serializedName());
			assertEquals(active.chapter(), failed.chapter(), reason.serializedName());
			assertEquals(null, failed.activeEncounterRef(), reason.serializedName());
			assertTrue(failed.retakeEntitled(), reason.serializedName());
			assertEquals(active.sheetEntitled(), failed.sheetEntitled(), reason.serializedName());
			assertEquals(active.remoteIssued(), failed.remoteIssued(), reason.serializedName());
			assertEquals(active.remoteCooldownUntilGameTime(), failed.remoteCooldownUntilGameTime(), reason.serializedName());
			assertEquals(List.of(
					new EffectIntent.CleanupEncounter(OWNER, ENCOUNTER, reason.serializedName()),
					new EffectIntent.ReconcileRetake(OWNER)
			), accepted.intents(), reason.serializedName());

			assertNoOp(CampaignReducer.reduce(accepted.nextState(), matching), failed, "no_active_encounter");
			assertNoOp(
					CampaignReducer.reduce(Optional.of(active), new CampaignEvent.Terminal(OTHER_OWNER, ENCOUNTER, reason)),
					active,
					"wrong_owner"
			);
			assertNoOp(
					CampaignReducer.reduce(Optional.of(active), new CampaignEvent.Terminal(OWNER, OTHER_ENCOUNTER, reason)),
					active,
					"wrong_encounter"
			);
		}
	}

	@Test
	void reloadNormalizationClearsOnlyMatchingActiveIntentOnce() {
		PlayerCampaignState active = activeState(OWNER, ENCOUNTER, PROFESSOR, 2);
		CampaignEvent.NormalizeReload normalize = new CampaignEvent.NormalizeReload(OWNER, ENCOUNTER);

		CampaignTransition accepted = CampaignReducer.reduce(Optional.of(active), normalize);
		assertAccepted(accepted, "reload_normalized");
		PlayerCampaignState normalized = accepted.nextState().orElseThrow();
		assertEquals(PlayerCampaignState.LectureStatus.RETAKE_READY, normalized.status());
		assertEquals(null, normalized.activeEncounterRef());
		assertTrue(normalized.retakeEntitled());
		assertEquals(2, normalized.attemptCount());
		assertEquals(List.of(
				new EffectIntent.CleanupEncounter(OWNER, ENCOUNTER, "reload_normalization"),
				new EffectIntent.ReconcileRetake(OWNER)
		), accepted.intents());

		assertNoOp(CampaignReducer.reduce(accepted.nextState(), normalize), normalized, "no_active_encounter");
		assertNoOp(
				CampaignReducer.reduce(Optional.of(active), new CampaignEvent.NormalizeReload(OTHER_OWNER, ENCOUNTER)),
				active,
				"wrong_owner"
		);
		assertNoOp(
				CampaignReducer.reduce(Optional.of(active), new CampaignEvent.NormalizeReload(OWNER, OTHER_ENCOUNTER)),
				active,
				"wrong_encounter"
		);
	}

	@Test
	void matchingVictoryCommitsPermanentLedgersAndWinsEveryReplay() {
		PlayerCampaignState active = activeState(OWNER, ENCOUNTER, PROFESSOR, 5);
		CampaignEvent.Victory victory = new CampaignEvent.Victory(OWNER, ENCOUNTER);

		CampaignTransition accepted = CampaignReducer.reduce(Optional.of(active), victory);
		assertAccepted(accepted, "victory_accepted");
		PlayerCampaignState passed = accepted.nextState().orElseThrow();
		assertEquals(PlayerCampaignState.CampaignChapter.LECTURE_PASSED, passed.chapter());
		assertEquals(PlayerCampaignState.LectureStatus.PASSED, passed.status());
		assertEquals(5, passed.attemptCount());
		assertEquals(null, passed.activeEncounterRef());
		assertTrue(passed.sheetEntitled());
		assertTrue(passed.remoteIssued());
		assertFalse(passed.retakeEntitled());
		assertEquals(List.of(
				new EffectIntent.CleanupEncounter(OWNER, ENCOUNTER, "victory"),
				new EffectIntent.GrantFirstRewards(OWNER)
		), accepted.intents());

		assertNoOp(CampaignReducer.reduce(accepted.nextState(), victory), passed, "no_active_encounter");
		assertNoOp(
				CampaignReducer.reduce(Optional.of(active), new CampaignEvent.Victory(OTHER_OWNER, ENCOUNTER)),
				active,
				"wrong_owner"
		);
		assertNoOp(
				CampaignReducer.reduce(Optional.of(active), new CampaignEvent.Victory(OWNER, OTHER_ENCOUNTER)),
				active,
				"wrong_encounter"
		);

		CampaignTransition failedFirst = CampaignReducer.reduce(
				Optional.of(active),
				new CampaignEvent.Terminal(OWNER, ENCOUNTER, TerminalReason.DISCONNECT)
		);
		assertNoOp(
				CampaignReducer.reduce(failedFirst.nextState(), victory),
				failedFirst.nextState().orElseThrow(),
				"no_active_encounter"
		);
		assertNoOp(
				CampaignReducer.reduce(accepted.nextState(), new CampaignEvent.Terminal(OWNER, ENCOUNTER, TerminalReason.DEATH)),
				passed,
				"no_active_encounter"
		);
	}

	@Test
	void retakeFallbackReservationAndLossAreReplaySafe() {
		PlayerCampaignState failed = failedState(OWNER, 3, null);
		CampaignEvent.ReconcileRetake reconcile = new CampaignEvent.ReconcileRetake(OWNER, FALLBACK);

		CampaignTransition reserved = CampaignReducer.reduce(Optional.of(failed), reconcile);
		assertAccepted(reserved, "retake_fallback_reserved");
		PlayerCampaignState withFallback = reserved.nextState().orElseThrow();
		assertEquals(FALLBACK, withFallback.retakeFallbackEntityUuid());
		assertEquals(List.of(new EffectIntent.MaterializeRetakeFallback(OWNER, FALLBACK)), reserved.intents());
		assertMonotonicFieldsEqual(failed, withFallback);

		assertNoOp(CampaignReducer.reduce(reserved.nextState(), reconcile), withFallback, "retake_representation_exists");
		assertNoOp(
				CampaignReducer.reduce(Optional.of(failed), new CampaignEvent.ReconcileRetake(OTHER_OWNER, FALLBACK)),
				failed,
				"wrong_owner"
		);

		CampaignEvent.RetakeFallback lost = new CampaignEvent.RetakeFallback(
				OWNER,
				FALLBACK,
				FallbackOperation.LOST
		);
		CampaignTransition cleared = CampaignReducer.reduce(Optional.of(withFallback), lost);
		assertAccepted(cleared, "retake_fallback_lost");
		PlayerCampaignState recoverable = cleared.nextState().orElseThrow();
		assertEquals(null, recoverable.retakeFallbackEntityUuid());
		assertTrue(recoverable.retakeEntitled());
		assertEquals(List.of(new EffectIntent.ReconcileRetake(OWNER)), cleared.intents());
		assertMonotonicFieldsEqual(withFallback, recoverable);

		assertNoOp(CampaignReducer.reduce(cleared.nextState(), lost), recoverable, "wrong_fallback");
		assertNoOp(
				CampaignReducer.reduce(Optional.of(withFallback), new CampaignEvent.RetakeFallback(
						OWNER,
						OTHER_FALLBACK,
						FallbackOperation.LOST
				)),
				withFallback,
				"wrong_fallback"
		);
	}

	@Test
	void sheetRecoveryUsesMonotonicSequenceToSuppressReplays() {
		PlayerCampaignState passed = passedState(OWNER, 4, 1_200L, 4L, 1_000L);
		CampaignEvent.RecoverSheet recover = new CampaignEvent.RecoverSheet(OWNER, 4L);

		CampaignTransition accepted = CampaignReducer.reduce(Optional.of(passed), recover);
		assertAccepted(accepted, "sheet_recovery_accepted");
		PlayerCampaignState recovered = accepted.nextState().orElseThrow();
		assertEquals(5L, recovered.sheetRecoverySequence());
		assertEquals(List.of(new EffectIntent.RecoverAttendanceSheet(OWNER, 5L)), accepted.intents());
		assertMonotonicFieldsEqual(passed, recovered);

		assertNoOp(CampaignReducer.reduce(accepted.nextState(), recover), recovered, "stale_sheet_recovery");
		assertNoOp(
				CampaignReducer.reduce(Optional.of(passed), new CampaignEvent.RecoverSheet(OWNER, 3L)),
				passed,
				"stale_sheet_recovery"
		);
		assertNoOp(
				CampaignReducer.reduce(Optional.of(passed), new CampaignEvent.RecoverSheet(OTHER_OWNER, 4L)),
				passed,
				"wrong_owner"
		);
		assertNoOp(
				CampaignReducer.reduce(Optional.of(failedState(OWNER, 4, null)), recover),
				failedState(OWNER, 4, null),
				"sheet_not_recoverable"
		);
	}

	@Test
	void remoteCooldownAndReadyNoticeNeverShortenOrReplay() {
		PlayerCampaignState passed = passedState(OWNER, 4, 1_200L, 2L, 1_000L);
		CampaignEvent.StartRemoteCooldown startCooldown = new CampaignEvent.StartRemoteCooldown(
				OWNER,
				1_200L,
				1_600L
		);

		CampaignTransition started = CampaignReducer.reduce(Optional.of(passed), startCooldown);
		assertAccepted(started, "remote_cooldown_started");
		PlayerCampaignState coolingDown = started.nextState().orElseThrow();
		assertEquals(1_600L, coolingDown.remoteCooldownUntilGameTime());
		assertEquals(1_000L, coolingDown.remoteReadyNoticeForDeadlineGameTime());
		assertEquals(List.of(new EffectIntent.ApplyRemoteCooldown(OWNER, 1_600L)), started.intents());
		assertMonotonicFieldsEqual(passed, coolingDown);

		assertNoOp(CampaignReducer.reduce(started.nextState(), startCooldown), coolingDown, "remote_on_cooldown");
		assertNoOp(
				CampaignReducer.reduce(Optional.of(coolingDown), new CampaignEvent.StartRemoteCooldown(OWNER, 1_600L, 1_500L)),
				coolingDown,
				"cooldown_deadline_not_monotonic"
		);
		assertNoOp(
				CampaignReducer.reduce(Optional.of(passed), new CampaignEvent.StartRemoteCooldown(OWNER, 1_199L, 1_599L)),
				passed,
				"remote_on_cooldown"
		);
		assertNoOp(
				CampaignReducer.reduce(Optional.of(passed), new CampaignEvent.StartRemoteCooldown(OTHER_OWNER, 1_200L, 1_600L)),
				passed,
				"wrong_owner"
		);

		CampaignEvent.RemoteReadyNotice ready = new CampaignEvent.RemoteReadyNotice(OWNER, 1_600L, 1_600L);
		CampaignTransition noticed = CampaignReducer.reduce(Optional.of(coolingDown), ready);
		assertAccepted(noticed, "remote_ready_noticed");
		PlayerCampaignState readyState = noticed.nextState().orElseThrow();
		assertEquals(1_600L, readyState.remoteCooldownUntilGameTime());
		assertEquals(1_600L, readyState.remoteReadyNoticeForDeadlineGameTime());
		assertEquals(List.of(new EffectIntent.NotifyRemoteReady(OWNER, 1_600L)), noticed.intents());

		assertNoOp(CampaignReducer.reduce(noticed.nextState(), ready), readyState, "remote_ready_already_noticed");
		assertNoOp(
				CampaignReducer.reduce(Optional.of(coolingDown), new CampaignEvent.RemoteReadyNotice(OWNER, 1_599L, 1_600L)),
				coolingDown,
				"stale_cooldown_deadline"
		);
		assertNoOp(
				CampaignReducer.reduce(Optional.of(coolingDown), new CampaignEvent.RemoteReadyNotice(OWNER, 1_600L, 1_599L)),
				coolingDown,
				"remote_not_ready"
		);
		assertNoOp(
				CampaignReducer.reduce(Optional.of(coolingDown), new CampaignEvent.RemoteReadyNotice(OTHER_OWNER, 1_600L, 1_600L)),
				coolingDown,
				"wrong_owner"
		);
	}

	@Test
	void transitionFactoriesAreImmutableAndNoOpCannotCarryEffects() {
		PlayerCampaignState state = failedState(OWNER, 1, null);
		List<EffectIntent> mutable = new ArrayList<>();
		mutable.add(new EffectIntent.ReconcileRetake(OWNER));
		CampaignTransition accepted = CampaignTransition.accepted(state, "test_accepted", mutable);
		mutable.clear();

		assertTrue(accepted.accepted());
		assertEquals(List.of(new EffectIntent.ReconcileRetake(OWNER)), accepted.intents());
		assertThrows(UnsupportedOperationException.class, () -> accepted.intents().clear());
		CampaignTransition noOp = CampaignTransition.noOp(Optional.of(state), "test_no_op");
		assertFalse(noOp.accepted());
		assertEquals(Optional.of(state), noOp.nextState());
		assertTrue(noOp.intents().isEmpty());
		assertThrows(NullPointerException.class, () -> CampaignReducer.reduce(null, startEvent(OWNER, ENCOUNTER, PROFESSOR)));
		assertThrows(NullPointerException.class, () -> CampaignReducer.reduce(Optional.empty(), null));
	}

	private static CampaignEvent.Start startEvent(UUID ownerUuid, UUID encounterUuid, UUID professorUuid) {
		return new CampaignEvent.Start(
				ownerUuid,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				DESK,
				Direction.NORTH,
				RETRY,
				encounterUuid,
				professorUuid
		);
	}

	private static PlayerCampaignState activeState(UUID ownerUuid, UUID encounterUuid, UUID professorUuid, int attempt) {
		return state(
				ownerUuid,
				PlayerCampaignState.CampaignChapter.PRE_LECTURE,
				PlayerCampaignState.LectureStatus.ACTIVE,
				attempt,
				new PlayerCampaignState.EncounterRef(ownerUuid, encounterUuid, professorUuid, attempt),
				false,
				false,
				false,
				null,
				900L,
				2L,
				800L
		);
	}

	private static PlayerCampaignState failedState(UUID ownerUuid, int attempt, UUID fallbackUuid) {
		return state(
				ownerUuid,
				PlayerCampaignState.CampaignChapter.PRE_LECTURE,
				PlayerCampaignState.LectureStatus.RETAKE_READY,
				attempt,
				null,
				false,
				false,
				true,
				fallbackUuid,
				900L,
				2L,
				800L
		);
	}

	private static PlayerCampaignState passedState(
			UUID ownerUuid,
			int attempt,
			long cooldownDeadline,
			long sheetRecoverySequence,
			long readyNoticeDeadline
	) {
		return state(
				ownerUuid,
				PlayerCampaignState.CampaignChapter.LECTURE_PASSED,
				PlayerCampaignState.LectureStatus.PASSED,
				attempt,
				null,
				true,
				true,
				false,
				null,
				cooldownDeadline,
				sheetRecoverySequence,
				readyNoticeDeadline
		);
	}

	private static PlayerCampaignState state(
			UUID ownerUuid,
			PlayerCampaignState.CampaignChapter chapter,
			PlayerCampaignState.LectureStatus status,
			int attemptCount,
			PlayerCampaignState.EncounterRef encounter,
			boolean sheetEntitled,
			boolean remoteIssued,
			boolean retakeEntitled,
			UUID fallbackUuid,
			long cooldownDeadline,
			long sheetRecoverySequence,
			long readyNoticeDeadline
	) {
		return new PlayerCampaignState(
				ownerUuid,
				chapter,
				status,
				attemptCount,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				DESK,
				Direction.NORTH,
				RETRY,
				encounter,
				sheetEntitled,
				remoteIssued,
				retakeEntitled,
				fallbackUuid,
				cooldownDeadline,
				sheetRecoverySequence,
				readyNoticeDeadline
		);
	}

	private static void assertAccepted(CampaignTransition transition, String reason) {
		assertTrue(transition.accepted(), () -> "expected accepted transition; seed=" + EVENT_ORDER_SEED);
		assertEquals(reason, transition.reason());
		assertTrue(transition.nextState().isPresent());
	}

	private static void assertNoOp(
			CampaignTransition transition,
			PlayerCampaignState expectedState,
			String expectedReason
	) {
		assertFalse(transition.accepted(), () -> "expected no-op transition; seed=" + EVENT_ORDER_SEED);
		assertEquals(Optional.of(expectedState), transition.nextState());
		assertEquals(expectedReason, transition.reason());
		assertTrue(transition.intents().isEmpty());
	}

	private static void assertMonotonicFieldsEqual(PlayerCampaignState before, PlayerCampaignState after) {
		assertEquals(before.chapter(), after.chapter());
		assertEquals(before.attemptCount(), after.attemptCount());
		assertEquals(before.sheetEntitled(), after.sheetEntitled());
		assertEquals(before.remoteIssued(), after.remoteIssued());
		assertTrue(after.remoteCooldownUntilGameTime() >= before.remoteCooldownUntilGameTime());
		assertTrue(after.sheetRecoverySequence() >= before.sheetRecoverySequence());
		assertTrue(after.remoteReadyNoticeForDeadlineGameTime() >= before.remoteReadyNoticeForDeadlineGameTime());
	}
}
