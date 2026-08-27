package dev.developershell.mixin;

import dev.developershell.lecture.RewardService;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Persists loaded-to-loaded entity section movement absent from Fabric lifecycle events. */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
abstract class ServerLevelRewardSectionMixin {
	@Inject(
			method = "onSectionChange(Lnet/minecraft/world/entity/Entity;)V",
			at = @At("HEAD")
	)
	private void developersHell$recordRewardSectionChange(Entity entity, CallbackInfo callback) {
		RewardService.onEntitySectionChange(entity);
	}
}
