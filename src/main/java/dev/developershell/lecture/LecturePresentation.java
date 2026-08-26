package dev.developershell.lecture;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Owner-only, server-authoritative presentation for one Professor Infinite Slides encounter.
 *
 * <p>This type projects already-decided {@link LectureStateMachine} state. Particles, sounds,
 * boss-bar copy, and translated text are redundant cues only; none feed back into combat.</p>
 */
public final class LecturePresentation {
	private static final int TICKS_PER_SECOND = 20;
	private static final int MIN_NORMAL_REFRESH_TICKS = 4;
	private static final int MIN_REDUCED_REFRESH_TICKS = 10;
	private static final int ACT_COUNT = LectureAct.values().length;

	private static final String BOSS_ACT_KEY = "bossbar.developers_hell.professor.act";
	private static final String OBJECTIVE_KEY = "message.developers_hell.lecture.objective";
	private static final String SLIDE_START_KEY = "message.developers_hell.lecture.slide_start";
	private static final String QUIZ_PROMPT_KEY = "message.developers_hell.lecture.quiz_prompt";
	private static final String QUIZ_OPTIONS_KEY = "message.developers_hell.lecture.quiz_options";
	private static final String ATTENDANCE_START_KEY = "message.developers_hell.lecture.attendance_start";
	private static final String RESULT_KEY_PREFIX = "message.developers_hell.lecture.result.";
	private static final String WINDOW_KEY = "message.developers_hell.lecture.window";

	private static final String SLIDE_COUNTDOWN_KEY = "actionbar.developers_hell.lecture.slide_countdown";
	private static final String QUIZ_COUNTDOWN_KEY = "actionbar.developers_hell.lecture.quiz_countdown";
	private static final String ATTENDANCE_COUNTDOWN_KEY = "actionbar.developers_hell.lecture.attendance_countdown";
	private static final String PROJECTOR_COOLDOWN_KEY = "actionbar.developers_hell.lecture.projector_cooldown";
	private static final String RECOVERY_KEY = "actionbar.developers_hell.lecture.recovery";
	private static final String COMPLETE_KEY = "actionbar.developers_hell.lecture.complete";
	private static final String RESULT_ACTION_KEY_PREFIX = "actionbar.developers_hell.lecture.result.";

	private final ServerLevel level;
	private final ServerPlayer owner;
	private final UUID encounterUuid;
	private final LectureGeometry.Layout layout;
	private final LectureRules rules;
	private final ServerBossEvent bossBar;

	private boolean closed;
	private Component currentInstruction;
	private String lastTransitionIdentity;
	private long lastParticleTick = Long.MIN_VALUE;
	private int actionBarUpdates;
	private int messageGroups;
	private int transitionSounds;
	private int emittedParticles;
	private int particleBursts;
	private LectureAct act;
	private LectureStateMachine.Stage stage;
	private String targetName;
	private LectureStateMachine.Density density;
	private String geometrySignature;
	private String cueIdentity;

	private LecturePresentation(
			ServerLevel level,
			ServerPlayer owner,
			UUID encounterUuid,
			BlockPos deskPos,
			Direction deskFacing,
			LectureRules rules
	) {
		this.level = Objects.requireNonNull(level, "level");
		this.owner = Objects.requireNonNull(owner, "owner");
		this.encounterUuid = Objects.requireNonNull(encounterUuid, "encounterUuid");
		this.layout = LectureGeometry.layout(deskPos, deskFacing);
		this.rules = Objects.requireNonNull(rules, "rules");
		this.bossBar = new ServerBossEvent(
				encounterUuid,
				Component.translatable(BOSS_ACT_KEY, 1, ACT_COUNT),
				BossEvent.BossBarColor.YELLOW,
				BossEvent.BossBarOverlay.PROGRESS
		);
		bossBar.setProgress(1.0F);
		bossBar.addPlayer(owner);
		bossBar.setVisible(true);
		sendMessageGroup(List.of(Component.translatable(OBJECTIVE_KEY)));
	}

