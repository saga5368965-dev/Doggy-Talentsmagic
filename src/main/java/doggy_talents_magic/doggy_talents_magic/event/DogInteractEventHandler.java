package doggy_talents_magic.doggy_talents_magic.event;

import doggytalents.common.entity.Dog;
import doggy_talents_magic.doggy_talents_magic.Doggy_talents_magic;
import doggy_talents_magic.doggy_talents_magic.talent.DogSpellCastingTalent;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Doggy_talents_magic.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DogInteractEventHandler {

    @SubscribeEvent
    public static void onDogInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Dog dog) || event.getLevel().isClientSide) {
            return;
        }

        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        ItemStack heldItem = player.getItemInHand(hand);

        // 自分の犬以外は処理しない
        if (!dog.isOwnedBy(player)) {
            return;
        }

        // 手に持っているアイテムが魔導書（SpellBook、またはSpellContainer）である場合のみ処理
        if (!heldItem.isEmpty() && (heldItem.getItem() instanceof SpellBook || ISpellContainer.isSpellContainer(heldItem))) {
            if (player.isShiftKeyDown()) {
                return;
            }

            // タレントインスタンスを安全に取得
            var talentInst = dog.getTalent(Doggy_talents_magic.SPELL_CASTING_TALENT).orElse(null);
            if (talentInst == null) {
                // 翻訳キー: message.doggy_talents_magic.no_talent
                player.sendSystemMessage(
                        Component.translatable("message.doggy_talents_magic.no_talent")
                                .withStyle(ChatFormatting.RED)
                );
                event.setCanceled(true);
                return;
            }

            if (talentInst instanceof DogSpellCastingTalent talent) {
                ItemStack currentEquipped = talent.getSpellbook();

                // 既に魔導書がタレント内に保持されていたらプレイヤーのインベントリに返す
                if (!currentEquipped.isEmpty()) {
                    if (!player.getInventory().add(currentEquipped.copy())) {
                        player.drop(currentEquipped.copy(), false);
                    }
                }

                // コピーを作成し、魔導書アイテム側の初期化ロジックを確実に走らせる
                ItemStack itemToEquip = heldItem.copy();
                if (itemToEquip.getItem() instanceof SpellBook spellBookItem) {
                    spellBookItem.initializeSpellContainer(itemToEquip);
                }

                // 初期化が完了した魔導書データをタレントに直接セット
                talent.setSpellbook(itemToEquip);

                // クリエイティブモードでなければ手持ちの魔導書を消費
                if (!player.getAbilities().instabuild) {
                    heldItem.shrink(1);
                }

                // 翻訳キー: message.doggy_talents_magic.equipped_spellbook
                player.sendSystemMessage(
                        Component.translatable("message.doggy_talents_magic.equipped_spellbook")
                                .withStyle(ChatFormatting.GREEN)
                );
                event.setCanceled(true);
            }
        }
    }
}