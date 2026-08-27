package dev.developershell.gametest;

import com.mojang.authlib.GameProfile;
import dev.developershell.campaign.CampaignSavedData;
import dev.developershell.campaign.CampaignService;
import dev.developershell.campaign.PlayerCampaignState;
import dev.developershell.item.InfiniteSlidesRemoteItem;
import dev.developershell.lecture.LectureEncounterManager;
import dev.developershell.lecture.LectureGeometry;
import dev.developershell.lecture.LectureStateMachine;
import dev.developershell.lecture.RewardService;
import dev.developershell.registry.ModEntities;
import dev.developershell.registry.ModItemIds;
import dev.developershell.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.phys.Vec3;

/** Integrated proof for the production Remote, durable native overlay, and ready edge. */
public final class RemoteGameTests implements CustomTestMethodInvoker {
	private static final BlockPos RELATIVE_DESK = new BlockPos(12, 2, 4);
	private static final Direction FACING = Direction.SOUTH;
	private static final String FIRED_KEY = "message.developers_hell.remote.fired";
	private static final String RECHARGING_KEY = "message.developers_hell.remote.recharging";
	private static final String READY_KEY = "message.developers_hell.remote.ready";
	private static final String UNAUTHORIZED_KEY = "message.developers_hell.remote.unauthorized";

	@GameTest(maxTicks = 180, padding = 24)
	public void productionRemoteUseCommitsOneEffectAndRejectedUseDoesNotReset(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(owner(1801), desk, level.getGameTime());
		ConnectedPlayer connection = null;
		LivingEntity target = null;
		try {
			buildArena(level, desk, FACING);
			connection = createSurvivalPlayer(context, ownerUuid, "remote-use");
			RecordingServerPlayer owner = connection.player();
			completeRealEncounter(context, owner, desk, FACING);
			ItemStack remote = moveRemoteToHand(context, owner);
			PlayerCampaignState authorized = state(level, ownerUuid);
			owner.getInventory().setItem(10, remote);
			owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.INFINITE_SLIDES_REMOTE));
			owner.clearFeedback();
			context.assertValueEqual(useRemote(level, owner), InteractionResult.SUCCESS_SERVER,
					"an unbound Remote is rejected without vanilla fallthrough");
			context.assertValueEqual(state(level, ownerUuid), authorized,
					"an unbound Remote cannot commit a cooldown");
			context.assertValueEqual(owner.recordedOverlayKeys(), List.of(UNAUTHORIZED_KEY),
					"an unbound Remote gets only unauthorized feedback");
			owner.setItemInHand(InteractionHand.MAIN_HAND, InfiniteSlidesRemoteItem.bound(
					new InfiniteSlidesRemoteItem.Binding(ownerUuid, UUID.randomUUID())));
			owner.clearFeedback();
			context.assertValueEqual(useRemote(level, owner), InteractionResult.SUCCESS_SERVER,
					"a stale owner-bound Remote is rejected without vanilla fallthrough");
			context.assertValueEqual(state(level, ownerUuid), authorized,
					"a stale projection identity cannot commit a cooldown");
			context.assertValueEqual(owner.recordedOverlayKeys(), List.of(UNAUTHORIZED_KEY),
					"a stale projection identity gets only unauthorized feedback");
			remote = owner.getInventory().removeItemNoUpdate(10);
			owner.setItemInHand(InteractionHand.MAIN_HAND, remote);

			context.assertTrue(ModItems.INFINITE_SLIDES_REMOTE instanceof InfiniteSlidesRemoteItem,
					"the production Remote registry field uses the custom item type");
			context.assertTrue(BuiltInRegistries.ITEM.getValue(ModItemIds.INFINITE_SLIDES_REMOTE.identifier())
					== ModItems.INFINITE_SLIDES_REMOTE,
					"the stable Remote key resolves to the same production instance");

