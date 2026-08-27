package dev.developershell.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.developershell.bossrush.BossRushManager;
import dev.developershell.bossrush.BossRushProgress;
import dev.developershell.config.DevHellConfig;
import dev.developershell.lecture.RetakeService;
import dev.developershell.module.ModuleId;
import dev.developershell.server.CampaignLifecycle;
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
	private static final String RETAKE_RECOVERED_KEY = "message.developers_hell.retake.recovered";
	private static final String RETAKE_FALLBACK_KEY = "message.developers_hell.retake.fallback";
	private static final String RETAKE_ALREADY_KEY = "message.developers_hell.retake.already";
	private static final String RETAKE_NOTHING_KEY = "message.developers_hell.retake.nothing";
	private static final String BOSS_RUSH_STATUS_KEY = "command.developers_hell.bossrush.status";

	public static void register(DevelopersHellRuntime runtime) {
		Objects.requireNonNull(runtime, "runtime");
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> dispatcher.register(
				Commands.literal("devhell")
						.then(Commands.literal("status").executes(context -> showStatus(
								context.getSource().getPlayerOrException(),
								runtime
						)))
						.then(Commands.literal("abort")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.executes(context -> abort(
										context.getSource().getPlayerOrException()
								)))
						.then(Commands.literal("recover")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.then(Commands.literal("retake").executes(context -> recoverRetake(
										context.getSource().getPlayerOrException()
								))))
						.then(Commands.literal("bossrush")
								.then(Commands.literal("start").executes(context -> bossRushStart(
										context.getSource().getPlayerOrException(), runtime
								)))
								.then(Commands.literal("status").executes(context -> bossRushStatus(
										context.getSource().getPlayerOrException(), runtime
								)))
								.then(Commands.literal("abort").executes(context -> bossRushAbort(
										context.getSource().getPlayerOrException(), runtime
								)))
								.then(Commands.literal("replay")
										.then(Commands.argument("boss", StringArgumentType.word())
												.executes(context -> bossRushReplay(
														context.getSource().getPlayerOrException(),
														runtime,
														StringArgumentType.getString(context, "boss")
												))))
						)
		));
	}

	private static int bossRushStart(ServerPlayer player, DevelopersHellRuntime runtime) {
		BossRushManager.StartResult result = runtime.bossRushManager().start(player);
		return result == BossRushManager.StartResult.STARTED
				|| result == BossRushManager.StartResult.SPONSOR_SKIPPED ? 1 : 0;
	}

	private static int bossRushStatus(ServerPlayer player, DevelopersHellRuntime runtime) {
		BossRushProgress progress = runtime.bossRushManager().status(player);
		player.sendSystemMessage(Component.translatable(
				BOSS_RUSH_STATUS_KEY,
				Component.translatable("command.developers_hell.bossrush.stage."
						+ progress.stage().serializedName()),
				booleanValue(progress.juryCleared()),
				booleanValue(progress.chairmanCleared()),
				booleanValue(progress.diplomaGranted())
		));
		return 1;
	}

	private static int bossRushAbort(ServerPlayer player, DevelopersHellRuntime runtime) {
		return runtime.bossRushManager().abort(player, "command") ? 1 : 0;
	}

	private static int bossRushReplay(
			ServerPlayer player,
			DevelopersHellRuntime runtime,
			String bossName
	) {
		try {
			return runtime.bossRushManager().replay(
					player,
					BossRushManager.ReplayBoss.fromCommand(bossName)
			) == BossRushManager.StartResult.STARTED ? 1 : 0;
		}
		catch (IllegalArgumentException exception) {
			player.sendSystemMessage(Component.translatable(
					"command.developers_hell.bossrush.replay.invalid", bossName));
			return 0;
		}
	}

	private static int abort(ServerPlayer player) {
		return CampaignLifecycle.onAbort(player) ? 1 : 0;
	}

	private static int recoverRetake(ServerPlayer player) {
		RetakeService.Outcome outcome = RetakeService.forLevel(player.level()).recover(player.getUUID());
		String messageKey = switch (outcome) {
			case INVENTORY_ISSUED -> RETAKE_RECOVERED_KEY;
			case FALLBACK_ISSUED -> RETAKE_FALLBACK_KEY;
			case ALREADY_PRESENT -> RETAKE_ALREADY_KEY;
			default -> RETAKE_NOTHING_KEY;
		};
		player.sendSystemMessage(Component.translatable(messageKey));
		return switch (outcome) {
			case INVENTORY_ISSUED, FALLBACK_ISSUED, ALREADY_PRESENT -> 1;
			default -> 0;
		};
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
