package dev.developershell.item;

import dev.developershell.campaign.CampaignService;
import dev.developershell.campaign.PlayerCampaignState;
import dev.developershell.lecture.ArenaValidationResult;
import dev.developershell.lecture.ArenaValidator;
import dev.developershell.lecture.RetakeService;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Owner-bound Retake request; the complete server transaction remains in RetakeService. */
public final class RetakeFormItem extends Item {
	private static final String TOOLTIP_KEY = "tooltip.developers_hell.retake_form.desk";
	private static final String NOTHING_KEY = "message.developers_hell.retake.nothing";

	public RetakeFormItem(Properties properties) {
		super(properties);
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

	@Override
	public void appendHoverText(
			ItemStack stack,
			TooltipContext context,
			TooltipDisplay display,
			Consumer<Component> tooltip,
			TooltipFlag flag
	) {
		tooltip.accept(Component.translatable(TOOLTIP_KEY));
	}

	/** Shared by Item.useOn and the pre-block Desk callback required by empty lecterns. */
	public InteractionResult interactWithBlock(
			Player player,
			Level level,
			InteractionHand hand,
			BlockHitResult hit
	) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.getItem() != this) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(level instanceof ServerLevel serverLevel)
				|| !(player instanceof ServerPlayer serverPlayer)
				|| player.isSpectator()) {
			return InteractionResult.FAIL;
		}

		BlockState block = level.getBlockState(hit.getBlockPos());
		Optional<PlayerCampaignState.RetakeKey> formKey = RetakeService.formKey(stack);
		Optional<PlayerCampaignState> stateView = CampaignService.snapshot(serverLevel, player.getUUID());
		if (!block.is(Blocks.LECTERN)
				|| formKey.isEmpty()
				|| !formKey.get().ownerUuid().equals(player.getUUID())
				|| stateView.isEmpty()) {
			return nothing(serverPlayer);
		}

		PlayerCampaignState state = stateView.get();
		boolean matchingAuthority = state.status() == PlayerCampaignState.LectureStatus.RETAKE_READY
				&& state.retakeKey().filter(formKey.get()::equals).isPresent()
				&& state.deskDimension().equals(PlayerCampaignState.OVERWORLD_DIMENSION)
				&& serverLevel.dimension().equals(Level.OVERWORLD)
				&& state.deskPos().equals(hit.getBlockPos())
				&& state.deskFacing() == block.getValue(LecternBlock.FACING);
		if (!matchingAuthority) {
			return nothing(serverPlayer);
		}

		ArenaValidationResult validation = ArenaValidator.validate(
				serverLevel,
				serverPlayer,
				hit.getBlockPos(),
				block.getValue(LecternBlock.FACING)
		);
		if (validation instanceof ArenaValidationResult.Rejected rejected) {
			serverPlayer.sendSystemMessage(Component.translatable(rejected.reason().translationKey()));
			return InteractionResult.FAIL;
		}

		RetakeService.Outcome outcome = RetakeService.forLevel(serverLevel).startRetake(
				serverPlayer,
				(ArenaValidationResult.Accepted) validation,
				stack
		);
		if (outcome != RetakeService.Outcome.RETRY_ACCEPTED) {
			return nothing(serverPlayer);
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	private static InteractionResult nothing(ServerPlayer player) {
		player.sendSystemMessage(Component.translatable(NOTHING_KEY));
		return InteractionResult.SUCCESS_SERVER;
	}
}
