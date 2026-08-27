package dev.developershell.entity;

import dev.developershell.campaign.CampaignService;
import dev.developershell.lecture.LectureEncounterManager;
import dev.developershell.lecture.LectureRules;
import dev.developershell.registry.ModEntities;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Stable Vindicator-derived combat identity for Professor Infinite Slides.
 *
 * <p>The entity carries only owner/encounter identity and a transient vulnerability window.
 * Durable progression and reward authority remain in {@link CampaignService}; ordinary entity
 * loot is disabled by the registry.</p>
 */
public final class ProfessorInfiniteSlidesEntity extends ModEntities.ProfessorEntity {
	private static final String OWNER_SAVE_KEY = "developers_hell_owner";
	private static final String ENCOUNTER_SAVE_KEY = "developers_hell_encounter";

	private UUID ownerUuid;
	private UUID encounterUuid;
	private boolean vulnerabilityOpen;
	private boolean loadedFromDisk;

	public ProfessorInfiniteSlidesEntity(EntityType<? extends Vindicator> type, Level level) {
		super(type, level);
	}

	@Override
	public void bind(UUID ownerUuid, UUID encounterUuid) {
		this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
		this.encounterUuid = Objects.requireNonNull(encounterUuid, "encounterUuid");
		var maxHealth = getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(LectureRules.standard().bossMaxHealth());
		}
		setHealth(LectureRules.standard().bossMaxHealth());
		vulnerabilityOpen = false;
		setNoAi(true);
	}

	@Override
	public UUID ownerUuid() {
		return ownerUuid;
	}

	@Override
	public UUID encounterUuid() {
		return encounterUuid;
	}

	public boolean isBoundTo(UUID ownerUuid, UUID encounterUuid) {
		return this.ownerUuid != null
				&& this.encounterUuid != null
				&& this.ownerUuid.equals(ownerUuid)
				&& this.encounterUuid.equals(encounterUuid);
	}

	/** True only after this instance decoded persisted entity data. */
	public boolean wasLoadedFromDisk() {
		return loadedFromDisk;
	}

	@Override
	public void setVulnerabilityOpen(boolean vulnerabilityOpen) {
		this.vulnerabilityOpen = vulnerabilityOpen && getHealth() > 0.0F;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		if (attacker == null) {
			return false;
		}

		LectureEncounterManager.ProfessorDamageAdmission admission =
				LectureEncounterManager.admitProfessorDamage(
						level,
						this,
						attacker.getUUID(),
						amount
				);
		if (!admission.accepted()) {
			return false;
		}

		float previousHealth = getHealth();
		boolean damaged = super.hurtServer(
				level,
				source,
				admission.acceptedDamage()
		);
		if (!damaged) {
			return false;
		}
		setHealth(admission.projectedHealth());
		if (!LectureEncounterManager.commitProfessorDamage(this, admission)) {
			setHealth(previousHealth);
			invulnerableTime = 0;
			return false;
		}
		return true;
	}

	@Override
	public void die(DamageSource source) {
		// Entity death is deliberately inert. The manager consumes accepted damage synchronously,
		// commits the matching campaign victory, then owns cleanup of this exact runtime entity.
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		loadedFromDisk = true;
		vulnerabilityOpen = false;
		UUID persistedOwner = input.read(OWNER_SAVE_KEY, UUIDUtil.CODEC).orElse(null);
		UUID persistedEncounter = input.read(ENCOUNTER_SAVE_KEY, UUIDUtil.CODEC).orElse(null);
		if (persistedOwner == null || persistedEncounter == null) {
			ownerUuid = null;
			encounterUuid = null;
			return;
		}
		ownerUuid = persistedOwner;
		encounterUuid = persistedEncounter;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (ownerUuid != null) {
			output.store(OWNER_SAVE_KEY, UUIDUtil.CODEC, ownerUuid);
		}
		if (encounterUuid != null) {
			output.store(ENCOUNTER_SAVE_KEY, UUIDUtil.CODEC, encounterUuid);
		}
	}
}
