package dev.developershell.command;

import dev.developershell.config.DevHellConfig;
import dev.developershell.module.ModuleId;
import dev.developershell.server.DevelopersHellRuntime;
import java.util.Objects;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Read-only player status surface; recovery mutations are added by their owning plan. */
public final class DevHellCommands {
	private static final String STATUS_HEADER_KEY = "command.developers_hell.status.header";
	private static final String STATUS_SOURCE_KEY = "command.developers_hell.status.source";
	private static final String STATUS_CAMPAIGN_KEY = "command.developers_hell.status.campaign";
	private static final String STATUS_DIFFICULTY_KEY = "command.developers_hell.status.difficulty";
	private static final String STATUS_BLOCK_DAMAGE_KEY = "command.developers_hell.status.boss_block_damage";
	private static final String STATUS_REDUCED_FLASHING_KEY = "command.developers_hell.status.reduced_flashing";
	private static final String STATUS_REDUCED_EFFECTS_KEY = "command.developers_hell.status.reduced_effects";
	private static final String STATUS_METADATA_SCHEDULE_KEY = "command.developers_hell.status.metadata_schedule";
	private static final String STATUS_DEADLINE_SCHEDULE_KEY = "command.developers_hell.status.deadline_schedule";
	private static final String STATUS_MODULE_KEY = "command.developers_hell.status.module";

	public static void register(DevelopersHellRuntime runtime) {
		Objects.requireNonNull(runtime, "runtime");
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> dispatcher.register(
				Commands.literal("devhell")
						.then(Commands.literal("status").executes(context -> showStatus(
								context.getSource().getPlayerOrException(),
								runtime
						)))
		));
	}

	private static int showStatus(ServerPlayer player, DevelopersHellRuntime runtime) {
		DevHellConfig config = runtime.config();
		player.sendSystemMessage(Component.translatable(STATUS_HEADER_KEY));
		player.sendSystemMessage(Component.translatable(
				STATUS_SOURCE_KEY,
				Component.translatable(sourceKey(runtime))
		));
		player.sendSystemMessage(Component.translatable(
				STATUS_CAMPAIGN_KEY,
				booleanValue(config.campaignEnabled())
		));
		player.sendSystemMessage(Component.translatable(
				STATUS_DIFFICULTY_KEY,
				Component.translatable(difficultyKey(config.difficulty()))
		));
		player.sendSystemMessage(Component.translatable(
				STATUS_BLOCK_DAMAGE_KEY,
				booleanValue(config.bossBlockDamage())
		));
		player.sendSystemMessage(Component.translatable(
				STATUS_REDUCED_FLASHING_KEY,
				booleanValue(config.reducedFlashing())
		));
		player.sendSystemMessage(Component.translatable(
				STATUS_REDUCED_EFFECTS_KEY,
				booleanValue(config.reducedEffects())
		));
		player.sendSystemMessage(Component.translatable(
				STATUS_METADATA_SCHEDULE_KEY,
				Component.translatable(scheduleKey(config.metadataRouletteSchedule()))
		));
		player.sendSystemMessage(Component.translatable(
				STATUS_DEADLINE_SCHEDULE_KEY,
				Component.translatable(scheduleKey(config.threeDayDeadlineSchedule()))
		));
		for (ModuleId module : ModuleId.values()) {
			player.sendSystemMessage(Component.translatable(
					STATUS_MODULE_KEY,
					Component.translatable(moduleKey(module)),
					booleanValue(runtime.moduleGate().isEnabled(module))
			));
		}
		return 1;
	}

	private static Component booleanValue(boolean enabled) {
		return Component.translatable(enabled
				? "command.developers_hell.status.value.enabled"
				: "command.developers_hell.status.value.disabled");
	}

	private static String sourceKey(DevelopersHellRuntime runtime) {
		return "command.developers_hell.status.source." + runtime.loadResult().sourceStatus().serializedName();
	}

	private static String difficultyKey(DevHellConfig.Difficulty difficulty) {
		return "command.developers_hell.status.difficulty." + difficulty.serializedName();
	}

	private static String scheduleKey(DevHellConfig.ScheduleMode schedule) {
		return "command.developers_hell.status.schedule." + schedule.serializedName();
	}

	private static String moduleKey(ModuleId module) {
		return "command.developers_hell.status.module." + module.serializedName();
	}

	private DevHellCommands() {
	}
}
