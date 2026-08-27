package dev.developershell.python;

import static dev.developershell.python.BoundedOreTraversal.StopReason.COMPLETE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.developershell.python.BoundedOreTraversal.BlockView;
import dev.developershell.python.BoundedOreTraversal.Limits;
import dev.developershell.python.BoundedOreTraversal.Plan;
import dev.developershell.python.BoundedOreTraversal.Position;
import dev.developershell.python.BoundedOreTraversal.StopReason;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class BoundedOreTraversalTest {
	private static final Position ORIGIN = new Position(0, 5, 0);
	private static final Limits DEFAULT = new Limits(64, 512, 8);
	private final BoundedOreTraversal traversal = new BoundedOreTraversal();

	@Test void emptyOriginProducesNoBlocks() {
		Plan plan = traversal.plan(new TestView(), ORIGIN, DEFAULT);
		assertEquals(List.of(), plan.positions());
		assertEquals(StopReason.EMPTY_ORIGIN, plan.stopReason());
	}
	@Test void oneOreReturnsOrigin() {
		Plan plan = traversal.plan(new TestView().ore(ORIGIN, "diamond"), ORIGIN, DEFAULT);
		assertEquals(List.of(ORIGIN), plan.positions());
		assertEquals(COMPLETE, plan.stopReason());
	}
	@Test void sixNeighborsUseStableOrder() {
		TestView view = new TestView().ore(ORIGIN, "diamond");
		List<Position> expected = List.of(
				ORIGIN, p(1, 5, 0), p(-1, 5, 0), p(0, 6, 0),
				p(0, 4, 0), p(0, 5, 1), p(0, 5, -1));
		expected.stream().skip(1).forEach(position -> view.ore(position, "diamond"));
		assertEquals(expected, traversal.plan(view, ORIGIN, DEFAULT).positions());
	}
	@Test void diagonalOreIsNotConnected() {
		TestView view = new TestView().ore(ORIGIN, "diamond").ore(p(1, 6, 0), "diamond");
		assertEquals(List.of(ORIGIN), traversal.plan(view, ORIGIN, DEFAULT).positions());
	}
	@Test void mixedOreIsExcluded() {
		TestView view = new TestView().ore(ORIGIN, "diamond").ore(p(1, 5, 0), "iron");
		assertEquals(List.of(ORIGIN), traversal.plan(view, ORIGIN, DEFAULT).positions());
	}
	@Test void chainTraversalIsBreadthFirstAndConnected() {
		TestView view = new TestView().ore(ORIGIN, "diamond")
				.ore(p(1, 5, 0), "diamond").ore(p(2, 5, 0), "diamond");
		assertEquals(List.of(ORIGIN, p(1, 5, 0), p(2, 5, 0)), traversal.plan(view, ORIGIN, DEFAULT).positions());
	}
	@Test void loopVisitsEveryOreOnce() {
		TestView view = new TestView().ore(ORIGIN, "diamond")
				.ore(p(1, 5, 0), "diamond").ore(p(1, 5, 1), "diamond").ore(p(0, 5, 1), "diamond");
		Plan plan = traversal.plan(view, ORIGIN, DEFAULT);
		assertEquals(4, plan.positions().size());
		assertEquals(4, Set.copyOf(plan.positions()).size());
	}
	@Test void maxBlocksHardCapsResult() {
		TestView view = chain(5);
		Plan plan = traversal.plan(view, ORIGIN, new Limits(2, 64, 8));
		assertEquals(2, plan.positions().size());
		assertEquals(StopReason.MAX_BLOCKS, plan.stopReason());
	}
	@Test void maxVisitedHardCapsSearch() {
		Plan plan = traversal.plan(chain(5), ORIGIN, new Limits(2, 2, 8));
		assertTrue(plan.positions().size() <= 2);
		assertEquals(StopReason.MAX_VISITED_NODES, plan.stopReason());
	}
	@Test void radiusZeroStopsAtOrigin() {
		Plan plan = traversal.plan(chain(2), ORIGIN, new Limits(4, 64, 0));
		assertEquals(List.of(ORIGIN), plan.positions());
		assertEquals(StopReason.RADIUS_BOUNDARY, plan.stopReason());
	}
	@Test void unloadedNeighborIsNeverPlanned() {
		Position next = p(1, 5, 0);
		TestView view = chain(2).unloaded(next);
		Plan plan = traversal.plan(view, ORIGIN, DEFAULT);
		assertEquals(List.of(ORIGIN), plan.positions());
		assertEquals(StopReason.UNLOADED_BOUNDARY, plan.stopReason());
	}
	@Test void dimensionBoundaryIsNeverCrossed() {
		Position next = p(1, 5, 0);
		TestView view = chain(2).dimension(next, "the_nether");
		Plan plan = traversal.plan(view, ORIGIN, DEFAULT);
		assertEquals(List.of(ORIGIN), plan.positions());
		assertEquals(StopReason.DIMENSION_BOUNDARY, plan.stopReason());
	}
	@Test void unbreakableNeighborIsNeverPlanned() {
		Position next = p(1, 5, 0);
		TestView view = chain(2).unbreakable(next);
		Plan plan = traversal.plan(view, ORIGIN, DEFAULT);
		assertEquals(List.of(ORIGIN), plan.positions());
		assertEquals(StopReason.UNBREAKABLE_BOUNDARY, plan.stopReason());
	}
	@Test void unbreakableOriginReturnsEmpty() {
		Plan plan = traversal.plan(new TestView().ore(ORIGIN, "diamond").unbreakable(ORIGIN), ORIGIN, DEFAULT);
		assertEquals(StopReason.UNBREAKABLE_BOUNDARY, plan.stopReason());
		assertTrue(plan.positions().isEmpty());
	}
	@Test void unloadedOriginReturnsEmpty() {
		Plan plan = traversal.plan(new TestView().ore(ORIGIN, "diamond").unloaded(ORIGIN), ORIGIN, DEFAULT);
		assertEquals(StopReason.UNLOADED_BOUNDARY, plan.stopReason());
	}
	@Test void heightBoundaryAtOriginReturnsEmpty() {
		Position tooHigh = p(0, 99, 0);
		Plan plan = traversal.plan(new TestView().ore(tooHigh, "diamond"), tooHigh, DEFAULT);
		assertEquals(StopReason.HEIGHT_BOUNDARY, plan.stopReason());
	}
	@Test void blankOriginDimensionIsRejected() {
		TestView view = new TestView().ore(ORIGIN, "diamond").dimension(ORIGIN, "");
		assertEquals(StopReason.DIMENSION_BOUNDARY, traversal.plan(view, ORIGIN, DEFAULT).stopReason());
	}
	@Test void resultListIsImmutable() {
		Plan plan = traversal.plan(new TestView().ore(ORIGIN, "diamond"), ORIGIN, DEFAULT);
		assertThrows(UnsupportedOperationException.class, () -> plan.positions().add(p(1, 5, 0)));
	}
	@Test void cappedPlanReportsRecursionError() { assertTrue(traversal.plan(chain(5), ORIGIN, new Limits(1, 64, 8)).recursionError()); }
	@Test void completePlanDoesNotReportRecursionError() { assertFalse(traversal.plan(chain(1), ORIGIN, DEFAULT).recursionError()); }
	@Test void emptyOriginDoesNotReportRecursionError() { assertFalse(traversal.plan(new TestView(), ORIGIN, DEFAULT).recursionError()); }
	@Test void sameWorldProducesRepeatableOrder() {
		TestView view = chain(5);
		assertEquals(traversal.plan(view, ORIGIN, DEFAULT), traversal.plan(view, ORIGIN, DEFAULT));
	}
	@Test void invalidLimitsRejectZeroBlocks() { assertThrows(IllegalArgumentException.class, () -> new Limits(0, 1, 1)); }
	@Test void invalidLimitsRejectTooFewVisitedNodes() { assertThrows(IllegalArgumentException.class, () -> new Limits(4, 3, 1)); }
	@Test void positionOffsetDetectsOverflow() { assertThrows(ArithmeticException.class, () -> new Position(Integer.MAX_VALUE, 0, 0).offset(1, 0, 0)); }

	private static Position p(int x, int y, int z) { return new Position(x, y, z); }
	private static TestView chain(int length) {
		TestView view = new TestView();
		for (int x = 0; x < length; x++) view.ore(p(x, 5, 0), "diamond");
		return view;
	}

	private static final class TestView implements BlockView {
		private final Map<Position, String> ores = new HashMap<>();
		private final Map<Position, String> dimensions = new HashMap<>();
		private final Set<Position> unloaded = new HashSet<>();
		private final Set<Position> unbreakable = new HashSet<>();

		TestView ore(Position position, String key) { ores.put(position, key); return this; }
		TestView dimension(Position position, String key) { dimensions.put(position, key); return this; }
		TestView unloaded(Position position) { unloaded.add(position); return this; }
		TestView unbreakable(Position position) { unbreakable.add(position); return this; }
		@Override public boolean loaded(Position position) { return !unloaded.contains(position); }
		@Override public boolean withinBuildHeight(Position position) { return position.y() >= 0 && position.y() <= 10; }
		@Override public String dimension(Position position) { return dimensions.getOrDefault(position, "overworld"); }
		@Override public String oreKey(Position position) { return ores.get(position); }
		@Override public boolean breakable(Position position) { return !unbreakable.contains(position); }
	}
}
