package dev.developershell.lecture;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Generated-source-set-only access to the package-private projection fault seam. */
public final class RewardServiceGameTestAccess {
	public static RewardService.Outcome reconcilePending(
			ServerPlayer owner,
			Predicate<ItemStack> forcedFailure
	) {
		return RewardService.reconcilePending(
				Objects.requireNonNull(owner, "owner"),
				Objects.requireNonNull(forcedFailure, "forcedFailure")
		);
	}

	public static void beginDimensionTransfer(ItemEntity source) {
		RewardService.beginDimensionTransferForGameTest(
				Objects.requireNonNull(source, "source"));
	}

	public static void suppressNextDimensionUnload(ItemEntity source) {
		RewardService.suppressNextDimensionUnloadForGameTest(
				Objects.requireNonNull(source, "source"));
	}

	public static boolean hasPendingDimensionTransfer(UUID entityUuid) {
		return RewardService.hasPendingDimensionTransferForGameTest(
				Objects.requireNonNull(entityUuid, "entityUuid"));
	}

	private RewardServiceGameTestAccess() {
	}
}
