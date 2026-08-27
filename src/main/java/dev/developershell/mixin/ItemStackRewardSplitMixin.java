package dev.developershell.mixin;

import dev.developershell.lecture.RewardService;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records the exact split output derived from an authenticated vanilla source object. */
@Mixin(ItemStack.class)
abstract class ItemStackRewardSplitMixin {
	@Inject(
			method = "split(I)Lnet/minecraft/world/item/ItemStack;",
			at = @At("RETURN")
	)
	private void developersHell$recordRewardSplit(
			int amount,
			CallbackInfoReturnable<ItemStack> callback
	) {
		RewardService.onRewardSourceSplit(
				(ItemStack) (Object) this, callback.getReturnValue());
	}
}
