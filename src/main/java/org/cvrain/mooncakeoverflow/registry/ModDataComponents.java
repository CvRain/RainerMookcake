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
}
