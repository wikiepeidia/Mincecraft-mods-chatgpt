package dev.developershell.gametest;

import com.mojang.authlib.GameProfile;
import dev.developershell.campaign.CampaignSavedData;
import dev.developershell.campaign.CampaignService;
import dev.developershell.DevelopersHell;
import dev.developershell.lecture.LectureEncounterManager;
import dev.developershell.registry.ModEntities;
import dev.developershell.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class FoundationGameTests implements CustomTestMethodInvoker {
	private static final UUID OWNER_UUID = UUID.fromString("c0de0000-0000-4000-8000-000000000201");
	private static final UUID COMPETING_OWNER_UUID = UUID.fromString("c0de0000-0000-4000-8000-000000000202");
	private static final long TRACER_SEED = 0x02_01_5L;

	@GameTest
	public void foundationTokenIsRegistered(GameTestHelper context) {
		Identifier actual = BuiltInRegistries.ITEM.getKey(ModItems.FOUNDATION_TOKEN);
		Identifier expected = DevelopersHell.id("foundation_token");
		context.assertValueEqual(actual, expected, "foundation token registry key");
		context.succeed();
	}

	@GameTest(maxTicks = 260, padding = 24)
	public void contractStartsSlideWindowAndCommitsFirstReward(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(new BlockPos(12, 2, 4));
		Direction facing = Direction.SOUTH;
		buildArena(level, desk, facing);
		List<BlockState> before = snapshotArena(level, desk, facing);
		ConnectedPlayer ownerConnection = null;
		ConnectedPlayer competitorConnection = null;

		try {
			ownerConnection = createSurvivalPlayer(
					context,
					OWNER_UUID,
					"tracer-owner",
					new BlockPos(12, 2, 2)
			);
			ServerPlayer owner = ownerConnection.player();
			ItemStack ownerContracts = new ItemStack(ModItems.CURSED_UNPAID_INTERNSHIP_CONTRACT, 2);
			owner.setItemInHand(InteractionHand.MAIN_HAND, ownerContracts);

			InteractionResult firstStart = useDesk(level, owner, desk);
			context.assertValueEqual(firstStart, InteractionResult.SUCCESS_SERVER, "first Contract start result");
			context.assertValueEqual(ownerContracts.getCount(), 1, "accepted start consumes exactly one Contract");

			CampaignSavedData data = CampaignSavedData.get(level);
			CampaignSavedData.PlayerProgress active = data.player(OWNER_UUID)
					.orElseThrow(() -> context.assertionException("missing active owner; seed=%s", TRACER_SEED));
			context.assertValueEqual(active.status(), CampaignSavedData.LectureStatus.ACTIVE, "accepted start status");
			context.assertValueEqual(active.attemptCount(), 1, "accepted start attempt count");
			context.assertValueEqual(active.deskPos(), desk, "persisted Internship Desk");
			context.assertTrue(active.retryPos() != null, "one persisted retry record; seed=" + TRACER_SEED);
			context.assertTrue(data.isDirty(), "START must mark SavedData dirty before materialized effects");
			context.assertValueEqual(LectureEncounterManager.activeRuntimeCount(), 1, "one runtime after start");
			context.assertValueEqual(
					level.getEntities(ModEntities.PROFESSOR, professor -> professor.ownerUuid().equals(OWNER_UUID)).size(),
					1,
					"one owner-scoped Professor"
			);

			InteractionResult duplicateStart = useDesk(level, owner, desk);
			context.assertFalse(duplicateStart == InteractionResult.SUCCESS_SERVER, "duplicate Contract start must be rejected");
			context.assertValueEqual(ownerContracts.getCount(), 1, "duplicate start consumes nothing");

			competitorConnection = createSurvivalPlayer(
					context,
					COMPETING_OWNER_UUID,
					"tracer-competitor",
					new BlockPos(11, 2, 2)
			);
			ServerPlayer competitor = competitorConnection.player();
			ItemStack competingContract = new ItemStack(ModItems.CURSED_UNPAID_INTERNSHIP_CONTRACT);
			competitor.setItemInHand(InteractionHand.MAIN_HAND, competingContract);
			InteractionResult competingStart = useDesk(level, competitor, desk);
			context.assertFalse(competingStart == InteractionResult.SUCCESS_SERVER, "same-desk competing start must be rejected");
			context.assertValueEqual(competingContract.getCount(), 1, "competing start consumes nothing");
			context.assertFalse(data.player(COMPETING_OWNER_UUID).isPresent(), "competing start writes no player state");
			context.assertValueEqual(LectureEncounterManager.activeRuntimeCount(), 1, "duplicate starts retain one runtime");
			context.assertValueEqual(snapshotArena(level, desk, facing), before, "Contract start preserves arena blocks");

			UUID encounterUuid = active.encounterUuid();
			UUID professorUuid = active.professorUuid();
			ConnectedPlayer finalOwnerConnection = ownerConnection;
			ConnectedPlayer finalCompetitorConnection = competitorConnection;
			context.runBeforeTestEnd(() -> LectureEncounterManager.cleanup(encounterUuid));
			context.runBeforeTestEnd(() -> clearArena(level, desk, facing));
			context.runAfterDelay(LectureEncounterManager.SLIDE_DECK_TELEGRAPH_TICKS + 1L, () -> {
				try {
					context.assertTrue(
							LectureEncounterManager.isVulnerabilityOpen(encounterUuid),
							"five-second Slide Deck cue must open the owner window; seed=" + TRACER_SEED
					);
					ModEntities.ProfessorEntity professor = LectureEncounterManager.professor(encounterUuid)
							.orElseThrow(() -> context.assertionException("missing Professor %s", professorUuid));
					float healthBeforeWrongOwner = professor.getHealth();
					boolean wrongOwnerDamage = professor.hurtServer(
							level,
							competitor.damageSources().playerAttack(competitor),
							10.0F
					);
					context.assertFalse(wrongOwnerDamage, "non-owner damage must be rejected");
					context.assertValueEqual(professor.getHealth(), healthBeforeWrongOwner, "non-owner damage changes no health");

					int sheetBefore = countItem(owner, ModItems.ATTENDANCE_SHEET);
					int remoteBefore = countItem(owner, ModItems.INFINITE_SLIDES_REMOTE);
					boolean ownerDamage = professor.hurtServer(
							level,
							owner.damageSources().playerAttack(owner),
							professor.getMaxHealth() + 1.0F
					);
					context.assertTrue(ownerDamage, "owner damage during the open window must be accepted");

					CampaignSavedData.PlayerProgress passed = data.player(OWNER_UUID)
							.orElseThrow(() -> context.assertionException("missing passed owner state"));
					context.assertValueEqual(passed.status(), CampaignSavedData.LectureStatus.PASSED, "matching victory status");
					context.assertTrue(passed.sheetEntitled(), "Attendance Sheet entitlement committed");
					context.assertTrue(passed.remoteIssued(), "first Remote ledger committed");
					context.assertTrue(passed.encounterUuid() == null, "victory clears active encounter UUID");
					context.assertValueEqual(countItem(owner, ModItems.ATTENDANCE_SHEET), sheetBefore + 1, "first Sheet grant");
					context.assertValueEqual(countItem(owner, ModItems.INFINITE_SLIDES_REMOTE), remoteBefore + 1, "first Remote grant");
					context.assertValueEqual(LectureEncounterManager.activeRuntimeCount(), 0, "victory removes runtime and boss bar");
					context.assertValueEqual(level.getEntities(ModEntities.PROFESSOR, entity -> true).size(), 0, "victory removes Professor");

					context.assertFalse(
							CampaignService.victory(level, OWNER_UUID, encounterUuid),
							"replayed matching victory must be a no-op"
					);
					context.assertFalse(
							CampaignService.victory(level, COMPETING_OWNER_UUID, encounterUuid),
							"wrong-owner victory must be a no-op"
					);
					context.assertFalse(
							CampaignService.victory(level, OWNER_UUID, UUID.fromString("c0de0000-0000-4000-8000-000000000299")),
							"stale encounter victory must be a no-op"
					);
					context.assertValueEqual(countItem(owner, ModItems.ATTENDANCE_SHEET), sheetBefore + 1, "replay grants no Sheet");
					context.assertValueEqual(countItem(owner, ModItems.INFINITE_SLIDES_REMOTE), remoteBefore + 1, "replay grants no Remote");
					context.assertValueEqual(snapshotArena(level, desk, facing), before, "victory preserves arena blocks");
					context.succeed();
				}
				finally {
					LectureEncounterManager.cleanup(encounterUuid);
					clearArena(level, desk, facing);
					finalCompetitorConnection.close();
					finalOwnerConnection.close();
				}
			});
		}
		catch (RuntimeException | Error failure) {
			if (competitorConnection != null) {
				competitorConnection.close();
			}
			if (ownerConnection != null) {
				ownerConnection.close();
			}
			clearArena(level, desk, facing);
			throw failure;
		}
	}

	private static InteractionResult useDesk(ServerLevel level, ServerPlayer player, BlockPos desk) {
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(desk), Direction.UP, desk, false);
		return UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
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
		ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
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

	private static int countItem(ServerPlayer player, Item item) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.getItem() == item) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private record ConnectedPlayer(ServerPlayer player, Runnable cleanup) implements AutoCloseable {
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
