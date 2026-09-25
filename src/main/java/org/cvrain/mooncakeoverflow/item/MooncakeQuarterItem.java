package org.cvrain.mooncakeoverflow.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.cvrain.mooncakeoverflow.Config;
import org.cvrain.mooncakeoverflow.block.MooncakePileBlock;
import org.cvrain.mooncakeoverflow.block.MooncakePileBlockEntity;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeData;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeKind;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeOxidation;
import org.cvrain.mooncakeoverflow.mooncake.PileCell;
import org.cvrain.mooncakeoverflow.registry.ModDataComponents;
import org.cvrain.mooncakeoverflow.registry.ModItems;

/**
 * 切开的月饼 —— 四分之一块（左上 / 右上 / 左下 / 右下）。
 *
 * <p>它继承 {@link ArrowItem}，再配上 {@code minecraft:arrows} 物品标签，
 * 所以**能当弩的弹药**（射出去是普通的箭，但消耗的是一块月饼，这已经足够搞怪了）。
 *
 * <p>身上记着四件事，全都从切之前那一整块月饼继承过来：
 * 形态（形状 × 纹样）、是不是铜月饼、氧化度、涂蜡，另外加上**这一块是哪一角**。
 *
 * <p>它不能单独摆成月饼堆 —— 要摆也是摆回一整块。对着已有的月饼堆右键会把它
 * **塞进空格子**（每格一块，正好能拼回一整块）。
 *
 * <p>注意它**不能吃**：右键已经被"塞进月饼堆"占了，而且四分之一块本来也不够塞牙缝。
 */
public class MooncakeQuarterItem extends ArrowItem {
    public MooncakeQuarterItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------ 组件读写

    public static PileCell cornerOf(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.MOONCAKE_CORNER.get(), PileCell.NW);
    }

    public static void setCorner(ItemStack stack, PileCell corner) {
        stack.set(ModDataComponents.MOONCAKE_CORNER.get(), corner);
    }

    public static boolean isCopper(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.MOONCAKE_COPPER.get(), Boolean.FALSE);
    }

    public static void setCopper(ItemStack stack, boolean copper) {
        if (copper) {
            stack.set(ModDataComponents.MOONCAKE_COPPER.get(), Boolean.TRUE);
        } else {
            stack.remove(ModDataComponents.MOONCAKE_COPPER.get());
        }
    }

    /**
     * 转成月饼堆里的一格。
     *
     * <p>永远是 {@code partial = true}：片是"某个月饼的一角"，
     * 渲染时只画它那一象限的纹样（四片拼起来才是一个完整的月饼）。
     */
    public static MooncakePileBlockEntity.Piece pieceOf(ItemStack stack) {
        return new MooncakePileBlockEntity.Piece(
                MooncakeData.kindOf(stack),
                isCopper(stack),
                MooncakeData.oxidationOf(stack),
                MooncakeData.isWaxed(stack),
                java.util.Optional.ofNullable(
                        stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)),
                true);
    }

    /** 从一整块月饼切出一片，组件原样继承（一片 = 四分之一块）。 */
    public static ItemStack cutFrom(ItemStack whole, PileCell corner) {
        ItemStack out = new ItemStack(ModItems.MOONCAKE_QUARTER.get());
        MooncakeData.setKind(out, MooncakeData.kindOf(whole));
        setCopper(out, MooncakeBlockItem.isCopper(whole));
        MooncakeData.setOxidation(out, MooncakeData.oxidationOf(whole));
        MooncakeData.setWaxed(out, MooncakeData.isWaxed(whole));
        setCorner(out, corner);
        return out;
    }

    /**
     * 是不是"四片月饼"——拼回一整块的条件。
     *
     * <p>只要数量对、都是月饼片就行：**不要求四片一样**，
     * 也不区分谁原来在哪个角（那是拼回去之后才决定的，按在工作台里的摆放顺序）。
     */
    public static boolean isFourQuarters(java.util.List<ItemStack> stacks) {
        if (stacks.size() != 4) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (!(stack.getItem() instanceof MooncakeQuarterItem)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 名字

    /**
     * 名字 = 一整块月饼的名字 + 哪一角。
     *
     * <p>例如「涂蜡的锈蚀的圆铜月饼·花形纹·左上」。
     */
    @Override
    public Component getName(ItemStack stack) {
        MooncakeKind kind = MooncakeData.kindOf(stack);
        Component base = Component.translatable(
                isCopper(stack) ? kind.copperNameKey() : kind.nameKey());

        String prefix = MooncakeData.oxidationOf(stack).prefixKey();
        if (isCopper(stack) && prefix != null) {
            base = Component.translatable(prefix, base);
        }
        if (MooncakeData.isWaxed(stack)) {
            base = Component.translatable("mooncake_overflow.waxed", base);
        }
        return Component.translatable("mooncake_overflow.quarter",
                base,
                Component.translatable(cornerOf(stack).translationKey()));
    }

    // ------------------------------------------------------------------ 交互

    /** 对着月饼堆右键 → 塞进空格子。 */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (!state.is(org.cvrain.mooncakeoverflow.registry.ModBlocks.MOONCAKE_BLOCK.get())) {
            return super.useOn(context);
        }
        MooncakePileBlockEntity pile = MooncakePileBlock.pileAt(level, pos);
        if (pile == null || !pile.hasRoom()) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        MooncakePileBlock.addPiece(level, pos, pieceOf(stack));
        SoundType sound = state.getSoundType();
        serverLevel.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                sound.getVolume(), sound.getPitch());
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    /** 铜的那一份在背包里一样会氧化。 */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!isCopper(stack) || !Config.itemOxidationEnabled || MooncakeData.isWaxed(stack)) {
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
