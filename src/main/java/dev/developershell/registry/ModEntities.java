package dev.developershell.registry;

import dev.developershell.DevelopersHell;
import dev.developershell.campaign.CampaignService;
import java.util.UUID;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;

public final class ModEntities {
	public static final ResourceKey<EntityType<?>> PROFESSOR_KEY = ResourceKey.create(
			Registries.ENTITY_TYPE,
			DevelopersHell.id("professor_infinite_slides")
	);

	public static final EntityType<ProfessorEntity> PROFESSOR = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			PROFESSOR_KEY,
			EntityType.Builder.of(ProfessorEntity::new, MobCategory.MONSTER)
					.sized(0.6F, 1.95F)
					.clientTrackingRange(10)
					.noLootTable()
					.build(PROFESSOR_KEY)
	);

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(PROFESSOR, Vindicator.createAttributes());
	}

	public static final class ProfessorEntity extends Vindicator {
		private UUID ownerUuid;
		private UUID encounterUuid;
		private boolean vulnerabilityOpen;
		private boolean victoryCommitted;

		public ProfessorEntity(EntityType<? extends Vindicator> type, Level level) {
			super(type, level);
		}

		public void bind(UUID ownerUuid, UUID encounterUuid) {
			this.ownerUuid = ownerUuid;
			this.encounterUuid = encounterUuid;
			setNoAi(true);
		}

		public UUID ownerUuid() {
			return ownerUuid;
		}

		public UUID encounterUuid() {
			return encounterUuid;
		}

		public void setVulnerabilityOpen(boolean vulnerabilityOpen) {
			this.vulnerabilityOpen = vulnerabilityOpen;
		}

		@Override
		public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
			Entity attacker = source.getEntity();
			if (!vulnerabilityOpen
					|| ownerUuid == null
					|| encounterUuid == null
					|| attacker == null
					|| !ownerUuid.equals(attacker.getUUID())) {
				return false;
			}
			return super.hurtServer(level, source, amount);
		}

		@Override
		public void die(DamageSource source) {
			if (!(level() instanceof ServerLevel serverLevel) || ownerUuid == null || encounterUuid == null) {
				return;
			}
			if (!victoryCommitted) {
				victoryCommitted = CampaignService.victory(serverLevel, ownerUuid, encounterUuid);
			}
			if (!victoryCommitted) {
				setHealth(Math.max(1.0F, getHealth()));
				return;
			}
			super.die(source);
			discard();
		}

		@Override
		protected void readAdditionalSaveData(ValueInput input) {
			super.readAdditionalSaveData(input);
			ownerUuid = input.read("developers_hell_owner", UUIDUtil.CODEC).orElse(null);
			encounterUuid = input.read("developers_hell_encounter", UUIDUtil.CODEC).orElse(null);
		}

		@Override
		protected void addAdditionalSaveData(ValueOutput output) {
			super.addAdditionalSaveData(output);
			if (ownerUuid != null) {
				output.store("developers_hell_owner", UUIDUtil.CODEC, ownerUuid);
			}
			if (encounterUuid != null) {
				output.store("developers_hell_encounter", UUIDUtil.CODEC, encounterUuid);
			}
		}
	}

	private ModEntities() {
	}
}
