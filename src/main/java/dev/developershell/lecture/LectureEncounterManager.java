package dev.developershell.lecture;

import dev.developershell.campaign.CampaignSavedData;
import dev.developershell.registry.ModEntities;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

/** Owns bounded, ephemeral encounter objects; durable progress stays in CampaignSavedData. */
public final class LectureEncounterManager {
	private static final String BOSS_BAR_ACT_KEY = "bossbar.developers_hell.professor.act";
	private static final String ACTION_SLIDE_COUNTDOWN_KEY = "actionbar.developers_hell.lecture.slide_countdown";
	private static final String ACTION_PROJECTOR_COOLDOWN_KEY = "actionbar.developers_hell.lecture.projector_cooldown";
	private static final String CHAT_OBJECTIVE_KEY = "message.developers_hell.lecture.objective";
	private static final String CHAT_SLIDE_START_KEY = "message.developers_hell.lecture.slide_start";
	private static final String SAFE_LANE_CENTER_KEY = "direction.developers_hell.lane.center";
	public static final int SLIDE_DECK_TELEGRAPH_TICKS = LectureRules.standard().slideDeckTelegraphTicks();
	private static final Map<UUID, LectureRuntime> RUNTIMES = new LinkedHashMap<>();
	private static LectureRules rules = LectureRules.standard();

	/** Configures the single logical-server manager after stable registries are initialized. */
	public static synchronized void initialize(LectureRules configuredRules) {
		if (!RUNTIMES.isEmpty()) {
			throw new IllegalStateException("Lecture rules cannot change while an encounter is active");
		}
		rules = java.util.Objects.requireNonNull(configuredRules, "configuredRules");
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
		Vec3 spawn = Vec3.atBottomCenterOf(progress.deskPos().relative(progress.deskFacing(), 9));
		professor.snapTo(spawn.x, spawn.y, spawn.z);
		if (!level.addFreshEntity(professor)) {
			professor.discard();
			return false;
		}

		ServerBossEvent bossBar = new ServerBossEvent(
				encounterUuid,
				Component.translatable(BOSS_BAR_ACT_KEY, 1, 3),
				BossEvent.BossBarColor.YELLOW,
				BossEvent.BossBarOverlay.PROGRESS
		);
		bossBar.setProgress(1.0F);
		bossBar.addPlayer(owner);
		bossBar.setVisible(true);
		LectureRuntime runtime = new LectureRuntime(
				level,
				owner,
				professor,
				bossBar,
				level.getGameTime(),
				rules
		);
		RUNTIMES.put(encounterUuid, runtime);
		runtime.beginPresentation();
		return true;
	}

