package dev.developershell.python;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Iterative, deterministic, strictly bounded connected-ore planner. */
public final class BoundedOreTraversal {
	private static final List<Offset> NEIGHBORS = List.of(
			new Offset(1, 0, 0), new Offset(-1, 0, 0),
			new Offset(0, 1, 0), new Offset(0, -1, 0),
			new Offset(0, 0, 1), new Offset(0, 0, -1)
	);

	public Plan plan(BlockView view, Position origin, Limits limits) {
		Objects.requireNonNull(view, "view");
		Objects.requireNonNull(origin, "origin");
		Objects.requireNonNull(limits, "limits");
		if (!view.withinBuildHeight(origin)) {
			return new Plan(List.of(), StopReason.HEIGHT_BOUNDARY, 0);
		}
		if (!view.loaded(origin)) {
			return new Plan(List.of(), StopReason.UNLOADED_BOUNDARY, 0);
		}
		String dimension = view.dimension(origin);
		String oreKey = view.oreKey(origin);
		if (dimension == null || dimension.isBlank()) {
			return new Plan(List.of(), StopReason.DIMENSION_BOUNDARY, 0);
		}
		if (oreKey == null || oreKey.isBlank()) {
			return new Plan(List.of(), StopReason.EMPTY_ORIGIN, 1);
		}
		if (!view.breakable(origin)) {
			return new Plan(List.of(), StopReason.UNBREAKABLE_BOUNDARY, 1);
		}

		ArrayDeque<Position> queue = new ArrayDeque<>();
		Set<Position> visited = new HashSet<>();
		List<Position> ordered = new ArrayList<>();
		queue.add(origin);
		visited.add(origin);
		StopReason boundary = StopReason.COMPLETE;

		while (!queue.isEmpty()) {
			if (ordered.size() >= limits.maxBlocks()) {
				return new Plan(ordered, StopReason.MAX_BLOCKS, visited.size());
			}
			Position current = queue.removeFirst();
			ordered.add(current);
			for (Offset offset : NEIGHBORS) {
				Position next = current.offset(offset.x(), offset.y(), offset.z());
				if (visited.contains(next)) {
					continue;
				}
				if (visited.size() >= limits.maxVisitedNodes()) {
					return new Plan(ordered, StopReason.MAX_VISITED_NODES, visited.size());
				}
				visited.add(next);
				if (origin.chebyshevDistance(next) > limits.radius()) {
					boundary = firstBoundary(boundary, StopReason.RADIUS_BOUNDARY);
					continue;
				}
				if (!view.withinBuildHeight(next)) {
					boundary = firstBoundary(boundary, StopReason.HEIGHT_BOUNDARY);
					continue;
				}
				if (!dimension.equals(view.dimension(next))) {
					boundary = firstBoundary(boundary, StopReason.DIMENSION_BOUNDARY);
					continue;
				}
				if (!view.loaded(next)) {
					boundary = firstBoundary(boundary, StopReason.UNLOADED_BOUNDARY);
					continue;
				}
				if (!oreKey.equals(view.oreKey(next))) {
					continue;
				}
				if (!view.breakable(next)) {
					boundary = firstBoundary(boundary, StopReason.UNBREAKABLE_BOUNDARY);
					continue;
				}
				queue.addLast(next);
			}
		}
		return new Plan(ordered, boundary, visited.size());
	}

	private static StopReason firstBoundary(StopReason current, StopReason candidate) {
		return current == StopReason.COMPLETE ? candidate : current;
	}

	public interface BlockView {
		boolean loaded(Position position);
		boolean withinBuildHeight(Position position);
		String dimension(Position position);
		String oreKey(Position position);
		boolean breakable(Position position);
	}

	public record Position(int x, int y, int z) {
		public Position offset(int dx, int dy, int dz) {
			return new Position(Math.addExact(x, dx), Math.addExact(y, dy), Math.addExact(z, dz));
		}

		public int chebyshevDistance(Position other) {
			long dx = Math.abs((long) x - other.x);
			long dy = Math.abs((long) y - other.y);
			long dz = Math.abs((long) z - other.z);
			return Math.toIntExact(Math.max(dx, Math.max(dy, dz)));
		}
	}

	public record Limits(int maxBlocks, int maxVisitedNodes, int radius) {
		public Limits {
			if (maxBlocks <= 0 || maxVisitedNodes < maxBlocks || radius < 0) {
				throw new IllegalArgumentException("Traversal limits must be positive and internally consistent");
			}
		}
	}

	public record Plan(List<Position> positions, StopReason stopReason, int visitedNodes) {
		public Plan {
			positions = List.copyOf(positions);
			Objects.requireNonNull(stopReason, "stopReason");
			if (visitedNodes < positions.size()) {
				throw new IllegalArgumentException("Visited count cannot be smaller than result count");
			}
		}

		public boolean recursionError() {
			return stopReason != StopReason.COMPLETE && stopReason != StopReason.EMPTY_ORIGIN;
		}
	}

	public enum StopReason {
		COMPLETE,
		EMPTY_ORIGIN,
		MAX_BLOCKS,
		MAX_VISITED_NODES,
		RADIUS_BOUNDARY,
		UNLOADED_BOUNDARY,
		HEIGHT_BOUNDARY,
		DIMENSION_BOUNDARY,
		UNBREAKABLE_BOUNDARY
	}

	private record Offset(int x, int y, int z) {}
}