			Vec3 forward = owner.getDirection().getUnitVec3();
			target = EntityTypes.COW.create(level, EntitySpawnReason.EVENT);
			context.assertTrue(target != null, "a bounded pushable target can be created");
			Vec3 targetPosition = owner.position().add(forward.scale(3.0D));
			target.snapTo(targetPosition.x, targetPosition.y, targetPosition.z);
			context.assertTrue(level.addFreshEntity(target), "the bounded target enters the real world query");

			owner.clearFeedback();
			InteractionResult accepted = useRemote(level, owner);
			context.assertValueEqual(accepted, InteractionResult.SUCCESS_SERVER,
					"ordinary production use is handled on the logical server");
			PlayerCampaignState coolingDown = state(level, ownerUuid);
			context.assertValueEqual(
					coolingDown.remoteCooldownUntilGameTime(),
					level.getGameTime() + InfiniteSlidesRemoteItem.COOLDOWN_TICKS,
					"accepted use commits the exact server-time deadline"
			);
			context.assertTrue(CampaignSavedData.get(level).isDirty(),
					"the deadline is dirty before the accepted world effect returns");
			context.assertTrue(target.getDeltaMovement().horizontalDistanceSqr() > 0.0D,
					"accepted use applies one bounded forward slide");
			context.assertTrue(owner.getCooldowns().isOnCooldown(remote),
					"accepted use starts the native overlay on the production stack");
			context.assertValueEqual(owner.trackingCooldowns().startedDurations(),
					List.of(InfiniteSlidesRemoteItem.COOLDOWN_TICKS),
					"accepted use starts exactly one full native cooldown");
			context.assertValueEqual(owner.recordedOverlayKeys(), List.of(FIRED_KEY),
					"accepted use emits one localized fired line");

