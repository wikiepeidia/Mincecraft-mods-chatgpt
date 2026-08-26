package dev.developershell.lecture;

import dev.developershell.campaign.CampaignSavedData;
import dev.developershell.campaign.CampaignEvent;
import dev.developershell.campaign.CampaignService;
import dev.developershell.campaign.CampaignTransition;
import dev.developershell.entity.HomeworkAddEntity;
import dev.developershell.registry.ModEntities;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Owns bounded, ephemeral encounter objects; durable progress stays in CampaignSavedData. */
public final class LectureEncounterManager {
	public static final int SLIDE_DECK_TELEGRAPH_TICKS = LectureRules.standard().slideDeckTelegraphTicks();
	public static final int HOMEWORK_ADD_LIFETIME_TICKS = 20 * 20;
	public static final long ENCOUNTER_TIMEOUT_TICKS = 20L * 60L * 20L;
	private static final double ESCAPE_RADIUS_SQUARED = 24.0D * 24.0D;
	private static final Map<UUID, LectureRuntime> RUNTIMES = new LinkedHashMap<>();
	private static LectureRules rules = LectureRules.standard();
	private static boolean reducedEffects;
	private static ExitHandler exitHandler = (runtime, reason) -> false;

	/** Configures the single logical-server manager after stable registries are initialized. */
	public static synchronized void initialize(LectureRules configuredRules, ExitHandler configuredExitHandler) {
		initialize(configuredRules, false, configuredExitHandler);
	}

	/** Binds immutable rules and accessibility density for one logical-server session. */
	public static synchronized void initialize(
			LectureRules configuredRules,
			boolean configuredReducedEffects,
			ExitHandler configuredExitHandler
	) {
		if (!RUNTIMES.isEmpty()) {
			throw new IllegalStateException("Lecture rules cannot change while an encounter is active");
		}
		rules = java.util.Objects.requireNonNull(configuredRules, "configuredRules");
		reducedEffects = configuredReducedEffects;
		exitHandler = java.util.Objects.requireNonNull(configuredExitHandler, "configuredExitHandler");
	}

	public static synchronized boolean start(
			ServerLevel level,
			ServerPlayer owner,
			CampaignSavedData.PlayerProgress progress
	) {
		UUID encounterUuid = progress.encounterUuid();
		if (encounterUuid == null
				|| progress.professorUuid() == null
				|| RUNTIMES.containsKey(encounterUuid)
				|| RUNTIMES.values().stream().anyMatch(runtime -> runtime.owner.getUUID().equals(owner.getUUID()))) {
			return false;
		}

		ModEntities.ProfessorEntity professor = ModEntities.PROFESSOR.create(level, EntitySpawnReason.EVENT);
		if (professor == null) {
			return false;
		}
		professor.setUUID(progress.professorUuid());
		professor.bind(owner.getUUID(), encounterUuid);
		var maxHealth = professor.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(rules.bossMaxHealth());
		}
		professor.setHealth(rules.bossMaxHealth());
		Vec3 spawn = Vec3.atBottomCenterOf(progress.deskPos().relative(progress.deskFacing(), 9));
		professor.snapTo(spawn.x, spawn.y, spawn.z);
		if (!level.addFreshEntity(professor)) {
			professor.discard();
			return false;
		}

