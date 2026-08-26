package dev.developershell.gametest;

import com.mojang.authlib.GameProfile;
import dev.developershell.campaign.CampaignSavedData;
import dev.developershell.campaign.CampaignService;
import dev.developershell.campaign.CampaignTransition;
import dev.developershell.campaign.PlayerCampaignState;
import dev.developershell.item.AttendanceSheetItem;
import dev.developershell.lecture.LectureEncounterManager;
import dev.developershell.lecture.LectureGeometry;
import dev.developershell.lecture.LectureStateMachine;
import dev.developershell.registry.ModEntities;
import dev.developershell.registry.ModItemIds;
import dev.developershell.registry.ModItems;
import dev.developershell.server.CampaignLifecycle;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Physical first-reward and recoverable Attendance Sheet acceptance tests. */
public final class RewardGameTests implements CustomTestMethodInvoker {
	private static final Direction FACING = Direction.SOUTH;
	private static final BlockPos RELATIVE_DESK = new BlockPos(12, 2, 4);
	private static final String VICTORY_KEY = "message.developers_hell.reward.victory";
	private static final String RECOVERED_KEY = "message.developers_hell.attendance_sheet.recovered";
	private static final String ALREADY_KEY = "message.developers_hell.attendance_sheet.already";
	private static final String NOTHING_KEY = "message.developers_hell.attendance_sheet.nothing";
	private static final String TOOLTIP_KEY = "tooltip.developers_hell.attendance_sheet.proof";

	@GameTest(maxTicks = 160, padding = 24)
	public void realEncounterVictoryReconcilesFirstReward(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(owner(1701), desk, level.getGameTime());
		ConnectedPlayer connection = null;
		try {
			buildArena(level, desk, FACING);
			connection = createSurvivalPlayer(context, ownerUuid, "reward-real-path");
			RecordingServerPlayer owner = connection.player();

			context.assertTrue(ModItems.ATTENDANCE_SHEET instanceof AttendanceSheetItem,
					"the stable Sheet registry entry uses the owner-bound item type");
			context.assertTrue(BuiltInRegistries.ITEM.getValue(ModItemIds.ATTENDANCE_SHEET.identifier())
					== ModItems.ATTENDANCE_SHEET, "the stable Sheet key resolves to the production item");
			List<Component> tooltip = new ArrayList<>();
			ModItems.ATTENDANCE_SHEET.appendHoverText(
					new ItemStack(ModItems.ATTENDANCE_SHEET),
					Item.TooltipContext.of(level),
					TooltipDisplay.DEFAULT,
					tooltip::add,
					TooltipFlag.NORMAL
			);
			context.assertValueEqual(tooltip.stream().map(RewardGameTests::translationKey).toList(),
					List.of(TOOLTIP_KEY), "the Sheet exposes one localized proof tooltip");

			VictoryResult result = completeRealEncounter(context, owner, desk, FACING);
			PlayerCampaignState passed = result.passed();
			context.assertValueEqual(passed.status(), PlayerCampaignState.LectureStatus.PASSED,
					"the real manager path commits PASSED");
			context.assertTrue(passed.sheetEntitled(), "the Sheet entitlement is durable before projection");
			context.assertTrue(passed.remoteIssued(), "the Remote ledger is durable before projection");
			context.assertValueEqual(countBoundSheets(owner, ownerUuid, passed.sheetRecoverySequence()), 1,
					"the accepted victory grants one owner-bound Sheet");
			context.assertValueEqual(countItem(owner, ModItems.INFINITE_SLIDES_REMOTE), 1,
					"the accepted victory grants one Remote");
			context.assertValueEqual(owner.recordedSystemMessageKeys(), List.of(VICTORY_KEY),
					"one accepted victory emits one truthful result message");
			context.assertValueEqual(LectureEncounterManager.activeRuntimeCount(), 0,
					"accepted victory removes the runtime");
			context.assertFalse(LectureEncounterManager.presentation(result.encounterUuid()).isPresent(),
					"accepted victory removes presentation");
			context.assertTrue(result.professor().isRemoved(), "accepted victory removes the Professor");

			owner.clearRecordedSystemMessages();
			context.assertFalse(LectureEncounterManager.onProfessorDamage(result.professor()),
					"a stale manager damage callback cannot reconcile again");
			List<CampaignTransition.EffectIntent> replayEffects = new ArrayList<>();
			CampaignTransition replay = CampaignService.commitVictory(
					level, ownerUuid, result.encounterUuid(), replayEffects::add);
			context.assertFalse(replay.accepted(), "a replayed persisted victory is rejected");
			context.assertTrue(replayEffects.isEmpty(), "a replayed victory dispatches no reward intent");
			context.assertFalse(CampaignLifecycle.onAbort(owner), "a late terminal callback loses the race");
			context.assertValueEqual(countBoundSheets(owner, ownerUuid, passed.sheetRecoverySequence()), 1,
					"all stale callbacks leave exactly one Sheet");
			context.assertValueEqual(countItem(owner, ModItems.INFINITE_SLIDES_REMOTE), 1,
					"all stale callbacks leave exactly one Remote");
			context.assertTrue(owner.recordedSystemMessageKeys().isEmpty(),
					"stale callbacks emit no player-visible result");
			context.succeed();
		}
		finally {
			removeRewardEntities(level.getServer(), ownerUuid, desk.relative(FACING.getOpposite(), 2));
			close(connection);
			clearArena(level, desk, FACING);
		}
	}

