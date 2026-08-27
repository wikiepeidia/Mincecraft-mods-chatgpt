package dev.developershell.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.developershell.campaign.CampaignSavedData;
import dev.developershell.campaign.CampaignService;
import dev.developershell.campaign.PlayerCampaignState;
import dev.developershell.lecture.LectureEncounterManager;
import dev.developershell.lecture.LectureGeometry;
import dev.developershell.lecture.RetakeService;
import dev.developershell.item.RetakeFormItem;
import dev.developershell.registry.ModItemIds;
import dev.developershell.registry.ModItems;
import dev.developershell.server.CampaignLifecycle;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Physical Retake projection and recovery acceptance tests. */
public final class RetakeGameTests implements CustomTestMethodInvoker {
	private static final Direction FACING = Direction.SOUTH;
	private static final BlockPos RELATIVE_DESK = new BlockPos(12, 2, 4);
	private static final String ISSUED_KEY = "message.developers_hell.retake.issued";
	private static final String FALLBACK_KEY = "message.developers_hell.retake.fallback";
	private static final String RECOVERED_KEY = "message.developers_hell.retake.recovered";
	private static final String ALREADY_KEY = "message.developers_hell.retake.already";
	private static final String NOTHING_KEY = "message.developers_hell.retake.nothing";

	@GameTest(maxTicks = 100, padding = 24)
	public void cleanupIssuesOneBoundInventoryFormAndReplayIssuesNothing(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(owner(801), desk, level.getGameTime());
		ConnectedPlayer connection = null;
		try {
			buildArena(level, desk, FACING);
			connection = createSurvivalPlayer(context, ownerUuid, "retake-inventory");
			RecordingServerPlayer owner = connection.player();
			PlayerCampaignState active = startAttempt(context, owner, desk, FACING);
			owner.clearRecordedSystemMessages();

			context.assertTrue(CampaignLifecycle.onAbort(owner), "matching cleanup is accepted");
			PlayerCampaignState ready = state(level, ownerUuid);
			PlayerCampaignState.RetakeKey key = ready.retakeKey().orElseThrow();
			context.assertValueEqual(boundFormCount(owner, key), 1,
					"accepted cleanup materializes exactly one owner-bound inventory Form");
			context.assertValueEqual(ready.status(), PlayerCampaignState.LectureStatus.RETAKE_READY,
					"cleanup leaves Retake-ready authority");
			context.assertValueEqual(ready.attemptCount(), active.attemptCount(),
					"physical issuance does not change progression");
			context.assertTrue(owner.recordedSystemMessageKeys().contains(ISSUED_KEY),
					"inventory issuance uses localized copy");

			owner.clearRecordedSystemMessages();
			context.assertFalse(CampaignLifecycle.onAbort(owner), "duplicate cleanup is rejected");
			context.assertValueEqual(boundFormCount(owner, key), 1,
					"duplicate cleanup cannot issue a second Form");
			context.assertFalse(owner.recordedSystemMessageKeys().contains(ISSUED_KEY),
					"duplicate cleanup emits no second issuance message");
			context.succeed();
		}
		finally {
			cleanupOwner(level, ownerUuid);
			close(connection);
			clearArena(level, desk, FACING);
		}
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void fullInventoryFallbackLossAndRecoveryStayExactlyOne(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(owner(802), desk, level.getGameTime());
		UUID intruderUuid = invocationOwnerUuid(owner(804), desk, level.getGameTime());
		ConnectedPlayer connection = null;
		ConnectedPlayer intruderConnection = null;
		try {
			buildArena(level, desk, FACING);
			connection = createSurvivalPlayer(context, ownerUuid, "retake-fallback");
			RecordingServerPlayer owner = connection.player();
			startAttempt(context, owner, desk, FACING);
			fillInventory(owner);
			owner.clearRecordedSystemMessages();

			context.assertTrue(CampaignLifecycle.onAbort(owner), "full-inventory cleanup is accepted");
			PlayerCampaignState ready = state(level, ownerUuid);
			PlayerCampaignState.RetakeKey key = ready.retakeKey().orElseThrow();
			UUID fallbackUuid = ready.retakeFallbackEntityUuid();
			context.assertTrue(fallbackUuid != null, "full inventory commits one materialized fallback UUID");
			context.assertTrue(ready.retakeFallbackReservationUuid() == null,
					"materialized fallback clears its reservation");
			ItemEntity fallback = fallback(level, fallbackUuid, context);
			context.assertValueEqual(RetakeService.formKey(fallback.getItem()).orElseThrow(), key,
					"fallback Form is bound to the exact owner and failed encounter");
			context.assertValueEqual(fallback.getUUID(), fallbackUuid,
					"tracked fallback UUID is the materialized entity UUID");
			context.assertValueEqual(boundFormCount(owner, key), 0,
					"full inventory has no concurrent inventory Form");
			context.assertTrue(owner.recordedSystemMessageKeys().contains(FALLBACK_KEY),
					"fallback placement uses localized inventory-full copy");
			intruderConnection = createSurvivalPlayer(context, intruderUuid, "retake-intruder");
			RecordingServerPlayer intruder = intruderConnection.player();
			fallback.playerTouch(intruder);
			context.assertFalse(fallback.isRemoved(), "owner-targeted fallback rejects another player pickup");
			context.assertValueEqual(boundFormCount(intruder, key), 0,
					"another player cannot acquire the owner-bound fallback Form");
			context.assertValueEqual(state(level, ownerUuid), ready,
					"wrong-player pickup changes no owner authority");

			ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(fallback, level);
			PlayerCampaignState lost = state(level, ownerUuid);
			context.assertTrue(lost.retakeFallbackEntityUuid() == null,
					"tracked unload clears the physical reference exactly once");
			ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(fallback, level);
			context.assertValueEqual(state(level, ownerUuid), lost,
					"duplicate unload is a durable no-op");
			ItemEntity staleReload = new ItemEntity(level, fallback.getX(), fallback.getY(), fallback.getZ(),
					fallback.getItem().copy());
			staleReload.setUUID(fallbackUuid);
			context.assertFalse(ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(
					staleReload, level, EntitySpawnReason.LOAD, true
			), "cleared fallback cannot resurrect from a stale chunk copy");
			fallback.discard();

			owner.getInventory().setItem(owner.getInventory().getSelectedSlot(), ItemStack.EMPTY);
			owner.clearRecordedSystemMessages();
			InteractionResult recovered = useBlock(level, owner, desk);
			context.assertValueEqual(recovered, InteractionResult.SUCCESS_SERVER,
					"empty-hand matching Desk handles recovery on the logical server");
			context.assertValueEqual(boundFormCount(owner, key), 1,
					"loss recovery creates one replacement Form");
			context.assertTrue(state(level, ownerUuid).retakeFallbackEntityUuid() == null,
					"inventory recovery has no concurrent fallback");
			context.assertValueEqual(owner.recordedSystemMessageKeys(), List.of(RECOVERED_KEY),
					"manual inventory recovery emits exact localized copy once");
			context.succeed();
		}
		finally {
			cleanupOwner(level, ownerUuid);
			close(intruderConnection);
			close(connection);
			clearArena(level, desk, FACING);
		}
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void emptyHandRecoveryRequiresMatchingDeskMissingProjectionAndReadyState(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		BlockPos wrongDesk = desk.relative(FACING.getClockWise(), 3);
		UUID ownerUuid = invocationOwnerUuid(owner(803), desk, level.getGameTime());
		ConnectedPlayer connection = null;
		try {
			buildArena(level, desk, FACING);
			level.setBlock(wrongDesk.below(), Blocks.STONE.defaultBlockState(), 3);
			level.setBlock(wrongDesk, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, FACING), 3);
			connection = createSurvivalPlayer(context, ownerUuid, "retake-desk");
			RecordingServerPlayer owner = connection.player();
			startAttempt(context, owner, desk, FACING);
			context.assertTrue(CampaignLifecycle.onAbort(owner), "setup cleanup is accepted");
			PlayerCampaignState.RetakeKey key = state(level, ownerUuid).retakeKey().orElseThrow();
			moveBoundFormOffHand(owner, key);

			owner.clearRecordedSystemMessages();
			context.assertValueEqual(useBlock(level, owner, desk), InteractionResult.SUCCESS_SERVER,
					"matching Desk handles an already-represented entitlement");
			context.assertValueEqual(owner.recordedSystemMessageKeys(), List.of(ALREADY_KEY),
					"existing Form suppresses another grant with exact copy");
			context.assertValueEqual(boundFormCount(owner, key), 1, "already-present recovery stays exactly one");

			removeBoundForms(owner, key);
			owner.clearRecordedSystemMessages();
			context.assertValueEqual(useBlock(level, owner, wrongDesk), InteractionResult.PASS,
					"empty-hand recovery passes a nonmatching lectern through to vanilla");
			context.assertTrue(owner.recordedSystemMessageKeys().isEmpty(),
					"empty-hand recovery emits no copy for a nonmatching lectern");
			context.assertValueEqual(boundFormCount(owner, key), 0, "wrong Desk does not recover");

			owner.clearRecordedSystemMessages();
			context.assertValueEqual(useBlock(level, owner, desk), InteractionResult.SUCCESS_SERVER,
					"matching missing projection recovers");
			context.assertValueEqual(boundFormCount(owner, key), 1, "matching Desk recovers exactly one");
			context.assertValueEqual(owner.recordedSystemMessageKeys(), List.of(RECOVERED_KEY),
					"matching recovery emits exact copy");
			context.succeed();
		}
		finally {
			cleanupOwner(level, ownerUuid);
			close(connection);
			level.setBlock(wrongDesk, Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(wrongDesk.below(), Blocks.AIR.defaultBlockState(), 3);
			clearArena(level, desk, FACING);
		}
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void productionRetakeItemRevalidatesCommitsAndConsumesExactlyOne(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(owner(805), desk, level.getGameTime());
		ConnectedPlayer connection = null;
		try {
			buildArena(level, desk, FACING);
			connection = createSurvivalPlayer(context, ownerUuid, "retake-success");
			RecordingServerPlayer owner = connection.player();
			startAttempt(context, owner, desk, FACING);
			context.assertTrue(CampaignLifecycle.onAbort(owner), "setup cleanup is accepted");
			PlayerCampaignState ready = state(level, ownerUuid);
			PlayerCampaignState.RetakeKey key = ready.retakeKey().orElseThrow();
			ItemStack form = moveBoundFormToHand(owner, key);

			context.assertTrue(ModItems.RETAKE_FORM instanceof RetakeFormItem,
					"the production registry exposes the custom Retake item");
			context.assertTrue(BuiltInRegistries.ITEM.getValue(ModItemIds.RETAKE_FORM.identifier())
					== ModItems.RETAKE_FORM, "the stable registry key resolves to the same production instance");
			context.assertValueEqual(RetakeService.formKey(form).orElseThrow(), key,
					"the production stack carries the exact durable Retake key");

			InteractionResult result = useHeldBlock(level, owner, desk);
			context.assertValueEqual(result, InteractionResult.SUCCESS_SERVER,
					"matching production Retake use succeeds on the logical server");
			PlayerCampaignState active = state(level, ownerUuid);
			context.assertValueEqual(active.status(), PlayerCampaignState.LectureStatus.ACTIVE,
					"retry commits ACTIVE state");
			context.assertValueEqual(active.attemptCount(), ready.attemptCount() + 1,
					"retry commits one new attempt");
			context.assertTrue(active.activeEncounterRef() != null,
					"retry commits one new encounter identity");
			context.assertFalse(active.retakeEntitled(), "accepted retry clears the old entitlement");
			context.assertValueEqual(form.getCount(), 0, "accepted retry consumes exactly one Form last");
			context.assertValueEqual(boundFormCount(owner, key), 0,
					"accepted retry leaves no old physical representation");
			context.assertTrue(LectureEncounterManager.runtimeSnapshot(active.encounterUuid()).isPresent(),
					"runtime starts only for the persisted encounter");
			context.succeed();
		}
		finally {
			cleanupOwner(level, ownerUuid);
			close(connection);
			clearArena(level, desk, FACING);
		}
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void wrongOwnerDeskStateAndChangedArenaAreAtomicNoOps(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		BlockPos wrongDesk = desk.relative(FACING.getClockWise(), 3);
		UUID ownerUuid = invocationOwnerUuid(owner(806), desk, level.getGameTime());
		UUID intruderUuid = invocationOwnerUuid(owner(807), desk, level.getGameTime());
		ConnectedPlayer ownerConnection = null;
		ConnectedPlayer intruderConnection = null;
		try {
			buildArena(level, desk, FACING);
			level.setBlock(wrongDesk.below(), Blocks.STONE.defaultBlockState(), 3);
			level.setBlock(wrongDesk, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, FACING), 3);
			ownerConnection = createSurvivalPlayer(context, ownerUuid, "retake-owner");
			intruderConnection = createSurvivalPlayer(context, intruderUuid, "retake-wrong-owner");
			RecordingServerPlayer owner = ownerConnection.player();
			RecordingServerPlayer intruder = intruderConnection.player();
			startAttempt(context, owner, desk, FACING);
			context.assertTrue(CampaignLifecycle.onAbort(owner), "setup cleanup is accepted");
			PlayerCampaignState ready = state(level, ownerUuid);
			PlayerCampaignState.RetakeKey key = ready.retakeKey().orElseThrow();
			ItemStack form = moveBoundFormToHand(owner, key);
			int runtimeCount = LectureEncounterManager.activeRuntimeCount();

			ItemStack stolenCopy = form.copy();
			intruder.setItemInHand(InteractionHand.MAIN_HAND, stolenCopy);
			intruder.clearRecordedSystemMessages();
			context.assertValueEqual(useHeldBlock(level, intruder, desk), InteractionResult.SUCCESS_SERVER,
					"wrong owner is handled as a localized no-op");
			context.assertValueEqual(stolenCopy.getCount(), 1, "wrong owner cannot consume the Form");
			context.assertValueEqual(state(level, ownerUuid), ready, "wrong owner cannot clear owner state");
			context.assertValueEqual(intruder.recordedSystemMessageKeys(), List.of(NOTHING_KEY),
					"wrong owner receives nothing-to-retake copy");

			owner.clearRecordedSystemMessages();
			context.assertValueEqual(useHeldBlock(level, owner, wrongDesk), InteractionResult.SUCCESS_SERVER,
					"wrong Desk is handled as a localized no-op");
			context.assertValueEqual(form.getCount(), 1, "wrong Desk cannot consume the Form");
			context.assertValueEqual(state(level, ownerUuid), ready, "wrong Desk cannot change state");
			context.assertValueEqual(owner.recordedSystemMessageKeys(), List.of(NOTHING_KEY),
					"wrong Desk receives nothing-to-retake copy");

			BlockPos changedFloor = LectureGeometry.layout(desk, FACING).floorAt(5, 0);
			level.setBlock(changedFloor, Blocks.AIR.defaultBlockState(), 3);
			owner.clearRecordedSystemMessages();
			context.assertValueEqual(useHeldBlock(level, owner, desk), InteractionResult.FAIL,
					"changed arena fails complete validation");
			context.assertValueEqual(form.getCount(), 1, "changed arena cannot consume the Form");
			context.assertValueEqual(state(level, ownerUuid), ready, "changed arena cannot change state");
			context.assertValueEqual(owner.recordedSystemMessageKeys(),
					List.of("message.developers_hell.contract.rejected.floor"),
					"changed arena reports the exact actionable validation reason");
			level.setBlock(changedFloor, Blocks.STONE.defaultBlockState(), 3);

			context.assertValueEqual(useHeldBlock(level, owner, desk), InteractionResult.SUCCESS_SERVER,
					"restored arena accepts the retry");
			PlayerCampaignState active = state(level, ownerUuid);
			ItemStack staleForm = RetakeService.boundForm(key);
			owner.setItemInHand(InteractionHand.MAIN_HAND, staleForm);
			owner.clearRecordedSystemMessages();
			context.assertValueEqual(useHeldBlock(level, owner, desk), InteractionResult.SUCCESS_SERVER,
					"active-state replay is a localized no-op");
			context.assertValueEqual(state(level, ownerUuid), active, "active-state replay changes no state");
			context.assertValueEqual(staleForm.getCount(), 1, "active-state replay consumes nothing");
			context.assertValueEqual(owner.recordedSystemMessageKeys(), List.of(NOTHING_KEY),
					"active-state replay receives nothing-to-retake copy");
			context.assertValueEqual(LectureEncounterManager.activeRuntimeCount(), runtimeCount + 1,
					"all rejections spawn no additional runtime");
			context.succeed();
		}
		finally {
			cleanupOwner(level, ownerUuid);
			close(intruderConnection);
			close(ownerConnection);
			level.setBlock(wrongDesk, Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(wrongDesk.below(), Blocks.AIR.defaultBlockState(), 3);
			clearArena(level, desk, FACING);
		}
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void abortAndRecoverCommandsRequireGameMasterAndUseSharedServices(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(owner(808), desk, level.getGameTime());
		ConnectedPlayer connection = null;
		try {
			buildArena(level, desk, FACING);
			connection = createSurvivalPlayer(context, ownerUuid, "retake-command");
			RecordingServerPlayer owner = connection.player();
			PlayerCampaignState active = startAttempt(context, owner, desk, FACING);

			assertCommandDenied(context, () -> level.getServer().getCommands().getDispatcher()
					.execute("devhell abort", owner.createCommandSourceStack()
							.withPermission(PermissionSet.NO_PERMISSIONS)), "unprivileged abort");
			context.assertValueEqual(state(level, ownerUuid), active,
					"unprivileged abort cannot reach the lifecycle service");

			int aborted = level.getServer().getCommands().getDispatcher().execute(
					"devhell abort",
					owner.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS)
			);
			context.assertValueEqual(aborted, 1, "game-master abort reaches shared lifecycle service");
			PlayerCampaignState ready = state(level, ownerUuid);
			PlayerCampaignState.RetakeKey key = ready.retakeKey().orElseThrow();
			context.assertValueEqual(boundFormCount(owner, key), 1,
					"shared abort reconciliation issues one production Form");

			removeBoundForms(owner, key);
			assertCommandDenied(context, () -> level.getServer().getCommands().getDispatcher()
					.execute("devhell recover retake", owner.createCommandSourceStack()
							.withPermission(PermissionSet.NO_PERMISSIONS)), "unprivileged recovery");
			context.assertValueEqual(boundFormCount(owner, key), 0,
					"unprivileged recovery cannot materialize a Form");

			int recovered = level.getServer().getCommands().getDispatcher().execute(
					"devhell recover retake",
					owner.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS)
			);
			context.assertValueEqual(recovered, 1, "game-master recovery reaches shared Retake service");
			context.assertValueEqual(boundFormCount(owner, key), 1,
					"shared recovery restores exactly one bound Form");
			context.succeed();
		}
		catch (CommandSyntaxException exception) {
			throw context.assertionException("authorized Retake command failed: %s", exception.getMessage());
		}
		finally {
			cleanupOwner(level, ownerUuid);
			close(connection);
			clearArena(level, desk, FACING);
		}
	}

	private static PlayerCampaignState startAttempt(
			GameTestHelper context,
			ServerPlayer owner,
			BlockPos desk,
			Direction facing
	) {
		ItemStack contract = new ItemStack(ModItems.CURSED_UNPAID_INTERNSHIP_CONTRACT);
		context.assertTrue(CampaignService.start(owner, desk, facing, contract),
				"Retake fixture starts through the production Contract transaction");
		context.assertTrue(contract.isEmpty(), "accepted setup consumes its Contract last");
		return state(owner.level(), owner.getUUID());
	}

	private static PlayerCampaignState state(ServerLevel level, UUID ownerUuid) {
		return CampaignSavedData.get(level).player(ownerUuid).orElseThrow();
	}

	private static ItemEntity fallback(ServerLevel level, UUID uuid, GameTestHelper context) {
		if (level.getEntityInAnyDimension(uuid) instanceof ItemEntity item) {
			return item;
		}
		throw context.assertionException("missing tracked Retake fallback %s", uuid);
	}

	private static int boundFormCount(ServerPlayer player, PlayerCampaignState.RetakeKey key) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (RetakeService.formKey(stack).filter(key::equals).isPresent()) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static void moveBoundFormOffHand(ServerPlayer player, PlayerCampaignState.RetakeKey key) {
		int selected = player.getInventory().getSelectedSlot();
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (RetakeService.formKey(stack).filter(key::equals).isPresent()) {
				player.getInventory().setItem(slot, ItemStack.EMPTY);
				int target = selected == 5 ? 6 : 5;
				player.getInventory().setItem(target, stack);
				player.getInventory().setItem(selected, ItemStack.EMPTY);
				return;
			}
		}
		throw new IllegalStateException("missing bound Retake Form");
	}

	private static ItemStack moveBoundFormToHand(
			ServerPlayer player,
			PlayerCampaignState.RetakeKey key
	) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (RetakeService.formKey(stack).filter(key::equals).isPresent()) {
				ItemStack moved = player.getInventory().removeItemNoUpdate(slot);
				player.setItemInHand(InteractionHand.MAIN_HAND, moved);
				return moved;
			}
		}
		throw new IllegalStateException("missing bound Retake Form");
	}

