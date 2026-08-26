package dev.developershell.campaign;

import dev.developershell.lecture.LectureEncounterManager;
import dev.developershell.registry.ModItems;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Logical-server transaction boundary for accepted campaign events and their effects. */
public final class CampaignService {
	private static final int ARENA_FORWARD = 17;
	private static final int ARENA_HALF_WIDTH = 8;
	private static final int REQUIRED_HEADROOM = 4;

	public static boolean start(
			ServerPlayer player,
			BlockPos deskPos,
			Direction deskFacing,
			ItemStack contract
	) {
		if (!(player.level() instanceof ServerLevel level)
				|| !level.getServer().isSameThread()
				|| player.isSpectator()
				|| contract.isEmpty()
				|| contract.getItem() != ModItems.CURSED_UNPAID_INTERNSHIP_CONTRACT) {
			return false;
		}
		Optional<BlockPos> retryPos = validateArena(level, deskPos, deskFacing);
		if (retryPos.isEmpty()) {
			return false;
		}

		CampaignSavedData data = CampaignSavedData.get(level);
		int nextAttempt;
		try {
			nextAttempt = data.player(player.getUUID())
					.map(state -> Math.addExact(state.attemptCount(), 1))
					.orElse(1);
		}
		catch (ArithmeticException exception) {
			return false;
		}
		UUID ownerUuid = player.getUUID();
		UUID encounterUuid = CampaignSavedData.deterministicUuid("encounter", ownerUuid, deskPos, nextAttempt);
		UUID professorUuid = CampaignSavedData.deterministicUuid("professor", ownerUuid, deskPos, nextAttempt);
		CampaignEvent.Start start = new CampaignEvent.Start(
				ownerUuid,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				deskPos,
				deskFacing,
				retryPos.get(),
				encounterUuid,
				professorUuid
		);
		boolean[] runtimeStarted = {false};
		CampaignTransition transition = apply(data, start, effect -> {
			if (effect instanceof CampaignTransition.EffectIntent.StartEncounter) {
				runtimeStarted[0] = LectureEncounterManager.start(
						level,
						player,
						data.player(ownerUuid).orElseThrow()
				);
			}
		});
		if (!transition.accepted()) {
			return false;
		}
		if (!runtimeStarted[0]) {
			apply(
					data,
					new CampaignEvent.Terminal(ownerUuid, encounterUuid, CampaignEvent.TerminalReason.ABORT),
					effect -> {
						if (effect instanceof CampaignTransition.EffectIntent.CleanupEncounter cleanup) {
							LectureEncounterManager.cleanup(cleanup.encounterUuid());
						}
					}
			);
			return false;
		}
		contract.shrink(1);
		return true;
	}

	/**
	 * Applies a matching victory exactly once. The durable ledger is dirty before cleanup,
	 * inventory grants, or boss presentation changes.
	 */
	public static boolean victory(ServerLevel level, UUID ownerUuid, UUID encounterUuid) {
		if (!level.getServer().isSameThread()) {
			return false;
		}
		Optional<ServerPlayer> participant = LectureEncounterManager.participant(encounterUuid);
		if (participant.isEmpty()
				|| !participant.get().getUUID().equals(ownerUuid)
				|| participant.get().level() != level) {
			return false;
		}

		CampaignSavedData data = CampaignSavedData.get(level);
		ServerPlayer player = participant.get();
		CampaignTransition transition = apply(
				data,
				new CampaignEvent.Victory(ownerUuid, encounterUuid),
				effect -> {
					if (effect instanceof CampaignTransition.EffectIntent.CleanupEncounter cleanup) {
						LectureEncounterManager.finishVictory(cleanup.encounterUuid());
					}
					else if (effect instanceof CampaignTransition.EffectIntent.GrantFirstRewards) {
						player.getInventory().add(new ItemStack(ModItems.ATTENDANCE_SHEET));
						player.getInventory().add(new ItemStack(ModItems.INFINITE_SLIDES_REMOTE));
					}
				}
		);
		if (!transition.accepted()) {
			return false;
		}
		return true;
	}

	/**
	 * Sole stateful campaign seam: reduce once, replace the durable record, mark it dirty, and only
	 * then dispatch immutable effects. Rejected events never write and never dispatch.
	 */
	public static CampaignTransition apply(
			ServerLevel level,
			CampaignEvent event,
			Consumer<CampaignTransition.EffectIntent> effectConsumer
	) {
		java.util.Objects.requireNonNull(level, "level");
		java.util.Objects.requireNonNull(event, "event");
		java.util.Objects.requireNonNull(effectConsumer, "effectConsumer");
		if (!level.getServer().isSameThread()) {
			return CampaignTransition.noOp(Optional.empty(), "wrong_thread");
		}
		return apply(CampaignSavedData.get(level), event, effectConsumer);
	}