	@GameTest(maxTicks = 180, padding = 24)
	public void fullInventoryVictoryUsesOwnerTargetedFallbackWithoutReplay(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		BlockPos retry = desk.relative(FACING.getOpposite(), 2);
		UUID ownerUuid = invocationOwnerUuid(owner(1702), desk, level.getGameTime());
		UUID intruderUuid = invocationOwnerUuid(owner(1703), desk, level.getGameTime());
		ConnectedPlayer ownerConnection = null;
		ConnectedPlayer intruderConnection = null;
		try {
			buildArena(level, desk, FACING);
			ownerConnection = createSurvivalPlayer(context, ownerUuid, "reward-full-owner");
			RecordingServerPlayer owner = ownerConnection.player();
			fillInventory(owner);

			VictoryResult result = completeRealEncounter(context, owner, desk, FACING);
			context.assertValueEqual(countItem(owner, ModItems.ATTENDANCE_SHEET), 0,
					"full inventory receives no hidden Sheet stack");
			context.assertValueEqual(countItem(owner, ModItems.INFINITE_SLIDES_REMOTE), 0,
					"full inventory receives no hidden Remote stack");
			List<ItemEntity> sheetFallbacks = boundSheetEntities(
					level.getServer(), ownerUuid, result.passed().sheetRecoverySequence());
			List<ItemEntity> remoteFallbacks = rewardEntitiesNear(
					level.getServer(), ModItems.INFINITE_SLIDES_REMOTE, retry);
			context.assertValueEqual(sheetFallbacks.size(), 1,
					"full inventory creates one owner-bound Sheet fallback");
			context.assertValueEqual(remoteFallbacks.size(), 1,
					"full inventory creates one Remote fallback");
			context.assertValueEqual(AttendanceSheetItem.binding(sheetFallbacks.getFirst().getItem()).orElseThrow(),
					new AttendanceSheetItem.Binding(ownerUuid, result.passed().sheetRecoverySequence()),
					"Sheet fallback carries the durable owner and recovery sequence");

			intruderConnection = createSurvivalPlayer(context, intruderUuid, "reward-full-intruder");
			RecordingServerPlayer intruder = intruderConnection.player();
			sheetFallbacks.getFirst().playerTouch(intruder);
			remoteFallbacks.getFirst().playerTouch(intruder);
			context.assertFalse(sheetFallbacks.getFirst().isRemoved(),
					"another player cannot pick up the owner-targeted Sheet fallback");
			context.assertFalse(remoteFallbacks.getFirst().isRemoved(),
					"another player cannot pick up the owner-targeted Remote fallback");
			context.assertValueEqual(countItem(intruder, ModItems.ATTENDANCE_SHEET), 0,
					"wrong owner receives no Sheet");
			context.assertValueEqual(countItem(intruder, ModItems.INFINITE_SLIDES_REMOTE), 0,
					"wrong owner receives no Remote");

			owner.clearRecordedSystemMessages();
			context.assertFalse(LectureEncounterManager.onProfessorDamage(result.professor()),
					"full-inventory replay is stale at the manager boundary");
			context.assertValueEqual(boundSheetEntities(
					level.getServer(), ownerUuid, result.passed().sheetRecoverySequence()).size(), 1,
					"replay creates no second Sheet fallback");
			context.assertValueEqual(rewardEntitiesNear(
					level.getServer(), ModItems.INFINITE_SLIDES_REMOTE, retry).size(), 1,
					"replay creates no second Remote fallback");
			context.assertTrue(owner.recordedSystemMessageKeys().isEmpty(),
					"fallback replay emits no second result");
			context.succeed();
		}
		finally {
			removeRewardEntities(level.getServer(), ownerUuid, retry);
			close(intruderConnection);
			close(ownerConnection);
			clearArena(level, desk, FACING);
		}
	}

