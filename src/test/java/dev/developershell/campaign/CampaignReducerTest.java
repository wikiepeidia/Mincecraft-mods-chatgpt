package dev.developershell.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.developershell.campaign.CampaignEvent.EncounterTerminal;
import dev.developershell.campaign.CampaignEvent.FallbackOperation;
import dev.developershell.campaign.CampaignEvent.TerminalReason;
import dev.developershell.campaign.CampaignTransition.EffectIntent;
import dev.developershell.lecture.ArenaValidationResult;
import dev.developershell.lecture.LectureGeometry;
import dev.developershell.lecture.RetakeService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

final class CampaignReducerTest {
	private static final UUID OWNER = UUID.fromString("c0de0000-0000-4000-8000-000000000401");
	private static final UUID OTHER_OWNER = UUID.fromString("c0de0000-0000-4000-8000-000000000402");
	private static final UUID ENCOUNTER = UUID.fromString("c0de0000-0000-4000-8000-000000000411");
	private static final UUID OTHER_ENCOUNTER = UUID.fromString("c0de0000-0000-4000-8000-000000000412");
	private static final UUID STALE_ENCOUNTER = UUID.fromString("c0de0000-0000-4000-8000-000000000413");
	private static final UUID PROFESSOR = UUID.fromString("c0de0000-0000-4000-8000-000000000421");
	private static final UUID OTHER_PROFESSOR = UUID.fromString("c0de0000-0000-4000-8000-000000000422");
	private static final UUID FALLBACK = UUID.fromString("c0de0000-0000-4000-8000-000000000431");
	private static final UUID OTHER_FALLBACK = UUID.fromString("c0de0000-0000-4000-8000-000000000432");
	private static final UUID THIRD_FALLBACK = UUID.fromString("c0de0000-0000-4000-8000-000000000433");
	private static final BlockPos DESK = new BlockPos(40, 72, -12);
	private static final BlockPos OTHER_DESK = new BlockPos(41, 72, -12);
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
				ENCOUNTER,
				null,
				FALLBACK,
				900L,
				2L,
				800L
		);
		CampaignEvent.Start retryStart = retakeStartEvent(
				OWNER,
				OTHER_ENCOUNTER,
				OTHER_PROFESSOR,
				retake.retakeKey().orElseThrow()
		);
		CampaignTransition retried = CampaignReducer.reduce(Optional.of(retake), retryStart);
		assertAccepted(retried, "start_accepted");
		PlayerCampaignState retriedState = retried.nextState().orElseThrow();
		assertEquals(4, retriedState.attemptCount());
		assertEquals(new PlayerCampaignState.EncounterRef(OWNER, OTHER_ENCOUNTER, OTHER_PROFESSOR, 4), retriedState.activeEncounterRef());
		assertFalse(retriedState.retakeEntitled());
		assertTrue(retriedState.retakeKey().isEmpty());
		assertEquals(null, retriedState.retakeFallbackReservationUuid());
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
	void terminalAndReloadCreateOneEncounterBoundEntitlementAcrossEveryReplayOrder() {
		PlayerCampaignState active = activeState(OWNER, ENCOUNTER, PROFESSOR, 4);
		CampaignEvent.Terminal terminal = new CampaignEvent.Terminal(
				OWNER,
				ENCOUNTER,
				TerminalReason.DEATH
		);
		CampaignEvent.NormalizeReload reload = new CampaignEvent.NormalizeReload(OWNER, ENCOUNTER);

		CampaignTransition terminalFirst = CampaignReducer.reduce(Optional.of(active), terminal);
		assertAccepted(terminalFirst, "terminal_death");
		PlayerCampaignState terminalState = terminalFirst.nextState().orElseThrow();
		assertEquals(
				new PlayerCampaignState.RetakeKey(OWNER, ENCOUNTER),
				terminalState.retakeKey().orElseThrow()
		);
		assertNoOp(CampaignReducer.reduce(terminalFirst.nextState(), terminal), terminalState, "no_active_encounter");
		assertNoOp(CampaignReducer.reduce(terminalFirst.nextState(), reload), terminalState, "no_active_encounter");
		assertNoOp(
				CampaignReducer.reduce(
						terminalFirst.nextState(),
						new CampaignEvent.Terminal(OWNER, STALE_ENCOUNTER, TerminalReason.ENTITY_UNLOAD)
				),
				terminalState,
				"no_active_encounter"
		);

		CampaignTransition reloadFirst = CampaignReducer.reduce(Optional.of(active), reload);
		assertAccepted(reloadFirst, "reload_normalized");
		PlayerCampaignState reloadState = reloadFirst.nextState().orElseThrow();
		assertEquals(terminalState.retakeKey(), reloadState.retakeKey());
		assertNoOp(CampaignReducer.reduce(reloadFirst.nextState(), terminal), reloadState, "no_active_encounter");
		assertMonotonicFieldsEqual(active, reloadState);
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
			assertEquals(
					new PlayerCampaignState.RetakeKey(OWNER, ENCOUNTER),
					failed.retakeKey().orElseThrow(),
					reason.serializedName()
			);
			assertEquals(null, failed.retakeFallbackReservationUuid(), reason.serializedName());
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
		assertEquals(new PlayerCampaignState.RetakeKey(OWNER, ENCOUNTER), normalized.retakeKey().orElseThrow());
		assertEquals(null, normalized.retakeFallbackReservationUuid());
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
		assertTrue(passed.retakeKey().isEmpty());
		assertEquals(null, passed.retakeFallbackReservationUuid());
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
	void everyFailureAndReloadRaceVictoryWithTheFirstPersistedTerminalWinning() {
		CampaignEvent.Victory victory = new CampaignEvent.Victory(OWNER, ENCOUNTER);
		List<TerminalCase> competingTerminals = competingTerminals();
		assertEquals(TerminalReason.values().length + 1, competingTerminals.size());

		for (TerminalCase terminalCase : competingTerminals) {
			PlayerCampaignState active = activeState(OWNER, ENCOUNTER, PROFESSOR, 5);

			CampaignTransition victoryFirst = CampaignReducer.reduce(Optional.of(active), victory);
			assertAccepted(victoryFirst, "victory_accepted");
			PlayerCampaignState passed = victoryFirst.nextState().orElseThrow();
			assertEquals(PlayerCampaignState.LectureStatus.PASSED, passed.status(), terminalCase.name());
			assertTrue(passed.sheetEntitled(), terminalCase.name());
			assertTrue(passed.remoteIssued(), terminalCase.name());
			assertEquals(List.of(
					new EffectIntent.CleanupEncounter(OWNER, ENCOUNTER, "victory"),
					new EffectIntent.GrantFirstRewards(OWNER)
			), victoryFirst.intents(), terminalCase.name());
			assertNoOp(
					CampaignReducer.reduce(victoryFirst.nextState(), terminalCase.event()),
					passed,
					"no_active_encounter"
			);

			CampaignTransition failureFirst = CampaignReducer.reduce(
					Optional.of(active),
					terminalCase.event()
			);
			assertAccepted(failureFirst, terminalCase.acceptedReason());
			PlayerCampaignState failed = failureFirst.nextState().orElseThrow();
			assertEquals(PlayerCampaignState.LectureStatus.RETAKE_READY, failed.status(), terminalCase.name());
			assertFalse(failed.sheetEntitled(), terminalCase.name());
			assertFalse(failed.remoteIssued(), terminalCase.name());
			assertEquals(
					new PlayerCampaignState.RetakeKey(OWNER, ENCOUNTER),
					failed.retakeKey().orElseThrow(),
					terminalCase.name()
			);
			assertNoOp(
					CampaignReducer.reduce(failureFirst.nextState(), victory),
					failed,
					"no_active_encounter"
			);
		}
	}

	@Test
	void servicePersistsVictoryBeforeEffectsAndSuppressesLateReconciliation() {
		PlayerCampaignState active = activeState(OWNER, ENCOUNTER, PROFESSOR, 5);
		CampaignSavedData data = CampaignSavedData.createForTesting(java.util.Map.of(OWNER, active));
		CampaignEvent.Victory victory = new CampaignEvent.Victory(OWNER, ENCOUNTER);
		List<EffectIntent> dispatched = new ArrayList<>();
		List<EffectIntent> reentrant = new ArrayList<>();

		CampaignTransition accepted = CampaignService.applyTerminal(data, victory, effect -> {
			PlayerCampaignState persisted = data.player(OWNER).orElseThrow();
			assertEquals(PlayerCampaignState.LectureStatus.PASSED, persisted.status());
			assertTrue(persisted.sheetEntitled());
			assertTrue(persisted.remoteIssued());
			dispatched.add(effect);
			if (effect instanceof EffectIntent.CleanupEncounter) {
				CampaignTransition lateCleanup = CampaignService.applyTerminal(
						data,
						new CampaignEvent.Terminal(OWNER, ENCOUNTER, TerminalReason.ENTITY_UNLOAD),
						reentrant::add
				);
				assertNoOp(lateCleanup, persisted, "no_active_encounter");
			}
		});

		assertAccepted(accepted, "victory_accepted");
		PlayerCampaignState pending = accepted.nextState().orElseThrow();
		assertTrue(pending.sheetProjectionPending());
		assertTrue(pending.remoteProjectionPending());
		assertEquals(ENCOUNTER, pending.remoteProjectionUuid());
		assertEquals(List.of(
				new EffectIntent.CleanupEncounter(OWNER, ENCOUNTER, "victory"),
				new EffectIntent.GrantFirstRewards(OWNER)
		), dispatched);
		assertTrue(reentrant.isEmpty());
		CampaignTransition sheetConfirmed = CampaignService.apply(
				data,
				new CampaignEvent.ConfirmSheetProjection(OWNER, pending.sheetRecoverySequence()),
				ignored -> {
				}
		);
		assertAccepted(sheetConfirmed, "sheet_projection_confirmed");
		assertFalse(sheetConfirmed.nextState().orElseThrow().sheetProjectionPending());
		assertTrue(sheetConfirmed.nextState().orElseThrow().remoteProjectionPending());
		CampaignTransition remoteConfirmed = CampaignService.apply(
				data,
				new CampaignEvent.ConfirmRemoteProjection(OWNER, ENCOUNTER),
				ignored -> {
				}
		);
		assertAccepted(remoteConfirmed, "remote_projection_confirmed");
		PlayerCampaignState passed = remoteConfirmed.nextState().orElseThrow();
		assertFalse(passed.sheetProjectionPending());
		assertFalse(passed.remoteProjectionPending());

		assertNoOp(
				CampaignService.applyTerminal(data, victory, dispatched::add),
				passed,
				"no_active_encounter"
		);
		assertEquals(2, dispatched.size());

		PlayerCampaignState.RetakeKey staleKey = new PlayerCampaignState.RetakeKey(OWNER, ENCOUNTER);
		assertNoOp(
				CampaignService.apply(
						data,
						new CampaignEvent.ReconcileRetake(OWNER, staleKey, FALLBACK),
						dispatched::add
				),
				passed,
				"retake_not_entitled"
		);
		assertNoOp(
				CampaignService.apply(
						data,
						new CampaignEvent.RetakeFallback(
								OWNER,
								staleKey,
								FALLBACK,
								FallbackOperation.MATERIALIZED
						),
						dispatched::add
				),
				passed,
				"retake_not_entitled"
		);

		CampaignEvent.RecoverSheet recover = new CampaignEvent.RecoverSheet(
				OWNER,
				passed.sheetRecoverySequence()
		);
		List<EffectIntent> recoveryEffects = new ArrayList<>();
		CampaignTransition recovered = CampaignService.apply(data, recover, effect -> {
			assertEquals(
					passed.sheetRecoverySequence() + 1L,
					data.player(OWNER).orElseThrow().sheetRecoverySequence()
			);
			recoveryEffects.add(effect);
		});
		assertAccepted(recovered, "sheet_recovery_accepted");
		PlayerCampaignState recoveredState = recovered.nextState().orElseThrow();
		assertEquals(
				List.of(new EffectIntent.RecoverAttendanceSheet(OWNER, recoveredState.sheetRecoverySequence())),
				recoveryEffects
		);
		assertNoOp(
				CampaignService.apply(data, recover, recoveryEffects::add),
				recoveredState,
				"stale_sheet_recovery"
		);
		assertEquals(1, recoveryEffects.size());

		CampaignTransition wrongOwner = CampaignService.apply(
				data,
				new CampaignEvent.RecoverSheet(OTHER_OWNER, recoveredState.sheetRecoverySequence()),
				recoveryEffects::add
		);
		assertFalse(wrongOwner.accepted());
		assertEquals(Optional.empty(), wrongOwner.nextState());
		assertEquals("missing_state", wrongOwner.reason());
		assertTrue(wrongOwner.intents().isEmpty());
		assertEquals(1, recoveryEffects.size());
	}

	@Test
	void firstRewardProjectionConfirmationsAreIndependentAndReplaySafe() {
		PlayerCampaignState active = activeState(OWNER, ENCOUNTER, PROFESSOR, 5);
		CampaignTransition victory = CampaignReducer.reduce(
				Optional.of(active), new CampaignEvent.Victory(OWNER, ENCOUNTER)
		);
		assertAccepted(victory, "victory_accepted");
		PlayerCampaignState pending = victory.nextState().orElseThrow();
		assertTrue(pending.sheetProjectionPending());
		assertTrue(pending.remoteProjectionPending());
		assertEquals(ENCOUNTER, pending.remoteProjectionUuid());
		assertNoOp(
				CampaignReducer.reduce(
						Optional.of(pending),
						new CampaignEvent.ConfirmSheetProjection(OWNER, pending.sheetRecoverySequence() + 1L)
				),
				pending,
				"stale_sheet_projection"
		);
		assertNoOp(
				CampaignReducer.reduce(
						Optional.of(pending),
						new CampaignEvent.ConfirmRemoteProjection(OWNER, STALE_ENCOUNTER)
				),
				pending,
				"stale_remote_projection"
		);
		assertNoOp(
				CampaignReducer.reduce(
						Optional.of(pending),
						new CampaignEvent.RecoverSheet(OWNER, pending.sheetRecoverySequence())
				),
				pending,
				"sheet_projection_pending"
		);
		assertNoOp(
				CampaignReducer.reduce(
						Optional.of(pending),
						new CampaignEvent.StartRemoteCooldown(OWNER, 1_000L, 1_400L)
				),
				pending,
				"remote_not_entitled"
		);
		assertNoOp(
				CampaignReducer.reduce(
						Optional.of(pending),
						new CampaignEvent.RemoteReadyNotice(OWNER, 0L, 1_000L)
				),
				pending,
				"remote_not_entitled"
		);

		CampaignTransition remote = CampaignReducer.reduce(
				Optional.of(pending), new CampaignEvent.ConfirmRemoteProjection(OWNER, ENCOUNTER)
		);
		assertAccepted(remote, "remote_projection_confirmed");
		PlayerCampaignState sheetOnlyPending = remote.nextState().orElseThrow();
		assertTrue(sheetOnlyPending.sheetProjectionPending());
		assertFalse(sheetOnlyPending.remoteProjectionPending());
		assertNoOp(
				CampaignReducer.reduce(
						Optional.of(sheetOnlyPending),
						new CampaignEvent.ConfirmRemoteProjection(OWNER, ENCOUNTER)
				),
				sheetOnlyPending,
				"remote_projection_not_pending"
		);

		CampaignTransition sheet = CampaignReducer.reduce(
				Optional.of(sheetOnlyPending),
				new CampaignEvent.ConfirmSheetProjection(OWNER, pending.sheetRecoverySequence())
		);
		assertAccepted(sheet, "sheet_projection_confirmed");
		PlayerCampaignState complete = sheet.nextState().orElseThrow();
		assertFalse(complete.sheetProjectionPending());
		assertFalse(complete.remoteProjectionPending());
		assertNoOp(
				CampaignReducer.reduce(
						Optional.of(complete),
						new CampaignEvent.ConfirmSheetProjection(OWNER, complete.sheetRecoverySequence())
				),
				complete,
				"sheet_projection_not_pending"
		);
	}

	@Test
	void legacyRemoteAbsencePersistsNormalPendingBeforeProjectionAndCannotReplay() {
		UUID projectionUuid = PlayerCampaignState.legacyRemoteProjectionUuid(OWNER, DESK, 4);
		PlayerCampaignState legacy = new PlayerCampaignState(
				OWNER,
				PlayerCampaignState.CampaignChapter.LECTURE_PASSED,
				PlayerCampaignState.LectureStatus.PASSED,
				4,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				DESK,
				Direction.NORTH,
				RETRY,
				null,
				true,
				true,
				false,
				null,
				null,
				null,
				0L,
				0L,
				0L,
				false,
				true,
				projectionUuid,
				true
		);
		assertNoOp(
				CampaignReducer.reduce(
						Optional.of(legacy),
						new CampaignEvent.ResolveLegacyRemoteAbsence(OWNER, STALE_ENCOUNTER)
				),
				legacy,
				"legacy_remote_not_pending"
		);

		CampaignEvent.ResolveLegacyRemoteAbsence resolve =
				new CampaignEvent.ResolveLegacyRemoteAbsence(OWNER, projectionUuid);
		CampaignTransition resolved = CampaignReducer.reduce(Optional.of(legacy), resolve);
		assertAccepted(resolved, "legacy_remote_absence_resolved");
		PlayerCampaignState normalPending = resolved.nextState().orElseThrow();
		assertTrue(normalPending.remoteProjectionPending());
		assertFalse(normalPending.legacyRemoteAdoptionPending());
		assertEquals(projectionUuid, normalPending.remoteProjectionUuid());
		assertNoOp(
				CampaignReducer.reduce(Optional.of(normalPending), resolve),
				normalPending,
				"legacy_remote_not_pending"
		);

		CampaignTransition confirmed = CampaignReducer.reduce(
				Optional.of(normalPending),
				new CampaignEvent.ConfirmRemoteProjection(OWNER, projectionUuid)
		);
		assertAccepted(confirmed, "remote_projection_confirmed");
		assertFalse(confirmed.nextState().orElseThrow().remoteProjectionPending());
		assertFalse(confirmed.nextState().orElseThrow().legacyRemoteAdoptionPending());
	}

	@Test
	void retakeFallbackReservationAndLossAreReplaySafe() {
		PlayerCampaignState failed = failedState(OWNER, 3, null);
		PlayerCampaignState.RetakeKey key = failed.retakeKey().orElseThrow();
		CampaignEvent.ReconcileRetake reconcile = new CampaignEvent.ReconcileRetake(OWNER, key, FALLBACK);

		CampaignTransition reserved = CampaignReducer.reduce(Optional.of(failed), reconcile);
		assertAccepted(reserved, "retake_fallback_reserved");
		PlayerCampaignState withReservation = reserved.nextState().orElseThrow();
		assertEquals(FALLBACK, withReservation.retakeFallbackReservationUuid());
		assertEquals(null, withReservation.retakeFallbackEntityUuid());
		assertEquals(List.of(new EffectIntent.MaterializeRetakeFallback(OWNER, FALLBACK)), reserved.intents());
		assertMonotonicFieldsEqual(failed, withReservation);

		assertNoOp(CampaignReducer.reduce(reserved.nextState(), reconcile), withReservation, "retake_representation_exists");
		assertNoOp(
				CampaignReducer.reduce(
						Optional.of(failed),
						new CampaignEvent.ReconcileRetake(
								OWNER,
								new PlayerCampaignState.RetakeKey(OWNER, STALE_ENCOUNTER),
								FALLBACK
						)
				),
				failed,
				"wrong_retake"
		);

		CampaignEvent.RetakeFallback materialized = new CampaignEvent.RetakeFallback(
				OWNER,
				key,
				FALLBACK,
				FallbackOperation.MATERIALIZED
		);
		CampaignTransition committed = CampaignReducer.reduce(Optional.of(withReservation), materialized);
		assertAccepted(committed, "retake_fallback_materialized");
		PlayerCampaignState withFallback = committed.nextState().orElseThrow();
		assertEquals(null, withFallback.retakeFallbackReservationUuid());
		assertEquals(FALLBACK, withFallback.retakeFallbackEntityUuid());
		assertTrue(committed.intents().isEmpty());
		assertNoOp(CampaignReducer.reduce(committed.nextState(), materialized), withFallback, "wrong_fallback");

		CampaignEvent.RetakeFallback lost = new CampaignEvent.RetakeFallback(
				OWNER,
				key,
				FALLBACK,
				FallbackOperation.LOST
		);
		CampaignTransition cleared = CampaignReducer.reduce(Optional.of(withFallback), lost);
		assertAccepted(cleared, "retake_fallback_lost");
		PlayerCampaignState recoverable = cleared.nextState().orElseThrow();
		assertEquals(null, recoverable.retakeFallbackReservationUuid());
		assertEquals(null, recoverable.retakeFallbackEntityUuid());
		assertTrue(recoverable.retakeEntitled());
		assertEquals(key, recoverable.retakeKey().orElseThrow());
		assertEquals(List.of(new EffectIntent.ReconcileRetake(OWNER)), cleared.intents());
		assertMonotonicFieldsEqual(withFallback, recoverable);

		assertNoOp(CampaignReducer.reduce(cleared.nextState(), lost), recoverable, "wrong_fallback");
		assertNoOp(
				CampaignReducer.reduce(Optional.of(withFallback), new CampaignEvent.RetakeFallback(
						OWNER,
						key,
						OTHER_FALLBACK,
						FallbackOperation.LOST
				)),
				withFallback,
				"wrong_fallback"
		);

		CampaignTransition replacementReserved = CampaignReducer.reduce(
				cleared.nextState(),
				new CampaignEvent.ReconcileRetake(OWNER, key, OTHER_FALLBACK)
		);
		assertAccepted(replacementReserved, "retake_fallback_reserved");
		PlayerCampaignState replacement = replacementReserved.nextState().orElseThrow();
		assertEquals(OTHER_FALLBACK, replacement.retakeFallbackReservationUuid());
		assertNoOp(CampaignReducer.reduce(Optional.of(replacement), lost), replacement, "wrong_fallback");

		CampaignTransition failedMaterialization = CampaignReducer.reduce(
				Optional.of(replacement),
				new CampaignEvent.RetakeFallback(
						OWNER,
						key,
						OTHER_FALLBACK,
						FallbackOperation.MATERIALIZATION_FAILED
				)
		);
		assertAccepted(failedMaterialization, "retake_fallback_materialization_failed");
		assertEquals(null, failedMaterialization.nextState().orElseThrow().retakeFallbackReservationUuid());
		assertEquals(List.of(new EffectIntent.ReconcileRetake(OWNER)), failedMaterialization.intents());
	}

	@Test
	void retryRequiresCurrentEntitlementAndMatchingDeskBeforeAtomicStart() {
		PlayerCampaignState failed = failedState(OWNER, 6, ENCOUNTER, FALLBACK);
		PlayerCampaignState.RetakeKey key = failed.retakeKey().orElseThrow();

		CampaignEvent.Start stale = retakeStartEvent(
				OWNER,
				OTHER_ENCOUNTER,
				OTHER_PROFESSOR,
				new PlayerCampaignState.RetakeKey(OWNER, STALE_ENCOUNTER)
		);
		assertNoOp(CampaignReducer.reduce(Optional.of(failed), stale), failed, "wrong_retake");

		CampaignEvent.Start wrongDesk = new CampaignEvent.Start(
				OWNER,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				OTHER_DESK,
				Direction.NORTH,
				RETRY,
				OTHER_ENCOUNTER,
				OTHER_PROFESSOR,
				key
		);
		assertNoOp(CampaignReducer.reduce(Optional.of(failed), wrongDesk), failed, "wrong_retake_desk");
		assertNoOp(
				CampaignReducer.reduce(Optional.of(failed), startEvent(OWNER, OTHER_ENCOUNTER, OTHER_PROFESSOR)),
				failed,
				"missing_retake_key"
		);

		CampaignTransition accepted = CampaignReducer.reduce(
				Optional.of(failed),
				retakeStartEvent(OWNER, OTHER_ENCOUNTER, OTHER_PROFESSOR, key)
		);
		assertAccepted(accepted, "start_accepted");
		PlayerCampaignState active = accepted.nextState().orElseThrow();
		assertEquals(7, active.attemptCount());
		assertEquals(PlayerCampaignState.LectureStatus.ACTIVE, active.status());
		assertTrue(active.retakeKey().isEmpty());
		assertEquals(null, active.retakeFallbackReservationUuid());
		assertEquals(null, active.retakeFallbackEntityUuid());
		assertEquals(failed.chapter(), active.chapter());
		assertEquals(failed.sheetEntitled(), active.sheetEntitled());
		assertEquals(failed.remoteIssued(), active.remoteIssued());
		assertEquals(failed.remoteCooldownUntilGameTime(), active.remoteCooldownUntilGameTime());
		assertEquals(failed.sheetRecoverySequence(), active.sheetRecoverySequence());
		assertEquals(
				failed.remoteReadyNoticeForDeadlineGameTime(),
				active.remoteReadyNoticeForDeadlineGameTime()
		);
		assertNoOp(CampaignReducer.reduce(accepted.nextState(), stale), active, "start_not_ready");
		assertNoOp(
				CampaignReducer.reduce(
						accepted.nextState(),
						new CampaignEvent.RetakeFallback(OWNER, key, FALLBACK, FallbackOperation.LOST)
				),
				active,
				"retake_not_entitled"
		);
	}

	@Test
	void retakeServicePrefersInventoryAndClearsDuplicateFallbackStateFirst() {
		PlayerCampaignState failed = failedState(OWNER, 3, FALLBACK);
		TestCampaignPort campaign = new TestCampaignPort(failed);
		TestRepresentationPort representations = new TestRepresentationPort(campaign);
		representations.inventoryForm = true;
		representations.fallbacks.add(FALLBACK);
		RetakeService service = new RetakeService(campaign, representations, () -> OTHER_FALLBACK);

		assertEquals(RetakeService.Outcome.ALREADY_PRESENT, service.reconcile(OWNER));
		PlayerCampaignState reconciled = campaign.state(OWNER).orElseThrow();
		assertTrue(reconciled.retakeEntitled());
		assertEquals(null, reconciled.retakeFallbackEntityUuid());
		assertTrue(representations.inventoryForm);
		assertFalse(representations.fallbacks.contains(FALLBACK));
		assertTrue(campaign.log.indexOf("apply:cleared") < campaign.log.indexOf("discard:state_first=true"));

		int writes = campaign.applyCount;
		assertEquals(RetakeService.Outcome.ALREADY_PRESENT, service.recover(OWNER));
		assertEquals(writes, campaign.applyCount);
		assertEquals(0, representations.insertAttempts);
	}

	@Test
	void retakeServiceReservesBeforeFallbackAndRecoversMaterializationFailure() {
		PlayerCampaignState failed = failedState(OWNER, 3, null);
		TestCampaignPort campaign = new TestCampaignPort(failed);
		TestRepresentationPort representations = new TestRepresentationPort(campaign);
		representations.insertSucceeds = false;
		representations.materializeSucceeds = false;
		List<UUID> fallbackIds = new ArrayList<>(List.of(FALLBACK, OTHER_FALLBACK));
		RetakeService service = new RetakeService(campaign, representations, () -> fallbackIds.remove(0));

		assertEquals(RetakeService.Outcome.MATERIALIZATION_FAILED, service.reconcile(OWNER));
		PlayerCampaignState recoverable = campaign.state(OWNER).orElseThrow();
		assertTrue(recoverable.retakeEntitled());
		assertEquals(null, recoverable.retakeFallbackReservationUuid());
		assertEquals(null, recoverable.retakeFallbackEntityUuid());
		assertTrue(campaign.log.indexOf("apply:reserve") < campaign.log.indexOf("materialize:reserved=true"));
		assertTrue(campaign.log.indexOf("materialize:reserved=true") < campaign.log.indexOf("apply:materialization_failed"));

		representations.materializeSucceeds = true;
		assertEquals(RetakeService.Outcome.FALLBACK_ISSUED, service.recover(OWNER));
		PlayerCampaignState represented = campaign.state(OWNER).orElseThrow();
		assertEquals(null, represented.retakeFallbackReservationUuid());
		assertEquals(OTHER_FALLBACK, represented.retakeFallbackEntityUuid());
		assertEquals(Set.of(OTHER_FALLBACK), representations.fallbacks);
		assertEquals(RetakeService.Outcome.ALREADY_PRESENT, service.reconcile(OWNER));
		assertEquals(2, representations.insertAttempts);
		assertEquals(2, representations.materializeAttempts);
	}

	@Test
	void retakeServiceReplacesOneLostFallbackWithoutChangingProgression() {
		PlayerCampaignState failed = failedState(OWNER, 5, FALLBACK);
		TestCampaignPort campaign = new TestCampaignPort(failed);
		TestRepresentationPort representations = new TestRepresentationPort(campaign);
		representations.insertSucceeds = true;
		RetakeService service = new RetakeService(campaign, representations, () -> OTHER_FALLBACK);

		assertEquals(RetakeService.Outcome.INVENTORY_ISSUED, service.recover(OWNER));
		PlayerCampaignState recovered = campaign.state(OWNER).orElseThrow();
		assertMonotonicFieldsEqual(failed, recovered);
		assertTrue(recovered.retakeEntitled());
		assertEquals(null, recovered.retakeFallbackEntityUuid());
		assertTrue(representations.inventoryForm);
		assertTrue(campaign.log.indexOf("apply:lost") < campaign.log.indexOf("insert:state_entitled=true"));

		int writes = campaign.applyCount;
		assertEquals(RetakeService.Outcome.ALREADY_PRESENT, service.recover(OWNER));
		assertEquals(writes, campaign.applyCount);
		assertEquals(1, representations.insertAttempts);
	}

	@Test
	void retakeServicePersistsAcceptedStartBeforePhysicalClearAndRejectsWithoutEffects() {
		PlayerCampaignState failed = failedState(OWNER, 6, FALLBACK);
		TestCampaignPort campaign = new TestCampaignPort(failed);
		TestRepresentationPort representations = new TestRepresentationPort(campaign);
		representations.inventoryForm = true;
		representations.fallbacks.add(FALLBACK);
		RetakeService service = new RetakeService(campaign, representations, () -> OTHER_FALLBACK);
		List<EffectIntent> runtimeIntents = new ArrayList<>();

		assertEquals(
				RetakeService.Outcome.RETRY_REJECTED,
				service.startRetake(
						OWNER,
						acceptedArena(OTHER_DESK),
						OTHER_ENCOUNTER,
						OTHER_PROFESSOR,
						runtimeIntents::add
				)
		);
		assertTrue(runtimeIntents.isEmpty());
		assertTrue(representations.inventoryForm);
		assertTrue(representations.fallbacks.contains(FALLBACK));
		assertEquals(failed, campaign.state(OWNER).orElseThrow());

		assertEquals(
				RetakeService.Outcome.RETRY_ACCEPTED,
				service.startRetake(
						OWNER,
						acceptedArena(DESK),
						OTHER_ENCOUNTER,
						OTHER_PROFESSOR,
						runtimeIntents::add
				)
		);
		PlayerCampaignState active = campaign.state(OWNER).orElseThrow();
		assertEquals(PlayerCampaignState.LectureStatus.ACTIVE, active.status());
		assertEquals(7, active.attemptCount());
		assertFalse(active.retakeEntitled());
		assertEquals(List.of(new EffectIntent.StartEncounter(active.activeEncounterRef())), runtimeIntents);
		assertFalse(representations.inventoryForm);
		assertFalse(representations.fallbacks.contains(FALLBACK));
		assertTrue(campaign.log.indexOf("effect:start:persisted=true") < campaign.log.indexOf("consume:state_active=true"));
		assertTrue(campaign.log.indexOf("effect:start:persisted=true") < campaign.log.indexOf("discard:state_first=true"));
	}

	@Test
	void retakeServiceStartsFromMaterializedReservationAndClearsItAfterPersistence() {
		PlayerCampaignState reserved = state(
				OWNER,
				PlayerCampaignState.CampaignChapter.PRE_LECTURE,
				PlayerCampaignState.LectureStatus.RETAKE_READY,
				6,
				null,
				false,
				false,
				true,
				ENCOUNTER,
				FALLBACK,
				null,
				900L,
				2L,
				800L
		);
		TestCampaignPort campaign = new TestCampaignPort(reserved);
		TestRepresentationPort representations = new TestRepresentationPort(campaign);
		representations.fallbacks.add(FALLBACK);
		RetakeService service = new RetakeService(campaign, representations, () -> OTHER_FALLBACK);

		assertEquals(
				RetakeService.Outcome.RETRY_ACCEPTED,
				service.startRetake(
						OWNER,
						acceptedArena(DESK),
						OTHER_ENCOUNTER,
						OTHER_PROFESSOR,
						intent -> true
				)
		);
		PlayerCampaignState active = campaign.state(OWNER).orElseThrow();
		assertEquals(PlayerCampaignState.LectureStatus.ACTIVE, active.status());
		assertEquals(null, active.retakeFallbackReservationUuid());
		assertFalse(representations.fallbacks.contains(FALLBACK));
		assertTrue(campaign.log.indexOf("effect:start:persisted=true") < campaign.log.indexOf("discard:state_first=true"));
	}

	@Test
	void retakeServiceCompensatesRuntimeFailureBeforeReplacingTheForm() {
		PlayerCampaignState failed = failedState(OWNER, 6, null);
		TestCampaignPort campaign = new TestCampaignPort(failed);
		TestRepresentationPort representations = new TestRepresentationPort(campaign);
		representations.inventoryForm = true;
		representations.inventoryKey = failed.retakeKey().orElseThrow();
		representations.insertSucceeds = true;
		RetakeService service = new RetakeService(campaign, representations, () -> FALLBACK);
		List<EffectIntent> runtimeIntents = new ArrayList<>();

		assertEquals(
				RetakeService.Outcome.RUNTIME_START_FAILED,
				service.startRetake(
						OWNER,
						acceptedArena(DESK),
						OTHER_ENCOUNTER,
						OTHER_PROFESSOR,
						intent -> {
							runtimeIntents.add(intent);
							return false;
						}
				)
		);
		PlayerCampaignState compensated = campaign.state(OWNER).orElseThrow();
		assertEquals(PlayerCampaignState.LectureStatus.RETAKE_READY, compensated.status());
		assertEquals(7, compensated.attemptCount());
		assertEquals(new PlayerCampaignState.RetakeKey(OWNER, OTHER_ENCOUNTER), compensated.retakeKey().orElseThrow());
		assertEquals(compensated.retakeKey().orElseThrow(), representations.inventoryKey);
		assertTrue(representations.inventoryForm);
		assertEquals(1, representations.insertAttempts);
		assertEquals(1, runtimeIntents.size());
		assertTrue(campaign.log.indexOf("effect:start:persisted=true") < campaign.log.indexOf("apply:terminal"));
		assertTrue(campaign.log.indexOf("apply:terminal") < campaign.log.indexOf("insert:state_entitled=true"));
	}

	@Test
	void retakeServiceDiscardsMaterializedFallbackWhenCommitLosesItsKey() {
		PlayerCampaignState failed = failedState(OWNER, 3, null);
		PlayerCampaignState changed = failedState(OWNER, 4, STALE_ENCOUNTER, null);
		TestCampaignPort campaign = new TestCampaignPort(failed);
		TestRepresentationPort representations = new TestRepresentationPort(campaign);
		representations.insertSucceeds = false;
		representations.materializeSucceeds = true;
		campaign.beforeMaterializedCommit = () -> assertTrue(campaign.data.replace(changed));
		RetakeService service = new RetakeService(campaign, representations, () -> FALLBACK);

		assertEquals(RetakeService.Outcome.STALE_STATE, service.reconcile(OWNER));
		assertEquals(changed, campaign.state(OWNER).orElseThrow());
		assertFalse(representations.fallbacks.contains(FALLBACK));
		assertTrue(campaign.log.contains("discard:state_first=true"));
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

	private static List<TerminalCase> competingTerminals() {
		List<TerminalCase> events = new ArrayList<>();
		for (TerminalReason reason : TerminalReason.values()) {
			events.add(new TerminalCase(
					reason.serializedName(),
					new CampaignEvent.Terminal(OWNER, ENCOUNTER, reason),
					"terminal_" + reason.serializedName()
			));
		}
		events.add(new TerminalCase(
				"reload",
				new CampaignEvent.NormalizeReload(OWNER, ENCOUNTER),
				"reload_normalized"
		));
		return List.copyOf(events);
	}

	private record TerminalCase(String name, EncounterTerminal event, String acceptedReason) {
	}

	private static ArenaValidationResult.Accepted acceptedArena(BlockPos deskPos) {
		LectureGeometry.Layout layout = LectureGeometry.layout(deskPos, Direction.NORTH);
		return new ArenaValidationResult.Accepted(layout, layout.retryCandidates().get(0));
	}

	private static final class TestCampaignPort implements RetakeService.CampaignPort {
		private final CampaignSavedData data;
		private final List<String> log = new ArrayList<>();
		private int applyCount;
		private Runnable beforeMaterializedCommit;

		private TestCampaignPort(PlayerCampaignState initialState) {
			data = CampaignSavedData.createForTesting(java.util.Map.of(initialState.ownerUuid(), initialState));
		}

		@Override
		public Optional<PlayerCampaignState> state(UUID ownerUuid) {
			return data.player(ownerUuid);
		}

		@Override
		public CampaignTransition apply(CampaignEvent event, Consumer<EffectIntent> effectConsumer) {
			applyCount++;
			if (event instanceof CampaignEvent.RetakeFallback fallback
					&& fallback.operation() == FallbackOperation.MATERIALIZED
					&& beforeMaterializedCommit != null) {
				Runnable hook = beforeMaterializedCommit;
				beforeMaterializedCommit = null;
				hook.run();
			}
			if (event instanceof CampaignEvent.ReconcileRetake) {
				log.add("apply:reserve");
			}
			else if (event instanceof CampaignEvent.RetakeFallback fallback) {
				log.add("apply:" + fallback.operation().serializedName());
			}
			else if (event instanceof CampaignEvent.Start) {
				log.add("apply:start");
			}
			else if (event instanceof CampaignEvent.Terminal) {
				log.add("apply:terminal");
			}
			return CampaignService.apply(data, event, intent -> {
				if (intent instanceof EffectIntent.StartEncounter) {
					boolean persisted = data.player(event.ownerUuid())
							.map(state -> state.status() == PlayerCampaignState.LectureStatus.ACTIVE
									&& !state.retakeEntitled())
							.orElse(false);
					log.add("effect:start:persisted=" + persisted);
				}
				effectConsumer.accept(intent);
			});
		}
	}

	private static final class TestRepresentationPort implements RetakeService.RepresentationPort {
		private final TestCampaignPort campaign;
		private final Set<UUID> fallbacks = new HashSet<>();
		private boolean inventoryForm;
		private PlayerCampaignState.RetakeKey inventoryKey;
		private boolean insertSucceeds;
		private boolean materializeSucceeds;
		private int insertAttempts;
		private int materializeAttempts;

		private TestRepresentationPort(TestCampaignPort campaign) {
			this.campaign = campaign;
		}

		@Override
		public boolean hasInventoryForm(PlayerCampaignState.RetakeKey key) {
			return inventoryForm && (inventoryKey == null || inventoryKey.equals(key));
		}

		@Override
		public boolean hasFallback(PlayerCampaignState.RetakeKey key, UUID fallbackEntityUuid) {
			return fallbacks.contains(fallbackEntityUuid);
		}

		@Override
		public boolean tryInsertInventoryForm(PlayerCampaignState.RetakeKey key) {
			insertAttempts++;
			boolean entitled = campaign.state(key.ownerUuid()).flatMap(PlayerCampaignState::retakeKey)
					.filter(key::equals).isPresent();
			campaign.log.add("insert:state_entitled=" + entitled);
			if (insertSucceeds) {
				inventoryForm = true;
				inventoryKey = key;
			}
			return insertSucceeds;
		}

		@Override
		public boolean materializeFallback(
				PlayerCampaignState.RetakeKey key,
				UUID fallbackEntityUuid,
				BlockPos retryPos
		) {
			materializeAttempts++;
			boolean reserved = campaign.state(key.ownerUuid())
					.map(state -> fallbackEntityUuid.equals(state.retakeFallbackReservationUuid()))
					.orElse(false);
			campaign.log.add("materialize:reserved=" + reserved);
			if (materializeSucceeds) {
				fallbacks.add(fallbackEntityUuid);
			}
			return materializeSucceeds;
		}

		@Override
		public void consumeInventoryForm(PlayerCampaignState.RetakeKey key) {
			boolean active = campaign.state(key.ownerUuid())
					.map(state -> state.status() == PlayerCampaignState.LectureStatus.ACTIVE
							&& !state.retakeEntitled())
					.orElse(false);
			campaign.log.add("consume:state_active=" + active);
			inventoryForm = false;
			inventoryKey = null;
		}

		@Override
		public void discardFallback(PlayerCampaignState.RetakeKey key, UUID fallbackEntityUuid) {
			boolean stateFirst = campaign.state(key.ownerUuid())
					.map(state -> !fallbackEntityUuid.equals(state.retakeFallbackEntityUuid())
							&& !fallbackEntityUuid.equals(state.retakeFallbackReservationUuid()))
					.orElse(true);
			campaign.log.add("discard:state_first=" + stateFirst);
			fallbacks.remove(fallbackEntityUuid);
		}
	}

	private static CampaignEvent.Start retakeStartEvent(
			UUID ownerUuid,
			UUID encounterUuid,
			UUID professorUuid,
			PlayerCampaignState.RetakeKey retakeKey
	) {
		return new CampaignEvent.Start(
				ownerUuid,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				DESK,
				Direction.NORTH,
				RETRY,
				encounterUuid,
				professorUuid,
				retakeKey
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
				null,
				null,
				900L,
				2L,
				800L
		);
	}

	private static PlayerCampaignState failedState(UUID ownerUuid, int attempt, UUID fallbackUuid) {
		return failedState(ownerUuid, attempt, ENCOUNTER, fallbackUuid);
	}

	private static PlayerCampaignState failedState(
			UUID ownerUuid,
			int attempt,
			UUID failedEncounterUuid,
			UUID fallbackUuid
	) {
		return state(
				ownerUuid,
				PlayerCampaignState.CampaignChapter.PRE_LECTURE,
				PlayerCampaignState.LectureStatus.RETAKE_READY,
				attempt,
				null,
				false,
				false,
				true,
				failedEncounterUuid,
				null,
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
				null,
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
			UUID retakeEncounterUuid,
			UUID fallbackReservationUuid,
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
				retakeEncounterUuid,
				fallbackReservationUuid,
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
