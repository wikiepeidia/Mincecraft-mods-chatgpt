package dev.developershell.bossrush;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Small server-authoritative Contract-to-Diploma runtime built entirely from vanilla entities.
 * Durable checkpoints live in {@link BossRushSavedData}; this type owns only bounded live objects.
 */
public final class BossRushManager {
	public static final int CHAPTER_DELAY_TICKS = 60;
	public static final int SPONSOR_COUNTDOWN_TICKS = 100;
	private static final double ESCAPE_RADIUS_SQUARED = 32.0D * 32.0D;
	private static final String ENTITY_TAG = "developers_hell_bossrush_owned";

	private final boolean campaignEnabled;
	private final Map<UUID, ActiveEncounter> activeByOwner = new LinkedHashMap<>();
	private final Map<UUID, PendingStart> pendingByOwner = new LinkedHashMap<>();
	private boolean lifecycleRegistered;

	public BossRushManager(boolean campaignEnabled) {
		this.campaignEnabled = campaignEnabled;
	}

	/** Registers callbacks exactly once for this session-owned manager. */
	public synchronized void registerLifecycle() {
		if (lifecycleRegistered) {
			return;
		}
		lifecycleRegistered = true;
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(this::allowDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				abort(player, "death");
			}
			else {
				markOwnedDeath(entity);
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> abort(player, "disconnect"));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity.isAlive()) {
				ownerForEntity(entity.getUUID()).ifPresent(owner -> abort(owner, "entity_unload"));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
	}

	public synchronized StartResult start(ServerPlayer player) {
		Objects.requireNonNull(player, "player");
		if (!campaignEnabled) {
			player.sendSystemMessage(Component.translatable("message.developers_hell.campaign.disabled"));
			return StartResult.DISABLED;
		}
		ActiveEncounter existing = activeByOwner.get(player.getUUID());
		if (existing != null) {
			if (existing.stage == BossRushStage.SPONSOR) {
				existing.nextActionTick = player.level().getGameTime();
				player.sendSystemMessage(Component.translatable(
						"message.developers_hell.bossrush.sponsor.skipped"));
				return StartResult.SPONSOR_SKIPPED;
			}
			player.sendSystemMessage(Component.translatable(
					"message.developers_hell.bossrush.already_active"));
			return StartResult.ALREADY_ACTIVE;
		}

		pendingByOwner.remove(player.getUUID());
		BossRushSavedData savedData = BossRushSavedData.get(player.level());
		BossRushProgress progress = savedData.normalizeRestart(player.getUUID());
		return switch (progress.stage()) {
			case READY_JURY -> beginPersisted(player, savedData, progress, BossRushStage.JURY);
			case READY_CHAIRMAN -> beginPersisted(player, savedData, progress, BossRushStage.CHAIRMAN);
			case SPONSOR -> startSponsor(player, false) ? StartResult.STARTED : StartResult.SPAWN_FAILED;
			case GRADUATED -> {
				player.sendSystemMessage(Component.translatable(
						"message.developers_hell.bossrush.graduated"));
				yield StartResult.GRADUATED;
			}
			default -> StartResult.SPAWN_FAILED;
		};
	}

