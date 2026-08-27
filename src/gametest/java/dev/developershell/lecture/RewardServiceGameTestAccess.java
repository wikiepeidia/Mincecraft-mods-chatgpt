package dev.developershell.lecture;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
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

	private RewardServiceGameTestAccess() {
	}
}