	/** Opens an owner-only presentation. The caller must immediately render the initial state. */
	public static LecturePresentation open(
			ServerLevel level,
			ServerPlayer owner,
			UUID encounterUuid,
			BlockPos deskPos,
			Direction deskFacing,
			LectureRules rules
	) {
		return new LecturePresentation(level, owner, encounterUuid, deskPos, deskFacing, rules);
	}

	/**
	 * Projects a server state transition into bounded, redundant player feedback.
	 * Re-rendering the same state/tick is idempotent for text, sounds, and particle bursts.
	 */
	public synchronized void render(
			LectureStateMachine.State state,
			List<LectureStateMachine.Intent> intents,
			long gameTick,
			float bossHealth,
			float bossMaxHealth
	) {
		Objects.requireNonNull(state, "state");
		intents = List.copyOf(Objects.requireNonNull(intents, "intents"));
		if (closed) {
			return;
		}
		if (!encounterUuid.equals(state.encounterUuid()) || !owner.getUUID().equals(state.ownerUuid())) {
			throw new IllegalArgumentException("Lecture presentation state does not match its owner/encounter");
		}
		if (gameTick < 0L || !Float.isFinite(bossHealth) || !Float.isFinite(bossMaxHealth) || bossMaxHealth <= 0.0F) {
			throw new IllegalArgumentException("Lecture presentation health/time must be finite and bounded");
		}

		act = state.act();
		stage = state.stage();
		targetName = targetName(state);
		density = state.reducedEffects()
				? LectureStateMachine.Density.ESSENTIAL_ONLY
				: LectureStateMachine.Density.STANDARD;
		geometrySignature = geometrySignature(state);
		cueIdentity = cueIdentity(state.act());

		bossBar.setName(Component.translatable(BOSS_ACT_KEY, state.act().number(), ACT_COUNT));
		bossBar.setProgress(Math.max(0.0F, Math.min(1.0F, bossHealth / bossMaxHealth)));
		sendInstruction(instruction(state, gameTick));

		String transitionIdentity = transitionIdentity(state);
		if (!transitionIdentity.equals(lastTransitionIdentity)) {
			lastTransitionIdentity = transitionIdentity;
			renderTransition(state, intents);
		}
		emitGeometry(state, gameTick);
	}

	/** Atomically removes every scheduled/player-facing cue. Later renders are no-ops. */
	public synchronized void close() {
		if (closed) {
			return;
		}
		closed = true;
		bossBar.setVisible(false);
		bossBar.removeAllPlayers();
		currentInstruction = null;
		lastTransitionIdentity = null;
		act = null;
		stage = null;
		targetName = null;
		density = null;
		geometrySignature = null;
		cueIdentity = null;
	}

	/** Reduced effects changes cadence only; the state deadline and semantic geometry are untouched. */
	public static int refreshTicks(LectureRules rules, boolean reducedEffects) {
		Objects.requireNonNull(rules, "rules");
		if (reducedEffects) {
			return Math.max(MIN_REDUCED_REFRESH_TICKS, rules.particleRefreshTicks() * 2);
		}
		return Math.max(MIN_NORMAL_REFRESH_TICKS, rules.particleRefreshTicks());
	}

	/** Stable non-color shape identity used by tests, accessibility copy, and debug snapshots. */
	public static String geometrySignature(LectureStateMachine.State state) {
		Objects.requireNonNull(state, "state");
		return switch (state.act()) {
			case SLIDE_DECK -> "SAFE " + state.safeLane().name()
					+ " DIAMOND; DANGER " + dangerLanes(state.safeLane()) + " X";
			case SURPRISE_QUIZ -> "A SQUARE; B CIRCLE; C DIAMOND; TARGET "
					+ state.correctPad().name();
			case ATTENDANCE_CHECK -> "RING " + state.attendanceQuadrant().name()
					+ "; CENTER MARKER";
		};
	}

