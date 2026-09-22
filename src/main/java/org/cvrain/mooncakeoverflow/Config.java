package org.cvrain.mooncakeoverflow;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 模组配置。
 *
 * <p>阶段一只有一个选项，但它比看起来重要。
 */
@Mod.EventBusSubscriber(modid = MooncakeOverflow.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue LOG_APOLOGY = BUILDER
            .comment("启动时打印那封道歉信。这是本模组的灵魂，关掉它请三思。")
            .define("logApology", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean logApology = true;

    private Config() {
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        logApology = LOG_APOLOGY.get();
    }
}
