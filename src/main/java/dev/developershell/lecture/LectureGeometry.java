package dev.developershell.lecture;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Pure, immutable coordinate contract for the Internship Desk and Lecture arena. */
public final class LectureGeometry {
	private static final int BOUNDARY_FORWARD_MIN = 1;
	private static final int BOUNDARY_FORWARD_MAX = 17;
	private static final int BOUNDARY_RIGHT_MIN = -8;
	private static final int BOUNDARY_RIGHT_MAX = 8;
	private static final int INTERIOR_FORWARD_MIN = 2;
	private static final int INTERIOR_FORWARD_MAX = 16;
	private static final int INTERIOR_RIGHT_MIN = -7;
	private static final int INTERIOR_RIGHT_MAX = 7;
	private static final int COMBAT_CENTER_FORWARD = 9;
	private static final int RETRY_RADIUS_MIN = 2;
	private static final int RETRY_RADIUS_MAX = 5;
	private static final int ATTENDANCE_ANCHOR_OFFSET = 4;
	public static final double ATTENDANCE_RADIUS = 2.5D;

	public static Layout layout(BlockPos deskPos, Direction forward) {
		Objects.requireNonNull(deskPos, "deskPos");
		Objects.requireNonNull(forward, "forward");
		if (!forward.getAxis().isHorizontal()) {
			throw new IllegalArgumentException("Lecture arena facing must be horizontal");
		}
		return new Layout(deskPos, forward, forward.getClockWise());
	}

	public record Layout(BlockPos deskPos, Direction forward, Direction right) {
		public Layout {
			deskPos = Objects.requireNonNull(deskPos, "deskPos").immutable();
			Objects.requireNonNull(forward, "forward");
			Objects.requireNonNull(right, "right");
			if (!forward.getAxis().isHorizontal() || right != forward.getClockWise()) {
				throw new IllegalArgumentException("Lecture local axes must be horizontal and clockwise");
			}
		}

		public BlockPos floorAt(int forwardOffset, int rightOffset) {
			return deskPos.relative(forward, forwardOffset).relative(right, rightOffset).below().immutable();
		}

		public BlockPos combatCenterFloor() {
			return floorAt(COMBAT_CENTER_FORWARD, 0);
		}

		public List<BlockPos> boundaryFloorPositions() {
			return floorRectangle(
					BOUNDARY_FORWARD_MIN,
					BOUNDARY_FORWARD_MAX,
					BOUNDARY_RIGHT_MIN,
					BOUNDARY_RIGHT_MAX
			);
		}

		public List<BlockPos> interiorFloorPositions() {
			return floorRectangle(
					INTERIOR_FORWARD_MIN,
					INTERIOR_FORWARD_MAX,
					INTERIOR_RIGHT_MIN,
					INTERIOR_RIGHT_MAX
			);
		}

		public List<BlockPos> headroomAt(int forwardOffset, int rightOffset) {
			if (!isInterior(forwardOffset, rightOffset)) {
				throw new IllegalArgumentException("Headroom coordinates must be inside the 15x15 combat interior");
			}
			BlockPos floor = floorAt(forwardOffset, rightOffset);
			return List.of(floor.above(), floor.above(2), floor.above(3), floor.above(4));
		}

		/**
		 * Duplicate-free nearest-shell retry order. The first candidate is L-2F; subsequent
		 * candidates cover the complete behind/side wedge through Chebyshev radius five.
		 */
		public List<BlockPos> retryCandidates() {
			List<BlockPos> candidates = new ArrayList<>(44);
			Direction back = forward.getOpposite();
			for (int shell = RETRY_RADIUS_MIN; shell <= RETRY_RADIUS_MAX; shell++) {
				candidates.add(deskPos.relative(back, shell).immutable());
				for (int lateral = 1; lateral <= shell; lateral++) {
					candidates.add(deskPos.relative(back, shell).relative(right, lateral).immutable());
					candidates.add(deskPos.relative(back, shell).relative(right, -lateral).immutable());
				}
				for (int distance = shell - 1; distance >= RETRY_RADIUS_MIN; distance--) {
					candidates.add(deskPos.relative(back, distance).relative(right, shell).immutable());
					candidates.add(deskPos.relative(back, distance).relative(right, -shell).immutable());
				}
			}
			return List.copyOf(candidates);
		}