	public synchronized Snapshot snapshot() {
		Set<UUID> participants = new LinkedHashSet<>();
		for (ServerPlayer player : bossBar.getPlayers()) {
			participants.add(player.getUUID());
		}
		return new Snapshot(
				participants,
				bossBar.getName(),
				currentInstruction,
				actionBarUpdates,
				messageGroups,
				transitionSounds,
				emittedParticles,
				particleBursts,
				act,
				stage,
				targetName,
				density,
				geometrySignature,
				cueIdentity
		);
	}

	private void renderTransition(
			LectureStateMachine.State state,
			List<LectureStateMachine.Intent> intents
	) {
		if (state.stage() == LectureStateMachine.Stage.WIND_UP) {
			switch (state.act()) {
				case SLIDE_DECK -> sendMessageGroup(List.of(Component.translatable(SLIDE_START_KEY)));
				case SURPRISE_QUIZ -> sendMessageGroup(List.of(
						Component.translatable(QUIZ_PROMPT_KEY, targetComponent(state)),
						Component.translatable(QUIZ_OPTIONS_KEY)
				));
				case ATTENDANCE_CHECK -> sendMessageGroup(List.of(
						Component.translatable(ATTENDANCE_START_KEY, targetComponent(state))
				));
			}
			playTransitionSound(actSound(state.act()), actPitch(state.act()));
		}
		else if (state.stage() == LectureStateMachine.Stage.RESOLVE) {
			sendMessageGroup(List.of(Component.translatable(
					RESULT_KEY_PREFIX + resolutionId(state.resolution())
			)));
			playTransitionSound(resolutionSound(state, intents), 0.9F);
		}
		else if (state.stage() == LectureStateMachine.Stage.VULNERABLE) {
			sendMessageGroup(List.of(Component.translatable(WINDOW_KEY)));
		}
	}

	private Component instruction(LectureStateMachine.State state, long gameTick) {
		return switch (state.stage()) {
			case WIND_UP -> switch (state.act()) {
				case SLIDE_DECK -> Component.translatable(
						SLIDE_COUNTDOWN_KEY,
						remainingSeconds(state, gameTick),
						targetComponent(state)
				);
				case SURPRISE_QUIZ -> Component.translatable(
						QUIZ_COUNTDOWN_KEY,
						remainingSeconds(state, gameTick)
				);
				case ATTENDANCE_CHECK -> Component.translatable(
						ATTENDANCE_COUNTDOWN_KEY,
						remainingSeconds(state, gameTick),
						targetComponent(state)
				);
			};
			case RESOLVE -> Component.translatable(
					RESULT_ACTION_KEY_PREFIX + resolutionId(state.resolution())
			);
			case VULNERABLE -> Component.translatable(PROJECTOR_COOLDOWN_KEY);
			case RECOVERY -> Component.translatable(RECOVERY_KEY, remainingSeconds(state, gameTick));
			case COMPLETE -> Component.translatable(COMPLETE_KEY);
		};
	}

	private void sendInstruction(Component instruction) {
		if (instruction.equals(currentInstruction)) {
			return;
		}
		currentInstruction = instruction.copy();
		owner.sendOverlayMessage(instruction);
		actionBarUpdates++;
	}

	private void sendMessageGroup(List<Component> messages) {
		if (closed || messages.isEmpty() || messageGroups >= maximumMessageGroups()) {
			return;
		}
		for (Component message : messages) {
			owner.sendSystemMessage(message);
		}
		messageGroups++;
	}

	private int maximumMessageGroups() {
		return rules.maxTransitionSoundsPerEncounter() * 2 + 1;
	}

	private void playTransitionSound(SoundEvent sound, float pitch) {
		if (closed || transitionSounds >= rules.maxTransitionSoundsPerEncounter()) {
			return;
		}
		owner.playSound(sound, 0.8F, pitch);
		transitionSounds++;
	}

