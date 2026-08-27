package dev.developershell.python;

import dev.developershell.module.ModuleGate;
import dev.developershell.module.ModuleId;
import dev.developershell.registry.ModItems;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Server-authoritative Fabric shell around finite local Python joke simulations. */
public final class PythonToolsRuntime {
	public static final int MAX_ORE_BLOCKS = 16;
	public static final int MAX_VISITED_NODES = 128;
	public static final int MAX_ORE_RADIUS = 8;
	public static final long RECURSION_COOLDOWN_TICKS = 200L;

	private static final PipEnvironment PIP = new PipEnvironment();
	private static final BoundedOreTraversal ORE_TRAVERSAL = new BoundedOreTraversal();
	private static final BoundedOreTraversal.Limits ORE_LIMITS =
			new BoundedOreTraversal.Limits(MAX_ORE_BLOCKS, MAX_VISITED_NODES, MAX_ORE_RADIUS);
	private static final Set<String> ORE_BLOCKS = Set.of(
			"coal_ore", "deepslate_coal_ore", "copper_ore", "deepslate_copper_ore",
			"iron_ore", "deepslate_iron_ore", "gold_ore", "deepslate_gold_ore",
			"nether_gold_ore", "redstone_ore", "deepslate_redstone_ore", "emerald_ore",
			"deepslate_emerald_ore", "lapis_ore", "deepslate_lapis_ore", "diamond_ore",
			"deepslate_diamond_ore", "nether_quartz_ore", "ancient_debris"
	);
	private static volatile PythonToolsRuntime active;

	private final ModuleGate moduleGate;
	private final Set<UUID> breakReentry = ConcurrentHashMap.newKeySet();
	private boolean registered;

	public PythonToolsRuntime(ModuleGate moduleGate) {
		this.moduleGate = Objects.requireNonNull(moduleGate, "moduleGate");
	}

	public synchronized void register() {
		if (registered || active != null) {
			throw new IllegalStateException("Python tools runtime already registered");
		}
		registered = true;
		active = this;
		PlayerBlockBreakEvents.AFTER.register(this::afterBlockBreak);
	}

	public int giveDemoTools(ServerPlayer player) {
		Objects.requireNonNull(player, "player");
		if (!moduleGate.isEnabled(ModuleId.PYTHON_TOOLS)) {
			player.sendSystemMessage(Component.translatable("message.developers_hell.python.disabled"));
			return 0;
		}
		giveOrDrop(player, new ItemStack(ModItems.PIP_WAND));
		giveOrDrop(player, new ItemStack(ModItems.VENV_FLASK));
		giveOrDrop(player, new ItemStack(ModItems.PYTHON_PICKAXE));
		player.sendSystemMessage(Component.translatable("message.developers_hell.python.demo"));
		return 1;
	}

	static InteractionResult usePipWand(Level level, Player player, InteractionHand hand, Item expected) {
		if (!validHeldItem(player, hand, expected)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		PythonToolsRuntime runtime = active;
		if (!(level instanceof ServerLevel serverLevel)
				|| !(player instanceof ServerPlayer serverPlayer)
				|| player.isSpectator() || runtime == null) {
			return InteractionResult.FAIL;
		}
		if (!runtime.enabled(serverPlayer)) {
			return InteractionResult.SUCCESS_SERVER;
		}

		PythonToolsSavedData data = PythonToolsSavedData.get(serverLevel);
		PythonToolsState before = data.snapshot(serverPlayer.getUUID());
		if (serverPlayer.isShiftKeyDown()) {
			PythonToolsState next = PIP.cycleSelection(before);
			if (!data.commitIfCurrent(serverPlayer.getUUID(), before, next)) {
				serverPlayer.sendOverlayMessage(Component.translatable("message.developers_hell.python.retry"));
				return InteractionResult.SUCCESS_SERVER;
			}
			FakePackage selected = next.selectedPackage();
			serverPlayer.sendOverlayMessage(Component.translatable(
					"message.developers_hell.python.selected", selected.id(), selected.xpCost()));
			return InteractionResult.SUCCESS_SERVER;
		}

		int availableXp = serverPlayer.isCreative() ? Integer.MAX_VALUE : serverPlayer.experienceLevel;
		PipOutcome outcome = PIP.install(before, availableXp, serverLevel.getGameTime());
		if (outcome.changedFrom(before)
				&& !data.commitIfCurrent(serverPlayer.getUUID(), before, outcome.nextState())) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.developers_hell.python.retry"));
			return InteractionResult.SUCCESS_SERVER;
		}
		applyPipOutcome(serverPlayer, outcome);
		return InteractionResult.SUCCESS_SERVER;
	}

