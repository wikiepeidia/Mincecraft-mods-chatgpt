package dev.developershell.bossrush;

/** Hard caps and deterministic windows for the deadline boss-rush slice. */
public final class BossRushRules {
	public static final int JURY_EVIDENCE_TARGETS = 2;
	public static final int JURY_JURORS = 3;
	public static final double JURY_SCOPE_RADIUS = 14.0D;
	public static final int HAZARD_PULSE_TICKS = 20;

	public static final int CHAIRMAN_RUBRIC_NODES = 3;
	public static final int CHAIRMAN_MINOR_REVISIONS = 2;
	public static final float CHAIRMAN_MINOR_REVISIONS_HEALTH_FRACTION = 0.60F;
	public static final double CHAIRMAN_ACCEPTANCE_RADIUS = 4.0D;
	public static final int CHAIRMAN_ACCEPTANCE_WINDOW_TICKS = 80;
	public static final int CHAIRMAN_ACCEPTANCE_RECOVERY_TICKS = 60;

	public static final int CODEX_AGENTS = 3;
	public static final double CODEX_OVERFLOW_INNER_RADIUS = 5.0D;
	public static final double CODEX_OVERFLOW_OUTER_RADIUS = 10.0D;
	public static final int CODEX_RING_PARTICLES = 24;
	public static final float CODEX_MAX_REASONING_HEALTH_FRACTION = 0.35F;
	public static final int CODEX_MAX_REASONING_WINDOW_TICKS = 60;
	public static final int CODEX_MAX_REASONING_RECOVERY_TICKS = 80;

	public static boolean insideScope(double distanceSquared) {
		return distanceSquared <= JURY_SCOPE_RADIUS * JURY_SCOPE_RADIUS;
	}

	public static boolean insideAcceptancePad(double distanceSquared) {
		return distanceSquared <= CHAIRMAN_ACCEPTANCE_RADIUS * CHAIRMAN_ACCEPTANCE_RADIUS;
	}

	public static boolean insideOverflowRing(double distanceSquared) {
		double innerSquared = CODEX_OVERFLOW_INNER_RADIUS * CODEX_OVERFLOW_INNER_RADIUS;
		double outerSquared = CODEX_OVERFLOW_OUTER_RADIUS * CODEX_OVERFLOW_OUTER_RADIUS;
		return distanceSquared >= innerSquared && distanceSquared <= outerSquared;
	}

	public static boolean juryJurorUnlocked(
			boolean evidenceCleared,
			int activeJurorIndex,
			int targetJurorIndex
	) {
		return evidenceCleared && activeJurorIndex >= 0 && activeJurorIndex == targetJurorIndex;
	}

	public static boolean chairmanCoreUnlocked(
			boolean rubricCleared,
			int phase,
			boolean windowOpen,
			boolean ownerOnAcceptancePad
	) {
		return rubricCleared && (phase == 0
				|| (phase >= 2 && windowOpen && ownerOnAcceptancePad));
	}

	public static boolean codexCoreUnlocked(boolean agentsCleared, int phase, boolean windowOpen) {
		return agentsCleared && (phase == 0 || windowOpen);
	}

	private BossRushRules() {
	}
}