	private void emitGeometry(LectureStateMachine.State state, long gameTick) {
		if (state.stage() != LectureStateMachine.Stage.WIND_UP
				|| gameTick < state.phaseStartedTick()
				|| gameTick >= state.deadlineTick()
				|| particleBursts >= rules.maxParticleBurstsPerEncounter()
				|| emittedParticles >= rules.maxParticlesPerEncounter()
				|| gameTick == lastParticleTick) {
			return;
		}
		long elapsedTicks = gameTick - state.phaseStartedTick();
		int refreshTicks = refreshTicks(rules, state.reducedEffects());
		if (elapsedTicks % refreshTicks != 0L) {
			return;
		}

		lastParticleTick = gameTick;
		List<Vec3> points = geometryPoints(state);
		int densityCount = state.reducedEffects()
				? Math.max(1, rules.particlesPerRefresh() / 2)
				: rules.particlesPerRefresh();
		int sendCount = Math.min(
				Math.min(densityCount, points.size()),
				rules.maxParticlesPerEncounter() - emittedParticles
		);
		int offset = Math.floorMod(particleBursts * Math.max(1, densityCount), points.size());
		ParticleOptions particle = particleFor(state.act());
		for (int index = 0; index < sendCount; index++) {
			Vec3 point = points.get((offset + index) % points.size());
			if (level.sendParticles(
					owner,
					particle,
					false,
					false,
					point.x,
					point.y,
					point.z,
					1,
					0.0D,
					0.0D,
					0.0D,
					0.0D
			)) {
				emittedParticles++;
			}
		}
		particleBursts++;
	}

	private List<Vec3> geometryPoints(LectureStateMachine.State state) {
		return switch (state.act()) {
			case SLIDE_DECK -> slidePoints(state.safeLane());
			case SURPRISE_QUIZ -> quizPoints();
			case ATTENDANCE_CHECK -> attendancePoints(state.attendanceQuadrant());
		};
	}

	private List<Vec3> slidePoints(LectureGeometry.Lane safeLane) {
		List<Vec3> points = new ArrayList<>(24);
		double safeCenter = laneCenter(safeLane);
		addDiamond(points, 9.0D, safeCenter, 1.6D);
		for (LectureGeometry.Lane lane : LectureGeometry.Lane.values()) {
			if (lane != safeLane) {
				addX(points, 9.0D, laneCenter(lane), 1.5D);
			}
		}
		return List.copyOf(points);
	}

	private List<Vec3> quizPoints() {
		List<Vec3> points = new ArrayList<>(24);
		addSquare(points, 9.0D, LectureGeometry.QuizPad.A.rightAnchor(), 1.35D);
		addCircle(points, 9.0D, LectureGeometry.QuizPad.B.rightAnchor(), 1.35D);
		addDiamond(points, 9.0D, LectureGeometry.QuizPad.C.rightAnchor(), 1.6D);
		return List.copyOf(points);
	}

	private List<Vec3> attendancePoints(LectureGeometry.AttendanceQuadrant quadrant) {
		LectureGeometry.LocalPosition center = LectureGeometry.attendanceCenter(quadrant);
		List<Vec3> points = new ArrayList<>(17);
		for (int index = 0; index < 12; index++) {
			double angle = Math.PI * 2.0D * index / 12.0D;
			points.add(worldPoint(
					center.forwardOffset() + Math.cos(angle) * LectureGeometry.ATTENDANCE_RADIUS,
					center.rightOffset() + Math.sin(angle) * LectureGeometry.ATTENDANCE_RADIUS
			));
		}
		points.add(worldPoint(center.forwardOffset(), center.rightOffset()));
		points.add(worldPoint(center.forwardOffset() - 0.75D, center.rightOffset()));
		points.add(worldPoint(center.forwardOffset() + 0.75D, center.rightOffset()));
		points.add(worldPoint(center.forwardOffset(), center.rightOffset() - 0.75D));
		points.add(worldPoint(center.forwardOffset(), center.rightOffset() + 0.75D));
		return List.copyOf(points);
	}