	static InteractionResult useVenvFlask(Level level, Player player, InteractionHand hand, Item expected) {
		if (!validHeldItem(player, hand, expected)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		PythonToolsRuntime runtime = active;
		if (!(level instanceof ServerLevel serverLevel)
				|| !(player instanceof ServerPlayer serverPlayer)
				|| player.isSpectator() || runtime == null) {
			return InteractionResult.FAIL;
		}
		if (!runtime.enabled(serverPlayer)) {
			return InteractionResult.SUCCESS_SERVER;
		}

		PythonToolsSavedData data = PythonToolsSavedData.get(serverLevel);
		PythonToolsState before = data.snapshot(serverPlayer.getUUID());
		PipOutcome outcome = PIP.useVenv(before, serverLevel.getGameTime());
		if (outcome.changedFrom(before)
				&& !data.commitIfCurrent(serverPlayer.getUUID(), before, outcome.nextState())) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.developers_hell.python.retry"));
			return InteractionResult.SUCCESS_SERVER;
		}
		applyVenvOutcome(serverPlayer, before, outcome);
		return InteractionResult.SUCCESS_SERVER;
	}

	private void afterBlockBreak(
			Level level,
			Player player,
			BlockPos origin,
			BlockState originState,
			BlockEntity blockEntity
	) {
		if (!(level instanceof ServerLevel serverLevel)
				|| !(player instanceof ServerPlayer serverPlayer)
				|| player.isSpectator()
				|| !moduleGate.isEnabled(ModuleId.PYTHON_TOOLS)
				|| serverPlayer.getMainHandItem().getItem() != ModItems.PYTHON_PICKAXE
				|| !breakReentry.add(serverPlayer.getUUID())) {
			return;
		}

		try {
			long gameTime = serverLevel.getGameTime();
			PythonToolsSavedData data = PythonToolsSavedData.get(serverLevel);
			PythonToolsState before = data.snapshot(serverPlayer.getUUID());
			RecursionCooldown cooldown = RecursionCooldown.restoreClamped(
					before.recursionCooldownUntilTick(), gameTime);
			if (cooldown.untilTick() != before.recursionCooldownUntilTick()) {
				PythonToolsState clamped = before.withRecursionCooldown(cooldown.untilTick());
				if (!data.commitIfCurrent(serverPlayer.getUUID(), before, clamped)) {
					return;
				}
				before = clamped;
			}
			if (!cooldown.ready(gameTime)) {
				serverPlayer.sendOverlayMessage(Component.translatable(
						"message.developers_hell.python.pickaxe.cooldown",
						remainingSeconds(cooldown.remaining(gameTime))));
				return;
			}

			BoundedOreTraversal.Position traversalOrigin = position(origin);
			BoundedOreTraversal.Plan plan = ORE_TRAVERSAL.plan(
					new MinecraftOreView(serverLevel, serverPlayer, origin, originState),
					traversalOrigin,
					ORE_LIMITS
			);
			if (plan.positions().isEmpty()) {
				return;
			}

			RecursionCooldown nextCooldown = cooldown;
			if (plan.recursionError()) {
				nextCooldown = cooldown.admit(gameTime, RECURSION_COOLDOWN_TICKS).cooldown();
				PythonToolsState next = before.withRecursionCooldown(nextCooldown.untilTick());
				if (!data.commitIfCurrent(serverPlayer.getUUID(), before, next)) {
					return;
				}
				serverPlayer.getCooldowns().addCooldown(
						serverPlayer.getMainHandItem(), Math.toIntExact(RECURSION_COOLDOWN_TICKS));
			}

			for (BoundedOreTraversal.Position target : plan.positions()) {
				BlockPos targetPos = blockPos(target);
				if (targetPos.equals(origin)) {
					continue;
				}
				if (serverPlayer.level() != serverLevel
						|| serverPlayer.getMainHandItem().getItem() != ModItems.PYTHON_PICKAXE
						|| !serverLevel.isLoaded(targetPos)
						|| !serverLevel.isInWorldBounds(targetPos)
						|| !serverLevel.getWorldBorder().isWithinBounds(targetPos)) {
					break;
				}
				BlockState current = serverLevel.getBlockState(targetPos);
				if (current.getBlock() != originState.getBlock()
						|| current.getDestroySpeed(serverLevel, targetPos) < 0.0F
						|| !serverPlayer.hasCorrectToolForDrops(current)) {
					continue;
				}
				serverPlayer.gameMode.destroyBlock(targetPos);
			}

			if (plan.recursionError()) {
				serverPlayer.sendOverlayMessage(Component.translatable(
						"message.developers_hell.python.pickaxe.recursion",
						plan.stopReason().name().toLowerCase(Locale.ROOT),
						remainingSeconds(nextCooldown.remaining(gameTime))));
			}
		}
		finally {
			breakReentry.remove(serverPlayer.getUUID());
		}
	}

