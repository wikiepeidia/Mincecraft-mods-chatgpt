package dev.developershell.mixin;

import dev.developershell.lecture.RewardService;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Brackets the real server menu click mutation used by container-click packets. */
@Mixin(AbstractContainerMenu.class)
abstract class AbstractContainerMenuRewardDropMixin {
	@Inject(
			method = "clicked(IILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V",
			at = @At("HEAD")
	)
	private void developersHell$stageMenuDrop(
			int slotIndex,
			int button,
			ContainerInput input,
			Player player,
			CallbackInfo callback
	) {
		RewardService.onMenuClickStart(
				(AbstractContainerMenu) (Object) this, slotIndex, input, player);
	}

	@Inject(
			method = "clicked(IILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V",
			at = @At("RETURN")
	)
	private void developersHell$finishMenuDrop(
			int slotIndex,
			int button,
			ContainerInput input,
			Player player,
			CallbackInfo callback
	) {
		RewardService.onMenuClickComplete((AbstractContainerMenu) (Object) this);
	}
}
