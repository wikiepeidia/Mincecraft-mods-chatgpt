package dev.developershell.gametest;

import dev.developershell.bossrush.BossRushGameTestAccess;
import dev.developershell.bossrush.BossRushGameTestAccess.RuntimeView;
import dev.developershell.bossrush.BossRushManager;
import dev.developershell.bossrush.BossRushProgress;
import dev.developershell.bossrush.BossRushStage;
import dev.developershell.registry.ModItems;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Production-path checks for the bounded emergency Contract-to-Diploma sequence. */
public final class BossRushGameTests implements CustomTestMethodInvoker {
	@GameTest(padding = 18)
	public void juryRequiresItsOwnerAndCleansFiveBoundedEntities(GameTestHelper context) {
		TestRun run = startInitial(context);
		try {
			RuntimeView jury = view(context, run);
			context.assertValueEqual(jury.stage(), BossRushStage.JURY, "Jury live stage");
			context.assertValueEqual(jury.ownedEntities().size(), 5, "bounded Jury entity count");
			context.assertValueEqual(jury.entities("EVIDENCE").size(), 2, "two evidence packets");
			context.assertValueEqual(jury.bossBarPlayers(), Set.of(run.owner().getUUID()),
					"Jury boss bar is owner-only");

			LivingEntity evidence = living(context, run.level(), first(jury.entities("EVIDENCE")));
			context.assertFalse(damage(run.level(), evidence, run.other(), evidence.getMaxHealth() + 20.0F),
					"non-owner cannot advance Jury evidence");
			LivingEntity firstJuror = living(context, run.level(), first(jury.entities("JUROR_ONE")));
			context.assertFalse(damage(run.level(), firstJuror, run.owner(), firstJuror.getMaxHealth() + 20.0F),
					"owner cannot skip evidence packets");

			killRoles(context, run, "EVIDENCE");
			BossRushGameTestAccess.tick(run.manager(), run.level().getServer());
			killRoles(context, run, "JUROR_ONE");
			BossRushGameTestAccess.tick(run.manager(), run.level().getServer());
			killRoles(context, run, "JUROR_TWO");
			BossRushGameTestAccess.tick(run.manager(), run.level().getServer());
			killRoles(context, run, "JUROR_THREE");
			Set<UUID> owned = jury.ownedEntities();
			BossRushGameTestAccess.tick(run.manager(), run.level().getServer());

			context.assertFalse(BossRushGameTestAccess.snapshot(run.manager(), run.owner().getUUID()).isPresent(),
					"Jury completion removes runtime and boss bar");
			assertEntitiesCleaned(context, run.level(), owned);
			BossRushProgress progress = BossRushGameTestAccess.progress(run.level(), run.owner().getUUID());
			context.assertValueEqual(progress.stage(), BossRushStage.READY_CHAIRMAN, "Jury checkpoint");
			context.assertValueEqual(countItem(run.owner(), ModItems.SIGNED_DEFENSE_MINUTES), 1,
					"one Signed Defense Minutes reward");
			context.assertValueEqual(countItem(run.owner(), ModItems.EVIDENCE_BINDER), 1,
					"one Evidence Binder reward");
			context.succeed();
		}
		finally {
			run.cleanup();
		}
	}

