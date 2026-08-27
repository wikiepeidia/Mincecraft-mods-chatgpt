package dev.developershell.python;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PythonToolsSavedDataTest {
	private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@Test void absentOwnerHasIndependentInitialState() {
		PythonToolsSavedData data = PythonToolsSavedData.createForTesting(Map.of());
		assertEquals(PythonToolsState.initial(), data.snapshot(OWNER));
		assertEquals(PythonToolsState.initial(), data.snapshot(OTHER));
	}
	@Test void compareAndCommitRequiresExactSnapshot() {
		PythonToolsSavedData data = PythonToolsSavedData.createForTesting(Map.of());
		PythonToolsState expected = data.snapshot(OWNER);
		PythonToolsState next = expected.cycleSelection();
		assertTrue(data.commitIfCurrent(OWNER, expected, next));
		assertFalse(data.commitIfCurrent(OWNER, expected, expected));
		assertEquals(next, data.snapshot(OWNER));
	}
	@Test void noChangeCommitDoesNotDirtyData() {
		PythonToolsSavedData data = PythonToolsSavedData.createForTesting(Map.of());
		PythonToolsState state = data.snapshot(OWNER);
		assertTrue(data.commitIfCurrent(OWNER, state, state));
		assertFalse(data.isDirty());
	}
	@Test void changedCommitMarksDataDirty() {
		PythonToolsSavedData data = PythonToolsSavedData.createForTesting(Map.of());
		PythonToolsState state = data.snapshot(OWNER);
		assertTrue(data.commitIfCurrent(OWNER, state, state.cycleSelection()));
		assertTrue(data.isDirty());
	}
	@Test void ownersRemainIsolated() {
		PythonToolsSavedData data = PythonToolsSavedData.createForTesting(Map.of());
		PythonToolsState state = data.snapshot(OWNER);
		data.commitIfCurrent(OWNER, state, state.cycleSelection());
		assertEquals(PythonToolsState.initial(), data.snapshot(OTHER));
	}
	@Test void codecRoundTripsAllFiniteState() {
		PythonToolsState state = new PythonToolsState(
				2, Set.of(FakePackage.NUMPY_OF_DESPAIR, FakePackage.FLASK_OVERFLOW), true, 50, 70, 9);
		PythonToolsSavedData original = PythonToolsSavedData.createForTesting(Map.of(OWNER, state));
		JsonElement json = PythonToolsSavedData.TYPE.codec().encodeStart(JsonOps.INSTANCE, original).getOrThrow();
		PythonToolsSavedData decoded = PythonToolsSavedData.TYPE.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(state, decoded.snapshot(OWNER));
	}
	@Test void codecRejectsFutureSchema() {
		String json = "{\"schema\":2,\"players\":[]}";
		assertTrue(PythonToolsSavedData.TYPE.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent());
	}
	@Test void codecRejectsDuplicateOwners() {
		String owner = OWNER.toString();
		String state = "{\"selected_index\":0,\"installed_packages\":[],\"dependency_conflict\":false," +
				"\"flask_cooldown_until_tick\":0,\"recursion_cooldown_until_tick\":0,\"revision\":0}";
		String json = "{\"schema\":1,\"players\":[{\"owner_uuid\":\"" + owner + "\",\"state\":" + state + "}," +
				"{\"owner_uuid\":\"" + owner + "\",\"state\":" + state + "}]}";
		assertTrue(PythonToolsSavedData.TYPE.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent());
	}
	@Test void stateRejectsNegativePersistedTicks() {
		assertThrows(IllegalArgumentException.class, () -> new PythonToolsState(0, Set.of(), false, -1, 0, 0));
	}
	@Test void stateRecursionCooldownMutationIsIdempotent() {
		PythonToolsState state = PythonToolsState.initial().withRecursionCooldown(20);
		assertEquals(state, state.withRecursionCooldown(20));
	}
}
