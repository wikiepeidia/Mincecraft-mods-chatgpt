package dev.developershell.python;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Thin item adapter for deterministic conflict clearing/isolation. */
public final class VenvFlaskItem extends Item {
	public VenvFlaskItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return PythonToolsRuntime.useVenvFlask(level, player, hand, this);
	}
}
