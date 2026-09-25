package org.cvrain.mooncakeoverflow.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.cvrain.mooncakeoverflow.block.PlainMooncakeBlock;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeData;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeKind;

/**
 * 月饼堆的物品形态（普通月饼 / 铜月饼共用）。
 *
 * <p>月饼既能吃也能摆，所以用 Shift 区分：
 * <ul>
 *   <li><b>右键</b> → 进食（走原版食物逻辑）</li>
 *   <li><b>Shift + 右键</b> → 放置 / 往已有的月饼堆里再放一块（**不同形态可以混装**）</li>
 * </ul>
 *
 * <p>原理：{@link #useOn} 在不按 Shift 时返回 {@code PASS}，
 * 原版就会继续往下走，调用 {@code Item#use} 触发进食。
 */
public class MooncakeBlockItem extends BlockItem {
    public MooncakeBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** 从物品上读形态（形状 × 纹样）。 */
    public static MooncakeKind kindOf(ItemStack stack) {
        return MooncakeData.kindOf(stack);
    }

    /** 名字随形态变化，例如「圆月饼·方纹」。 */
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(kindOf(stack).nameKey());
    }

    /**
     * 能不能往这一堆里再加一块。
     *
     * <p>普通月饼只看形态，所以永远可以；铜月饼还要看氧化度和涂蜡是否一致
     * （那两项是整块共用的），见 {@code CopperMooncakeBlockItem}。
     */
    protected boolean canAddTo(BlockState state, ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();

        // 没按 Shift → 什么都不做，把这次右键让给进食逻辑
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        ItemStack stack = context.getItemInHand();

        // 目标不是同一堆月饼 → 走普通的方块放置（getStateForPlacement 会把状态带进去）
        if (!state.is(getBlock())) {
            return super.useOn(context);
        }

        EnumProperty<MooncakeKind> empty = PlainMooncakeBlock.firstEmptySlot(state);
        if (empty == null || !canAddTo(state, stack)) {
            return InteractionResult.PASS; // 四格满了 / 状态对不上
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS; // 客户端不做事，等服务端同步
        }

        serverLevel.setBlockAndUpdate(pos, state.setValue(empty, kindOf(stack)));
        serverLevel.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // 按着 Shift 时不进食，免得"想摆一块结果吃掉了"
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        return super.use(level, player, hand);
    }
}
