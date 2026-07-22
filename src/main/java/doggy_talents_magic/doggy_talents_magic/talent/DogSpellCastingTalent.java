package doggy_talents_magic.doggy_talents_magic.talent;

import doggytalents.api.anim.DogAnimation;
import doggytalents.api.inferface.AbstractDog;
import doggytalents.api.registry.Talent;
import doggytalents.api.registry.TalentInstance;
import doggytalents.common.entity.Dog;
import doggytalents.common.entity.DogAllyCheck;
import doggytalents.common.entity.ai.triggerable.TriggerableAction;
import doggy_talents_magic.doggy_talents_magic.Doggy_talents_magic;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import io.redspace.ironsspellbooks.spells.CastingMobAimingData;
import io.redspace.ironsspellbooks.spells.ender.TeleportSpell;
import io.redspace.ironsspellbooks.spells.fire.BurningDashSpell;
import io.redspace.ironsspellbooks.spells.holy.HealSpell;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class DogSpellCastingTalent extends TalentInstance {

    private int cooldown = 0;
    private ItemStack spellbookStack = ItemStack.EMPTY;

    public DogSpellCastingTalent(Talent talentIn, int levelIn) {
        super(talentIn, levelIn);
    }

    public void setSpellbook(ItemStack stack) {
        this.spellbookStack = stack;
        this.spellbookStack.setCount(1);
    }

    public ItemStack getSpellbook() {
        return this.spellbookStack;
    }

    @Override
    public void tick(AbstractDog dogIn) {
        if (dogIn.level().isClientSide) return;

        if (this.cooldown > 0) {
            --this.cooldown;
        }

        if (dogIn instanceof Dog dog) {
            LivingEntity castTarget = dog.getTarget();
            int talentLevel = this.level();

            if (castTarget != null && castTarget.isAlive()) {
                var owner = dog.getOwner();
                boolean isAlly = owner != null && (castTarget == owner || DogAllyCheck.isAlliedToDog(dog, castTarget, owner));

                if (isAlly && talentLevel < 2) {
                    return;
                }

                double distanceSq = dog.distanceToSqr(castTarget);

                if (talentLevel >= 3 && !isAlly && distanceSq <= 49.0D) {
                    Vec3 escapeVec = dog.position().subtract(castTarget.position()).normalize();
                    Vec3 targetPos = dog.position().add(escapeVec.scale(2.0D));
                    dog.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, 1.35D);
                }

                if (distanceSq <= 900.0D) {
                    if (this.cooldown <= 0 && !this.spellbookStack.isEmpty()) {
                        dog.getNavigation().stop();
                        this.cooldown = 10;
                        dog.triggerAction(new DogSpellCastAction(dog, this.spellbookStack, getCooldownTicks(), castTarget));
                    }
                } else {
                    if (dog.getAnim() == DogAnimation.NONE && !isAlly) {
                        dog.getNavigation().moveTo(castTarget, 1.25D);
                    }
                }
            }
        }
    }

    public int getCooldownTicks() {
        var level = this.level();
        if (level >= 5) return 30;
        if (level <= 1) return 70;
        if (level <= 2) return 60;
        if (level <= 3) return 50;
        if (level <= 4) return 40;
        return 45;
    }

    @Override
    public void writeToNBT(AbstractDog dogIn, CompoundTag compound) {
        super.writeToNBT(dogIn, compound);
        if (!this.spellbookStack.isEmpty()) {
            CompoundTag itemTag = new CompoundTag();
            this.spellbookStack.save(itemTag);
            compound.put("EquippedSpellbook", itemTag);
        }
    }

    @Override
    public void readFromNBT(AbstractDog dogIn, CompoundTag compound) {
        super.readFromNBT(dogIn, compound);
        if (compound.contains("EquippedSpellbook")) {
            this.spellbookStack = ItemStack.of(compound.getCompound("EquippedSpellbook"));
        } else {
            this.spellbookStack = ItemStack.EMPTY;
        }
    }

    public static class DogSpellCastAction extends TriggerableAction {
        private ActionPhase phase;
        private int stopTick;
        private final ItemStack spellbookStack;
        private final int nextCooldown;
        private LivingEntity forcedTarget;
        private int tickTillCast = 6;

        private AbstractSpell activeSpell = null;
        private int activeSpellLevel = 1;
        private MagicData magicData = null;
        private CastingMobAimingData aimingData = null;
        private int castDurationRemaining = 0;
        private ItemEntity floatingBookEntity = null;

        public DogSpellCastAction(Dog dog, ItemStack spellbook, int nextCooldown, LivingEntity target) {
            super(dog, false, false);
            this.phase = ActionPhase.START_CAST;
            this.spellbookStack = spellbook;
            this.nextCooldown = nextCooldown;
            this.forcedTarget = target;
        }

        @Override
        public void onStart() {
            if (phase == ActionPhase.START_CAST) {
                prepareSpell();
                if (this.activeSpell == null) {
                    this.setState(ActionState.FINISHED);
                    return;
                }
                beginHowlAnim();
                spawnFloatingSpellbook();
            } else {
                this.setState(ActionState.FINISHED);
                return;
            }
            this.dog.setForcedActionAnim(true);
            this.dog.dogSoundManager.setAmbientLocked(true);
        }

        private void prepareSpell() {
            if (!ISpellContainer.isSpellContainer(spellbookStack)) return;
            ISpellContainer container = ISpellContainer.get(spellbookStack);
            List<AbstractSpell> spells = new ArrayList<>();
            for (int i = 0; i < container.getMaxSpellCount(); i++) {
                SpellData data = container.getSpellAtIndex(i);
                if (data != null && data.getSpell() != null && data.getSpell() != SpellRegistry.none()) {
                    spells.add(data.getSpell());
                }
            }

            if (!spells.isEmpty()) {
                this.activeSpell = spells.get(dog.getRandom().nextInt(spells.size()));

                int talentLevel = 1;
                var talentInst = dog.getTalent(Doggy_talents_magic.SPELL_CASTING_TALENT).orElse(null);
                if (talentInst != null) {
                    talentLevel = talentInst.level();
                }
                this.activeSpellLevel = Math.min(talentLevel, this.activeSpell.getMaxLevel());

                // 正・負のエフェクト判定
                boolean isPositive = isPositiveSpell(this.activeSpell);

                if (isPositive) {
                    if (talentLevel >= 2) {
                        var owner = dog.getOwner();
                        if (owner != null && owner.isAlive() && owner.getHealth() < owner.getMaxHealth() && dog.distanceToSqr(owner) <= 64.0D) {
                            this.forcedTarget = owner;
                        }
                    }
                } else {
                    var owner = dog.getOwner();
                    if (this.forcedTarget == owner || (owner != null && DogAllyCheck.isAlliedToDog(dog, this.forcedTarget, owner))) {
                        LivingEntity enemyTarget = dog.getTarget();
                        if (enemyTarget != null && enemyTarget.isAlive()) {
                            this.forcedTarget = enemyTarget;
                        }
                    }
                }

                this.magicData = MagicData.getPlayerMagicData(dog);

                if (this.magicData != null) {
                    if (this.magicData.getSyncedData() == null) {
                        this.magicData.setSyncedData(new SyncedSpellData(dog));
                    }

                    int effectiveCastTime = this.activeSpell.getEffectiveCastTime(this.activeSpellLevel, dog);
                    this.castDurationRemaining = effectiveCastTime;

                    if (this.activeSpell != SpellRegistry.TELEPORT_SPELL.get() && this.activeSpell != SpellRegistry.FROST_STEP_SPELL.get()) {
                        if (this.activeSpell == SpellRegistry.BLOOD_STEP_SPELL.get()) {
                            setTeleportLocationBehindTarget(3);
                        } else if (this.activeSpell == SpellRegistry.BURNING_DASH_SPELL.get()) {
                            this.magicData.setAdditionalCastData(new BurningDashSpell.BurningDashDirectionOverrideCastData());
                        } else if (this.forcedTarget != null) {
                            this.aimingData = new CastingMobAimingData();
                            this.aimingData.updateAim(this.forcedTarget, 1.0F);
                            this.magicData.setAdditionalCastData(this.aimingData);
                        }
                    } else {
                        setTeleportLocationBehindTarget(10);
                    }

                    this.magicData.initiateCast(this.activeSpell, this.activeSpellLevel, effectiveCastTime, CastSource.MOB, SpellSelectionManager.MAINHAND);
                    this.activeSpell.onServerPreCast(dog.level(), this.activeSpellLevel, dog, this.magicData);
                }
            }
        }

        private boolean isPositiveSpell(AbstractSpell spell) {
            // 修正箇所 1: getSpellId() は ResourceLocation なので getPath() を呼ぶ
            String path = spell.getSpellId();

            if (path.contains("damage") || path.contains("ray") || path.contains("bolt") ||
                    path.contains("blast") || path.contains("slash") || path.contains("drain") ||
                    path.contains("curse") || path.contains("poison") || path.contains("slow") ||
                    path.contains("wither") || path.contains("fire") || path.contains("ice")) {
                return false;
            }

            return spell instanceof HealSpell ||
                    spell.getSchoolType() == SchoolRegistry.HOLY.get() ||
                    path.contains("heal") ||
                    path.contains("blessing") ||
                    path.contains("fortify") ||
                    path.contains("haste") ||
                    path.contains("shield") ||
                    path.contains("ward");
        }

        private void spawnFloatingSpellbook() {
            if (!dog.level().isClientSide && !spellbookStack.isEmpty()) {
                Vec3 look = dog.getLookAngle();
                Vec3 spawnPos = dog.getEyePosition().add(look.scale(0.8D)).add(0, -0.2D, 0);

                ItemEntity itemEntity = new ItemEntity(dog.level(), spawnPos.x, spawnPos.y, spawnPos.z, spellbookStack.copy());
                itemEntity.setNeverPickUp();
                itemEntity.setNoGravity(true);
                itemEntity.setDeltaMovement(Vec3.ZERO);
                dog.level().addFreshEntity(itemEntity);
                this.floatingBookEntity = itemEntity;
            }
        }

        private void setTeleportLocationBehindTarget(int distance) {
            if (this.forcedTarget != null && this.magicData != null) {
                Vec3 rotation = this.forcedTarget.getLookAngle().normalize().scale(-distance);
                Vec3 teleportPos = rotation.add(this.forcedTarget.position());
                this.magicData.setAdditionalCastData(new TeleportSpell.TeleportData(teleportPos));
            }
        }

        @Override
        public void tick() {
            if (dog.getAnim() != DogAnimation.HOWL) {
                this.setState(ActionState.FINISHED);
                return;
            }
            if (dog.tickCount >= stopTick) {
                this.setState(ActionState.FINISHED);
                return;
            }

            if (this.floatingBookEntity != null && this.floatingBookEntity.isAlive()) {
                Vec3 look = dog.getLookAngle();
                Vec3 frontPos = dog.getEyePosition().add(look.scale(0.8D)).add(0, -0.1D, 0);
                this.floatingBookEntity.setPos(frontPos.x, frontPos.y, frontPos.z);
                this.floatingBookEntity.setDeltaMovement(Vec3.ZERO);
            }

            if (dog.level().isClientSide && dog.tickCount % 2 == 0) {
                spawnCastVisuals();
            }

            if (phase == ActionPhase.START_CAST) {
                if (this.forcedTarget != null && this.forcedTarget.isAlive()) {
                    dog.getLookControl().setLookAt(this.forcedTarget, 45.0F, 45.0F);

                    // 修正箇所 2: 1.20.1 では setAimPosition メソッドではなく aimPosition フィールドへ直接代入する
                    if (this.aimingData != null) {
                        this.aimingData.updateAim(this.forcedTarget, 1.0F);
                    }
                }

                if (tickTillCast > 0) {
                    --tickTillCast;
                    if (tickTillCast == 0) {
                        this.dog.playSound(SoundEvents.WOLF_HOWL, 0.5F, dog.getVoicePitch());
                    }
                }

                if (tickTillCast == 0) {
                    if (this.forcedTarget != null && this.forcedTarget.isAlive()) {
                        double d0 = this.forcedTarget.getX() - dog.getX();
                        double d1 = this.forcedTarget.getEyeY() - dog.getEyeY();
                        double d2 = this.forcedTarget.getZ() - dog.getZ();
                        double d3 = Mth.sqrt((float)(d0 * d0 + d2 * d2));
                        float f = (float)(Mth.atan2(d2, d0) * (180F / (float)Math.PI)) - 90F;
                        float f1 = (float)(-(Mth.atan2(d1, d3) * (180F / (float)Math.PI)));

                        dog.setYRot(f);
                        dog.setYHeadRot(f);
                        dog.setXRot(f1);
                    }

                    if (!dog.level().isClientSide && this.activeSpell != null && this.magicData != null) {
                        this.magicData.handleCastDuration();
                        this.activeSpell.onServerCastTick(dog.level(), this.activeSpellLevel, dog, this.magicData);

                        if (this.castDurationRemaining == this.activeSpell.getEffectiveCastTime(this.activeSpellLevel, dog)) {
                            if (this.activeSpell.getCastType() == CastType.LONG || this.activeSpell.getCastType() == CastType.INSTANT || this.activeSpell.getCastType() == CastType.CONTINUOUS) {
                                if (this.activeSpell.checkPreCastConditions(dog.level(), this.activeSpellLevel, dog, this.magicData)) {
                                    this.activeSpell.onCast(dog.level(), this.activeSpellLevel, dog, CastSource.MOB, this.magicData);
                                }
                            }
                        }

                        --this.castDurationRemaining;

                        if (this.activeSpell.getCastType() == CastType.CONTINUOUS && this.castDurationRemaining > 0) {
                            return;
                        }

                        this.activeSpell.onServerCastComplete(dog.level(), this.activeSpellLevel, dog, this.magicData, false);
                    }

                    this.phase = ActionPhase.CAST_DONE;
                    this.setState(ActionState.FINISHED);
                }
            }
        }

        private void spawnCastVisuals() {
            Vec3 pos = dog.position();
            dog.level().addParticle(
                    ParticleTypes.ENCHANT,
                    pos.x + (dog.getRandom().nextDouble() - 0.5D) * 0.8D,
                    pos.y + 0.3D + dog.getRandom().nextDouble() * 0.5D,
                    pos.z + (dog.getRandom().nextDouble() - 0.5D) * 0.8D,
                    0, 0.1D, 0
            );
        }

        @Override
        public void onStop() {
            if (this.floatingBookEntity != null && this.floatingBookEntity.isAlive()) {
                this.floatingBookEntity.discard();
                this.floatingBookEntity = null;
            }

            this.dog.setForcedActionAnim(false);
            dog.dogSoundManager.setAmbientLocked(false);
            if (!dog.getAnim().interupting()) {
                dog.setAnim(DogAnimation.NONE);
            }
            if (!dog.level().isClientSide && this.magicData != null) {
                this.magicData.resetCastingState();
            }
            setTalentCooldown();
        }

        private void beginHowlAnim() {
            int spellCastTime = (this.activeSpell != null) ? this.activeSpell.getEffectiveCastTime(this.activeSpellLevel, dog) : 0;
            this.stopTick = dog.tickCount + Math.max(DogAnimation.HOWL.getLengthTicks(), this.tickTillCast + spellCastTime + 10);
            this.dog.setAnim(DogAnimation.HOWL);
            this.tickTillCast = 6;
        }

        private void setTalentCooldown() {
            dog.getTalent(Doggy_talents_magic.SPELL_CASTING_TALENT)
                    .ifPresent(inst -> {
                        if (inst instanceof DogSpellCastingTalent talent) {
                            talent.cooldown = this.nextCooldown;
                        }
                    });
        }

        @Override
        public boolean canOverrideSit() {
            return false;
        }

        private enum ActionPhase {
            START_CAST, CAST_DONE
        }
    }
}