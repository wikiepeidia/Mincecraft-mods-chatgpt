package dev.developershell.item;

import dev.developershell.lecture.ArenaValidationResult;
import dev.developershell.lecture.ArenaValidator;
import dev.developershell.server.DevelopersHellRuntime.CampaignServiceAdapter;
import java.util.Objects;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.Direction;
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

public final class CursedInternshipContractItem extends Item {
	private static final String WRONG_TARGET_KEY = "message.developers_hell.contract.find_lectern";
	private static final String CAMPAIGN_DISABLED_KEY = "message.developers_hell.campaign.disabled";
	private static final String LECTERN_TOOLTIP_KEY = "tooltip.developers_hell.contract.lectern";
	private static final String LECTURE_TOOLTIP_KEY = "tooltip.developers_hell.contract.lecture";
	private static final String BLOCKS_TOOLTIP_KEY = "tooltip.developers_hell.contract.blocks";

	private boolean interactionRegistered;
	private volatile CampaignServiceAdapter campaignService;

	public CursedInternshipContractItem(Properties properties) {
		super(properties);
	}

	/**
	 * Empty lecterns consume vanilla empty-hand handling before Item.useOn. Register the
	 * Fabric pre-block callback so ordinary Contract use reaches the logical server.
	 */
	public synchronized void registerInteraction(CampaignServiceAdapter campaignService) {
		Objects.requireNonNull(campaignService, "campaignService");
		if (interactionRegistered) {
			if (this.campaignService != campaignService) {
				throw new IllegalStateException("Contract interaction already bound to another runtime");
			}
			return;
		}
		this.campaignService = campaignService;
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

	@Override
	public void appendHoverText(
			ItemStack stack,
			TooltipContext context,
			TooltipDisplay display,
			Consumer<Component> tooltip,
			TooltipFlag flag
	) {
		tooltip.accept(Component.translatable(LECTERN_TOOLTIP_KEY));
		tooltip.accept(Component.translatable(LECTURE_TOOLTIP_KEY));
		tooltip.accept(Component.translatable(BLOCKS_TOOLTIP_KEY));
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
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.translatable(WRONG_TARGET_KEY));
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.FAIL;
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
		CampaignServiceAdapter service = campaignService;
		if (service == null) {
			return InteractionResult.FAIL;
		}
		if (!service.campaignEnabled()) {
			serverPlayer.sendSystemMessage(Component.translatable(CAMPAIGN_DISABLED_KEY));
			return InteractionResult.FAIL;
		}
		Direction facing = state.getValue(LecternBlock.FACING);
		ArenaValidationResult validation = ArenaValidator.validate(
				(ServerLevel) level,
				serverPlayer,
				hit.getBlockPos(),
				facing
		);
		if (validation instanceof ArenaValidationResult.Rejected rejected) {
			serverPlayer.sendSystemMessage(Component.translatable(rejected.reason().translationKey()));
			return InteractionResult.FAIL;
		}
		ArenaValidationResult start = service.start(
				serverPlayer,
				(ArenaValidationResult.Accepted) validation,
				stack
		);
		if (start instanceof ArenaValidationResult.Rejected rejected) {
			serverPlayer.sendSystemMessage(Component.translatable(rejected.reason().translationKey()));
			return InteractionResult.FAIL;
		}
		return InteractionResult.SUCCESS_SERVER;
	}
}
