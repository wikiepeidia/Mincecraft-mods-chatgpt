package dev.developershell.python;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Thin item adapter; all install authority remains in the server runtime and pure engine. */
public final class PipWandItem extends Item {
	public PipWandItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return PythonToolsRuntime.usePipWand(level, player, hand, this);
	}
}
