package dev.developershell.mixin;

import dev.developershell.lecture.RewardService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Completes or compensates the state-first reward transfer around Minecraft's entity add. */
@Mixin(ServerLevel.class)
abstract class ServerLevelRewardAdmissionMixin {
	@Inject(method = "addEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"))
	private void developersHell$stageRewardAdmission(
			Entity entity,
			CallbackInfoReturnable<Boolean> callback
	) {
		RewardService.onEntityAddStart(entity);
	}

	@Inject(method = "addEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"))
	private void developersHell$completeRewardAdmission(
			Entity entity,
			CallbackInfoReturnable<Boolean> callback
	) {
		RewardService.onEntityAddResult(entity, callback.getReturnValueZ());
	}
}