	@GameTest(padding = 18)
	public void chairmanUsesRubricsRevisionsAndTheAcceptancePad(GameTestHelper context) {
		TestRun run = startAt(context, new BossRushProgress(
				newPlayer(context).getUUID(), BossRushStage.READY_CHAIRMAN, true, false, false));
		try {
			RuntimeView chairman = view(context, run);
			context.assertValueEqual(chairman.stage(), BossRushStage.CHAIRMAN, "Chairman live stage");
			context.assertValueEqual(chairman.ownedEntities().size(), 4, "bounded initial Chairman entity count");
			context.assertValueEqual(chairman.entities("RUBRIC").size(), 3, "three rubric nodes");

			LivingEntity rubric = living(context, run.level(), first(chairman.entities("RUBRIC")));
			context.assertFalse(damage(run.level(), rubric, run.other(), rubric.getMaxHealth() + 20.0F),
					"non-owner cannot clear a rubric node");
			killRoles(context, run, "RUBRIC");
			BossRushGameTestAccess.tick(run.manager(), run.level().getServer());

			LivingEntity boss = living(context, run.level(), first(chairman.entities("CHAIRMAN")));
			context.assertTrue(damage(run.level(), boss, run.owner(), 55.0F),
					"owner can reach the revision threshold after rubrics");
			BossRushGameTestAccess.tick(run.manager(), run.level().getServer());
			RuntimeView revisions = view(context, run);
			context.assertValueEqual(revisions.ownedEntities().size(), 6, "Chairman remains capped at six entities");
			context.assertValueEqual(revisions.entities("REVISION").size(), 2, "two Minor Revisions");
			context.assertFalse(damage(run.level(), boss, run.owner(), boss.getMaxHealth()),
					"Chairman is protected while revisions remain");

			killRoles(context, run, "REVISION");
			BossRushGameTestAccess.tick(run.manager(), run.level().getServer());
			Vec3 acceptance = Vec3.atCenterOf(run.owner().blockPosition().offset(0, 0, 4));
			run.owner().snapTo(acceptance.x, acceptance.y, acceptance.z);
			context.assertTrue(damage(run.level(), boss, run.owner(), boss.getMaxHealth() + 20.0F),
					"acceptance pad opens the Chairman finishing hit");
			Set<UUID> owned = revisions.ownedEntities();
			BossRushGameTestAccess.tick(run.manager(), run.level().getServer());

			context.assertFalse(BossRushGameTestAccess.snapshot(run.manager(), run.owner().getUUID()).isPresent(),
					"Chairman completion removes runtime and boss bar");
			assertEntitiesCleaned(context, run.level(), owned);
			context.assertValueEqual(
					BossRushGameTestAccess.progress(run.level(), run.owner().getUUID()).stage(),
					BossRushStage.SPONSOR,
					"Chairman checkpoint"
			);
			context.assertValueEqual(countItem(run.owner(), ModItems.APPROVED_REVISION_STAMP), 1,
					"one Approved Revision Stamp reward");
			context.assertValueEqual(countItem(run.owner(), ModItems.RED_PEN), 1, "one Red Pen reward");
			context.succeed();
		}
		finally {
			run.cleanup();
		}
	}

