package dev.developershell.item;

import dev.developershell.campaign.CampaignService;
import dev.developershell.campaign.CampaignTransition;
import dev.developershell.campaign.PlayerCampaignState;
import dev.developershell.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server-owned practical reward with one bounded forward slide per durable cooldown. */
public final class InfiniteSlidesRemoteItem extends Item {
	public static final int COOLDOWN_TICKS = 400;
	static final double EFFECT_RANGE_BLOCKS = 6.0D;
	static final int MAX_TARGETS = 6;
	static final double HORIZONTAL_IMPULSE = 0.9D;
	static final double VERTICAL_IMPULSE = 0.15D;
	static final int MAX_CUE_PARTICLES = 12;

	private static final double SLIDE_HALF_WIDTH_BLOCKS = 1.25D;
	private static final double SLIDE_VERTICAL_REACH_BLOCKS = 2.0D;
	private static final String OWNER_TAG = "developers_hell_remote_owner";
	private static final String PROJECTION_TAG = "developers_hell_remote_projection";
	private static final String TOOLTIP_EFFECT_KEY =
			"tooltip.developers_hell.infinite_slides_remote.effect";
	private static final String TOOLTIP_COOLDOWN_KEY =
			"tooltip.developers_hell.infinite_slides_remote.cooldown";
	private static final String FIRED_KEY = "message.developers_hell.remote.fired";
	private static final String RECHARGING_KEY = "message.developers_hell.remote.recharging";
	private static final String UNAUTHORIZED_KEY = "message.developers_hell.remote.unauthorized";
	private static boolean useCallbackRegistered;

	public InfiniteSlidesRemoteItem(Properties properties) {
		super(properties);
	}

