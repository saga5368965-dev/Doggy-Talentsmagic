package doggy_talents_magic.doggy_talents_magic;

import com.mojang.logging.LogUtils;
import doggytalents.api.registry.Talent;
import doggy_talents_magic.doggy_talents_magic.talent.DogManaAuraTalent;
import doggy_talents_magic.doggy_talents_magic.talent.DogSpellCastingTalent;
import doggy_talents_magic.doggy_talents_magic.talent.DogWizardCharmerTalent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;

@Mod(Doggy_talents_magic.MODID)
public class Doggy_talents_magic {

    public static final String MODID = "doggy_talents_magic";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceKey<Registry<Talent>> TALENTS_REGISTRY_KEY =
            ResourceKey.createRegistryKey(new ResourceLocation("doggytalents", "talents"));

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static Talent SPELL_CASTING_TALENT;
    public static Talent MANA_AURA_TALENT;
    public static Talent WIZARD_CHARMER_TALENT;
    public Doggy_talents_magic() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerTalents);

        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("DOGGY TALENTS MAGIC COMMON SETUP");
    }

    private void registerTalents(RegisterEvent event) {
        if (event.getRegistryKey().equals(TALENTS_REGISTRY_KEY)) {
            SPELL_CASTING_TALENT = new Talent(DogSpellCastingTalent::new);
            MANA_AURA_TALENT = new Talent(DogManaAuraTalent::new);
            WIZARD_CHARMER_TALENT = new Talent(DogWizardCharmerTalent::new);

            event.register(TALENTS_REGISTRY_KEY,
                    new ResourceLocation(MODID, "spell_casting"),
                    () -> SPELL_CASTING_TALENT
            );
            event.register(TALENTS_REGISTRY_KEY,
                    new ResourceLocation(MODID, "mana_aura"),
                    () -> MANA_AURA_TALENT
            );
            event.register(TALENTS_REGISTRY_KEY,
                    new ResourceLocation(MODID, "wizard_charmer"),
                    () -> WIZARD_CHARMER_TALENT
            );

            LOGGER.info("Successfully registered all Doggy Talents Magic talents!");
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
        }
    }
}