package dev.developershell.bossrush;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class BossRushRuntimeStateTest {
	@Test
	void ownershipIsExactAndEntityTrackingIsDeduplicated() {
		UUID owner = UUID.randomUUID();
		UUID entity = UUID.randomUUID();
		BossRushRuntimeState state = new BossRushRuntimeState(owner, UUID.randomUUID());
		state.track(entity);
		state.track(entity);
		assertTrue(state.isOwner(owner));
		assertFalse(state.isOwner(UUID.randomUUID()));
		assertEquals(Set.of(entity), state.ownedEntityUuids());
	}

	@Test
	void runtimeTimerIsBounded() {
		BossRushRuntimeState state = new BossRushRuntimeState(UUID.randomUUID(), UUID.randomUUID());
		assertFalse(state.advance(BossRushRuntimeState.MAX_RUNTIME_TICKS - 1));
		assertTrue(state.advance(1));
		assertTrue(state.advance(0));
		assertThrows(IllegalArgumentException.class, () -> state.advance(-1));
	}

	@Test
	void cleanupIsIdempotentAndClosesTheLedger() {
		UUID entity = UUID.randomUUID();
		BossRushRuntimeState state = new BossRushRuntimeState(UUID.randomUUID(), UUID.randomUUID());
		state.track(entity);
		assertEquals(Set.of(entity), state.close());
		assertEquals(Set.of(), state.close());
		assertTrue(state.closed());
		assertThrows(IllegalStateException.class, () -> state.track(UUID.randomUUID()));
	}
}