		public AttendanceRing attendanceRing(AttendanceQuadrant quadrant) {
			Objects.requireNonNull(quadrant, "quadrant");
			BlockPos center = combatCenterFloor()
					.relative(forward, quadrant.forwardOffset())
					.relative(right, quadrant.rightOffset());
			return new AttendanceRing(quadrant, center, ATTENDANCE_RADIUS);
		}

		private List<BlockPos> floorRectangle(
				int forwardMinimum,
				int forwardMaximum,
				int rightMinimum,
				int rightMaximum
		) {
			List<BlockPos> positions = new ArrayList<>(
					(forwardMaximum - forwardMinimum + 1) * (rightMaximum - rightMinimum + 1)
			);
			for (int forwardOffset = forwardMinimum; forwardOffset <= forwardMaximum; forwardOffset++) {
				for (int rightOffset = rightMinimum; rightOffset <= rightMaximum; rightOffset++) {
					positions.add(floorAt(forwardOffset, rightOffset));
				}
			}
			return List.copyOf(positions);
		}

		private static boolean isInterior(int forwardOffset, int rightOffset) {
			return forwardOffset >= INTERIOR_FORWARD_MIN
					&& forwardOffset <= INTERIOR_FORWARD_MAX
					&& rightOffset >= INTERIOR_RIGHT_MIN
					&& rightOffset <= INTERIOR_RIGHT_MAX;
		}
	}

	public enum Lane {
		LEFT(-7, -3),
		CENTER(-2, 2),
		RIGHT(3, 7);

		private final List<Integer> rightOffsets;

		Lane(int minimumRight, int maximumRight) {
			rightOffsets = IntStream.rangeClosed(minimumRight, maximumRight).boxed().toList();
		}

		public List<Integer> rightOffsets() {
			return rightOffsets;
		}
	}

	public enum QuizPad {
		A(-5, "square"),
		B(0, "circle"),
		C(5, "diamond");

		private final int rightAnchor;
		private final String shapeId;

		QuizPad(int rightAnchor, String shapeId) {
			this.rightAnchor = rightAnchor;
			this.shapeId = shapeId;
		}

		public int rightAnchor() {
			return rightAnchor;
		}

		public String shapeId() {
			return shapeId;
		}
	}

	public enum AttendanceQuadrant {
		FRONT_LEFT(ATTENDANCE_ANCHOR_OFFSET, -ATTENDANCE_ANCHOR_OFFSET),
		FRONT_RIGHT(ATTENDANCE_ANCHOR_OFFSET, ATTENDANCE_ANCHOR_OFFSET),
		BACK_LEFT(-ATTENDANCE_ANCHOR_OFFSET, -ATTENDANCE_ANCHOR_OFFSET),
		BACK_RIGHT(-ATTENDANCE_ANCHOR_OFFSET, ATTENDANCE_ANCHOR_OFFSET);

		private final int forwardOffset;
		private final int rightOffset;

		AttendanceQuadrant(int forwardOffset, int rightOffset) {
			this.forwardOffset = forwardOffset;
			this.rightOffset = rightOffset;
		}

		public int forwardOffset() {
			return forwardOffset;
		}

		public int rightOffset() {
			return rightOffset;
		}
	}

	public record AttendanceRing(AttendanceQuadrant quadrant, BlockPos centerFloor, double radius) {
		public AttendanceRing {
			Objects.requireNonNull(quadrant, "quadrant");
			centerFloor = Objects.requireNonNull(centerFloor, "centerFloor").immutable();
			if (radius <= 0.0D || !Double.isFinite(radius)) {
				throw new IllegalArgumentException("Attendance radius must be finite and positive");
			}
		}
	}

	private LectureGeometry() {
	}
}
