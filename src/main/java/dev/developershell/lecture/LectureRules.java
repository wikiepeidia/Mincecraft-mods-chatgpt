package dev.developershell.lecture;

import java.util.Objects;

/** Immutable, bounded tuning for one logical-server lecture session. */
public final class LectureRules {
	private static final int TICKS_PER_SECOND = 20;
	private static final int STANDARD_BOSS_HEALTH = 120;
	private static final int STANDARD_QUIZ_TELEGRAPH_TICKS = 8 * TICKS_PER_SECOND;
	private static final int STANDARD_ATTENDANCE_TELEGRAPH_TICKS = 6 * TICKS_PER_SECOND;
	private static final int STANDARD_RECOVERY_TICKS = 3 * TICKS_PER_SECOND;
	private static final int STANDARD_MISS_DAMAGE = 4;
	private static final int STANDARD_MAX_HOMEWORK_ADDS = 3;
	private static final LectureRules STANDARD = new LectureRules(
			5 * TICKS_PER_SECOND,
			4 * TICKS_PER_SECOND,
			TICKS_PER_SECOND,
			10,
			6,
			24,
			8,
			STANDARD_BOSS_HEALTH,
			STANDARD_MISS_DAMAGE,
			STANDARD_MAX_HOMEWORK_ADDS,
			STANDARD_QUIZ_TELEGRAPH_TICKS,
			STANDARD_ATTENDANCE_TELEGRAPH_TICKS
	);

	private final int slideDeckTelegraphTicks;
	private final int vulnerabilityTicks;
	private final int actionBarUpdateTicks;
	private final int particleRefreshTicks;
	private final int particlesPerRefresh;
	private final int maxParticleBurstsPerEncounter;
	private final int maxTransitionSoundsPerEncounter;
	private final int bossMaxHealth;
	private final int missDamage;
	private final int maxHomeworkAdds;
	private final int quizTelegraphTicks;
	private final int attendanceTelegraphTicks;

	/**
	 * Compatibility constructor retained for all existing callers and equality assertions.
	 * Combat values use the unchanged Standard defaults unless supplied by {@link #configured}.
	 */
	public LectureRules(
			int slideDeckTelegraphTicks,
			int vulnerabilityTicks,
			int actionBarUpdateTicks,
			int particleRefreshTicks,
			int particlesPerRefresh,
			int maxParticleBurstsPerEncounter,
			int maxTransitionSoundsPerEncounter
	) {
		this(
				slideDeckTelegraphTicks,
				vulnerabilityTicks,
				actionBarUpdateTicks,
				particleRefreshTicks,
				particlesPerRefresh,
				maxParticleBurstsPerEncounter,
				maxTransitionSoundsPerEncounter,
				STANDARD_BOSS_HEALTH,
				STANDARD_MISS_DAMAGE,
				STANDARD_MAX_HOMEWORK_ADDS,
				STANDARD_QUIZ_TELEGRAPH_TICKS,
				STANDARD_ATTENDANCE_TELEGRAPH_TICKS
		);
	}

	private LectureRules(
			int slideDeckTelegraphTicks,
			int vulnerabilityTicks,
			int actionBarUpdateTicks,
			int particleRefreshTicks,
			int particlesPerRefresh,
			int maxParticleBurstsPerEncounter,
			int maxTransitionSoundsPerEncounter,
			int bossMaxHealth,
			int missDamage,
			int maxHomeworkAdds,
			int quizTelegraphTicks,
			int attendanceTelegraphTicks
	) {
		requireRange("slideDeckTelegraphTicks", slideDeckTelegraphTicks, 3 * TICKS_PER_SECOND, 10 * TICKS_PER_SECOND);
		requireRange("vulnerabilityTicks", vulnerabilityTicks, TICKS_PER_SECOND, 10 * TICKS_PER_SECOND);
		requireRange("actionBarUpdateTicks", actionBarUpdateTicks, TICKS_PER_SECOND, TICKS_PER_SECOND);
		requireRange("particleRefreshTicks", particleRefreshTicks, 4, TICKS_PER_SECOND);
		requireRange("particlesPerRefresh", particlesPerRefresh, 1, 12);
		requireRange("maxParticleBurstsPerEncounter", maxParticleBurstsPerEncounter, 1, 40);
		requireRange("maxTransitionSoundsPerEncounter", maxTransitionSoundsPerEncounter, 1, 12);
		requireRange("bossMaxHealth", bossMaxHealth, 40, 400);
		requireRange("missDamage", missDamage, 1, 20);
		requireRange("maxHomeworkAdds", maxHomeworkAdds, 0, 8);
		requireRange("quizTelegraphTicks", quizTelegraphTicks, 80, 300);
		requireRange("attendanceTelegraphTicks", attendanceTelegraphTicks, 80, 240);
		this.slideDeckTelegraphTicks = slideDeckTelegraphTicks;
		this.vulnerabilityTicks = vulnerabilityTicks;
		this.actionBarUpdateTicks = actionBarUpdateTicks;
		this.particleRefreshTicks = particleRefreshTicks;
		this.particlesPerRefresh = particlesPerRefresh;
		this.maxParticleBurstsPerEncounter = maxParticleBurstsPerEncounter;
		this.maxTransitionSoundsPerEncounter = maxTransitionSoundsPerEncounter;
		this.bossMaxHealth = bossMaxHealth;
		this.missDamage = missDamage;
		this.maxHomeworkAdds = maxHomeworkAdds;
		this.quizTelegraphTicks = quizTelegraphTicks;
		this.attendanceTelegraphTicks = attendanceTelegraphTicks;
	}

