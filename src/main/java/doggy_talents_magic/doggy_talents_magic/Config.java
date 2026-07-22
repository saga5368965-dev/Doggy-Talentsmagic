package doggy_talents_magic.doggy_talents_magic;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Doggy_talents_magic.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // コンフィグ項目
    public static final ForgeConfigSpec.IntValue GIFT_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CHARM_MESSAGE;
    public static final ForgeConfigSpec.IntValue BASE_CHARM_RANGE;

    static {
        BUILDER.push("Wizard Charm Talent Settings");

        GIFT_COOLDOWN_SECONDS = BUILDER
                .comment("魔術師が犬にプレゼントをくれる基本間隔（秒）")
                .defineInRange("giftCooldownSeconds", 45, 5, 600);

        ENABLE_CHARM_MESSAGE = BUILDER
                .comment("魔術師からアイテムをもらった時にチャットメッセージを表示するかどうか")
                .define("enableCharmMessage", true);

        BASE_CHARM_RANGE = BUILDER
                .comment("魔術師をメロメロにする基本の範囲（ブロック数）")
                .defineInRange("baseCharmRange", 8, 3, 32);

        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}