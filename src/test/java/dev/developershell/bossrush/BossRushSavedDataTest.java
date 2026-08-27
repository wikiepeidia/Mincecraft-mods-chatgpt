package dev.developershell.bossrush;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class BossRushSavedDataTest {
	private static final UUID OWNER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
	private static final UUID OTHER = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

	@Test
	void codecRoundTripsStableStageAndFirstClearFlags() {
		BossRushProgress progress = new BossRushProgress(
				OWNER, BossRushStage.SPONSOR, true, true, false);
		BossRushSavedData original = BossRushSavedData.createForTesting(Map.of(OWNER, progress));
		JsonElement encoded = BossRushSavedData.TYPE.codec()
				.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
		BossRushSavedData decoded = BossRushSavedData.TYPE.codec()
				.parse(JsonOps.INSTANCE, encoded).getOrThrow();
		assertEquals(progress, decoded.snapshot(OWNER));
		assertEquals(BossRushStage.READY_JURY, decoded.snapshot(OTHER).stage());
	}

	@Test
	void mutationsAreOwnerIsolatedAndFirstClearIsIdempotent() {
		BossRushSavedData data = BossRushSavedData.createForTesting(Map.of());
		data.begin(OWNER, BossRushStage.JURY);
		BossRushProgress.Completion first = data.complete(OWNER, BossRushStage.JURY);
		assertTrue(first.firstClear());
		assertEquals(BossRushStage.READY_CHAIRMAN, data.snapshot(OWNER).stage());
		assertEquals(BossRushStage.READY_JURY, data.snapshot(OTHER).stage());

		data.replaceForGameTest(first.progress().withStage(BossRushStage.JURY));
		BossRushProgress.Completion replay = data.complete(OWNER, BossRushStage.JURY);
		assertFalse(replay.firstClear());
	}

	@Test
	void failedSpawnRollbackRequiresExactCurrentState() {
		BossRushSavedData data = BossRushSavedData.createForTesting(Map.of());
		BossRushProgress ready = data.snapshot(OWNER);
		BossRushProgress active = data.begin(OWNER, BossRushStage.JURY);
		assertFalse(data.restoreIfCurrent(OWNER, ready, active));
		assertTrue(data.restoreIfCurrent(OWNER, active, ready));
		assertEquals(ready, data.snapshot(OWNER));
		assertThrows(IllegalStateException.class, () -> data.begin(OWNER, BossRushStage.CODEX));
	}

	@Test
	void statusSnapshotNeverNormalizesAnActiveRuntime() {
		BossRushSavedData data = BossRushSavedData.createForTesting(Map.of());
		data.begin(OWNER, BossRushStage.JURY);
		data.setDirty(false);
		assertEquals(BossRushStage.JURY, data.snapshot(OWNER).stage());
		assertFalse(data.isDirty());
		assertEquals(BossRushStage.JURY, data.snapshot(OWNER).stage());
	}
}