	@GameTest(padding = 18)
	public void codexCountdownAgentsAndMaxReasoningGrantOneDiploma(GameTestHelper context) {
		ServerPlayer owner = newPlayer(context);
		TestRun run = startAt(context, new BossRushProgress(
				owner.getUUID(), BossRushStage.SPONSOR, true, true, false), owner);
		try {
			RuntimeView sponsor = view(context, run);
			context.assertValueEqual(sponsor.stage(), BossRushStage.SPONSOR, "Sponsor countdown stage");
			context.assertValueEqual(sponsor.ownedEntities().size(), 1, "one bounded sponsor entity");
			context.assertValueEqual(run.manager().start(run.owner()), BossRushManager.StartResult.SPONSOR_SKIPPED,
					"second start skips the local countdown");
			BossRushGameTestAccess.tick(run.manager(), run.level().getServer());

			RuntimeView codex = view(context, run);
			context.assertValueEqual(codex.stage(), BossRushStage.CODEX, "Codex transformation stage");
			context.assertValueEqual(codex.ownedEntities().size(), 4, "bounded Codex entity count");
			context.assertValueEqual(codex.entities("AGENT").size(), 3, "Builder Reviewer Executor trio");
			LivingEntity agent = living(context, run.level(), first(codex.entities("AGENT")));
			context.assertFalse(damage(run.level(), agent, run.other(), agent.getMaxHealth() + 20.0F),
					"non-owner cannot clear a Codex agent");
			LivingEntity boss = living(context, run.level(), first(codex.entities("CODEX")));
			context.assertFalse(damage(run.level(), boss, run.owner(), boss.getMaxHealth()),
					"Codex core is protected while agents remain");

			killRoles(context, run, "AGENT");
			BossRushGameTestAccess.tick(run.manager(), run.level().getServer());
			context.assertTrue(damage(run.level(), boss, run.owner(), 82.0F),
					"owner reaches MAX Reasoning after clearing agents");
			BossRushGameTestAccess.tick(run.manager(), run.level().getServer());
			context.assertFalse(damage(run.level(), boss, run.other(), boss.getMaxHealth()),
					"non-owner cannot use MAX Reasoning");
			context.assertTrue(damage(run.level(), boss, run.owner(), boss.getMaxHealth() + 20.0F),
					"owner uses the MAX Reasoning window");
			Set<UUID> owned = codex.ownedEntities();
			BossRushGameTestAccess.tick(run.manager(), run.level().getServer());

			context.assertFalse(BossRushGameTestAccess.snapshot(run.manager(), run.owner().getUUID()).isPresent(),
					"Codex completion removes runtime and boss bar");
			assertEntitiesCleaned(context, run.level(), owned);
			context.assertValueEqual(
					BossRushGameTestAccess.progress(run.level(), run.owner().getUUID()).stage(),
					BossRushStage.GRADUATED,
					"Codex victory graduates the owner"
			);
			context.assertValueEqual(countItem(run.owner(), ModItems.DEFINITELY_LEGITIMATE_DIPLOMA), 1,
					"Codex first clear grants one Diploma");
			context.assertValueEqual(run.manager().start(run.owner()), BossRushManager.StartResult.GRADUATED,
					"graduated start is progression-neutral");
			context.assertValueEqual(countItem(run.owner(), ModItems.DEFINITELY_LEGITIMATE_DIPLOMA), 1,
					"graduated start grants no second Diploma");
			context.succeed();
		}
		finally {
			run.cleanup();
		}
	}

	@GameTest(padding = 18)
	public void directChainNormalizesAbortAndReplayCannotDuplicateRewards(GameTestHelper context) {
		TestRun run = startInitial(context);
		try {
			RuntimeView abortedJury = view(context, run);
			context.assertTrue(run.manager().abort(run.owner(), "gametest"), "active Jury abort succeeds");
			context.assertFalse(BossRushGameTestAccess.snapshot(run.manager(), run.owner().getUUID()).isPresent(),
					"abort removes runtime and boss bar");
			assertEntitiesCleaned(context, run.level(), abortedJury.ownedEntities());
			context.assertValueEqual(
					BossRushGameTestAccess.progress(run.level(), run.owner().getUUID()).stage(),
					BossRushStage.READY_JURY,
					"abort normalizes to the Jury checkpoint"
			);
			context.assertValueEqual(run.manager().start(run.owner()), BossRushManager.StartResult.STARTED,
					"normalized Jury restarts");

			completeJury(context, run);
			context.assertValueEqual(run.manager().start(run.owner()), BossRushManager.StartResult.STARTED,
					"direct chain starts Chairman");
			completeChairman(context, run);
			context.assertValueEqual(run.manager().start(run.owner()), BossRushManager.StartResult.STARTED,
					"direct chain starts sponsor countdown");
			context.assertValueEqual(run.manager().start(run.owner()), BossRushManager.StartResult.SPONSOR_SKIPPED,
					"direct chain skips sponsor countdown");
			BossRushGameTestAccess.tick(run.manager(), run.level().getServer());
			completeCodex(context, run);

			context.assertValueEqual(
					BossRushGameTestAccess.progress(run.level(), run.owner().getUUID()).stage(),
					BossRushStage.GRADUATED,
					"direct chain reaches graduated"
			);
			context.assertValueEqual(countItem(run.owner(), ModItems.DEFINITELY_LEGITIMATE_DIPLOMA), 1,
					"direct chain grants one Diploma");
			int totalRewards = totalRewards(run.owner());

			context.assertValueEqual(
					run.manager().replay(run.owner(), BossRushManager.ReplayBoss.JURY),
					BossRushManager.StartResult.STARTED,
					"graduated player can replay Jury"
			);
			completeJury(context, run);
			context.assertValueEqual(totalRewards(run.owner()), totalRewards,
					"replay grants no duplicate first-clear rewards");
			context.assertValueEqual(
					BossRushGameTestAccess.progress(run.level(), run.owner().getUUID()).stage(),
					BossRushStage.GRADUATED,
					"replay cannot change graduated progress"
			);
			context.assertFalse(BossRushGameTestAccess.snapshot(run.manager(), run.owner().getUUID()).isPresent(),
					"replay cleanup removes runtime and boss bar");
			context.succeed();
		}
		finally {
			run.cleanup();
		}
	}

