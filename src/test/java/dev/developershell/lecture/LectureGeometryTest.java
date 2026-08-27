package dev.developershell.lecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

final class LectureGeometryTest {
	private static final BlockPos DESK = new BlockPos(41, 73, -19);
	private static final List<Direction> HORIZONTAL_FACINGS = List.of(
			Direction.NORTH,
			Direction.EAST,
			Direction.SOUTH,
			Direction.WEST
	);

	@Test
	void allFourFacingsFreezeExactBoundaryInteriorAndHeadroom() {
		for (Direction facing : HORIZONTAL_FACINGS) {
			LectureGeometry.Layout layout = LectureGeometry.layout(DESK, facing);
			Direction right = facing.getClockWise();

			assertEquals(DESK, layout.deskPos(), facing + " desk");
			assertEquals(facing, layout.forward(), facing + " forward");
			assertEquals(right, layout.right(), facing + " clockwise right");
			assertEquals(DESK.relative(facing, 9).below(), layout.combatCenterFloor(), facing + " center floor");

			List<BlockPos> boundary = layout.boundaryFloorPositions();
			List<BlockPos> interior = layout.interiorFloorPositions();
			assertEquals(17 * 17, boundary.size(), facing + " boundary count");
			assertEquals(boundary.size(), new HashSet<>(boundary).size(), facing + " boundary uniqueness");
			assertEquals(15 * 15, interior.size(), facing + " interior count");
			assertEquals(interior.size(), new HashSet<>(interior).size(), facing + " interior uniqueness");
			assertTrue(new HashSet<>(boundary).containsAll(interior), facing + " interior subset");
			assertEquals(17 * 17 - 15 * 15, boundary.stream().filter(pos -> !interior.contains(pos)).count(),
					facing + " one-block margin");

			List<BlockPos> expectedBoundary = new ArrayList<>();
			for (int forward = 1; forward <= 17; forward++) {
				for (int lateral = -8; lateral <= 8; lateral++) {
					expectedBoundary.add(DESK.relative(facing, forward).relative(right, lateral).below());
				}
			}
			assertEquals(expectedBoundary, boundary, facing + " forward-major boundary order");

			List<BlockPos> expectedInterior = new ArrayList<>();
			for (int forward = 2; forward <= 16; forward++) {
				for (int lateral = -7; lateral <= 7; lateral++) {
					expectedInterior.add(DESK.relative(facing, forward).relative(right, lateral).below());
				}
			}
			assertEquals(expectedInterior, interior, facing + " forward-major interior order");

			BlockPos sampleFloor = layout.floorAt(2, -7);
			assertEquals(
					List.of(sampleFloor.above(), sampleFloor.above(2), sampleFloor.above(3), sampleFloor.above(4)),
					layout.headroomAt(2, -7),
					facing + " exact four-block headroom"
			);
			assertEquals(
					List.of(DESK.getY(), DESK.getY() + 1, DESK.getY() + 2, DESK.getY() + 3),
					layout.headroomAt(2, -7).stream().map(BlockPos::getY).toList(),
					facing + " world headroom levels"
			);
		}
	}

	@Test
	void lanePartitionsCoverTheInteriorExactlyOnce() {
		Map<LectureGeometry.Lane, List<Integer>> expected = new EnumMap<>(LectureGeometry.Lane.class);
		expected.put(LectureGeometry.Lane.LEFT, IntStream.rangeClosed(-7, -3).boxed().toList());
		expected.put(LectureGeometry.Lane.CENTER, IntStream.rangeClosed(-2, 2).boxed().toList());
		expected.put(LectureGeometry.Lane.RIGHT, IntStream.rangeClosed(3, 7).boxed().toList());

		List<Integer> covered = new ArrayList<>();
		for (LectureGeometry.Lane lane : LectureGeometry.Lane.values()) {
			assertEquals(expected.get(lane), lane.rightOffsets(), lane + " exact offsets");
			assertEquals(5, lane.rightOffsets().size(), lane + " width");
			covered.addAll(lane.rightOffsets());
		}
		assertEquals(IntStream.rangeClosed(-7, 7).boxed().toList(), covered, "contiguous lateral coverage");
		assertEquals(covered.size(), new HashSet<>(covered).size(), "no lane overlap");
	}

