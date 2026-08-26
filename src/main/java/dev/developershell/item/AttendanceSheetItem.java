package dev.developershell.item;

import com.mojang.serialization.Codec;
import dev.developershell.registry.ModItems;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;

/** Durable-entitlement projection bound to one player and recovery generation. */
public final class AttendanceSheetItem extends Item {
	private static final String OWNER_TAG = "developers_hell_sheet_owner";
	private static final String RECOVERY_SEQUENCE_TAG = "developers_hell_sheet_recovery_sequence";
	private static final String TOOLTIP_KEY = "tooltip.developers_hell.attendance_sheet.proof";

	public AttendanceSheetItem(Properties properties) {
		super(properties);
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

	/** Creates the non-stackable physical proof for one persisted entitlement generation. */
	public static ItemStack bound(Binding binding) {
		Objects.requireNonNull(binding, "binding");
		ItemStack stack = new ItemStack(ModItems.ATTENDANCE_SHEET);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.store(OWNER_TAG, UUIDUtil.CODEC, binding.ownerUuid());
			tag.store(RECOVERY_SEQUENCE_TAG, Codec.LONG, binding.recoverySequence());
		});
		return stack;
	}

	/** Reads a complete binding; malformed or unbound stacks fail closed. */
	public static Optional<Binding> binding(ItemStack stack) {
		Objects.requireNonNull(stack, "stack");
		if (stack.isEmpty() || stack.getItem() != ModItems.ATTENDANCE_SHEET) {
			return Optional.empty();
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null || customData.isEmpty()) {
			return Optional.empty();
		}
		CompoundTag tag = customData.copyTag();
		Optional<UUID> ownerUuid = tag.read(OWNER_TAG, UUIDUtil.CODEC);
		Optional<Long> recoverySequence = tag.read(RECOVERY_SEQUENCE_TAG, Codec.LONG);
		if (ownerUuid.isEmpty() || recoverySequence.isEmpty() || recoverySequence.get() < 0L) {
			return Optional.empty();
		}
		return Optional.of(new Binding(ownerUuid.get(), recoverySequence.get()));
	}

	/** Exact identity of one current Sheet projection. */
	public record Binding(UUID ownerUuid, long recoverySequence) {
		public Binding {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			if (recoverySequence < 0L) {
				throw new IllegalArgumentException("recoverySequence must be non-negative");
			}
		}
	}
}