	private void addSquare(List<Vec3> points, double forward, double right, double radius) {
		for (int step = -1; step <= 1; step++) {
			points.add(worldPoint(forward - radius, right + step * radius));
			points.add(worldPoint(forward + radius, right + step * radius));
		}
		points.add(worldPoint(forward, right - radius));
		points.add(worldPoint(forward, right + radius));
	}

	private void addCircle(List<Vec3> points, double forward, double right, double radius) {
		for (int index = 0; index < 8; index++) {
			double angle = Math.PI * 2.0D * index / 8.0D;
			points.add(worldPoint(forward + Math.cos(angle) * radius, right + Math.sin(angle) * radius));
		}
	}

	private void addDiamond(List<Vec3> points, double forward, double right, double radius) {
		points.add(worldPoint(forward - radius, right));
		points.add(worldPoint(forward - radius / 2.0D, right - radius / 2.0D));
		points.add(worldPoint(forward, right - radius));
		points.add(worldPoint(forward + radius / 2.0D, right - radius / 2.0D));
		points.add(worldPoint(forward + radius, right));
		points.add(worldPoint(forward + radius / 2.0D, right + radius / 2.0D));
		points.add(worldPoint(forward, right + radius));
		points.add(worldPoint(forward - radius / 2.0D, right + radius / 2.0D));
	}

	private void addX(List<Vec3> points, double forward, double right, double radius) {
		points.add(worldPoint(forward - radius, right - radius));
		points.add(worldPoint(forward - radius / 2.0D, right - radius / 2.0D));
		points.add(worldPoint(forward, right));
		points.add(worldPoint(forward + radius / 2.0D, right + radius / 2.0D));
		points.add(worldPoint(forward + radius, right + radius));
		points.add(worldPoint(forward - radius, right + radius));
		points.add(worldPoint(forward - radius / 2.0D, right + radius / 2.0D));
		points.add(worldPoint(forward + radius / 2.0D, right - radius / 2.0D));
		points.add(worldPoint(forward + radius, right - radius));
	}

	private Vec3 worldPoint(double forwardOffset, double rightOffset) {
		BlockPos desk = layout.deskPos();
		Direction forward = layout.forward();
		Direction right = layout.right();
		return new Vec3(
				desk.getX() + 0.5D + forward.getStepX() * forwardOffset + right.getStepX() * rightOffset,
				desk.getY() + 0.15D,
				desk.getZ() + 0.5D + forward.getStepZ() * forwardOffset + right.getStepZ() * rightOffset
		);
	}

	private static int remainingSeconds(LectureStateMachine.State state, long gameTick) {
		long remainingTicks = Math.max(1L, state.deadlineTick() - gameTick);
		return (int) Math.max(1L, (remainingTicks + TICKS_PER_SECOND - 1L) / TICKS_PER_SECOND);
	}

	private static String transitionIdentity(LectureStateMachine.State state) {
		return state.act().name() + ':'
				+ state.stage().name() + ':'
				+ state.phaseStartedTick() + ':'
				+ state.resolution().name();
	}

	private static String targetName(LectureStateMachine.State state) {
		return switch (state.act()) {
			case SLIDE_DECK -> state.safeLane().name();
			case SURPRISE_QUIZ -> state.correctPad().name();
			case ATTENDANCE_CHECK -> state.attendanceQuadrant().name();
		};
	}

	private static Component targetComponent(LectureStateMachine.State state) {
		return switch (state.act()) {
			case SLIDE_DECK -> Component.translatable(
					"direction.developers_hell.lane." + state.safeLane().name().toLowerCase(Locale.ROOT)
			);
			case SURPRISE_QUIZ -> Component.translatable(
					"direction.developers_hell.quiz_pad." + state.correctPad().name().toLowerCase(Locale.ROOT)
			);
			case ATTENDANCE_CHECK -> Component.translatable(
					"direction.developers_hell.attendance."
							+ state.attendanceQuadrant().name().toLowerCase(Locale.ROOT)
			);
		};
	}

