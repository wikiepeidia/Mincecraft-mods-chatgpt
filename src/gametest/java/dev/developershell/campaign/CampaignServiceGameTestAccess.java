package dev.developershell.campaign;

import dev.developershell.lecture.ArenaValidationResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** GameTest-only bridge for the package-private runtime-start failure seam. */
public final class CampaignServiceGameTestAccess {
	public static ArenaValidationResult startWithRuntimeFailure(
			ServerPlayer player,
			ArenaValidationResult.Accepted arena,
			ItemStack contract
	) {
		return CampaignService.start(
				player,
				arena,
				contract,
				(level, owner, progress, afterSpawn) -> false
		);
	}

	private CampaignServiceGameTestAccess() {
	}
}
