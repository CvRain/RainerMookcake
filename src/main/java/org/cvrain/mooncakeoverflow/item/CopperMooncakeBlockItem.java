package org.cvrain.mooncakeoverflow.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.cvrain.mooncakeoverflow.Config;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeData;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeOxidation;

/**
 * 铜月饼的物品形态 —— 月饼外面包了一圈铜，所以它会**变质**。
 *
 * <p>和普通月饼的区别只有两点：
 * <ol>
 *   <li>名字多套两层前缀（氧化度、涂蜡）</li>
 *   <li>放在背包里会随时间氧化，见 {@link #inventoryTick}</li>
 * </ol>
 *
 * <p>两者可以放进同一堆月饼里 —— 哪一格是铜的、氧化到哪一步，都记在方块实体上。
 */
public class CopperMooncakeBlockItem extends MooncakeBlockItem {
    public CopperMooncakeBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean isCopperItem() {
        return true;
    }

    /**
     * 名字随形态、氧化度、涂蜡变化，例如「涂蜡的锈蚀的圆铜月饼·方纹」。
     *
     * <p>前缀靠两层可翻译文本套出来（{@code "锈蚀的%s"} 再套 {@code "涂蜡的%s"}），
     * 而不是给 4 × 2 × 6 = 48 种组合各写一条语言键。
     */
    @Override
    public Component getName(ItemStack stack) {
        Component name = Component.translatable(kindOf(stack).copperNameKey());

        String prefix = MooncakeData.oxidationOf(stack).prefixKey();
        if (prefix != null) {
            name = Component.translatable(prefix, name);
        }
        if (MooncakeData.isWaxed(stack)) {
            name = Component.translatable("mooncake_overflow.waxed", name);
        }
        return name;
    }

    /**
     * 在背包里慢慢氧化 —— 铜月饼真的会放坏。
     *
     * <p>26.1 的 {@code Item#inventoryTick} 只在服务端被调用，所以这里不用再判断端。
     * 走的是整摞物品（每格一次），不是每刻全背包扫描，性能上等价于原版的堆叠衰减。
     */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!Config.itemOxidationEnabled || MooncakeData.isWaxed(stack)) {
            return;
        }
        MooncakeOxidation oxidation = MooncakeData.oxidationOf(stack);
        if (oxidation.isFullyOxidized()) {
            return;
        }
        int ticks = Config.itemOxidationTicksPerStage;
        if (ticks > 0 && level.getRandom().nextInt(ticks) == 0) {
            MooncakeData.setOxidation(stack, oxidation.next());
        }
    }
}