	static CampaignTransition apply(
			CampaignSavedData data,
			CampaignEvent event,
			Consumer<CampaignTransition.EffectIntent> effectConsumer
	) {
		java.util.Objects.requireNonNull(data, "data");
		java.util.Objects.requireNonNull(event, "event");
		java.util.Objects.requireNonNull(effectConsumer, "effectConsumer");
		Optional<PlayerCampaignState> current = data.player(event.ownerUuid());
		if (!data.isWritableSchema()) {
			return CampaignTransition.noOp(current, "read_only_data");
		}
		if (event instanceof CampaignEvent.Start start
				&& data.hasActiveDeskForOther(start.ownerUuid(), start.deskDimension(), start.deskPos())) {
			return CampaignTransition.noOp(current, "desk_occupied");
		}

		CampaignTransition transition = CampaignReducer.reduce(current, event);
		if (!transition.accepted()) {
			return transition;
		}
		if (!data.replace(transition.nextState().orElseThrow())) {
			return CampaignTransition.noOp(current, "persistence_rejected");
		}
		data.setDirty();
		transition.intents().forEach(effectConsumer);
		return transition;
	}

	/**
	 * Admits Professor damage only while entity, runtime participant, attacker, and durable
	 * encounter identity all describe the same active owner window.
	 */
	public static boolean canDamageProfessor(
			ServerLevel level,
			UUID ownerUuid,
			UUID encounterUuid,
			UUID attackerUuid
	) {
		if (!level.getServer().isSameThread()
				|| ownerUuid == null
				|| encounterUuid == null
				|| !ownerUuid.equals(attackerUuid)) {
			return false;
		}
		Optional<ServerPlayer> participant = LectureEncounterManager.participant(encounterUuid);
		if (participant.isEmpty()
				|| !participant.get().getUUID().equals(ownerUuid)
				|| participant.get().level() != level) {
			return false;
		}
		CampaignSavedData data = CampaignSavedData.get(level);
		return data.isWritableSchema()
				&& data.player(ownerUuid)
						.map(state -> state.matchesActiveEncounter(ownerUuid, encounterUuid))
						.orElse(false);
	}

	private static Optional<BlockPos> validateArena(ServerLevel level, BlockPos deskPos, Direction deskFacing) {
		if (!level.dimension().equals(Level.OVERWORLD) || !deskFacing.getAxis().isHorizontal()) {
			return Optional.empty();
		}
		BlockState deskState = level.getBlockState(deskPos);
		if (!deskState.is(Blocks.LECTERN) || deskState.getValue(LecternBlock.FACING) != deskFacing) {
			return Optional.empty();
		}

		Direction right = deskFacing.getClockWise();
		for (int forward = 1; forward <= ARENA_FORWARD; forward++) {
			for (int lateral = -ARENA_HALF_WIDTH; lateral <= ARENA_HALF_WIDTH; lateral++) {
				BlockPos floor = deskPos.relative(deskFacing, forward).relative(right, lateral).below();
				if (!isLoadedAndInside(level, floor)
						|| !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
					return Optional.empty();
				}
				for (int height = 1; height <= REQUIRED_HEADROOM; height++) {
					BlockPos headroom = floor.above(height);
					if (!isLoadedAndInside(level, headroom)
							|| !level.getBlockState(headroom).getCollisionShape(level, headroom).isEmpty()
							|| !level.getFluidState(headroom).isEmpty()) {
						return Optional.empty();
					}
				}
			}
		}
		return findRetryPos(level, deskPos, deskFacing);
	}

	private static Optional<BlockPos> findRetryPos(ServerLevel level, BlockPos deskPos, Direction deskFacing) {
		Direction right = deskFacing.getClockWise();
		for (int distance = 2; distance <= 5; distance++) {
			for (int lateral = 0; lateral <= 2; lateral++) {
				for (int sign : new int[] {1, -1}) {
					BlockPos candidate = deskPos.relative(deskFacing.getOpposite(), distance).relative(right, lateral * sign);
					if (isSafeRetry(level, candidate)) {
						return Optional.of(candidate.immutable());
					}
				}
			}
		}
		return Optional.empty();
	}

	private static boolean isSafeRetry(ServerLevel level, BlockPos position) {
		BlockPos floor = position.below();
		return isLoadedAndInside(level, floor)
				&& isLoadedAndInside(level, position.above())
				&& level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
				&& level.getBlockState(position).getCollisionShape(level, position).isEmpty()
				&& level.getBlockState(position.above()).getCollisionShape(level, position.above()).isEmpty()
				&& level.getFluidState(position).isEmpty()
				&& level.getFluidState(position.above()).isEmpty();
	}

	private static boolean isLoadedAndInside(ServerLevel level, BlockPos position) {
		return level.isLoaded(position) && level.getWorldBorder().isWithinBounds(position);
	}

	private CampaignService() {
	}
}
