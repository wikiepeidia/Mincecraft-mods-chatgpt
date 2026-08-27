package dev.developershell.lecture;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure immutable combat domain for Professor Infinite Slides.
 *
 * <p>Callers provide only recorded server facts. This type owns no world, clock, random,
 * filesystem, or reward authority.</p>
 */
public final class LectureStateMachine {
	private static final long ATTEMPT_SALT = 0x9E3779B97F4A7C15L;
	private static final long ACT_SALT = 0xD1B54A32D192ED03L;
	private static final long CYCLE_SALT = 0x94D049BB133111EBL;
	private static final long QUIZ_SALT = 0xBF58476D1CE4E5B9L;

	public enum Stage {
		WIND_UP,
		RESOLVE,
		VULNERABLE,
		RECOVERY,
		COMPLETE
	}

	public enum Resolution {
		NONE,
		SAFE,
		MISSED,
		CORRECT,
		WRONG,
		NO_ANSWER,
		PRESENT,
		ABSENT,
		DETENTION
	}

	public enum Consequence {
		INFORMATION_OVERLOAD,
		DETENTION
	}

	public enum Density {
		STANDARD,
		ESSENTIAL_ONLY
	}

	public enum DamageRejection {
		NONE,
		CLOSED_WINDOW,
		WRONG_OWNER,
		STALE_ENCOUNTER,
		INVALID_AMOUNT,
		AT_THRESHOLD,
		COMPLETE
	}

	/** Pure projection used by the Minecraft entity before it delegates to vanilla damage. */
	public record DamageAdmission(
			boolean accepted,
			float acceptedDamage,
			float projectedHealth,
			float thresholdHealth,
			boolean closesWindow,
			boolean victoryIntent,
			DamageRejection rejection
	) {
		public DamageAdmission {
			Objects.requireNonNull(rejection, "rejection");
			if (!Float.isFinite(acceptedDamage)
					|| !Float.isFinite(projectedHealth)
					|| !Float.isFinite(thresholdHealth)
					|| acceptedDamage < 0.0F
					|| projectedHealth < 0.0F
					|| thresholdHealth < 0.0F) {
				throw new IllegalArgumentException("Damage admission values must be finite and non-negative");
			}
			if (accepted != (rejection == DamageRejection.NONE)) {
				throw new IllegalArgumentException("Accepted admission must use the NONE rejection identity");
			}
			if (accepted && (acceptedDamage <= 0.0F || projectedHealth < thresholdHealth)) {
				throw new IllegalArgumentException("Accepted entity damage cannot cross the act floor");
			}
			if (victoryIntent && (!closesWindow || thresholdHealth != 0.0F)) {
				throw new IllegalArgumentException("Victory is possible only when the final window closes");
			}
		}
	}

	public record State(
			UUID encounterUuid,
			UUID ownerUuid,
			int attempt,
			LectureAct act,
			Stage stage,
			long phaseStartedTick,
			long deadlineTick,
			int actCycle,
			int quizIndex,
			int absenceCount,
			int bossHealth,
			long choiceSeed,
			LectureGeometry.Lane safeLane,
			LectureGeometry.QuizPad correctPad,
			LectureGeometry.AttendanceQuadrant attendanceQuadrant,
			Resolution resolution,
			boolean reducedEffects,
			boolean advanceAfterRecovery,
			boolean victoryIntentEmitted,
			LectureRules rules
	) {
		public State {
			Objects.requireNonNull(encounterUuid, "encounterUuid");
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			Objects.requireNonNull(act, "act");
			Objects.requireNonNull(stage, "stage");
			Objects.requireNonNull(resolution, "resolution");
			Objects.requireNonNull(rules, "rules");
			if (attempt < 1 || actCycle < 0 || quizIndex < 0) {
				throw new IllegalArgumentException("Attempt, act cycle, and quiz index must be bounded non-negative values");
			}
			if (phaseStartedTick < 0L || deadlineTick < phaseStartedTick) {
				throw new IllegalArgumentException("Lecture phase deadline must be finite and monotonic");
			}
			if (absenceCount < 0 || absenceCount > 3) {
				throw new IllegalArgumentException("Attendance absence count must be between zero and three");
			}
			if (bossHealth < 0 || bossHealth > rules.bossMaxHealth()) {
				throw new IllegalArgumentException("Professor health is outside the bounded Lecture rules");
			}
			if (stage == Stage.WIND_UP && resolution != Resolution.NONE) {
				throw new IllegalArgumentException("Wind-up cannot carry a prior resolution");
			}
			if (stage == Stage.RESOLVE && resolution == Resolution.NONE) {
				throw new IllegalArgumentException("Resolve state requires one recorded result");
			}
			if (stage == Stage.COMPLETE && (!victoryIntentEmitted || bossHealth != 0)) {
				throw new IllegalArgumentException("Complete state requires zero health and one victory intent");
			}
			boolean validTarget = switch (act) {
				case SLIDE_DECK -> safeLane != null && correctPad == null && attendanceQuadrant == null;
				case SURPRISE_QUIZ -> safeLane == null && correctPad != null && attendanceQuadrant == null;
				case ATTENDANCE_CHECK -> safeLane == null && correctPad == null && attendanceQuadrant != null;
			};
			if (!validTarget) {
				throw new IllegalArgumentException("Exactly one target must match the current Lecture act");
			}
		}
	}