	private boolean enabled(ServerPlayer player) {
		if (moduleGate.isEnabled(ModuleId.PYTHON_TOOLS)) {
			return true;
		}
		player.sendOverlayMessage(Component.translatable("message.developers_hell.python.disabled"));
		return false;
	}

	private static void applyPipOutcome(ServerPlayer player, PipOutcome outcome) {
		FakePackage fakePackage = outcome.fakePackage();
		switch (outcome.kind()) {
			case INSTALLED -> {
				chargeXp(player, outcome.xpCharged());
				applyPackageEffect(player, fakePackage);
				player.sendOverlayMessage(Component.translatable(
						"message.developers_hell.python.installed",
						fakePackage.id(), fakePackage.effect().name().toLowerCase(Locale.ROOT)));
			}
			case DEPENDENCY_CONFLICT -> {
				chargeXp(player, outcome.xpCharged());
				giveOrDrop(player, new ItemStack(ModItems.DEPENDENCY_CONFLICT));
				player.sendOverlayMessage(Component.translatable(
						"message.developers_hell.python.conflict", fakePackage.id()));
			}
			case INSUFFICIENT_XP -> player.sendOverlayMessage(Component.translatable(
					"message.developers_hell.python.insufficient_xp",
					fakePackage.id(), fakePackage.xpCost()));
			case ALREADY_INSTALLED -> player.sendOverlayMessage(Component.translatable(
					"message.developers_hell.python.already_installed", fakePackage.id()));
			case CONFLICT_ACTIVE -> player.sendOverlayMessage(Component.translatable(
					"message.developers_hell.python.conflict", fakePackage.id()));
			default -> throw new IllegalStateException("Unexpected pip outcome: " + outcome.kind());
		}
	}

	private static void applyVenvOutcome(
			ServerPlayer player,
			PythonToolsState before,
			PipOutcome outcome
	) {
		switch (outcome.kind()) {
			case VENV_CLEARED -> {
				if (before.dependencyConflict()) {
					removeOneConflictToken(player);
				}
				player.getCooldowns().addCooldown(
						player.getMainHandItem(), Math.toIntExact(PipEnvironment.VENV_COOLDOWN_TICKS));
				if (before.dependencyConflict()) {
					player.sendOverlayMessage(Component.translatable(
							"message.developers_hell.python.venv.cleared"));
				}
				else {
					player.sendOverlayMessage(Component.translatable(
							"message.developers_hell.python.venv.isolated",
							outcome.fakePackage().id()));
				}
			}
			case VENV_COOLDOWN -> player.sendOverlayMessage(Component.translatable(
					"message.developers_hell.python.venv.cooldown",
					remainingSeconds(before.flaskCooldownUntilTick() - player.level().getGameTime())));
			case VENV_CLEAN -> player.sendOverlayMessage(Component.translatable(
					"message.developers_hell.python.venv.noop"));
			default -> throw new IllegalStateException("Unexpected venv outcome: " + outcome.kind());
		}
	}

