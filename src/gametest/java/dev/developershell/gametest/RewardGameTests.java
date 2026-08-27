package dev.developershell.gametest;

import com.mojang.authlib.GameProfile;
import dev.developershell.DevelopersHell;
import dev.developershell.campaign.CampaignEvent;
import dev.developershell.campaign.CampaignSavedData;
import dev.developershell.campaign.CampaignService;
import dev.developershell.campaign.CampaignServiceGameTestAccess;
import dev.developershell.campaign.CampaignTransition;
import dev.developershell.campaign.PlayerCampaignState;
import dev.developershell.item.AttendanceSheetItem;
import dev.developershell.item.InfiniteSlidesRemoteItem;
import dev.developershell.lecture.LectureEncounterManager;
import dev.developershell.lecture.LectureGeometry;
import dev.developershell.lecture.LectureStateMachine;
import dev.developershell.lecture.RewardService;
import dev.developershell.lecture.RewardServiceGameTestAccess;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Physical first-reward and recoverable Attendance Sheet acceptance tests. */
public final class RewardGameTests implements CustomTestMethodInvoker {
	private static final Direction FACING = Direction.SOUTH;
	private static final BlockPos RELATIVE_DESK = new BlockPos(12, 2, 4);
	private static final String VICTORY_KEY = "message.developers_hell.reward.victory";
	private static final String RECOVERED_KEY = "message.developers_hell.attendance_sheet.recovered";
	private static final String ALREADY_KEY = "message.developers_hell.attendance_sheet.already";
	private static final String TOOLTIP_KEY = "tooltip.developers_hell.attendance_sheet.proof";

