package dev.developershell.lecture;

/** Immutable, bounded tuning for the retained Standard lecture tracer. */
public record LectureRules(
		int slideDeckTelegraphTicks,
		int vulnerabilityTicks,
		int actionBarUpdateTicks,
		int particleRefreshTicks,
		int particlesPerRefresh,
		int maxParticleBurstsPerEncounter,
		int maxTransitionSoundsPerEncounter
) {
	private static final int TICKS_PER_SECOND = 20;
	private static final LectureRules STANDARD = new LectureRules(
			5 * TICKS_PER_SECOND,
			4 * TICKS_PER_SECOND,
			TICKS_PER_SECOND,
			10,
			6,
			24,
			8
	);

	public LectureRules {
		requireRange("slideDeckTelegraphTicks", slideDeckTelegraphTicks, 3 * TICKS_PER_SECOND, 10 * TICKS_PER_SECOND);
		requireRange("vulnerabilityTicks", vulnerabilityTicks, TICKS_PER_SECOND, 10 * TICKS_PER_SECOND);
		requireRange("actionBarUpdateTicks", actionBarUpdateTicks, TICKS_PER_SECOND, TICKS_PER_SECOND);
		requireRange("particleRefreshTicks", particleRefreshTicks, 4, TICKS_PER_SECOND);
		requireRange("particlesPerRefresh", particlesPerRefresh, 1, 12);
		requireRange("maxParticleBurstsPerEncounter", maxParticleBurstsPerEncounter, 1, 40);
		requireRange("maxTransitionSoundsPerEncounter", maxTransitionSoundsPerEncounter, 1, 12);
	}

	public static LectureRules standard() {
		return STANDARD;
	}

	public int slideCycleTicks() {
		return slideDeckTelegraphTicks + vulnerabilityTicks;
	}

	public int maxParticlesPerEncounter() {
		return particlesPerRefresh * maxParticleBurstsPerEncounter;
	}

	private static void requireRange(String field, int value, int minimum, int maximum) {
		if (value < minimum || value > maximum) {
			throw new IllegalArgumentException(field + " must be between " + minimum + " and " + maximum);
		}
	}
}