	private static boolean validHeldItem(Player player, InteractionHand hand, Item expected) {
		return player.getItemInHand(hand).getItem() == expected;
	}

	private static void chargeXp(ServerPlayer player, int levels) {
		if (!player.isCreative() && levels > 0) {
			player.giveExperienceLevels(-levels);
		}
	}

	private static void applyPackageEffect(ServerPlayer player, FakePackage fakePackage) {
		var effect = switch (fakePackage.effect()) {
			case HASTE -> MobEffects.HASTE;
			case SPEED -> MobEffects.SPEED;
			case RESISTANCE -> MobEffects.RESISTANCE;
			case JUMP_BOOST -> MobEffects.JUMP_BOOST;
		};
		player.addEffect(new MobEffectInstance(effect, fakePackage.durationTicks(), 0));
	}

	private static void removeOneConflictToken(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.getItem() == ModItems.DEPENDENCY_CONFLICT) {
				stack.shrink(1);
				player.getInventory().setChanged();
				return;
			}
		}
	}

	private static int remainingSeconds(long ticks) {
		long positive = Math.max(0L, ticks);
		return Math.toIntExact(Math.min(
				Integer.MAX_VALUE, positive / 20L + (positive % 20L == 0L ? 0L : 1L)));
	}

	private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		if (!player.addItem(stack)) {
			player.drop(stack, false);
		}
	}

	private static BoundedOreTraversal.Position position(BlockPos position) {
		return new BoundedOreTraversal.Position(position.getX(), position.getY(), position.getZ());
	}

	private static BlockPos blockPos(BoundedOreTraversal.Position position) {
		return new BlockPos(position.x(), position.y(), position.z());
	}

	private static final class MinecraftOreView implements BoundedOreTraversal.BlockView {
		private final ServerLevel level;
		private final ServerPlayer player;
		private final BlockPos origin;
		private final BlockState originState;
		private final String dimension;

		private MinecraftOreView(
				ServerLevel level,
				ServerPlayer player,
				BlockPos origin,
				BlockState originState
		) {
			this.level = level;
			this.player = player;
			this.origin = origin.immutable();
			this.originState = originState;
			this.dimension = level.dimension().toString();
		}

		@Override
		public boolean loaded(BoundedOreTraversal.Position position) {
			return level.isLoaded(blockPos(position));
		}

		@Override
		public boolean withinBuildHeight(BoundedOreTraversal.Position position) {
			BlockPos pos = blockPos(position);
			return level.isInWorldBounds(pos)
					&& level.isInsideBuildHeight(pos)
					&& level.getWorldBorder().isWithinBounds(pos);
		}

		@Override
		public String dimension(BoundedOreTraversal.Position position) {
			return dimension;
		}

		@Override
		public String oreKey(BoundedOreTraversal.Position position) {
			BlockState state = stateAt(position);
			String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
			return ORE_BLOCKS.contains(path) ? path : "";
		}

		@Override
		public boolean breakable(BoundedOreTraversal.Position position) {
			BlockPos pos = blockPos(position);
			BlockState state = stateAt(position);
			return state.getDestroySpeed(level, pos) >= 0.0F && player.hasCorrectToolForDrops(state);
		}

		private BlockState stateAt(BoundedOreTraversal.Position position) {
			BlockPos pos = blockPos(position);
			return pos.equals(origin) ? originState : level.getBlockState(pos);
		}
	}
}