	/** Projects the complete accepted config snapshot while preserving the seven-argument API. */
	public static LectureRules configured(
			int slideDeckTelegraphTicks,
			int vulnerabilityTicks,
			int actionBarUpdateTicks,
			int particleRefreshTicks,
			int particlesPerRefresh,
			int maxParticleBurstsPerEncounter,
			int maxTransitionSoundsPerEncounter,
			int bossMaxHealth,
			int missDamage,
			int maxHomeworkAdds,
			int quizTelegraphTicks,
			int attendanceTelegraphTicks
	) {
		return new LectureRules(
				slideDeckTelegraphTicks,
				vulnerabilityTicks,
				actionBarUpdateTicks,
				particleRefreshTicks,
				particlesPerRefresh,
				maxParticleBurstsPerEncounter,
				maxTransitionSoundsPerEncounter,
				bossMaxHealth,
				missDamage,
				maxHomeworkAdds,
				quizTelegraphTicks,
				attendanceTelegraphTicks
		);
	}

	public static LectureRules standard() {
		return STANDARD;
	}

	public int slideDeckTelegraphTicks() {
		return slideDeckTelegraphTicks;
	}

	public int vulnerabilityTicks() {
		return vulnerabilityTicks;
	}

	public int actionBarUpdateTicks() {
		return actionBarUpdateTicks;
	}

	public int particleRefreshTicks() {
		return particleRefreshTicks;
	}

	public int particlesPerRefresh() {
		return particlesPerRefresh;
	}

	public int maxParticleBurstsPerEncounter() {
		return maxParticleBurstsPerEncounter;
	}

	public int maxTransitionSoundsPerEncounter() {
		return maxTransitionSoundsPerEncounter;
	}

	public int slideCycleTicks() {
		return slideDeckTelegraphTicks + vulnerabilityTicks;
	}

	public int maxParticlesPerEncounter() {
		return particlesPerRefresh * maxParticleBurstsPerEncounter;
	}

	public int bossMaxHealth() {
		return bossMaxHealth;
	}

	public int quizTelegraphTicks() {
		return quizTelegraphTicks;
	}

	public int attendanceTelegraphTicks() {
		return attendanceTelegraphTicks;
	}

	/** Three-second readable reset from the UI recovery copy. */
	public int recoveryTicks() {
		return STANDARD_RECOVERY_TICKS;
	}

	public int slideDeckMissDamage() {
		return missDamage;
	}

	public int detentionDamage() {
		return missDamage;
	}

	public int maxHomeworkIntentsPerResolve() {
		return 1;
	}

	public int maxHomeworkAdds() {
		return maxHomeworkAdds;
	}

	/**
	 * Equality intentionally remains the original seven-field value contract. The added combat
	 * projection is operational session tuning and does not invalidate existing config assertions.
	 */
	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof LectureRules that)) {
			return false;
		}
		return slideDeckTelegraphTicks == that.slideDeckTelegraphTicks
				&& vulnerabilityTicks == that.vulnerabilityTicks
				&& actionBarUpdateTicks == that.actionBarUpdateTicks
				&& particleRefreshTicks == that.particleRefreshTicks
				&& particlesPerRefresh == that.particlesPerRefresh
				&& maxParticleBurstsPerEncounter == that.maxParticleBurstsPerEncounter
				&& maxTransitionSoundsPerEncounter == that.maxTransitionSoundsPerEncounter;
	}

	@Override
	public int hashCode() {
		return Objects.hash(
				slideDeckTelegraphTicks,
				vulnerabilityTicks,
				actionBarUpdateTicks,
				particleRefreshTicks,
				particlesPerRefresh,
				maxParticleBurstsPerEncounter,
				maxTransitionSoundsPerEncounter
		);
	}

	@Override
	public String toString() {
		return "LectureRules[slideDeckTelegraphTicks=" + slideDeckTelegraphTicks
				+ ", vulnerabilityTicks=" + vulnerabilityTicks
				+ ", actionBarUpdateTicks=" + actionBarUpdateTicks
				+ ", particleRefreshTicks=" + particleRefreshTicks
				+ ", particlesPerRefresh=" + particlesPerRefresh
				+ ", maxParticleBurstsPerEncounter=" + maxParticleBurstsPerEncounter
				+ ", maxTransitionSoundsPerEncounter=" + maxTransitionSoundsPerEncounter + "]";
	}

	private static void requireRange(String field, int value, int minimum, int maximum) {
		if (value < minimum || value > maximum) {
			throw new IllegalArgumentException(field + " must be between " + minimum + " and " + maximum);
		}
	}
}
