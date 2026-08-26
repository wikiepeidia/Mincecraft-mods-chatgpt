package dev.developershell.gametest;

import com.mojang.authlib.GameProfile;
import dev.developershell.DevelopersHell;
import dev.developershell.campaign.CampaignSavedData;
import dev.developershell.campaign.CampaignService;
import dev.developershell.campaign.PlayerCampaignState;
import dev.developershell.entity.HomeworkAddEntity;
import dev.developershell.lecture.LectureAct;
import dev.developershell.lecture.LectureEncounterManager;
import dev.developershell.lecture.LectureGeometry;
import dev.developershell.lecture.LecturePresentation;
import dev.developershell.lecture.LectureRules;
import dev.developershell.lecture.LectureStateMachine;
import dev.developershell.registry.ModEntities;
import dev.developershell.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Real server-runtime proof for Professor Infinite Slides and its bounded Homework consequence. */
public final class LectureBossGameTests implements CustomTestMethodInvoker {
	private static final UUID OWNER_UUID = UUID.fromString("c0de0000-0000-4000-8000-000000000210");
	private static final UUID OTHER_UUID = UUID.fromString("c0de0000-0000-4000-8000-000000000211");
	private static final UUID THREE_ACT_OWNER_UUID = UUID.fromString("c0de0000-0000-4000-8000-000000000213");
	private static final UUID THREE_ACT_OTHER_UUID = UUID.fromString("c0de0000-0000-4000-8000-000000000214");