	@Test
	void quizPadsAndAttendanceRingsUseFixedLocalAnchors() {
		assertEquals(List.of(-5, 0, 5),
				List.of(LectureGeometry.QuizPad.A.rightAnchor(), LectureGeometry.QuizPad.B.rightAnchor(),
						LectureGeometry.QuizPad.C.rightAnchor()));
		assertEquals(List.of("square", "circle", "diamond"),
				List.of(LectureGeometry.QuizPad.A.shapeId(), LectureGeometry.QuizPad.B.shapeId(),
						LectureGeometry.QuizPad.C.shapeId()));

		LectureGeometry.Layout layout = LectureGeometry.layout(DESK, Direction.SOUTH);
		BlockPos center = layout.combatCenterFloor();
		assertEquals(center.relative(Direction.SOUTH, 4).relative(Direction.EAST, 4),
				layout.attendanceRing(LectureGeometry.AttendanceQuadrant.FRONT_LEFT).centerFloor());
		assertEquals(center.relative(Direction.SOUTH, 4).relative(Direction.WEST, 4),
				layout.attendanceRing(LectureGeometry.AttendanceQuadrant.FRONT_RIGHT).centerFloor());
		assertEquals(center.relative(Direction.NORTH, 4).relative(Direction.EAST, 4),
				layout.attendanceRing(LectureGeometry.AttendanceQuadrant.BACK_LEFT).centerFloor());
		assertEquals(center.relative(Direction.NORTH, 4).relative(Direction.WEST, 4),
				layout.attendanceRing(LectureGeometry.AttendanceQuadrant.BACK_RIGHT).centerFloor());
		for (LectureGeometry.AttendanceQuadrant quadrant : LectureGeometry.AttendanceQuadrant.values()) {
			assertEquals(2.5D, layout.attendanceRing(quadrant).radius(), quadrant + " exact radius");
		}
	}

	@Test
	void everyCombatZoneUsesOneExactServerSideContainmentContract() {
		for (LectureGeometry.Lane lane : LectureGeometry.Lane.values()) {
			for (int rightOffset : lane.rightOffsets()) {
				LectureGeometry.LocalPosition position = new LectureGeometry.LocalPosition(9.0D, rightOffset);
				assertTrue(lane.contains(position), () -> lane + " must contain " + position);
				assertEquals(lane, LectureGeometry.laneAt(position).orElseThrow(),
						() -> "unique lane at " + position);
			}
		}
		assertTrue(LectureGeometry.laneAt(new LectureGeometry.LocalPosition(9.0D, -7.5D)).isPresent());
		assertTrue(LectureGeometry.laneAt(new LectureGeometry.LocalPosition(9.0D, 7.5D)).isEmpty());
		assertTrue(LectureGeometry.laneAt(new LectureGeometry.LocalPosition(1.49D, 0.0D)).isEmpty());

		for (LectureGeometry.QuizPad pad : LectureGeometry.QuizPad.values()) {
			LectureGeometry.LocalPosition center = new LectureGeometry.LocalPosition(9.0D, pad.rightAnchor());
			assertTrue(pad.contains(center), () -> pad + " center " + center);
			assertEquals(pad, LectureGeometry.quizPadAt(center).orElseThrow(),
					() -> "unique quiz pad at " + center);
		}
		assertTrue(LectureGeometry.quizPadAt(new LectureGeometry.LocalPosition(9.0D, -2.5D)).isEmpty(),
				"space between pads is no answer");

		for (LectureGeometry.AttendanceQuadrant quadrant : LectureGeometry.AttendanceQuadrant.values()) {
			LectureGeometry.LocalPosition center = LectureGeometry.attendanceCenter(quadrant);
			assertTrue(LectureGeometry.isInsideAttendanceRing(quadrant, center),
					() -> quadrant + " center " + center);
			LectureGeometry.LocalPosition edge = new LectureGeometry.LocalPosition(
					center.forwardOffset() + LectureGeometry.ATTENDANCE_RADIUS,
					center.rightOffset()
			);
			assertTrue(LectureGeometry.isInsideAttendanceRing(quadrant, edge),
					() -> quadrant + " radius edge " + edge);
			LectureGeometry.LocalPosition outside = new LectureGeometry.LocalPosition(
					center.forwardOffset() + LectureGeometry.ATTENDANCE_RADIUS + 0.01D,
					center.rightOffset()
			);
			assertFalse(LectureGeometry.isInsideAttendanceRing(quadrant, outside),
					() -> quadrant + " outside " + outside);
		}
	}

