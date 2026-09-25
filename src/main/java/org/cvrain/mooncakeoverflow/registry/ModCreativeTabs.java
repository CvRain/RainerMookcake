package org.cvrain.mooncakeoverflow.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.cvrain.mooncakeoverflow.MooncakeOverflow;

import org.cvrain.mooncakeoverflow.mooncake.MooncakeData;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeKind;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeOxidation;

/**
 * 创造模式物品栏。
 *
 * <p>注意创造栏**不允许重复条目**，同一个 ItemStack 放两次会直接崩
 * （{@code IllegalStateException: Accidentally adding the same item stack twice}）。
 * 氧化链的样本要跳过铜阶段 —— 那正好就是上面那轮里的「圆形月饼」。
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

                        // 普通月饼：形状 × 纹样 = 6 种形态全放进来，
                        // 否则创造模式只能拿到默认的圆形，其它形态得靠切石机
                        for (MooncakeKind kind : MooncakeKind.values()) {
                            if (!kind.isEmpty()) {
                                output.accept(MooncakeData.plain(kind));
                            }
                        }

                        // 铜月饼：同样 6 种形态（这些才会氧化）
                        for (MooncakeKind kind : MooncakeKind.values()) {
                            if (!kind.isEmpty()) {
                                output.accept(MooncakeData.copper(kind, MooncakeOxidation.DEFAULT, false));
                            }
                        }

                        // 氧化链和涂蜡各来一个样本 —— 真等它自己氧化要好几个小时，
                        // 想直接看效果的话从这里拿。
                        // 铜阶段跳过：上面那轮的圆铜月饼就是它，重复放会直接崩
                        for (MooncakeOxidation oxidation : MooncakeOxidation.values()) {
                            if (oxidation == MooncakeOxidation.DEFAULT) {
                                continue;
                            }
                            output.accept(MooncakeData.copper(
                                    MooncakeKind.DEFAULT, oxidation, false));
                        }
                        output.accept(MooncakeData.copper(
                                MooncakeKind.DEFAULT, MooncakeOxidation.DEFAULT, true));
                    })
                    .build());
}
