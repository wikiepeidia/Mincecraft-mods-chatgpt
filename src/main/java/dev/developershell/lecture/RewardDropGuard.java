package dev.developershell.lecture;

import dev.developershell.campaign.CampaignEvent;
import dev.developershell.campaign.CampaignService;
import dev.developershell.campaign.CampaignTransition;
import dev.developershell.campaign.PlayerCampaignState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Authenticates loose reward entities from exact, synchronous vanilla source transactions.
 * Persistent binding data validates the candidate, but never establishes its source authority.
 */
final class RewardDropGuard {
	private static final ThreadLocal<ArrayDeque<DropContext>> ACTIVE =
			ThreadLocal.withInitial(ArrayDeque::new);
	private static final Map<ItemEntity, LiveTransfer> LIVE_TRANSFERS = new IdentityHashMap<>();

	static synchronized void beginQDrop(ServerPlayer player) {
		Objects.requireNonNull(player, "player");
		Inventory inventory = player.getInventory();
		int slot = inventory.getSelectedSlot();
		DropContext context = new DropContext(DropKind.Q, player, player.level());
		addUniquePlayerInventorySource(
				context, player, inventory.getItem(slot), new InventoryRecovery(inventory, slot));
		ACTIVE.get().push(context);
	}

	static synchronized void endQDrop(ServerPlayer player) {
		finish(DropKind.Q, player);
	}