	@Test
	void retryOrderStartsTwoBehindAndIsFiniteThroughRadiusFive() {
		for (Direction facing : HORIZONTAL_FACINGS) {
			LectureGeometry.Layout layout = LectureGeometry.layout(DESK, facing);
			Direction back = facing.getOpposite();
			Direction right = facing.getClockWise();
			List<BlockPos> expected = new ArrayList<>();
			for (int shell = 2; shell <= 5; shell++) {
				expected.add(DESK.relative(back, shell));
				for (int lateral = 1; lateral <= shell; lateral++) {
					expected.add(DESK.relative(back, shell).relative(right, lateral));
					expected.add(DESK.relative(back, shell).relative(right, -lateral));
				}
				for (int distance = shell - 1; distance >= 2; distance--) {
					expected.add(DESK.relative(back, distance).relative(right, shell));
					expected.add(DESK.relative(back, distance).relative(right, -shell));
				}
			}

			assertEquals(expected, layout.retryCandidates(), facing + " documented retry order");
			assertEquals(DESK.relative(facing, -2), layout.retryCandidates().getFirst(), facing + " first retry");
			assertEquals(44, layout.retryCandidates().size(), facing + " finite retry count");
			assertEquals(44, new HashSet<>(layout.retryCandidates()).size(), facing + " no duplicate probes");
			assertTrue(layout.retryCandidates().stream().allMatch(candidate ->
					Math.max(Math.abs(candidate.getX() - DESK.getX()), Math.abs(candidate.getZ() - DESK.getZ())) <= 5),
					facing + " radius-five bound");
		}
	}

	@Test
	void retryRadiusOneAndEightBoundTheCompleteConfiguredShells() {
		LectureGeometry.Layout radiusOne = LectureGeometry.layout(DESK, Direction.NORTH, 1);
		assertEquals(1, radiusOne.retrySearchRadius());
		assertEquals(3, radiusOne.retryCandidates().size());
		assertEquals(DESK.relative(Direction.SOUTH), radiusOne.retryCandidates().getFirst());
		assertEquals(3, new HashSet<>(radiusOne.retryCandidates()).size());

		LectureGeometry.Layout radiusEight = LectureGeometry.layout(DESK, Direction.NORTH, 8);
		assertEquals(8, radiusEight.retrySearchRadius());
		assertEquals(119, radiusEight.retryCandidates().size());
		assertEquals(119, new HashSet<>(radiusEight.retryCandidates()).size());
		assertTrue(radiusEight.retryCandidates().contains(DESK.relative(Direction.SOUTH, 8)));
		assertTrue(radiusEight.retryCandidates().stream().allMatch(candidate ->
				Math.max(Math.abs(candidate.getX() - DESK.getX()), Math.abs(candidate.getZ() - DESK.getZ())) <= 8));
	}

	@Test
	void typedResultsAndEveryRejectionExposeStableLocalizationKeys() {
		Map<ArenaRejection, String> expectedKeys = Map.of(
				ArenaRejection.WRONG_TARGET, "message.developers_hell.contract.find_lectern",
				ArenaRejection.WRONG_DIMENSION, "message.developers_hell.contract.rejected.dimension",
				ArenaRejection.UNLOADED_OR_OUTSIDE_BORDER, "message.developers_hell.contract.rejected.loaded_border",
				ArenaRejection.NON_SOLID_FLOOR, "message.developers_hell.contract.rejected.floor",
				ArenaRejection.INSUFFICIENT_HEADROOM, "message.developers_hell.contract.rejected.headroom",
				ArenaRejection.UNSAFE_RETRY, "message.developers_hell.contract.rejected.retry",
				ArenaRejection.ACTIVE_ENCOUNTER, "message.developers_hell.contract.rejected.active",
				ArenaRejection.SPAWN_CAPACITY, "message.developers_hell.contract.rejected.spawn"
		);
		assertEquals(expectedKeys.keySet(), Set.of(ArenaRejection.values()), "closed rejection family");
		expectedKeys.forEach((rejection, key) -> assertEquals(key, rejection.translationKey(), rejection.name()));

		LectureGeometry.Layout layout = LectureGeometry.layout(DESK, Direction.NORTH);
		ArenaValidationResult.Accepted accepted = new ArenaValidationResult.Accepted(
				layout,
				layout.retryCandidates().getFirst()
		);
		assertTrue(accepted.accepted());
		assertEquals(layout.combatCenterFloor(), accepted.combatOriginFloor());
		assertFalse(accepted.rejection().isPresent());

		ArenaValidationResult.Rejected rejected = new ArenaValidationResult.Rejected(ArenaRejection.NON_SOLID_FLOOR);
		assertFalse(rejected.accepted());
		assertEquals(ArenaRejection.NON_SOLID_FLOOR, rejected.rejection().orElseThrow());
		assertInstanceOf(ArenaValidationResult.Rejected.class, rejected);
	}

	@Test
	void verticalFacingCannotCreateAHorizontalArena() {
		assertThrows(IllegalArgumentException.class, () -> LectureGeometry.layout(DESK, Direction.UP));
		assertThrows(IllegalArgumentException.class, () -> LectureGeometry.layout(DESK, Direction.DOWN));
		assertThrows(IllegalArgumentException.class, () -> LectureGeometry.layout(DESK, Direction.NORTH, 0));
		assertThrows(IllegalArgumentException.class, () -> LectureGeometry.layout(DESK, Direction.NORTH, 9));
	}
}