	private static String dangerLanes(LectureGeometry.Lane safeLane) {
		List<String> danger = new ArrayList<>(2);
		for (LectureGeometry.Lane lane : LectureGeometry.Lane.values()) {
			if (lane != safeLane) {
				danger.add(lane.name());
			}
		}
		return String.join("+", danger);
	}

	private static double laneCenter(LectureGeometry.Lane lane) {
		List<Integer> offsets = lane.rightOffsets();
		return (offsets.getFirst() + offsets.getLast()) / 2.0D;
	}

	private static String cueIdentity(LectureAct act) {
		return switch (act) {
			case SLIDE_DECK -> "slide";
			case SURPRISE_QUIZ -> "quiz";
			case ATTENDANCE_CHECK -> "attendance";
		};
	}

	private static SoundEvent actSound(LectureAct act) {
		return switch (act) {
			case SLIDE_DECK -> SoundEvents.BOOK_PAGE_TURN;
			case SURPRISE_QUIZ -> SoundEvents.PLAYER_LEVELUP;
			case ATTENDANCE_CHECK -> SoundEvents.BELL_BLOCK;
		};
	}

	private static float actPitch(LectureAct act) {
		return switch (act) {
			case SLIDE_DECK -> 0.85F;
			case SURPRISE_QUIZ -> 1.2F;
			case ATTENDANCE_CHECK -> 0.7F;
		};
	}

	private static SoundEvent resolutionSound(
			LectureStateMachine.State state,
			List<LectureStateMachine.Intent> intents
	) {
		boolean adverseIntent = intents.stream().anyMatch(intent ->
				intent instanceof LectureStateMachine.Intent.DirectDamage
						|| intent instanceof LectureStateMachine.Intent.Homework
						|| intent instanceof LectureStateMachine.Intent.Attendance attendance && attendance.detention()
		);
		if (adverseIntent) {
			return SoundEvents.VILLAGER_NO;
		}
		return switch (state.resolution()) {
			case SAFE, CORRECT, PRESENT -> SoundEvents.VILLAGER_YES;
			default -> SoundEvents.VILLAGER_NO;
		};
	}

	private static ParticleOptions particleFor(LectureAct act) {
		return switch (act) {
			case SLIDE_DECK -> ParticleTypes.ENCHANT;
			case SURPRISE_QUIZ -> ParticleTypes.END_ROD;
			case ATTENDANCE_CHECK -> ParticleTypes.HAPPY_VILLAGER;
		};
	}

	private static String resolutionId(LectureStateMachine.Resolution resolution) {
		return resolution.name().toLowerCase(Locale.ROOT);
	}

	/** Immutable debug/test view; all mutable component/set inputs are defensively copied. */
	public record Snapshot(
			Set<UUID> participantUuids,
			Component bossName,
			Component currentInstruction,
			int actionBarUpdates,
			int messageGroups,
			int transitionSounds,
			int emittedParticles,
			int particleBursts,
			LectureAct act,
			LectureStateMachine.Stage stage,
			String targetName,
			LectureStateMachine.Density density,
			String geometrySignature,
			String cueIdentity
	) {
		public Snapshot {
			participantUuids = Set.copyOf(Objects.requireNonNull(participantUuids, "participantUuids"));
			bossName = Objects.requireNonNull(bossName, "bossName").copy();
			currentInstruction = currentInstruction == null ? null : currentInstruction.copy();
			if (actionBarUpdates < 0
					|| messageGroups < 0
					|| transitionSounds < 0
					|| emittedParticles < 0
					|| particleBursts < 0) {
				throw new IllegalArgumentException("Lecture presentation counters cannot be negative");
			}
		}

		/** Compatibility name used by the encounter manager's public projection. */
		public Component bossBarName() {
			return bossName.copy();
		}
	}
}
