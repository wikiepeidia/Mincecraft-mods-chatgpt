package dev.developershell.bossrush;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class BossRushProgressTest {
	private static final UUID OWNER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

	@Test
	void legalChainAdvancesAndRecordsEachFirstClearOnce() {
		BossRushProgress jury = BossRushProgress.initial(OWNER).begin(BossRushStage.JURY);
		BossRushProgress.Completion juryClear = jury.complete(BossRushStage.JURY);
		assertTrue(juryClear.firstClear());
		assertEquals(BossRushStage.READY_CHAIRMAN, juryClear.progress().stage());

		BossRushProgress chairman = juryClear.progress().begin(BossRushStage.CHAIRMAN);
		BossRushProgress.Completion chairmanClear = chairman.complete(BossRushStage.CHAIRMAN);
		assertTrue(chairmanClear.firstClear());
		assertEquals(BossRushStage.SPONSOR, chairmanClear.progress().stage());

		BossRushProgress codex = chairmanClear.progress().begin(BossRushStage.CODEX);
		BossRushProgress.Completion codexClear = codex.complete(BossRushStage.CODEX);
		assertTrue(codexClear.firstClear());
		assertEquals(BossRushStage.GRADUATED, codexClear.progress().stage());
		assertTrue(codexClear.progress().diplomaGranted());
	}

	@Test
	void illegalTransitionsFailClosed() {
		BossRushProgress initial = BossRushProgress.initial(OWNER);
		assertThrows(IllegalStateException.class, () -> initial.begin(BossRushStage.CHAIRMAN));
		assertThrows(IllegalStateException.class, () -> initial.complete(BossRushStage.JURY));
		assertThrows(IllegalArgumentException.class, () -> new BossRushProgress(
				OWNER, BossRushStage.GRADUATED, true, true, false));
	}

	@Test
	void restartNormalizationUsesLastDurableCheckpoint() {
		assertEquals(BossRushStage.READY_JURY,
				BossRushProgress.initial(OWNER).begin(BossRushStage.JURY).normalizeRestart().stage());
		BossRushProgress juryCleared = BossRushProgress.initial(OWNER)
				.begin(BossRushStage.JURY).complete(BossRushStage.JURY).progress();
		assertEquals(BossRushStage.READY_CHAIRMAN,
				juryCleared.begin(BossRushStage.CHAIRMAN).normalizeRestart().stage());
		BossRushProgress chairmanCleared = juryCleared.begin(BossRushStage.CHAIRMAN)
				.complete(BossRushStage.CHAIRMAN).progress();
		assertEquals(BossRushStage.SPONSOR,
				chairmanCleared.begin(BossRushStage.CODEX).normalizeRestart().stage());
	}

	@Test
	void existingRewardFlagsMakeReplayedCompletionProgressionNeutral() {
		BossRushProgress replay = new BossRushProgress(
				OWNER, BossRushStage.JURY, true, true, true);
		BossRushProgress.Completion result = replay.complete(BossRushStage.JURY);
		assertFalse(result.firstClear());
		assertTrue(result.progress().juryCleared());
		assertTrue(result.progress().diplomaGranted());
	}
}
