package org.cvrain.mooncakeoverflow;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 模组配置。
 *
 * <p>阶段一只有一个选项，但它比看起来重要 —— 它是那封道歉信。
 * 阶段二加了氧化相关的三个开关。
 */
@Mod.EventBusSubscriber(modid = MooncakeOverflow.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue LOG_APOLOGY = BUILDER
            .comment("启动时打印那封道歉信。这是本模组的灵魂，关掉它请三思。")
            .define("logApology", true);

    private static final ForgeConfigSpec.BooleanValue BLOCK_OXIDATION_ENABLED = BUILDER
            .comment("摆在地上的月饼会不会像铜块一样慢慢氧化。")
            .define("blockOxidationEnabled", true);

    private static final ForgeConfigSpec.BooleanValue ITEM_OXIDATION_ENABLED = BUILDER
            .comment("背包里的月饼会不会慢慢变质（氧化）。")
            .define("itemOxidationEnabled", true);

    private static final ForgeConfigSpec.IntValue ITEM_OXIDATION_TICKS_PER_STAGE = BUILDER
            .comment("背包里的月饼多少刻氧化一档。24000 刻 = 游戏内一天。",
                    "默认 48000 = 两天一档，三档要六天，够你把它们忘在背包里。")
            .defineInRange("itemOxidationTicksPerStage", 48000, 1, Integer.MAX_VALUE);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean logApology = true;
    public static boolean blockOxidationEnabled = true;
    public static boolean itemOxidationEnabled = true;
    public static int itemOxidationTicksPerStage = 48000;

    private Config() {
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        logApology = LOG_APOLOGY.get();
        blockOxidationEnabled = BLOCK_OXIDATION_ENABLED.get();
        itemOxidationEnabled = ITEM_OXIDATION_ENABLED.get();
        itemOxidationTicksPerStage = ITEM_OXIDATION_TICKS_PER_STAGE.get();
    }
}
