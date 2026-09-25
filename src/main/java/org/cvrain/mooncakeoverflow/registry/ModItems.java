package org.cvrain.mooncakeoverflow.registry;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.cvrain.mooncakeoverflow.MooncakeOverflow;
import org.cvrain.mooncakeoverflow.item.CopperMooncakeBlockItem;
import org.cvrain.mooncakeoverflow.item.MooncakeBlockItem;
import org.cvrain.mooncakeoverflow.item.ShapeBlockItem;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeShape;

/**
 * 物品注册表 —— 阶段一的完整工艺链。
 *
 * <pre>
 *   COCOA_BEAN_PASTE     可可豆豆沙   ← 可可豆 + 装水的炼药锅
 *   MOONCAKE_DOUGH       月饼面团     ← 4 小麦 + 糖 + 鸡蛋
 *   FILLED_MOONCAKE_DOUGH 带馅面饼    ← 4 月饼面团 + 豆沙（可放置）
 *   MOONCAKE_MOLD        月饼模具     ← 铁锭 + 木棍
 *   RAW_MOONCAKE         生月饼       ← 用模具压印面团后取出
 *   MOONCAKE             月饼         ← 烤生月饼（可食用，不会氧化）
 *   COPPER_MOONCAKE      铜月饼       ← 月饼 + 铜锭（会氧化）
 * </pre>
 */
public final class ModItems {
    private ModItems() {
    }

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MooncakeOverflow.MODID);

    /** 可可豆豆沙：可可豆与炼药锅里的水混合的产物。 */
    public static final RegistryObject<Item> COCOA_BEAN_PASTE = ITEMS.register("cocoa_bean_paste",
            () -> new Item(new Item.Properties().setId(ITEMS.key("cocoa_bean_paste"))));

    /** 月饼面团：做月饼的基础。 */
    public static final RegistryObject<Item> MOONCAKE_DOUGH = ITEMS.register("mooncake_dough",
            () -> new Item(new Item.Properties().setId(ITEMS.key("mooncake_dough"))));

    /**
     * 带馅面饼：把馅包进面团里，可以放置到地上等待压印。
     *
     * <p>注意 {@code useBlockDescriptionPrefix()}：26.1 起物品名用 {@code item.} 还是 {@code block.}
     * 前缀是**由 Properties 显式决定**的（{@code Item.getDescriptionId()} 是 final，没法覆写）。
     * 方块物品必须自己声明，否则会显示成未翻译的 `item.mooncake_overflow.xxx` 键名。
     * 前缀后面的 path 用的是**物品自己的 id**，不是方块的 id。
     */
    public static final RegistryObject<Item> FILLED_MOONCAKE_DOUGH = ITEMS.register("filled_mooncake_dough",
            () -> new BlockItem(ModBlocks.MOONCAKE_DOUGH_BLOCK.get(),
                    new Item.Properties()
                            .setId(ITEMS.key("filled_mooncake_dough"))
                            .useBlockDescriptionPrefix()));

    /** 圆形模具：压出圆形的月饼面团。有耐久，用完会坏。 */
    public static final RegistryObject<Item> MOONCAKE_MOLD = ITEMS.register("mooncake_mold",
            () -> new Item(new Item.Properties()
                    .setId(ITEMS.key("mooncake_mold"))
                    .durability(64)));

    /** 方形模具：压出方形的月饼面团。和圆形模具是"竖着 / 横着"的关系。 */
    public static final RegistryObject<Item> SQUARE_MOONCAKE_MOLD = ITEMS.register("square_mooncake_mold",
            () -> new Item(new Item.Properties()
                    .setId(ITEMS.key("square_mooncake_mold"))
                    .durability(64)));

    /**
     * 生月饼（圆形）。压印完成但还没烤。
     *
     * <p>生月饼按形状**拆成两个物品**，是为了过得了熔炉：熔炉配方读不到输入物品的组件，
     * 只有原料本身不同，两条配方才能各自产出对应形状的成品。
     * 成品那边就不用拆了 —— 配方结果可以带组件。
     */
    public static final RegistryObject<Item> RAW_MOONCAKE = ITEMS.register("raw_mooncake",
            () -> new ShapeBlockItem(ModBlocks.RAW_MOONCAKE.get(), MooncakeShape.ROUND,
                    new Item.Properties()
                            .setId(ITEMS.key("raw_mooncake"))
                            .useBlockDescriptionPrefix()));

    /** 生月饼（方形）。 */
    public static final RegistryObject<Item> SQUARE_RAW_MOONCAKE = ITEMS.register("square_raw_mooncake",
            () -> new ShapeBlockItem(ModBlocks.RAW_MOONCAKE.get(), MooncakeShape.SQUARE,
                    new Item.Properties()
                            .setId(ITEMS.key("square_raw_mooncake"))
                            .useBlockDescriptionPrefix()));

    /**
     * 月饼：成品。
     *
     * <p>它同时是食物和方块：
     * <ul>
     *   <li><b>右键</b> → 吃（{@code alwaysEdible()}，满饱食度也能吃）</li>
     *   <li><b>Shift + 右键</b> → 摆到地上，同一格最多叠 4 块</li>
     * </ul>
     */
    public static final RegistryObject<Item> MOONCAKE = ITEMS.register("mooncake",
            () -> new MooncakeBlockItem(ModBlocks.MOONCAKE_BLOCK.get(),
                    new Item.Properties()
                            .setId(ITEMS.key("mooncake"))
                            .useBlockDescriptionPrefix()
                            .food(new FoodProperties.Builder()
                                    .nutrition(6)
                                    .saturationModifier(0.8F)
                                    .alwaysEdible()
                                    .build())));

    /**
     * 铜月饼：月饼外面包了一圈铜（合成台：月饼 + 铜锭）。
     *
     * <p>这是**会氧化的那一个**。没包铜的普通月饼放多久都不会坏，
     * 因为它身上没有任何可氧化的东西。
     */
    public static final RegistryObject<Item> COPPER_MOONCAKE = ITEMS.register("copper_mooncake",
            () -> new CopperMooncakeBlockItem(ModBlocks.MOONCAKE_BLOCK.get(),
                    new Item.Properties()
                            .setId(ITEMS.key("copper_mooncake"))
                            .useBlockDescriptionPrefix()
                            .food(new FoodProperties.Builder()
                                    .nutrition(6)
                                    .saturationModifier(0.8F)
                                    .alwaysEdible()
                                    .build())));
}
