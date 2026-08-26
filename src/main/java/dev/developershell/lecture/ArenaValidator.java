package dev.developershell.lecture;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Bounded read-only arena validator shared by pure tests and the logical-server adapter. */
public final class ArenaValidator {
	public static ArenaValidationResult validate(
			ReadOnlyWorld world,
			UUID ownerUuid,
			BlockPos deskPos,
			Direction deskFacing
	) {
		Objects.requireNonNull(world, "world");
		Objects.requireNonNull(ownerUuid, "ownerUuid");
		Objects.requireNonNull(deskPos, "deskPos");
		Objects.requireNonNull(deskFacing, "deskFacing");

		if (!deskFacing.getAxis().isHorizontal() || !world.isMatchingLectern(deskPos, deskFacing)) {
			return rejected(ArenaRejection.WRONG_TARGET);
		}
		if (!world.isOverworld()) {
			return rejected(ArenaRejection.WRONG_DIMENSION);
		}

		LectureGeometry.Layout layout = LectureGeometry.layout(deskPos, deskFacing);
		if (world.hasActiveEncounter(ownerUuid, deskPos)) {
			return rejected(ArenaRejection.ACTIVE_ENCOUNTER);
		}

		for (BlockPos floor : layout.boundaryFloorPositions()) {
			if (!world.isLoadedAndInsideBorder(floor)) {
				return rejected(ArenaRejection.UNLOADED_OR_OUTSIDE_BORDER);
			}
		}
		for (BlockPos floor : layout.interiorFloorPositions()) {
			for (int height = 1; height <= 4; height++) {
				if (!world.isLoadedAndInsideBorder(floor.above(height))) {
					return rejected(ArenaRejection.UNLOADED_OR_OUTSIDE_BORDER);
				}
			}
		}

		for (BlockPos floor : layout.boundaryFloorPositions()) {
			if (!world.hasSolidSupport(floor)) {
				return rejected(ArenaRejection.NON_SOLID_FLOOR);
			}
		}
		for (BlockPos floor : layout.interiorFloorPositions()) {
			for (int height = 1; height <= 4; height++) {
				if (!world.isPassableAndNonHazardous(floor.above(height))) {
					return rejected(ArenaRejection.INSUFFICIENT_HEADROOM);
				}
			}
		}

		BlockPos retryPos = null;
		for (BlockPos candidate : layout.retryCandidates()) {
			BlockPos support = candidate.below();
			BlockPos upperBody = candidate.above();
			if (world.isLoadedAndInsideBorder(support)
					&& world.isLoadedAndInsideBorder(candidate)
					&& world.isLoadedAndInsideBorder(upperBody)
					&& world.hasSolidSupport(support)
					&& world.isPassableAndNonHazardous(candidate)
					&& world.isPassableAndNonHazardous(upperBody)) {
				retryPos = candidate;
				break;
			}
		}
		if (retryPos == null) {
			return rejected(ArenaRejection.UNSAFE_RETRY);
		}

		BlockPos professorFeet = layout.combatCenterFloor().above();
		if (!world.hasSpawnCapacity(ownerUuid, professorFeet)) {
			return rejected(ArenaRejection.SPAWN_CAPACITY);
		}
		return new ArenaValidationResult.Accepted(layout, retryPos);
	}

	private static ArenaValidationResult.Rejected rejected(ArenaRejection rejection) {
		return new ArenaValidationResult.Rejected(rejection);
	}

	/** Every method is a bounded query; implementations must never load chunks or mutate state. */
	public interface ReadOnlyWorld {
		boolean isMatchingLectern(BlockPos deskPos, Direction deskFacing);

		boolean isOverworld();

		boolean isLoadedAndInsideBorder(BlockPos pos);

		boolean hasSolidSupport(BlockPos pos);

		boolean isPassableAndNonHazardous(BlockPos pos);

		boolean hasActiveEncounter(UUID ownerUuid, BlockPos deskPos);

		boolean hasSpawnCapacity(UUID ownerUuid, BlockPos professorFeet);
	}

	private ArenaValidator() {
	}
}
