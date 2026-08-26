package dev.developershell.gametest;

import com.mojang.authlib.GameProfile;
import dev.developershell.DevelopersHell;
import dev.developershell.campaign.CampaignSavedData;
import dev.developershell.campaign.CampaignService;
import dev.developershell.lecture.LectureEncounterManager;
import dev.developershell.lecture.LectureRules;
import dev.developershell.registry.ModEntities;
import dev.developershell.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class FoundationGameTests implements CustomTestMethodInvoker {
	private static final UUID OWNER_UUID = UUID.fromString("c0de0000-0000-4000-8000-000000000201");
	private static final UUID COMPETING_OWNER_UUID = UUID.fromString("c0de0000-0000-4000-8000-000000000202");
	private static final UUID DISCOVERY_OWNER_UUID = UUID.fromString("c0de0000-0000-4000-8000-000000000216");
	private static final long TRACER_SEED = 0x02_01_5L;
	private static final Identifier CONTRACT_ADVANCEMENT_ID =
			DevelopersHell.id("a_suspicious_opportunity");
	private static final ResourceKey<Recipe<?>> CONTRACT_RECIPE_KEY = ResourceKey.create(
			Registries.RECIPE,
			DevelopersHell.id("cursed_unpaid_internship_contract")
	);
	private static final List<String> CONTRACT_TOOLTIP_KEYS = List.of(
			"tooltip.developers_hell.contract.lectern",
			"tooltip.developers_hell.contract.lecture",
			"tooltip.developers_hell.contract.blocks"
	);
	private static final String WRONG_TARGET_KEY = "message.developers_hell.contract.find_lectern";

	@GameTest
	public void foundationTokenIsRegistered(GameTestHelper context) {
		Identifier actual = BuiltInRegistries.ITEM.getKey(ModItems.FOUNDATION_TOKEN);
		Identifier expected = DevelopersHell.id("foundation_token");
		context.assertValueEqual(actual, expected, "foundation token registry key");
		context.succeed();
	}

	@GameTest(padding = 24)
	public void contractDiscoveryCraftingAndGuidanceAreSurvivalReachable(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		MinecraftServer server = level.getServer();
		BlockPos wrongTarget = context.absolutePos(new BlockPos(12, 2, 4));
		ConnectedPlayer ownerConnection = null;
		level.setBlock(wrongTarget, Blocks.STONE.defaultBlockState(), 3);

		try {
			ownerConnection = createSurvivalPlayer(
					context,
					DISCOVERY_OWNER_UUID,
					"discovery-owner",
					new BlockPos(12, 2, 2)
			);
			RecordingServerPlayer owner = ownerConnection.player();
			AdvancementHolder discovery = server.getAdvancements().get(CONTRACT_ADVANCEMENT_ID);
			context.assertTrue(discovery != null, "A Suspicious Opportunity advancement must load by exact ID");
			DisplayInfo display = discovery.value().display()
					.orElseThrow(() -> context.assertionException("discovery advancement display missing"));
			context.assertValueEqual(
					translationKey(display.getTitle()),
					"advancement.developers_hell.a_suspicious_opportunity.title",
					"localized discovery title"
			);
			context.assertValueEqual(
					translationKey(display.getDescription()),
					"advancement.developers_hell.a_suspicious_opportunity.description",
					"localized discovery description"
			);
			context.assertValueEqual(
					discovery.value().rewards().recipes(),
					List.of(CONTRACT_RECIPE_KEY),
					"discovery rewards the exact Contract recipe"
			);
			context.assertTrue(
					server.getRecipeManager().byKey(CONTRACT_RECIPE_KEY).isPresent(),
					"Contract recipe must load by exact ID"
			);

			context.assertFalse(
					owner.getAdvancements().getOrStartProgress(discovery).isDone(),
					"discovery starts incomplete"
			);
			context.assertFalse(owner.getRecipeBook().contains(CONTRACT_RECIPE_KEY), "recipe starts hidden");
			owner.getInventory().add(new ItemStack(Items.PAPER));
			CriteriaTriggers.INVENTORY_CHANGED.trigger(owner, owner.getInventory(), new ItemStack(Items.PAPER));
			context.assertFalse(
					owner.getAdvancements().getOrStartProgress(discovery).isDone(),
					"paper alone does not complete discovery"
			);
			context.assertFalse(owner.getRecipeBook().contains(CONTRACT_RECIPE_KEY), "paper alone keeps recipe hidden");
			owner.getInventory().add(new ItemStack(Items.INK_SAC));
			CriteriaTriggers.INVENTORY_CHANGED.trigger(owner, owner.getInventory(), new ItemStack(Items.INK_SAC));
			context.assertTrue(
					owner.getAdvancements().getOrStartProgress(discovery).isDone(),
					"paper plus ink completes discovery"
			);
			context.assertTrue(owner.getRecipeBook().contains(CONTRACT_RECIPE_KEY), "discovery unlocks Contract recipe");

			CraftingInput input = CraftingInput.of(
					2,
					1,
					List.of(new ItemStack(Items.PAPER), new ItemStack(Items.INK_SAC))
			);
			RecipeHolder<CraftingRecipe> recipe = server.getRecipeManager()
					.getRecipeFor(RecipeType.CRAFTING, input, level)
					.orElseThrow(() -> context.assertionException("paper plus ink did not match a crafting recipe"));
			context.assertValueEqual(recipe.id(), CONTRACT_RECIPE_KEY, "paper plus ink matches exact Contract recipe");
			ItemStack crafted = recipe.value().assemble(input);
			context.assertTrue(
					crafted.getItem() == ModItems.CURSED_UNPAID_INTERNSHIP_CONTRACT,
					"recipe produces the registered Contract"
			);
			context.assertValueEqual(crafted.getCount(), 1, "recipe produces one Contract");

			List<Component> tooltip = new ArrayList<>();
			ModItems.CURSED_UNPAID_INTERNSHIP_CONTRACT.appendHoverText(
					crafted,
					Item.TooltipContext.of(level),
					TooltipDisplay.DEFAULT,
					tooltip::add,
					TooltipFlag.NORMAL
			);
			context.assertValueEqual(
					tooltip.stream().map(FoundationGameTests::translationKey).toList(),
					CONTRACT_TOOLTIP_KEYS,
					"Contract tooltip has exactly three localized instructions"
			);

			owner.setItemInHand(InteractionHand.MAIN_HAND, crafted);
			owner.clearRecordedSystemMessages();
			InteractionResult wrongUse = useBlock(level, owner, wrongTarget);
			context.assertValueEqual(wrongUse, InteractionResult.SUCCESS_SERVER, "wrong target is handled on the server");
			context.assertValueEqual(crafted.getCount(), 1, "wrong target consumes no Contract");
			context.assertValueEqual(
					owner.recordedSystemMessageKeys(),
					List.of(WRONG_TARGET_KEY),
					"wrong target sends one localized lectern direction"
			);
			context.assertFalse(
					CampaignSavedData.get(level).player(DISCOVERY_OWNER_UUID).isPresent(),
					"wrong target writes no campaign state"
			);
			context.assertValueEqual(
					level.getEntities(ModEntities.PROFESSOR, professor -> professor.ownerUuid().equals(DISCOVERY_OWNER_UUID)).size(),
					0,
					"wrong target spawns no Professor"
			);
			level.setBlock(wrongTarget, Blocks.AIR.defaultBlockState(), 3);
			ownerConnection.close();
			ownerConnection = null;
			context.succeed();
		}
		finally {
			if (ownerConnection != null) {
				ownerConnection.close();
			}
			level.setBlock(wrongTarget, Blocks.AIR.defaultBlockState(), 3);
		}
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
			ItemStack ownerContracts = craftContract(level, context);
			ownerContracts.setCount(2);
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
			LectureEncounterManager.PresentationSnapshot startedPresentation = LectureEncounterManager.presentation(encounterUuid)
					.orElseThrow(() -> context.assertionException("missing owner presentation"));
			context.assertValueEqual(startedPresentation.participantUuids(), Set.of(OWNER_UUID), "one owner boss-bar participant");
			context.assertValueEqual(
					translationKey(startedPresentation.bossBarName()),
					"bossbar.developers_hell.professor.act",
					"translated boss-bar name"
			);
			context.assertValueEqual(
					translationKey(startedPresentation.currentInstruction()),
					"actionbar.developers_hell.lecture.slide_countdown",
					"translated initial action-bar instruction"
			);
			context.assertValueEqual(startedPresentation.actionBarUpdates(), 1, "one initial action-bar update");
			context.assertValueEqual(startedPresentation.messageGroups(), 1, "one transition-scoped start message group");
			context.assertValueEqual(startedPresentation.transitionSounds(), 1, "one transition-scoped start sound");
			context.assertTrue(
					startedPresentation.emittedParticles() <= LectureRules.standard().maxParticlesPerEncounter(),
					"initial particles stay under the encounter cap"
			);
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
					LectureEncounterManager.PresentationSnapshot openPresentation = LectureEncounterManager.presentation(encounterUuid)
							.orElseThrow(() -> context.assertionException("missing open-window presentation"));
					context.assertValueEqual(openPresentation.participantUuids(), Set.of(OWNER_UUID), "inactive player sees no boss bar");
					context.assertValueEqual(
							translationKey(openPresentation.currentInstruction()),
							"actionbar.developers_hell.lecture.projector_cooldown",
							"translated open-window action instruction"
					);
					context.assertValueEqual(openPresentation.actionBarUpdates(), 6, "whole-second action updates plus transition");
					context.assertValueEqual(openPresentation.messageGroups(), 1, "chat does not repeat during countdown");
					context.assertValueEqual(openPresentation.transitionSounds(), 2, "sound emits only at phase transitions");
					context.assertTrue(openPresentation.emittedParticles() > 0, "telegraph emits a bounded particle cue");
					context.assertTrue(
							openPresentation.emittedParticles() <= LectureRules.standard().maxParticlesPerEncounter(),
							"telegraph particles stay under the encounter cap"
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
					context.assertFalse(LectureEncounterManager.presentation(encounterUuid).isPresent(), "victory removes presentation state");
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
		return useBlock(level, player, desk);
	}

	private static InteractionResult useBlock(ServerLevel level, ServerPlayer player, BlockPos target) {
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
		return UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
	}

	private static ItemStack craftContract(ServerLevel level, GameTestHelper context) {
		CraftingInput input = CraftingInput.of(
				2,
				1,
				List.of(new ItemStack(Items.PAPER), new ItemStack(Items.INK_SAC))
		);
		RecipeHolder<CraftingRecipe> recipe = level.getServer().getRecipeManager()
				.getRecipeFor(RecipeType.CRAFTING, input, level)
				.orElseThrow(() -> context.assertionException("paper plus ink did not match the Contract recipe"));
		context.assertValueEqual(recipe.id(), CONTRACT_RECIPE_KEY, "valid tracer uses exact crafted Contract recipe");
		ItemStack crafted = recipe.value().assemble(input);
		context.assertTrue(
				crafted.getItem() == ModItems.CURSED_UNPAID_INTERNSHIP_CONTRACT,
				"valid tracer starts from crafted Contract output"
		);
		return crafted;
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
			return recordedSystemMessages.stream()
					.map(FoundationGameTests::translationKey)
					.toList();
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
