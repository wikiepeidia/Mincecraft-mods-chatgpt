package dev.developershell.python;

import java.util.Objects;

/** Pure decision result. Adapters must persist {@code nextState} before side effects. */
public record PipOutcome(
		Kind kind,
		PythonToolsState nextState,
		FakePackage fakePackage,
		int xpCharged
) {
	public PipOutcome {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(nextState, "nextState");
		Objects.requireNonNull(fakePackage, "fakePackage");
		if (xpCharged < 0) {
			throw new IllegalArgumentException("XP charge cannot be negative");
		}
	}

	public boolean changedFrom(PythonToolsState previous) {
		return !nextState.equals(Objects.requireNonNull(previous, "previous"));
	}

	public boolean appliesEffect() {
		return kind == Kind.INSTALLED;
	}

	public enum Kind {
		INSTALLED,
		DEPENDENCY_CONFLICT,
		INSUFFICIENT_XP,
		ALREADY_INSTALLED,
		CONFLICT_ACTIVE,
		VENV_CLEARED,
		VENV_COOLDOWN,
		VENV_CLEAN
	}
}
