package dev.developershell.entity;

import dev.developershell.lecture.LectureEncounterManager;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Ephemeral, no-loot quiz consequence owned by one live Lecture runtime. */
public final class HomeworkAddEntity extends Zombie {
	public static final float MAX_HEALTH = 12.0F;
	public static final double ATTACK_DAMAGE = 2.0D;
	private static final String OWNER_SAVE_KEY = "developers_hell_owner";
	private static final String ENCOUNTER_SAVE_KEY = "developers_hell_encounter";

	private UUID ownerUuid;
	private UUID encounterUuid;
	private boolean loadedFromDisk;

	public HomeworkAddEntity(EntityType<? extends Zombie> type, Level level) {
		super(type, level);
		xpReward = 0;
	}

	public void bind(UUID ownerUuid, UUID encounterUuid) {
		this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
		this.encounterUuid = Objects.requireNonNull(encounterUuid, "encounterUuid");
		var maxHealth = getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(MAX_HEALTH);
		}
		var attackDamage = getAttribute(Attributes.ATTACK_DAMAGE);
		if (attackDamage != null) {
			attackDamage.setBaseValue(ATTACK_DAMAGE);
		}
		setHealth(MAX_HEALTH);
		setPersistenceRequired();
		setCanBreakDoors(false);
	}

	public UUID ownerUuid() {
		return ownerUuid;
	}

	public UUID encounterUuid() {
		return encounterUuid;
	}

	public boolean isBoundTo(UUID ownerUuid, UUID encounterUuid) {
		return this.ownerUuid != null
				&& this.encounterUuid != null
				&& this.ownerUuid.equals(ownerUuid)
				&& this.encounterUuid.equals(encounterUuid);
	}

	public boolean wasLoadedFromDisk() {
		return loadedFromDisk;
	}

	@Override
	public void tick() {
		if (!level().isClientSide()) {
			if (loadedFromDisk
					|| ownerUuid == null
					|| encounterUuid == null
					|| !LectureEncounterManager.isHomeworkAddCurrent(ownerUuid, encounterUuid, getUUID())) {
				discard();
				return;
			}
			ServerPlayer owner = LectureEncounterManager.participant(encounterUuid)
					.filter(player -> player.getUUID().equals(ownerUuid) && player.level() == level())
					.orElse(null);
			setTarget(owner);
		}
		super.tick();
	}

	@Override
	public void setTarget(LivingEntity target) {
		if (target != null && (ownerUuid == null || !ownerUuid.equals(target.getUUID()))) {
			return;
		}
		super.setTarget(target);
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return ownerUuid != null && ownerUuid.equals(target.getUUID()) && super.canAttack(target);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		return target instanceof LivingEntity living && canAttack(living) && super.doHurtTarget(level, target);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		loadedFromDisk = true;
		ownerUuid = input.read(OWNER_SAVE_KEY, UUIDUtil.CODEC).orElse(null);
		encounterUuid = input.read(ENCOUNTER_SAVE_KEY, UUIDUtil.CODEC).orElse(null);
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