			target.setDeltaMovement(Vec3.ZERO);
			owner.clearFeedback();
			InteractionResult rejected = useRemote(level, owner);
			context.assertValueEqual(rejected, InteractionResult.SUCCESS_SERVER,
					"rejected ordinary use is handled without vanilla fallthrough");
			context.assertValueEqual(state(level, ownerUuid), coolingDown,
					"rejected use changes no persisted deadline or notice identity");
			context.assertValueEqual(target.getDeltaMovement(), Vec3.ZERO,
					"rejected use applies no second world effect");
			context.assertValueEqual(owner.trackingCooldowns().startedDurations(),
					List.of(InfiniteSlidesRemoteItem.COOLDOWN_TICKS),
					"rejected use does not reset the native overlay");
			context.assertValueEqual(owner.recordedOverlayKeys(), List.of(RECHARGING_KEY),
					"rejected use emits only one ceiling-time recharge line");
			context.succeed();
		}
		finally {
			if (target != null) {
				target.discard();
			}
			close(connection);
			clearArena(level, desk, FACING);
		}
	}

	@GameTest(maxTicks = 140, padding = 24)
	public void deathRespawnAndJoinRestorePersistedRemainingOverlayWithoutChat(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(owner(1802), desk, level.getGameTime());
		buildArena(level, desk, FACING);
		ConnectedPlayer connection = createSurvivalPlayer(context, ownerUuid, "remote-life");
		RecordingServerPlayer owner = connection.player();
		completeRealEncounter(context, owner, desk, FACING);
		moveRemoteToHand(context, owner);
		owner.clearFeedback();
		context.assertValueEqual(useRemote(level, owner), InteractionResult.SUCCESS_SERVER,
				"lifecycle fixture starts through ordinary production use");
		PlayerCampaignState coolingDown = state(level, ownerUuid);
		long deadline = coolingDown.remoteCooldownUntilGameTime();
		PlayerList players = level.getServer().getPlayerList();
		ServerPlayer[] respawnedHolder = {null};

		context.runBeforeTestEnd(() -> {
			if (respawnedHolder[0] != null && players.getPlayers().contains(respawnedHolder[0])) {
				players.remove(respawnedHolder[0]);
			}
			close(connection);
			clearArena(level, desk, FACING);
		});
		context.runAfterDelay(40L, () -> {
			boolean originalKeepInventory = level.getGameRules().get(GameRules.KEEP_INVENTORY);
			ServerPlayer respawned;
			try {
				level.getGameRules().set(GameRules.KEEP_INVENTORY, true, level.getServer());
				owner.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
				context.assertTrue(owner.connection.hasClientLoaded(),
						"the embedded connection reaches the vanilla client-loaded damage gate");
				context.assertTrue(owner.hurtServer(
						level, owner.damageSources().generic(), Float.MAX_VALUE),
						"the real server damage path kills the connected player");
				context.assertTrue(owner.isDeadOrDying(),
						"the lifecycle fixture reaches the real ServerPlayer death boundary");
				respawned = players.respawn(owner, false, Entity.RemovalReason.KILLED);
				respawnedHolder[0] = respawned;
			}
			finally {
				level.getGameRules().set(
						GameRules.KEEP_INVENTORY, originalKeepInventory, level.getServer());
			}
			context.assertTrue(respawned != owner && players.getPlayer(ownerUuid) == respawned,
					"PlayerList replaces the dead player through the real respawn boundary");
			ItemStack respawnedRemote = moveRemoteToHand(context, respawned);
			int respawnRemainder = InfiniteSlidesRemoteItem.Cooldown.restoredOverlayTicks(
					deadline, level.getGameTime());
			context.assertTrue(respawnRemainder > 0 && respawnRemainder < InfiniteSlidesRemoteItem.COOLDOWN_TICKS,
					"server ticks, not wall time, reduce the respawn remainder");
			context.assertTrue(respawned.getCooldowns().isOnCooldown(respawnedRemote),
					"the production respawn callback rebuilds the native cooldown from the durable deadline");
			context.assertValueEqual(state(level, ownerUuid), coolingDown,
					"real death and respawn preserve the durable deadline and ready marker");
			int selected = respawned.getInventory().getSelectedSlot();
			ItemStack reconnectRemote = respawned.getInventory().removeItemNoUpdate(selected);
			context.assertTrue(reconnectRemote == respawnedRemote,
					"the reconnect fixture transfers the exact respawned Remote without copying it");

			players.remove(respawned);
			respawnedHolder[0] = null;
			close(connection);
			ConnectedPlayer rejoinConnection = createSurvivalPlayer(
					context, ownerUuid, "remote-rejoin", reconnectRemote);
			RecordingServerPlayer rejoined = rejoinConnection.player();
			ItemStack rejoinedRemote = moveRemoteToHand(context, rejoined);
			int joinRemainder = InfiniteSlidesRemoteItem.Cooldown.restoredOverlayTicks(
					deadline, level.getGameTime());
			context.assertValueEqual(rejoined.trackingCooldowns().startedDurations(),
					List.of(joinRemainder),
					"save/join normalization rebuilds one native cooldown group from the persisted deadline");
			context.assertTrue(rejoined.getCooldowns().isOnCooldown(rejoinedRemote),
					"the joined player's persisted Remote receives the restored native overlay");
			context.assertTrue(rejoined.recordedOverlayKeys().isEmpty()
					&& rejoined.recordedSystemKeys().isEmpty(),
					"join restoration stays silent");
			context.assertValueEqual(state(level, ownerUuid), coolingDown,
					"death, respawn, and join preserve the durable deadline and ready marker");
			close(rejoinConnection);
			context.succeed();
		});
	}

	@GameTest(maxTicks = 460, padding = 24)
	public void readyEdgeWaitsForPresentRemoteAndCriticalInstructionThenEmitsOnce(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(owner(1803), desk, level.getGameTime());
		buildArena(level, desk, FACING);
		ConnectedPlayer connection = createSurvivalPlayer(context, ownerUuid, "remote-ready");
		RecordingServerPlayer owner = connection.player();
		completeRealEncounter(context, owner, desk, FACING);
		moveRemoteToHand(context, owner);
		owner.clearFeedback();
		context.assertValueEqual(useRemote(level, owner), InteractionResult.SUCCESS_SERVER,
				"ready fixture starts through ordinary production use");
		long deadline = state(level, ownerUuid).remoteCooldownUntilGameTime();
		owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		owner.clearFeedback();

		context.runBeforeTestEnd(() -> {
			close(connection);
			clearArena(level, desk, FACING);
		});
		context.runAfterDelay(405L, () -> {
			context.assertTrue(level.getGameTime() >= deadline,
					"the production logical-server clock reaches the saved deadline");
			context.assertValueEqual(state(level, ownerUuid).remoteReadyNoticeForDeadlineGameTime(), 0L,
					"an absent Remote keeps the elapsed ready edge pending");
			context.assertTrue(owner.recordedOverlayKeys().isEmpty() && owner.readySounds() == 0,
					"item absence emits no ambient feedback");

			owner.setItemInHand(InteractionHand.MAIN_HAND, boundRemote(level, ownerUuid));
			context.assertFalse(RewardService.reconcileRemoteReady(owner, true),
					"critical action-bar priority defers the elapsed ready edge");
			context.assertValueEqual(state(level, ownerUuid).remoteReadyNoticeForDeadlineGameTime(), 0L,
					"critical deferral leaves the exact deadline edge unconsumed");
			context.assertTrue(owner.recordedOverlayKeys().isEmpty() && owner.readySounds() == 0,
					"critical deferral cannot overwrite the action bar or play its cue");
			owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		});
		context.runAfterDelay(408L, () -> {
			context.assertValueEqual(state(level, ownerUuid).remoteReadyNoticeForDeadlineGameTime(), 0L,
					"an absent Remote keeps the deferred deadline edge pending across production ticks");
			context.assertTrue(owner.recordedOverlayKeys().isEmpty() && owner.readySounds() == 0,
					"the deferred edge stays silent until presentation is safe and possible");
			owner.setItemInHand(InteractionHand.MAIN_HAND, boundRemote(level, ownerUuid));
		});
		context.runAfterDelay(412L, () -> {
			context.assertValueEqual(state(level, ownerUuid).remoteReadyNoticeForDeadlineGameTime(), deadline,
					"the next production tick persists the exact deferred edge");
			context.assertValueEqual(owner.recordedOverlayKeys(), List.of(READY_KEY),
					"the owner sees one localized ready line");
			context.assertValueEqual(owner.readySounds(), 1, "the owner hears one short ready cue");
		});
		context.runAfterDelay(416L, () -> {
			context.assertValueEqual(owner.recordedOverlayKeys(), List.of(READY_KEY),
					"later production ticks cannot replay the persisted edge");
			context.assertValueEqual(owner.readySounds(), 1,
					"the ready sound never becomes per-tick spam");
			context.succeed();
		});
	}

	private static void completeRealEncounter(
			GameTestHelper context,
			RecordingServerPlayer owner,
			BlockPos desk,
			Direction facing
	) {
		ItemStack contract = new ItemStack(ModItems.CURSED_UNPAID_INTERNSHIP_CONTRACT);
		context.assertTrue(CampaignService.start(owner, desk, facing, contract),
				"Remote fixture starts through the production Contract transaction");
		context.assertTrue(contract.isEmpty(), "accepted Contract is consumed after durable start");
		PlayerCampaignState active = state(owner.level(), owner.getUUID());
		UUID encounterUuid = active.encounterUuid();
		context.assertTrue(LectureEncounterManager.hasCriticalActionInstruction(owner),
				"the real ACTIVE encounter reserves its critical owner action bar");
		ModEntities.ProfessorEntity professor = LectureEncounterManager.professor(encounterUuid)
				.orElseThrow(() -> context.assertionException("missing Remote-path Professor"));

		LectureStateMachine.State slide = combatState(context, encounterUuid);
		placeAtLocal(owner, desk, facing, 9, laneCenter(slide.safeLane()));
		tick(owner.level(), encounterUuid, slide.deadlineTick());
		tick(owner.level(), encounterUuid, slide.deadlineTick() + 1L);
		context.assertTrue(professor.hurtServer(
				owner.level(), owner.damageSources().playerAttack(owner), professor.getMaxHealth()),
				"real Act 1 owner hit is accepted");
		LectureStateMachine.State slideRecovery = combatState(context, encounterUuid);
		context.assertValueEqual(slideRecovery.bossHealth(), 80, "Act 1 preserves the health floor");
		tick(owner.level(), encounterUuid, slideRecovery.deadlineTick());

		LectureStateMachine.State quiz = combatState(context, encounterUuid);
		placeAtLocal(owner, desk, facing, 9, quiz.correctPad().rightAnchor());
		tick(owner.level(), encounterUuid, quiz.deadlineTick());
		tick(owner.level(), encounterUuid, quiz.deadlineTick() + 1L);
		context.assertTrue(professor.hurtServer(
				owner.level(), owner.damageSources().playerAttack(owner), professor.getMaxHealth()),
				"real Act 2 owner hit is accepted");
		LectureStateMachine.State quizRecovery = combatState(context, encounterUuid);
		context.assertValueEqual(quizRecovery.bossHealth(), 40, "Act 2 preserves the health floor");
		tick(owner.level(), encounterUuid, quizRecovery.deadlineTick());

		LectureStateMachine.State attendance = combatState(context, encounterUuid);
		LectureGeometry.LocalPosition ring = LectureGeometry.attendanceCenter(attendance.attendanceQuadrant());
		placeAtLocal(owner, desk, facing, (int) ring.forwardOffset(), (int) ring.rightOffset());
		tick(owner.level(), encounterUuid, attendance.deadlineTick());
		tick(owner.level(), encounterUuid, attendance.deadlineTick() + 1L);
		context.assertTrue(LectureEncounterManager.isVulnerabilityOpen(encounterUuid),
				"real Attendance success opens the final window");
		context.assertTrue(professor.hurtServer(
				owner.level(), owner.damageSources().playerAttack(owner), professor.getMaxHealth()),
				"real final owner hit commits victory and rewards");
		PlayerCampaignState passed = state(owner.level(), owner.getUUID());
		context.assertValueEqual(passed.status(), PlayerCampaignState.LectureStatus.PASSED,
				"Remote fixture reaches the real passed state");
		context.assertTrue(passed.remoteIssued(), "Remote entitlement is committed before use testing");
		context.assertFalse(passed.remoteProjectionPending(),
				"ordinary victory confirms the bound Remote before it is usable");
	}

	private static ItemStack moveRemoteToHand(GameTestHelper context, ServerPlayer owner) {
		PlayerCampaignState state = state(owner.level(), owner.getUUID());
		InfiniteSlidesRemoteItem.Binding expected = new InfiniteSlidesRemoteItem.Binding(
				owner.getUUID(), state.remoteProjectionUuid());
		int selected = owner.getInventory().getSelectedSlot();
		for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
			ItemStack stack = owner.getInventory().getItem(slot);
			if (InfiniteSlidesRemoteItem.binding(stack).filter(expected::equals).isEmpty()) {
				continue;
			}
			if (slot != selected) {
				owner.getInventory().setItem(selected, stack);
				owner.getInventory().setItem(slot, ItemStack.EMPTY);
			}
			return owner.getInventory().getItem(selected);
		}
		throw context.assertionException("real victory did not grant the production Remote");
	}

	private static ItemStack boundRemote(ServerLevel level, UUID ownerUuid) {
		PlayerCampaignState state = state(level, ownerUuid);
		return InfiniteSlidesRemoteItem.bound(new InfiniteSlidesRemoteItem.Binding(
				ownerUuid, state.remoteProjectionUuid()));
	}

	private static InteractionResult useRemote(ServerLevel level, ServerPlayer owner) {
		ItemStack stack = owner.getItemInHand(InteractionHand.MAIN_HAND);
		return owner.gameMode.useItem(owner, level, stack, InteractionHand.MAIN_HAND);
	}

	private static PlayerCampaignState state(ServerLevel level, UUID ownerUuid) {
		return CampaignSavedData.get(level).player(ownerUuid).orElseThrow();
	}

	private static LectureStateMachine.State combatState(GameTestHelper context, UUID encounterUuid) {
		return LectureEncounterManager.combatState(encounterUuid)
				.orElseThrow(() -> context.assertionException("missing combat state for %s", encounterUuid));
	}

	private static void tick(ServerLevel level, UUID encounterUuid, long gameTick) {
		LectureEncounterManager.tick(level.getServer(), encounterUuid, gameTick);
	}

	private static int laneCenter(LectureGeometry.Lane lane) {
		return switch (lane) {
			case LEFT -> -5;
			case CENTER -> 0;
			case RIGHT -> 5;
		};
	}

	private static void placeAtLocal(
			ServerPlayer player,
			BlockPos desk,
			Direction facing,
			int forward,
			int right
	) {
		BlockPos feet = desk.relative(facing, forward).relative(facing.getClockWise(), right);
		Vec3 position = Vec3.atBottomCenterOf(feet);
		player.snapTo(position.x, position.y, position.z);
	}

	private static ConnectedPlayer createSurvivalPlayer(
			GameTestHelper context,
			UUID uuid,
			String name
	) {
		return createSurvivalPlayer(context, uuid, name, ItemStack.EMPTY);
	}

	private static ConnectedPlayer createSurvivalPlayer(
			GameTestHelper context,
			UUID uuid,
			String name,
			ItemStack initialSelectedStack
	) {
		ServerLevel level = context.getLevel();
		MinecraftServer server = level.getServer();
		PlayerList players = server.getPlayerList();
		GameProfile profile = new GameProfile(uuid, name);
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
		RecordingServerPlayer player = new RecordingServerPlayer(
				server, level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel(connection);
		AtomicBoolean cleaned = new AtomicBoolean();
		Runnable cleanup = () -> {
			if (!cleaned.compareAndSet(false, true)) {
				return;
			}
			try {
				if (players.getPlayers().contains(player)) {
					players.remove(player);
				}
			}
			finally {
				channel.finishAndReleaseAll();
			}
		};
		context.runBeforeTestEnd(cleanup);
		try {
			if (!initialSelectedStack.isEmpty()) {
				player.getInventory().setItem(
						player.getInventory().getSelectedSlot(), initialSelectedStack);
			}
			players.placeNewPlayer(connection, player, cookie);
			player.setGameMode(GameType.SURVIVAL);
			Vec3 spawn = context.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(12, 2, 2)));
			player.snapTo(spawn.x, spawn.y, spawn.z);
			return new ConnectedPlayer(player, cleanup);
		}
		catch (RuntimeException | Error failure) {
			cleanup.run();
			throw failure;
		}
	}

	private static void buildArena(ServerLevel level, BlockPos desk, Direction facing) {
		Direction right = facing.getClockWise();
		level.setBlock(desk.below(), Blocks.STONE.defaultBlockState(), 3);
		level.setBlock(desk, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing), 3);
		BlockPos retry = desk.relative(facing.getOpposite(), 2);
		level.setBlock(retry.below(), Blocks.STONE.defaultBlockState(), 3);
		level.setBlock(retry, Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(retry.above(), Blocks.AIR.defaultBlockState(), 3);
		for (int forward = 1; forward <= 17; forward++) {
			for (int lateral = -8; lateral <= 8; lateral++) {
				BlockPos floor = desk.relative(facing, forward).relative(right, lateral).below();
				level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
				for (int headroom = 1; headroom <= 4; headroom++) {
					level.setBlock(floor.above(headroom), Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}
	}

	private static void clearArena(ServerLevel level, BlockPos desk, Direction facing) {
		Direction right = facing.getClockWise();
		level.setBlock(desk.below(), Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(desk, Blocks.AIR.defaultBlockState(), 3);
		BlockPos retry = desk.relative(facing.getOpposite(), 2);
		level.setBlock(retry.below(), Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(retry, Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(retry.above(), Blocks.AIR.defaultBlockState(), 3);
		for (int forward = 1; forward <= 17; forward++) {
			for (int lateral = -8; lateral <= 8; lateral++) {
				BlockPos floor = desk.relative(facing, forward).relative(right, lateral).below();
				for (int height = 0; height <= 4; height++) {
					level.setBlock(floor.above(height), Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}
	}

	private static UUID invocationOwnerUuid(UUID seed, BlockPos desk, long gameTime) {
		String value = seed + ":" + desk.getX() + ":" + desk.getY() + ":" + desk.getZ() + ":" + gameTime;
		return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
	}

	private static UUID owner(int suffix) {
		return UUID.fromString("c0de0000-0000-4000-8000-%012d".formatted(suffix));
	}

	private static String translationKey(Component component) {
		if (component == null || !(component.getContents() instanceof TranslatableContents translated)) {
			return "";
		}
		return translated.getKey();
	}

	private static void close(ConnectedPlayer connection) {
		if (connection != null) {
			connection.close();
		}
	}

	private static final class TrackingCooldowns extends ItemCooldowns {
		private final List<Integer> startedDurations = new ArrayList<>();

		@Override
		public void addCooldown(ItemStack stack, int duration) {
			startedDurations.add(duration);
			super.addCooldown(stack, duration);
		}

		private List<Integer> startedDurations() {
			return List.copyOf(startedDurations);
		}
	}

	private static final class RecordingServerPlayer extends ServerPlayer {
		private final List<Component> recordedOverlayMessages = new ArrayList<>();
		private final List<Component> recordedSystemMessages = new ArrayList<>();
		private int readySounds;

		private RecordingServerPlayer(
				MinecraftServer server,
				ServerLevel level,
				GameProfile profile,
				ClientInformation clientInformation
		) {
			super(server, level, profile, clientInformation);
		}

		@Override
		protected ItemCooldowns createItemCooldowns() {
			return new TrackingCooldowns();
		}

		@Override
		public void sendOverlayMessage(Component message) {
			recordedOverlayMessages.add(message);
		}

		@Override
		public void sendSystemMessage(Component message) {
			recordedSystemMessages.add(message);
		}

		@Override
		public void playSound(SoundEvent sound, float volume, float pitch) {
			readySounds++;
		}

		private TrackingCooldowns trackingCooldowns() {
			return (TrackingCooldowns) getCooldowns();
		}

		private void clearFeedback() {
			recordedOverlayMessages.clear();
			recordedSystemMessages.clear();
			readySounds = 0;
		}

		private List<String> recordedOverlayKeys() {
			return recordedOverlayMessages.stream().map(RemoteGameTests::translationKey).toList();
		}

		private List<String> recordedSystemKeys() {
			return recordedSystemMessages.stream().map(RemoteGameTests::translationKey).toList();
		}

		private int readySounds() {
			return readySounds;
		}
	}

	private record ConnectedPlayer(RecordingServerPlayer player, Runnable cleanup) implements AutoCloseable {
		@Override
		public void close() {
			cleanup.run();
		}
	}

	@Override
	public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
		method.invoke(this, context);
	}
}
