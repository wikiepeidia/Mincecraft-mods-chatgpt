package dev.developershell.bossrush;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class BossRushRulesTest {
	@Test
	void encounterCountsAndTimersStayStrictlyBounded() {
		assertTrue(BossRushRules.JURY_EVIDENCE_TARGETS <= 2);
		assertTrue(BossRushRules.JURY_JURORS <= 3);
		assertTrue(BossRushRules.CHAIRMAN_RUBRIC_NODES <= 3);
		assertTrue(BossRushRules.CHAIRMAN_MINOR_REVISIONS <= 2);
		assertTrue(BossRushRules.CODEX_AGENTS <= 3);
		assertTrue(BossRushRules.CODEX_RING_PARTICLES <= 24);
		assertTrue(BossRushRules.CHAIRMAN_ACCEPTANCE_WINDOW_TICKS > 0);
		assertTrue(BossRushRules.CODEX_MAX_REASONING_WINDOW_TICKS > 0);
	}

	@Test
	void scopeAndAcceptanceGeometryIncludeTheirReadableBoundaries() {
		assertTrue(BossRushRules.insideScope(14.0D * 14.0D));
		assertFalse(BossRushRules.insideScope(14.01D * 14.01D));
		assertTrue(BossRushRules.insideAcceptancePad(4.0D * 4.0D));
		assertFalse(BossRushRules.insideAcceptancePad(4.01D * 4.01D));
	}

	@Test
	void contextOverflowUsesOneFiniteRingRatherThanUnboundedArea() {
		assertFalse(BossRushRules.insideOverflowRing(4.9D * 4.9D));
		assertTrue(BossRushRules.insideOverflowRing(5.0D * 5.0D));
		assertTrue(BossRushRules.insideOverflowRing(10.0D * 10.0D));
		assertFalse(BossRushRules.insideOverflowRing(10.1D * 10.1D));
	}

	@Test
	void unlockConditionsRequireEachNamedMechanic() {
		assertFalse(BossRushRules.juryJurorUnlocked(false, 0, 0));
		assertTrue(BossRushRules.juryJurorUnlocked(true, 1, 1));
		assertFalse(BossRushRules.juryJurorUnlocked(true, 1, 2));

		assertFalse(BossRushRules.chairmanCoreUnlocked(false, 0, false, false));
		assertTrue(BossRushRules.chairmanCoreUnlocked(true, 0, false, false));
		assertFalse(BossRushRules.chairmanCoreUnlocked(true, 2, true, false));
		assertTrue(BossRushRules.chairmanCoreUnlocked(true, 2, true, true));

		assertFalse(BossRushRules.codexCoreUnlocked(false, 0, false));
		assertTrue(BossRushRules.codexCoreUnlocked(true, 0, false));
		assertFalse(BossRushRules.codexCoreUnlocked(true, 1, false));
		assertTrue(BossRushRules.codexCoreUnlocked(true, 1, true));
	}
}
