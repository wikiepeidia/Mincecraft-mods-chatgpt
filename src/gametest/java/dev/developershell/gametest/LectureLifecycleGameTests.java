package dev.developershell.gametest;

import com.mojang.authlib.GameProfile;
import dev.developershell.campaign.CampaignSavedData;
import dev.developershell.campaign.CampaignService;
import dev.developershell.campaign.PlayerCampaignState;
import dev.developershell.entity.ProfessorInfiniteSlidesEntity;
import dev.developershell.lecture.LectureEncounterManager;
import dev.developershell.registry.ModEntities;
import dev.developershell.registry.ModItems;
import dev.developershell.server.CampaignLifecycle;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class LectureLifecycleGameTests implements CustomTestMethodInvoker {
	private static final UUID EXIT_OWNER_UUID = UUID.fromString("c0de0000-0000-4000-8000-000000000501");
	private static final UUID RELOAD_OWNER_UUID = UUID.fromString("c0de0000-0000-4000-8000-000000000502");
	private static final String RELOAD_KEY = "message.developers_hell.lecture.reload";
	private static final String RETAKE_KEY = "message.developers_hell.lecture.retake";

	@GameTest(padding = 24)
	public void deathEscapeTimeoutDimensionDisconnectAbortAndUnloadConvergeExactlyOnce(
			GameTestHelper context
	) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(new BlockPos(12, 2, 4));
		Direction facing = Direction.SOUTH;
		buildArena(level, desk, facing);
		List<BlockState> blocksBefore = snapshotArena(level, desk, facing);
		ConnectedPlayer connection = null;

		try {
			connection = createSurvivalPlayer(context, EXIT_OWNER_UUID, "lifecycle-exits", new BlockPos(12, 2, 2));
			RecordingServerPlayer owner = connection.player();

			PlayerCampaignState death = startAttempt(context, level, owner, desk, facing);
			ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(owner, owner.damageSources().generic());
			assertConverged(context, level, death, blocksBefore, desk, facing, "death");
			PlayerCampaignState deathResult = CampaignSavedData.get(level).player(owner.getUUID()).orElseThrow();
			ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(owner, owner.damageSources().generic());
			context.assertValueEqual(CampaignSavedData.get(level).player(owner.getUUID()).orElseThrow(), deathResult,
					"repeated death is a state no-op");

			PlayerCampaignState escape = startAttempt(context, level, owner, desk, facing);
			owner.snapTo(Vec3.atBottomCenterOf(desk.offset(40, 0, 40)));
			LectureEncounterManager.tick(level.getServer());
			assertConverged(context, level, escape, blocksBefore, desk, facing, "escape");
			owner.snapTo(Vec3.atBottomCenterOf(desk.relative(facing.getOpposite(), 2)));

			PlayerCampaignState timeout = startAttempt(context, level, owner, desk, facing);
			LectureEncounterManager.RuntimeSnapshot timeoutRuntime = LectureEncounterManager.runtimeSnapshot(
					timeout.encounterUuid()
			).orElseThrow(() -> context.assertionException("missing timeout runtime"));
			LectureEncounterManager.tick(
					level.getServer(),
					timeoutRuntime.startedAtGameTime() + LectureEncounterManager.ENCOUNTER_TIMEOUT_TICKS
			);
			assertConverged(context, level, timeout, blocksBefore, desk, facing, "timeout");

			PlayerCampaignState dimension = startAttempt(context, level, owner, desk, facing);
			ServerLevel nether = level.getServer().getLevel(Level.NETHER);
			context.assertTrue(nether != null, "GameTest server must expose the Nether for dimension cleanup proof");
			owner.setServerLevel(nether);
			try {
				LectureEncounterManager.tick(level.getServer());
			}
			finally {
				owner.setServerLevel(level);
			}
			assertConverged(context, level, dimension, blocksBefore, desk, facing, "dimension");

			PlayerCampaignState disconnect = startAttempt(context, level, owner, desk, facing);
			ServerPlayerEvents.LEAVE.invoker().onLeave(owner);
			assertConverged(context, level, disconnect, blocksBefore, desk, facing, "disconnect");

			PlayerCampaignState abort = startAttempt(context, level, owner, desk, facing);
			context.assertTrue(CampaignLifecycle.onAbort(owner), "matching abort must be accepted");
			assertConverged(context, level, abort, blocksBefore, desk, facing, "abort");
			context.assertFalse(CampaignLifecycle.onAbort(owner), "replayed abort must be rejected");

			PlayerCampaignState unload = startAttempt(context, level, owner, desk, facing);
			ProfessorInfiniteSlidesEntity professor = (ProfessorInfiniteSlidesEntity) LectureEncounterManager.professor(
					unload.encounterUuid()
			).orElseThrow(() -> context.assertionException("missing unload Professor"));
			ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(professor, level);
			assertConverged(context, level, unload, blocksBefore, desk, facing, "unload");
			PlayerCampaignState unloadResult = CampaignSavedData.get(level).player(owner.getUUID()).orElseThrow();
			ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(professor, level);
			context.assertValueEqual(CampaignSavedData.get(level).player(owner.getUUID()).orElseThrow(), unloadResult,
					"cleanup-generated unload is stale");

			connection.close();
			connection = null;
			context.succeed();
		}
		finally {
			if (connection != null) {
				connection.close();
			}
			cleanupOwnerRuntimes(level.getServer(), EXIT_OWNER_UUID);
			clearArena(level, desk, facing);
		}
	}

	@GameTest(padding = 24)
	public void reloadJoinRejectsOrphanAndCancelsEveryQueuedImpact(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(new BlockPos(12, 2, 4));
		Direction facing = Direction.SOUTH;
		buildArena(level, desk, facing);
		List<BlockState> blocksBefore = snapshotArena(level, desk, facing);
		ConnectedPlayer connection = null;

		try {
			connection = createSurvivalPlayer(context, RELOAD_OWNER_UUID, "lifecycle-reload", new BlockPos(12, 2, 2));
			RecordingServerPlayer owner = connection.player();
			PlayerCampaignState reload = startAttempt(context, level, owner, desk, facing);
			owner.clearRecordedSystemMessages();
			ServerPlayerEvents.JOIN.invoker().onJoin(owner);
			assertConverged(context, level, reload, blocksBefore, desk, facing, "reload");
			context.assertValueEqual(owner.recordedSystemMessageKeys(), List.of(RELOAD_KEY, RETAKE_KEY),
					"reload shows exactly one safe Retake message group");

			owner.clearRecordedSystemMessages();
			ServerPlayerEvents.JOIN.invoker().onJoin(owner);
			LectureEncounterManager.tick(level.getServer(), level.getGameTime() + LectureEncounterManager.ENCOUNTER_TIMEOUT_TICKS);
			context.assertTrue(owner.recordedSystemMessageKeys().isEmpty(),
					"replayed join neither resumes a cast nor repeats presentation");

			ProfessorInfiniteSlidesEntity incomplete = ModEntities.PROFESSOR.create(level, EntitySpawnReason.LOAD);
			context.assertTrue(incomplete != null, "incomplete orphan construction");
			context.assertFalse(ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(
					incomplete, level, EntitySpawnReason.LOAD, true
			), "incomplete disk Professor must be rejected");

			ProfessorInfiniteSlidesEntity stale = ModEntities.PROFESSOR.create(level, EntitySpawnReason.LOAD);
			context.assertTrue(stale != null, "stale orphan construction");
			stale.bind(owner.getUUID(), UUID.fromString("c0de0000-0000-4000-8000-000000000599"));
			stale.setUUID(UUID.fromString("c0de0000-0000-4000-8000-000000000598"));
			context.assertFalse(ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(
					stale, level, EntitySpawnReason.LOAD, true
			), "stale disk Professor must be rejected");
			ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(stale, level);
			context.assertTrue(stale.isRemoved(), "defensive entity-load guard discards a stale Professor");

			PlayerCampaignState matchingDisk = startAttempt(context, level, owner, desk, facing);
			ProfessorInfiniteSlidesEntity restored = ModEntities.PROFESSOR.create(level, EntitySpawnReason.LOAD);
			context.assertTrue(restored != null, "matching disk Professor construction");
			restored.bind(owner.getUUID(), matchingDisk.encounterUuid());
			restored.setUUID(matchingDisk.professorUuid());
			owner.clearRecordedSystemMessages();
			context.assertFalse(ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(
					restored, level, EntitySpawnReason.LOAD, true
			), "matching disk Professor is rejected after startup normalization");
			assertConverged(context, level, matchingDisk, blocksBefore, desk, facing, "orphan reload");
			context.assertValueEqual(owner.recordedSystemMessageKeys(), List.of(RELOAD_KEY, RETAKE_KEY),
					"matching disk normalization visibly returns the online owner to Retake");

			connection.close();
			connection = null;
			context.succeed();
		}
		finally {
			if (connection != null) {
				connection.close();
			}
			cleanupOwnerRuntimes(level.getServer(), RELOAD_OWNER_UUID);
			clearArena(level, desk, facing);
		}
	}

	private static PlayerCampaignState startAttempt(
			GameTestHelper context,
			ServerLevel level,
			ServerPlayer owner,
			BlockPos desk,
			Direction facing
	) {
		ItemStack contract = new ItemStack(ModItems.CURSED_UNPAID_INTERNSHIP_CONTRACT);
		context.assertTrue(CampaignService.start(owner, desk, facing, contract), "attempt must start through production service");
		PlayerCampaignState active = CampaignSavedData.get(level).player(owner.getUUID())
				.orElseThrow(() -> context.assertionException("missing active campaign state"));
		context.assertValueEqual(active.status(), PlayerCampaignState.LectureStatus.ACTIVE, "attempt starts active");
		context.assertTrue(active.activeEncounterRef() != null, "attempt stores one active reference");
		context.assertTrue(LectureEncounterManager.runtimeSnapshot(active.encounterUuid()).isPresent(),
				"attempt owns one bounded runtime");
		context.assertTrue(LectureEncounterManager.presentation(active.encounterUuid()).isPresent(),
				"attempt owns one presentation");
		return active;
	}

	private static void assertConverged(
			GameTestHelper context,
			ServerLevel level,
			PlayerCampaignState active,
			List<BlockState> blocksBefore,
			BlockPos desk,
			Direction facing,
			String exit
	) {
		PlayerCampaignState safe = CampaignSavedData.get(level).player(active.ownerUuid())
				.orElseThrow(() -> context.assertionException("missing state after %s", exit));
		context.assertValueEqual(safe.status(), PlayerCampaignState.LectureStatus.RETAKE_READY, exit + " status");
		context.assertTrue(safe.activeEncounterRef() == null, exit + " clears active identity before cleanup");
		context.assertValueEqual(safe.chapter(), active.chapter(), exit + " preserves chapter");
		context.assertValueEqual(safe.attemptCount(), active.attemptCount(), exit + " preserves attempts");
		context.assertValueEqual(safe.deskPos(), active.deskPos(), exit + " preserves desk");
		context.assertValueEqual(safe.retryPos(), active.retryPos(), exit + " preserves retry point");
		context.assertValueEqual(safe.sheetEntitled(), active.sheetEntitled(), exit + " preserves Sheet ledger");
		context.assertValueEqual(safe.remoteIssued(), active.remoteIssued(), exit + " preserves Remote ledger");
		context.assertValueEqual(safe.remoteCooldownUntilGameTime(), active.remoteCooldownUntilGameTime(),
				exit + " preserves cooldown");
		context.assertTrue(safe.retakeEntitled(), exit + " exposes safe Retake authority");
		context.assertTrue(CampaignSavedData.get(level).isDirty(), exit + " is durable before effects");
		context.assertFalse(LectureEncounterManager.runtimeSnapshot(active.encounterUuid()).isPresent(),
				exit + " removes bounded runtime");
		context.assertFalse(LectureEncounterManager.presentation(active.encounterUuid()).isPresent(),
				exit + " removes boss presentation");
		context.assertValueEqual(level.getEntities(ModEntities.PROFESSOR, professor ->
				active.ownerUuid().equals(professor.ownerUuid())
						&& active.encounterUuid().equals(professor.encounterUuid())).size(), 0,
				exit + " removes owned Professor");
		context.assertValueEqual(snapshotArena(level, desk, facing), blocksBefore, exit + " preserves arena blocks");
	}

	private static void cleanupOwnerRuntimes(MinecraftServer server, UUID ownerUuid) {
		for (LectureEncounterManager.RuntimeSnapshot runtime : LectureEncounterManager.activeRuntimeSnapshots(server)) {
			if (runtime.ownerUuid().equals(ownerUuid)) {
				LectureEncounterManager.cleanup(runtime.encounterUuid());
			}
		}
	}

	private static ConnectedPlayer createSurvivalPlayer(
			GameTestHelper context,
			UUID uuid,
			String name,
			BlockPos relativeSpawn
	) {
		ServerLevel level = context.getLevel();
		MinecraftServer server = level.getServer();
		PlayerList players = server.getPlayerList();
		GameProfile profile = new GameProfile(uuid, name);
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
		RecordingServerPlayer player = new RecordingServerPlayer(
				server,
				level,
				cookie.gameProfile(),
				cookie.clientInformation()
		);
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
			players.placeNewPlayer(connection, player, cookie);
			player.setGameMode(GameType.SURVIVAL);
			Vec3 spawn = context.absoluteVec(Vec3.atBottomCenterOf(relativeSpawn));
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

	private static List<BlockState> snapshotArena(ServerLevel level, BlockPos desk, Direction facing) {
		Direction right = facing.getClockWise();
		List<BlockState> snapshot = new ArrayList<>(17 * 17 * 5);
		for (int forward = 1; forward <= 17; forward++) {
			for (int lateral = -8; lateral <= 8; lateral++) {
				BlockPos floor = desk.relative(facing, forward).relative(right, lateral).below();
				for (int height = 0; height <= 4; height++) {
					snapshot.add(level.getBlockState(floor.above(height)));
				}
			}
		}
		return List.copyOf(snapshot);
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

	private static String translationKey(Component component) {
		if (component == null || !(component.getContents() instanceof TranslatableContents translated)) {
			return "";
		}
		return translated.getKey();
	}

	private static final class RecordingServerPlayer extends ServerPlayer {
		private final List<Component> recordedSystemMessages = new ArrayList<>();

		private RecordingServerPlayer(
				MinecraftServer server,
				ServerLevel level,
				GameProfile profile,
				ClientInformation clientInformation
		) {
			super(server, level, profile, clientInformation);
		}

		@Override
		public void sendSystemMessage(Component message) {
			recordedSystemMessages.add(message);
			super.sendSystemMessage(message);
		}

		private void clearRecordedSystemMessages() {
			recordedSystemMessages.clear();
		}

		private List<String> recordedSystemMessageKeys() {
			return recordedSystemMessages.stream().map(LectureLifecycleGameTests::translationKey).toList();
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
