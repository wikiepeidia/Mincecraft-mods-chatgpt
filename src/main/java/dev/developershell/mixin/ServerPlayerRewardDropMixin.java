package dev.developershell.mixin;

import dev.developershell.lecture.RewardService;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Brackets vanilla selected-slot throws and whole-menu close transactions. */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerRewardDropMixin {
	@Inject(method = "drop(Z)V", at = @At("HEAD"))
	private void developersHell$stageSelectedDrop(boolean dropAll, CallbackInfo callback) {
		RewardService.onQDropStart((ServerPlayer) (Object) this);
	}

	@Inject(method = "drop(Z)V", at = @At("RETURN"))
	private void developersHell$finishSelectedDrop(boolean dropAll, CallbackInfo callback) {
		RewardService.onQDropComplete((ServerPlayer) (Object) this);
	}

	@Inject(method = "doCloseContainer()V", at = @At("HEAD"))
	private void developersHell$stageMenuClose(CallbackInfo callback) {
		RewardService.onMenuCloseStart((ServerPlayer) (Object) this);
	}

	@Inject(method = "doCloseContainer()V", at = @At("RETURN"))
	private void developersHell$finishMenuClose(CallbackInfo callback) {
		RewardService.onMenuCloseComplete((ServerPlayer) (Object) this);
	}
}