	@GameTest(maxTicks = 100, padding = 24)
	public void legacySchemaOneDeliveredRemoteAdoptsAtMostOneOwnerHeldStack(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(owner(1691), desk, level.getGameTime());
		ConnectedPlayer connection = null;
		try {
			connection = createSurvivalPlayer(context, ownerUuid, "reward-legacy-delivered");
			RecordingServerPlayer owner = connection.player();
			PlayerCampaignState migrated = legacyRemoteMigrationState(ownerUuid, desk, FACING, 3);
			CampaignServiceGameTestAccess.replaceState(level, migrated);
			owner.getInventory().setItem(0, new ItemStack(ModItems.INFINITE_SLIDES_REMOTE));
			owner.getInventory().setItem(1, new ItemStack(ModItems.INFINITE_SLIDES_REMOTE));
			ItemStack partialBinding = InfiniteSlidesRemoteItem.bound(
					new InfiniteSlidesRemoteItem.Binding(ownerUuid, UUID.randomUUID()));
			CustomData.update(DataComponents.CUSTOM_DATA, partialBinding,
					tag -> tag.remove("developers_hell_remote_projection"));
			owner.getInventory().setItem(2, partialBinding);

			context.assertValueEqual(
					RewardService.reconcilePending(owner),
					RewardService.Outcome.ALREADY_PRESENT,
					"one owner-held schema-1 Remote is adopted before confirmation"
			);
			PlayerCampaignState resolved = state(level, ownerUuid);
			context.assertFalse(resolved.remoteProjectionPending(),
					"adopted legacy Remote confirms the durable projection");
			context.assertFalse(resolved.legacyRemoteAdoptionPending(),
					"adoption resolution cannot replay");
			context.assertValueEqual(
					countBoundRemotes(owner, ownerUuid, resolved.remoteProjectionUuid()),
					1,
					"at most one owner-held legacy Remote becomes authoritative"
			);
			context.assertTrue(InfiniteSlidesRemoteItem.isUnboundLegacy(owner.getInventory().getItem(1)),
					"the second legacy Remote remains inert and unbound");
			context.assertFalse(InfiniteSlidesRemoteItem.isUnboundLegacy(owner.getInventory().getItem(2)),
					"a partial private binding fails closed instead of being adopted");
			context.assertValueEqual(RewardService.reconcilePending(owner), RewardService.Outcome.ALREADY_PRESENT,
					"reconciliation replay binds and issues nothing else");
			context.assertValueEqual(countItem(owner, ModItems.INFINITE_SLIDES_REMOTE), 3,
					"migration neither deletes nor duplicates old physical stacks");
			context.succeed();
		}
		finally {
			removeBoundRewards(level.getServer(), ownerUuid);
			close(connection);
		}
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void legacySchemaOneFailedRemotePersistsPendingThenRecoversOnce(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(owner(1692), desk, level.getGameTime());
		ConnectedPlayer connection = null;
		try {
			connection = createSurvivalPlayer(context, ownerUuid, "reward-legacy-failed");
			RecordingServerPlayer owner = connection.player();
			PlayerCampaignState migrated = legacyRemoteMigrationState(ownerUuid, desk, FACING, 4);
			CampaignServiceGameTestAccess.replaceState(level, migrated);

			context.assertValueEqual(
					RewardServiceGameTestAccess.reconcilePending(
							owner,
							stack -> stack.getItem() == ModItems.INFINITE_SLIDES_REMOTE
					),
					RewardService.Outcome.MATERIALIZATION_FAILED,
					"absence is persisted before a failed replacement attempt"
			);
			PlayerCampaignState pending = state(level, ownerUuid);
			context.assertTrue(pending.remoteProjectionPending(),
					"old failed delivery remains durably retryable");
			context.assertFalse(pending.legacyRemoteAdoptionPending(),
					"known absence transitions to ordinary pending before materialization");
			context.assertValueEqual(pending.remoteProjectionUuid(), migrated.remoteProjectionUuid(),
					"migration never changes the reserved projection identity");
			context.assertValueEqual(countItem(owner, ModItems.INFINITE_SLIDES_REMOTE), 0,
					"failed retry creates no hidden Remote");

			context.assertValueEqual(RewardService.reconcilePending(owner), RewardService.Outcome.INVENTORY_ISSUED,
					"a later reconciliation recovers the old failed grant");
			PlayerCampaignState complete = state(level, ownerUuid);
			context.assertFalse(complete.remoteProjectionPending(), "successful retry confirms once");
			context.assertValueEqual(
					countBoundRemotes(owner, ownerUuid, complete.remoteProjectionUuid()),
					1,
					"old failed grant produces exactly one bound Remote"
			);
			context.assertValueEqual(RewardService.reconcilePending(owner), RewardService.Outcome.ALREADY_PRESENT,
					"completed migration cannot reissue");
			context.assertValueEqual(countItem(owner, ModItems.INFINITE_SLIDES_REMOTE), 1,
					"replay preserves exactly one Remote");
			context.succeed();
		}
		finally {
			removeBoundRewards(level.getServer(), ownerUuid);
			close(connection);
		}
	}

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
			context.assertFalse(passed.sheetProjectionPending(),
					"the visible Sheet confirms its independent projection");
			context.assertFalse(passed.remoteProjectionPending(),
					"the visible Remote confirms its independent projection");
			context.assertValueEqual(countBoundSheets(owner, ownerUuid, passed.sheetRecoverySequence()), 1,
					"the accepted victory grants one owner-bound Sheet");
			context.assertValueEqual(countBoundRemotes(owner, ownerUuid, passed.remoteProjectionUuid()), 1,
					"the accepted victory grants one exact owner-bound Remote");
			context.assertValueEqual(owner.recordedSystemMessageKeys(), List.of(VICTORY_KEY),
					"one accepted victory emits one truthful result message");
			context.assertValueEqual(LectureEncounterManager.activeRuntimeCount(), 0,
					"accepted victory removes the runtime");
			context.assertFalse(LectureEncounterManager.presentation(result.encounterUuid()).isPresent(),
					"accepted victory removes presentation");
			context.assertTrue(result.professor().isRemoved(), "accepted victory removes the Professor");

			owner.clearRecordedSystemMessages();
			context.assertFalse(LectureEncounterManager.admitProfessorDamage(
					level, result.professor(), ownerUuid, result.professor().getMaxHealth()).accepted(),
					"a stale Professor cannot obtain another manager damage ticket");
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
			removeRewardEntities(level.getServer(), ownerUuid);
			close(connection);
			clearArena(level, desk, FACING);
		}
	}

	@GameTest(maxTicks = 180, padding = 24)
	public void fullInventoryVictoryUsesOwnerTargetedFallbackWithoutReplay(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
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
			List<ItemEntity> remoteFallbacks = boundRemoteEntities(
					level.getServer(), ownerUuid, result.passed().remoteProjectionUuid());
			context.assertValueEqual(sheetFallbacks.size(), 1,
					"full inventory creates one owner-bound Sheet fallback");
			context.assertValueEqual(remoteFallbacks.size(), 1,
					"full inventory creates one Remote fallback");
			PlayerCampaignState.RewardFallbackRef sheetReservation =
					Objects.requireNonNull(result.passed().sheetFallback(), "persisted Sheet fallback");
			PlayerCampaignState.RewardFallbackRef remoteReservation =
					Objects.requireNonNull(result.passed().remoteFallback(), "persisted Remote fallback");
			context.assertTrue(sheetReservation.materialized(),
					"Sheet reservation is durably marked materialized before confirmation returns");
			context.assertTrue(remoteReservation.materialized(),
					"Remote reservation is durably marked materialized before confirmation returns");
			context.assertValueEqual(sheetReservation.entityUuid(), sheetFallbacks.getFirst().getUUID(),
					"persisted Sheet reservation names the exact physical entity");
			context.assertValueEqual(remoteReservation.entityUuid(), remoteFallbacks.getFirst().getUUID(),
					"persisted Remote reservation names the exact physical entity");
			context.assertFalse(sheetReservation.entityUuid().equals(remoteReservation.entityUuid()),
					"the two reward projections never share a fallback UUID");
			context.assertValueEqual(AttendanceSheetItem.binding(sheetFallbacks.getFirst().getItem()).orElseThrow(),
					new AttendanceSheetItem.Binding(ownerUuid, result.passed().sheetRecoverySequence()),
					"Sheet fallback carries the durable owner and recovery sequence");
			context.assertValueEqual(InfiniteSlidesRemoteItem.binding(remoteFallbacks.getFirst().getItem()).orElseThrow(),
					new InfiniteSlidesRemoteItem.Binding(ownerUuid, result.passed().remoteProjectionUuid()),
					"Remote fallback carries its durable owner and projection identity");

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
			context.assertFalse(LectureEncounterManager.admitProfessorDamage(
					level, result.professor(), ownerUuid, result.professor().getMaxHealth()).accepted(),
					"full-inventory replay is stale at the manager boundary");
			context.assertValueEqual(boundSheetEntities(
					level.getServer(), ownerUuid, result.passed().sheetRecoverySequence()).size(), 1,
					"replay creates no second Sheet fallback");
			context.assertValueEqual(boundRemoteEntities(
					level.getServer(), ownerUuid, result.passed().remoteProjectionUuid()).size(), 1,
					"replay creates no second Remote fallback");
			context.assertTrue(owner.recordedSystemMessageKeys().isEmpty(),
					"fallback replay emits no second result");
			context.succeed();
		}
		finally {
			removeRewardEntities(level.getServer(), ownerUuid);
			close(intruderConnection);
			close(ownerConnection);
			clearArena(level, desk, FACING);
		}
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void ownerQDropPreservesConfirmedSheet(GameTestHelper context) {
		assertOwnerLiveDropRoundTrip(context, owner(1716), "reward-q-sheet", false, false, false);
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void ownerQDropPreservesConfirmedRemoteAndRemoteRemainsUsable(GameTestHelper context) {
		assertOwnerLiveDropRoundTrip(context, owner(1717), "reward-q-remote", true, false, false);
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void ownerDeathDropPreservesConfirmedSheet(GameTestHelper context) {
		assertOwnerLiveDropRoundTrip(context, owner(1718), "reward-death-sheet", false, true, false);
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void ownerDeathDropPreservesConfirmedRemoteAndRemoteRemainsUsable(GameTestHelper context) {
		assertOwnerLiveDropRoundTrip(context, owner(1719), "reward-death-remote", true, true, false);
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void earlierAdmissionRejectsQDroppedSheetWithoutLoss(GameTestHelper context) {
		assertOwnerLiveDropRoundTrip(context, owner(1724), "reward-early-q-sheet", false, false, true);
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void earlierAdmissionRejectsQDroppedRemoteWithoutLossAndRemoteRemainsUsable(GameTestHelper context) {
		assertOwnerLiveDropRoundTrip(context, owner(1725), "reward-early-q-remote", true, false, true);
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void earlierAdmissionRejectsDeathDroppedSheetWithoutLoss(GameTestHelper context) {
		assertOwnerLiveDropRoundTrip(context, owner(1726), "reward-early-death-sheet", false, true, true);
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void earlierAdmissionRejectsDeathDroppedRemoteWithoutLossAndRemoteRemainsUsable(GameTestHelper context) {
		assertOwnerLiveDropRoundTrip(context, owner(1727), "reward-early-death-remote", true, true, true);
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void nonSelectedGuiThrowRejectsSheetWithoutLoss(GameTestHelper context) {
		assertAlternativeRewardDropRecovers(
				context, owner(1728), "reward-gui-sheet", false, AlternativeDrop.GUI_SLOT);
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void nonSelectedGuiThrowRejectsRemoteWithoutLossAndRemoteRemainsUsable(GameTestHelper context) {
		assertAlternativeRewardDropRecovers(
				context, owner(1729), "reward-gui-remote", true, AlternativeDrop.GUI_SLOT);
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void carriedCursorCloseRejectsSheetWithoutLoss(GameTestHelper context) {
		assertAlternativeRewardDropRecovers(
				context, owner(1730), "reward-cursor-sheet", false, AlternativeDrop.CURSOR_CLOSE);
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void carriedCursorCloseRejectsRemoteWithoutLossAndRemoteRemainsUsable(GameTestHelper context) {
		assertAlternativeRewardDropRecovers(
				context, owner(1731), "reward-cursor-remote", true, AlternativeDrop.CURSOR_CLOSE);
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void containerBreakRejectsSheetWithoutLoss(GameTestHelper context) {
		assertAlternativeRewardDropRecovers(
				context, owner(1732), "reward-container-sheet", false, AlternativeDrop.CONTAINER_BREAK);
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void containerBreakRejectsRemoteWithoutLossAndRemoteRemainsUsable(GameTestHelper context) {
		assertAlternativeRewardDropRecovers(
				context, owner(1733), "reward-container-remote", true, AlternativeDrop.CONTAINER_BREAK);
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void sheetPickupClearsFallbackBeforeStaleDiskCopyCanReload(GameTestHelper context) {
		assertPickupClearsUuidAuthority(context, owner(1720), "reward-pickup-sheet", false);
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void remotePickupClearsFallbackBeforeStaleDiskCopyCanReload(GameTestHelper context) {
		assertPickupClearsUuidAuthority(context, owner(1721), "reward-pickup-remote", true);
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void materializedSheetRejectsSameDimensionStalePreRelocationDiskCopy(
			GameTestHelper context
	) {
		assertSameDimensionStaleRelocationRejected(
				context, owner(1722), "reward-relocation-sheet");
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void materializedRemoteDimensionTravelRollsBackRejectedTargetAndRejectsStaleSource(
			GameTestHelper context
	) {
		assertDimensionTravelIsAtomic(
				context, owner(1723), "reward-dimension-remote");
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void rewardFallbackReservationsSurviveTornWriteAndUnloadedChunksWithoutReissue(
			GameTestHelper context
	) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(owner(1707), desk, level.getGameTime());
		ConnectedPlayer connection = null;
		try {
			buildArena(level, desk, FACING);
			connection = createSurvivalPlayer(context, ownerUuid, "reward-unloaded-reservations");
			RecordingServerPlayer owner = connection.player();
			PlayerCampaignState pending = persistPendingVictory(context, owner, desk, FACING);
			fillInventory(owner);
			BlockPos unloaded = pending.retryPos().offset(4_096, 0, 4_096);
			context.assertFalse(level.isLoaded(unloaded),
					"the torn-write fixture starts in an unloaded chunk");
			pending = withRetryPos(pending, unloaded);
			CampaignServiceGameTestAccess.replaceState(level, pending);

			UUID sheetUuid = UUID.randomUUID();
			UUID remoteUuid = UUID.randomUUID();
			persistFallbackReservation(
					context,
					level,
					new CampaignEvent.SheetProjectionKey(ownerUuid, pending.sheetRecoverySequence()),
					sheetUuid,
					unloaded
			);
			PlayerCampaignState reserved = persistFallbackReservation(
					context,
					level,
					new CampaignEvent.RemoteProjectionKey(ownerUuid, pending.remoteProjectionUuid()),
					remoteUuid,
					unloaded
			);
			context.assertValueEqual(reserved.sheetFallback().entityUuid(), sheetUuid,
					"torn Sheet write preserves the exact reserved UUID");
			context.assertValueEqual(reserved.remoteFallback().entityUuid(), remoteUuid,
					"torn Remote write preserves the exact reserved UUID");
			context.assertFalse(reserved.sheetFallback().materialized(),
					"suppressed Sheet effect remains an explicit reservation");
			context.assertFalse(reserved.remoteFallback().materialized(),
					"suppressed Remote effect remains an explicit reservation");

			context.assertValueEqual(RewardService.reconcilePending(owner), RewardService.Outcome.FALLBACK_PENDING,
					"unloaded exact reservations wait without replacement");
			context.assertValueEqual(state(level, ownerUuid), reserved,
					"reconciliation neither clears nor reissues unloaded reservations");
			context.assertFalse(level.isLoaded(unloaded),
					"reconciliation does not force-load the tracked chunk");
			ServerPlayerEvents.JOIN.invoker().onJoin(owner);
			ServerPlayerEvents.JOIN.invoker().onJoin(owner);
			context.assertValueEqual(state(level, ownerUuid), reserved,
					"restart/join replay retains both exact reservations");
			context.assertFalse(level.isLoaded(unloaded),
					"restart reconciliation still does not force-load the chunk");
			context.assertValueEqual(boundSheetEntities(
					level.getServer(), ownerUuid, pending.sheetRecoverySequence()).size(), 0,
					"unloaded Sheet reservation creates no second entity");
			context.assertValueEqual(boundRemoteEntities(
					level.getServer(), ownerUuid, pending.remoteProjectionUuid()).size(), 0,
					"unloaded Remote reservation creates no second entity");
			context.succeed();
		}
		finally {
			removeBoundRewards(level.getServer(), ownerUuid);
			close(connection);
			clearArena(level, desk, FACING);
		}
	}

	@GameTest(maxTicks = 120, padding = 24)
	public void rewardFallbackDiskLoadRequiresExactTrackedUuidAndConvergesSynchronously(
			GameTestHelper context
	) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(owner(1708), desk, level.getGameTime());
		ConnectedPlayer connection = null;
		try {
			buildArena(level, desk, FACING);
			connection = createSurvivalPlayer(context, ownerUuid, "reward-disk-fallbacks");
			RecordingServerPlayer owner = connection.player();
			PlayerCampaignState pending = persistPendingVictory(context, owner, desk, FACING);
			fillInventory(owner);
			BlockPos fallbackPos = pending.retryPos();
			context.assertTrue(level.isLoaded(fallbackPos),
					"disk fixture uses a loaded chunk so missing means destructively absent");

			UUID sheetUuid = UUID.randomUUID();
			UUID oldRemoteUuid = UUID.randomUUID();
			persistFallbackReservation(
					context,
					level,
					new CampaignEvent.SheetProjectionKey(ownerUuid, pending.sheetRecoverySequence()),
					sheetUuid,
					fallbackPos
			);
			persistFallbackReservation(
					context,
					level,
					new CampaignEvent.RemoteProjectionKey(ownerUuid, pending.remoteProjectionUuid()),
					oldRemoteUuid,
					fallbackPos
			);

			AttendanceSheetItem.Binding sheetBinding =
					new AttendanceSheetItem.Binding(ownerUuid, pending.sheetRecoverySequence());
			InfiniteSlidesRemoteItem.Binding remoteBinding =
					new InfiniteSlidesRemoteItem.Binding(ownerUuid, pending.remoteProjectionUuid());
			ItemEntity sheetDisk = diskRoundTripItem(level,
					fallbackItem(level, fallbackPos, AttendanceSheetItem.bound(sheetBinding), sheetUuid));
			ItemEntity remoteDisk = diskRoundTripItem(level,
					fallbackItem(level, fallbackPos, InfiniteSlidesRemoteItem.bound(remoteBinding), oldRemoteUuid));
			context.assertValueEqual(sheetDisk.getUUID(), sheetUuid,
					"Sheet entity UUID survives its Minecraft save/load path");
			context.assertValueEqual(remoteDisk.getUUID(), oldRemoteUuid,
					"Remote entity UUID survives its Minecraft save/load path");
			context.assertTrue(ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(
					sheetDisk, level, EntitySpawnReason.LOAD, true),
					"the exact tracked Sheet disk entity is admitted");
			context.assertTrue(ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(
					remoteDisk, level, EntitySpawnReason.LOAD, true),
					"the exact tracked Remote disk entity is admitted");

			ItemEntity untrackedSheet = fallbackItem(
					level, fallbackPos, AttendanceSheetItem.bound(sheetBinding), UUID.randomUUID());
			ItemEntity untrackedRemote = fallbackItem(
					level, fallbackPos, InfiniteSlidesRemoteItem.bound(remoteBinding), UUID.randomUUID());
			context.assertFalse(ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(
					untrackedSheet, level, EntitySpawnReason.LOAD, true),
					"a same-binding Sheet with an untracked UUID is rejected");
			context.assertFalse(ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(
					untrackedRemote, level, EntitySpawnReason.LOAD, true),
					"a same-binding Remote with an untracked UUID is rejected");

			context.assertTrue(level.addFreshEntity(sheetDisk),
					"the exact Sheet enters the synchronous entity-load callback");
			PlayerCampaignState afterSheetLoad = state(level, ownerUuid);
			context.assertFalse(afterSheetLoad.sheetProjectionPending(),
					"synchronous Sheet load confirms only after its exact representation exists");
			context.assertTrue(afterSheetLoad.sheetFallback().materialized(),
					"synchronous Sheet load marks the reservation materialized");

			context.assertValueEqual(RewardService.reconcilePending(owner), RewardService.Outcome.FALLBACK_ISSUED,
					"loaded-and-missing Remote clears its old UUID before creating a replacement");
			PlayerCampaignState converged = state(level, ownerUuid);
			PlayerCampaignState.RewardFallbackRef newRemote =
					Objects.requireNonNull(converged.remoteFallback(), "replacement Remote fallback");
			context.assertFalse(converged.remoteProjectionPending(),
					"replacement Remote synchronously materializes and confirms");
			context.assertTrue(newRemote.materialized(),
					"replacement Remote leaves a durable materialized reference");
			context.assertFalse(newRemote.entityUuid().equals(oldRemoteUuid),
					"destructive recovery never reuses the cleared UUID");
			List<ItemEntity> remoteFallbacks = boundRemoteEntities(
					level.getServer(), ownerUuid, pending.remoteProjectionUuid());
			context.assertValueEqual(remoteFallbacks.size(), 1,
					"loaded-missing recovery creates exactly one Remote entity");
			context.assertValueEqual(remoteFallbacks.getFirst().getUUID(), newRemote.entityUuid(),
					"the replacement state names the exact new Remote entity");
			context.assertFalse(ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(
					remoteDisk, level, EntitySpawnReason.LOAD, true),
					"the cleared old Remote UUID cannot reauthorize on a later disk load");

			BlockPos relocated = fallbackPos.relative(Direction.EAST);
			sheetDisk.snapTo(Vec3.atCenterOf(relocated));
			sheetDisk.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
			ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(sheetDisk, level);
			PlayerCampaignState afterRelocate = state(level, ownerUuid);
			context.assertValueEqual(afterRelocate.sheetFallback().entityUuid(), sheetUuid,
					"non-destructive Sheet unload retains the exact UUID");
			context.assertValueEqual(afterRelocate.sheetFallback().position(), relocated,
					"non-destructive Sheet unload records its relocation");
			context.assertTrue(afterRelocate.sheetFallback().materialized(),
					"non-destructive Sheet unload does not reopen materialization");

			ItemEntity replacementRemote = remoteFallbacks.getFirst();
			replacementRemote.setRemoved(Entity.RemovalReason.KILLED);
			ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(replacementRemote, level);
			context.assertTrue(state(level, ownerUuid).remoteFallback() == null,
					"destructive Remote unload clears its exact physical reference");
			ItemEntity staleReplacement = fallbackItem(
					level, fallbackPos, InfiniteSlidesRemoteItem.bound(remoteBinding), newRemote.entityUuid());
			context.assertFalse(ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(
					staleReplacement, level, EntitySpawnReason.LOAD, true),
					"a destructively cleared replacement cannot resurrect from disk");
			context.succeed();
		}
		finally {
			removeRewardEntities(level.getServer(), ownerUuid);
			close(connection);
			clearArena(level, desk, FACING);
		}
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void sheetOnlyRepresentationReconcilesRemoteIndependently(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(owner(1710), desk, level.getGameTime());
		ConnectedPlayer connection = null;
		try {
			buildArena(level, desk, FACING);
			connection = createSurvivalPlayer(context, ownerUuid, "reward-sheet-only");
			RecordingServerPlayer owner = connection.player();
			PlayerCampaignState pending = persistPendingVictory(context, owner, desk, FACING);
			owner.getInventory().setItem(0, AttendanceSheetItem.bound(
					new AttendanceSheetItem.Binding(ownerUuid, pending.sheetRecoverySequence())));
			owner.getInventory().setItem(1, new ItemStack(ModItems.INFINITE_SLIDES_REMOTE));
			owner.getInventory().setItem(2, InfiniteSlidesRemoteItem.bound(
					new InfiniteSlidesRemoteItem.Binding(owner(9710), UUID.randomUUID())));

			context.assertValueEqual(RewardService.reconcilePending(owner),
					RewardService.Outcome.INVENTORY_ISSUED,
					"an observed Sheet does not suppress the pending Remote");
			PlayerCampaignState complete = state(level, ownerUuid);
			context.assertFalse(complete.sheetProjectionPending(), "the observed exact Sheet confirms once");
			context.assertFalse(complete.remoteProjectionPending(), "the independently issued Remote confirms once");
			context.assertValueEqual(countBoundSheets(
					owner, ownerUuid, complete.sheetRecoverySequence()), 1,
					"the observed Sheet is not duplicated");
			context.assertValueEqual(countBoundRemotes(
					owner, ownerUuid, complete.remoteProjectionUuid()), 1,
					"one exact Remote is issued despite unrelated and unbound Remote stacks");
			context.assertValueEqual(countItem(owner, ModItems.INFINITE_SLIDES_REMOTE), 3,
					"unbound and wrong-owner stacks remain unrelated to reconciliation");
			context.succeed();
		}
		finally {
			removeBoundRewards(level.getServer(), ownerUuid);
			close(connection);
			clearArena(level, desk, FACING);
		}
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void remoteOnlyRepresentationReconcilesSheetIndependently(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(owner(1711), desk, level.getGameTime());
		ConnectedPlayer connection = null;
		try {
			buildArena(level, desk, FACING);
			connection = createSurvivalPlayer(context, ownerUuid, "reward-remote-only");
			RecordingServerPlayer owner = connection.player();
			PlayerCampaignState pending = persistPendingVictory(context, owner, desk, FACING);
			owner.getInventory().setItem(0, InfiniteSlidesRemoteItem.bound(
					new InfiniteSlidesRemoteItem.Binding(ownerUuid, pending.remoteProjectionUuid())));
			owner.getInventory().setItem(1, new ItemStack(ModItems.ATTENDANCE_SHEET));
			owner.getInventory().setItem(2, AttendanceSheetItem.bound(
					new AttendanceSheetItem.Binding(owner(9711), pending.sheetRecoverySequence())));

			context.assertValueEqual(RewardService.reconcilePending(owner),
					RewardService.Outcome.INVENTORY_ISSUED,
					"an observed Remote does not suppress the pending Sheet");
			PlayerCampaignState complete = state(level, ownerUuid);
			context.assertFalse(complete.sheetProjectionPending(), "the independently issued Sheet confirms once");
			context.assertFalse(complete.remoteProjectionPending(), "the observed exact Remote confirms once");
			context.assertValueEqual(countBoundSheets(
					owner, ownerUuid, complete.sheetRecoverySequence()), 1,
					"one exact Sheet is issued despite unrelated and unbound Sheet stacks");
			context.assertValueEqual(countBoundRemotes(
					owner, ownerUuid, complete.remoteProjectionUuid()), 1,
					"the observed Remote is not duplicated");
			context.succeed();
		}
		finally {
			removeBoundRewards(level.getServer(), ownerUuid);
			close(connection);
			clearArena(level, desk, FACING);
		}
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void intruderExactBindingsCannotAcknowledgeOwnerDelivery(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(owner(1709), desk, level.getGameTime());
		UUID intruderUuid = invocationOwnerUuid(owner(9709), desk, level.getGameTime());
		ConnectedPlayer ownerConnection = null;
		ConnectedPlayer intruderConnection = null;
		try {
			buildArena(level, desk, FACING);
			ownerConnection = createSurvivalPlayer(context, ownerUuid, "reward-holder-owner");
			intruderConnection = createSurvivalPlayer(context, intruderUuid, "reward-holder-intruder");
			RecordingServerPlayer owner = ownerConnection.player();
			RecordingServerPlayer intruder = intruderConnection.player();
			PlayerCampaignState pending = persistPendingVictory(context, owner, desk, FACING);
			intruder.getInventory().setItem(0, AttendanceSheetItem.bound(
					new AttendanceSheetItem.Binding(ownerUuid, pending.sheetRecoverySequence())));
			intruder.getInventory().setItem(1, InfiniteSlidesRemoteItem.bound(
					new InfiniteSlidesRemoteItem.Binding(ownerUuid, pending.remoteProjectionUuid())));

			context.assertValueEqual(
					RewardServiceGameTestAccess.reconcilePending(owner, ignored -> true),
					RewardService.Outcome.MATERIALIZATION_FAILED,
					"intruder-held exact bindings cannot acknowledge failed owner delivery"
			);
			context.assertValueEqual(state(level, ownerUuid), pending,
					"intruder inventory leaves both owner projections durably pending");
			context.assertValueEqual(countBoundSheets(owner, ownerUuid, pending.sheetRecoverySequence()), 0,
					"the owner still has no Sheet representation after the forced failure");
			context.assertValueEqual(countBoundRemotes(owner, ownerUuid, pending.remoteProjectionUuid()), 0,
					"the owner still has no Remote representation after the forced failure");

			context.assertValueEqual(RewardService.reconcilePending(owner), RewardService.Outcome.INVENTORY_ISSUED,
					"normal retry delivers both projections to their binding owner");
			PlayerCampaignState complete = state(level, ownerUuid);
			context.assertFalse(complete.sheetProjectionPending(),
					"owner-held Sheet confirms after physical delivery");
			context.assertFalse(complete.remoteProjectionPending(),
					"owner-held Remote confirms after physical delivery");
			context.assertValueEqual(countBoundSheets(owner, ownerUuid, complete.sheetRecoverySequence()), 1,
					"the owner receives one exact Sheet despite the intruder copy");
			context.assertValueEqual(countBoundRemotes(owner, ownerUuid, complete.remoteProjectionUuid()), 1,
					"the owner receives one exact Remote despite the intruder copy");
			context.assertValueEqual(countBoundSheets(intruder, ownerUuid, pending.sheetRecoverySequence()), 1,
					"the intruder's mutable Sheet tag never becomes owner authority");
			context.assertValueEqual(countBoundRemotes(intruder, ownerUuid, pending.remoteProjectionUuid()), 1,
					"the intruder's mutable Remote tag never becomes owner authority");
			context.succeed();
		}
		finally {
			removeBoundRewards(level.getServer(), ownerUuid);
			close(intruderConnection);
			close(ownerConnection);
			clearArena(level, desk, FACING);
		}
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void intruderExactSheetCannotBlockOwnerRecovery(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(owner(1714), desk, level.getGameTime());
		UUID intruderUuid = invocationOwnerUuid(owner(9714), desk, level.getGameTime());
		ConnectedPlayer ownerConnection = null;
		ConnectedPlayer intruderConnection = null;
		try {
			buildArena(level, desk, FACING);
			ownerConnection = createSurvivalPlayer(context, ownerUuid, "sheet-holder-owner");
			RecordingServerPlayer owner = ownerConnection.player();
			persistPendingVictory(context, owner, desk, FACING);
			context.assertValueEqual(RewardService.reconcilePending(owner), RewardService.Outcome.INVENTORY_ISSUED,
					"recovery fixture first completes the owner's reward delivery");
			PlayerCampaignState passed = state(level, ownerUuid);
			removeBoundSheets(level.getServer(), ownerUuid);

			intruderConnection = createSurvivalPlayer(context, intruderUuid, "sheet-holder-intruder");
			RecordingServerPlayer intruder = intruderConnection.player();
			intruder.getInventory().setItem(0, AttendanceSheetItem.bound(
					new AttendanceSheetItem.Binding(ownerUuid, passed.sheetRecoverySequence())));
			owner.clearRecordedSystemMessages();
			context.assertValueEqual(useEmptyHand(level, owner, desk), InteractionResult.SUCCESS_SERVER,
					"intruder-held exact binding cannot block matching-owner Desk recovery");

			PlayerCampaignState recovered = state(level, ownerUuid);
			context.assertValueEqual(recovered.sheetRecoverySequence(), passed.sheetRecoverySequence() + 1L,
					"owner recovery advances to a fresh Sheet generation");
			context.assertFalse(recovered.sheetProjectionPending(),
					"the fresh owner-held Sheet confirms normally");
			context.assertValueEqual(countBoundSheets(owner, ownerUuid, recovered.sheetRecoverySequence()), 1,
					"recovery delivers one fresh Sheet to the owner");
			context.assertValueEqual(countBoundSheets(intruder, ownerUuid, passed.sheetRecoverySequence()), 1,
					"the intruder's old mutable binding remains non-authoritative");
			context.assertValueEqual(countBoundRemotes(owner, ownerUuid, recovered.remoteProjectionUuid()), 1,
					"Sheet recovery never duplicates the owner's Remote");
			context.assertValueEqual(owner.recordedSystemMessageKeys(), List.of(RECOVERED_KEY),
					"owner recovery reports the exact recovered result, not already-present");
			context.succeed();
		}
		finally {
			removeBoundRewards(level.getServer(), ownerUuid);
			close(intruderConnection);
			close(ownerConnection);
			clearArena(level, desk, FACING);
		}
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void sheetSuccessRemoteFailureRetriesOnlyRemoteOnJoin(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(owner(1712), desk, level.getGameTime());
		ConnectedPlayer connection = null;
		try {
			buildArena(level, desk, FACING);
			connection = createSurvivalPlayer(context, ownerUuid, "reward-partial-failure");
			RecordingServerPlayer owner = connection.player();
			persistPendingVictory(context, owner, desk, FACING);

			context.assertValueEqual(RewardServiceGameTestAccess.reconcilePending(
					owner, stack -> stack.getItem() == ModItems.INFINITE_SLIDES_REMOTE),
					RewardService.Outcome.MATERIALIZATION_FAILED,
					"the GameTest-only fault leaves only the Remote projection pending");
			PlayerCampaignState partial = state(level, ownerUuid);
			context.assertFalse(partial.sheetProjectionPending(), "the successful Sheet confirms independently");
			context.assertTrue(partial.remoteProjectionPending(), "the failed Remote remains durably pending");
			context.assertValueEqual(countBoundSheets(
					owner, ownerUuid, partial.sheetRecoverySequence()), 1,
					"the successful Sheet is observable before confirmation");
			context.assertValueEqual(countBoundRemotes(
					owner, ownerUuid, partial.remoteProjectionUuid()), 0,
					"a forced Remote failure produces no hidden representation");

			ServerPlayerEvents.JOIN.invoker().onJoin(owner);
			PlayerCampaignState complete = state(level, ownerUuid);
			context.assertFalse(complete.sheetProjectionPending(), "join does not reopen the completed Sheet");
			context.assertFalse(complete.remoteProjectionPending(), "join retries and confirms the pending Remote");
			context.assertValueEqual(countBoundSheets(
					owner, ownerUuid, complete.sheetRecoverySequence()), 1,
					"join does not duplicate the Sheet");
			context.assertValueEqual(countBoundRemotes(
					owner, ownerUuid, complete.remoteProjectionUuid()), 1,
					"join materializes exactly one bound Remote");
			context.succeed();
		}
		finally {
			removeBoundRewards(level.getServer(), ownerUuid);
			close(connection);
			clearArena(level, desk, FACING);
		}
	}

	@GameTest(maxTicks = 120, padding = 24)
	public void failedBothRecoverAtDeskAndLostRemoteDoesNotReplay(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(owner(1713), desk, level.getGameTime());
		ConnectedPlayer connection = null;
		try {
			buildArena(level, desk, FACING);
			connection = createSurvivalPlayer(context, ownerUuid, "reward-total-failure");
			RecordingServerPlayer owner = connection.player();
			persistPendingVictory(context, owner, desk, FACING);

			context.assertValueEqual(RewardServiceGameTestAccess.reconcilePending(owner, ignored -> true),
					RewardService.Outcome.MATERIALIZATION_FAILED,
					"both forced failures leave both projections retryable");
			PlayerCampaignState failed = state(level, ownerUuid);
			context.assertTrue(failed.sheetProjectionPending(), "failed Sheet remains pending");
			context.assertTrue(failed.remoteProjectionPending(), "failed Remote remains pending");
			context.assertValueEqual(countBoundSheets(
					owner, ownerUuid, failed.sheetRecoverySequence()), 0,
					"no Sheet confirmation exists without an observable representation");
			context.assertValueEqual(countBoundRemotes(
					owner, ownerUuid, failed.remoteProjectionUuid()), 0,
					"no Remote confirmation exists without an observable representation");

			context.assertValueEqual(useEmptyHand(level, owner, desk), InteractionResult.SUCCESS_SERVER,
					"the matching saved Desk retries both pending projections");
			PlayerCampaignState recovered = state(level, ownerUuid);
			context.assertFalse(recovered.sheetProjectionPending(), "Desk retry confirms the Sheet");
			context.assertFalse(recovered.remoteProjectionPending(), "Desk retry confirms the Remote");
			context.assertValueEqual(countBoundSheets(
					owner, ownerUuid, recovered.sheetRecoverySequence()), 1,
					"Desk retry materializes one Sheet");
			context.assertValueEqual(countBoundRemotes(
					owner, ownerUuid, recovered.remoteProjectionUuid()), 1,
					"Desk retry materializes one Remote");

			removeBoundRemotes(level.getServer(), ownerUuid);
			moveBoundSheetOffSelected(owner, ownerUuid, recovered.sheetRecoverySequence());
			ServerPlayerEvents.JOIN.invoker().onJoin(owner);
			context.assertValueEqual(useEmptyHand(level, owner, desk), InteractionResult.SUCCESS_SERVER,
					"later join and Desk checks remain bounded after a confirmed Remote is lost");
			context.assertValueEqual(state(level, ownerUuid), recovered,
					"later loss never reopens the durable Remote projection");
			context.assertValueEqual(countBoundRemotes(
					owner, ownerUuid, recovered.remoteProjectionUuid()), 0,
					"a legitimately issued then lost Remote is not duplicated");
			context.succeed();
		}
		finally {
			removeBoundRewards(level.getServer(), ownerUuid);
			close(connection);
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
			context.assertValueEqual(useEmptyHand(level, owner, wrongDesk), InteractionResult.PASS,
					"a nonmatching lectern passes through to vanilla handling");
			context.assertTrue(owner.recordedSystemMessageKeys().isEmpty(),
					"a nonmatching lectern emits no recovery feedback");
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

	private static PlayerCampaignState persistFallbackReservation(
			GameTestHelper context,
			ServerLevel level,
			CampaignEvent.RewardProjectionKey key,
			UUID entityUuid,
			BlockPos position
	) {
		PlayerCampaignState before = state(level, key.ownerUuid());
		List<CampaignTransition.EffectIntent> suppressedEffects = new ArrayList<>();
		CampaignTransition transition = CampaignService.apply(
				level,
				new CampaignEvent.RewardFallback(
						key,
						entityUuid,
						before.deskDimension(),
						position,
						CampaignEvent.RewardFallbackOperation.RESERVE
				),
				suppressedEffects::add
		);
		context.assertTrue(transition.accepted(),
				"fallback reservation commits before its synchronous materialization effect");
		context.assertValueEqual(suppressedEffects.size(), 1,
				"one reservation emits exactly one materialization effect");
		context.assertTrue(suppressedEffects.getFirst()
				instanceof CampaignTransition.EffectIntent.MaterializeRewardFallback materialize
				&& materialize.key().equals(key)
				&& materialize.fallback().entityUuid().equals(entityUuid),
				"the materialization effect carries the exact persisted reservation");
		return transition.nextState().orElseThrow();
	}

	private static void assertOwnerLiveDropRoundTrip(
			GameTestHelper context,
			UUID seed,
			String playerName,
			boolean remote,
			boolean deathDrop,
			boolean earlyRejection
	) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(seed, desk, level.getGameTime());
		ConnectedPlayer connection = null;
		try {
			buildArena(level, desk, FACING);
			connection = createSurvivalPlayer(context, ownerUuid, playerName);
			RecordingServerPlayer owner = connection.player();
			persistPendingVictory(context, owner, desk, FACING);
			context.assertValueEqual(
					RewardService.reconcilePending(owner),
					RewardService.Outcome.INVENTORY_ISSUED,
					"the live-drop fixture confirms both durable reward projections"
			);
			PlayerCampaignState confirmed = state(level, ownerUuid);
			context.assertFalse(confirmed.sheetProjectionPending(), "Sheet is confirmed before transfer");
			context.assertFalse(confirmed.remoteProjectionPending(), "Remote is confirmed before transfer");

			ItemStack authoritative = remote
					? findBoundRemote(owner, ownerUuid, confirmed.remoteProjectionUuid())
					: findBoundSheet(owner, ownerUuid, confirmed.sheetRecoverySequence());
			retainOnlySelected(owner, authoritative);
			context.assertValueEqual(countLiveRewardRepresentations(
					level.getServer(), owner, confirmed, remote), 1,
					"the confirmed reward starts with exactly one inventory representation");

			int selected = owner.getInventory().getSelectedSlot();
			int forgedSlot = selected == 1 ? 2 : 1;
			ItemStack copiedStack = authoritative.copy();
			ItemEntity copiedEqual = fallbackItem(
					level, owner.blockPosition(), copiedStack, UUID.randomUUID());
			if (deathDrop) {
				owner.setHealth(0.0F);
				owner.getInventory().setItem(forgedSlot, copiedStack);
			}
			else {
				owner.getInventory().setItem(forgedSlot, authoritative);
				owner.getInventory().setItem(selected, ItemStack.EMPTY);
				copiedEqual.setThrower(owner);
			}
			context.assertFalse(level.addFreshEntity(copiedEqual),
					deathDrop
							? "a copied binding in the dead inventory cannot impersonate a real death source transaction"
							: "a copied binding with the old thrower/empty-selected heuristic lacks an exact Q source ticket");
			owner.getInventory().setItem(forgedSlot, ItemStack.EMPTY);
			owner.getInventory().setItem(selected, authoritative);
			context.assertTrue((remote ? state(level, ownerUuid).remoteFallback()
					: state(level, ownerUuid).sheetFallback()) == null,
					"rejected fabrication creates no durable fallback authority");
			authoritative = assertRejectedLiveDropRollsBack(
					context, level, owner, confirmed, remote, deathDrop, earlyRejection);

			if (deathDrop) {
				owner.dropEquipmentForTest();
				owner.setHealth(owner.getMaxHealth());
			}
			else {
				owner.drop(false);
			}
			List<ItemEntity> dropped = remote
					? boundRemoteEntities(level.getServer(), ownerUuid, confirmed.remoteProjectionUuid())
					: boundSheetEntities(level.getServer(), ownerUuid, confirmed.sheetRecoverySequence());
			context.assertValueEqual(dropped.size(), 1,
					"the production live-drop path materializes exactly one reward entity");
			PlayerCampaignState afterDrop = state(level, ownerUuid);
			PlayerCampaignState.RewardFallbackRef fallback = Objects.requireNonNull(
					remote ? afterDrop.remoteFallback() : afterDrop.sheetFallback(),
					"live transfer fallback");
			context.assertValueEqual(fallback.entityUuid(), dropped.getFirst().getUUID(),
					"state-first transfer names the exact admitted entity");
			context.assertTrue(fallback.materialized(),
					"an admitted live transfer is immediately durable and materialized");
			context.assertValueEqual(countLiveRewardRepresentations(
					level.getServer(), owner, afterDrop, remote), 1,
					"the live transfer replaces inventory authority without duplication");

			ItemEntity rewardEntity = dropped.getFirst();
			rewardEntity.setNoPickUpDelay();
			rewardEntity.playerTouch(owner);
			context.assertTrue(rewardEntity.isRemoved(), "the owner recovers the exact live-drop entity");
			context.assertValueEqual(countLiveRewardRepresentations(
					level.getServer(), owner, state(level, ownerUuid), remote), 1,
					"pickup restores exactly one inventory representation");
			if (remote) {
				ItemStack recovered = findBoundRemote(owner, ownerUuid, confirmed.remoteProjectionUuid());
				retainOnlySelected(owner, recovered);
				long beforeUse = state(level, ownerUuid).remoteCooldownUntilGameTime();
				context.assertValueEqual(
						owner.gameMode.useItem(owner, level, recovered, InteractionHand.MAIN_HAND),
						InteractionResult.SUCCESS_SERVER,
						"the recovered Remote remains server-authorized and usable"
				);
				context.assertTrue(state(level, ownerUuid).remoteCooldownUntilGameTime() > beforeUse,
						"recovered Remote use commits its durable cooldown");
			}
			context.succeed();
		}
		finally {
			removeBoundRewards(level.getServer(), ownerUuid);
			close(connection);
			clearArena(level, desk, FACING);
		}
	}

	private static ItemStack assertRejectedLiveDropRollsBack(
			GameTestHelper context,
			ServerLevel level,
			RecordingServerPlayer owner,
			PlayerCampaignState confirmed,
			boolean remote,
			boolean deathDrop,
			boolean earlyRejection
	) {
		AtomicBoolean rejectNext = new AtomicBoolean(true);
		AtomicReference<ItemEntity> rejectedEntity = new AtomicReference<>();
		var rejector = (net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.AllowLoad)
				(entity, candidateLevel, spawnReason, loadedFromDisk) -> {
			if (entity instanceof ItemEntity item
					&& (deathDrop ? item.getOwner() == null : item.getOwner() == owner)
					&& (remote
							? InfiniteSlidesRemoteItem.binding(item.getItem()).filter(binding ->
									binding.ownerUuid().equals(owner.getUUID())
											&& binding.projectionUuid().equals(confirmed.remoteProjectionUuid())).isPresent()
							: AttendanceSheetItem.binding(item.getItem()).filter(binding ->
									binding.ownerUuid().equals(owner.getUUID())
											&& binding.recoverySequence() == confirmed.sheetRecoverySequence()).isPresent())
					&& rejectNext.compareAndSet(true, false)) {
				rejectedEntity.set(item);
				return false;
			}
			return true;
		};
		if (earlyRejection) {
			Identifier earlyPhase = DevelopersHell.id("reward_early_" + owner.getUUID());
			ServerEntityEvents.ALLOW_LOAD.addPhaseOrdering(earlyPhase, Event.DEFAULT_PHASE);
			ServerEntityEvents.ALLOW_LOAD.register(earlyPhase, rejector);
		}
		else {
			ServerEntityEvents.ALLOW_LOAD.register(rejector);
		}
		ItemStack unrelated = deathDrop ? ItemStack.EMPTY : new ItemStack(Items.DIRT, 7);
		int sourceSlot = owner.getInventory().getSelectedSlot();
		int unrelatedSlot = owner.getInventory().getSelectedSlot() == 1 ? 2 : 1;
		if (!deathDrop) {
			owner.getInventory().setItem(unrelatedSlot, unrelated);
			owner.drop(false);
		}
		else {
			owner.dropEquipmentForTest();
		}

		context.assertFalse(rejectNext.get(), earlyRejection
				? "the exact production live drop reached the earlier ordered rejection listener"
				: "the exact production live drop reached the later rejection listener");
		context.assertValueEqual(RewardServiceGameTestAccess.pendingLiveTransferCount(), 0,
				"rejected live drop leaves zero transient source/admission tickets");
		context.assertTrue((remote ? state(level, owner.getUUID()).remoteFallback()
				: state(level, owner.getUUID()).sheetFallback()) == null,
				"the ServerLevel RETURN mixin rolls rejected admission authority back synchronously");
		context.assertFalse(remote ? state(level, owner.getUUID()).remoteProjectionPending()
				: state(level, owner.getUUID()).sheetProjectionPending(),
				"exact source restoration reconfirms the projection without a pending ticket");
		if (!deathDrop) {
			context.assertTrue(owner.getInventory().getItem(unrelatedSlot) == unrelated
					&& unrelated.getCount() == 7,
					"rollback neither overwrites nor deletes an unrelated inventory stack");
		}
		context.assertValueEqual((remote
				? boundRemoteEntities(level.getServer(), owner.getUUID(), confirmed.remoteProjectionUuid())
				: boundSheetEntities(level.getServer(), owner.getUUID(), confirmed.sheetRecoverySequence())).size(), 0,
				"a rejected add leaves no tracked reward entity");
		ItemStack exactRejectedStack = Objects.requireNonNull(
				Objects.requireNonNull(rejectedEntity.get(), "rejected live-drop entity").getItem(),
				"rejected live-drop stack");
		context.assertTrue(level.getEntity(rejectedEntity.get().getUUID()) == null,
				"the exact rejected entity UUID never becomes live authority");
		context.assertTrue(remote
				? InfiniteSlidesRemoteItem.binding(exactRejectedStack).filter(binding ->
						binding.ownerUuid().equals(owner.getUUID())
								&& binding.projectionUuid().equals(confirmed.remoteProjectionUuid())).isPresent()
				: AttendanceSheetItem.binding(exactRejectedStack).filter(binding ->
						binding.ownerUuid().equals(owner.getUUID())
								&& binding.recoverySequence() == confirmed.sheetRecoverySequence()).isPresent(),
				"the restored object retains the exact confirmed reward binding");
		context.assertTrue(owner.getInventory().getItem(sourceSlot) == exactRejectedStack,
				"rollback restores the exact rejected stack object to its exact vanilla source slot");
		context.assertValueEqual(remote
					? countBoundRemotes(owner, owner.getUUID(), confirmed.remoteProjectionUuid())
					: countBoundSheets(owner, owner.getUUID(), confirmed.sheetRecoverySequence()), 1,
				"rejected admission restores exactly one reward to its authenticated origin inventory");
		if (!deathDrop) {
			owner.getInventory().setItem(unrelatedSlot, ItemStack.EMPTY);
		}
		ItemStack restored = remote
				? findBoundRemote(owner, owner.getUUID(), confirmed.remoteProjectionUuid())
				: findBoundSheet(owner, owner.getUUID(), confirmed.sheetRecoverySequence());
		retainOnlySelected(owner, restored);
		return restored;
	}

	@GameTest(maxTicks = 160, padding = 24)
	public void materializedSheetNaturalLoadedChunkRelocationPersistsAndReloadsThroughDiskPath(
			GameTestHelper context
	) {
		assertNaturalRelocationPersistsAndReloads(
				context, owner(1734), "reward-natural-relocation-sheet");
	}

	private static void assertAlternativeRewardDropRecovers(
			GameTestHelper context,
			UUID seed,
			String playerName,
			boolean remote,
			AlternativeDrop mode
	) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(seed, desk, level.getGameTime());
		ConnectedPlayer connection = null;
		BlockPos chestPos = null;
		try {
			buildArena(level, desk, FACING);
			connection = createSurvivalPlayer(context, ownerUuid, playerName);
			RecordingServerPlayer owner = connection.player();
			persistPendingVictory(context, owner, desk, FACING);
			context.assertValueEqual(
					RewardService.reconcilePending(owner),
					RewardService.Outcome.INVENTORY_ISSUED,
					"alternative-drop fixture confirms both durable projections"
			);
			PlayerCampaignState confirmed = state(level, ownerUuid);
			ItemStack authoritative = remote
					? findBoundRemote(owner, ownerUuid, confirmed.remoteProjectionUuid())
					: findBoundSheet(owner, ownerUuid, confirmed.sheetRecoverySequence());
			retainOnlySelected(owner, authoritative);

			ItemEntity copiedEqual = fallbackItem(
					level, owner.blockPosition(), authoritative.copy(), UUID.randomUUID());
			int selected = owner.getInventory().getSelectedSlot();
			int forgedHoldingSlot = selected == 1 ? 2 : 1;
			owner.getInventory().setItem(forgedHoldingSlot, authoritative);
			owner.getInventory().setItem(selected, ItemStack.EMPTY);
			copiedEqual.setThrower(owner);
			context.assertFalse(level.addFreshEntity(copiedEqual),
					"a copied-equal thrower candidate matching the old heuristic lacks an exact source ticket");
			context.assertTrue(level.getEntity(copiedEqual.getUUID()) == null,
					"the unrelated copied-equal entity never becomes tracked");
			owner.getInventory().setItem(forgedHoldingSlot, ItemStack.EMPTY);
			owner.getInventory().setItem(selected, authoritative);

			AtomicBoolean rejectNext = new AtomicBoolean(true);
			AtomicReference<ItemEntity> rejectedEntity = new AtomicReference<>();
			Identifier earlyPhase = DevelopersHell.id("reward_alt_" + ownerUuid);
			ServerEntityEvents.ALLOW_LOAD.addPhaseOrdering(earlyPhase, Event.DEFAULT_PHASE);
			ServerEntityEvents.ALLOW_LOAD.register(earlyPhase,
					(entity, candidateLevel, spawnReason, loadedFromDisk) -> {
						if (entity instanceof ItemEntity item
								&& candidateLevel == level
								&& rewardMatches(item.getItem(), confirmed, remote)
								&& rejectNext.compareAndSet(true, false)) {
							rejectedEntity.set(item);
							return false;
						}
						return true;
					});

			int sourceSlot = selected == 9 ? 10 : 9;
			ItemStack unrelated = new ItemStack(Items.DIRT, 7);
			switch (mode) {
				case GUI_SLOT -> {
					owner.getInventory().setItem(selected, unrelated);
					owner.getInventory().setItem(sourceSlot, authoritative);
					int menuSlot = owner.containerMenu.findSlot(owner.getInventory(), sourceSlot)
							.orElseThrow();
					owner.containerMenu.clicked(menuSlot, 1, ContainerInput.THROW, owner);
					context.assertTrue(owner.getInventory().getItem(sourceSlot)
							== Objects.requireNonNull(rejectedEntity.get()).getItem(),
							"GUI rollback restores the exact extracted candidate to its exact source slot");
					context.assertTrue(owner.getInventory().getItem(selected) == unrelated
							&& unrelated.getCount() == 7,
							"GUI rollback preserves the unrelated selected stack by identity and count");
				}
				case CURSOR_CLOSE -> {
					owner.getInventory().setItem(selected, ItemStack.EMPTY);
					fillInventory(owner);
					chestPos = owner.blockPosition().offset(2, 0, 0);
					level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
					ChestBlockEntity chest = (ChestBlockEntity) Objects.requireNonNull(
							level.getBlockEntity(chestPos), "cursor-close chest");
					context.assertTrue(owner.openMenu(chest).isPresent(),
							"cursor-close fixture opens a real server chest menu");
					owner.containerMenu.setCarried(authoritative);
					owner.doCloseContainer();
					context.assertTrue(owner.containerMenu == owner.inventoryMenu,
							"cursor close completes the real server menu transition");
				}
				case CONTAINER_BREAK -> {
					owner.getInventory().setItem(selected, unrelated);
					chestPos = owner.blockPosition().offset(2, 0, 0);
					level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
					ChestBlockEntity chest = (ChestBlockEntity) Objects.requireNonNull(
							level.getBlockEntity(chestPos), "container-break chest");
					chest.setItem(0, authoritative);
					context.assertTrue(level.destroyBlock(chestPos, true, owner),
							"real chest destruction reaches vanilla pre-removal content drops");
					context.assertTrue(level.getBlockEntity(chestPos) == null,
							"the source chest is really gone before recovery is asserted");
					context.assertTrue(owner.getInventory().getItem(selected) == unrelated
							&& unrelated.getCount() == 7,
							"container recovery preserves the unrelated selected stack");
				}
			}

			context.assertFalse(rejectNext.get(),
					"the exact vanilla alternative-drop candidate reached the earlier rejection listener");
			context.assertValueEqual(RewardServiceGameTestAccess.pendingLiveTransferCount(), 0,
					"alternative rejection leaves zero transient admission tickets");
			context.assertValueEqual(countLiveRewardRepresentations(
					level.getServer(), owner, state(level, ownerUuid), remote), 1,
					"rejection leaves exactly one usable or recoverable physical representation");
			context.assertFalse(remote ? state(level, ownerUuid).remoteProjectionPending()
					: state(level, ownerUuid).sheetProjectionPending(),
					"recovery finishes with no pending projection ticket");

			List<ItemEntity> loose = remote
					? boundRemoteEntities(level.getServer(), ownerUuid, confirmed.remoteProjectionUuid())
					: boundSheetEntities(level.getServer(), ownerUuid, confirmed.sheetRecoverySequence());
			if (!loose.isEmpty()) {
				owner.getInventory().setItem(selected, ItemStack.EMPTY);
				loose.getFirst().setNoPickUpDelay();
				loose.getFirst().playerTouch(owner);
			}
			if (remote) {
				ItemStack recovered = findBoundRemote(owner, ownerUuid, confirmed.remoteProjectionUuid());
				retainOnlySelected(owner, recovered);
				long beforeUse = state(level, ownerUuid).remoteCooldownUntilGameTime();
				context.assertValueEqual(
						owner.gameMode.useItem(owner, level, recovered, InteractionHand.MAIN_HAND),
						InteractionResult.SUCCESS_SERVER,
						"alternative-drop recovery keeps the confirmed Remote usable"
				);
				context.assertTrue(state(level, ownerUuid).remoteCooldownUntilGameTime() > beforeUse,
						"recovered Remote commits its authoritative cooldown");
			}
			context.succeed();
		}
		finally {
			removeBoundRewards(level.getServer(), ownerUuid);
			if (chestPos != null) {
				level.setBlock(chestPos, Blocks.AIR.defaultBlockState(), 3);
			}
			close(connection);
			clearArena(level, desk, FACING);
		}
	}

	private static boolean rewardMatches(
			ItemStack stack,
			PlayerCampaignState state,
			boolean remote
	) {
		return remote
				? InfiniteSlidesRemoteItem.binding(stack).filter(binding ->
						binding.ownerUuid().equals(state.ownerUuid())
								&& binding.projectionUuid().equals(state.remoteProjectionUuid())).isPresent()
				: AttendanceSheetItem.binding(stack).filter(binding ->
						binding.ownerUuid().equals(state.ownerUuid())
								&& binding.recoverySequence() == state.sheetRecoverySequence()).isPresent();
	}

	private static void assertPickupClearsUuidAuthority(
			GameTestHelper context,
			UUID seed,
			String playerName,
			boolean remote
	) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(seed, desk, level.getGameTime());
		ConnectedPlayer connection = null;
		try {
			buildArena(level, desk, FACING);
			connection = createSurvivalPlayer(context, ownerUuid, playerName);
			RecordingServerPlayer owner = connection.player();
			persistPendingVictory(context, owner, desk, FACING);
			context.assertValueEqual(RewardService.reconcilePending(owner),
					RewardService.Outcome.INVENTORY_ISSUED,
					"pickup fixture confirms both durable reward projections");
			PlayerCampaignState confirmed = state(level, ownerUuid);
			ItemStack stack = remote
					? findBoundRemote(owner, ownerUuid, confirmed.remoteProjectionUuid())
					: findBoundSheet(owner, ownerUuid, confirmed.sheetRecoverySequence());
			retainOnlySelected(owner, stack);
			owner.drop(false);
			List<ItemEntity> dropped = remote
					? boundRemoteEntities(level.getServer(), ownerUuid, confirmed.remoteProjectionUuid())
					: boundSheetEntities(level.getServer(), ownerUuid, confirmed.sheetRecoverySequence());
			context.assertValueEqual(dropped.size(), 1,
					"the pre-pickup fixture has one exact live fallback");
			ItemEntity live = dropped.getFirst();
			ItemEntity staleDisk = diskRoundTripItem(level, live);
			context.assertValueEqual(staleDisk.getUUID(), live.getUUID(),
					"the simulated stale disk copy retains the pre-pickup UUID");

			live.setNoPickUpDelay();
			live.playerTouch(owner);
			context.assertTrue(live.isRemoved(),
					"vanilla pickup discards the entity after emptying its ItemStack");
			PlayerCampaignState pickedUp = state(level, ownerUuid);
			context.assertTrue((remote ? pickedUp.remoteFallback() : pickedUp.sheetFallback()) == null,
					"exact UUID lookup clears durable fallback authority despite the empty stack");
			context.assertFalse(ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(
					staleDisk, level, EntitySpawnReason.LOAD, true),
					"the stale pre-pickup disk entity cannot reclaim inventory authority");
			context.assertValueEqual(countLiveRewardRepresentations(
					level.getServer(), owner, pickedUp, remote), 1,
					"only the owner's inventory remains authoritative after pickup");
			context.succeed();
		}
		finally {
			removeBoundRewards(level.getServer(), ownerUuid);
			close(connection);
			clearArena(level, desk, FACING);
		}
	}

	private static void assertSameDimensionStaleRelocationRejected(
			GameTestHelper context,
			UUID seed,
			String playerName
	) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(seed, desk, level.getGameTime());
		ConnectedPlayer connection = null;
		boolean cleanupScheduled = false;
		try {
			buildArena(level, desk, FACING);
			connection = createSurvivalPlayer(context, ownerUuid, playerName);
			RecordingServerPlayer owner = connection.player();
			persistPendingVictory(context, owner, desk, FACING);
			context.assertValueEqual(RewardService.reconcilePending(owner),
					RewardService.Outcome.INVENTORY_ISSUED,
					"same-dimension relocation fixture confirms both projections");
			PlayerCampaignState confirmed = state(level, ownerUuid);
			ItemStack sheet = findBoundSheet(owner, ownerUuid, confirmed.sheetRecoverySequence());
			retainOnlySelected(owner, sheet);
			owner.drop(false);
			ItemEntity current = boundSheetEntities(
					level.getServer(), ownerUuid, confirmed.sheetRecoverySequence()).getFirst();
			ItemEntity staleDisk = diskRoundTripItem(level, current);
			PlayerCampaignState.RewardFallbackRef prior = Objects.requireNonNull(
					state(level, ownerUuid).sheetFallback(), "current Sheet authority");
			ChunkPos sourceChunk = ChunkPos.containing(prior.position());
			AttendanceSheetItem.Binding expected = new AttendanceSheetItem.Binding(
					ownerUuid, confirmed.sheetRecoverySequence());
			context.assertTrue(level.getEntity(current.getUUID()) == current,
					"the pre-relocation Sheet UUID resolves to the one current live entity");
			context.assertFalse(ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(
					staleDisk, level, EntitySpawnReason.LOAD, true),
					"a same-UUID live entity vetoes its pre-relocation disk copy before any later callback");

			BlockPos relocatedPos = prior.position().offset(48, 0, 0);
			level.getChunkAt(relocatedPos);
			current.snapTo(Vec3.atCenterOf(relocatedPos));
			PlayerCampaignState afterMovement = state(level, ownerUuid);
			context.assertValueEqual(afterMovement.sheetFallback().entityUuid(), current.getUUID(),
					"real section relocation preserves the exact current UUID");
			context.assertValueEqual(afterMovement.sheetFallback().position(), current.blockPosition(),
					"real section callback records the current Sheet position");
			context.assertTrue(afterMovement.sheetFallback().materialized(),
					"real section relocation preserves materialized authority");
			context.assertFalse(current.isRemoved(),
					"real section transfer keeps the current Sheet alive");
			context.assertFalse(current.getItem().isEmpty(),
					"real section transfer keeps the exact Sheet stack nonempty");
			context.assertTrue(AttendanceSheetItem.binding(current.getItem())
					.filter(expected::equals).isPresent(),
					"real section transfer keeps the exact confirmed Sheet binding");
			DevelopersHell.LOGGER.info("REWARD_RELOCATION_TEST_PATH=REAL_SECTION_CALLBACK");
			PlayerCampaignState relocated = state(level, ownerUuid);
			PlayerCampaignState.RewardFallbackRef expectedRelocated = prior.at(
					level.dimension().identifier().toString(), current.blockPosition(), true);
			context.assertValueEqual(relocated.sheetFallback(), expectedRelocated,
					"the production section callback CAS-relocates the exact durable Sheet context");
			context.assertFalse(ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(
					staleDisk, level, EntitySpawnReason.LOAD, true),
					"the old-chunk disk copy cannot reclaim materialized Sheet authority");
			context.assertTrue(boundSheetEntities(
					level.getServer(), ownerUuid, confirmed.sheetRecoverySequence()).stream()
					.noneMatch(item -> ChunkPos.containing(item.blockPosition()).equals(sourceChunk)),
					"real section relocation leaves no bound Sheet in the old source chunk");
			context.assertValueEqual(countLiveRewardRepresentations(
					level.getServer(), owner, relocated, false), 1,
					"same-dimension stale admission leaves exactly one current Sheet");
			context.succeed();
		}
		finally {
			if (!cleanupScheduled) {
				removeBoundRewards(level.getServer(), ownerUuid);
				close(connection);
				clearArena(level, desk, FACING);
			}
		}
	}

	private static void assertDimensionTravelIsAtomic(
			GameTestHelper context,
			UUID seed,
			String playerName
	) {
		ServerLevel sourceLevel = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(seed, desk, sourceLevel.getGameTime());
		ConnectedPlayer connection = null;
		ServerLevel forcedTargetLevel = null;
		ChunkPos forcedTargetChunk = null;
		boolean cleanupScheduled = false;
		try {
			buildArena(sourceLevel, desk, FACING);
			connection = createSurvivalPlayer(context, ownerUuid, playerName);
			RecordingServerPlayer owner = connection.player();
			persistPendingVictory(context, owner, desk, FACING);
			context.assertValueEqual(RewardService.reconcilePending(owner),
					RewardService.Outcome.INVENTORY_ISSUED,
					"dimension-travel fixture confirms both projections");
			PlayerCampaignState confirmed = state(sourceLevel, ownerUuid);
			ItemStack remote = findBoundRemote(owner, ownerUuid, confirmed.remoteProjectionUuid());
			retainOnlySelected(owner, remote);
			owner.drop(false);
			ItemEntity source = boundRemoteEntities(
					sourceLevel.getServer(), ownerUuid, confirmed.remoteProjectionUuid()).getFirst();
			UUID entityUuid = source.getUUID();
			ItemEntity staleSourceDisk = diskRoundTripItem(sourceLevel, source);
			PlayerCampaignState.RewardFallbackRef sourceRef = Objects.requireNonNull(
					state(sourceLevel, ownerUuid).remoteFallback(), "source Remote authority");

			ServerLevel targetLevel = Objects.requireNonNull(
					sourceLevel.getServer().getLevel(Level.NETHER), "GameTest Nether");
			int offset = Math.floorMod(ownerUuid.hashCode(), 512) * 32;
			BlockPos targetPos = new BlockPos(12000 + offset, 80, 12000 + offset);
			forcedTargetLevel = targetLevel;
			forcedTargetChunk = ChunkPos.containing(targetPos);
			context.assertTrue(targetLevel.setChunkForced(
					forcedTargetChunk.x(), forcedTargetChunk.z(), true),
					"cross-dimension fixture pins a real entity-ticking target chunk");
			targetLevel.getChunkAt(targetPos);
			Entity targetOwner = owner.teleport(new TeleportTransition(
					targetLevel,
					Vec3.atCenterOf(targetPos),
					Vec3.ZERO,
					owner.getYRot(),
					owner.getXRot(),
					TeleportTransition.DO_NOTHING
			));
			context.assertTrue(targetOwner == owner,
					"the authenticated owner supplies a real target-dimension player ticket");
			AtomicBoolean rejectTargetOnce = new AtomicBoolean(true);
			AtomicBoolean observedStateFirstTarget = new AtomicBoolean(false);
			InfiniteSlidesRemoteItem.Binding expected = new InfiniteSlidesRemoteItem.Binding(
					ownerUuid, confirmed.remoteProjectionUuid());
			ServerEntityEvents.ALLOW_LOAD.register((entity, candidateLevel, spawnReason, loadedFromDisk) -> {
				if (candidateLevel == targetLevel
						&& spawnReason == EntitySpawnReason.DIMENSION_TRAVEL
						&& entity instanceof ItemEntity item
						&& item.getUUID().equals(entityUuid)
						&& InfiniteSlidesRemoteItem.binding(item.getItem()).filter(expected::equals).isPresent()
						&& rejectTargetOnce.compareAndSet(true, false)) {
					PlayerCampaignState.RewardFallbackRef observed =
							state(candidateLevel, ownerUuid).remoteFallback();
					observedStateFirstTarget.set(observed != null
							&& observed.dimension().equals(targetLevel.dimension().identifier().toString())
							&& ChunkPos.containing(observed.position()).equals(ChunkPos.containing(targetPos)));
					return false;
				}
				return true;
			});

			ConnectedPlayer cleanupConnection = connection;
			ChunkPos cleanupTargetChunk = forcedTargetChunk;
			context.runAfterDelay(20, () -> {
				try {
			PlayerCampaignState.RewardFallbackRef expectedFirstRollback = sourceRef.at(
					sourceLevel.dimension().identifier().toString(), source.blockPosition(), true);
			Entity rejectedReplacement = source.teleport(new TeleportTransition(
					targetLevel,
					Vec3.atCenterOf(targetPos),
					Vec3.ZERO,
					source.getYRot(),
					source.getXRot(),
					TeleportTransition.DO_NOTHING
			));
			context.assertTrue(rejectedReplacement != null && rejectedReplacement != source,
					"Minecraft creates the documented replacement before target admission");
			context.assertValueEqual(rejectedReplacement.getUUID(), entityUuid,
					"dimension-travel replacement preserves exact entity identity");
			context.assertFalse(rejectTargetOnce.get(),
					"a later listener exercises the ignored target-add failure");
			context.assertTrue(observedStateFirstTarget.get(),
					"target authority is durable before the later listener can reject add");
			context.assertTrue(targetLevel.getEntity(entityUuid) == null,
					"the rejected target replacement is never tracked");
			List<ItemEntity> restoredSource = boundRemoteEntities(
					sourceLevel.getServer(), ownerUuid, confirmed.remoteProjectionUuid());
			context.assertValueEqual(restoredSource.size(), 1,
					"target rejection restores exactly one source Remote");
			context.assertTrue(restoredSource.getFirst().level() == sourceLevel,
					"the exact rejected transfer returns to its authenticated source level");
			context.assertValueEqual(
					state(sourceLevel, ownerUuid).remoteFallback(), expectedFirstRollback,
					"target rejection CAS-rolls durable authority back to the exact source ref");

			ItemEntity restored = restoredSource.getFirst();
			context.assertTrue(sourceLevel.getEntity(entityUuid) == restored,
					"source UUID lookup resolves the compensated physical Remote before retry");
			context.assertTrue(InfiniteSlidesRemoteItem.binding(restored.getItem())
					.filter(expected::equals).isPresent(),
					"later-listener compensation preserves the exact confirmed Remote binding");

			RewardServiceGameTestAccess.beginDimensionTransfer(restored);
			PlayerCampaignState.RewardFallbackRef beforeEarlyRejection = Objects.requireNonNull(
					state(sourceLevel, ownerUuid).remoteFallback(), "pre-admission source authority");
			context.assertValueEqual(beforeEarlyRejection, expectedFirstRollback,
					"the earlier-listener fixture stages the exact authoritative source ref");
			ItemStack preAdmissionStack = restored.getItem().copy();
			context.assertTrue(InfiniteSlidesRemoteItem.binding(preAdmissionStack)
					.filter(expected::equals).isPresent(),
					"the staged source stack carries the exact durable owner/projection identity");
			RewardServiceGameTestAccess.suppressNextDimensionUnload(restored);
			restored.discard();
			context.assertTrue(sourceLevel.getEntity(entityUuid) == null,
					"synthetic earlier-listener fixture removes the staged source exactly once");
			ItemEntity preAdmissionRejected = fallbackItem(
					targetLevel, targetPos, preAdmissionStack, entityUuid);
			preAdmissionRejected.setTarget(ownerUuid);
			RewardService.onEntityAddResult(preAdmissionRejected, false);
			List<ItemEntity> restoredBeforeAdmission = boundRemoteEntities(
					sourceLevel.getServer(), ownerUuid, confirmed.remoteProjectionUuid());
			context.assertValueEqual(restoredBeforeAdmission.size(), 1,
					"earlier-listener rejection restores exactly one authenticated source Remote");
			ItemEntity exactPreAdmissionRestore = restoredBeforeAdmission.getFirst();
			context.assertValueEqual(exactPreAdmissionRestore.getUUID(), entityUuid,
					"pre-admission compensation preserves the exact pending entity UUID");
			context.assertTrue(sourceLevel.getEntity(entityUuid) == exactPreAdmissionRestore,
					"source UUID lookup resolves only the compensated Remote");
			context.assertTrue(targetLevel.getEntity(entityUuid) == null,
					"earlier-listener rejection creates no alternate target authority");
			context.assertValueEqual(
					state(sourceLevel, ownerUuid).remoteFallback(), expectedFirstRollback,
					"pre-admission rejection keeps durable authority at the exact source ref");
			context.assertTrue(InfiniteSlidesRemoteItem.binding(
					exactPreAdmissionRestore.getItem()).filter(expected::equals).isPresent(),
					"pre-admission compensation preserves the exact Remote binding");
			context.assertFalse(RewardServiceGameTestAccess.hasPendingDimensionTransfer(entityUuid),
					"successful pre-admission compensation consumes the one pending handoff ticket");
			context.assertValueEqual(countLiveRewardRepresentations(
					sourceLevel.getServer(), owner, state(sourceLevel, ownerUuid), true), 1,
					"pre-admission compensation leaves exactly one physical Remote representation");

			restored = exactPreAdmissionRestore;
			Entity transferred = restored.teleport(new TeleportTransition(
					targetLevel,
					Vec3.atCenterOf(targetPos),
					Vec3.ZERO,
					restored.getYRot(),
					restored.getXRot(),
					TeleportTransition.DO_NOTHING
			));
			context.assertTrue(transferred instanceof ItemEntity && transferred != restored,
					"the accepted retry uses the real cross-dimension replacement path");
			context.assertValueEqual(transferred.getUUID(), entityUuid,
					"accepted dimension travel preserves the durable entity UUID");
			context.assertTrue(sourceLevel.getEntity(entityUuid) == null,
					"accepted transfer leaves no source entity with the reward UUID");
			ItemEntity returnedTarget = (ItemEntity) transferred;
			context.assertFalse(returnedTarget.isRemoved(),
					"the accepted target Remote remains a physical entity");
			context.assertFalse(returnedTarget.getItem().isEmpty(),
					"the accepted target Remote retains its exact physical stack");
			context.assertTrue(InfiniteSlidesRemoteItem.binding(returnedTarget.getItem())
					.filter(expected::equals).isPresent(),
					"the accepted target entity retains the confirmed Remote binding");
			List<ItemEntity> acceptedTargets = boundRemoteEntities(
					sourceLevel.getServer(), ownerUuid, confirmed.remoteProjectionUuid());
			context.assertTrue(acceptedTargets.size() <= 1,
					"the target entity store never exposes duplicate bound Remotes");
			if (acceptedTargets.isEmpty()) {
				context.assertTrue(targetLevel.getEntity(entityUuid) == null,
						"the headless Nether keeps the accepted Remote in its hidden durable section");
			}
			else {
				context.assertTrue(acceptedTargets.getFirst() == returnedTarget,
						"a visible target lookup resolves the accepted replacement itself");
				context.assertTrue(targetLevel.getEntity(entityUuid) == returnedTarget,
						"target UUID lookup resolves the one visible physical Remote");
			}
			ItemEntity duplicateUuidProbe = new ItemEntity(
					targetLevel, targetPos.getX(), targetPos.getY(), targetPos.getZ(),
					new ItemStack(Items.STONE));
			duplicateUuidProbe.setUUID(entityUuid);
			context.assertFalse(targetLevel.addFreshEntity(duplicateUuidProbe),
					"the durable target entity store reserves the exact accepted UUID once");
			PlayerCampaignState moved = state(sourceLevel, ownerUuid);
			context.assertValueEqual(moved.remoteFallback().dimension(),
					targetLevel.dimension().identifier().toString(),
					"dimension-travel CAS commits target dimension authority");
			context.assertValueEqual(ChunkPos.containing(moved.remoteFallback().position()),
					ChunkPos.containing(targetPos),
					"dimension-travel CAS commits target chunk authority");
			context.assertFalse(ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(
					staleSourceDisk, sourceLevel, EntitySpawnReason.LOAD, true),
					"the stale source-dimension disk copy cannot reclaim moved authority");
			int hiddenTargetRepresentation = acceptedTargets.isEmpty() ? 1 : 0;
			context.assertValueEqual(countLiveRewardRepresentations(
					sourceLevel.getServer(), owner, moved, true) + hiddenTargetRepresentation, 1,
					"cross-dimension transfer retains one authoritative Remote representation");
				}
				finally {
					removeBoundRewards(sourceLevel.getServer(), ownerUuid);
					targetLevel.setChunkForced(
							cleanupTargetChunk.x(), cleanupTargetChunk.z(), false);
					close(cleanupConnection);
					clearArena(sourceLevel, desk, FACING);
				}
				context.succeed();
			});
			cleanupScheduled = true;
		}
		finally {
			if (!cleanupScheduled) {
				removeBoundRewards(sourceLevel.getServer(), ownerUuid);
				if (forcedTargetLevel != null && forcedTargetChunk != null) {
					forcedTargetLevel.setChunkForced(
							forcedTargetChunk.x(), forcedTargetChunk.z(), false);
				}
				close(connection);
				clearArena(sourceLevel, desk, FACING);
			}
		}
	}

	private static ItemStack findBoundSheet(ServerPlayer owner, UUID ownerUuid, long sequence) {
		AttendanceSheetItem.Binding expected = new AttendanceSheetItem.Binding(ownerUuid, sequence);
		for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
			ItemStack stack = owner.getInventory().getItem(slot);
			if (AttendanceSheetItem.binding(stack).filter(expected::equals).isPresent()) {
				return stack;
			}
		}
		throw new IllegalStateException("missing confirmed Attendance Sheet");
	}

	private static ItemStack findBoundRemote(ServerPlayer owner, UUID ownerUuid, UUID projectionUuid) {
		InfiniteSlidesRemoteItem.Binding expected =
				new InfiniteSlidesRemoteItem.Binding(ownerUuid, projectionUuid);
		for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
			ItemStack stack = owner.getInventory().getItem(slot);
			if (InfiniteSlidesRemoteItem.binding(stack).filter(expected::equals).isPresent()) {
				return stack;
			}
		}
		throw new IllegalStateException("missing confirmed Infinite Slides Remote");
	}

	private static void retainOnlySelected(ServerPlayer owner, ItemStack retained) {
		int selected = owner.getInventory().getSelectedSlot();
		for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
			owner.getInventory().setItem(slot, ItemStack.EMPTY);
		}
		owner.getInventory().setItem(selected, retained);
	}

	private static int countLiveRewardRepresentations(
			MinecraftServer server,
			ServerPlayer owner,
			PlayerCampaignState state,
			boolean remote
	) {
		return remote
				? countBoundRemotes(owner, state.ownerUuid(), state.remoteProjectionUuid())
						+ boundRemoteEntities(server, state.ownerUuid(), state.remoteProjectionUuid()).size()
				: countBoundSheets(owner, state.ownerUuid(), state.sheetRecoverySequence())
						+ boundSheetEntities(server, state.ownerUuid(), state.sheetRecoverySequence()).size();
	}

	private static ItemEntity fallbackItem(
			ServerLevel level,
			BlockPos position,
			ItemStack stack,
			UUID entityUuid
	) {
		ItemEntity item = new ItemEntity(
				level,
				position.getX() + 0.5D,
				position.getY() + 0.25D,
				position.getZ() + 0.5D,
				stack
		);
		item.setUUID(entityUuid);
		return item;
	}

	private static ItemEntity diskRoundTripItem(ServerLevel level, ItemEntity original) {
		TagValueOutput output = TagValueOutput.createWithContext(
				ProblemReporter.DISCARDING,
				level.registryAccess()
		);
		original.saveWithoutId(output);
		ItemEntity restored = new ItemEntity(level, 0.0D, 0.0D, 0.0D, ItemStack.EMPTY);
		restored.load(TagValueInput.create(
				ProblemReporter.DISCARDING,
				level.registryAccess(),
				output.buildResult()
		));
		return restored;
	}

	private static ItemEntity realDiskRoundTripItem(ServerLevel level, ItemEntity original) {
		TagValueOutput output = TagValueOutput.createWithContext(
				ProblemReporter.DISCARDING,
				level.registryAccess()
		);
		if (!original.save(output)) {
			throw new IllegalStateException("reward ItemEntity refused real entity serialization");
		}
		Entity restored = EntityType.loadEntityRecursive(
				TagValueInput.create(
						ProblemReporter.DISCARDING,
						level.registryAccess(),
						output.buildResult()
				),
				level,
				EntitySpawnReason.LOAD,
				EntityProcessor.NOP
		);
		if (!(restored instanceof ItemEntity item)) {
			throw new IllegalStateException("real reward entity deserialization did not produce ItemEntity");
		}
		return item;
	}

	private static PlayerCampaignState persistPendingVictory(
			GameTestHelper context,
			RecordingServerPlayer owner,
			BlockPos desk,
			Direction facing
	) {
		PlayerCampaignState active = startAttempt(context, owner, desk, facing);
		UUID encounterUuid = active.encounterUuid();
		List<CampaignTransition.EffectIntent> suppressedEffects = new ArrayList<>();
		CampaignTransition transition = CampaignService.commitVictory(
				owner.level(), owner.getUUID(), encounterUuid, suppressedEffects::add);
		context.assertTrue(transition.accepted(),
				"the fault fixture durably commits the authorized active encounter victory");
		context.assertValueEqual(suppressedEffects.size(), 2,
				"the fault fixture suppresses only the two post-commit effects");
		PlayerCampaignState pending = state(owner.level(), owner.getUUID());
		context.assertTrue(pending.sheetProjectionPending(),
				"victory persists Sheet pending before projection effects");
		context.assertTrue(pending.remoteProjectionPending(),
				"victory persists Remote pending before projection effects");
		LectureEncounterManager.cleanup(encounterUuid);
		return pending;
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

	private static PlayerCampaignState withRetryPos(PlayerCampaignState state, BlockPos retryPos) {
		return new PlayerCampaignState(
				state.ownerUuid(),
				state.chapter(),
				state.status(),
				state.attemptCount(),
				state.deskDimension(),
				state.deskPos(),
				state.deskFacing(),
				retryPos,
				state.activeEncounterRef(),
				state.sheetEntitled(),
				state.remoteIssued(),
				state.retakeEntitled(),
				state.retakeEncounterUuid(),
				state.retakeFallbackReservationUuid(),
				state.retakeFallbackEntityUuid(),
				state.remoteCooldownUntilGameTime(),
				state.sheetRecoverySequence(),
				state.remoteReadyNoticeForDeadlineGameTime(),
				state.sheetProjectionPending(),
				state.remoteProjectionPending(),
				state.remoteProjectionUuid(),
				state.legacyRemoteAdoptionPending(),
				state.sheetFallback(),
				state.remoteFallback()
		);
	}

	private static PlayerCampaignState legacyRemoteMigrationState(
			UUID ownerUuid,
			BlockPos desk,
			Direction facing,
			int attempt
	) {
		UUID projectionUuid = PlayerCampaignState.legacyRemoteProjectionUuid(ownerUuid, desk, attempt);
		return new PlayerCampaignState(
				ownerUuid,
				PlayerCampaignState.CampaignChapter.LECTURE_PASSED,
				PlayerCampaignState.LectureStatus.PASSED,
				attempt,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				desk,
				facing,
				desk.relative(facing.getOpposite(), 2),
				null,
				true,
				true,
				false,
				null,
				null,
				null,
				0L,
				0L,
				0L,
				false,
				true,
				projectionUuid,
				true
		);
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

	private static int countBoundRemotes(ServerPlayer player, UUID ownerUuid, UUID projectionUuid) {
		int count = 0;
		InfiniteSlidesRemoteItem.Binding expected =
				new InfiniteSlidesRemoteItem.Binding(ownerUuid, projectionUuid);
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (InfiniteSlidesRemoteItem.binding(stack).filter(expected::equals).isPresent()) {
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

	private static List<ItemEntity> boundRemoteEntities(
			MinecraftServer server,
			UUID ownerUuid,
			UUID projectionUuid
	) {
		InfiniteSlidesRemoteItem.Binding expected =
				new InfiniteSlidesRemoteItem.Binding(ownerUuid, projectionUuid);
		List<ItemEntity> result = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity item
						&& !item.isRemoved()
						&& InfiniteSlidesRemoteItem.binding(item.getItem()).filter(expected::equals).isPresent()) {
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

	private static void removeBoundRemotes(MinecraftServer server, UUID ownerUuid) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				if (InfiniteSlidesRemoteItem.binding(player.getInventory().getItem(slot))
						.map(InfiniteSlidesRemoteItem.Binding::ownerUuid).filter(ownerUuid::equals).isPresent()) {
					player.getInventory().setItem(slot, ItemStack.EMPTY);
				}
			}
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity item
						&& InfiniteSlidesRemoteItem.binding(item.getItem())
						.map(InfiniteSlidesRemoteItem.Binding::ownerUuid).filter(ownerUuid::equals).isPresent()) {
					item.discard();
				}
			}
		}
	}

	private static void removeBoundRewards(MinecraftServer server, UUID ownerUuid) {
		removeBoundSheets(server, ownerUuid);
		removeBoundRemotes(server, ownerUuid);
	}

	private static void removeRewardEntities(MinecraftServer server, UUID ownerUuid) {
		removeBoundRewards(server, ownerUuid);
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

		private void dropEquipmentForTest() {
			super.dropEquipment(level());
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

	private static void assertNaturalRelocationPersistsAndReloads(
			GameTestHelper context,
			UUID seed,
			String playerName
	) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(RELATIVE_DESK);
		UUID ownerUuid = invocationOwnerUuid(seed, desk, level.getGameTime());
		ConnectedPlayer connection = null;
		boolean cleanupScheduled = false;
		try {
			buildArena(level, desk, FACING);
			connection = createSurvivalPlayer(context, ownerUuid, playerName);
			RecordingServerPlayer owner = connection.player();
			persistPendingVictory(context, owner, desk, FACING);
			context.assertValueEqual(RewardService.reconcilePending(owner),
					RewardService.Outcome.INVENTORY_ISSUED,
					"natural-relocation fixture confirms both projections");
			PlayerCampaignState confirmed = state(level, ownerUuid);
			ItemStack sheet = findBoundSheet(owner, ownerUuid, confirmed.sheetRecoverySequence());
			retainOnlySelected(owner, sheet);
			owner.drop(false);
			ItemEntity current = boundSheetEntities(
					level.getServer(), ownerUuid, confirmed.sheetRecoverySequence()).getFirst();

			ChunkPos fixtureChunk = ChunkPos.containing(current.blockPosition());
			BlockPos edge = new BlockPos(
					fixtureChunk.getMaxBlockX(),
					current.blockPosition().getY() + 8,
					current.blockPosition().getZ()
			);
			BlockPos target = edge.offset(2, 0, 0);
			level.getChunkAt(edge);
			level.getChunkAt(target);
			owner.snapTo(Vec3.atCenterOf(edge.offset(-2, 0, 0)));
			current.setNoGravity(true);
			current.snapTo(Vec3.atCenterOf(edge));
			PlayerCampaignState.RewardFallbackRef sourceRef = Objects.requireNonNull(
					state(level, ownerUuid).sheetFallback(), "natural relocation source authority");
			ItemEntity staleSourceDisk = realDiskRoundTripItem(level, current);
			AtomicBoolean unloadedDuringMovement = new AtomicBoolean(false);
			ServerEntityEvents.ENTITY_UNLOAD.register((entity, candidateLevel) -> {
				if (entity == current && candidateLevel == level) {
					unloadedDuringMovement.set(true);
				}
			});
			current.setDeltaMovement(Vec3.ZERO);

			ConnectedPlayer cleanupConnection = connection;
			context.runAfterDelay(2, () -> {
				try {
					current.move(MoverType.SELF, new Vec3(2.0D, 0.0D, 0.0D));
					context.assertFalse(unloadedDuringMovement.get(),
							"loaded-to-loaded real entity movement uses section change, not ENTITY_UNLOAD");
					context.assertFalse(ChunkPos.containing(current.blockPosition()).equals(fixtureChunk),
							"real ItemEntity movement crosses into the adjacent loaded chunk");
					PlayerCampaignState.RewardFallbackRef relocatedRef = sourceRef.at(
							level.dimension().identifier().toString(), current.blockPosition(), true);
					context.assertValueEqual(state(level, ownerUuid).sheetFallback(), relocatedRef,
							"section movement persists the exact full durable fallback reference");

					ItemEntity movedDisk = realDiskRoundTripItem(level, current);
					current.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
					context.assertTrue(level.getEntity(current.getUUID()) == null,
							"real unload removes the live entity before disk admission");
					level.addLegacyChunkEntities(Stream.of(movedDisk));
					context.assertTrue(level.getEntity(movedDisk.getUUID()) == movedDisk,
							"real loadedFromDisk admission restores the exact moved entity");
					context.assertValueEqual(state(level, ownerUuid).sheetFallback(), relocatedRef,
							"real disk load preserves the relocated durable authority");

					ItemEntity movedReload = realDiskRoundTripItem(level, movedDisk);
					movedDisk.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
					context.assertTrue(level.getEntity(movedDisk.getUUID()) == null,
							"the accepted moved copy unloads before stale-source admission is tested");
					level.addLegacyChunkEntities(Stream.of(staleSourceDisk));
					context.assertTrue(level.getEntity(staleSourceDisk.getUUID()) == null,
							"the stale source-chunk disk copy is rejected without duplicate-UUID masking");
					level.addLegacyChunkEntities(Stream.of(movedReload));
					context.assertTrue(level.getEntity(movedReload.getUUID()) == movedReload,
							"the exact moved durable copy remains admissible after stale rejection");
					context.assertValueEqual(countLiveRewardRepresentations(
							level.getServer(), owner, state(level, ownerUuid), false), 1,
							"relocation, reload, and stale rejection leave one representation");
				}
				finally {
					removeBoundRewards(level.getServer(), ownerUuid);
					close(cleanupConnection);
					clearArena(level, desk, FACING);
				}
				context.succeed();
			});
			cleanupScheduled = true;
		}
		finally {
			if (!cleanupScheduled) {
				removeBoundRewards(level.getServer(), ownerUuid);
				close(connection);
				clearArena(level, desk, FACING);
			}
		}
	}

	private enum AlternativeDrop {
		GUI_SLOT,
		CURSOR_CLOSE,
		CONTAINER_BREAK
	}

	@Override
	public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
		method.invoke(this, context);
	}
}
