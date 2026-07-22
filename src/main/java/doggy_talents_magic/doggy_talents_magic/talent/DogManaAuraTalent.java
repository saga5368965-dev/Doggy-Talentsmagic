package doggy_talents_magic.doggy_talents_magic.talent;

import doggytalents.api.inferface.AbstractDog;
import doggytalents.api.registry.Talent;
import doggytalents.api.registry.TalentInstance;
import doggytalents.common.entity.Dog;
import doggytalents.common.entity.DogAllyCheck;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class DogManaAuraTalent extends TalentInstance {

    public DogManaAuraTalent(Talent talentIn, int levelIn) {
        super(talentIn, levelIn);
    }

    @Override
    public void tick(AbstractDog dogIn) {
        if (dogIn.level().isClientSide) return;
        if (dogIn.tickCount % 20 == 0 && dogIn instanceof Dog dog) {
            int talentLevel = this.level();
            double range = 8.0D + (talentLevel * 2.0D);
            float manaAmount = 1.0F + (talentLevel * 1.5F);

            AABB area = dog.getBoundingBox().inflate(range);
            List<LivingEntity> entities = dog.level().getEntitiesOfClass(LivingEntity.class, area);

            var owner = dog.getOwner();

            for (LivingEntity entity : entities) {
                boolean isAlly = (owner != null && (entity == owner || DogAllyCheck.isAlliedToDog(dog, entity, owner)));

                if (isAlly && entity.isAlive()) {
                    MagicData magicData = MagicData.getPlayerMagicData(entity);
                    if (magicData != null) {
                        float currentMana = magicData.getMana();
                        magicData.addMana(manaAmount);
                        if (entity instanceof ServerPlayer serverPlayer) {
                            serverPlayer.serverLevel().sendParticles(
                                    ParticleTypes.WITCH,
                                    entity.getX(), entity.getY() + 1.0D, entity.getZ(),
                                    3, 0.2D, 0.3D, 0.2D, 0.02D
                            );
                        }
                    }
                }
            }
        }
    }
}
