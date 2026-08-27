package dev.developershell.python;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

final class PipEnvironmentTest {
	private final PipEnvironment environment = new PipEnvironment();

	@Test void initialSelectionIsNumpy() { assertEquals(FakePackage.NUMPY_OF_DESPAIR, PythonToolsState.initial().selectedPackage()); }
	@Test void cycleAdvancesSelection() { assertEquals(FakePackage.FLASK_OVERFLOW, environment.cycleSelection(PythonToolsState.initial()).selectedPackage()); }
	@Test void fourCyclesWrapSelection() {
		PythonToolsState state = PythonToolsState.initial();
		for (int i = 0; i < 4; i++) state = environment.cycleSelection(state);
		assertEquals(FakePackage.NUMPY_OF_DESPAIR, state.selectedPackage());
	}
	@Test void cycleDoesNotMutateOriginal() {
		PythonToolsState initial = PythonToolsState.initial();
		environment.cycleSelection(initial);
		assertEquals(0, initial.selectedIndex());
	}
	@Test void exactXpInstallsAndChargesExactlyOnce() {
		PipOutcome outcome = environment.install(PythonToolsState.initial(), 2, 10);
		assertEquals(PipOutcome.Kind.INSTALLED, outcome.kind());
		assertEquals(2, outcome.xpCharged());
		assertTrue(outcome.nextState().installedPackages().contains(FakePackage.NUMPY_OF_DESPAIR));
	}
	@Test void insufficientXpNeverMutatesOrCharges() {
		PythonToolsState state = PythonToolsState.initial();
		PipOutcome outcome = environment.install(state, 1, 10);
		assertEquals(PipOutcome.Kind.INSUFFICIENT_XP, outcome.kind());
		assertSame(state, outcome.nextState());
		assertEquals(0, outcome.xpCharged());
	}
	@Test void repeatedInstallIsIdempotent() {
		PythonToolsState installed = environment.install(PythonToolsState.initial(), 2, 10).nextState();
		PipOutcome replay = environment.install(installed, 100, 11);
		assertEquals(PipOutcome.Kind.ALREADY_INSTALLED, replay.kind());
		assertSame(installed, replay.nextState());
		assertEquals(0, replay.xpCharged());
	}
	@Test void successfulInstallAdvertisesEffect() { assertTrue(environment.install(PythonToolsState.initial(), 2, 0).appliesEffect()); }
	@Test void rejectedInstallNeverAdvertisesEffect() { assertFalse(environment.install(PythonToolsState.initial(), 0, 0).appliesEffect()); }
	@Test void numpyAndPandasConflictSymmetrically() {
		assertFalse(environment.compatible(FakePackage.NUMPY_OF_DESPAIR, FakePackage.PANDAS_IN_PRODUCTION));
		assertFalse(environment.compatible(FakePackage.PANDAS_IN_PRODUCTION, FakePackage.NUMPY_OF_DESPAIR));
	}
	@Test void flaskAndDjangoConflictSymmetrically() {
		assertFalse(environment.compatible(FakePackage.FLASK_OVERFLOW, FakePackage.DJANGO_UNCHAINED));
		assertFalse(environment.compatible(FakePackage.DJANGO_UNCHAINED, FakePackage.FLASK_OVERFLOW));
	}
	@Test void numpyAndFlaskAreCompatible() { assertTrue(environment.compatible(FakePackage.NUMPY_OF_DESPAIR, FakePackage.FLASK_OVERFLOW)); }
	@Test void numpyAndDjangoAreCompatible() { assertTrue(environment.compatible(FakePackage.NUMPY_OF_DESPAIR, FakePackage.DJANGO_UNCHAINED)); }
	@Test void flaskAndPandasAreCompatible() { assertTrue(environment.compatible(FakePackage.FLASK_OVERFLOW, FakePackage.PANDAS_IN_PRODUCTION)); }
	@Test void djangoAndPandasAreCompatible() { assertTrue(environment.compatible(FakePackage.DJANGO_UNCHAINED, FakePackage.PANDAS_IN_PRODUCTION)); }
	@Test void packageIsCompatibleWithItself() {
		for (FakePackage fakePackage : FakePackage.values()) assertTrue(environment.compatible(fakePackage, fakePackage));
	}
	@Test void incompatibleInstallCreatesOneConflictAndChargesCost() {
		PythonToolsState state = state(3, Set.of(FakePackage.NUMPY_OF_DESPAIR), false, 0);
		PipOutcome outcome = environment.install(state, 5, 20);
		assertEquals(PipOutcome.Kind.DEPENDENCY_CONFLICT, outcome.kind());
		assertTrue(outcome.nextState().dependencyConflict());
		assertEquals(5, outcome.xpCharged());
		assertEquals(Set.of(FakePackage.NUMPY_OF_DESPAIR), outcome.nextState().installedPackages());
	}
	@Test void activeConflictRejectsReplayWithoutSecondCharge() {
		PythonToolsState conflicted = state(3, Set.of(FakePackage.NUMPY_OF_DESPAIR), true, 0);
		PipOutcome replay = environment.install(conflicted, 99, 21);
		assertEquals(PipOutcome.Kind.CONFLICT_ACTIVE, replay.kind());
		assertSame(conflicted, replay.nextState());
		assertEquals(0, replay.xpCharged());
	}
	@Test void venvClearsConflictAndInstalledPackages() {
		PythonToolsState conflicted = state(3, Set.of(FakePackage.NUMPY_OF_DESPAIR), true, 0);
		PipOutcome outcome = environment.useVenv(conflicted, 50);
		assertEquals(PipOutcome.Kind.VENV_CLEARED, outcome.kind());
		assertFalse(outcome.nextState().dependencyConflict());
		assertTrue(outcome.nextState().installedPackages().isEmpty());
		assertEquals(150, outcome.nextState().flaskCooldownUntilTick());
	}
	@Test void venvCanResetCompatibleEnvironment() {
		PythonToolsState installed = state(1, Set.of(FakePackage.NUMPY_OF_DESPAIR), false, 0);
		assertEquals(PipOutcome.Kind.VENV_CLEARED, environment.useVenv(installed, 5).kind());
	}
	@Test void venvIsolatesSelectedInstalledPackage() {
		PythonToolsState installed = state(
				1, Set.of(FakePackage.NUMPY_OF_DESPAIR, FakePackage.FLASK_OVERFLOW), false, 0);
		PipOutcome outcome = environment.useVenv(installed, 5);
		assertEquals(Set.of(FakePackage.FLASK_OVERFLOW), outcome.nextState().installedPackages());
	}
	@Test void venvOnCleanEnvironmentIsNoOp() {
		PythonToolsState state = PythonToolsState.initial();
		PipOutcome outcome = environment.useVenv(state, 5);
		assertEquals(PipOutcome.Kind.VENV_CLEAN, outcome.kind());
		assertSame(state, outcome.nextState());
	}
	@Test void venvCooldownRejectsEarlyReplay() {
		PythonToolsState cooldown = state(0, Set.of(), false, 100);
		assertEquals(PipOutcome.Kind.VENV_COOLDOWN, environment.useVenv(cooldown, 99).kind());
	}
	@Test void venvCooldownAllowsExactDeadline() {
		PythonToolsState cooldown = state(0, Set.of(FakePackage.NUMPY_OF_DESPAIR), false, 100);
		assertEquals(PipOutcome.Kind.VENV_CLEARED, environment.useVenv(cooldown, 100).kind());
	}
	@Test void stateRevisionAdvancesOnlyOnMutation() {
		PythonToolsState initial = PythonToolsState.initial();
		PythonToolsState installed = environment.install(initial, 2, 0).nextState();
		assertEquals(initial.revision() + 1, installed.revision());
		assertEquals(installed.revision(), environment.install(installed, 99, 1).nextState().revision());
	}
	@Test void installedSetIsImmutable() {
		PythonToolsState state = state(0, Set.of(FakePackage.NUMPY_OF_DESPAIR), false, 0);
		assertThrows(UnsupportedOperationException.class, () -> state.installedPackages().add(FakePackage.FLASK_OVERFLOW));
	}
	@Test void negativeXpIsRejected() { assertThrows(IllegalArgumentException.class, () -> environment.install(PythonToolsState.initial(), -1, 0)); }
	@Test void negativeTickIsRejected() { assertThrows(IllegalArgumentException.class, () -> environment.useVenv(PythonToolsState.initial(), -1)); }
	@Test void changedOutcomeDiffersFromExpectedState() {
		PythonToolsState state = PythonToolsState.initial();
		assertTrue(environment.install(state, 2, 0).changedFrom(state));
		assertFalse(environment.install(state, 0, 0).changedFrom(state));
	}

	private static PythonToolsState state(int selected, Set<FakePackage> installed, boolean conflict, long flaskCooldown) {
		return new PythonToolsState(selected, installed, conflict, flaskCooldown, 0, 0);
	}
}
