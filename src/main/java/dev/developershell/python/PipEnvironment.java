package dev.developershell.python;

import java.util.Objects;
import java.util.Set;

/** Deterministic fake pip/venv rules with no external execution surface. */
public final class PipEnvironment {
	public static final long VENV_COOLDOWN_TICKS = 100L;

	private static final Set<PackagePair> INCOMPATIBLE = Set.of(
			PackagePair.of(FakePackage.NUMPY_OF_DESPAIR, FakePackage.PANDAS_IN_PRODUCTION),
			PackagePair.of(FakePackage.FLASK_OVERFLOW, FakePackage.DJANGO_UNCHAINED)
	);

	public PipOutcome install(PythonToolsState state, int availableXpLevels, long gameTick) {
		Objects.requireNonNull(state, "state");
		if (availableXpLevels < 0 || gameTick < 0) {
			throw new IllegalArgumentException("XP and game tick cannot be negative");
		}
		FakePackage selected = state.selectedPackage();
		if (state.dependencyConflict()) {
			return outcome(PipOutcome.Kind.CONFLICT_ACTIVE, state, selected, 0);
		}
		if (state.installedPackages().contains(selected)) {
			return outcome(PipOutcome.Kind.ALREADY_INSTALLED, state, selected, 0);
		}
		if (availableXpLevels < selected.xpCost()) {
			return outcome(PipOutcome.Kind.INSUFFICIENT_XP, state, selected, 0);
		}
		boolean compatible = state.installedPackages().stream()
				.allMatch(installed -> compatible(installed, selected));
		if (!compatible) {
			return outcome(PipOutcome.Kind.DEPENDENCY_CONFLICT, state.conflict(), selected, selected.xpCost());
		}
		return outcome(PipOutcome.Kind.INSTALLED, state.install(selected), selected, selected.xpCost());
	}

	public PipOutcome useVenv(PythonToolsState state, long gameTick) {
		Objects.requireNonNull(state, "state");
		if (gameTick < 0) {
			throw new IllegalArgumentException("Game tick cannot be negative");
		}
		FakePackage selected = state.selectedPackage();
		if (gameTick < state.flaskCooldownUntilTick()) {
			return outcome(PipOutcome.Kind.VENV_COOLDOWN, state, selected, 0);
		}
		if (!state.dependencyConflict() && state.installedPackages().isEmpty()) {
			return outcome(PipOutcome.Kind.VENV_CLEAN, state, selected, 0);
		}
		long deadline = Math.addExact(gameTick, VENV_COOLDOWN_TICKS);
		return outcome(PipOutcome.Kind.VENV_CLEARED, state.clearEnvironment(deadline), selected, 0);
	}

	public PythonToolsState cycleSelection(PythonToolsState state) {
		return Objects.requireNonNull(state, "state").cycleSelection();
	}

	public boolean compatible(FakePackage left, FakePackage right) {
		Objects.requireNonNull(left, "left");
		Objects.requireNonNull(right, "right");
		return left == right || !INCOMPATIBLE.contains(PackagePair.of(left, right));
	}

	private static PipOutcome outcome(
			PipOutcome.Kind kind,
			PythonToolsState state,
			FakePackage selected,
			int xp
	) {
		return new PipOutcome(kind, state, selected, xp);
	}

	private record PackagePair(FakePackage first, FakePackage second) {
		static PackagePair of(FakePackage left, FakePackage right) {
			return left.ordinal() <= right.ordinal()
					? new PackagePair(left, right)
					: new PackagePair(right, left);
		}
	}
}