	private static TestRun startInitial(GameTestHelper context) {
		ServerPlayer owner = newPlayer(context);
		return startAt(context, BossRushProgress.initial(owner.getUUID()), owner);
	}

	private static TestRun startAt(GameTestHelper context, BossRushProgress progress) {
		ServerPlayer owner = findPlayer(context, progress.ownerUuid());
		return startAt(context, progress, owner);
	}

	private static TestRun startAt(GameTestHelper context, BossRushProgress progress, ServerPlayer owner) {
		ServerLevel level = context.getLevel();
		BossRushGameTestAccess.replaceProgress(level, progress);
		ServerPlayer other = newPlayer(context);
		BossRushManager manager = new BossRushManager(true);
		manager.registerLifecycle();
		TestRun run = new TestRun(context, level, manager, owner, other);
		context.runBeforeTestEnd(run::cleanup);
		context.assertValueEqual(manager.start(owner), BossRushManager.StartResult.STARTED,
				"production manager starts requested checkpoint");
		return run;
	}

	private static ServerPlayer findPlayer(GameTestHelper context, UUID ownerUuid) {
		ServerPlayer player = context.getLevel().getServer().getPlayerList().getPlayer(ownerUuid);
		if (player == null) {
			throw context.assertionException("missing GameTest player %s", ownerUuid);
		}
		return player;
	}

	private static ServerPlayer newPlayer(GameTestHelper context) {
		ServerPlayer player = context.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 spawn = context.absoluteVec(new Vec3(8.5D, 2.0D, 8.5D));
		player.snapTo(spawn.x, spawn.y, spawn.z);
		return player;
	}

	private static RuntimeView view(GameTestHelper context, TestRun run) {
		return BossRushGameTestAccess.snapshot(run.manager(), run.owner().getUUID())
				.orElseThrow(() -> context.assertionException("missing boss-rush runtime"));
	}

	private static void completeJury(GameTestHelper context, TestRun run) {
		RuntimeView jury = view(context, run);
		killRoles(context, run, "EVIDENCE");
		BossRushGameTestAccess.tick(run.manager(), run.level().getServer());
		for (String role : List.of("JUROR_ONE", "JUROR_TWO", "JUROR_THREE")) {
			killRoles(context, run, role);
			BossRushGameTestAccess.tick(run.manager(), run.level().getServer());
		}
		assertEntitiesCleaned(context, run.level(), jury.ownedEntities());
	}

	private static void completeChairman(GameTestHelper context, TestRun run) {
		RuntimeView chairman = view(context, run);
		killRoles(context, run, "RUBRIC");
		BossRushGameTestAccess.tick(run.manager(), run.level().getServer());
		LivingEntity boss = living(context, run.level(), first(chairman.entities("CHAIRMAN")));
		context.assertTrue(damage(run.level(), boss, run.owner(), 55.0F), "Chairman threshold damage");
		BossRushGameTestAccess.tick(run.manager(), run.level().getServer());
		RuntimeView revisions = view(context, run);
		killRoles(context, run, "REVISION");
		BossRushGameTestAccess.tick(run.manager(), run.level().getServer());
		Vec3 acceptance = Vec3.atCenterOf(run.owner().blockPosition().offset(0, 0, 4));
		run.owner().snapTo(acceptance.x, acceptance.y, acceptance.z);
		context.assertTrue(damage(run.level(), boss, run.owner(), boss.getMaxHealth() + 20.0F),
				"Chairman finishing damage");
		BossRushGameTestAccess.tick(run.manager(), run.level().getServer());
		assertEntitiesCleaned(context, run.level(), revisions.ownedEntities());
	}