	/** Creates the one owner-bound physical projection for a committed first victory. */
	public static ItemStack bound(Binding binding) {
		Objects.requireNonNull(binding, "binding");
		ItemStack stack = new ItemStack(ModItems.INFINITE_SLIDES_REMOTE);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.store(OWNER_TAG, UUIDUtil.CODEC, binding.ownerUuid());
			tag.store(PROJECTION_TAG, UUIDUtil.CODEC, binding.projectionUuid());
		});
		return stack;
	}

	/** Reads a complete owner/projection binding; incomplete or unrelated stacks fail closed. */
	public static Optional<Binding> binding(ItemStack stack) {
		Objects.requireNonNull(stack, "stack");
		if (stack.isEmpty() || stack.getItem() != ModItems.INFINITE_SLIDES_REMOTE) {
			return Optional.empty();
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null || customData.isEmpty()) {
			return Optional.empty();
		}
		CompoundTag tag = customData.copyTag();
		Optional<UUID> ownerUuid = tag.read(OWNER_TAG, UUIDUtil.CODEC);
		Optional<UUID> projectionUuid = tag.read(PROJECTION_TAG, UUIDUtil.CODEC);
		return ownerUuid.isPresent() && projectionUuid.isPresent()
				? Optional.of(new Binding(ownerUuid.get(), projectionUuid.get()))
				: Optional.empty();
	}

	/**
	 * Identifies only the pre-schema-2 Remote shape. A partially tagged stack remains malformed
	 * and cannot be claimed by migration.
	 */
	public static boolean isUnboundLegacy(ItemStack stack) {
		Objects.requireNonNull(stack, "stack");
		if (stack.isEmpty() || stack.getItem() != ModItems.INFINITE_SLIDES_REMOTE) {
			return false;
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null || customData.isEmpty()) {
			return true;
		}
		CompoundTag tag = customData.copyTag();
		return !tag.contains(OWNER_TAG) && !tag.contains(PROJECTION_TAG);
	}

	/** Binds one owner-held legacy Remote in place while preserving unrelated components. */
	public static boolean bindLegacy(ItemStack stack, Binding binding) {
		Objects.requireNonNull(stack, "stack");
		Objects.requireNonNull(binding, "binding");
		if (!isUnboundLegacy(stack)) {
			return false;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.store(OWNER_TAG, UUIDUtil.CODEC, binding.ownerUuid());
			tag.store(PROJECTION_TAG, UUIDUtil.CODEC, binding.projectionUuid());
		});
		return InfiniteSlidesRemoteItem.binding(stack).filter(binding::equals).isPresent();
	}

	/**
	 * Registers the pre-vanilla item callback once. Minecraft checks native cooldown before
	 * calling {@link #use}; this callback preserves rejected-use feedback while leaving the
	 * persisted deadline authoritative.
	 */
	public synchronized void registerUseCallback() {
		if (useCallbackRegistered) {
			return;
		}
		useCallbackRegistered = true;
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (level.isClientSide() || stack.getItem() != this) {
				return InteractionResult.PASS;
			}
			return use(level, player, hand);
		});
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
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
		Optional<Binding> binding = binding(stack);
		Optional<PlayerCampaignState> state = CampaignService.snapshot(serverLevel, serverPlayer.getUUID());
		if (binding.isEmpty()
				|| state.isEmpty()
				|| !binding.get().ownerUuid().equals(serverPlayer.getUUID())
				|| state.get().status() != PlayerCampaignState.LectureStatus.PASSED
				|| !state.get().remoteIssued()
				|| state.get().remoteProjectionPending()
				|| !binding.get().projectionUuid().equals(state.get().remoteProjectionUuid())) {
			serverPlayer.sendOverlayMessage(Component.translatable(UNAUTHORIZED_KEY));
			return InteractionResult.SUCCESS_SERVER;
		}

		CampaignTransition transition = CampaignService.commitRemoteCooldown(
				serverPlayer,
				intent -> applyAcceptedIntent(serverLevel, serverPlayer, stack, intent)
		);
		if (transition.accepted()) {
			return InteractionResult.SUCCESS_SERVER;
		}

		PlayerCampaignState resultingState = transition.nextState().orElse(null);
		if (resultingState != null && transition.reason().equals("remote_on_cooldown")) {
			int seconds = Cooldown.remainingSeconds(
					resultingState.remoteCooldownUntilGameTime(),
					serverLevel.getGameTime()
			);
			serverPlayer.sendOverlayMessage(Component.translatable(RECHARGING_KEY, seconds));
		}
		else {
			serverPlayer.sendOverlayMessage(Component.translatable(UNAUTHORIZED_KEY));
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public void appendHoverText(
			ItemStack stack,
			TooltipContext context,
			TooltipDisplay display,
			Consumer<Component> tooltip,
			TooltipFlag flag
	) {
		tooltip.accept(Component.translatable(TOOLTIP_EFFECT_KEY));
		tooltip.accept(Component.translatable(TOOLTIP_COOLDOWN_KEY));
	}

	/** Pure server-tick arithmetic isolated from Minecraft registry bootstrap for Loader JUnit. */
	public static final class Cooldown {
		/** Exact logical-server deadline for one accepted activation. */
		public static long deadline(long gameTime) {
			return Math.addExact(gameTime, COOLDOWN_TICKS);
		}

		/** Ceiling whole seconds for redundant rejected-use feedback. */
		public static int remainingSeconds(long deadlineGameTime, long gameTime) {
			long remaining = positiveRemaining(deadlineGameTime, gameTime);
			long seconds = remaining / 20L + (remaining % 20L == 0L ? 0L : 1L);
			return (int) Math.min(Integer.MAX_VALUE, seconds);
		}

		/** Native overlay duration reconstructed from one persisted deadline. */
		public static int restoredOverlayTicks(long deadlineGameTime, long gameTime) {
			return (int) Math.min(COOLDOWN_TICKS, positiveRemaining(deadlineGameTime, gameTime));
		}

		/** Pure once-per-deadline edge used by the lifecycle projection plan. */
		public static boolean readyNoticeDue(
				long deadlineGameTime,
				long noticedDeadlineGameTime,
				long gameTime
		) {
			return deadlineGameTime > 0L
					&& gameTime >= deadlineGameTime
					&& noticedDeadlineGameTime < deadlineGameTime;
		}

		private static long positiveRemaining(long deadlineGameTime, long gameTime) {
			return deadlineGameTime <= gameTime ? 0L : deadlineGameTime - gameTime;
		}

		private Cooldown() {
		}
	}

	private static void applyAcceptedIntent(
			ServerLevel level,
			ServerPlayer owner,
			ItemStack stack,
			CampaignTransition.EffectIntent intent
	) {
		if (!(intent instanceof CampaignTransition.EffectIntent.ApplyRemoteCooldown cooldown)
				|| !cooldown.ownerUuid().equals(owner.getUUID())) {
			return;
		}

		owner.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		projectBoundedSlide(level, owner);
		owner.sendOverlayMessage(Component.translatable(FIRED_KEY));
	}

	private static void projectBoundedSlide(ServerLevel level, ServerPlayer owner) {
		Vec3 forward = owner.getDirection().getUnitVec3();
		Vec3 sweep = forward.scale(EFFECT_RANGE_BLOCKS);
		AABB bounds = owner.getBoundingBox()
				.expandTowards(sweep)
				.inflate(SLIDE_HALF_WIDTH_BLOCKS, SLIDE_VERTICAL_REACH_BLOCKS, SLIDE_HALF_WIDTH_BLOCKS);
		List<LivingEntity> targets = new ArrayList<>(MAX_TARGETS);
		level.getEntities(
				EntityTypeTest.<Entity, LivingEntity>forClass(LivingEntity.class),
				bounds,
				target -> isBoundedTarget(owner, target, forward),
				targets,
				MAX_TARGETS
		);

		for (LivingEntity target : targets) {
			target.push(
					forward.x * HORIZONTAL_IMPULSE,
					VERTICAL_IMPULSE,
					forward.z * HORIZONTAL_IMPULSE
			);
		}

		Vec3 cue = owner.getEyePosition().add(forward.scale(1.5D));
		level.sendParticles(
				ParticleTypes.GUST,
				cue.x,
				cue.y,
				cue.z,
				MAX_CUE_PARTICLES,
				0.25D,
				0.15D,
				0.25D,
				0.02D
		);
		level.playSound(
				null,
				owner.getX(),
				owner.getY(),
				owner.getZ(),
				SoundEvents.BREEZE_SLIDE,
				SoundSource.PLAYERS,
				0.8F,
				1.1F
		);
	}

	private static boolean isBoundedTarget(ServerPlayer owner, LivingEntity target, Vec3 forward) {
		if (target == owner
				|| !target.isAlive()
				|| target.isSpectator()
				|| !target.isPushable()
				|| target.distanceToSqr(owner) > EFFECT_RANGE_BLOCKS * EFFECT_RANGE_BLOCKS) {
			return false;
		}
		Vec3 offset = target.position().subtract(owner.position());
		double forwardDistance = offset.dot(forward);
		if (forwardDistance <= 0.0D || forwardDistance > EFFECT_RANGE_BLOCKS) {
			return false;
		}
		Vec3 lateral = offset.subtract(forward.scale(forwardDistance));
		return lateral.horizontalDistanceSqr()
				<= SLIDE_HALF_WIDTH_BLOCKS * SLIDE_HALF_WIDTH_BLOCKS;
	}

	/** Exact identity of the only retryable first-victory Remote projection. */
	public record Binding(UUID ownerUuid, UUID projectionUuid) {
		public Binding {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			Objects.requireNonNull(projectionUuid, "projectionUuid");
		}
	}

}