	@GameTest(maxTicks = 200, padding = 24)
	public void matchingDeskRecoversOnlyMissingSheetWithoutRewardReplay(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		BlockPos wrongDesk = desk.relative(FACING.getClockWise(), 3);
		UUID ownerUuid = invocationOwnerUuid(owner(1704), desk, level.getGameTime());
		UUID intruderUuid = invocationOwnerUuid(owner(1705), desk, level.getGameTime());
		ConnectedPlayer ownerConnection = null;
		ConnectedPlayer intruderConnection = null;
		try {
			buildArena(level, desk, FACING);
			level.setBlock(wrongDesk.below(), Blocks.STONE.defaultBlockState(), 3);
			level.setBlock(wrongDesk, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, FACING), 3);
			ownerConnection = createSurvivalPlayer(context, ownerUuid, "sheet-recovery-owner");
			RecordingServerPlayer owner = ownerConnection.player();
			VictoryResult result = completeRealEncounter(context, owner, desk, FACING);
			PlayerCampaignState passed = result.passed();
			moveBoundSheetOffSelected(owner, ownerUuid, passed.sheetRecoverySequence());

			owner.clearRecordedSystemMessages();
			context.assertValueEqual(useEmptyHand(level, owner, desk), InteractionResult.SUCCESS_SERVER,
					"matching Desk handles an existing Sheet representation");
			context.assertValueEqual(owner.recordedSystemMessageKeys(), List.of(ALREADY_KEY),
					"existing Sheet emits only the localized already-present fact");
			context.assertValueEqual(state(level, ownerUuid), passed,
					"existing Sheet recovery changes no durable state");

			removeBoundSheets(level.getServer(), ownerUuid);
			owner.clearRecordedSystemMessages();
			context.assertValueEqual(useEmptyHand(level, owner, wrongDesk), InteractionResult.SUCCESS_SERVER,
					"wrong Desk is a localized recovery no-op");
			context.assertValueEqual(owner.recordedSystemMessageKeys(), List.of(NOTHING_KEY),
					"wrong Desk reports no recoverable Sheet");
			context.assertValueEqual(state(level, ownerUuid), passed,
					"wrong Desk cannot advance the recovery sequence");

			intruderConnection = createSurvivalPlayer(context, intruderUuid, "sheet-recovery-intruder");
			RecordingServerPlayer intruder = intruderConnection.player();
			context.assertValueEqual(useEmptyHand(level, intruder, desk), InteractionResult.PASS,
					"a player without this owner record cannot enter Sheet recovery");
			context.assertValueEqual(countItem(intruder, ModItems.ATTENDANCE_SHEET), 0,
					"wrong owner receives no Sheet");

			owner.clearRecordedSystemMessages();
			context.assertValueEqual(useEmptyHand(level, owner, desk), InteractionResult.SUCCESS_SERVER,
					"matching owner and Desk recover the missing Sheet");
			PlayerCampaignState recovered = state(level, ownerUuid);
			context.assertValueEqual(recovered.sheetRecoverySequence(), passed.sheetRecoverySequence() + 1L,
					"accepted recovery advances exactly one replay marker");
			assertRecoveryPreservesCampaign(context, passed, recovered);
			context.assertValueEqual(countBoundSheets(owner, ownerUuid, recovered.sheetRecoverySequence()), 1,
					"accepted recovery creates one current owner-bound Sheet");
			context.assertValueEqual(countItem(owner, ModItems.INFINITE_SLIDES_REMOTE), 1,
					"Sheet recovery never issues another Remote");
			context.assertValueEqual(owner.recordedSystemMessageKeys(), List.of(RECOVERED_KEY),
					"accepted recovery emits its truthful localized result once");

			moveBoundSheetOffSelected(owner, ownerUuid, recovered.sheetRecoverySequence());
			owner.clearRecordedSystemMessages();
			context.assertValueEqual(useEmptyHand(level, owner, desk), InteractionResult.SUCCESS_SERVER,
					"replayed recovery observes the current Sheet");
			context.assertValueEqual(state(level, ownerUuid), recovered,
					"replayed recovery changes no state");
			context.assertValueEqual(countBoundSheets(owner, ownerUuid, recovered.sheetRecoverySequence()), 1,
					"replayed recovery cannot duplicate the Sheet");
			context.assertValueEqual(owner.recordedSystemMessageKeys(), List.of(ALREADY_KEY),
					"replayed recovery emits no restored claim");
			context.succeed();
		}
		finally {
			removeBoundSheets(level.getServer(), ownerUuid);
			close(intruderConnection);
			close(ownerConnection);
			level.setBlock(wrongDesk, Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(wrongDesk.below(), Blocks.AIR.defaultBlockState(), 3);
			clearArena(level, desk, FACING);
		}
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void directDeathAndCompatibilityVictoryCannotBypassManager(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(owner(1706), desk, level.getGameTime());
		ConnectedPlayer connection = null;
		try {
			buildArena(level, desk, FACING);
			connection = createSurvivalPlayer(context, ownerUuid, "reward-direct-path");
			RecordingServerPlayer owner = connection.player();
			PlayerCampaignState active = startAttempt(context, owner, desk, FACING);
			ModEntities.ProfessorEntity professor = LectureEncounterManager.professor(active.encounterUuid())
					.orElseThrow(() -> context.assertionException("missing direct-path Professor"));
			owner.clearRecordedSystemMessages();

			professor.die(owner.damageSources().playerAttack(owner));
			context.assertValueEqual(state(level, ownerUuid), active,
					"direct entity death cannot commit campaign victory");
			context.assertFalse(professor.isRemoved(),
					"direct death leaves the live manager to decide the terminal outcome");
			context.assertFalse(CampaignService.victory(level, ownerUuid, active.encounterUuid()),
					"the legacy compatibility wrapper is a safe false-returning no-op");
			context.assertValueEqual(state(level, ownerUuid), active,
					"the legacy wrapper cannot persist PASSED");
			context.assertValueEqual(countItem(owner, ModItems.ATTENDANCE_SHEET), 0,
					"no direct path grants a Sheet");
			context.assertValueEqual(countItem(owner, ModItems.INFINITE_SLIDES_REMOTE), 0,
					"no direct path grants a Remote");
			context.assertTrue(owner.recordedSystemMessageKeys().isEmpty(),
					"no direct path presents a victory result");

			context.assertTrue(CampaignLifecycle.onAbort(owner), "the first real terminal wins the race");
			List<CampaignTransition.EffectIntent> lateEffects = new ArrayList<>();
			context.assertFalse(CampaignService.commitVictory(
					level, ownerUuid, active.encounterUuid(), lateEffects::add).accepted(),
					"victory after terminal cleanup is stale");
			context.assertTrue(lateEffects.isEmpty(), "the losing victory race emits no reward intent");
			context.assertValueEqual(countItem(owner, ModItems.ATTENDANCE_SHEET), 0,
					"the terminal race grants no Sheet");
			context.assertValueEqual(countItem(owner, ModItems.INFINITE_SLIDES_REMOTE), 0,
					"the terminal race grants no Remote");
			context.succeed();
		}
		finally {
			close(connection);
			clearArena(level, desk, FACING);
		}
	}

	private static VictoryResult completeRealEncounter(
			GameTestHelper context,
			RecordingServerPlayer owner,
			BlockPos desk,
			Direction facing
	) {
		PlayerCampaignState active = startAttempt(context, owner, desk, facing);
		UUID encounterUuid = active.encounterUuid();
		ModEntities.ProfessorEntity professor = LectureEncounterManager.professor(encounterUuid)
				.orElseThrow(() -> context.assertionException("missing reward-path Professor"));

		LectureStateMachine.State slide = combatState(context, encounterUuid);
		placeAtLocal(owner, desk, facing, 9, laneCenter(slide.safeLane()));
		tick(owner.level(), encounterUuid, slide.deadlineTick());
		tick(owner.level(), encounterUuid, slide.deadlineTick() + 1L);
		context.assertTrue(professor.hurtServer(
				owner.level(), owner.damageSources().playerAttack(owner), professor.getMaxHealth()),
				"real Act 1 owner hit is accepted");
		LectureStateMachine.State slideRecovery = combatState(context, encounterUuid);
		context.assertValueEqual(slideRecovery.bossHealth(), 80, "real Act 1 preserves the 80-health floor");
		context.assertValueEqual(slideRecovery.stage(), LectureStateMachine.Stage.RECOVERY,
				"the manager consumes Act 1 damage immediately");
		tick(owner.level(), encounterUuid, slideRecovery.deadlineTick());

		LectureStateMachine.State quiz = combatState(context, encounterUuid);
		placeAtLocal(owner, desk, facing, 9, quiz.correctPad().rightAnchor());
		tick(owner.level(), encounterUuid, quiz.deadlineTick());
		tick(owner.level(), encounterUuid, quiz.deadlineTick() + 1L);
		context.assertTrue(professor.hurtServer(
				owner.level(), owner.damageSources().playerAttack(owner), professor.getMaxHealth()),
				"real Act 2 owner hit is accepted");
		LectureStateMachine.State quizRecovery = combatState(context, encounterUuid);
		context.assertValueEqual(quizRecovery.bossHealth(), 40, "real Act 2 preserves the 40-health floor");
		context.assertValueEqual(quizRecovery.stage(), LectureStateMachine.Stage.RECOVERY,
				"the manager consumes Act 2 damage immediately");
		tick(owner.level(), encounterUuid, quizRecovery.deadlineTick());

		LectureStateMachine.State attendance = combatState(context, encounterUuid);
		LectureGeometry.LocalPosition ring = LectureGeometry.attendanceCenter(attendance.attendanceQuadrant());
		placeAtLocal(owner, desk, facing, (int) ring.forwardOffset(), (int) ring.rightOffset());
		tick(owner.level(), encounterUuid, attendance.deadlineTick());
		tick(owner.level(), encounterUuid, attendance.deadlineTick() + 1L);
		context.assertTrue(LectureEncounterManager.isVulnerabilityOpen(encounterUuid),
				"real Attendance success opens the final accepted window");
		owner.clearRecordedSystemMessages();
		context.assertTrue(professor.hurtServer(
				owner.level(), owner.damageSources().playerAttack(owner), professor.getMaxHealth()),
				"real final owner hit enters the manager-owned victory seam");
		PlayerCampaignState passed = state(owner.level(), owner.getUUID());
		return new VictoryResult(active, passed, encounterUuid, professor);
	}

	private static PlayerCampaignState startAttempt(
			GameTestHelper context,
			ServerPlayer owner,
			BlockPos desk,
			Direction facing
	) {
		ItemStack contract = new ItemStack(ModItems.CURSED_UNPAID_INTERNSHIP_CONTRACT);
		context.assertTrue(CampaignService.start(owner, desk, facing, contract),
				"reward fixture starts through the production Contract transaction");
		context.assertTrue(contract.isEmpty(), "accepted reward fixture consumes its Contract last");
		return state(owner.level(), owner.getUUID());
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

	private static int countBoundSheets(ServerPlayer player, UUID ownerUuid, long sequence) {
		int count = 0;
		AttendanceSheetItem.Binding expected = new AttendanceSheetItem.Binding(ownerUuid, sequence);
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (AttendanceSheetItem.binding(stack).filter(expected::equals).isPresent()) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static List<ItemEntity> boundSheetEntities(MinecraftServer server, UUID ownerUuid, long sequence) {
		AttendanceSheetItem.Binding expected = new AttendanceSheetItem.Binding(ownerUuid, sequence);
		List<ItemEntity> result = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity item
						&& !item.isRemoved()
						&& AttendanceSheetItem.binding(item.getItem()).filter(expected::equals).isPresent()) {
					result.add(item);
				}
			}
		}
		return result;
	}

