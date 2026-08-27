package dev.developershell.campaign;

import dev.developershell.lecture.ArenaValidationResult;
import net.minecraft.server.level.ServerLevel;
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

	/** Installs one already-decoded migration fixture without exposing mutable production APIs. */
	public static void replaceState(ServerLevel level, PlayerCampaignState state) {
		CampaignSavedData data = CampaignSavedData.get(level);
		if (!data.replace(state)) {
			throw new IllegalStateException("GameTest campaign fixture rejected state");
		}
		data.setDirty();
	}

	private CampaignServiceGameTestAccess() {
	}
}
