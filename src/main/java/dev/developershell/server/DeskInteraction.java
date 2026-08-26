package dev.developershell.server;

import dev.developershell.campaign.CampaignService;
import dev.developershell.campaign.PlayerCampaignState;
import dev.developershell.item.RetakeFormItem;
import dev.developershell.lecture.RetakeService;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** One pre-block callback for empty-hand recovery at the saved Internship Desk. */
public final class DeskInteraction {
	private static final String RECOVERED_KEY = "message.developers_hell.retake.recovered";
	private static final String FALLBACK_KEY = "message.developers_hell.retake.fallback";
	private static final String ALREADY_KEY = "message.developers_hell.retake.already";
	private static final String NOTHING_KEY = "message.developers_hell.retake.nothing";
	private static boolean registered;

	public static synchronized void register() {
		if (registered) {
			return;
		}
		UseBlockCallback.EVENT.register(DeskInteraction::interact);
		registered = true;
	}

	private static InteractionResult interact(
			Player player,
			Level level,
			InteractionHand hand,
			BlockHitResult hit
	) {
		if (player.getItemInHand(hand).getItem() instanceof RetakeFormItem retakeForm) {
			return retakeForm.interactWithBlock(player, level, hand, hit);
		}
		if (!player.getItemInHand(hand).isEmpty()) {
			return InteractionResult.PASS;
		}
		BlockState block = level.getBlockState(hit.getBlockPos());
		if (!block.is(Blocks.LECTERN)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel serverLevel)
				|| !(player instanceof ServerPlayer serverPlayer)
				|| player.isSpectator()) {
			return InteractionResult.FAIL;
		}

		Optional<PlayerCampaignState> stateView = CampaignService.snapshot(serverLevel, player.getUUID());
		if (stateView.isEmpty()) {
			return InteractionResult.PASS;
		}
		PlayerCampaignState state = stateView.get();
		boolean matchingDesk = state.ownerUuid().equals(player.getUUID())
				&& state.deskDimension().equals(PlayerCampaignState.OVERWORLD_DIMENSION)
				&& serverLevel.dimension().equals(Level.OVERWORLD)
				&& state.deskPos().equals(hit.getBlockPos())
				&& state.deskFacing() == block.getValue(LecternBlock.FACING);
		if (!matchingDesk || state.status() != PlayerCampaignState.LectureStatus.RETAKE_READY) {
			serverPlayer.sendSystemMessage(Component.translatable(NOTHING_KEY));
			return InteractionResult.SUCCESS_SERVER;
		}

		RetakeService.Outcome outcome = RetakeService.forLevel(serverLevel).recover(player.getUUID());
		String messageKey = switch (outcome) {
			case INVENTORY_ISSUED -> RECOVERED_KEY;
			case FALLBACK_ISSUED -> FALLBACK_KEY;
			case ALREADY_PRESENT -> ALREADY_KEY;
			default -> NOTHING_KEY;
		};
		serverPlayer.sendSystemMessage(Component.translatable(messageKey));
		return InteractionResult.SUCCESS_SERVER;
	}

	private DeskInteraction() {
	}
}
