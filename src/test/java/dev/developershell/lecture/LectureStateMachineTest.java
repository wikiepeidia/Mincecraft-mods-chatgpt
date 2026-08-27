package dev.developershell.lecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class LectureStateMachineTest {
	private static final UUID ENCOUNTER = UUID.fromString("c0de0000-0000-4000-8000-000000000901");
	private static final UUID OWNER = UUID.fromString("c0de0000-0000-4000-8000-000000000902");
	private static final LectureRules RULES = LectureRules.standard();

	@Test
	void standardRulesFreezeExactThreeActTimingThresholdsAndBounds() {
		assertEquals(120, RULES.bossMaxHealth());
		assertEquals(100, LectureAct.SLIDE_DECK.windUpTicks(RULES));
		assertEquals(160, LectureAct.SURPRISE_QUIZ.windUpTicks(RULES));
		assertEquals(120, LectureAct.ATTENDANCE_CHECK.windUpTicks(RULES));
		assertEquals(80, RULES.vulnerabilityTicks());
		assertEquals(60, RULES.recoveryTicks());
		assertEquals(List.of(80, 40, 0),
				List.of(
						LectureAct.SLIDE_DECK.healthThreshold(),
						LectureAct.SURPRISE_QUIZ.healthThreshold(),
						LectureAct.ATTENDANCE_CHECK.healthThreshold()
				));
		assertEquals(1, RULES.maxHomeworkIntentsPerResolve());
		assertTrue(RULES.slideDeckMissDamage() > 0);
		assertTrue(RULES.detentionDamage() > 0);
		assertTrue(RULES.maxParticleBurstsPerEncounter() <= 40);
		assertTrue(RULES.maxTransitionSoundsPerEncounter() <= 12);
	}

	@Test
	void slideDeckResolvesExactlyAtTick100ForEveryLaneAndOpensTickBoundedWindow() {
		Set<LectureGeometry.Lane> seen = EnumSet.noneOf(LectureGeometry.Lane.class);
		for (int sample = 0; sample < 96 && seen.size() < LectureGeometry.Lane.values().length; sample++) {
			UUID encounter = uuidFor(sample);
			LectureStateMachine.Output started = LectureStateMachine.start(
					encounter, OWNER, 2, 1_000L, RULES, false
			);
			LectureStateMachine.State windUp = started.state();
			LectureGeometry.Lane lane = windUp.safeLane();
			seen.add(lane);
			String seedContext = "seed=" + windUp.choiceSeed() + ", lane=" + lane;

			LectureStateMachine.Output before = LectureStateMachine.step(
					windUp,
					new LectureStateMachine.Input.Tick(
							1_099L,
							laneCenter(lane),
							20
					)
			);
			assertEquals(LectureStateMachine.Stage.WIND_UP, before.state().stage(), seedContext);
			assertTrue(before.intents().isEmpty(), seedContext);

			LectureStateMachine.Output resolved = LectureStateMachine.step(
					windUp,
					new LectureStateMachine.Input.Tick(
							1_100L,
							laneCenter(lane),
							20
					)
			);
			assertEquals(LectureStateMachine.Stage.RESOLVE, resolved.state().stage(), seedContext);
			assertEquals(LectureStateMachine.Resolution.SAFE, resolved.state().resolution(), seedContext);
			assertTrue(resolved.intents().isEmpty(), seedContext);

			LectureStateMachine.Output opened = settle(resolved);
			assertEquals(LectureStateMachine.Stage.VULNERABLE, opened.state().stage(), seedContext);
			assertEquals(1_180L, opened.state().deadlineTick(), seedContext);
			LectureStateMachine.Intent.Vulnerability window = onlyIntent(
					opened,
					LectureStateMachine.Intent.Vulnerability.class
			);
			assertTrue(window.open(), seedContext);
			assertEquals(80, window.thresholdHealth(), seedContext);
			assertEquals(80, window.durationTicks(), seedContext);
		}
		assertEquals(EnumSet.allOf(LectureGeometry.Lane.class), seen);
	}

	@Test
	void slideDeckMissIsBoundedAndReducedEffectsNeverChangesCollisionMeaning() {
		LectureStateMachine.State normal = LectureStateMachine.start(ENCOUNTER, OWNER, 1, 0L, RULES, false).state();
		LectureStateMachine.State reduced = LectureStateMachine.start(ENCOUNTER, OWNER, 1, 0L, RULES, true).state();
		assertEquals(normal.choiceSeed(), reduced.choiceSeed());
		assertEquals(normal.safeLane(), reduced.safeLane());
		assertNotEquals(normal.reducedEffects(), reduced.reducedEffects());

		LectureGeometry.Lane unsafeLane = EnumSet.allOf(LectureGeometry.Lane.class).stream()
				.filter(lane -> lane != normal.safeLane())
				.findFirst()
				.orElseThrow();
		LectureStateMachine.Input.Tick miss = new LectureStateMachine.Input.Tick(100L, laneCenter(unsafeLane), 4);
		LectureStateMachine.Output normalResolved = LectureStateMachine.step(normal, miss);
		LectureStateMachine.Output reducedResolved = LectureStateMachine.step(reduced, miss);
		assertEquals(normalResolved.state().resolution(), reducedResolved.state().resolution());
		assertEquals(LectureStateMachine.Resolution.MISSED, normalResolved.state().resolution());
		LectureStateMachine.Intent.DirectDamage damage = onlyIntent(
				normalResolved,
				LectureStateMachine.Intent.DirectDamage.class
		);
		assertEquals(3, damage.amount(), "bounded by owner health - 1");
		assertEquals(damage, onlyIntent(reducedResolved, LectureStateMachine.Intent.DirectDamage.class));

		LectureStateMachine.Intent.Telegraph normalTelegraph = onlyIntent(
				LectureStateMachine.start(ENCOUNTER, OWNER, 1, 0L, RULES, false),
				LectureStateMachine.Intent.Telegraph.class
		);
		LectureStateMachine.Intent.Telegraph reducedTelegraph = onlyIntent(
				LectureStateMachine.start(ENCOUNTER, OWNER, 1, 0L, RULES, true),
				LectureStateMachine.Intent.Telegraph.class
		);
		assertEquals(normalTelegraph.choiceSeed(), reducedTelegraph.choiceSeed());
		assertEquals(normalTelegraph.targetName(), reducedTelegraph.targetName());
		assertNotEquals(normalTelegraph.density(), reducedTelegraph.density());
	}

	@Test
	void surpriseQuizCoversEveryPadAndWrongOrNoAnswerEmitsAtMostOneHomework() {
		Set<LectureGeometry.QuizPad> seen = EnumSet.noneOf(LectureGeometry.QuizPad.class);
		for (int sample = 0; sample < 96 && seen.size() < LectureGeometry.QuizPad.values().length; sample++) {
			LectureStateMachine.State quiz = startAct(
					uuidFor(200 + sample), LectureAct.SURPRISE_QUIZ, 0, 0, 80, false
			);
			seen.add(quiz.correctPad());
			String seedContext = "seed=" + quiz.choiceSeed() + ", pad=" + quiz.correctPad();

			LectureStateMachine.Output correct = LectureStateMachine.step(
					quiz,
					new LectureStateMachine.Input.Tick(160L, padCenter(quiz.correctPad()), 20)
			);
			assertEquals(LectureStateMachine.Resolution.CORRECT, correct.state().resolution(), seedContext);
			assertTrue(correct.intents().stream().noneMatch(LectureStateMachine.Intent.Homework.class::isInstance),
					seedContext);
			assertEquals(LectureStateMachine.Stage.VULNERABLE, settle(correct).state().stage(), seedContext);

			LectureGeometry.QuizPad wrongPad = EnumSet.allOf(LectureGeometry.QuizPad.class).stream()
					.filter(pad -> pad != quiz.correctPad())
					.findFirst()
					.orElseThrow();
			LectureStateMachine.Output wrong = LectureStateMachine.step(
					quiz,
					new LectureStateMachine.Input.Tick(160L, padCenter(wrongPad), 20)
			);
			assertEquals(LectureStateMachine.Resolution.WRONG, wrong.state().resolution(), seedContext);
			assertEquals(1L, wrong.intents().stream().filter(LectureStateMachine.Intent.Homework.class::isInstance).count(),
					seedContext);
			assertEquals(1, onlyIntent(wrong, LectureStateMachine.Intent.Homework.class).count(), seedContext);
			assertEquals(LectureStateMachine.Stage.RECOVERY, settle(wrong).state().stage(), seedContext);

			LectureStateMachine.Output noAnswer = LectureStateMachine.step(
					quiz,
					new LectureStateMachine.Input.Tick(
							160L,
							new LectureGeometry.LocalPosition(2.0D, 0.0D),
							20
					)
			);
			assertEquals(LectureStateMachine.Resolution.NO_ANSWER, noAnswer.state().resolution(), seedContext);
			assertEquals(1L, noAnswer.intents().stream().filter(LectureStateMachine.Intent.Homework.class::isInstance).count(),
					seedContext);
		}
		assertEquals(EnumSet.allOf(LectureGeometry.QuizPad.class), seen);
	}

	@Test
	void attendanceCoversEveryQuadrantCountsThreeAbsencesAndDetentionIsNonlethal() {
		Set<LectureGeometry.AttendanceQuadrant> seen = EnumSet.noneOf(LectureGeometry.AttendanceQuadrant.class);
		LectureStateMachine.State state = startAct(ENCOUNTER, LectureAct.ATTENDANCE_CHECK, 0, 0, 40, false);
		for (int absence = 1; absence <= 3; absence++) {
			seen.add(state.attendanceQuadrant());
			String seedContext = "seed=" + state.choiceSeed() + ", absence=" + absence;
			LectureStateMachine.Output before = LectureStateMachine.step(
					state,
					new LectureStateMachine.Input.Tick(state.deadlineTick() - 1L, LectureGeometry.attendanceCenter(state.attendanceQuadrant()), 6)
			);
			assertEquals(LectureStateMachine.Stage.WIND_UP, before.state().stage(), seedContext);

			LectureStateMachine.Output absent = LectureStateMachine.step(
					state,
					new LectureStateMachine.Input.Tick(
							state.deadlineTick(),
							new LectureGeometry.LocalPosition(9.0D, 0.0D),
							6
					)
			);
			assertEquals(absence == 3 ? LectureStateMachine.Resolution.DETENTION : LectureStateMachine.Resolution.ABSENT,
					absent.state().resolution(), seedContext);
			assertEquals(absence, absent.state().absenceCount(), seedContext);
			LectureStateMachine.Intent.Attendance attendance = onlyIntent(
					absent,
					LectureStateMachine.Intent.Attendance.class
			);
			assertEquals(absence, attendance.absenceCount(), seedContext);
			if (absence < 3) {
				state = finishRecovery(settle(absent));
			}
			else {
				LectureStateMachine.Intent.DirectDamage detention = onlyIntent(
						absent,
						LectureStateMachine.Intent.DirectDamage.class
				);
				assertEquals(Math.min(RULES.detentionDamage(), 5), detention.amount(),
						"detention clamps to health - 1; " + seedContext);
				assertEquals(LectureStateMachine.Consequence.DETENTION, detention.consequence(), seedContext);
				LectureStateMachine.State retry = finishRecovery(settle(absent));
				assertEquals(LectureStateMachine.Stage.WIND_UP, retry.stage(), seedContext);
				assertEquals(3, retry.absenceCount(), seedContext);
				LectureStateMachine.Output present = LectureStateMachine.step(
						retry,
						new LectureStateMachine.Input.Tick(
								retry.deadlineTick(),
								LectureGeometry.attendanceCenter(retry.attendanceQuadrant()),
								6
						)
				);
				assertEquals(LectureStateMachine.Resolution.PRESENT, present.state().resolution(), seedContext);
				assertEquals(LectureStateMachine.Stage.VULNERABLE, settle(present).state().stage(), seedContext);
			}
		}

		for (int sample = 0; sample < 96 && seen.size() < LectureGeometry.AttendanceQuadrant.values().length; sample++) {
			LectureStateMachine.State candidate = startAct(
					uuidFor(400 + sample), LectureAct.ATTENDANCE_CHECK, sample, 0, 40, false
			);
			seen.add(candidate.attendanceQuadrant());
		}
		assertEquals(EnumSet.allOf(LectureGeometry.AttendanceQuadrant.class), seen);
	}

	@Test
	void thresholdsCannotBeSkippedAndRecoveryAdvancesActsDeterministically() {
		LectureStateMachine.State vulnerable = settle(LectureStateMachine.step(
				LectureStateMachine.start(ENCOUNTER, OWNER, 3, 0L, RULES, false).state(),
				new LectureStateMachine.Input.Tick(100L, laneCenter(
						LectureStateMachine.start(ENCOUNTER, OWNER, 3, 0L, RULES, false).state().safeLane()), 20)
		)).state();

		LectureStateMachine.Output hit = LectureStateMachine.step(
				vulnerable,
				new LectureStateMachine.Input.Damage(100L, OWNER, ENCOUNTER, 999)
		);
		assertEquals(80, hit.state().bossHealth());
		assertEquals(LectureStateMachine.Stage.RECOVERY, hit.state().stage());
		assertEquals(40, onlyIntent(hit, LectureStateMachine.Intent.DamageAccepted.class).amount());
		assertTrue(hit.state().advanceAfterRecovery());

		LectureStateMachine.State quiz = finishRecovery(hit);
		assertEquals(LectureAct.SURPRISE_QUIZ, quiz.act());
		assertEquals(80, quiz.bossHealth());
		assertEquals(LectureStateMachine.Stage.WIND_UP, quiz.stage());
		assertEquals(160L, quiz.deadlineTick() - quiz.phaseStartedTick());
	}

	@Test
	void minimumConfiguredHealthCanCompleteAllThreeActs() {
		LectureRules minimumRules = LectureRules.configured(
				100, 80, 20, 10, 6, 24, 8, 81, 4, 3, 160, 120
		);
		LectureStateMachine.State slide = LectureStateMachine.start(
				ENCOUNTER, OWNER, 1, 0L, minimumRules, false
		).state();
		LectureStateMachine.State slideWindow = settle(LectureStateMachine.step(
				slide,
				new LectureStateMachine.Input.Tick(slide.deadlineTick(), laneCenter(slide.safeLane()), 20)
		)).state();
		LectureStateMachine.Output slideHit = LectureStateMachine.step(
				slideWindow,
				new LectureStateMachine.Input.Damage(slideWindow.phaseStartedTick(), OWNER, ENCOUNTER, 999)
		);
		assertEquals(80, slideHit.state().bossHealth());

		LectureStateMachine.State quiz = finishRecovery(slideHit);
		LectureStateMachine.State quizWindow = settle(LectureStateMachine.step(
				quiz,
				new LectureStateMachine.Input.Tick(quiz.deadlineTick(), padCenter(quiz.correctPad()), 20)
		)).state();
		LectureStateMachine.Output quizHit = LectureStateMachine.step(
				quizWindow,
				new LectureStateMachine.Input.Damage(quizWindow.phaseStartedTick(), OWNER, ENCOUNTER, 999)
		);
		assertEquals(40, quizHit.state().bossHealth());

		LectureStateMachine.State attendance = finishRecovery(quizHit);
		LectureStateMachine.State attendanceWindow = settle(LectureStateMachine.step(
				attendance,
				new LectureStateMachine.Input.Tick(
						attendance.deadlineTick(),
						LectureGeometry.attendanceCenter(attendance.attendanceQuadrant()),
						20
				)
		)).state();
		LectureStateMachine.Output victory = LectureStateMachine.step(
				attendanceWindow,
				new LectureStateMachine.Input.Damage(attendanceWindow.phaseStartedTick(), OWNER, ENCOUNTER, 999)
		);
		assertEquals(LectureStateMachine.Stage.COMPLETE, victory.state().stage());
		assertEquals(0, victory.state().bossHealth());
		assertEquals(1L, victory.intents().stream().filter(LectureStateMachine.Intent.Victory.class::isInstance).count());
	}

	@Test
	void vulnerabilityUsesHalfOpenEightyTickBoundaryAndTimeoutRepeatsOnlyCurrentAct() {
		LectureStateMachine.State windUp = LectureStateMachine.start(ENCOUNTER, OWNER, 3, 0L, RULES, false).state();
		LectureStateMachine.State vulnerable = settle(LectureStateMachine.step(
				windUp,
				new LectureStateMachine.Input.Tick(100L, laneCenter(windUp.safeLane()), 20)
		)).state();
		assertEquals(100L, vulnerable.phaseStartedTick());
		assertEquals(180L, vulnerable.deadlineTick());
		assertEquals(LectureStateMachine.Stage.VULNERABLE, LectureStateMachine.step(
				vulnerable,
				new LectureStateMachine.Input.Tick(179L, laneCenter(windUp.safeLane()), 20)
		).state().stage());

		LectureStateMachine.Output timedOut = LectureStateMachine.step(
				vulnerable,
				new LectureStateMachine.Input.Tick(180L, laneCenter(windUp.safeLane()), 20)
		);
		assertEquals(LectureStateMachine.Stage.RECOVERY, timedOut.state().stage());
		assertFalse(timedOut.state().advanceAfterRecovery());
		assertFalse(onlyIntent(timedOut, LectureStateMachine.Intent.Vulnerability.class).open());
		LectureStateMachine.State repeated = finishRecovery(timedOut);
		assertEquals(LectureAct.SLIDE_DECK, repeated.act());
		assertEquals(1, repeated.actCycle());
		assertNotEquals(windUp.choiceSeed(), repeated.choiceSeed(),
				() -> "act cycle must contribute; seed0=" + windUp.choiceSeed() + ", seed1=" + repeated.choiceSeed());
	}

	@Test
	void detentionAtOneHealthEmitsNoDamageAndNeverRepeatsAfterThirdAbsence() {
		LectureStateMachine.State thirdAbsence = LectureStateMachine.testingState(
				ENCOUNTER, OWNER, 4, LectureAct.ATTENDANCE_CHECK, 2, 0, 2, 40, 0L, RULES, false
		);
		LectureStateMachine.Output detention = LectureStateMachine.step(
				thirdAbsence,
				new LectureStateMachine.Input.Tick(120L, new LectureGeometry.LocalPosition(9.0D, 0.0D), 1)
		);
		assertEquals(LectureStateMachine.Resolution.DETENTION, detention.state().resolution());
		assertTrue(detention.intents().stream().noneMatch(LectureStateMachine.Intent.DirectDamage.class::isInstance));

		LectureStateMachine.State afterDetention = finishRecovery(settle(detention));
		LectureStateMachine.Output fourthMiss = LectureStateMachine.step(
				afterDetention,
				new LectureStateMachine.Input.Tick(
						afterDetention.deadlineTick(),
						new LectureGeometry.LocalPosition(9.0D, 0.0D),
						20
				)
		);
		assertEquals(3, fourthMiss.state().absenceCount());
		assertEquals(LectureStateMachine.Resolution.ABSENT, fourthMiss.state().resolution());
		assertFalse(onlyIntent(fourthMiss, LectureStateMachine.Intent.Attendance.class).detention());
		assertTrue(fourthMiss.intents().stream().noneMatch(LectureStateMachine.Intent.DirectDamage.class::isInstance));
	}

	@Test
	void explicitSeedRepeatsAndIncludesAttemptCycleAndQuizIndex() {
		LectureStateMachine.State first = LectureStateMachine.start(ENCOUNTER, OWNER, 7, 500L, RULES, false).state();
		LectureStateMachine.State repeated = LectureStateMachine.start(ENCOUNTER, OWNER, 7, 500L, RULES, false).state();
		assertEquals(first, repeated, () -> "seed=" + first.choiceSeed());
		LectureStateMachine.State nextAttempt = LectureStateMachine.start(ENCOUNTER, OWNER, 8, 500L, RULES, false).state();
		assertNotEquals(first.choiceSeed(), nextAttempt.choiceSeed(),
				() -> "attempt must contribute; seed=" + first.choiceSeed());
		LectureStateMachine.State nextEncounter = LectureStateMachine.start(uuidFor(1), OWNER, 7, 500L, RULES, false).state();
		assertNotEquals(first.choiceSeed(), nextEncounter.choiceSeed(),
				() -> "encounter UUID must contribute; seed=" + first.choiceSeed());

		LectureStateMachine.State quiz0 = startAct(ENCOUNTER, LectureAct.SURPRISE_QUIZ, 4, 0, 80, false);
		LectureStateMachine.State quiz1 = LectureStateMachine.testingState(
				ENCOUNTER, OWNER, 7, LectureAct.SURPRISE_QUIZ, 4, 1, 0, 80, 0L, RULES, false
		);
		assertNotEquals(quiz0.choiceSeed(), quiz1.choiceSeed(),
				() -> "quiz index must contribute; seed0=" + quiz0.choiceSeed() + ", seed1=" + quiz1.choiceSeed());
		assertNotEquals(
				LectureStateMachine.choiceSeed(ENCOUNTER, 7, LectureAct.SLIDE_DECK, 0, 0),
				LectureStateMachine.choiceSeed(ENCOUNTER, 7, LectureAct.SURPRISE_QUIZ, 0, 0),
				"act identity must contribute to the explicit seed"
		);
	}

	@Test
	void entityDamageAdmissionRejectsClosedWrongOwnerStaleAndInvalidDamage() {
		UUID stranger = uuidFor(800);
		UUID staleEncounter = uuidFor(801);
		assertEquals(
				LectureStateMachine.DamageRejection.CLOSED_WINDOW,
				LectureStateMachine.admitEntityDamage(
						OWNER, ENCOUNTER, OWNER, ENCOUNTER, false, 120.0F, 80.0F, 5.0F
				).rejection()
		);
		assertEquals(
				LectureStateMachine.DamageRejection.WRONG_OWNER,
				LectureStateMachine.admitEntityDamage(
						OWNER, ENCOUNTER, stranger, ENCOUNTER, true, 120.0F, 80.0F, 5.0F
				).rejection()
		);
		assertEquals(
				LectureStateMachine.DamageRejection.STALE_ENCOUNTER,
				LectureStateMachine.admitEntityDamage(
						OWNER, ENCOUNTER, OWNER, staleEncounter, true, 120.0F, 80.0F, 5.0F
				).rejection()
		);
		for (float invalid : List.of(0.0F, -1.0F, Float.NaN, Float.POSITIVE_INFINITY)) {
			LectureStateMachine.DamageAdmission rejected = LectureStateMachine.admitEntityDamage(
					OWNER, ENCOUNTER, OWNER, ENCOUNTER, true, 120.0F, 80.0F, invalid
			);
			assertFalse(rejected.accepted(), () -> "invalid damage=" + invalid);
			assertEquals(LectureStateMachine.DamageRejection.INVALID_AMOUNT, rejected.rejection(),
					() -> "invalid damage=" + invalid);
		}
	}

	@Test
	void entityDamageAdmissionClampsAtEachActFloorWithoutSkipping() {
		assertAdmission(120.0F, 80.0F, 999.0F, 40.0F, 80.0F, false);
		assertAdmission(80.0F, 40.0F, 999.0F, 40.0F, 40.0F, false);
		assertAdmission(40.0F, 0.0F, 999.0F, 40.0F, 0.0F, true);

		LectureStateMachine.DamageAdmission partial = LectureStateMachine.admitEntityDamage(
				OWNER, ENCOUNTER, OWNER, ENCOUNTER, true, 120.0F, 80.0F, 7.5F
		);
		assertTrue(partial.accepted());
		assertEquals(7.5F, partial.acceptedDamage());
		assertEquals(112.5F, partial.projectedHealth());
		assertFalse(partial.closesWindow());
		assertFalse(partial.victoryIntent());
	}

	@Test
	void entityDamageAdmissionUsesTheExplicitActiveActFloor() {
		LectureStateMachine.DamageAdmission quizAdmission = LectureStateMachine.admitEntityDamage(
				OWNER, ENCOUNTER, OWNER, ENCOUNTER, true, 120.0F, 40.0F, 999.0F
		);
		assertTrue(quizAdmission.accepted());
		assertEquals(80.0F, quizAdmission.acceptedDamage());
		assertEquals(40.0F, quizAdmission.projectedHealth());
		assertEquals(40.0F, quizAdmission.thresholdHealth());
	}

	@Test
	void pureDamageRejectsWrongStaleAndExpiredInputsWithoutChangingState() {
		LectureStateMachine.State windUp = LectureStateMachine.start(ENCOUNTER, OWNER, 3, 0L, RULES, false).state();
		LectureStateMachine.State vulnerable = settle(LectureStateMachine.step(
				windUp,
				new LectureStateMachine.Input.Tick(100L, laneCenter(windUp.safeLane()), 20)
		)).state();
		List<LectureStateMachine.Input.Damage> rejected = List.of(
				new LectureStateMachine.Input.Damage(100L, uuidFor(810), ENCOUNTER, 5),
				new LectureStateMachine.Input.Damage(100L, OWNER, uuidFor(811), 5),
				new LectureStateMachine.Input.Damage(180L, OWNER, ENCOUNTER, 5),
				new LectureStateMachine.Input.Damage(100L, OWNER, ENCOUNTER, 0)
		);
		for (LectureStateMachine.Input.Damage input : rejected) {
			LectureStateMachine.Output output = LectureStateMachine.step(vulnerable, input);
			assertEquals(vulnerable, output.state(), () -> "rejected input=" + input);
			assertInstanceOf(LectureStateMachine.Intent.DamageRejected.class, output.intents().getFirst(),
					() -> "rejected input=" + input);
		}
	}

	@Test
	void finalMatchingThresholdEmitsVictoryExactlyOnce() {
		LectureStateMachine.State attendance = LectureStateMachine.testingState(
				ENCOUNTER, OWNER, 5, LectureAct.ATTENDANCE_CHECK, 0, 0, 0, 40, 0L, RULES, false
		);
		LectureStateMachine.Output present = LectureStateMachine.step(
				attendance,
				new LectureStateMachine.Input.Tick(
						120L,
						LectureGeometry.attendanceCenter(attendance.attendanceQuadrant()),
						20
				)
		);
		LectureStateMachine.State vulnerable = settle(present).state();
		LectureStateMachine.Output victory = LectureStateMachine.step(
				vulnerable,
				new LectureStateMachine.Input.Damage(120L, OWNER, ENCOUNTER, 999)
		);
		assertEquals(LectureStateMachine.Stage.COMPLETE, victory.state().stage());
		assertEquals(0, victory.state().bossHealth());
		assertEquals(1L, victory.intents().stream().filter(LectureStateMachine.Intent.Victory.class::isInstance).count());

		LectureStateMachine.Output replay = LectureStateMachine.step(
				victory.state(),
				new LectureStateMachine.Input.Damage(120L, OWNER, ENCOUNTER, 999)
		);
		assertEquals(victory.state(), replay.state());
		assertTrue(replay.intents().stream().noneMatch(LectureStateMachine.Intent.Victory.class::isInstance));
		assertEquals(
				LectureStateMachine.DamageRejection.COMPLETE,
				onlyIntent(replay, LectureStateMachine.Intent.DamageRejected.class).reason()
		);
	}

	private static LectureStateMachine.State startAct(
			UUID encounter,
			LectureAct act,
			int cycle,
			int quizIndex,
			int bossHealth,
			boolean reducedEffects
	) {
		return LectureStateMachine.testingState(
				encounter, OWNER, 7, act, cycle, quizIndex, 0, bossHealth, 0L, RULES, reducedEffects
		);
	}

	private static LectureStateMachine.Output settle(LectureStateMachine.Output resolved) {
		return LectureStateMachine.step(
				resolved.state(),
				new LectureStateMachine.Input.Tick(
						resolved.state().deadlineTick(),
						new LectureGeometry.LocalPosition(9.0D, 0.0D),
						20
				)
		);
	}

	private static LectureStateMachine.State finishRecovery(LectureStateMachine.Output recovery) {
		assertEquals(LectureStateMachine.Stage.RECOVERY, recovery.state().stage());
		return LectureStateMachine.step(
				recovery.state(),
				new LectureStateMachine.Input.Tick(
						recovery.state().deadlineTick(),
						new LectureGeometry.LocalPosition(9.0D, 0.0D),
						20
				)
		).state();
	}

	private static LectureGeometry.LocalPosition laneCenter(LectureGeometry.Lane lane) {
		return new LectureGeometry.LocalPosition(9.0D, lane.rightOffsets().get(2));
	}

	private static LectureGeometry.LocalPosition padCenter(LectureGeometry.QuizPad pad) {
		return new LectureGeometry.LocalPosition(9.0D, pad.rightAnchor());
	}

	private static UUID uuidFor(int sample) {
		return new UUID(ENCOUNTER.getMostSignificantBits() + sample, ENCOUNTER.getLeastSignificantBits() - sample);
	}

	private static void assertAdmission(
			float health,
			float threshold,
			float requested,
			float accepted,
			float projected,
			boolean victory
	) {
		LectureStateMachine.DamageAdmission admission = LectureStateMachine.admitEntityDamage(
				OWNER, ENCOUNTER, OWNER, ENCOUNTER, true, health, threshold, requested
		);
		assertTrue(admission.accepted(), () -> "health=" + health + ", requested=" + requested);
		assertEquals(accepted, admission.acceptedDamage(), () -> "health=" + health + ", requested=" + requested);
		assertEquals(projected, admission.projectedHealth(), () -> "health=" + health + ", requested=" + requested);
		assertTrue(admission.closesWindow(), () -> "health=" + health + ", requested=" + requested);
		assertEquals(victory, admission.victoryIntent(), () -> "health=" + health + ", requested=" + requested);
	}

	private static <T extends LectureStateMachine.Intent> T onlyIntent(
			LectureStateMachine.Output output,
			Class<T> type
	) {
		List<T> matching = new ArrayList<>();
		for (LectureStateMachine.Intent intent : output.intents()) {
			if (type.isInstance(intent)) {
				matching.add(type.cast(intent));
			}
		}
		assertEquals(1, matching.size(), () -> "expected one " + type.getSimpleName() + " in " + output.intents());
		return matching.getFirst();
	}
}
