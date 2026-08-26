package dev.developershell.lecture;

import dev.developershell.campaign.CampaignSavedData;
import dev.developershell.registry.ModEntities;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

/** Owns bounded, ephemeral encounter objects; durable progress stays in CampaignSavedData. */
public final class LectureEncounterManager {
	public static final int SLIDE_DECK_TELEGRAPH_TICKS = 100;
	private static final int VULNERABILITY_TICKS = 80;
	private static final int SLIDE_CYCLE_TICKS = SLIDE_DECK_TELEGRAPH_TICKS + VULNERABILITY_TICKS;
	private static final Map<UUID, LectureRuntime> RUNTIMES = new LinkedHashMap<>();

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
				Component.literal("Professor Infinite Slides — SLIDE DECK 5"),
				BossEvent.BossBarColor.PURPLE,
				BossEvent.BossBarOverlay.PROGRESS
		);
		bossBar.setProgress(1.0F);
		bossBar.addPlayer(owner);
		bossBar.setVisible(true);
		RUNTIMES.put(encounterUuid, new LectureRuntime(
				level,
				owner,
				professor,
				bossBar,
				level.getGameTime()
		));
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
			int cycleTick = (int) (elapsed % SLIDE_CYCLE_TICKS);
			boolean open = cycleTick >= SLIDE_DECK_TELEGRAPH_TICKS;
			runtime.setVulnerabilityOpen(open);
			if (open) {
				runtime.bossBar.setName(Component.literal("Professor Infinite Slides — PROJECTOR COOLDOWN — HIT NOW"));
			}
			else {
				int seconds = Math.max(1, (SLIDE_DECK_TELEGRAPH_TICKS - cycleTick + 19) / 20);
				runtime.bossBar.setName(Component.literal("Professor Infinite Slides — SLIDE DECK " + seconds));
			}
			runtime.bossBar.setProgress(Math.max(0.0F, runtime.professor.getHealth() / runtime.professor.getMaxHealth()));
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
		private boolean vulnerabilityOpen;

		private LectureRuntime(
				ServerLevel level,
				ServerPlayer owner,
				ModEntities.ProfessorEntity professor,
				ServerBossEvent bossBar,
				long startedAtGameTime
		) {
			this.level = level;
			this.owner = owner;
			this.professor = professor;
			this.bossBar = bossBar;
			this.startedAtGameTime = startedAtGameTime;
		}

		private UUID encounterUuid() {
			return professor.encounterUuid();
		}

		private void setVulnerabilityOpen(boolean open) {
			vulnerabilityOpen = open;
			professor.setVulnerabilityOpen(open);
		}
	}

	private LectureEncounterManager() {
	}
}
