package dev.developershell.server;

import dev.developershell.campaign.CampaignService;
import dev.developershell.config.DevHellConfig;
import dev.developershell.config.DevHellConfigLoader;
import dev.developershell.lecture.LectureEncounterManager;
import dev.developershell.lecture.LectureRules;
import dev.developershell.module.ModuleGate;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Immutable session composition; adapters never retain live world or player objects. */
public final class DevelopersHellRuntime {
	private static final String CAMPAIGN_DISABLED_KEY = "message.developers_hell.campaign.disabled";
	private final DevHellConfigLoader.LoadResult loadResult;
	private final ModuleGate moduleGate;
	private final LectureRules lectureRules;
	private final CampaignServiceAdapter campaignService;
	private final LectureManagerAdapter lectureManager;

	private DevelopersHellRuntime(
			DevHellConfigLoader.LoadResult loadResult,
			ModuleGate moduleGate,
			LectureRules lectureRules,
			CampaignServiceAdapter campaignService,
			LectureManagerAdapter lectureManager
	) {
		this.loadResult = Objects.requireNonNull(loadResult, "loadResult");
		this.moduleGate = Objects.requireNonNull(moduleGate, "moduleGate");
		this.lectureRules = Objects.requireNonNull(lectureRules, "lectureRules");
		this.campaignService = Objects.requireNonNull(campaignService, "campaignService");
		this.lectureManager = Objects.requireNonNull(lectureManager, "lectureManager");
	}

	public static DevelopersHellRuntime create(DevHellConfigLoader.LoadResult loadResult) {
		Objects.requireNonNull(loadResult, "loadResult");
		DevHellConfig config = loadResult.config();
		DevHellConfig.LectureTuning tuning = config.lecture();
		LectureRules rules = new LectureRules(
				tuning.slideDeckTelegraphTicks(),
				tuning.vulnerabilityTicks(),
				tuning.actionBarUpdateTicks(),
				tuning.particleRefreshTicks(),
				tuning.particlesPerRefresh(),
				tuning.maxParticleBurstsPerEncounter(),
				tuning.maxTransitionSoundsPerEncounter()
		);
		return new DevelopersHellRuntime(
				loadResult,
				config.moduleGate(),
				rules,
				new CampaignServiceAdapter(config.campaignEnabled()),
				new LectureManagerAdapter(rules)
		);
	}

	public DevHellConfigLoader.LoadResult loadResult() {
		return loadResult;
	}

	public DevHellConfig config() {
		return loadResult.config();
	}

	public ModuleGate moduleGate() {
		return moduleGate;
	}

	public LectureRules lectureRules() {
		return lectureRules;
	}

	public CampaignServiceAdapter campaignService() {
		return campaignService;
	}

	public LectureManagerAdapter lectureManager() {
		return lectureManager;
	}

	/** One-shot behavior adapter over the retained state-before-effects campaign service. */
	public static final class CampaignServiceAdapter {
		private final boolean campaignEnabled;

		private CampaignServiceAdapter(boolean campaignEnabled) {
			this.campaignEnabled = campaignEnabled;
		}

		public boolean campaignEnabled() {
			return campaignEnabled;
		}

		public boolean start(
				ServerPlayer player,
				BlockPos deskPos,
				Direction deskFacing,
				ItemStack contract
		) {
			if (!campaignEnabled) {
				player.sendSystemMessage(Component.translatable(CAMPAIGN_DISABLED_KEY));
				return false;
			}
			return CampaignService.start(player, deskPos, deskFacing, contract);
		}
	}

	/** One-shot rule binding and tick adapter over the retained encounter manager. */
	public static final class LectureManagerAdapter {
		private final LectureRules rules;
		private volatile boolean initialized;

		private LectureManagerAdapter(LectureRules rules) {
			this.rules = Objects.requireNonNull(rules, "rules");
		}

		public synchronized void initialize() {
			if (initialized) {
				throw new IllegalStateException("Lecture manager already initialized for this runtime");
			}
			LectureEncounterManager.initialize(rules);
			initialized = true;
		}

		public void tick(MinecraftServer server) {
			if (!initialized) {
				throw new IllegalStateException("Lecture manager must be initialized before ticking");
			}
			LectureEncounterManager.tick(Objects.requireNonNull(server, "server"));
		}

		public LectureRules rules() {
			return rules;
		}
	}
}
