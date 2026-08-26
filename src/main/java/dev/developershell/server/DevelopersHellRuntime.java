package dev.developershell.server;

import dev.developershell.campaign.CampaignService;
import dev.developershell.campaign.CampaignEvent;
import dev.developershell.campaign.CampaignTransition;
import dev.developershell.config.DevHellConfig;
import dev.developershell.config.DevHellConfigLoader;
import dev.developershell.lecture.ArenaRejection;
import dev.developershell.lecture.ArenaValidationResult;
import dev.developershell.lecture.LectureEncounterManager;
import dev.developershell.lecture.LectureRules;
import dev.developershell.lecture.RetakeService;
import dev.developershell.module.ModuleGate;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Immutable session composition; adapters never retain live world or player objects. */
public final class DevelopersHellRuntime {
	private static final String CAMPAIGN_DISABLED_KEY = "message.developers_hell.campaign.disabled";
	private final DevHellConfigLoader.LoadResult loadResult;
	private final ModuleGate moduleGate;
	private final LectureRules lectureRules;
	private final CampaignServiceAdapter campaignService;
	private final LifecycleAdapter lifecycle;
	private final LectureManagerAdapter lectureManager;

	private DevelopersHellRuntime(
			DevHellConfigLoader.LoadResult loadResult,
			ModuleGate moduleGate,
			LectureRules lectureRules,
			CampaignServiceAdapter campaignService,
			LifecycleAdapter lifecycle,
			LectureManagerAdapter lectureManager
	) {
		this.loadResult = Objects.requireNonNull(loadResult, "loadResult");
		this.moduleGate = Objects.requireNonNull(moduleGate, "moduleGate");
		this.lectureRules = Objects.requireNonNull(lectureRules, "lectureRules");
		this.campaignService = Objects.requireNonNull(campaignService, "campaignService");
		this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
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
		CampaignServiceAdapter campaignService = new CampaignServiceAdapter(config.campaignEnabled());
		LifecycleAdapter lifecycle = new LifecycleAdapter();
		return new DevelopersHellRuntime(
				loadResult,
				config.moduleGate(),
				rules,
				campaignService,
				lifecycle,
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

	public LifecycleAdapter lifecycle() {
		return lifecycle;
	}

	public LectureManagerAdapter lectureManager() {
		return lectureManager;
	}

	/**
	 * Applies lifecycle events through the retained persist-before-effect campaign service.
	 * Physical Retake materialization remains owned by the later Retake service plan.
	 */
	public static final class LifecycleAdapter {
		private static final String RELOAD_KEY = "message.developers_hell.lecture.reload";
		private static final String RETAKE_KEY = "message.developers_hell.lecture.retake";
		private static final String RETAKE_ISSUED_KEY = "message.developers_hell.retake.issued";
		private static final String RETAKE_FALLBACK_KEY = "message.developers_hell.retake.fallback";
		private final Set<UUID> pendingReloadNotices = new LinkedHashSet<>();
		private RetakeReconciler retakeReconciler;

		/** Explicit one-time binding keeps lifecycle persistence separate from physical projections. */
		public synchronized void bindRetakeReconciler(RetakeReconciler reconciler) {
			Objects.requireNonNull(reconciler, "reconciler");
			if (retakeReconciler != null && retakeReconciler != reconciler) {
				throw new IllegalStateException("Retake reconciler already bound to another runtime adapter");
			}
			retakeReconciler = reconciler;
		}

		public boolean submit(ServerLevel level, CampaignEvent event, ServerPlayer feedbackPlayer) {
			Objects.requireNonNull(level, "level");
			Objects.requireNonNull(event, "event");
			ServerPlayer feedback = feedbackPlayer != null
					? feedbackPlayer
					: feedbackPlayer(level, event);
			boolean[] reconcileRetake = {false};
			CampaignTransition transition = CampaignService.apply(level, event, intent -> {
				if (intent instanceof CampaignTransition.EffectIntent.CleanupEncounter cleanup) {
					LectureEncounterManager.cleanup(cleanup.encounterUuid());
				}
				else if (intent instanceof CampaignTransition.EffectIntent.ReconcileRetake) {
					reconcileRetake[0] = true;
				}
			});
			if (!transition.accepted()) {
				return false;
			}
			RetakeService.Outcome retakeOutcome = null;
			if (reconcileRetake[0]) {
				RetakeReconciler reconciler = retakeReconciler;
				if (reconciler == null) {
					throw new IllegalStateException("Retake reconciler must be bound before lifecycle callbacks");
				}
				retakeOutcome = reconciler.reconcile(level, event.ownerUuid());
			}

			if (event instanceof CampaignEvent.NormalizeReload) {
				if (feedback == null) {
					pendingReloadNotices.add(event.ownerUuid());
				}
				else {
					sendReloadNotice(feedback);
				}
			}
			else if (event instanceof CampaignEvent.Terminal terminal && feedback != null) {
				feedback.sendSystemMessage(Component.translatable(terminalMessageKey(terminal.reason())));
				if (reconcileRetake[0]) {
					feedback.sendSystemMessage(Component.translatable(RETAKE_KEY));
				}
			}
			if (feedback != null) {
				sendMaterializationNotice(feedback, retakeOutcome);
			}
			return true;
		}

		public void deliverPendingReloadNotice(ServerPlayer player) {
			Objects.requireNonNull(player, "player");
			if (pendingReloadNotices.remove(player.getUUID())) {
				sendReloadNotice(player);
			}
		}

		public void cleanupStaleRuntime(LectureEncounterManager.RuntimeSnapshot runtime) {
			LectureEncounterManager.cleanupIfIdentityMatches(Objects.requireNonNull(runtime, "runtime"));
		}

		private static ServerPlayer feedbackPlayer(ServerLevel level, CampaignEvent event) {
			if (event instanceof CampaignEvent.Terminal terminal) {
				return LectureEncounterManager.participant(terminal.encounterUuid()).orElse(null);
			}
			return level.getServer().getPlayerList().getPlayer(event.ownerUuid());
		}

		private static String terminalMessageKey(CampaignEvent.TerminalReason reason) {
			return switch (reason) {
				case DEATH -> "message.developers_hell.lecture.failure.death";
				case ESCAPE -> "message.developers_hell.lecture.failure.escape";
				case TIMEOUT -> "message.developers_hell.lecture.failure.timeout";
				case DIMENSION_CHANGE -> "message.developers_hell.lecture.failure.dimension";
				case DISCONNECT -> "message.developers_hell.lecture.failure.disconnect";
				case ABORT -> "message.developers_hell.lecture.failure.abort";
				case SERVER_STOP -> "message.developers_hell.lecture.failure.server_stop";
				case ENTITY_UNLOAD -> "message.developers_hell.lecture.failure.unload";
			};
		}

		private static void sendReloadNotice(ServerPlayer player) {
			player.sendSystemMessage(Component.translatable(RELOAD_KEY));
			player.sendSystemMessage(Component.translatable(RETAKE_KEY));
		}

		private static void sendMaterializationNotice(
				ServerPlayer player,
				RetakeService.Outcome outcome
		) {
			if (outcome == RetakeService.Outcome.INVENTORY_ISSUED) {
				player.sendSystemMessage(Component.translatable(RETAKE_ISSUED_KEY));
			}
			else if (outcome == RetakeService.Outcome.FALLBACK_ISSUED) {
				player.sendSystemMessage(Component.translatable(RETAKE_FALLBACK_KEY));
			}
		}

		@FunctionalInterface
		public interface RetakeReconciler {
			RetakeService.Outcome reconcile(ServerLevel level, UUID ownerUuid);
		}
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

		public ArenaValidationResult start(
				ServerPlayer player,
				ArenaValidationResult.Accepted arena,
				ItemStack contract
		) {
			if (!campaignEnabled) {
				player.sendSystemMessage(Component.translatable(CAMPAIGN_DISABLED_KEY));
				return new ArenaValidationResult.Rejected(ArenaRejection.SPAWN_CAPACITY);
			}
			return CampaignService.start(player, arena, contract);
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
			LectureEncounterManager.initialize(rules, CampaignLifecycle::onRuntimeExit);
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
