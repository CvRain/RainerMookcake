package org.cvrain.mooncakeoverflow.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.cvrain.mooncakeoverflow.MooncakeOverflow;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeKind;

/**
 * 自定义物品组件。
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
}
