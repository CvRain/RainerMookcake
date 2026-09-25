package org.cvrain.mooncakeoverflow.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import org.cvrain.mooncakeoverflow.block.MooncakePileBlock;
import org.cvrain.mooncakeoverflow.block.MooncakePileBlockEntity;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeData;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeKind;

import javax.annotation.Nullable;

/**
 * 月饼堆的物品形态（普通月饼 / 铜月饼共用）。
 *
 * <p>月饼既能吃也能摆，所以用 Shift 区分：
 * <ul>
 *   <li><b>右键</b> → 进食（走原版食物逻辑）</li>
 *   <li><b>Shift + 右键</b> → 放置 / 往已有的月饼堆里再放一块
 *       （**任意月饼都能混进同一堆**，形态 / 是不是铜的 / 氧化度 / 涂蜡各记各的）</li>
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

    /** 这一摞是不是铜月饼（只有铜月饼会氧化）。 */
    public boolean isCopperItem() {
        return false;
    }

    public static boolean isCopper(ItemStack stack) {
        return stack.getItem() instanceof MooncakeBlockItem item && item.isCopperItem();
    }

    /**
     * 名字随形态变化，例如「圆月饼·方纹」。
     *
     * <p>五仁月饼更啰嗦：四个角各报一次名，串成
     * 「五仁月饼（左上·圆月饼 + 右上·氧化的方铜月饼·花形纹 + …）」——
     * 这正是这个模组的主题，**名称溢出**。
     *
     * <p>名字里那四个角是**切下来的位置**，不代表它摆下去占四格 ——
     * 五仁月饼和普通月饼一样只占一格。
     */
    @Override
    public Component getName(ItemStack stack) {
        // 五仁月饼的名字走原版 custom_name 组件（见 CompositeMooncakeRecipe），
        // 到不了这里；这里只管普通月饼的"形态名"。
        return Component.translatable(kindOf(stack).nameKey());
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

        // 目标不是月饼堆 → 走普通的方块放置（setPlacedBy 会把这一块塞进方块实体）
        if (!level.getBlockState(pos).is(getBlock())) {
            return super.useOn(context);
        }

        MooncakePileBlockEntity pile = MooncakePileBlock.pileAt(level, pos);
        if (pile == null || !pile.hasRoom()) {
            return InteractionResult.PASS; // 四格都满了
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS; // 客户端不做事，等服务端同步
        }

        ItemStack stack = context.getItemInHand();
        // 一个月饼就是一个月饼：只摆一格（五仁月饼也一样，它只是名字更长的月饼）
        MooncakePileBlock.addPiece(level, pos, MooncakePileBlockEntity.Piece.of(stack));
        // 和方块自己的 SoundType 保持一致（原版蛋糕是 WOOL）
        // 注意别用 BlockItem#getPlaceSound —— 26.1 里它标了 @Deprecated
        SoundType sound = level.getBlockState(pos).getSoundType();
        serverLevel.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                sound.getVolume(), sound.getPitch());

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