	public sealed interface Input permits Input.Tick, Input.Damage {
		long gameTick();

		record Tick(long gameTick, LectureGeometry.LocalPosition ownerPosition, int ownerHealth) implements Input {
			public Tick {
				Objects.requireNonNull(ownerPosition, "ownerPosition");
				if (gameTick < 0L || ownerHealth < 1) {
					throw new IllegalArgumentException("Tick and live owner health must be non-negative");
				}
			}
		}

		record Damage(long gameTick, UUID attackerUuid, UUID encounterUuid, int amount) implements Input {
			public Damage {
				Objects.requireNonNull(attackerUuid, "attackerUuid");
				Objects.requireNonNull(encounterUuid, "encounterUuid");
				if (gameTick < 0L) {
					throw new IllegalArgumentException("Damage tick must be non-negative");
				}
			}
		}
	}

	public record Output(State state, List<Intent> intents) {
		public Output {
			Objects.requireNonNull(state, "state");
			intents = List.copyOf(Objects.requireNonNull(intents, "intents"));
			if (intents.size() > 4) {
				throw new IllegalArgumentException("One Lecture transition may emit at most four bounded intents");
			}
		}
	}

	public sealed interface Intent permits
			Intent.Telegraph,
			Intent.DirectDamage,
			Intent.Homework,
			Intent.Attendance,
			Intent.Vulnerability,
			Intent.Recovery,
			Intent.DamageAccepted,
			Intent.DamageRejected,
			Intent.Victory {
		record Telegraph(
				LectureAct act,
				long choiceSeed,
				String targetName,
				int durationTicks,
				Density density
		) implements Intent {
			public Telegraph {
				Objects.requireNonNull(act, "act");
				Objects.requireNonNull(targetName, "targetName");
				Objects.requireNonNull(density, "density");
				if (targetName.isBlank() || durationTicks <= 0) {
					throw new IllegalArgumentException("Telegraph target and duration must be bounded");
				}
			}
		}

		record DirectDamage(int amount, Consequence consequence) implements Intent {
			public DirectDamage {
				Objects.requireNonNull(consequence, "consequence");
				if (amount <= 0) {
					throw new IllegalArgumentException("Direct damage intent must be positive");
				}
			}
		}

		record Homework(int count) implements Intent {
			public Homework {
				if (count != 1) {
					throw new IllegalArgumentException("One resolution may emit exactly one Homework intent");
				}
			}
		}

		record Attendance(int absenceCount, boolean detention) implements Intent {
			public Attendance {
				if (absenceCount < 1 || absenceCount > 3) {
					throw new IllegalArgumentException("Attendance intent must remain between one and three");
				}
			}
		}

		record Vulnerability(boolean open, int thresholdHealth, int durationTicks) implements Intent {
			public Vulnerability {
				if (thresholdHealth < 0 || durationTicks < 0 || open == (durationTicks == 0)) {
					throw new IllegalArgumentException("Vulnerability duration must match open state");
				}
			}
		}

		record Recovery(LectureAct act, int durationTicks, boolean advanceAfterRecovery) implements Intent {
			public Recovery {
				Objects.requireNonNull(act, "act");
				if (durationTicks <= 0) {
					throw new IllegalArgumentException("Recovery duration must be positive");
				}
			}
		}

		record DamageAccepted(int amount, int remainingBossHealth, int thresholdHealth) implements Intent {
			public DamageAccepted {
				if (amount <= 0 || remainingBossHealth < thresholdHealth || thresholdHealth < 0) {
					throw new IllegalArgumentException("Accepted damage must respect the current act threshold");
				}
			}
		}

		record DamageRejected(DamageRejection reason) implements Intent {
			public DamageRejected {
				Objects.requireNonNull(reason, "reason");
			}
		}

		record Victory(UUID ownerUuid, UUID encounterUuid) implements Intent {
			public Victory {
				Objects.requireNonNull(ownerUuid, "ownerUuid");
				Objects.requireNonNull(encounterUuid, "encounterUuid");
			}
		}
	}

