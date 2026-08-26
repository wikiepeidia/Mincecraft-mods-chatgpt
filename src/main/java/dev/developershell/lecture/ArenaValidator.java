package dev.developershell.lecture;

import dev.developershell.campaign.CampaignSavedData;
import dev.developershell.campaign.PlayerCampaignState;
import dev.developershell.registry.ModEntities;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Bounded read-only arena validator shared by pure tests and the logical-server adapter. */
public final class ArenaValidator {
	/** Production adapter: all probes run on the logical server and never force chunk loads. */
	public static ArenaValidationResult validate(
			ServerLevel level,
			ServerPlayer player,
			BlockPos deskPos,
			Direction deskFacing
	) {
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(player, "player");
		if (player.level() != level || !level.getServer().isSameThread()) {
			return new ArenaValidationResult.Rejected(ArenaRejection.SPAWN_CAPACITY);
		}
		return validate(new ServerWorld(level), player.getUUID(), deskPos, deskFacing);
	}

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

	private record ServerWorld(ServerLevel level) implements ReadOnlyWorld {
		private ServerWorld {
			Objects.requireNonNull(level, "level");
		}

		@Override
		public boolean isMatchingLectern(BlockPos deskPos, Direction deskFacing) {
			if (!level.isLoaded(deskPos)) {
				return false;
			}
			BlockState state = level.getBlockState(deskPos);
			return state.is(Blocks.LECTERN) && state.getValue(LecternBlock.FACING) == deskFacing;
		}

		@Override
		public boolean isOverworld() {
			return level.dimension().equals(Level.OVERWORLD);
		}

		@Override
		public boolean isLoadedAndInsideBorder(BlockPos pos) {
			return level.isLoaded(pos) && level.getWorldBorder().isWithinBounds(pos);
		}

		@Override
		public boolean hasSolidSupport(BlockPos pos) {
			return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
		}

		@Override
		public boolean isPassableAndNonHazardous(BlockPos pos) {
			return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
					&& level.getFluidState(pos).isEmpty();
		}

		@Override
		public boolean hasActiveEncounter(UUID ownerUuid, BlockPos deskPos) {
			boolean persistedOwnerActive = CampaignSavedData.get(level).player(ownerUuid)
					.map(state -> state.status() == PlayerCampaignState.LectureStatus.ACTIVE
							|| state.activeEncounterRef() != null)
					.orElse(false);
			if (persistedOwnerActive) {
				return true;
			}
			return LectureEncounterManager.activeRuntimeSnapshots(level.getServer()).stream()
					.anyMatch(runtime -> runtime.level() == level
							&& (runtime.ownerUuid().equals(ownerUuid) || runtime.deskPos().equals(deskPos)));
		}

		@Override
		public boolean hasSpawnCapacity(UUID ownerUuid, BlockPos professorFeet) {
			if (!ModEntities.PROFESSOR.canSummon() || !ModEntities.PROFESSOR.canSpawn(level)) {
				return false;
			}
			Vec3 spawn = Vec3.atBottomCenterOf(professorFeet);
			AABB spawnBounds = ModEntities.PROFESSOR.getSpawnAABB(spawn.x, spawn.y, spawn.z);
			if (!level.getWorldBorder().isWithinBounds(spawnBounds)
					|| !level.noCollision(spawnBounds)
					|| !level.getEntities(null, spawnBounds).isEmpty()) {
				return false;
			}
			return LectureEncounterManager.activeRuntimeSnapshots(level.getServer()).stream()
					.noneMatch(runtime -> runtime.level() == level && runtime.ownerUuid().equals(ownerUuid));
		}
	}

	private ArenaValidator() {
	}
}