	private static void removeBoundForms(ServerPlayer player, PlayerCampaignState.RetakeKey key) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (RetakeService.formKey(player.getInventory().getItem(slot)).filter(key::equals).isPresent()) {
				player.getInventory().setItem(slot, ItemStack.EMPTY);
			}
		}
	}

	private static void fillInventory(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
		}
	}

	private static InteractionResult useBlock(ServerLevel level, ServerPlayer player, BlockPos target) {
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		return useHeldBlock(level, player, target);
	}

	private static InteractionResult useHeldBlock(ServerLevel level, ServerPlayer player, BlockPos target) {
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
		return UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
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

	private static void cleanupOwner(ServerLevel level, UUID ownerUuid) {
		for (LectureEncounterManager.RuntimeSnapshot runtime
				: LectureEncounterManager.activeRuntimeSnapshots(level.getServer())) {
			if (runtime.ownerUuid().equals(ownerUuid)) {
				LectureEncounterManager.cleanup(runtime.encounterUuid());
			}
		}
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
				new net.minecraft.world.phys.AABB(-30_000_000, level.getMinY(), -30_000_000,
						30_000_000, level.getMaxY(), 30_000_000), entity ->
				RetakeService.formKey(entity.getItem())
						.map(PlayerCampaignState.RetakeKey::ownerUuid)
						.filter(ownerUuid::equals)
						.isPresent())) {
			item.discard();
		}
	}

	private static ConnectedPlayer createSurvivalPlayer(
			GameTestHelper context,
			UUID uuid,
			String name
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
			BlockPos spawn = context.absolutePos(RELATIVE_DESK.relative(FACING.getOpposite(), 2));
			player.snapTo(Vec3.atBottomCenterOf(spawn));
			return new ConnectedPlayer(player, cleanup);
		}
		catch (RuntimeException | Error failure) {
			cleanup.run();
			throw failure;
		}
	}

	private static void close(ConnectedPlayer connection) {
		if (connection != null) {
			connection.close();
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

	private static void assertCommandDenied(
			GameTestHelper context,
			ThrowingCommand command,
			String description
	) {
		try {
			command.run();
		}
		catch (CommandSyntaxException expected) {
			return;
		}
		throw context.assertionException("%s must be rejected by the permission predicate", description);
	}

	@FunctionalInterface
	private interface ThrowingCommand {
		void run() throws CommandSyntaxException;
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
			return recordedSystemMessages.stream().map(RetakeGameTests::translationKey).toList();
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
