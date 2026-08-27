package dev.developershell.lecture;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
	private static final int NORMAL_RETRY_RADIUS_MIN = 2;
	public static final int MIN_RETRY_SEARCH_RADIUS = 1;
	public static final int MAX_RETRY_SEARCH_RADIUS = 8;
	public static final int DEFAULT_RETRY_SEARCH_RADIUS = 5;
	private static final int ATTENDANCE_ANCHOR_OFFSET = 4;
	private static final double INTERIOR_FORWARD_MIN_EDGE = INTERIOR_FORWARD_MIN - 0.5D;
	private static final double INTERIOR_FORWARD_MAX_EDGE = INTERIOR_FORWARD_MAX + 0.5D;
	private static final double INTERIOR_RIGHT_MIN_EDGE = INTERIOR_RIGHT_MIN - 0.5D;
	private static final double INTERIOR_RIGHT_MAX_EDGE = INTERIOR_RIGHT_MAX + 0.5D;
	private static final double QUIZ_PAD_HALF_SIZE = 1.5D;
	public static final double ATTENDANCE_RADIUS = 2.5D;

	/** Finite, Minecraft-independent coordinates in the Desk's forward/right basis. */
	public record LocalPosition(double forwardOffset, double rightOffset) {
		public LocalPosition {
			if (!Double.isFinite(forwardOffset) || !Double.isFinite(rightOffset)) {
				throw new IllegalArgumentException("Lecture local position must be finite");
			}
		}
	}

	public static Optional<Lane> laneAt(LocalPosition position) {
		Objects.requireNonNull(position, "position");
		if (!isInsideInterior(position)) {
			return Optional.empty();
		}
		for (Lane lane : Lane.values()) {
			if (lane.contains(position)) {
				return Optional.of(lane);
			}
		}
		return Optional.empty();
	}

	public static Optional<QuizPad> quizPadAt(LocalPosition position) {
		Objects.requireNonNull(position, "position");
		if (!isInsideInterior(position)) {
			return Optional.empty();
		}
		for (QuizPad pad : QuizPad.values()) {
			if (pad.contains(position)) {
				return Optional.of(pad);
			}
		}
		return Optional.empty();
	}

	public static LocalPosition attendanceCenter(AttendanceQuadrant quadrant) {
		Objects.requireNonNull(quadrant, "quadrant");
		return new LocalPosition(
				COMBAT_CENTER_FORWARD + quadrant.forwardOffset(),
				quadrant.rightOffset()
		);
	}

	public static boolean isInsideAttendanceRing(AttendanceQuadrant quadrant, LocalPosition position) {
		Objects.requireNonNull(position, "position");
		LocalPosition center = attendanceCenter(quadrant);
		double forwardDistance = position.forwardOffset() - center.forwardOffset();
		double rightDistance = position.rightOffset() - center.rightOffset();
		return isInsideInterior(position)
				&& forwardDistance * forwardDistance + rightDistance * rightDistance
				<= ATTENDANCE_RADIUS * ATTENDANCE_RADIUS;
	}

	private static boolean isInsideInterior(LocalPosition position) {
		return position.forwardOffset() >= INTERIOR_FORWARD_MIN_EDGE
				&& position.forwardOffset() < INTERIOR_FORWARD_MAX_EDGE
				&& position.rightOffset() >= INTERIOR_RIGHT_MIN_EDGE
				&& position.rightOffset() < INTERIOR_RIGHT_MAX_EDGE;
	}

	public static Layout layout(BlockPos deskPos, Direction forward) {
		return layout(deskPos, forward, DEFAULT_RETRY_SEARCH_RADIUS);
	}

	public static Layout layout(BlockPos deskPos, Direction forward, int retrySearchRadius) {
		Objects.requireNonNull(deskPos, "deskPos");
		Objects.requireNonNull(forward, "forward");
		if (!forward.getAxis().isHorizontal()) {
			throw new IllegalArgumentException("Lecture arena facing must be horizontal");
		}
		return new Layout(deskPos, forward, forward.getClockWise(), retrySearchRadius);
	}

	public record Layout(BlockPos deskPos, Direction forward, Direction right, int retrySearchRadius) {
		public Layout {
			deskPos = Objects.requireNonNull(deskPos, "deskPos").immutable();
			Objects.requireNonNull(forward, "forward");
			Objects.requireNonNull(right, "right");
			if (!forward.getAxis().isHorizontal() || right != forward.getClockWise()) {
				throw new IllegalArgumentException("Lecture local axes must be horizontal and clockwise");
			}
			if (retrySearchRadius < MIN_RETRY_SEARCH_RADIUS
					|| retrySearchRadius > MAX_RETRY_SEARCH_RADIUS) {
				throw new IllegalArgumentException("Retry search radius must be between 1 and 8");
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
		 * Duplicate-free nearest-shell retry order through the configured maximum Chebyshev
		 * radius. Normal searches start at L-2F; radius one is the bounded exception so the
		 * smallest accepted configuration still probes its complete shell.
		 */
		public List<BlockPos> retryCandidates() {
			List<BlockPos> candidates = new ArrayList<>();
			Direction back = forward.getOpposite();
			int minimumShell = Math.min(NORMAL_RETRY_RADIUS_MIN, retrySearchRadius);
			for (int shell = minimumShell; shell <= retrySearchRadius; shell++) {
				candidates.add(deskPos.relative(back, shell).immutable());
				for (int lateral = 1; lateral <= shell; lateral++) {
					candidates.add(deskPos.relative(back, shell).relative(right, lateral).immutable());
					candidates.add(deskPos.relative(back, shell).relative(right, -lateral).immutable());
				}
				for (int distance = shell - 1; distance >= minimumShell; distance--) {
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
		private final double minimumEdge;
		private final double maximumEdge;

		Lane(int minimumRight, int maximumRight) {
			rightOffsets = IntStream.rangeClosed(minimumRight, maximumRight).boxed().toList();
			minimumEdge = minimumRight - 0.5D;
			maximumEdge = maximumRight + 0.5D;
		}

		public List<Integer> rightOffsets() {
			return rightOffsets;
		}

		public boolean contains(LocalPosition position) {
			Objects.requireNonNull(position, "position");
			return isInsideInterior(position)
					&& position.rightOffset() >= minimumEdge
					&& position.rightOffset() < maximumEdge;
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

		public boolean contains(LocalPosition position) {
			Objects.requireNonNull(position, "position");
			return isInsideInterior(position)
					&& position.forwardOffset() >= COMBAT_CENTER_FORWARD - QUIZ_PAD_HALF_SIZE
					&& position.forwardOffset() < COMBAT_CENTER_FORWARD + QUIZ_PAD_HALF_SIZE
					&& position.rightOffset() >= rightAnchor - QUIZ_PAD_HALF_SIZE
					&& position.rightOffset() < rightAnchor + QUIZ_PAD_HALF_SIZE;
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
