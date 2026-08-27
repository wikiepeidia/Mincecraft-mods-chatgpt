package dev.developershell.mixin;

import dev.developershell.lecture.RewardService;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores only exact reward stacks whose vanilla death entity add was rejected. */
@Mixin(Inventory.class)
abstract class InventoryRewardDropMixin {
	@Inject(method = "dropAll()V", at = @At("HEAD"))
	private void developersHell$stageRewardDrops(CallbackInfo callback) {
		RewardService.onDeathInventoryDropStart((Inventory) (Object) this);
	}

	@Inject(method = "dropAll()V", at = @At("RETURN"))
	private void developersHell$restoreRejectedRewardDrops(CallbackInfo callback) {
		RewardService.onDeathInventoryDropComplete((Inventory) (Object) this);
	}
}