	private static List<ItemEntity> rewardEntitiesNear(MinecraftServer server, Item itemType, BlockPos retry) {
		List<ItemEntity> result = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity item
						&& !item.isRemoved()
						&& item.getItem().getItem() == itemType
						&& item.blockPosition().distManhattan(retry) <= 2) {
					result.add(item);
				}
			}
		}
		return result;
	}

	private static void moveBoundSheetOffSelected(ServerPlayer player, UUID ownerUuid, long sequence) {
		AttendanceSheetItem.Binding expected = new AttendanceSheetItem.Binding(ownerUuid, sequence);
		int selected = player.getInventory().getSelectedSlot();
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (AttendanceSheetItem.binding(stack).filter(expected::equals).isEmpty()) {
				continue;
			}
			if (slot != selected) {
				player.getInventory().setItem(selected, ItemStack.EMPTY);
				return;
			}
			for (int target = 0; target < player.getInventory().getContainerSize(); target++) {
				if (target != selected && player.getInventory().getItem(target).isEmpty()) {
					player.getInventory().setItem(target, stack);
					player.getInventory().setItem(selected, ItemStack.EMPTY);
					return;
				}
			}
		}
		throw new IllegalStateException("missing movable owner-bound Attendance Sheet");
	}

	private static void removeBoundSheets(MinecraftServer server, UUID ownerUuid) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				if (AttendanceSheetItem.binding(player.getInventory().getItem(slot))
						.map(AttendanceSheetItem.Binding::ownerUuid).filter(ownerUuid::equals).isPresent()) {
					player.getInventory().setItem(slot, ItemStack.EMPTY);
				}
			}
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity item
						&& AttendanceSheetItem.binding(item.getItem())
						.map(AttendanceSheetItem.Binding::ownerUuid).filter(ownerUuid::equals).isPresent()) {
					item.discard();
				}
			}
		}
	}

	private static void removeRewardEntities(MinecraftServer server, UUID ownerUuid, BlockPos retry) {
		removeBoundSheets(server, ownerUuid);
		for (ItemEntity item : rewardEntitiesNear(server, ModItems.INFINITE_SLIDES_REMOTE, retry)) {
			item.discard();
		}
	}

	private static void fillInventory(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
		}
	}

	private static InteractionResult useEmptyHand(ServerLevel level, ServerPlayer player, BlockPos target) {
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
		return UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
	}

	private static void assertRecoveryPreservesCampaign(
			GameTestHelper context,
			PlayerCampaignState before,
			PlayerCampaignState after
	) {
		context.assertValueEqual(after.ownerUuid(), before.ownerUuid(), "recovery owner");
		context.assertValueEqual(after.chapter(), before.chapter(), "recovery chapter");
		context.assertValueEqual(after.status(), before.status(), "recovery status");
		context.assertValueEqual(after.attemptCount(), before.attemptCount(), "recovery attempt count");
		context.assertValueEqual(after.deskDimension(), before.deskDimension(), "recovery desk dimension");
		context.assertValueEqual(after.deskPos(), before.deskPos(), "recovery desk position");
		context.assertValueEqual(after.deskFacing(), before.deskFacing(), "recovery desk facing");
		context.assertValueEqual(after.retryPos(), before.retryPos(), "recovery retry position");
		context.assertTrue(Objects.equals(after.activeEncounterRef(), before.activeEncounterRef()),
				"recovery active encounter");
		context.assertValueEqual(after.sheetEntitled(), before.sheetEntitled(), "recovery Sheet entitlement");
		context.assertValueEqual(after.remoteIssued(), before.remoteIssued(), "recovery Remote ledger");
		context.assertValueEqual(after.retakeEntitled(), before.retakeEntitled(), "recovery Retake entitlement");
		context.assertTrue(Objects.equals(after.retakeEncounterUuid(), before.retakeEncounterUuid()),
				"recovery Retake identity");
		context.assertTrue(Objects.equals(
				after.retakeFallbackReservationUuid(), before.retakeFallbackReservationUuid()),
				"recovery Retake reservation");
		context.assertTrue(Objects.equals(after.retakeFallbackEntityUuid(), before.retakeFallbackEntityUuid()),
				"recovery Retake fallback");
		context.assertValueEqual(after.remoteCooldownUntilGameTime(), before.remoteCooldownUntilGameTime(),
				"recovery Remote cooldown");
		context.assertValueEqual(after.remoteReadyNoticeForDeadlineGameTime(),
				before.remoteReadyNoticeForDeadlineGameTime(), "recovery Remote ready notice");
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
			return recordedSystemMessages.stream().map(RewardGameTests::translationKey).toList();
		}
	}

	private record ConnectedPlayer(RecordingServerPlayer player, Runnable cleanup) implements AutoCloseable {
		@Override
		public void close() {
			cleanup.run();
		}
	}

	private record VictoryResult(
			PlayerCampaignState active,
			PlayerCampaignState passed,
			UUID encounterUuid,
			ModEntities.ProfessorEntity professor
	) {
	}

	@Override
	public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
		method.invoke(this, context);
	}
}
