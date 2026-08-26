package dev.developershell.item;

import dev.developershell.campaign.CampaignService;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class CursedInternshipContractItem extends Item {
	private boolean interactionRegistered;

	public CursedInternshipContractItem(Properties properties) {
		super(properties);
	}

	/**
	 * Empty lecterns consume vanilla empty-hand handling before Item.useOn. Register the
	 * Fabric pre-block callback so ordinary Contract use reaches the logical server.
	 */
	public synchronized void registerInteraction() {
		if (interactionRegistered) {
			return;
		}
		UseBlockCallback.EVENT.register(this::interactWithBlock);
		interactionRegistered = true;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		BlockHitResult hit = new BlockHitResult(
				context.getClickLocation(),
				context.getClickedFace(),
				context.getClickedPos(),
				context.isInside()
		);
		return interactWithBlock(player, context.getLevel(), context.getHand(), hit);
	}

	private InteractionResult interactWithBlock(
			Player player,
			Level level,
			InteractionHand hand,
			BlockHitResult hit
	) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.getItem() != this) {
			return InteractionResult.PASS;
		}
		BlockState state = level.getBlockState(hit.getBlockPos());
		if (!state.is(Blocks.LECTERN)) {
			return InteractionResult.PASS;
		}
		if (player.isSpectator()) {
			return InteractionResult.FAIL;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.FAIL;
		}
		Direction facing = state.getValue(LecternBlock.FACING);
		return CampaignService.start(serverPlayer, hit.getBlockPos(), facing, stack)
				? InteractionResult.SUCCESS_SERVER
				: InteractionResult.FAIL;
	}
}