	static synchronized void beginDeathDrop(Inventory inventory) {
		Objects.requireNonNull(inventory, "inventory");
		DropContext context = new DropContext(
				DropKind.DEATH, inventory, inventory.player.level() instanceof ServerLevel level ? level : null);
		if (inventory.player instanceof ServerPlayer player && context.level != null) {
			List<DropSource> candidates = new ArrayList<>();
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				CampaignEvent.RewardProjectionKey key =
						RewardService.authoritativeProjectionKey(context.level, stack).orElse(null);
				if (key != null && key.ownerUuid().equals(player.getUUID())) {
					candidates.add(new DropSource(
							stack, key, new InventoryRecovery(inventory, slot)));
				}
			}
			context.sources.addAll(uniqueByProjection(candidates));
		}
		ACTIVE.get().push(context);
	}

	static synchronized void endDeathDrop(Inventory inventory) {
		finish(DropKind.DEATH, inventory);
	}

	static synchronized void beginMenuClick(
			AbstractContainerMenu menu,
			int slotIndex,
			ContainerInput input,
			Player player
	) {
		Objects.requireNonNull(menu, "menu");
		DropContext context = new DropContext(
				DropKind.MENU_CLICK,
				menu,
				player instanceof ServerPlayer serverPlayer ? serverPlayer.level() : null
		);
		if (player instanceof ServerPlayer serverPlayer && context.level != null) {
			if (input == ContainerInput.THROW && menu.isValidSlotIndex(slotIndex)) {
				Slot slot = menu.getSlot(slotIndex);
				if (slot.container == serverPlayer.getInventory()) {
					addUniquePlayerInventorySource(
							context,
							serverPlayer,
							slot.getItem(),
							new InventoryRecovery(serverPlayer.getInventory(), slot.getContainerSlot())
					);
				}
			}
			else if (input == ContainerInput.PICKUP
					&& slotIndex == AbstractContainerMenu.SLOT_CLICKED_OUTSIDE) {
				ItemStack carried = menu.getCarried();
				CampaignEvent.RewardProjectionKey key =
						RewardService.authoritativeProjectionKey(context.level, carried).orElse(null);
				if (key != null
						&& key.ownerUuid().equals(serverPlayer.getUUID())
						&& countInventoryMatches(serverPlayer.getInventory(), key) == 0
						&& countMenuMatches(menu, key) == 1) {
					context.sources.add(new DropSource(carried, key, new CursorRecovery(menu)));
				}
			}
		}
		ACTIVE.get().push(context);
	}

	static synchronized void endMenuClick(AbstractContainerMenu menu) {
		finish(DropKind.MENU_CLICK, menu);
	}

	static synchronized void beginMenuClose(ServerPlayer player) {
		Objects.requireNonNull(player, "player");
		AbstractContainerMenu menu = player.containerMenu;
		DropContext context = new DropContext(DropKind.MENU_CLOSE, player, player.level());
		ItemStack carried = menu.getCarried();
		CampaignEvent.RewardProjectionKey key =
				RewardService.authoritativeProjectionKey(context.level, carried).orElse(null);
		if (key != null
				&& key.ownerUuid().equals(player.getUUID())
				&& countInventoryMatches(player.getInventory(), key) == 0
				&& countMenuMatches(menu, key) == 1) {
			context.sources.add(new DropSource(carried, key, DurableRecovery.INSTANCE));
		}
		ACTIVE.get().push(context);
	}

	static synchronized void endMenuClose(ServerPlayer player) {
		finish(DropKind.MENU_CLOSE, player);
	}

	static synchronized void beginContainerDrop(Level level, Container container) {
		Objects.requireNonNull(container, "container");
		ServerLevel serverLevel = level instanceof ServerLevel candidate ? candidate : null;
		DropContext context = new DropContext(DropKind.CONTAINER, container, serverLevel);
		if (serverLevel != null) {
			List<DropSource> candidates = new ArrayList<>();
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				ItemStack stack = container.getItem(slot);
				CampaignEvent.RewardProjectionKey key =
						RewardService.authoritativeProjectionKey(serverLevel, stack).orElse(null);
				if (key == null) {
					continue;
				}
				ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(key.ownerUuid());
				if (owner == null || countInventoryMatches(owner.getInventory(), key) == 0) {
					candidates.add(new DropSource(stack, key, DurableRecovery.INSTANCE));
				}
			}
			context.sources.addAll(uniqueByProjection(candidates));
		}
		ACTIVE.get().push(context);
	}

	static synchronized void endContainerDrop(Container container) {
		finish(DropKind.CONTAINER, container);
	}

	static synchronized void onStackSplit(ItemStack source, ItemStack result) {
		if (source == null || result == null || result.isEmpty()) {
			return;
		}
		for (DropContext context : ACTIVE.get()) {
			for (DropSource candidate : context.sources) {
				if (candidate.source == source) {
					candidate.derived.put(result, Boolean.TRUE);
					return;
				}
			}
		}
	}

	static synchronized void onEntityAddStart(Entity entity) {
		if (!(entity instanceof ItemEntity item) || !(item.level() instanceof ServerLevel level)) {
			return;
		}
		Candidate candidate = findCandidate(level, item.getItem());
		if (candidate == null || candidate.source.claimed.put(item.getItem(), Boolean.TRUE) != null) {
			return;
		}
		if (RewardService.authoritativeProjectionKey(level, item.getItem())
				.filter(candidate.source.key::equals).isEmpty()) {
			return;
		}

		PlayerCampaignState.RewardFallbackRef transferred = new PlayerCampaignState.RewardFallbackRef(
				item.getUUID(), RewardService.dimensionId(level), item.blockPosition(), true);
		CampaignTransition transition = CampaignService.apply(
				level,
				new CampaignEvent.RewardFallback(
						candidate.source.key,
						transferred.entityUuid(),
						transferred.dimension(),
						transferred.position(),
						CampaignEvent.RewardFallbackOperation.TRANSFERRED
				),
				ignored -> {
				}
		);
		if (!transition.accepted()
				|| RewardService.currentFallback(level, candidate.source.key)
						.filter(transferred::equals).isEmpty()) {
			candidate.source.rejected.add(item.getItem());
			return;
		}
		item.setTarget(candidate.source.key.ownerUuid());
		LIVE_TRANSFERS.put(item, new LiveTransfer(candidate.context, candidate.source, transferred));
	}

	static synchronized void onEntityAddResult(ItemEntity item, boolean added) {
		LiveTransfer transfer = LIVE_TRANSFERS.remove(item);
		if (added || transfer == null || !(item.level() instanceof ServerLevel level)) {
			return;
		}
		CampaignTransition requeued = CampaignService.apply(
				level,
				new CampaignEvent.RewardFallback(
						transfer.source.key,
						transfer.ref.entityUuid(),
						transfer.ref.dimension(),
						transfer.ref.position(),
						CampaignEvent.RewardFallbackOperation.REQUEUED,
						transfer.ref
				),
				ignored -> {
				}
		);
		if (requeued.accepted()
				&& RewardService.currentFallback(level, transfer.source.key).isEmpty()) {
			transfer.source.rejected.add(item.getItem());
		}
	}

	static synchronized void onEntityLoaded(ItemEntity item) {
		LIVE_TRANSFERS.remove(item);
	}

	static synchronized int pendingTransferCount() {
		return LIVE_TRANSFERS.size() + ACTIVE.get().size();
	}

	private static Candidate findCandidate(ServerLevel level, ItemStack stack) {
		Candidate found = null;
		for (DropContext context : ACTIVE.get()) {
			if (context.level != level) {
				continue;
			}
			for (DropSource source : context.sources) {
				if ((source.source == stack || source.derived.containsKey(stack))
						&& RewardService.matchesProjection(stack, source.key)) {
					if (found != null) {
						return null;
					}
					found = new Candidate(context, source);
				}
			}
		}
		return found;
	}

	private static void addUniquePlayerInventorySource(
			DropContext context,
			ServerPlayer player,
			ItemStack stack,
			Recovery recovery
	) {
		if (context.level == null) {
			return;
		}
		CampaignEvent.RewardProjectionKey key =
				RewardService.authoritativeProjectionKey(context.level, stack).orElse(null);
		if (key != null
				&& key.ownerUuid().equals(player.getUUID())
				&& countInventoryMatches(player.getInventory(), key) == 1) {
			context.sources.add(new DropSource(stack, key, recovery));
		}
	}

	private static List<DropSource> uniqueByProjection(List<DropSource> candidates) {
		Map<CampaignEvent.RewardProjectionKey, Integer> counts = new HashMap<>();
		for (DropSource source : candidates) {
			counts.merge(source.key, 1, Integer::sum);
		}
		return candidates.stream().filter(source -> counts.get(source.key) == 1).toList();
	}

	private static int countInventoryMatches(
			Inventory inventory,
			CampaignEvent.RewardProjectionKey key
	) {
		int count = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (RewardService.matchesProjection(inventory.getItem(slot), key)) {
				count++;
			}
		}
		return count;
	}

	private static int countMenuMatches(
			AbstractContainerMenu menu,
			CampaignEvent.RewardProjectionKey key
	) {
		int count = RewardService.matchesProjection(menu.getCarried(), key) ? 1 : 0;
		for (Slot slot : menu.slots) {
			if (RewardService.matchesProjection(slot.getItem(), key)) {
				count++;
			}
		}
		return count;
	}

	private static void finish(DropKind kind, Object boundary) {
		ArrayDeque<DropContext> stack = ACTIVE.get();
		if (stack.isEmpty()) {
			return;
		}
		DropContext context = stack.pop();
		if (context.kind != kind || context.boundary != boundary) {
			stack.clear();
		}
		else {
			context.finish();
		}
		if (stack.isEmpty()) {
			ACTIVE.remove();
		}
	}

	private enum DropKind {
		Q,
		DEATH,
		MENU_CLICK,
		MENU_CLOSE,
		CONTAINER
	}

	private interface Recovery {
		boolean restore(ItemStack stack);

		boolean durable();
	}

	private record InventoryRecovery(Inventory inventory, int slot) implements Recovery {
		@Override
		public boolean restore(ItemStack stack) {
			if (stack.isEmpty() || slot < 0 || slot >= inventory.getContainerSize()
					|| !inventory.getItem(slot).isEmpty()) {
				return false;
			}
			inventory.setItem(slot, stack);
			return inventory.getItem(slot) == stack;
		}

		@Override
		public boolean durable() {
			return false;
		}
	}

	private record CursorRecovery(AbstractContainerMenu menu) implements Recovery {
		@Override
		public boolean restore(ItemStack stack) {
			ItemStack current = menu.getCarried();
			if (current.isEmpty()) {
				menu.setCarried(stack);
				return menu.getCarried() == stack;
			}
			if (ItemStack.isSameItemSameComponents(current, stack)
					&& current.getCount() + stack.getCount() <= current.getMaxStackSize()) {
				current.grow(stack.getCount());
				return true;
			}
			return false;
		}

		@Override
		public boolean durable() {
			return false;
		}
	}

	private enum DurableRecovery implements Recovery {
		INSTANCE;

		@Override
		public boolean restore(ItemStack stack) {
			return false;
		}

		@Override
		public boolean durable() {
			return true;
		}
	}

	private static final class DropContext {
		private final DropKind kind;
		private final Object boundary;
		private final ServerLevel level;
		private final List<DropSource> sources = new ArrayList<>();

		private DropContext(DropKind kind, Object boundary, ServerLevel level) {
			this.kind = Objects.requireNonNull(kind, "kind");
			this.boundary = Objects.requireNonNull(boundary, "boundary");
			this.level = level;
		}

		private void finish() {
			if (level == null) {
				return;
			}
			Set<UUID> reconcileOwners = new HashSet<>();
			for (DropSource source : sources) {
				for (ItemStack rejected : source.rejected) {
					boolean restored = !source.recovery.durable() && source.recovery.restore(rejected);
					if (restored && RewardService.ensureProjectionConfirmed(level, source.key)) {
						continue;
					}
					reconcileOwners.add(source.key.ownerUuid());
				}
			}
			for (UUID ownerUuid : reconcileOwners) {
				ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
				if (owner != null) {
					RewardService.reconcilePending(owner);
				}
			}
		}
	}

	private static final class DropSource {
		private final ItemStack source;
		private final CampaignEvent.RewardProjectionKey key;
		private final Recovery recovery;
		private final Map<ItemStack, Boolean> derived = new IdentityHashMap<>();
		private final Map<ItemStack, Boolean> claimed = new IdentityHashMap<>();
		private final List<ItemStack> rejected = new ArrayList<>();

		private DropSource(
				ItemStack source,
				CampaignEvent.RewardProjectionKey key,
				Recovery recovery
		) {
			this.source = Objects.requireNonNull(source, "source");
			this.key = Objects.requireNonNull(key, "key");
			this.recovery = Objects.requireNonNull(recovery, "recovery");
		}
	}

	private record Candidate(DropContext context, DropSource source) {
	}

	private record LiveTransfer(
			DropContext context,
			DropSource source,
			PlayerCampaignState.RewardFallbackRef ref
	) {
	}

	private RewardDropGuard() {
	}
}
