package dev.developershell.gametest;

import com.mojang.authlib.GameProfile;
import dev.developershell.campaign.CampaignEvent;
import dev.developershell.campaign.CampaignSavedData;
import dev.developershell.campaign.CampaignService;
import dev.developershell.campaign.PlayerCampaignState;
import dev.developershell.lecture.LectureEncounterManager;
import dev.developershell.lecture.LectureGeometry;
import dev.developershell.registry.ModEntities;
import dev.developershell.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class ContractArenaGameTests implements CustomTestMethodInvoker {
	private static final Direction FACING = Direction.SOUTH;
	private static final BlockPos RELATIVE_DESK = new BlockPos(12, 2, 4);
	private static final UUID WRONG_TARGET_OWNER = owner(601);
	private static final UUID WRONG_DIMENSION_OWNER = owner(602);
	private static final UUID BORDER_OWNER = owner(603);
	private static final UUID FLOOR_OWNER = owner(604);
	private static final UUID HEADROOM_OWNER = owner(605);
	private static final UUID RETRY_OWNER = owner(606);
	private static final UUID ACTIVE_OWNER = owner(607);
	private static final UUID SPAWN_OWNER = owner(608);
	private static final UUID VALID_OWNER = owner(609);

	@GameTest(maxTicks = 80, padding = 24)
	public void wrongTargetRejectionIsAtomic(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos target = context.absolutePos(RELATIVE_DESK);
		WorldEdits edits = new WorldEdits(level);
		ConnectedPlayer connection = null;
		try {
			edits.set(target, Blocks.STONE.defaultBlockState());
			connection = createSurvivalPlayer(context, level, WRONG_TARGET_OWNER, "arena-wrong-target");
			RecordingServerPlayer player = connection.player();
			ItemStack contract = contract(player, 1);
			assertAtomicRejection(
					context,
					level,
					player,
					contract,
					target,
					edits,
					InteractionResult.SUCCESS_SERVER,
					"message.developers_hell.contract.find_lectern"
			);
			context.succeed();
		}
		finally {
			close(connection);
			edits.restore();
		}
	}

	@GameTest(maxTicks = 80, padding = 24)
	public void wrongDimensionRejectionIsAtomic(GameTestHelper context) {
		ServerLevel overworld = context.getLevel();
		ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
		context.assertTrue(nether != null, "GameTest server must expose the Nether for exact dimension rejection");
		BlockPos desk = new BlockPos(8, 64, 8);
		WorldEdits edits = new WorldEdits(nether);
		RecordingServerPlayer player = detachedPlayer(nether, WRONG_DIMENSION_OWNER, "arena-wrong-dimension");
		try {
			edits.set(desk.below(), Blocks.STONE.defaultBlockState());
			edits.set(desk, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, FACING));
			ItemStack contract = contract(player, 1);
			assertAtomicRejection(
					context,
					nether,
					player,
					contract,
					desk,
					edits,
					InteractionResult.FAIL,
					"message.developers_hell.contract.rejected.dimension"
			);
			context.succeed();
		}
		finally {
			edits.restore();
		}
	}

	@GameTest(maxTicks = 80, padding = 24)
	public void outsideBorderRejectionIsAtomic(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		WorldEdits edits = new WorldEdits(level);
		ConnectedPlayer connection = null;
		WorldBorder border = level.getWorldBorder();
		double previousCenterX = border.getCenterX();
		double previousCenterZ = border.getCenterZ();
		double previousSize = border.getSize();
		try {
			buildValidArena(edits, desk, FACING);
			connection = createSurvivalPlayer(context, level, BORDER_OWNER, "arena-border");
			RecordingServerPlayer player = connection.player();
			ItemStack contract = contract(player, 1);
			border.setCenter(desk.getX() + 0.5D, desk.getZ() + 0.5D);
			border.setSize(8.0D);
			assertAtomicRejection(
					context,
					level,
					player,
					contract,
					desk,
					edits,
					InteractionResult.FAIL,
					"message.developers_hell.contract.rejected.loaded_border"
			);
			context.succeed();
		}
		finally {
			border.setCenter(previousCenterX, previousCenterZ);
			border.setSize(previousSize);
			close(connection);
			edits.restore();
		}
	}

	@GameTest(maxTicks = 80, padding = 24)
	public void nonSolidFloorRejectionIsAtomic(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		WorldEdits edits = new WorldEdits(level);
		ConnectedPlayer connection = null;
		try {
			buildValidArena(edits, desk, FACING);
			edits.set(LectureGeometry.layout(desk, FACING).floorAt(17, 8), Blocks.AIR.defaultBlockState());
			connection = createSurvivalPlayer(context, level, FLOOR_OWNER, "arena-floor");
			RecordingServerPlayer player = connection.player();
			assertAtomicRejection(context, level, player, contract(player, 1), desk, edits, InteractionResult.FAIL,
					"message.developers_hell.contract.rejected.floor");
			context.succeed();
		}
		finally {
			close(connection);
			edits.restore();
		}
	}

	@GameTest(maxTicks = 80, padding = 24)
	public void insufficientHeadroomRejectionIsAtomic(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		WorldEdits edits = new WorldEdits(level);
		ConnectedPlayer connection = null;
		try {
			buildValidArena(edits, desk, FACING);
			edits.set(LectureGeometry.layout(desk, FACING).floorAt(2, -7).above(4), Blocks.STONE.defaultBlockState());
			connection = createSurvivalPlayer(context, level, HEADROOM_OWNER, "arena-headroom");
			RecordingServerPlayer player = connection.player();
			assertAtomicRejection(context, level, player, contract(player, 1), desk, edits, InteractionResult.FAIL,
					"message.developers_hell.contract.rejected.headroom");
			context.succeed();
		}
		finally {
			close(connection);
			edits.restore();
		}
	}

	@GameTest(maxTicks = 80, padding = 24)
	public void unsafeRetryRejectionIsAtomic(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		WorldEdits edits = new WorldEdits(level);
		ConnectedPlayer connection = null;
		try {
			buildValidArena(edits, desk, FACING);
			for (BlockPos candidate : LectureGeometry.layout(desk, FACING).retryCandidates()) {
				edits.set(candidate.below(), Blocks.AIR.defaultBlockState());
				edits.set(candidate, Blocks.AIR.defaultBlockState());
				edits.set(candidate.above(), Blocks.AIR.defaultBlockState());
			}
			connection = createSurvivalPlayer(context, level, RETRY_OWNER, "arena-retry");
			RecordingServerPlayer player = connection.player();
			assertAtomicRejection(context, level, player, contract(player, 1), desk, edits, InteractionResult.FAIL,
					"message.developers_hell.contract.rejected.retry");
			context.succeed();
		}
		finally {
			close(connection);
			edits.restore();
		}
	}

	@GameTest(maxTicks = 80, padding = 24)
	public void spawnCapacityRejectionIsAtomic(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		WorldEdits edits = new WorldEdits(level);
		ConnectedPlayer connection = null;
		Vindicator blocker = null;
		try {
			buildValidArena(edits, desk, FACING);
			blocker = EntityTypes.VINDICATOR.create(level, EntitySpawnReason.EVENT);
			context.assertTrue(blocker != null, "Vindicator must create for spawn-capacity fixture");
			BlockPos professorFeet = LectureGeometry.layout(desk, FACING).combatCenterFloor().above();
			blocker.snapTo(Vec3.atBottomCenterOf(professorFeet));
			context.assertTrue(level.addFreshEntity(blocker), "spawn-capacity blocker must enter the fixture");
			connection = createSurvivalPlayer(context, level, SPAWN_OWNER, "arena-spawn");
			RecordingServerPlayer player = connection.player();
			assertAtomicRejection(context, level, player, contract(player, 1), desk, edits, InteractionResult.FAIL,
					"message.developers_hell.contract.rejected.spawn");
			context.succeed();
		}
		finally {
			if (blocker != null) {
				blocker.discard();
			}
			close(connection);
			edits.restore();
		}
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void activeEncounterRejectionIsAtomic(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		WorldEdits edits = new WorldEdits(level);
		ConnectedPlayer connection = null;
		UUID encounterUuid = null;
		try {
			buildValidArena(edits, desk, FACING);
			connection = createSurvivalPlayer(context, level, ACTIVE_OWNER, "arena-active");
			RecordingServerPlayer player = connection.player();
			ItemStack contracts = contract(player, 2);
			context.assertValueEqual(useBlock(level, player, desk), InteractionResult.SUCCESS_SERVER,
					"fixture start must be accepted");
			PlayerCampaignState active = CampaignSavedData.get(level).player(ACTIVE_OWNER)
					.orElseThrow(() -> context.assertionException("missing active state"));
			encounterUuid = active.encounterUuid();
			assertAtomicRejection(context, level, player, contracts, desk, edits, InteractionResult.FAIL,
					"message.developers_hell.contract.rejected.active");
			context.succeed();
		}
		finally {
			abort(level, ACTIVE_OWNER, encounterUuid);
			close(connection);
			edits.restore();
		}
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void validStartPersistsExactGeometryBeforeEffects(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		LectureGeometry.Layout layout = LectureGeometry.layout(desk, FACING);
		WorldEdits edits = new WorldEdits(level);
		ConnectedPlayer connection = null;
		UUID encounterUuid = null;
		try {
			buildValidArena(edits, desk, FACING);
			connection = createSurvivalPlayer(context, level, VALID_OWNER, "arena-valid");
			RecordingServerPlayer player = connection.player();
			ItemStack contracts = contract(player, 2);
			Map<BlockPos, BlockState> blocksBefore = edits.currentSnapshot();
			ServerPlayer.RespawnConfig playerRespawnBefore = player.getRespawnConfig();
			LevelData.RespawnData worldRespawnBefore = level.getRespawnData();
			int runtimeBefore = LectureEncounterManager.activeRuntimeCount();
			int professorBefore = professors(level);
			player.clearRecordedSystemMessages();

			context.assertValueEqual(useBlock(level, player, desk), InteractionResult.SUCCESS_SERVER,
					"valid Contract result");
			context.assertValueEqual(contracts.getCount(), 1, "valid start consumes exactly one Contract");
			PlayerCampaignState active = CampaignSavedData.get(level).player(VALID_OWNER)
					.orElseThrow(() -> context.assertionException("missing accepted campaign state"));
			encounterUuid = active.encounterUuid();
			context.assertValueEqual(active.status(), PlayerCampaignState.LectureStatus.ACTIVE, "active status");
			context.assertValueEqual(active.attemptCount(), 1, "first attempt");
			context.assertValueEqual(active.deskDimension(), PlayerCampaignState.OVERWORLD_DIMENSION, "desk dimension");
			context.assertValueEqual(active.deskPos(), desk, "exact desk");
			context.assertValueEqual(active.deskFacing(), FACING, "exact facing");
			context.assertValueEqual(active.retryPos(), layout.retryCandidates().getFirst(), "first safe retry");
			context.assertValueEqual(
					level.getEntities(ModEntities.PROFESSOR, entity -> entity.ownerUuid().equals(VALID_OWNER)).getFirst().blockPosition(),
					layout.combatCenterFloor().above(),
					"derived combat origin"
			);
			context.assertTrue(CampaignSavedData.get(level).isDirty(), "START is dirty before runtime effects");
			context.assertValueEqual(LectureEncounterManager.activeRuntimeCount(), runtimeBefore + 1, "one runtime");
			context.assertValueEqual(professors(level), professorBefore + 1, "one Professor");
			context.assertValueEqual(edits.currentSnapshot(), blocksBefore, "valid start changes no selected block");
			context.assertTrue(Objects.equals(player.getRespawnConfig(), playerRespawnBefore), "player respawn unchanged");
			context.assertValueEqual(level.getRespawnData(), worldRespawnBefore, "world respawn unchanged");
			context.assertValueEqual(
					player.recordedSystemMessageKeys(),
					List.of("message.developers_hell.contract.signed", "message.developers_hell.lecture.objective"),
					"one accepted Contract message group"
			);
			context.succeed();
		}
		finally {
			abort(level, VALID_OWNER, encounterUuid);
			close(connection);
			edits.restore();
		}
	}

	private static void assertAtomicRejection(
			GameTestHelper context,
			ServerLevel level,
			RecordingServerPlayer player,
			ItemStack contract,
			BlockPos target,
			WorldEdits edits,
			InteractionResult expectedResult,
			String expectedMessageKey
	) {
		SurfaceSnapshot before = snapshot(level, player, contract, edits);
		player.clearRecordedSystemMessages();
		InteractionResult result = useBlock(level, player, target);
		SurfaceSnapshot after = snapshot(level, player, contract, edits);

		context.assertValueEqual(result, expectedResult, expectedMessageKey + " interaction result");
		context.assertValueEqual(after, before, expectedMessageKey + " five-surface and respawn no-op");
		context.assertValueEqual(player.recordedSystemMessageKeys(), List.of(expectedMessageKey),
				expectedMessageKey + " exact localized feedback");
	}

	private static SurfaceSnapshot snapshot(
			ServerLevel level,
			RecordingServerPlayer player,
			ItemStack contract,
			WorldEdits edits
	) {
		long presentations = LectureEncounterManager.activeRuntimeSnapshots(level.getServer()).stream()
				.filter(runtime -> runtime.level() == level)
				.filter(runtime -> LectureEncounterManager.presentation(runtime.encounterUuid()).isPresent())
				.count();
		return new SurfaceSnapshot(
				contract.getCount(),
				CampaignSavedData.get(level).player(player.getUUID()),
				professors(level),
				LectureEncounterManager.activeRuntimeCount(),
				presentations,
				edits.currentSnapshot(),
				player.getRespawnConfig(),
				level.getRespawnData()
		);
	}

	private static void buildValidArena(WorldEdits edits, BlockPos desk, Direction facing) {
		LectureGeometry.Layout layout = LectureGeometry.layout(desk, facing);
		edits.set(desk.below(), Blocks.STONE.defaultBlockState());
		edits.set(desk, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing));
		for (BlockPos floor : layout.boundaryFloorPositions()) {
			edits.set(floor, Blocks.STONE.defaultBlockState());
		}
		for (BlockPos floor : layout.interiorFloorPositions()) {
			for (int height = 1; height <= 4; height++) {
				edits.set(floor.above(height), Blocks.AIR.defaultBlockState());
			}
		}
		for (BlockPos candidate : layout.retryCandidates()) {
			edits.set(candidate.below(), Blocks.AIR.defaultBlockState());
			edits.set(candidate, Blocks.AIR.defaultBlockState());
			edits.set(candidate.above(), Blocks.AIR.defaultBlockState());
		}
		BlockPos retry = layout.retryCandidates().getFirst();
		edits.set(retry.below(), Blocks.STONE.defaultBlockState());
		// The one-block safety margin is not combat headroom and may remain occupied.
		edits.set(layout.floorAt(1, 0).above(), Blocks.STONE.defaultBlockState());
	}

	private static ItemStack contract(ServerPlayer player, int count) {
		ItemStack stack = new ItemStack(ModItems.CURSED_UNPAID_INTERNSHIP_CONTRACT, count);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		return stack;
	}

	private static InteractionResult useBlock(ServerLevel level, ServerPlayer player, BlockPos target) {
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
		return UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
	}

	private static int professors(ServerLevel level) {
		return level.getEntities(ModEntities.PROFESSOR, entity -> true).size();
	}

	private static void abort(ServerLevel level, UUID ownerUuid, UUID encounterUuid) {
		if (encounterUuid == null) {
			return;
		}
		CampaignService.apply(
				level,
				new CampaignEvent.Terminal(ownerUuid, encounterUuid, CampaignEvent.TerminalReason.ABORT),
				intent -> {
					if (intent instanceof dev.developershell.campaign.CampaignTransition.EffectIntent.CleanupEncounter cleanup) {
						LectureEncounterManager.cleanup(cleanup.encounterUuid());
					}
				}
		);
		LectureEncounterManager.cleanup(encounterUuid);
	}

	private static ConnectedPlayer createSurvivalPlayer(
			GameTestHelper context,
			ServerLevel level,
			UUID uuid,
			String name
	) {
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
			BlockPos spawn = context.absolutePos(RELATIVE_DESK.offset(0, 0, -2));
			player.snapTo(Vec3.atBottomCenterOf(spawn));
			return new ConnectedPlayer(player, cleanup);
		}
		catch (RuntimeException | Error failure) {
			cleanup.run();
			throw failure;
		}
	}

	private static RecordingServerPlayer detachedPlayer(ServerLevel level, UUID uuid, String name) {
		GameProfile profile = new GameProfile(uuid, name);
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
		RecordingServerPlayer player = new RecordingServerPlayer(
				level.getServer(),
				level,
				cookie.gameProfile(),
				cookie.clientInformation()
		);
		return player;
	}

	private static void close(ConnectedPlayer connection) {
		if (connection != null) {
			connection.close();
		}
	}

	private static String translationKey(Component component) {
		if (component == null || !(component.getContents() instanceof TranslatableContents translated)) {
			return "";
		}
		return translated.getKey();
	}

	private static UUID owner(int suffix) {
		return UUID.fromString("c0de0000-0000-4000-8000-%012d".formatted(suffix));
	}

	private record SurfaceSnapshot(
			int contractCount,
			Optional<PlayerCampaignState> campaignState,
			int professorCount,
			int runtimeCount,
			long presentationCount,
			Map<BlockPos, BlockState> blocks,
			ServerPlayer.RespawnConfig playerRespawn,
			LevelData.RespawnData worldRespawn
	) {
		private SurfaceSnapshot {
			campaignState = Objects.requireNonNull(campaignState, "campaignState");
			blocks = Map.copyOf(blocks);
			Objects.requireNonNull(worldRespawn, "worldRespawn");
		}
	}

	private static final class WorldEdits {
		private final ServerLevel level;
		private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();

		private WorldEdits(ServerLevel level) {
			this.level = Objects.requireNonNull(level, "level");
		}

		private void set(BlockPos pos, BlockState state) {
			BlockPos immutable = pos.immutable();
			originals.putIfAbsent(immutable, level.getBlockState(immutable));
			level.setBlock(immutable, state, 3);
		}

		private Map<BlockPos, BlockState> currentSnapshot() {
			Map<BlockPos, BlockState> snapshot = new LinkedHashMap<>();
			for (BlockPos pos : originals.keySet()) {
				snapshot.put(pos, level.getBlockState(pos));
			}
			return Map.copyOf(snapshot);
		}

		private void restore() {
			List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(originals.entrySet());
			for (int index = entries.size() - 1; index >= 0; index--) {
				Map.Entry<BlockPos, BlockState> entry = entries.get(index);
				level.setBlock(entry.getKey(), entry.getValue(), 3);
			}
			originals.clear();
		}
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
		}

		private void clearRecordedSystemMessages() {
			recordedSystemMessages.clear();
		}

		private List<String> recordedSystemMessageKeys() {
			return recordedSystemMessages.stream().map(ContractArenaGameTests::translationKey).toList();
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
