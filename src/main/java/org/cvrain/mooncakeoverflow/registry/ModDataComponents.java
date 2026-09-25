package org.cvrain.mooncakeoverflow.registry;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.cvrain.mooncakeoverflow.MooncakeOverflow;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeKind;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeOxidation;
import org.cvrain.mooncakeoverflow.mooncake.PileCell;

/**
 * 自定义物品组件。
 *
 * <p>三个组件都是「默认值不写」的：{@code round_round} 的形态、{@code copper} 的氧化度、
 * {@code false} 的涂蜡。这样熔炉烤出来的和切石机切出来的（组件不同）才能堆叠成一摞。
 */
public final class ModDataComponents {
    private ModDataComponents() {
    }

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MooncakeOverflow.MODID);

    /**
     * 月饼的形态（形状 × 纹样）。
     *
     * <p>客户端物品模型用 {@code minecraft:select} + {@code property: "minecraft:component"}
     * 读它来切换外观，所以必须 {@code networkSynchronized}（否则客户端看不到）。
     */
    public static final RegistryObject<DataComponentType<MooncakeKind>> MOONCAKE_KIND =
            DATA_COMPONENTS.register("mooncake_kind",
                    () -> DataComponentType.<MooncakeKind>builder()
                            .persistent(MooncakeKind.CODEC)
                            .networkSynchronized(MooncakeKind.STREAM_CODEC)
                            .build());

    /**
     * 月饼的氧化程度（铜 → 斑驳 → 锈蚀 → 氧化）。
     *
     * <p>物品放在背包里会随时间自己变，方块则走随机刻。同样要被物品模型读到，
     * 所以也要 {@code networkSynchronized}。
     */
    public static final RegistryObject<DataComponentType<MooncakeOxidation>> MOONCAKE_OXIDATION =
            DATA_COMPONENTS.register("mooncake_oxidation",
                    () -> DataComponentType.<MooncakeOxidation>builder()
                            .persistent(MooncakeOxidation.CODEC)
                            .networkSynchronized(MooncakeOxidation.STREAM_CODEC)
                            .build());

    /**
     * 是否涂过蜡。涂了就不再氧化。
     *
     * <p>涂蜡**不改变外观**，所以它不需要参与物品模型的 {@code select}，
     * 方块那边也不需要为它多一个模型。
     */
    public static final RegistryObject<DataComponentType<Boolean>> MOONCAKE_WAXED =
            DATA_COMPONENTS.register("mooncake_waxed",
                    () -> DataComponentType.<Boolean>builder()
                            .persistent(Codec.BOOL)
                            .networkSynchronized(ByteBufCodecs.BOOL)
                            .build());

    /**
     * 切开的那一片是月饼的哪一角。
     *
     * <p>切石机里四个角是**四个可选项**，所以它得记在物品上。
     * 拼回一整块时按"在工作台里的摆放顺序"决定谁去哪一格，这个名字上的角更多是给玩家看的。
     */
    public static final RegistryObject<DataComponentType<PileCell>> MOONCAKE_CORNER =
            DATA_COMPONENTS.register("mooncake_corner",
                    () -> DataComponentType.<PileCell>builder()
                            .persistent(PileCell.CODEC)
                            .networkSynchronized(PileCell.STREAM_CODEC)
                            .build());

    /**
     * 这一块是不是从**铜月饼**上切下来的。
     *
     * <p>为什么要单独一个标记：铜月饼的"铜阶段"本来就不写氧化度组件
     * （那样才能和别的堆在一起），所以光看有没有氧化度分不出
     * "普通月饼的四分之一"和"刚包好铜的四分之一"。
     */
    public static final RegistryObject<DataComponentType<Boolean>> MOONCAKE_COPPER =
            DATA_COMPONENTS.register("mooncake_copper",
                    () -> DataComponentType.<Boolean>builder()
                            .persistent(Codec.BOOL)
                            .networkSynchronized(ByteBufCodecs.BOOL)
                            .build());

}
