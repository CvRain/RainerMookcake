package org.cvrain.mooncakeoverflow.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.cvrain.mooncakeoverflow.MooncakeOverflow;
import org.cvrain.mooncakeoverflow.block.MooncakeBlock;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeKind;

/**
 * 创造模式物品栏。
 */
public final class ModCreativeTabs {
    private ModCreativeTabs() {
    }

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MooncakeOverflow.MODID);

    public static final RegistryObject<CreativeModeTab> MOONCAKES = CREATIVE_MODE_TABS.register("mooncakes",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MooncakeOverflow.MODID + ".mooncakes"))
                    .withTabsBefore(CreativeModeTabs.FOOD_AND_DRINKS)
                    .icon(() -> new ItemStack(ModItems.MOONCAKE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.COCOA_BEAN_PASTE.get());
                        output.accept(ModItems.MOONCAKE_DOUGH.get());
                        output.accept(ModItems.FILLED_MOONCAKE_DOUGH.get());
                        output.accept(ModItems.MOONCAKE_MOLD.get());
                        output.accept(ModItems.SQUARE_MOONCAKE_MOLD.get());
                        output.accept(ModItems.RAW_MOONCAKE.get());
                        output.accept(ModItems.SQUARE_RAW_MOONCAKE.get());
                        // 形状 × 纹样 = 6 种形态全放进来，
                        // 否则创造模式只能拿到默认的圆形，其它形态得靠切石机
                        for (MooncakeKind kind : MooncakeKind.values()) {
                            if (!kind.isEmpty()) {
                                output.accept(MooncakeBlock.stackOf(kind));
                            }
                        }
                    })
                    .build());
}
