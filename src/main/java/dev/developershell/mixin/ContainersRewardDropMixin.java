package dev.developershell.mixin;

import dev.developershell.lecture.RewardService;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Brackets all vanilla container-content drops before a destroyed container disappears. */
@Mixin(Containers.class)
abstract class ContainersRewardDropMixin {
	@Inject(
			method = "dropContents(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/Container;)V",
			at = @At("HEAD")
	)
	private static void developersHell$stageContainerDrop(
			Level level,
			double x,
			double y,
			double z,
			Container container,
			CallbackInfo callback
	) {
		RewardService.onContainerDropStart(level, container);
	}

	@Inject(
			method = "dropContents(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/Container;)V",
			at = @At("RETURN")
	)
	private static void developersHell$finishContainerDrop(
			Level level,
			double x,
			double y,
			double z,
			Container container,
			CallbackInfo callback
	) {
		RewardService.onContainerDropComplete(container);
	}
}