	public static void tick(MinecraftServer server) {
		for (LectureRuntime runtime : snapshot()) {
			if (runtime.level.getServer() != server) {
				continue;
			}
			if (runtime.owner.isRemoved() || runtime.professor.isRemoved()) {
				cleanup(runtime.encounterUuid());
				continue;
			}
			long elapsed = Math.max(0L, runtime.level.getGameTime() - runtime.startedAtGameTime);
			runtime.tickPresentation((int) (elapsed % runtime.rules.slideCycleTicks()));
		}
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

	public static synchronized Optional<ServerPlayer> participant(UUID encounterUuid) {
		LectureRuntime runtime = RUNTIMES.get(encounterUuid);
		return runtime == null ? Optional.empty() : Optional.of(runtime.owner);
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
		runtime.setVulnerabilityOpen(false);
		runtime.bossBar.setVisible(false);
		runtime.bossBar.removeAllPlayers();
	}

	/** Idempotent materialized cleanup for failed starts and lifecycle teardown. */
	public static synchronized void cleanup(UUID encounterUuid) {
		LectureRuntime runtime = RUNTIMES.remove(encounterUuid);
		if (runtime == null) {
			return;
		}
		runtime.setVulnerabilityOpen(false);
		runtime.bossBar.setVisible(false);
		runtime.bossBar.removeAllPlayers();
		if (!runtime.professor.isRemoved()) {
			runtime.professor.discard();
		}
	}

	private static synchronized java.util.List<LectureRuntime> snapshot() {
		return new ArrayList<>(RUNTIMES.values());
	}

	private static final class LectureRuntime {
		private final ServerLevel level;
		private final ServerPlayer owner;
		private final ModEntities.ProfessorEntity professor;
		private final ServerBossEvent bossBar;
		private final long startedAtGameTime;
		private final LectureRules rules;
		private boolean vulnerabilityOpen;
		private Component currentInstruction;
		private int lastCountdownSeconds = -1;
		private int lastParticleCycleTick = -1;
		private int actionBarUpdates;
		private int messageGroups;
		private int transitionSounds;
		private int particleBursts;
		private int emittedParticles;
		private float lastBossProgress = 1.0F;

		private LectureRuntime(
				ServerLevel level,
				ServerPlayer owner,
				ModEntities.ProfessorEntity professor,
				ServerBossEvent bossBar,
				long startedAtGameTime,
				LectureRules rules
		) {
			this.level = level;
			this.owner = owner;
			this.professor = professor;
			this.bossBar = bossBar;
			this.startedAtGameTime = startedAtGameTime;
			this.rules = rules;
		}

		private UUID encounterUuid() {
			return professor.encounterUuid();
		}

		private void setVulnerabilityOpen(boolean open) {
			vulnerabilityOpen = open;
			professor.setVulnerabilityOpen(open);
		}

		private void beginPresentation() {
			bossBar.setName(Component.translatable(BOSS_BAR_ACT_KEY, 1, 3));
			sendInstruction(slideCountdown(0));
			owner.sendSystemMessage(Component.translatable(CHAT_OBJECTIVE_KEY));
			owner.sendSystemMessage(Component.translatable(CHAT_SLIDE_START_KEY));
			messageGroups++;
			playTransitionSound(SoundEvents.BOOK_PAGE_TURN, 0.85F);
			emitTelegraphParticles(0);
		}

		private void tickPresentation(int cycleTick) {
			boolean open = cycleTick >= rules.slideDeckTelegraphTicks();
			if (open != vulnerabilityOpen) {
				setVulnerabilityOpen(open);
				if (open) {
					lastCountdownSeconds = -1;
					sendInstruction(Component.translatable(ACTION_PROJECTOR_COOLDOWN_KEY));
					playTransitionSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.15F);
				}
				else {
					lastParticleCycleTick = -1;
					sendInstruction(slideCountdown(cycleTick));
					playTransitionSound(SoundEvents.BOOK_PAGE_TURN, 0.85F);
					emitTelegraphParticles(cycleTick);
				}
			}
			else if (!open) {
				sendInstruction(slideCountdown(cycleTick));
				emitTelegraphParticles(cycleTick);
			}

			float progress = Math.max(0.0F, professor.getHealth() / professor.getMaxHealth());
			if (Float.compare(progress, lastBossProgress) != 0) {
				bossBar.setProgress(progress);
				lastBossProgress = progress;
			}
		}

		private Component slideCountdown(int cycleTick) {
			int remainingTicks = Math.max(1, rules.slideDeckTelegraphTicks() - cycleTick);
			int seconds = Math.max(1, (remainingTicks + rules.actionBarUpdateTicks() - 1) / rules.actionBarUpdateTicks());
			if (seconds == lastCountdownSeconds && currentInstruction != null) {
				return currentInstruction;
			}
			lastCountdownSeconds = seconds;
			return Component.translatable(
					ACTION_SLIDE_COUNTDOWN_KEY,
					seconds,
					Component.translatable(SAFE_LANE_CENTER_KEY)
			);
		}

		private void sendInstruction(Component instruction) {
			if (instruction.equals(currentInstruction)) {
				return;
			}
			currentInstruction = instruction;
			owner.sendOverlayMessage(instruction);
			actionBarUpdates++;
		}

		private void playTransitionSound(SoundEvent sound, float pitch) {
			if (transitionSounds >= rules.maxTransitionSoundsPerEncounter()) {
				return;
			}
			owner.playSound(sound, 0.8F, pitch);
			transitionSounds++;
		}

		private void emitTelegraphParticles(int cycleTick) {
			if (particleBursts >= rules.maxParticleBurstsPerEncounter()
					|| cycleTick >= rules.slideDeckTelegraphTicks()
					|| cycleTick % rules.particleRefreshTicks() != 0
					|| cycleTick == lastParticleCycleTick) {
				return;
			}
			lastParticleCycleTick = cycleTick;
			int sent = level.sendParticles(
					owner,
					ParticleTypes.ENCHANT,
					false,
					false,
					professor.getX(),
					professor.getY() + 1.0D,
					professor.getZ(),
					rules.particlesPerRefresh(),
					1.5D,
					0.15D,
					1.5D,
					0.0D
			) ? rules.particlesPerRefresh() : 0;
			particleBursts++;
			emittedParticles += sent;
		}

		private PresentationSnapshot presentationSnapshot() {
			Set<UUID> participantUuids = new LinkedHashSet<>();
			for (ServerPlayer player : bossBar.getPlayers()) {
				participantUuids.add(player.getUUID());
			}
			return new PresentationSnapshot(
					Set.copyOf(participantUuids),
					bossBar.getName().copy(),
					currentInstruction == null ? null : currentInstruction.copy(),
					actionBarUpdates,
					messageGroups,
					transitionSounds,
					emittedParticles
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
			int emittedParticles
	) {
		public PresentationSnapshot {
			participantUuids = Set.copyOf(participantUuids);
		}
	}

	private LectureEncounterManager() {
	}
}
