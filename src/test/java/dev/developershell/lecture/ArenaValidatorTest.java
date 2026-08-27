package dev.developershell.lecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

final class ArenaValidatorTest {
	private static final BlockPos DESK = new BlockPos(30, 70, 30);
	private static final Direction FACING = Direction.NORTH;
	private static final UUID OWNER = UUID.fromString("e2607788-1f22-47f4-afaa-d45c59ef17dd");

	@Test
	void radiusOneUsesOnlyItsCompleteOuterShell() {
		BlockPos withinRadius = DESK.relative(FACING.getOpposite());
		ArenaValidationResult.Accepted accepted = assertInstanceOf(
				ArenaValidationResult.Accepted.class,
				validate(1, withinRadius)
		);
		assertEquals(withinRadius, accepted.retryPos());
		assertEquals(1, accepted.layout().retrySearchRadius());

		BlockPos outsideRadius = DESK.relative(FACING.getOpposite(), 5);
		ArenaValidationResult.Rejected rejected = assertInstanceOf(
				ArenaValidationResult.Rejected.class,
				validate(1, outsideRadius)
		);
		assertEquals(ArenaRejection.UNSAFE_RETRY, rejected.reason());
	}

	@Test
	void radiusFiveRejectsAOnlySafeRadiusSixCandidate() {
		BlockPos radiusSix = DESK.relative(FACING.getOpposite(), 6);
		ArenaValidationResult.Rejected rejected = assertInstanceOf(
				ArenaValidationResult.Rejected.class,
				validate(5, radiusSix)
		);
		assertEquals(ArenaRejection.UNSAFE_RETRY, rejected.reason());
	}

	@Test
	void radiusEightCanSelectItsOuterShell() {
		BlockPos radiusEight = DESK.relative(FACING.getOpposite(), 8);
		ArenaValidationResult.Accepted accepted = assertInstanceOf(
				ArenaValidationResult.Accepted.class,
				validate(8, radiusEight)
		);
		assertEquals(radiusEight, accepted.retryPos());
		assertEquals(8, accepted.layout().retrySearchRadius());
	}

	private static ArenaValidationResult validate(int radius, BlockPos onlySafeRetryFeet) {
		return ArenaValidator.validate(
				new SafeArenaWorld(onlySafeRetryFeet),
				OWNER,
				DESK,
				FACING,
				radius
		);
	}

	private static final class SafeArenaWorld implements ArenaValidator.ReadOnlyWorld {
		private final Set<BlockPos> safeStandingSurfaces;
		private final Set<BlockPos> safeOccupancy;

		private SafeArenaWorld(BlockPos onlySafeRetryFeet) {
			LectureGeometry.Layout layout = LectureGeometry.layout(DESK, FACING);
			safeStandingSurfaces = new HashSet<>(layout.boundaryFloorPositions());
			safeStandingSurfaces.add(onlySafeRetryFeet.below());
			safeOccupancy = new HashSet<>();
			for (BlockPos floor : layout.interiorFloorPositions()) {
				for (int height = 1; height <= 4; height++) {
					safeOccupancy.add(floor.above(height));
				}
			}
			safeOccupancy.add(onlySafeRetryFeet);
			safeOccupancy.add(onlySafeRetryFeet.above());
		}

		@Override
		public boolean isMatchingLectern(BlockPos deskPos, Direction deskFacing) {
			return DESK.equals(deskPos) && FACING == deskFacing;
		}

		@Override
		public boolean isOverworld() {
			return true;
		}

		@Override
		public boolean isLoadedAndInsideBorder(BlockPos pos) {
			return true;
		}

		@Override
		public boolean isSafeStandingSurface(BlockPos pos) {
			return safeStandingSurfaces.contains(pos);
		}

		@Override
		public boolean isSafeOccupancy(BlockPos pos) {
			return safeOccupancy.contains(pos);
		}

		@Override
		public boolean hasActiveEncounter(UUID ownerUuid, BlockPos deskPos) {
			return false;
		}

		@Override
		public boolean hasSpawnCapacity(UUID ownerUuid, BlockPos professorFeet) {
			return true;
		}
	}
}
