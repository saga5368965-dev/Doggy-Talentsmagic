package doggy_talents_magic.doggy_talents_magic.talent;

import doggytalents.api.inferface.AbstractDog;
import doggytalents.api.registry.Talent;
import doggytalents.api.registry.TalentInstance;
import doggytalents.common.entity.Dog;
import doggy_talents_magic.doggy_talents_magic.Config;
import io.redspace.ironsspellbooks.entity.mobs.wizards.IMerchantWizard;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class DogWizardCharmerTalent extends TalentInstance {

    private static final String GIFT_COOLDOWN_KEY = "DoggyMagicGiftCooldown";
    private static final ResourceLocation GREAT_INK_LOOT_TABLE = new ResourceLocation("irons_spellbooks", "magic_items/great_ink");

    public DogWizardCharmerTalent(Talent talentIn, int levelIn) {
        super(talentIn, levelIn);
    }

    @Override
    public void tick(AbstractDog dogIn) {
        if (dogIn.level().isClientSide) return;
        if (dogIn.tickCount % 20 == 0 && dogIn instanceof Dog dog) {
            int talentLevel = this.level();
            double range = Config.BASE_CHARM_RANGE.get() + ((talentLevel - 1) * 2.0D);

            AABB area = dog.getBoundingBox().inflate(range);
            List<Mob> nearbyWizards = dog.level().getEntitiesOfClass(Mob.class, area,
                    entity -> entity instanceof IMerchantWizard);

            for (Mob wizard : nearbyWizards) {
                if (!wizard.isAlive() || wizard.isAggressive()) continue;

                IMerchantWizard merchant = (IMerchantWizard) wizard;
                wizard.getLookControl().setLookAt(dog, 30.0F, 30.0F);
                spawnCharmParticles(wizard);
                applyTradeDiscount(merchant, talentLevel);

                handleWizardGifting(wizard, dog, talentLevel);
            }
        }
    }

    private void applyTradeDiscount(IMerchantWizard merchant, int talentLevel) {
        var offers = merchant.getOffers();
        if (offers == null || offers.isEmpty()) return;

        int discountAmount = Math.min(1 + talentLevel, 5);

        for (MerchantOffer offer : offers) {
            int currentBaseCost = offer.getBaseCostA().getCount();
            int maxDiscount = Math.max(1, currentBaseCost / 2);
            offer.setSpecialPriceDiff(-Math.min(discountAmount, maxDiscount));
        }
    }

    private void handleWizardGifting(Mob wizard, Dog dog, int talentLevel) {
        CompoundTag persistentData = wizard.getPersistentData();
        int cooldown = persistentData.getInt(GIFT_COOLDOWN_KEY);

        if (cooldown > 0) {
            persistentData.putInt(GIFT_COOLDOWN_KEY, cooldown - 20);
            return;
        }

        // コンフィグで設定した秒数をベースにクールダウン計算 (レベルが上がると少し縮小)
        int baseCooldownTicks = Config.GIFT_COOLDOWN_SECONDS.get() * 20;
        int nextCooldownTicks = Math.max(100, baseCooldownTicks - (talentLevel * 40));
        persistentData.putInt(GIFT_COOLDOWN_KEY, nextCooldownTicks);

        ItemStack giftStack = getRandomGift(wizard, talentLevel);

        if (!giftStack.isEmpty()) {
            Vec3 spawnPos = wizard.position().add(0, 0.5D, 0);
            ItemEntity itemEntity = new ItemEntity(wizard.level(), spawnPos.x, spawnPos.y, spawnPos.z, giftStack);

            Vec3 throwVec = dog.position().subtract(wizard.position()).normalize().scale(0.2D).add(0, 0.2D, 0);
            itemEntity.setDeltaMovement(throwVec);
            wizard.level().addFreshEntity(itemEntity);

            // 音は鳴らさず、手を振る動作のみ実行
            wizard.swing(wizard.getUsedItemHand());

            // --- メッセージ表示処理 ---
            if (Config.ENABLE_CHARM_MESSAGE.get()) {
                var owner = dog.getOwner();
                if (owner instanceof Player player) {
                    Component message = Component.translatable(
                            "message.doggy_talents_magic.wizard_gift",
                            wizard.getDisplayName().getString(),
                            dog.getName().getString()
                    ).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC);

                    player.sendSystemMessage(message);
                }
            }
        }
    }

    private ItemStack getRandomGift(Mob wizard, int talentLevel) {
        var random = wizard.getRandom();
        float roll = random.nextFloat();
        if (talentLevel >= 3 && roll < 0.05f && wizard.level() instanceof ServerLevel serverLevel) {
            LootTable lootTable = serverLevel.getServer().getLootData().getLootTable(GREAT_INK_LOOT_TABLE);
            LootParams lootParams = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, wizard.position())
                    .withParameter(LootContextParams.THIS_ENTITY, wizard)
                    .create(LootContextParamSets.GIFT);

            List<ItemStack> items = lootTable.getRandomItems(lootParams);
            if (!items.isEmpty()) {
                return items.get(0);
            }
        }
        if (roll < 0.30f) {
            return new ItemStack(ItemRegistry.ARCANE_ESSENCE.get(), 1 + random.nextInt(talentLevel));
        } else if (roll < 0.55f) {
            return new ItemStack(ItemRegistry.INK_UNCOMMON.get(), 1);
        } else if (roll < 0.75f && talentLevel >= 2) {
            return new ItemStack(ItemRegistry.INK_RARE.get(), 1);
        } else if (roll < 0.90f) {
            return new ItemStack(Items.EMERALD, 1 + random.nextInt(3));
        } else {
            return new ItemStack(Items.LAPIS_LAZULI, 2 + random.nextInt(4));
        }
    }

    private void spawnCharmParticles(Mob wizard) {
        if (wizard.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.HEART,
                    wizard.getX(), wizard.getY() + wizard.getEyeHeight() + 0.3D, wizard.getZ(),
                    1, 0.2D, 0.1D, 0.2D, 0.02D
            );
        }
    }
}