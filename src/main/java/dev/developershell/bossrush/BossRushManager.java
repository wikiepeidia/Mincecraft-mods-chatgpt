package dev.developershell.bossrush;

import dev.developershell.registry.ModItems;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
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
		return BossRushSavedData.get(player.level()).snapshot(player.getUUID());
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
		for (int index = 0; index < BossRushRules.JURY_EVIDENCE_TARGETS; index++) {
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
		for (int index = 0; index < BossRushRules.JURY_JURORS; index++) {
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
		for (int index = 0; index < BossRushRules.CHAIRMAN_RUBRIC_NODES; index++) {
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
			int remainingSeconds = (int) Math.ceil(Math.max(0L,
					encounter.nextActionTick - gameTime) / 20.0D);
			if (remainingSeconds != encounter.lastCueSecond) {
				encounter.lastCueSecond = remainingSeconds;
				owner.sendSystemMessage(Component.translatable(
						"actionbar.developers_hell.bossrush.sponsor.countdown", remainingSeconds), true);
				encounter.level.sendParticles(
						ParticleTypes.END_ROD,
						encounter.origin.getX() + 0.5D,
						encounter.origin.getY() + 1.5D,
						encounter.origin.getZ() + 1.5D,
						8,
						1.0D, 1.0D, 1.0D, 0.02D
				);
			}
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
		long gameTime = encounter.level.getGameTime();
		double distanceSquared = encounter.owner.position().distanceToSqr(Vec3.atCenterOf(encounter.origin));
		if (gameTime >= encounter.nextHazardTick) {
			encounter.nextHazardTick = gameTime + BossRushRules.HAZARD_PULSE_TICKS;
			emitRing(encounter, BossRushRules.JURY_SCOPE_RADIUS, ParticleTypes.WAX_ON);
			if (!BossRushRules.insideScope(distanceSquared)) {
				encounter.owner.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
				encounter.owner.hurtServer(
						encounter.level, encounter.owner.damageSources().magic(), 2.0F);
				encounter.owner.sendSystemMessage(Component.translatable(
						"actionbar.developers_hell.bossrush.jury.scope_creep"), true);
			}
		}
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
		if (!encounter.allDead(Role.RUBRIC)) {
			return;
		}
		LivingEntity chairman = encounter.living(Role.CHAIRMAN).orElse(null);
		if (chairman == null || !chairman.isAlive() || encounter.allDead(Role.CHAIRMAN)) {
			completeFight(encounter);
			return;
		}
		long gameTime = encounter.level.getGameTime();
		if (encounter.phase == 0
				&& chairman.getHealth() / chairman.getMaxHealth()
						<= BossRushRules.CHAIRMAN_MINOR_REVISIONS_HEALTH_FRACTION) {
			encounter.phase = 1;
			spawnMinorRevisions(encounter);
			encounter.owner.sendSystemMessage(Component.translatable(
					"message.developers_hell.bossrush.chairman.minor_revisions"));
			return;
		}
		if (encounter.phase == 1 && encounter.allDead(Role.REVISION)) {
			encounter.phase = 2;
			encounter.windowOpen = true;
			encounter.nextActionTick = gameTime + BossRushRules.CHAIRMAN_ACCEPTANCE_WINDOW_TICKS;
			encounter.owner.sendSystemMessage(Component.translatable(
					"message.developers_hell.bossrush.chairman.major_revisions"));
		}
		if (encounter.phase >= 2) {
			cycleWindow(
					encounter,
					gameTime,
					BossRushRules.CHAIRMAN_ACCEPTANCE_WINDOW_TICKS,
					BossRushRules.CHAIRMAN_ACCEPTANCE_RECOVERY_TICKS,
					"actionbar.developers_hell.bossrush.chairman.window",
					"actionbar.developers_hell.bossrush.chairman.recovery"
			);
			if (gameTime >= encounter.nextHazardTick) {
				encounter.nextHazardTick = gameTime + BossRushRules.HAZARD_PULSE_TICKS;
				emitRing(
						encounter,
						Vec3.atCenterOf(encounter.origin.offset(0, 0, 4)),
						BossRushRules.CHAIRMAN_ACCEPTANCE_RADIUS,
						ParticleTypes.HAPPY_VILLAGER
				);
			}
		}
	}

	private void tickCodex(ActiveEncounter encounter) {
		if (!encounter.allDead(Role.AGENT)) {
			return;
		}
		LivingEntity codex = encounter.living(Role.CODEX).orElse(null);
		if (codex == null || !codex.isAlive() || encounter.allDead(Role.CODEX)) {
			completeFight(encounter);
			return;
		}
		long gameTime = encounter.level.getGameTime();
		if (gameTime >= encounter.nextHazardTick) {
			encounter.nextHazardTick = gameTime + BossRushRules.HAZARD_PULSE_TICKS;
			emitRing(encounter, BossRushRules.CODEX_OVERFLOW_INNER_RADIUS, ParticleTypes.ELECTRIC_SPARK);
			emitRing(encounter, BossRushRules.CODEX_OVERFLOW_OUTER_RADIUS, ParticleTypes.SOUL_FIRE_FLAME);
			double distanceSquared = encounter.owner.position().distanceToSqr(Vec3.atCenterOf(encounter.origin));
			if (BossRushRules.insideOverflowRing(distanceSquared)) {
				encounter.owner.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
				encounter.owner.hurtServer(
						encounter.level, encounter.owner.damageSources().magic(), 2.0F);
				encounter.owner.sendSystemMessage(Component.translatable(
						"actionbar.developers_hell.bossrush.codex.context_overflow"), true);
			}
		}
		if (encounter.phase == 0
				&& codex.getHealth() / codex.getMaxHealth()
						<= BossRushRules.CODEX_MAX_REASONING_HEALTH_FRACTION) {
			encounter.phase = 1;
			encounter.windowOpen = true;
			encounter.nextActionTick = gameTime + BossRushRules.CODEX_MAX_REASONING_WINDOW_TICKS;
			encounter.owner.sendSystemMessage(Component.translatable(
					"message.developers_hell.bossrush.codex.max_reasoning"));
		}
		if (encounter.phase >= 1) {
			cycleWindow(
					encounter,
					gameTime,
					BossRushRules.CODEX_MAX_REASONING_WINDOW_TICKS,
					BossRushRules.CODEX_MAX_REASONING_RECOVERY_TICKS,
					"actionbar.developers_hell.bossrush.codex.max_window",
					"actionbar.developers_hell.bossrush.codex.recovery"
			);
		}
	}

	private void spawnMinorRevisions(ActiveEncounter encounter) {
		for (int index = 0; index < BossRushRules.CHAIRMAN_MINOR_REVISIONS; index++) {
			Vindicator revision = EntityTypes.VINDICATOR.create(
					encounter.level, EntitySpawnReason.EVENT);
			if (revision == null) {
				continue;
			}
			prepareMob(
					revision,
					encounter.owner,
					"entity.developers_hell.minor_revision",
					24.0F,
					false
			);
			revision.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
			spawnAt(encounter, revision, index == 0 ? -4 : 4, 5);
			addOwned(encounter, revision, Role.REVISION);
		}
	}

	private static void cycleWindow(
			ActiveEncounter encounter,
			long gameTime,
			int openTicks,
			int recoveryTicks,
			String openKey,
			String recoveryKey
	) {
		if (gameTime >= encounter.nextActionTick) {
			encounter.windowOpen = !encounter.windowOpen;
			encounter.nextActionTick = gameTime + (encounter.windowOpen ? openTicks : recoveryTicks);
			encounter.lastCueSecond = -1;
		}
		int seconds = (int) Math.ceil(Math.max(0L, encounter.nextActionTick - gameTime) / 20.0D);
		if (seconds != encounter.lastCueSecond) {
			encounter.lastCueSecond = seconds;
			encounter.owner.sendSystemMessage(Component.translatable(
					encounter.windowOpen ? openKey : recoveryKey,
					seconds
			), true);
		}
	}

	private static void emitRing(ActiveEncounter encounter, double radius, ParticleOptions particle) {
		emitRing(encounter, Vec3.atCenterOf(encounter.origin), radius, particle);
	}

	private static void emitRing(
			ActiveEncounter encounter,
			Vec3 center,
			double radius,
			ParticleOptions particle
	) {
		for (int index = 0; index < BossRushRules.CODEX_RING_PARTICLES; index++) {
			double angle = Math.PI * 2.0D * index / BossRushRules.CODEX_RING_PARTICLES;
			encounter.level.sendParticles(
					particle,
					center.x + Math.cos(angle) * radius,
					center.y - 0.25D,
					center.z + Math.sin(angle) * radius,
					1,
					0.0D, 0.0D, 0.0D, 0.0D
			);
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
		if (completion.firstClear()) {
			grantFirstClearRewards(encounter.owner, encounter.stage);
		}
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

	private static void grantFirstClearRewards(ServerPlayer player, BossRushStage stage) {
		switch (stage) {
			case JURY -> {
				ensureReward(player, ModItems.SIGNED_DEFENSE_MINUTES);
				ensureReward(player, ModItems.EVIDENCE_BINDER);
			}
			case CHAIRMAN -> {
				ensureReward(player, ModItems.APPROVED_REVISION_STAMP);
				ensureReward(player, ModItems.RED_PEN);
			}
			case CODEX -> ensureReward(player, ModItems.DEFINITELY_LEGITIMATE_DIPLOMA);
			default -> {
			}
		}
		player.sendSystemMessage(Component.translatable(
				"message.developers_hell.bossrush.reward." + stage.serializedName()));
		player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F, 1.25F);
	}

	private static boolean ensureReward(ServerPlayer player, Item item) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot).is(item)) {
				return false;
			}
		}
		ItemStack reward = new ItemStack(item);
		if (!player.addItem(reward)) {
			player.drop(reward, false, false);
		}
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
					|| BossRushRules.juryJurorUnlocked(
							encounter.allDead(Role.EVIDENCE),
							encounter.currentJurorIndex(),
							encounter.jurorIndex(role)
					);
			case CHAIRMAN -> role == Role.RUBRIC || role == Role.REVISION
					|| (role == Role.CHAIRMAN && BossRushRules.chairmanCoreUnlocked(
							encounter.allDead(Role.RUBRIC),
							encounter.phase,
							encounter.windowOpen,
							encounter.ownerInsideAcceptancePad()
					));
			case CODEX -> role == Role.AGENT
					|| (role == Role.CODEX && BossRushRules.codexCoreUnlocked(
							encounter.allDead(Role.AGENT), encounter.phase, encounter.windowOpen));
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
		REVISION,
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
		private long nextHazardTick;
		private boolean windowOpen;
		private int lastCueSecond = -1;

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

		private Optional<LivingEntity> living(Role role) {
			return roles.entrySet().stream()
					.filter(entry -> entry.getValue() == role)
					.map(entry -> level.getEntityInAnyDimension(entry.getKey()))
					.filter(LivingEntity.class::isInstance)
					.map(LivingEntity.class::cast)
					.findFirst();
		}

		private boolean ownerInsideAcceptancePad() {
			return BossRushRules.insideAcceptancePad(owner.position().distanceToSqr(
					Vec3.atCenterOf(origin.offset(0, 0, 4))));
		}

		private Role currentJurorRole() {
			int index = currentJurorIndex();
			return index < 0 ? null : Role.values()[Role.JUROR_ONE.ordinal() + index];
		}

		private int currentJurorIndex() {
			return firstAlive(Role.JUROR_ONE, Role.JUROR_TWO, Role.JUROR_THREE);
		}

		private int jurorIndex(Role role) {
			if (role == null || role.ordinal() < Role.JUROR_ONE.ordinal()
					|| role.ordinal() > Role.JUROR_THREE.ordinal()) {
				return -1;
			}
			return role.ordinal() - Role.JUROR_ONE.ordinal();
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