	public static Output start(
			UUID encounterUuid,
			UUID ownerUuid,
			int attempt,
			long gameTick,
			LectureRules rules,
			boolean reducedEffects
	) {
		return outputWithTelegraph(newWindUp(
				encounterUuid,
				ownerUuid,
				attempt,
				LectureAct.SLIDE_DECK,
				0,
				0,
				0,
				rules.bossMaxHealth(),
				gameTick,
				rules,
				reducedEffects
		));
	}

	public static Output step(State state, Input input) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(input, "input");
		if (input.gameTick() < state.phaseStartedTick()) {
			return new Output(state, List.of());
		}
		if (input instanceof Input.Damage damage) {
			return applyDamage(state, damage);
		}
		Input.Tick tick = (Input.Tick) input;
		return switch (state.stage()) {
			case WIND_UP -> tick.gameTick() < state.deadlineTick()
					? new Output(state, List.of())
					: resolve(state, tick);
			case RESOLVE -> settleResolution(state, tick.gameTick());
			case VULNERABLE -> tick.gameTick() < state.deadlineTick()
					? new Output(state, List.of())
					: beginRecovery(state, tick.gameTick(), false, true);
			case RECOVERY -> tick.gameTick() < state.deadlineTick()
					? new Output(state, List.of())
					: finishRecovery(state, tick.gameTick());
			case COMPLETE -> new Output(state, List.of());
		};
	}

	/**
	 * Admits a vanilla damage request against the active server-owned window.
	 * Campaign persistence validation remains an additional required conjunct at the entity seam.
	 */
	public static DamageAdmission admitEntityDamage(
			UUID expectedOwnerUuid,
			UUID expectedEncounterUuid,
			UUID attackerUuid,
			UUID currentEncounterUuid,
			boolean windowOpen,
			float currentHealth,
			float thresholdHealth,
			float requestedDamage
	) {
		if (!windowOpen) {
			return rejectedEntityDamage(DamageRejection.CLOSED_WINDOW, currentHealth);
		}
		if (expectedOwnerUuid == null || !expectedOwnerUuid.equals(attackerUuid)) {
			return rejectedEntityDamage(DamageRejection.WRONG_OWNER, currentHealth);
		}
		if (expectedEncounterUuid == null || !expectedEncounterUuid.equals(currentEncounterUuid)) {
			return rejectedEntityDamage(DamageRejection.STALE_ENCOUNTER, currentHealth);
		}
		if (!Float.isFinite(requestedDamage)
				|| requestedDamage <= 0.0F
				|| !Float.isFinite(thresholdHealth)
				|| thresholdHealth < 0.0F) {
			return rejectedEntityDamage(DamageRejection.INVALID_AMOUNT, currentHealth);
		}
		if (!Float.isFinite(currentHealth) || currentHealth <= 0.0F) {
			return rejectedEntityDamage(DamageRejection.COMPLETE, 0.0F);
		}

		float available = Math.max(0.0F, currentHealth - thresholdHealth);
		if (available <= 0.0F) {
			return rejectedEntityDamage(DamageRejection.AT_THRESHOLD, currentHealth);
		}
		float acceptedDamage = Math.min(requestedDamage, available);
		float projectedHealth = Math.max(thresholdHealth, currentHealth - acceptedDamage);
		boolean closesWindow = projectedHealth <= thresholdHealth;
		return new DamageAdmission(
				true,
				acceptedDamage,
				projectedHealth,
				thresholdHealth,
				closesWindow,
				closesWindow && thresholdHealth == 0.0F,
				DamageRejection.NONE
		);
	}

	static State testingState(
			UUID encounterUuid,
			UUID ownerUuid,
			int attempt,
			LectureAct act,
			int actCycle,
			int quizIndex,
			int absenceCount,
			int bossHealth,
			long gameTick,
			LectureRules rules,
			boolean reducedEffects
	) {
		return newWindUp(
				encounterUuid,
				ownerUuid,
				attempt,
				act,
				actCycle,
				quizIndex,
				absenceCount,
				bossHealth,
				gameTick,
				rules,
				reducedEffects
		);
	}

	private static Output resolve(State state, Input.Tick tick) {
		List<Intent> intents = new ArrayList<>(2);
		Resolution resolution;
		int absenceCount = state.absenceCount();
		switch (state.act()) {
			case SLIDE_DECK -> {
				boolean safe = state.safeLane().contains(tick.ownerPosition());
				resolution = safe ? Resolution.SAFE : Resolution.MISSED;
				if (!safe) {
					addNonlethalDamage(
							intents,
							state.rules().slideDeckMissDamage(),
							tick.ownerHealth(),
							Consequence.INFORMATION_OVERLOAD
					);
				}
			}
			case SURPRISE_QUIZ -> {
				var chosen = LectureGeometry.quizPadAt(tick.ownerPosition());
				if (chosen.isEmpty()) {
					resolution = Resolution.NO_ANSWER;
					intents.add(new Intent.Homework(state.rules().maxHomeworkIntentsPerResolve()));
				}
				else if (chosen.get() == state.correctPad()) {
					resolution = Resolution.CORRECT;
				}
				else {
					resolution = Resolution.WRONG;
					intents.add(new Intent.Homework(state.rules().maxHomeworkIntentsPerResolve()));
				}
			}
			case ATTENDANCE_CHECK -> {
				if (LectureGeometry.isInsideAttendanceRing(state.attendanceQuadrant(), tick.ownerPosition())) {
					resolution = Resolution.PRESENT;
				}
				else {
					int previousAbsences = absenceCount;
					absenceCount = Math.min(3, previousAbsences + 1);
					boolean detention = previousAbsences < 3 && absenceCount == 3;
					resolution = detention ? Resolution.DETENTION : Resolution.ABSENT;
					intents.add(new Intent.Attendance(absenceCount, detention));
					if (detention) {
						addNonlethalDamage(
								intents,
								state.rules().detentionDamage(),
								tick.ownerHealth(),
								Consequence.DETENTION
						);
					}
				}
			}
			default -> throw new IllegalStateException("Unhandled Lecture act " + state.act());
		}
		return new Output(copy(
				state,
				Stage.RESOLVE,
				state.phaseStartedTick(),
				state.deadlineTick(),
				absenceCount,
				state.bossHealth(),
				resolution,
				false,
				state.victoryIntentEmitted()
		), intents);
	}

	private static Output settleResolution(State state, long gameTick) {
		boolean opensWindow = switch (state.resolution()) {
			case SAFE, MISSED, CORRECT, PRESENT -> true;
			default -> false;
		};
		if (!opensWindow) {
			return beginRecovery(state, gameTick, false, false);
		}
		long deadline = boundedDeadline(gameTick, state.rules().vulnerabilityTicks());
		State vulnerable = copy(
				state,
				Stage.VULNERABLE,
				gameTick,
				deadline,
				state.absenceCount(),
				state.bossHealth(),
				state.resolution(),
				false,
				state.victoryIntentEmitted()
		);
		return new Output(vulnerable, List.of(new Intent.Vulnerability(
				true,
				state.act().healthThreshold(),
				state.rules().vulnerabilityTicks()
		)));
	}

	private static Output applyDamage(State state, Input.Damage damage) {
		DamageRejection rejection = null;
		if (state.stage() == Stage.COMPLETE) {
			rejection = DamageRejection.COMPLETE;
		}
		else if (state.stage() != Stage.VULNERABLE
				|| damage.gameTick() < state.phaseStartedTick()
				|| damage.gameTick() >= state.deadlineTick()) {
			rejection = DamageRejection.CLOSED_WINDOW;
		}
		else if (!state.ownerUuid().equals(damage.attackerUuid())) {
			rejection = DamageRejection.WRONG_OWNER;
		}
		else if (!state.encounterUuid().equals(damage.encounterUuid())) {
			rejection = DamageRejection.STALE_ENCOUNTER;
		}
		else if (damage.amount() <= 0) {
			rejection = DamageRejection.INVALID_AMOUNT;
		}

		int threshold = state.act().healthThreshold();
		int available = Math.max(0, state.bossHealth() - threshold);
		if (rejection == null && available == 0) {
			rejection = DamageRejection.AT_THRESHOLD;
		}
		if (rejection != null) {
			return new Output(state, List.of(new Intent.DamageRejected(rejection)));
		}

		int accepted = Math.min(damage.amount(), available);
		int nextHealth = state.bossHealth() - accepted;
		Intent.DamageAccepted acceptedIntent = new Intent.DamageAccepted(accepted, nextHealth, threshold);
		if (nextHealth > threshold) {
			return new Output(copy(
					state,
					Stage.VULNERABLE,
					state.phaseStartedTick(),
					state.deadlineTick(),
					state.absenceCount(),
					nextHealth,
					state.resolution(),
					false,
					state.victoryIntentEmitted()
			), List.of(acceptedIntent));
		}

		if (threshold == 0) {
			State complete = copy(
					state,
					Stage.COMPLETE,
					damage.gameTick(),
					damage.gameTick(),
					state.absenceCount(),
					0,
					state.resolution(),
					false,
					true
			);
			return new Output(complete, List.of(
					acceptedIntent,
					new Intent.Vulnerability(false, threshold, 0),
					new Intent.Victory(state.ownerUuid(), state.encounterUuid())
			));
		}

		Output recovery = beginRecovery(copy(
				state,
				Stage.VULNERABLE,
				state.phaseStartedTick(),
				state.deadlineTick(),
				state.absenceCount(),
				nextHealth,
				state.resolution(),
				false,
				state.victoryIntentEmitted()
		), damage.gameTick(), true, true);
		List<Intent> intents = new ArrayList<>(recovery.intents().size() + 1);
		intents.add(acceptedIntent);
		intents.addAll(recovery.intents());
		return new Output(recovery.state(), intents);
	}

	private static Output beginRecovery(State state, long gameTick, boolean advance, boolean closeWindow) {
		long deadline = boundedDeadline(gameTick, state.rules().recoveryTicks());
		State recovery = copy(
				state,
				Stage.RECOVERY,
				gameTick,
				deadline,
				state.absenceCount(),
				state.bossHealth(),
				state.resolution(),
				advance,
				state.victoryIntentEmitted()
		);
		List<Intent> intents = new ArrayList<>(2);
		if (closeWindow) {
			intents.add(new Intent.Vulnerability(false, state.act().healthThreshold(), 0));
		}
		intents.add(new Intent.Recovery(state.act(), state.rules().recoveryTicks(), advance));
		return new Output(recovery, intents);
	}

	private static Output finishRecovery(State state, long gameTick) {
		LectureAct nextAct = state.advanceAfterRecovery()
				? state.act().next().orElse(state.act())
				: state.act();
		int nextCycle = state.advanceAfterRecovery() ? 0 : state.actCycle() + 1;
		int nextQuizIndex = state.quizIndex();
		if (!state.advanceAfterRecovery() && state.act() == LectureAct.SURPRISE_QUIZ) {
			nextQuizIndex++;
		}
		State windUp = newWindUp(
				state.encounterUuid(),
				state.ownerUuid(),
				state.attempt(),
				nextAct,
				nextCycle,
				nextQuizIndex,
				state.absenceCount(),
				state.bossHealth(),
				gameTick,
				state.rules(),
				state.reducedEffects()
		);
		return outputWithTelegraph(windUp);
	}

	private static State newWindUp(
			UUID encounterUuid,
			UUID ownerUuid,
			int attempt,
			LectureAct act,
			int actCycle,
			int quizIndex,
			int absenceCount,
			int bossHealth,
			long gameTick,
			LectureRules rules,
			boolean reducedEffects
	) {
		Objects.requireNonNull(rules, "rules");
		long seed = choiceSeed(encounterUuid, attempt, act, actCycle, quizIndex);
		LectureGeometry.Lane lane = act == LectureAct.SLIDE_DECK
				? choose(LectureGeometry.Lane.values(), seed)
				: null;
		LectureGeometry.QuizPad pad = act == LectureAct.SURPRISE_QUIZ
				? choose(LectureGeometry.QuizPad.values(), seed)
				: null;
		LectureGeometry.AttendanceQuadrant quadrant = act == LectureAct.ATTENDANCE_CHECK
				? choose(LectureGeometry.AttendanceQuadrant.values(), seed)
				: null;
		return new State(
				encounterUuid,
				ownerUuid,
				attempt,
				act,
				Stage.WIND_UP,
				gameTick,
				boundedDeadline(gameTick, act.windUpTicks(rules)),
				actCycle,
				quizIndex,
				absenceCount,
				bossHealth,
				seed,
				lane,
				pad,
				quadrant,
				Resolution.NONE,
				reducedEffects,
				false,
				false,
				rules
		);
	}

	private static Output outputWithTelegraph(State state) {
		String targetName = switch (state.act()) {
			case SLIDE_DECK -> state.safeLane().name();
			case SURPRISE_QUIZ -> state.correctPad().name();
			case ATTENDANCE_CHECK -> state.attendanceQuadrant().name();
		};
		return new Output(state, List.of(new Intent.Telegraph(
				state.act(),
				state.choiceSeed(),
				targetName,
				state.act().windUpTicks(state.rules()),
				state.reducedEffects() ? Density.ESSENTIAL_ONLY : Density.STANDARD
		)));
	}

	private static State copy(
			State state,
			Stage stage,
			long phaseStartedTick,
			long deadlineTick,
			int absenceCount,
			int bossHealth,
			Resolution resolution,
			boolean advanceAfterRecovery,
			boolean victoryIntentEmitted
	) {
		return new State(
				state.encounterUuid(),
				state.ownerUuid(),
				state.attempt(),
				state.act(),
				stage,
				phaseStartedTick,
				deadlineTick,
				state.actCycle(),
				state.quizIndex(),
				absenceCount,
				bossHealth,
				state.choiceSeed(),
				state.safeLane(),
				state.correctPad(),
				state.attendanceQuadrant(),
				resolution,
				state.reducedEffects(),
				advanceAfterRecovery,
				victoryIntentEmitted,
				state.rules()
		);
	}

	private static void addNonlethalDamage(
			List<Intent> intents,
			int configuredDamage,
			int ownerHealth,
			Consequence consequence
	) {
		int boundedDamage = Math.min(configuredDamage, Math.max(0, ownerHealth - 1));
		if (boundedDamage > 0) {
			intents.add(new Intent.DirectDamage(boundedDamage, consequence));
		}
	}

	static long choiceSeed(UUID encounterUuid, int attempt, LectureAct act, int actCycle, int quizIndex) {
		Objects.requireNonNull(encounterUuid, "encounterUuid");
		Objects.requireNonNull(act, "act");
		if (attempt < 1 || actCycle < 0 || quizIndex < 0) {
			throw new IllegalArgumentException("Seed inputs must be bounded non-negative values");
		}
		long seed = encounterUuid.getMostSignificantBits();
		seed ^= Long.rotateLeft(encounterUuid.getLeastSignificantBits(), 29);
		seed ^= ATTEMPT_SALT * attempt;
		seed ^= ACT_SALT * (act.ordinal() + 1L);
		seed ^= CYCLE_SALT * (actCycle + 1L);
		seed ^= QUIZ_SALT * (quizIndex + 1L);
		return mix64(seed);
	}

	private static long mix64(long value) {
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}

	private static <T> T choose(T[] values, long seed) {
		return values[Math.floorMod(seed, values.length)];
	}

	private static long boundedDeadline(long startTick, int durationTicks) {
		if (startTick < 0L || durationTicks <= 0) {
			throw new IllegalArgumentException("Lecture deadlines require non-negative time and positive duration");
		}
		return Math.addExact(startTick, durationTicks);
	}

	private static DamageAdmission rejectedEntityDamage(DamageRejection rejection, float currentHealth) {
		float safeHealth = Float.isFinite(currentHealth) ? Math.max(0.0F, currentHealth) : 0.0F;
		return new DamageAdmission(
				false,
				0.0F,
				safeHealth,
				0.0F,
				false,
				false,
				rejection
		);
	}

	private LectureStateMachine() {
	}
}
