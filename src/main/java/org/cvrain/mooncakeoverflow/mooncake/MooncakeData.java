package org.cvrain.mooncakeoverflow.mooncake;

import net.minecraft.world.item.ItemStack;
import org.cvrain.mooncakeoverflow.registry.ModDataComponents;
import org.cvrain.mooncakeoverflow.registry.ModItems;

/**
 * 月饼身上那几个「会变」的属性的读写口子。
 *
 * <p>规矩只有一条：**默认值不写组件**。{@code round_round} 形态、{@code copper} 氧化度、
 * 没涂蜡，三者都不写。原因很实际 —— 熔炉烤出来、切石机切出来、掉落物还原出来的，
 * 只要组件表一致就能堆成一摞，否则玩家会看到两摞一模一样的月饼。
 *
 * <p>注意普通月饼（{@link #plain}）**压根不写氧化度**：氧化是外面那圈铜的事，
 * 没包铜的月饼不会变质。这也是为什么它不能跟铜月饼堆在一起。
 */
public final class MooncakeData {
    private MooncakeData() {
    }

    public static MooncakeKind kindOf(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.MOONCAKE_KIND.get(), MooncakeKind.DEFAULT);
    }

    public static void setKind(ItemStack stack, MooncakeKind kind) {
        if (kind == MooncakeKind.DEFAULT) {
            stack.remove(ModDataComponents.MOONCAKE_KIND.get());
        } else {
            stack.set(ModDataComponents.MOONCAKE_KIND.get(), kind);
        }
    }

    public static MooncakeOxidation oxidationOf(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.MOONCAKE_OXIDATION.get(), MooncakeOxidation.DEFAULT);
    }

    public static void setOxidation(ItemStack stack, MooncakeOxidation oxidation) {
        if (oxidation == MooncakeOxidation.DEFAULT) {
            stack.remove(ModDataComponents.MOONCAKE_OXIDATION.get());
        } else {
            stack.set(ModDataComponents.MOONCAKE_OXIDATION.get(), oxidation);
        }
    }

    public static boolean isWaxed(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.MOONCAKE_WAXED.get(), Boolean.FALSE);
    }

    public static void setWaxed(ItemStack stack, boolean waxed) {
        if (waxed) {
            stack.set(ModDataComponents.MOONCAKE_WAXED.get(), Boolean.TRUE);
        } else {
            stack.remove(ModDataComponents.MOONCAKE_WAXED.get());
        }
    }

    /** 普通月饼：只有形态，不会氧化。 */
    public static ItemStack plain(MooncakeKind kind) {
        ItemStack stack = new ItemStack(ModItems.MOONCAKE.get());
        setKind(stack, kind);
        return stack;
    }

    /** 铜月饼：形态 + 氧化度 + 涂蜡。 */
    public static ItemStack copper(MooncakeKind kind, MooncakeOxidation oxidation, boolean waxed) {
        ItemStack stack = new ItemStack(ModItems.COPPER_MOONCAKE.get());
        setKind(stack, kind);
        setOxidation(stack, oxidation);
        setWaxed(stack, waxed);
        return stack;
    }
}
