package dev.developershell.python;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RecursionCooldownTest {
	@Test void idleIsReadyAtZero() { assertTrue(RecursionCooldown.idle().ready(0)); }
	@Test void activeCooldownRejectsBeforeDeadline() { assertFalse(new RecursionCooldown(10).ready(9)); }
	@Test void cooldownAllowsExactDeadline() { assertTrue(new RecursionCooldown(10).ready(10)); }
	@Test void remainingClampsAtZero() { assertEquals(0, new RecursionCooldown(10).remaining(11)); }
	@Test void admittedUseStartsExactDeadline() {
		var result = RecursionCooldown.idle().admit(40, 20);
		assertTrue(result.admitted());
		assertEquals(60, result.cooldown().untilTick());
	}
	@Test void rejectedReentryPreservesDeadline() {
		RecursionCooldown cooldown = new RecursionCooldown(60);
		var result = cooldown.admit(59, 20);
		assertFalse(result.admitted());
		assertEquals(cooldown, result.cooldown());
	}
	@Test void restoredFutureDeadlineIsClamped() {
		assertEquals(1_300, RecursionCooldown.restoreClamped(Long.MAX_VALUE, 100).untilTick());
	}
	@Test void restoredNegativeDeadlineBecomesIdle() { assertEquals(0, RecursionCooldown.restoreClamped(-50, 100).untilTick()); }
	@Test void negativeGameTickIsRejected() { assertThrows(IllegalArgumentException.class, () -> RecursionCooldown.idle().ready(-1)); }
	@Test void zeroDurationIsRejected() { assertThrows(IllegalArgumentException.class, () -> RecursionCooldown.idle().admit(0, 0)); }
	@Test void excessiveDurationIsRejected() { assertThrows(IllegalArgumentException.class, () -> RecursionCooldown.idle().admit(0, 1_201)); }
	@Test void negativeDeadlineConstructorIsRejected() { assertThrows(IllegalArgumentException.class, () -> new RecursionCooldown(-1)); }
}