		LectureStateMachine.Output initial = LectureStateMachine.start(
				encounterUuid,
				owner.getUUID(),
				progress.attemptCount(),
				level.getGameTime(),
				rules,
				reducedEffects
		);
		LecturePresentation presentation = LecturePresentation.open(
				level,
				owner,
				encounterUuid,
				progress.deskPos(),
				progress.deskFacing(),
				rules
		);
		LectureRuntime runtime = new LectureRuntime(
				level,
				owner,
				professor,
				presentation,
				level.getGameTime(),
				progress.attemptCount(),
				progress.deskPos(),
				progress.deskFacing(),
				rules,
				initial.state()
		);
		RUNTIMES.put(encounterUuid, runtime);
		runtime.applyOutput(initial, level.getGameTime());
		return true;
	}

	public static void tick(MinecraftServer server) {
		for (LectureRuntime runtime : snapshot()) {
			if (runtime.level.getServer() == server) {
				tickRuntime(server, runtime, runtime.level.getGameTime());
			}
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			RewardService.reconcileRemoteReady(player, hasCriticalActionInstruction(player));
		}
	}

	/** Deterministic per-encounter clock seam; production uses each level's game time. */
	public static void tick(MinecraftServer server, UUID encounterUuid, long observedGameTime) {
		java.util.Objects.requireNonNull(encounterUuid, "encounterUuid");
		if (observedGameTime < 0L) {
			throw new IllegalArgumentException("observedGameTime must be non-negative");
		}
		LectureRuntime runtime;
		synchronized (LectureEncounterManager.class) {
			runtime = RUNTIMES.get(encounterUuid);
		}
		if (runtime != null && runtime.level.getServer() == server) {
			tickRuntime(server, runtime, observedGameTime);
		}
	}

	private static void tickRuntime(MinecraftServer server, LectureRuntime runtime, long observedGameTime) {
		CampaignEvent.TerminalReason exit = runtime.exitReason(server, observedGameTime);
		if (exit != null) {
			exitHandler.onExit(runtime.snapshot(), exit);
			return;
		}
		if (!isCurrent(runtime)) {
			return;
		}
		runtime.tickCombat(observedGameTime);
	}

	public static synchronized int activeRuntimeCount() {
		return RUNTIMES.size();
	}

	public static synchronized boolean isVulnerabilityOpen(UUID encounterUuid) {
		LectureRuntime runtime = RUNTIMES.get(encounterUuid);
		return runtime != null && runtime.vulnerabilityOpen;
	}

	public static synchronized Optional<ModEntities.ProfessorEntity> professor(UUID encounterUuid) {
		LectureRuntime runtime = RUNTIMES.get(encounterUuid);
		return runtime == null ? Optional.empty() : Optional.of(runtime.professor);
	}

	/**
	 * Consumes health changed by one admitted entity hit immediately. The exact runtime Professor
	 * identity is required, so stale or manually-created entities can never reach victory.
	 */
	public static synchronized boolean onProfessorDamage(ModEntities.ProfessorEntity professor) {
		java.util.Objects.requireNonNull(professor, "professor");
		UUID encounterUuid = professor.encounterUuid();
		LectureRuntime runtime = encounterUuid == null ? null : RUNTIMES.get(encounterUuid);
		if (runtime == null || runtime.professor != professor || runtime.closed) {
			return false;
		}
		return runtime.synchronizeProfessorDamage(runtime.lastObservedGameTime);
	}

	/** Immutable pure state view for tests/debugging; never exposes mutable runtime ownership. */
	public static synchronized Optional<LectureStateMachine.State> combatState(UUID encounterUuid) {
		LectureRuntime runtime = RUNTIMES.get(encounterUuid);
		return runtime == null ? Optional.empty() : Optional.of(runtime.state);
	}

	public static synchronized Optional<HomeworkAddEntity> homeworkAdd(UUID encounterUuid) {
		LectureRuntime runtime = RUNTIMES.get(encounterUuid);
		return runtime == null || runtime.homeworkAdd == null || runtime.homeworkAdd.isRemoved()
				? Optional.empty()
				: Optional.of(runtime.homeworkAdd);
	}

	/** Entity-side fail-closed ownership guard for live and disk-loaded Homework adds. */
	public static synchronized boolean isHomeworkAddCurrent(
			UUID ownerUuid,
			UUID encounterUuid,
			UUID entityUuid
	) {
		LectureRuntime runtime = RUNTIMES.get(encounterUuid);
		return runtime != null
				&& runtime.owner.getUUID().equals(ownerUuid)
				&& runtime.homeworkAdd != null
				&& !runtime.homeworkAdd.isRemoved()
				&& runtime.homeworkAdd.getUUID().equals(entityUuid);
	}

	public static synchronized Optional<ServerPlayer> participant(UUID encounterUuid) {
		LectureRuntime runtime = RUNTIMES.get(encounterUuid);
		return runtime == null ? Optional.empty() : Optional.of(runtime.owner);
	}

	/** Any live owner encounter reserves the action bar for its current critical instruction. */
	public static synchronized boolean hasCriticalActionInstruction(ServerPlayer player) {
		java.util.Objects.requireNonNull(player, "player");
		return RUNTIMES.values().stream().anyMatch(runtime ->
				!runtime.closed
						&& runtime.level == player.level()
						&& runtime.owner == player
		);
	}

	public static synchronized Optional<RuntimeSnapshot> runtimeSnapshot(UUID encounterUuid) {
		LectureRuntime runtime = RUNTIMES.get(encounterUuid);
		return runtime == null ? Optional.empty() : Optional.of(runtime.snapshot());
	}

	/** Bounded runtime registry view used for stop cleanup; never scans world entities or disk state. */
	public static synchronized java.util.List<RuntimeSnapshot> activeRuntimeSnapshots(MinecraftServer server) {
		java.util.List<RuntimeSnapshot> snapshots = new ArrayList<>();
		for (LectureRuntime runtime : RUNTIMES.values()) {
			if (runtime.level.getServer() == server) {
				snapshots.add(runtime.snapshot());
			}
		}
		return java.util.List.copyOf(snapshots);
	}

	/** Read-only test/debug view; never exposes the mutable boss event or runtime. */
	public static synchronized Optional<PresentationSnapshot> presentation(UUID encounterUuid) {
		LectureRuntime runtime = RUNTIMES.get(encounterUuid);
		return runtime == null ? Optional.empty() : Optional.of(runtime.presentationSnapshot());
	}

	/** Removes presentation/runtime after durable victory has already committed. */
	public static synchronized void finishVictory(UUID encounterUuid) {
		LectureRuntime runtime = RUNTIMES.remove(encounterUuid);
		if (runtime == null) {
			return;
		}
		runtime.close(true);
	}

	/** Idempotent materialized cleanup for failed starts and lifecycle teardown. */
	public static synchronized void cleanup(UUID encounterUuid) {
		LectureRuntime runtime = RUNTIMES.remove(encounterUuid);
		if (runtime == null) {
			return;
		}
		runtime.close(true);
	}

	/** Cleans an already-stale runtime only when every bounded identity field still matches. */
	public static synchronized boolean cleanupIfIdentityMatches(RuntimeSnapshot expected) {
		java.util.Objects.requireNonNull(expected, "expected");
		LectureRuntime runtime = RUNTIMES.get(expected.encounterUuid());
		if (runtime == null || !runtime.matchesIdentity(expected)) {
			return false;
		}
		RUNTIMES.remove(expected.encounterUuid());
		runtime.close(true);
		return true;
	}

	private static synchronized boolean isCurrent(LectureRuntime runtime) {
		return RUNTIMES.get(runtime.encounterUuid()) == runtime;
	}

	private static synchronized java.util.List<LectureRuntime> snapshot() {
		return new ArrayList<>(RUNTIMES.values());
	}

	private static final class LectureRuntime {
		private final ServerLevel level;
		private final ServerPlayer owner;
		private final ModEntities.ProfessorEntity professor;
		private final LecturePresentation presentation;
		private final long startedAtGameTime;
		private final int attemptNumber;
		private final BlockPos deskPos;
		private final Direction deskFacing;
		private final Vec3 arenaCenter;
		private final Set<Entity> ownedEntities;
		private final LectureRules rules;
		private LectureStateMachine.State state;
		private HomeworkAddEntity homeworkAdd;
		private long homeworkAddExpiresAt;
		private int homeworkAddsSpawned;
		private boolean closed;
		private boolean vulnerabilityOpen;
		private long lastObservedGameTime;

		private LectureRuntime(
				ServerLevel level,
				ServerPlayer owner,
				ModEntities.ProfessorEntity professor,
				LecturePresentation presentation,
				long startedAtGameTime,
				int attemptNumber,
				BlockPos deskPos,
				Direction deskFacing,
				LectureRules rules,
				LectureStateMachine.State initialState
		) {
			this.level = level;
			this.owner = owner;
			this.professor = professor;
			this.presentation = presentation;
			this.startedAtGameTime = startedAtGameTime;
			this.attemptNumber = attemptNumber;
			this.deskPos = deskPos.immutable();
			this.deskFacing = deskFacing;
			this.arenaCenter = Vec3.atBottomCenterOf(deskPos.relative(deskFacing, 9));
			this.ownedEntities = new LinkedHashSet<>();
			this.ownedEntities.add(professor);
			this.rules = rules;
			this.state = initialState;
			this.lastObservedGameTime = startedAtGameTime;
		}

		private UUID encounterUuid() {
			return professor.encounterUuid();
		}

		private void setVulnerabilityOpen(boolean open) {
			if (closed) {
				return;
			}
			vulnerabilityOpen = open;
			if (open) {
				// A fresh act window is a new deterministic admission boundary, not the prior hit's i-frame.
				professor.invulnerableTime = 0;
			}
			professor.setVulnerabilityOpen(open);
		}

		private CampaignEvent.TerminalReason exitReason(MinecraftServer server, long observedGameTime) {
			if (owner.isDeadOrDying()) {
				return CampaignEvent.TerminalReason.DEATH;
			}
			if (owner.isRemoved() || server.getPlayerList().getPlayer(owner.getUUID()) != owner) {
				return CampaignEvent.TerminalReason.DISCONNECT;
			}
			if (owner.level() != level) {
				return CampaignEvent.TerminalReason.DIMENSION_CHANGE;
			}
			if (professor.isRemoved()) {
				return CampaignEvent.TerminalReason.ENTITY_UNLOAD;
			}
			double horizontalDistance = owner.position().subtract(arenaCenter).horizontalDistanceSqr();
			if (horizontalDistance > ESCAPE_RADIUS_SQUARED
					|| Math.abs(owner.getY() - arenaCenter.y) > 16.0D) {
				return CampaignEvent.TerminalReason.ESCAPE;
			}
			if (observedGameTime - startedAtGameTime >= ENCOUNTER_TIMEOUT_TICKS) {
				return CampaignEvent.TerminalReason.TIMEOUT;
			}
			return null;
		}

		private RuntimeSnapshot snapshot() {
			Set<UUID> entityUuids = new LinkedHashSet<>();
			for (Entity entity : ownedEntities) {
				entityUuids.add(entity.getUUID());
			}
			return new RuntimeSnapshot(
					level,
					owner.getUUID(),
					encounterUuid(),
					professor.getUUID(),
					attemptNumber,
					startedAtGameTime,
					deskPos,
					deskFacing,
					Set.copyOf(entityUuids)
			);
		}

		private boolean matchesIdentity(RuntimeSnapshot expected) {
			return expected.level() == level
					&& expected.ownerUuid().equals(owner.getUUID())
					&& expected.encounterUuid().equals(encounterUuid())
					&& expected.professorUuid().equals(professor.getUUID())
					&& expected.attemptNumber() == attemptNumber;
		}

		private void tickCombat(long observedGameTime) {
			if (closed) {
				return;
			}
			lastObservedGameTime = Math.max(lastObservedGameTime, observedGameTime);
			maintainHomeworkAdd(observedGameTime);
			synchronizeProfessorDamage(observedGameTime);
			if (closed || !isCurrent(this)) {
				return;
			}
			LectureStateMachine.Output output = LectureStateMachine.step(
					state,
					new LectureStateMachine.Input.Tick(
							observedGameTime,
							ownerLocalPosition(),
							Math.max(1, (int) Math.floor(owner.getHealth()))
					)
			);
			applyOutput(output, observedGameTime);
		}

		private boolean synchronizeProfessorDamage(long observedGameTime) {
			int observedHealth = Math.max(0, Math.round(professor.getHealth()));
			int acceptedDamage = state.bossHealth() - observedHealth;
			if (acceptedDamage <= 0 || state.stage() == LectureStateMachine.Stage.COMPLETE) {
				return false;
			}
			LectureStateMachine.Output output = LectureStateMachine.step(
					state,
					new LectureStateMachine.Input.Damage(
							observedGameTime,
							owner.getUUID(),
							encounterUuid(),
							acceptedDamage
					)
			);
			applyOutput(output, observedGameTime);
			return true;
		}

		private void applyOutput(LectureStateMachine.Output output, long observedGameTime) {
			state = output.state();
			for (LectureStateMachine.Intent intent : output.intents()) {
				if (intent instanceof LectureStateMachine.Intent.DirectDamage damage) {
					// Direct consequences must not disappear behind join/attack invulnerability frames.
					owner.setHealth(Math.max(1.0F, owner.getHealth() - damage.amount()));
				}
				else if (intent instanceof LectureStateMachine.Intent.Homework) {
					spawnHomeworkAdd(observedGameTime);
				}
				else if (intent instanceof LectureStateMachine.Intent.Vulnerability vulnerability) {
					setVulnerabilityOpen(vulnerability.open());
				}
				else if (intent instanceof LectureStateMachine.Intent.Victory victory) {
					commitVictory(victory);
				}
			}
			if (!closed && isCurrent(this)) {
				presentation.render(
						state,
						output.intents(),
						observedGameTime,
						professor.getHealth(),
						professor.getMaxHealth()
				);
			}
		}

		private void commitVictory(LectureStateMachine.Intent.Victory victory) {
			CampaignTransition transition = CampaignService.commitVictory(
					level,
					victory.ownerUuid(),
					victory.encounterUuid(),
					effect -> {
					}
			);
			if (!transition.accepted()) {
				LectureEncounterManager.cleanup(victory.encounterUuid());
				return;
			}
			boolean matchingCleanup = transition.intents().stream().anyMatch(effect ->
					effect instanceof CampaignTransition.EffectIntent.CleanupEncounter cleanup
							&& cleanup.ownerUuid().equals(victory.ownerUuid())
							&& cleanup.encounterUuid().equals(victory.encounterUuid())
							&& cleanup.reason().equals("victory")
			);
			if (!matchingCleanup) {
				LectureEncounterManager.cleanup(victory.encounterUuid());
				return;
			}
			RewardService.reconcileVictory(
					level,
					victory.ownerUuid(),
					victory.encounterUuid(),
					transition
			);
			LectureEncounterManager.finishVictory(victory.encounterUuid());
		}

		private LectureGeometry.LocalPosition ownerLocalPosition() {
			Vec3 deskCenter = Vec3.atBottomCenterOf(deskPos);
			Vec3 delta = owner.position().subtract(deskCenter);
			Direction right = deskFacing.getClockWise();
			double forwardOffset = delta.x * deskFacing.getStepX() + delta.z * deskFacing.getStepZ();
			double rightOffset = delta.x * right.getStepX() + delta.z * right.getStepZ();
			return new LectureGeometry.LocalPosition(forwardOffset, rightOffset);
		}

		private void spawnHomeworkAdd(long observedGameTime) {
			maintainHomeworkAdd(observedGameTime);
			if (homeworkAdd != null || homeworkAddsSpawned >= rules.maxHomeworkAdds()) {
				return;
			}
			HomeworkAddEntity add = ModEntities.HOMEWORK_ADD.create(level, EntitySpawnReason.EVENT);
			if (add == null) {
				return;
			}
			add.bind(owner.getUUID(), encounterUuid());
			BlockPos spawnFeet = deskPos.relative(deskFacing, 9).relative(deskFacing.getClockWise(), 2);
			Vec3 spawn = Vec3.atBottomCenterOf(spawnFeet);
			add.snapTo(spawn.x, spawn.y, spawn.z);
			add.setTarget(owner);
			homeworkAdd = add;
			homeworkAddExpiresAt = Math.addExact(observedGameTime, HOMEWORK_ADD_LIFETIME_TICKS);
			ownedEntities.add(add);
			if (!level.addFreshEntity(add)) {
				ownedEntities.remove(add);
				homeworkAdd = null;
				homeworkAddExpiresAt = 0L;
				add.discard();
				return;
			}
			homeworkAddsSpawned++;
		}

		private void maintainHomeworkAdd(long observedGameTime) {
			if (homeworkAdd == null) {
				return;
			}
			if (homeworkAdd.isRemoved() || observedGameTime >= homeworkAddExpiresAt) {
				if (!homeworkAdd.isRemoved()) {
					homeworkAdd.discard();
				}
				ownedEntities.remove(homeworkAdd);
				homeworkAdd = null;
				homeworkAddExpiresAt = 0L;
				return;
			}
			homeworkAdd.setTarget(owner);
		}

		private void close(boolean discardEntities) {
			if (closed) {
				return;
			}
			closed = true;
			vulnerabilityOpen = false;
			professor.setVulnerabilityOpen(false);
			presentation.close();
			for (Entity entity : java.util.List.copyOf(ownedEntities)) {
				if (!entity.isRemoved() && (discardEntities || entity != professor)) {
					entity.discard();
				}
			}
			homeworkAdd = null;
			homeworkAddExpiresAt = 0L;
			ownedEntities.clear();
		}

		private PresentationSnapshot presentationSnapshot() {
			LecturePresentation.Snapshot snapshot = presentation.snapshot();
			return new PresentationSnapshot(
					snapshot.participantUuids(),
					snapshot.bossBarName(),
					snapshot.currentInstruction(),
					snapshot.actionBarUpdates(),
					snapshot.messageGroups(),
					snapshot.transitionSounds(),
					snapshot.emittedParticles(),
					snapshot.particleBursts(),
					snapshot.act(),
					snapshot.stage(),
					snapshot.targetName(),
					snapshot.density(),
					snapshot.geometrySignature(),
					snapshot.cueIdentity()
			);
		}
	}

	public record PresentationSnapshot(
			Set<UUID> participantUuids,
			Component bossBarName,
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
		public PresentationSnapshot {
			participantUuids = Set.copyOf(participantUuids);
		}
	}

	public record RuntimeSnapshot(
			ServerLevel level,
			UUID ownerUuid,
			UUID encounterUuid,
			UUID professorUuid,
			int attemptNumber,
			long startedAtGameTime,
			BlockPos deskPos,
			Direction deskFacing,
			Set<UUID> ownedEntityUuids
	) {
		public RuntimeSnapshot {
			java.util.Objects.requireNonNull(level, "level");
			java.util.Objects.requireNonNull(ownerUuid, "ownerUuid");
			java.util.Objects.requireNonNull(encounterUuid, "encounterUuid");
			java.util.Objects.requireNonNull(professorUuid, "professorUuid");
			if (attemptNumber < 1 || startedAtGameTime < 0L) {
				throw new IllegalArgumentException("runtime attempt/time must be positive");
			}
			deskPos = java.util.Objects.requireNonNull(deskPos, "deskPos").immutable();
			java.util.Objects.requireNonNull(deskFacing, "deskFacing");
			ownedEntityUuids = Set.copyOf(ownedEntityUuids);
		}
	}

	@FunctionalInterface
	public interface ExitHandler {
		boolean onExit(RuntimeSnapshot runtime, CampaignEvent.TerminalReason reason);
	}

	private LectureEncounterManager() {
	}
}
