package dev.developershell.python;

/** Pure admission guard for the pickaxe's visible RecursionError cooldown. */
public record RecursionCooldown(long untilTick) {
	public static final long MAX_RESTORED_FUTURE_TICKS = 1_200L;

	public RecursionCooldown {
		if (untilTick < 0) {
			throw new IllegalArgumentException("Cooldown deadline cannot be negative");
		}
	}

	public static RecursionCooldown idle() {
		return new RecursionCooldown(0L);
	}

	public boolean ready(long gameTick) {
		validateTick(gameTick);
		return gameTick >= untilTick;
	}

	public long remaining(long gameTick) {
		validateTick(gameTick);
		return Math.max(0L, untilTick - gameTick);
	}

	public Admission admit(long gameTick, long durationTicks) {
		validateTick(gameTick);
		if (durationTicks <= 0 || durationTicks > MAX_RESTORED_FUTURE_TICKS) {
			throw new IllegalArgumentException("Cooldown duration is outside the bounded range");
		}
		if (!ready(gameTick)) {
			return new Admission(false, this);
		}
		return new Admission(true, new RecursionCooldown(Math.addExact(gameTick, durationTicks)));
	}

	public static RecursionCooldown restoreClamped(long persistedUntilTick, long gameTick) {
		validateTick(gameTick);
		long lower = Math.max(0L, persistedUntilTick);
		long max = Math.addExact(gameTick, MAX_RESTORED_FUTURE_TICKS);
		return new RecursionCooldown(Math.min(lower, max));
	}

	private static void validateTick(long gameTick) {
		if (gameTick < 0) {
			throw new IllegalArgumentException("Game tick cannot be negative");
		}
	}

	public record Admission(boolean admitted, RecursionCooldown cooldown) {
		public Admission {
			if (cooldown == null) {
				throw new IllegalArgumentException("Cooldown is required");
			}
		}
	}
}