	public synchronized StartResult replay(ServerPlayer player, ReplayBoss boss) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(boss, "boss");
		if (activeByOwner.containsKey(player.getUUID())) {
			return StartResult.ALREADY_ACTIVE;
		}
		if (BossRushSavedData.get(player.level()).normalizeRestart(player.getUUID()).stage()
				!= BossRushStage.GRADUATED) {
			player.sendSystemMessage(Component.translatable(
					"message.developers_hell.bossrush.replay_locked"));
			return StartResult.REPLAY_LOCKED;
		}
		pendingByOwner.remove(player.getUUID());
		boolean started = switch (boss) {
			case JURY -> startFight(player, BossRushStage.JURY, true);
			case CHAIRMAN -> startFight(player, BossRushStage.CHAIRMAN, true);
			case CODEX -> startSponsor(player, true);
		};
		return started ? StartResult.STARTED : StartResult.SPAWN_FAILED;
	}

	public synchronized boolean abort(ServerPlayer player, String reason) {
		Objects.requireNonNull(player, "player");
		return abort(player.getUUID(), reason);
	}

	public synchronized BossRushProgress status(ServerPlayer player) {
		Objects.requireNonNull(player, "player");
		return BossRushSavedData.get(player.level()).normalizeRestart(player.getUUID());
	}

	public void tick(MinecraftServer server) {
		Objects.requireNonNull(server, "server");
		for (PendingStart pending : pendingSnapshot()) {
			if (pending.server != server || server.getTickCount() < pending.dueServerTick) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(pending.ownerUuid);
			synchronized (this) {
				pendingByOwner.remove(pending.ownerUuid, pending);
			}
			if (player != null && player.isAlive()) {
				start(player);
			}
		}
		for (ActiveEncounter encounter : activeSnapshot()) {
			if (encounter.level.getServer() == server) {
				tickEncounter(encounter);
			}
		}
	}

	public synchronized int onServerStopping(MinecraftServer server) {
		int cleaned = 0;
		for (ActiveEncounter encounter : activeSnapshot()) {
			if (encounter.level.getServer() == server && abort(encounter.ownerUuid, "server_stop")) {
				cleaned++;
			}
		}
		pendingByOwner.entrySet().removeIf(entry -> entry.getValue().server == server);
		return cleaned;
	}

	private StartResult beginPersisted(
			ServerPlayer player,
			BossRushSavedData savedData,
			BossRushProgress ready,
			BossRushStage liveStage
	) {
		BossRushProgress active = savedData.begin(player.getUUID(), liveStage);
		if (startFight(player, liveStage, false)) {
			return StartResult.STARTED;
		}
		savedData.restoreIfCurrent(player.getUUID(), active, ready);
		return StartResult.SPAWN_FAILED;
	}

	private synchronized boolean startSponsor(ServerPlayer player, boolean replay) {
		ActiveEncounter encounter = ActiveEncounter.open(player, BossRushStage.SPONSOR, replay);
		Villager sponsor = EntityTypes.VILLAGER.create(player.level(), EntitySpawnReason.EVENT);
		if (sponsor == null) {
			encounter.close();
			return false;
		}
		prepareMob(sponsor, player, "entity.developers_hell.rich_sponsor", 40.0F, true);
		sponsor.setInvulnerable(true);
		sponsor.setNoAi(true);
		sponsor.setGlowingTag(true);
		sponsor.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GLOWSTONE_DUST));
		spawnAt(encounter, sponsor, 0, 1);
		if (!addOwned(encounter, sponsor, Role.SPONSOR)) {
			encounter.close();
			return false;
		}
		encounter.nextActionTick = player.level().getGameTime() + SPONSOR_COUNTDOWN_TICKS;
		encounter.bar.setName(Component.translatable("bossbar.developers_hell.sponsor"));
		player.sendSystemMessage(Component.translatable("message.developers_hell.bossrush.sponsor.start"));
		activeByOwner.put(player.getUUID(), encounter);
		return true;
	}

	private synchronized boolean startFight(ServerPlayer player, BossRushStage stage, boolean replay) {
		ActiveEncounter encounter = ActiveEncounter.open(player, stage, replay);
		boolean spawned = switch (stage) {
			case JURY -> spawnJury(encounter);
			case CHAIRMAN -> spawnChairman(encounter);
			case CODEX -> spawnCodex(encounter);
			default -> false;
		};
		if (!spawned) {
			encounter.close();
			return false;
		}
		activeByOwner.put(player.getUUID(), encounter);
		player.sendSystemMessage(Component.translatable(
				"message.developers_hell.bossrush.started." + stage.serializedName()));
		return true;
	}

	private boolean spawnJury(ActiveEncounter encounter) {
		for (int index = 0; index < 2; index++) {
			Silverfish clerk = EntityTypes.SILVERFISH.create(encounter.level, EntitySpawnReason.EVENT);
			if (clerk == null) {
				return false;
			}
			prepareMob(clerk, encounter.owner, "entity.developers_hell.citation_clerk", 12.0F, false);
			spawnAt(encounter, clerk, index == 0 ? -3 : 3, 3);
			if (!addOwned(encounter, clerk, Role.EVIDENCE)) {
				return false;
			}
		}
		for (int index = 0; index < 3; index++) {
			Vindicator juror = EntityTypes.VINDICATOR.create(encounter.level, EntitySpawnReason.EVENT);
			if (juror == null) {
				return false;
			}
			prepareMob(juror, encounter.owner, "entity.developers_hell.juror." + (index + 1), 28.0F, false);
			juror.setGlowingTag(true);
			juror.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
			spawnAt(encounter, juror, (index - 1) * 3, 7);
			if (!addOwned(encounter, juror, Role.values()[Role.JUROR_ONE.ordinal() + index])) {
				return false;
			}
		}
		encounter.bar.setName(Component.translatable("bossbar.developers_hell.jury"));
		return true;
	}

	private boolean spawnChairman(ActiveEncounter encounter) {
		Evoker chairman = EntityTypes.EVOKER.create(encounter.level, EntitySpawnReason.EVENT);
		if (chairman == null) {
			return false;
		}
		prepareMob(chairman, encounter.owner, "entity.developers_hell.chairman", 80.0F, false);
		chairman.setGlowingTag(true);
		chairman.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
		spawnAt(encounter, chairman, 0, 7);
		if (!addOwned(encounter, chairman, Role.CHAIRMAN)) {
			return false;
		}
		for (int index = 0; index < 3; index++) {
			Silverfish node = EntityTypes.SILVERFISH.create(encounter.level, EntitySpawnReason.EVENT);
			if (node == null) {
				return false;
			}
			prepareMob(node, encounter.owner, "entity.developers_hell.rubric_node", 16.0F, false);
			node.setGlowingTag(true);
			spawnAt(encounter, node, (index - 1) * 4, 3);
			if (!addOwned(encounter, node, Role.RUBRIC)) {
				return false;
			}
		}
		encounter.bar.setName(Component.translatable("bossbar.developers_hell.chairman"));
		return true;
	}

	private boolean spawnCodex(ActiveEncounter encounter) {
		WitherSkeleton codex = EntityTypes.WITHER_SKELETON.create(encounter.level, EntitySpawnReason.EVENT);
		if (codex == null) {
			return false;
		}
		prepareMob(codex, encounter.owner, "entity.developers_hell.codex_overdraft", 110.0F, false);
		codex.setGlowingTag(true);
		codex.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
		codex.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
		spawnAt(encounter, codex, 0, 8);
		if (!addOwned(encounter, codex, Role.CODEX)) {
			return false;
		}
		String[] names = {"builder", "reviewer", "executor"};
		for (int index = 0; index < names.length; index++) {
			Vex agent = EntityTypes.VEX.create(encounter.level, EntitySpawnReason.EVENT);
			if (agent == null) {
				return false;
			}
			prepareMob(agent, encounter.owner, "entity.developers_hell.agent." + names[index], 18.0F, false);
			agent.setGlowingTag(true);
			spawnAt(encounter, agent, (index - 1) * 4, 4);
			if (!addOwned(encounter, agent, Role.AGENT)) {
				return false;
			}
		}
		encounter.bar.setName(Component.translatable("bossbar.developers_hell.codex"));
		return true;
	}

	private void tickEncounter(ActiveEncounter encounter) {
		ServerPlayer owner = encounter.level.getServer().getPlayerList().getPlayer(encounter.ownerUuid);
		if (owner == null || !owner.isAlive() || owner.level() != encounter.level
				|| owner.position().distanceToSqr(Vec3.atCenterOf(encounter.origin)) > ESCAPE_RADIUS_SQUARED) {
			abort(encounter.ownerUuid, "owner_exit");
			return;
		}
		if (encounter.ledger.advance(1)) {
			abort(encounter.ownerUuid, "timeout");
			return;
		}

		long gameTime = encounter.level.getGameTime();
		if (encounter.stage == BossRushStage.SPONSOR) {
			float remaining = Math.max(0.0F,
					(encounter.nextActionTick - gameTime) / (float) SPONSOR_COUNTDOWN_TICKS);
			encounter.bar.setProgress(remaining);
			if (gameTime >= encounter.nextActionTick) {
				transformSponsor(encounter);
			}
			return;
		}

		encounter.deadEntities.addAll(encounter.ledger.ownedEntityUuids().stream()
				.filter(uuid -> {
					Entity entity = encounter.level.getEntityInAnyDimension(uuid);
					return entity != null && entity instanceof LivingEntity living && !living.isAlive();
				}).toList());
		if (encounter.stage == BossRushStage.JURY) {
			tickJury(encounter);
		}
		else if (encounter.stage == BossRushStage.CHAIRMAN) {
			tickChairman(encounter);
		}
		else if (encounter.stage == BossRushStage.CODEX) {
			tickCodex(encounter);
		}
		updateBossBar(encounter);
	}

	private void tickJury(ActiveEncounter encounter) {
		if (encounter.allDead(Role.EVIDENCE)) {
			int aliveJuror = encounter.firstAlive(Role.JUROR_ONE, Role.JUROR_TWO, Role.JUROR_THREE);
			if (aliveJuror < 0) {
				completeFight(encounter);
				return;
			}
			if (encounter.phase != aliveJuror + 1) {
				encounter.phase = aliveJuror + 1;
				encounter.owner.sendSystemMessage(Component.translatable(
						"message.developers_hell.bossrush.jury.but_why", encounter.phase));
			}
		}
	}

	private void tickChairman(ActiveEncounter encounter) {
		if (encounter.allDead(Role.RUBRIC) && encounter.allDead(Role.CHAIRMAN)) {
			completeFight(encounter);
		}
	}

	private void tickCodex(ActiveEncounter encounter) {
		if (encounter.allDead(Role.AGENT) && encounter.allDead(Role.CODEX)) {
			completeFight(encounter);
		}
	}

	private synchronized void transformSponsor(ActiveEncounter sponsor) {
		if (activeByOwner.get(sponsor.ownerUuid) != sponsor) {
			return;
		}
		activeByOwner.remove(sponsor.ownerUuid);
		sponsor.close();
		if (!sponsor.replay) {
			BossRushSavedData data = BossRushSavedData.get(sponsor.level);
			BossRushProgress before = data.snapshot(sponsor.ownerUuid);
			BossRushProgress active = data.begin(sponsor.ownerUuid, BossRushStage.CODEX);
			if (!startFight(sponsor.owner, BossRushStage.CODEX, false)) {
				data.restoreIfCurrent(sponsor.ownerUuid, active, before);
			}
		}
		else {
			startFight(sponsor.owner, BossRushStage.CODEX, true);
		}
	}

	private synchronized void completeFight(ActiveEncounter encounter) {
		if (activeByOwner.get(encounter.ownerUuid) != encounter) {
			return;
		}
		activeByOwner.remove(encounter.ownerUuid);
		encounter.close();
		if (encounter.replay) {
			encounter.owner.sendSystemMessage(Component.translatable(
					"message.developers_hell.bossrush.replay_complete"));
			return;
		}

		BossRushProgress.Completion completion = BossRushSavedData.get(encounter.level)
				.complete(encounter.ownerUuid, encounter.stage);
		encounter.owner.sendSystemMessage(Component.translatable(
				"message.developers_hell.bossrush.completed." + encounter.stage.serializedName()));
		if (completion.progress().stage() != BossRushStage.GRADUATED) {
			pendingByOwner.put(encounter.ownerUuid, new PendingStart(
					encounter.ownerUuid,
					encounter.level.getServer(),
					encounter.level.getServer().getTickCount() + CHAPTER_DELAY_TICKS
			));
		}
	}

	private synchronized boolean abort(UUID ownerUuid, String reason) {
		ActiveEncounter encounter = activeByOwner.remove(ownerUuid);
		pendingByOwner.remove(ownerUuid);
		if (encounter == null) {
			return false;
		}
		encounter.close();
		if (!encounter.replay) {
			BossRushSavedData.get(encounter.level).normalizeRestart(ownerUuid);
		}
		encounter.owner.sendSystemMessage(Component.translatable(
				"message.developers_hell.bossrush.aborted", reason));
		return true;
	}

	private boolean allowDamage(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
		ActiveEncounter encounter = encounterForEntity(entity.getUUID()).orElse(null);
		if (encounter == null) {
			return true;
		}
		Entity attacker = source.getEntity();
		if (!(attacker instanceof ServerPlayer player) || !encounter.ownerUuid.equals(player.getUUID())) {
			return false;
		}
		Role role = encounter.roles.get(entity.getUUID());
		return switch (encounter.stage) {
			case JURY -> role == Role.EVIDENCE
					|| (encounter.allDead(Role.EVIDENCE)
						&& role == encounter.currentJurorRole());
			case CHAIRMAN -> role == Role.RUBRIC
					|| (role == Role.CHAIRMAN && encounter.allDead(Role.RUBRIC));
			case CODEX -> role == Role.AGENT
					|| (role == Role.CODEX && encounter.allDead(Role.AGENT));
			default -> false;
		};
	}

	private synchronized void markOwnedDeath(LivingEntity entity) {
		encounterForEntity(entity.getUUID()).ifPresent(encounter ->
				encounter.deadEntities.add(entity.getUUID()));
	}

	private synchronized Optional<ActiveEncounter> encounterForEntity(UUID entityUuid) {
		return activeByOwner.values().stream()
				.filter(encounter -> encounter.roles.containsKey(entityUuid))
				.findFirst();
	}

	private synchronized Optional<ServerPlayer> ownerForEntity(UUID entityUuid) {
		return encounterForEntity(entityUuid).map(encounter -> encounter.owner);
	}

	private static void prepareMob(
			Mob mob,
			ServerPlayer owner,
			String nameKey,
			float health,
			boolean noAi
	) {
		mob.setCustomName(Component.translatable(nameKey));
		mob.setCustomNameVisible(true);
		mob.setPersistenceRequired();
		mob.setCanPickUpLoot(false);
		mob.skipDropExperience();
		mob.setNoAi(noAi);
		mob.setTarget(noAi ? null : owner);
		mob.addTag(ENTITY_TAG);
		var maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(health);
		}
		mob.setHealth(health);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setDropChance(slot, 0.0F);
		}
	}

	private static void spawnAt(ActiveEncounter encounter, Mob mob, int right, int forward) {
		BlockPos pos = encounter.origin.offset(right, 0, forward);
		mob.snapTo(Vec3.atBottomCenterOf(pos));
	}

	private static boolean addOwned(ActiveEncounter encounter, Mob mob, Role role) {
		encounter.ledger.track(mob.getUUID());
		encounter.roles.put(mob.getUUID(), role);
		if (encounter.level.addFreshEntity(mob)) {
			return true;
		}
		mob.discard();
		return false;
	}

	private static void updateBossBar(ActiveEncounter encounter) {
		float current = 0.0F;
		float maximum = 0.0F;
		for (UUID uuid : encounter.ledger.ownedEntityUuids()) {
			Entity entity = encounter.level.getEntityInAnyDimension(uuid);
			if (entity instanceof LivingEntity living && living.isAlive()) {
				current += living.getHealth();
				maximum += living.getMaxHealth();
			}
		}
		encounter.bar.setProgress(maximum <= 0.0F ? 0.0F : Math.max(0.0F, Math.min(1.0F, current / maximum)));
	}

	private synchronized List<ActiveEncounter> activeSnapshot() {
		return List.copyOf(activeByOwner.values());
	}

	private synchronized List<PendingStart> pendingSnapshot() {
		return List.copyOf(pendingByOwner.values());
	}

	synchronized Optional<Snapshot> snapshotForGameTest(UUID ownerUuid) {
		ActiveEncounter encounter = activeByOwner.get(ownerUuid);
		if (encounter == null) {
			return Optional.empty();
		}
		return Optional.of(new Snapshot(
				encounter.ownerUuid,
				encounter.stage,
				encounter.replay,
				encounter.ledger.ownedEntityUuids(),
				Map.copyOf(encounter.roles),
				Set.copyOf(encounter.deadEntities),
				encounter.bar.getPlayers().stream().map(ServerPlayer::getUUID).collect(java.util.stream.Collectors.toSet())
		));
	}

	synchronized void tickForGameTest(MinecraftServer server) {
		tick(server);
	}

	public enum ReplayBoss {
		JURY,
		CHAIRMAN,
		CODEX;

		public static ReplayBoss fromCommand(String value) {
			return switch (value) {
				case "jury" -> JURY;
				case "chairman" -> CHAIRMAN;
				case "codex" -> CODEX;
				default -> throw new IllegalArgumentException("Unknown replay boss: " + value);
			};
		}
	}

	public enum StartResult {
		STARTED,
		ALREADY_ACTIVE,
		SPONSOR_SKIPPED,
		GRADUATED,
		REPLAY_LOCKED,
		DISABLED,
		SPAWN_FAILED
	}

	enum Role {
		EVIDENCE,
		JUROR_ONE,
		JUROR_TWO,
		JUROR_THREE,
		CHAIRMAN,
		RUBRIC,
		SPONSOR,
		CODEX,
		AGENT
	}

	record Snapshot(
			UUID ownerUuid,
			BossRushStage stage,
			boolean replay,
			Set<UUID> ownedEntities,
			Map<UUID, Role> roles,
			Set<UUID> deadEntities,
			Set<UUID> bossBarPlayers
	) {
	}

	private static final class ActiveEncounter {
		private final UUID ownerUuid;
		private final ServerPlayer owner;
		private final ServerLevel level;
		private final BlockPos origin;
		private final BossRushStage stage;
		private final boolean replay;
		private final BossRushRuntimeState ledger;
		private final ServerBossEvent bar;
		private final Map<UUID, Role> roles = new LinkedHashMap<>();
		private final Set<UUID> deadEntities = new java.util.LinkedHashSet<>();
		private int phase;
		private long nextActionTick;

		private ActiveEncounter(ServerPlayer owner, BossRushStage stage, boolean replay) {
			this.ownerUuid = owner.getUUID();
			this.owner = owner;
			this.level = owner.level();
			this.origin = owner.blockPosition();
			this.stage = stage;
			this.replay = replay;
			UUID encounterUuid = UUID.randomUUID();
			this.ledger = new BossRushRuntimeState(ownerUuid, encounterUuid);
			this.bar = new ServerBossEvent(
					encounterUuid,
					Component.translatable("bossbar.developers_hell.bossrush"),
					BossEvent.BossBarColor.YELLOW,
					BossEvent.BossBarOverlay.PROGRESS
			);
			bar.addPlayer(owner);
			bar.setProgress(1.0F);
			bar.setVisible(true);
		}

		private static ActiveEncounter open(ServerPlayer owner, BossRushStage stage, boolean replay) {
			return new ActiveEncounter(owner, stage, replay);
		}

		private boolean allDead(Role role) {
			return roles.entrySet().stream()
					.filter(entry -> entry.getValue() == role)
					.allMatch(entry -> deadEntities.contains(entry.getKey()));
		}

		private int firstAlive(Role... orderedRoles) {
			for (int index = 0; index < orderedRoles.length; index++) {
				Role role = orderedRoles[index];
				boolean alive = roles.entrySet().stream()
						.anyMatch(entry -> entry.getValue() == role && !deadEntities.contains(entry.getKey()));
				if (alive) {
					return index;
				}
			}
			return -1;
		}

		private Role currentJurorRole() {
			int index = firstAlive(Role.JUROR_ONE, Role.JUROR_TWO, Role.JUROR_THREE);
			return index < 0 ? null : Role.values()[Role.JUROR_ONE.ordinal() + index];
		}

		private void close() {
			for (UUID uuid : ledger.close()) {
				Entity entity = level.getEntityInAnyDimension(uuid);
				if (entity != null && !entity.isRemoved()) {
					entity.discard();
				}
			}
			roles.clear();
			deadEntities.clear();
			bar.setVisible(false);
			bar.removeAllPlayers();
		}
	}

	private record PendingStart(UUID ownerUuid, MinecraftServer server, int dueServerTick) {
	}
}
