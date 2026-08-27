package dev.developershell;

import dev.developershell.command.DevHellCommands;
import dev.developershell.config.ConfigIssue;
import dev.developershell.config.DevHellConfigLoader;
import dev.developershell.lecture.RetakeService;
import dev.developershell.python.PythonToolsRuntime;
import dev.developershell.registry.ModEntities;
import dev.developershell.registry.ModItems;
import dev.developershell.server.CampaignLifecycle;
import dev.developershell.server.DeskInteraction;
import dev.developershell.server.DevelopersHellRuntime;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DevelopersHell implements ModInitializer {
	public static final String MOD_ID = "developers_hell";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModItems.initialize();
		ModEntities.initialize();

		DevHellConfigLoader.LoadResult loadResult = DevHellConfigLoader.loadFromConfigDirectory();
		DevelopersHellRuntime runtime = DevelopersHellRuntime.create(loadResult);
		PythonToolsRuntime pythonTools = new PythonToolsRuntime(runtime.moduleGate());
		LOGGER.info(
				"Developer's Hell config source={}, campaignEnabled={}, difficulty={}, enabledModules={}/{}",
				loadResult.sourceStatus().serializedName(),
				runtime.config().campaignEnabled(),
				runtime.config().difficulty().serializedName(),
				runtime.moduleGate().enabledModules().size(),
				dev.developershell.module.ModuleId.values().length
		);
		for (ConfigIssue issue : loadResult.issues()) {
			LOGGER.warn(
					"Developer's Hell config issue path={} expected={}",
					issue.path(),
					issue.expected()
			);
		}

		RetakeService.registerFallbackLifecycle();
		runtime.lifecycle().bindRetakeReconciler(RetakeService::reconcile);
		CampaignLifecycle.register(runtime);
		runtime.lectureManager().initialize();
		runtime.bossRushManager().registerLifecycle();
		ModItems.CURSED_UNPAID_INTERNSHIP_CONTRACT.registerInteraction(runtime.campaignService());
		ModItems.RETAKE_FORM.bindArenaSearchRadius(runtime.campaignService().arenaSearchRadius());
		DeskInteraction.register();
		pythonTools.register();
		DevHellCommands.register(runtime, pythonTools);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			runtime.lectureManager().tick(server);
			runtime.bossRushManager().tick(server);
			if (server.getTickCount() == 1) {
				LOGGER.info("DEVELOPERS_HELL_SERVER_FIRST_TICK_READY");
			}
		});
		LOGGER.info("Developer's Hell foundation initialized");
	}
}
