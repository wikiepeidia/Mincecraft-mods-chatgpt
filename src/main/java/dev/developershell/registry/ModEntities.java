package dev.developershell.registry;

import dev.developershell.DevelopersHell;
import dev.developershell.entity.ProfessorInfiniteSlidesEntity;
import java.util.UUID;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.level.Level;

public final class ModEntities {
	public static final ResourceKey<EntityType<?>> PROFESSOR_KEY = ResourceKey.create(
			Registries.ENTITY_TYPE,
			DevelopersHell.id("professor_infinite_slides")
	);

	public static final EntityType<ProfessorInfiniteSlidesEntity> PROFESSOR = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			PROFESSOR_KEY,
			EntityType.Builder.of(ProfessorInfiniteSlidesEntity::new, MobCategory.MONSTER)
					.sized(0.6F, 1.95F)
					.clientTrackingRange(10)
					.noLootTable()
					.build(PROFESSOR_KEY)
	);

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(PROFESSOR, Vindicator.createAttributes());
	}

	/**
	 * Narrow Plan 01 compatibility surface for the retained encounter manager.
	 * The registered runtime implementation and all combat/persistence behavior live in the
	 * final top-level {@link ProfessorInfiniteSlidesEntity}.
	 */
	public abstract static class ProfessorEntity extends Vindicator {
		protected ProfessorEntity(EntityType<? extends Vindicator> type, Level level) {
			super(type, level);
		}

		public abstract void bind(UUID ownerUuid, UUID encounterUuid);

		public abstract UUID ownerUuid();

		public abstract UUID encounterUuid();

		public abstract void setVulnerabilityOpen(boolean vulnerabilityOpen);
	}

	private ModEntities() {
	}
}