	private static void completeCodex(GameTestHelper context, TestRun run) {
		RuntimeView codex = view(context, run);
		killRoles(context, run, "AGENT");
		BossRushGameTestAccess.tick(run.manager(), run.level().getServer());
		LivingEntity boss = living(context, run.level(), first(codex.entities("CODEX")));
		context.assertTrue(damage(run.level(), boss, run.owner(), 82.0F), "Codex threshold damage");
		BossRushGameTestAccess.tick(run.manager(), run.level().getServer());
		context.assertTrue(damage(run.level(), boss, run.owner(), boss.getMaxHealth() + 20.0F),
				"Codex finishing damage");
		BossRushGameTestAccess.tick(run.manager(), run.level().getServer());
		assertEntitiesCleaned(context, run.level(), codex.ownedEntities());
	}

	private static void killRoles(GameTestHelper context, TestRun run, String role) {
		RuntimeView snapshot = view(context, run);
		for (UUID uuid : snapshot.entities(role)) {
			LivingEntity entity = living(context, run.level(), uuid);
			context.assertTrue(damage(run.level(), entity, run.owner(), entity.getMaxHealth() + 100.0F),
					"owner can defeat " + role);
		}
	}

	private static boolean damage(ServerLevel level, LivingEntity target, ServerPlayer attacker, float amount) {
		return target.hurtServer(level, attacker.damageSources().playerAttack(attacker), amount);
	}

	private static LivingEntity living(GameTestHelper context, ServerLevel level, UUID uuid) {
		Entity entity = level.getEntityInAnyDimension(uuid);
		if (!(entity instanceof LivingEntity living)) {
			throw context.assertionException("missing living boss-rush entity %s", uuid);
		}
		return living;
	}

	private static UUID first(Set<UUID> uuids) {
		return uuids.iterator().next();
	}

	private static void assertEntitiesCleaned(GameTestHelper context, ServerLevel level, Set<UUID> uuids) {
		for (UUID uuid : uuids) {
			Entity entity = level.getEntityInAnyDimension(uuid);
			context.assertTrue(entity == null || entity.isRemoved(), "owned entity cleanup " + uuid);
		}
	}

	private static int countItem(ServerPlayer player, Item item) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(item)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static int totalRewards(ServerPlayer player) {
		return countItem(player, ModItems.SIGNED_DEFENSE_MINUTES)
				+ countItem(player, ModItems.EVIDENCE_BINDER)
				+ countItem(player, ModItems.APPROVED_REVISION_STAMP)
				+ countItem(player, ModItems.RED_PEN)
				+ countItem(player, ModItems.DEFINITELY_LEGITIMATE_DIPLOMA);
	}

	@Override
	public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
		method.invoke(this, context);
	}

	private static final class TestRun {
		private final GameTestHelper context;
		private final ServerLevel level;
		private final BossRushManager manager;
		private final ServerPlayer owner;
		private final ServerPlayer other;
		private boolean cleaned;

		private TestRun(
				GameTestHelper context,
				ServerLevel level,
				BossRushManager manager,
				ServerPlayer owner,
				ServerPlayer other
		) {
			this.context = context;
			this.level = level;
			this.manager = manager;
			this.owner = owner;
			this.other = other;
		}

		private ServerLevel level() {
			return level;
		}

		private BossRushManager manager() {
			return manager;
		}

		private ServerPlayer owner() {
			return owner;
		}

		private ServerPlayer other() {
			return other;
		}

		private synchronized void cleanup() {
			if (cleaned) {
				return;
			}
			cleaned = true;
			manager.abort(owner, "gametest_cleanup");
			var players = level.getServer().getPlayerList();
			if (players.getPlayers().contains(other)) {
				players.remove(other);
			}
			if (players.getPlayers().contains(owner)) {
				players.remove(owner);
			}
		}
	}
}