	@GameTest(maxTicks = 80, padding = 24)
	public void homeworkAddRegistryIdentityAndOrphanGuardAreStable(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		context.assertValueEqual(
				BuiltInRegistries.ENTITY_TYPE.getKey(ModEntities.HOMEWORK_ADD),
				DevelopersHell.id("homework_add"),
				"Homework add has one stable unconditional registry ID"
		);

		HomeworkAddEntity original = ModEntities.HOMEWORK_ADD.create(level, EntitySpawnReason.EVENT);
		context.assertTrue(original != null, "Homework add factory");
		UUID encounterUuid = UUID.fromString("c0de0000-0000-4000-8000-000000000212");
		original.bind(OWNER_UUID, encounterUuid);
		context.assertTrue(original.getMaxHealth() <= HomeworkAddEntity.MAX_HEALTH, "bounded add health");
		context.assertTrue(
				original.getAttributeValue(Attributes.ATTACK_DAMAGE) <= HomeworkAddEntity.ATTACK_DAMAGE,
				"bounded add attack damage"
		);

		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		original.saveWithoutId(output);
		HomeworkAddEntity restored = ModEntities.HOMEWORK_ADD.create(level, EntitySpawnReason.LOAD);
		context.assertTrue(restored != null, "Homework add reload factory");
		restored.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()));
		context.assertValueEqual(restored.ownerUuid(), OWNER_UUID, "owner identity survives save/load");
		context.assertValueEqual(restored.encounterUuid(), encounterUuid, "encounter identity survives save/load");
		context.assertTrue(restored.wasLoadedFromDisk(), "disk identity is marked ephemeral");

		restored.snapTo(context.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(2, 2, 2))));
		context.assertTrue(level.addFreshEntity(restored), "orphan can enter the load callback path");
		restored.tick();
		context.assertTrue(restored.isRemoved(), "disk-loaded Homework is rejected without a live runtime owner");
		context.succeed();
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void wrongQuizSpawnsOneOwnerScopedCleanupOwnedHomeworkAdd(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(new BlockPos(12, 2, 4));
		Direction facing = Direction.SOUTH;
		buildArena(level, desk, facing);
		ConnectedPlayer ownerConnection = null;
		ConnectedPlayer otherConnection = null;
		UUID encounterUuid = null;
		try {
			ownerConnection = createSurvivalPlayer(context, OWNER_UUID, "lecture-owner", new BlockPos(12, 2, 2));
			otherConnection = createSurvivalPlayer(context, OTHER_UUID, "lecture-other", new BlockPos(11, 2, 2));
			ServerPlayer owner = ownerConnection.player();
			ServerPlayer other = otherConnection.player();
			PlayerCampaignState active = startAttempt(context, level, owner, desk, facing);
			UUID activeEncounterUuid = active.encounterUuid();
			encounterUuid = activeEncounterUuid;

			LectureStateMachine.State slide = combatState(context, activeEncounterUuid);
			context.assertValueEqual(slide.act(), LectureAct.SLIDE_DECK, "first act");
			placeAtLocal(owner, desk, facing, 9, laneCenter(slide.safeLane()));
			tick(level, activeEncounterUuid, slide.deadlineTick());
			tick(level, activeEncounterUuid, slide.deadlineTick() + 1L);
			context.assertTrue(LectureEncounterManager.isVulnerabilityOpen(activeEncounterUuid), "safe lane opens Act 1 window");
			var professor = LectureEncounterManager.professor(activeEncounterUuid)
					.orElseThrow(() -> context.assertionException("missing Professor"));
			context.assertTrue(
					professor.hurtServer(level, owner.damageSources().playerAttack(owner), professor.getMaxHealth()),
					"owner closes Act 1 threshold"
			);
			tick(level, activeEncounterUuid, slide.deadlineTick() + 2L);
			LectureStateMachine.State recovery = combatState(context, activeEncounterUuid);
			context.assertValueEqual(recovery.stage(), LectureStateMachine.Stage.RECOVERY, "threshold starts recovery");
			tick(level, activeEncounterUuid, recovery.deadlineTick());

			LectureStateMachine.State quiz = combatState(context, activeEncounterUuid);
			context.assertValueEqual(quiz.act(), LectureAct.SURPRISE_QUIZ, "second act");
			placeAtLocal(owner, desk, facing, 3, 0);
			tick(level, activeEncounterUuid, quiz.deadlineTick());
			HomeworkAddEntity add = LectureEncounterManager.homeworkAdd(activeEncounterUuid)
					.orElseThrow(() -> context.assertionException("wrong/no quiz must spawn one Homework add"));
			context.assertTrue(add.isBoundTo(OWNER_UUID, activeEncounterUuid), "add binds exact owner and encounter");
			context.assertValueEqual(
					level.getEntities(ModEntities.HOMEWORK_ADD, entity -> entity.isBoundTo(OWNER_UUID, activeEncounterUuid)).size(),
					1,
					"one active Homework add cap"
			);
			add.setTarget(other);
			context.assertFalse(add.getTarget() == other, "wrong player cannot become Homework target");
			add.setTarget(owner);
			context.assertValueEqual(add.getTarget(), owner, "bound owner is the only target");

			tick(level, activeEncounterUuid, quiz.deadlineTick() + 1L);
			LectureStateMachine.State quizRecovery = combatState(context, activeEncounterUuid);
			tick(level, activeEncounterUuid, quizRecovery.deadlineTick());
			LectureStateMachine.State repeatedQuiz = combatState(context, activeEncounterUuid);
			placeAtLocal(owner, desk, facing, 3, 0);
			tick(level, activeEncounterUuid, repeatedQuiz.deadlineTick());
			context.assertValueEqual(
					level.getEntities(ModEntities.HOMEWORK_ADD, entity -> entity.isBoundTo(OWNER_UUID, activeEncounterUuid)).size(),
					1,
					"a second wrong resolution cannot exceed one active add"
			);

			LectureEncounterManager.cleanup(activeEncounterUuid);
			context.assertTrue(add.isRemoved(), "terminal cleanup removes Homework immediately");
			context.assertFalse(LectureEncounterManager.homeworkAdd(activeEncounterUuid).isPresent(), "cleanup clears add schedule");
			context.assertFalse(LectureEncounterManager.presentation(activeEncounterUuid).isPresent(), "cleanup clears presentation");
			context.succeed();
		}
		finally {
			if (encounterUuid != null) {
				LectureEncounterManager.cleanup(encounterUuid);
			}
			if (otherConnection != null) {
				otherConnection.close();
			}
			if (ownerConnection != null) {
				ownerConnection.close();
			}
			clearArena(level, desk, facing);
		}
	}

	@GameTest(maxTicks = 140, padding = 24)
	public void threeActsUseRedundantCuesOwnerDamageAndAtomicVictoryCleanup(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos desk = context.absolutePos(new BlockPos(12, 2, 4));
		Direction facing = Direction.SOUTH;
		buildArena(level, desk, facing);
		ConnectedPlayer ownerConnection = null;
		ConnectedPlayer otherConnection = null;
		UUID encounterUuid = null;
		try {
			ownerConnection = createSurvivalPlayer(
					context, THREE_ACT_OWNER_UUID, "three-act-owner", new BlockPos(12, 2, 2));
			otherConnection = createSurvivalPlayer(
					context, THREE_ACT_OTHER_UUID, "three-act-other", new BlockPos(11, 2, 2));
			ServerPlayer owner = ownerConnection.player();
			ServerPlayer other = otherConnection.player();
			PlayerCampaignState active = startAttempt(context, level, owner, desk, facing);
			UUID activeEncounterUuid = active.encounterUuid();
			encounterUuid = activeEncounterUuid;

			LectureStateMachine.State slide = combatState(context, activeEncounterUuid);
			LectureEncounterManager.PresentationSnapshot slideCue = presentation(context, activeEncounterUuid);
			assertPresentationIdentity(
					context,
					slideCue,
					LectureAct.SLIDE_DECK,
					LectureStateMachine.Stage.WIND_UP,
					slide.safeLane().name(),
					"DIAMOND",
					"X",
					"slide"
			);
			context.assertValueEqual(slideCue.participantUuids(), Set.of(THREE_ACT_OWNER_UUID),
					"boss bar is participant-only");
			context.assertValueEqual(translationKey(slideCue.bossBarName()),
					"bossbar.developers_hell.professor.act", "translated Act 1 boss name");
			int initialActions = slideCue.actionBarUpdates();
			tick(level, activeEncounterUuid, slide.phaseStartedTick() + 1L);
			context.assertValueEqual(presentation(context, activeEncounterUuid).actionBarUpdates(), initialActions,
					"action line does not update faster than whole seconds");

			LectureGeometry.Lane unsafeLane = slide.safeLane() == LectureGeometry.Lane.LEFT
					? LectureGeometry.Lane.RIGHT
					: LectureGeometry.Lane.LEFT;
			float healthBeforeMiss = owner.getHealth();
			placeAtLocal(owner, desk, facing, 9, laneCenter(unsafeLane));
			tick(level, activeEncounterUuid, slide.deadlineTick());
			context.assertTrue(owner.getHealth() < healthBeforeMiss && owner.getHealth() >= 1.0F,
					"unsafe Slide applies bounded nonlethal server damage");
			tick(level, activeEncounterUuid, slide.deadlineTick() + 1L);
			context.assertTrue(LectureEncounterManager.isVulnerabilityOpen(activeEncounterUuid),
					"Slide miss still exposes an explicit recovery window");
			var professor = LectureEncounterManager.professor(activeEncounterUuid)
					.orElseThrow(() -> context.assertionException("missing Professor"));
			float professorHealth = professor.getHealth();
			context.assertFalse(
					professor.hurtServer(level, other.damageSources().playerAttack(other), professor.getMaxHealth()),
					"wrong owner cannot damage Professor"
			);
			context.assertValueEqual(professor.getHealth(), professorHealth, "wrong-owner damage is a no-op");
			context.assertTrue(
					professor.hurtServer(level, owner.damageSources().playerAttack(owner), professor.getMaxHealth()),
					"owner closes Act 1 floor"
			);
			tick(level, activeEncounterUuid, slide.deadlineTick() + 2L);
			LectureStateMachine.State slideRecovery = combatState(context, activeEncounterUuid);
			context.assertValueEqual(slideRecovery.bossHealth(), 80, "Act 1 floor remains 80");
			tick(level, activeEncounterUuid, slideRecovery.deadlineTick());

			LectureStateMachine.State quiz = combatState(context, activeEncounterUuid);
			LectureEncounterManager.PresentationSnapshot quizCue = presentation(context, activeEncounterUuid);
			assertPresentationIdentity(
					context,
					quizCue,
					LectureAct.SURPRISE_QUIZ,
					LectureStateMachine.Stage.WIND_UP,
					quiz.correctPad().name(),
					"SQUARE",
					"CIRCLE",
					"quiz"
			);
			context.assertTrue(quizCue.geometrySignature().contains("DIAMOND"), "quiz includes C diamond identity");
			context.assertTrue(quizCue.messageGroups() >= 2, "quiz prompt/options emit one deduplicated message group");
			placeAtLocal(owner, desk, facing, 9, quiz.correctPad().rightAnchor());
			tick(level, activeEncounterUuid, quiz.deadlineTick());
			context.assertFalse(LectureEncounterManager.homeworkAdd(activeEncounterUuid).isPresent(),
					"correct answer spawns no Homework add");
			tick(level, activeEncounterUuid, quiz.deadlineTick() + 1L);
			context.assertTrue(LectureEncounterManager.isVulnerabilityOpen(activeEncounterUuid),
					"correct answer opens Act 2 window");
			context.assertTrue(
					professor.hurtServer(level, owner.damageSources().playerAttack(owner), professor.getMaxHealth()),
					"owner closes Act 2 floor"
			);
			tick(level, activeEncounterUuid, quiz.deadlineTick() + 2L);
			LectureStateMachine.State quizRecovery = combatState(context, activeEncounterUuid);
			context.assertValueEqual(quizRecovery.bossHealth(), 40, "Act 2 floor remains 40");
			tick(level, activeEncounterUuid, quizRecovery.deadlineTick());

			for (int expectedAbsence = 1; expectedAbsence <= 3; expectedAbsence++) {
				LectureStateMachine.State attendance = combatState(context, activeEncounterUuid);
				LectureEncounterManager.PresentationSnapshot attendanceCue = presentation(context, activeEncounterUuid);
				assertPresentationIdentity(
						context,
						attendanceCue,
						LectureAct.ATTENDANCE_CHECK,
						LectureStateMachine.Stage.WIND_UP,
						attendance.attendanceQuadrant().name(),
						"RING",
						"CENTER",
						"attendance"
				);
				placeAtLocal(owner, desk, facing, 9, 0);
				float beforeAbsence = owner.getHealth();
				tick(level, activeEncounterUuid, attendance.deadlineTick());
				LectureStateMachine.State absent = combatState(context, activeEncounterUuid);
				context.assertValueEqual(absent.absenceCount(), expectedAbsence, "bounded absence count");
				if (expectedAbsence == 3) {
					context.assertTrue(owner.getHealth() < beforeAbsence && owner.getHealth() >= 1.0F,
							"third absence applies nonlethal detention once");
				}
				tick(level, activeEncounterUuid, attendance.deadlineTick() + 1L);
				LectureStateMachine.State absenceRecovery = combatState(context, activeEncounterUuid);
				context.assertValueEqual(absenceRecovery.stage(), LectureStateMachine.Stage.RECOVERY,
						"absence cannot manufacture a damage window");
				tick(level, activeEncounterUuid, absenceRecovery.deadlineTick());
			}

			LectureStateMachine.State finalAttendance = combatState(context, activeEncounterUuid);
			LectureGeometry.LocalPosition ringCenter = LectureGeometry.attendanceCenter(
					finalAttendance.attendanceQuadrant());
			placeAtLocal(
					owner,
					desk,
					facing,
					(int) ringCenter.forwardOffset(),
					(int) ringCenter.rightOffset()
			);
			tick(level, activeEncounterUuid, finalAttendance.deadlineTick());
			tick(level, activeEncounterUuid, finalAttendance.deadlineTick() + 1L);
			context.assertTrue(LectureEncounterManager.isVulnerabilityOpen(activeEncounterUuid),
					"PRESENT opens final damage window");
			int sheetBefore = countItem(owner, ModItems.ATTENDANCE_SHEET);
			int remoteBefore = countItem(owner, ModItems.INFINITE_SLIDES_REMOTE);
			context.assertTrue(
					professor.hurtServer(level, owner.damageSources().playerAttack(owner), professor.getMaxHealth()),
					"final owner hit commits victory"
			);
			PlayerCampaignState passed = CampaignSavedData.get(level).player(THREE_ACT_OWNER_UUID)
					.orElseThrow(() -> context.assertionException("missing passed state"));
			context.assertValueEqual(passed.status(), PlayerCampaignState.LectureStatus.PASSED, "three-act victory status");
			context.assertValueEqual(countItem(owner, ModItems.ATTENDANCE_SHEET), sheetBefore + 1, "one Sheet reward");
			context.assertValueEqual(countItem(owner, ModItems.INFINITE_SLIDES_REMOTE), remoteBefore + 1, "one Remote reward");
			context.assertFalse(CampaignService.victory(level, THREE_ACT_OWNER_UUID, activeEncounterUuid),
					"replayed victory is a no-op");
			context.assertValueEqual(countItem(owner, ModItems.ATTENDANCE_SHEET), sheetBefore + 1,
					"victory replay grants no Sheet");
			context.assertValueEqual(LectureEncounterManager.activeRuntimeCount(), 0, "victory removes runtime");
			context.assertFalse(LectureEncounterManager.presentation(activeEncounterUuid).isPresent(),
					"victory removes bar/action/emitter/sound schedule");
			context.assertFalse(LectureEncounterManager.homeworkAdd(activeEncounterUuid).isPresent(),
					"victory removes Homework schedule");
			context.assertValueEqual(level.getEntities(ModEntities.PROFESSOR, entity -> true).size(), 0,
					"victory removes Professor");
			context.succeed();
		}
		finally {
			if (encounterUuid != null) {
				LectureEncounterManager.cleanup(encounterUuid);
			}
			if (otherConnection != null) {
				otherConnection.close();
			}
			if (ownerConnection != null) {
				ownerConnection.close();
			}
			clearArena(level, desk, facing);
		}
	}

	@GameTest
	public void reducedEffectsKeepSemanticShapesWithLowerBoundedCadence(GameTestHelper context) {
		LectureRules rules = LectureRules.standard();
		UUID encounterUuid = UUID.fromString("c0de0000-0000-4000-8000-000000000215");
		LectureStateMachine.State normal = LectureStateMachine.start(
				encounterUuid, THREE_ACT_OWNER_UUID, 1, 0L, rules, false).state();
		LectureStateMachine.State reduced = LectureStateMachine.start(
				encounterUuid, THREE_ACT_OWNER_UUID, 1, 0L, rules, true).state();
		context.assertValueEqual(normal.safeLane(), reduced.safeLane(), "reduced effects preserves deterministic target");
		context.assertValueEqual(
				LecturePresentation.geometrySignature(normal),
				LecturePresentation.geometrySignature(reduced),
				"reduced effects preserves safe/danger shape meaning"
		);
		context.assertValueEqual(normal.deadlineTick(), reduced.deadlineTick(), "reduced effects preserves timing");
		context.assertTrue(LecturePresentation.refreshTicks(rules, false) >= 4,
				"normal geometry refresh is no faster than four ticks");
		context.assertTrue(LecturePresentation.refreshTicks(rules, true) >= 10,
				"reduced geometry refresh is no faster than ten ticks");
		context.assertValueEqual(reduced.reducedEffects(), true, "reduced density flag remains explicit");
		context.succeed();
	}

	private static LectureEncounterManager.PresentationSnapshot presentation(
			GameTestHelper context,
			UUID encounterUuid
	) {
		return LectureEncounterManager.presentation(encounterUuid)
				.orElseThrow(() -> context.assertionException("missing presentation for %s", encounterUuid));
	}

	private static void assertPresentationIdentity(
			GameTestHelper context,
			LectureEncounterManager.PresentationSnapshot snapshot,
			LectureAct act,
			LectureStateMachine.Stage stage,
			String target,
			String firstShape,
			String secondShape,
			String cueIdentity
	) {
		context.assertValueEqual(snapshot.act(), act, "presentation act");
		context.assertValueEqual(snapshot.stage(), stage, "presentation stage");
		context.assertValueEqual(snapshot.targetName(), target, "recorded geometry target");
		context.assertTrue(snapshot.geometrySignature().contains(firstShape), "first non-color shape identity");
		context.assertTrue(snapshot.geometrySignature().contains(secondShape), "second non-color shape identity");
		context.assertValueEqual(snapshot.cueIdentity(), cueIdentity, "distinct vanilla cue identity");
		context.assertTrue(snapshot.currentInstruction() != null, "text response remains present");
		context.assertTrue(snapshot.transitionSounds() <= LectureRules.standard().maxTransitionSoundsPerEncounter(),
				"transition sounds remain capped");
		context.assertTrue(snapshot.emittedParticles() <= LectureRules.standard().maxParticlesPerEncounter(),
				"particles remain capped");
	}

	private static PlayerCampaignState startAttempt(
			GameTestHelper context,
			ServerLevel level,
			ServerPlayer owner,
			BlockPos desk,
			Direction facing
	) {
		ItemStack contract = new ItemStack(ModItems.CURSED_UNPAID_INTERNSHIP_CONTRACT);
		context.assertTrue(CampaignService.start(owner, desk, facing, contract), "real Contract service starts encounter");
		context.assertTrue(contract.isEmpty(), "accepted Contract is consumed last");
		return CampaignSavedData.get(level).player(owner.getUUID())
				.orElseThrow(() -> context.assertionException("missing active campaign state"));
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
