package org.cvrain.mooncakeoverflow.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.cvrain.mooncakeoverflow.Config;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeData;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeKind;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeOxidation;

/**
 * 摆在地上的**铜月饼**堆 —— 月饼外面包了一圈铜，所以会像铜块一样氧化。
 *
 * <p>这是本模组氧化机制的载体：没包铜的普通月饼不会变质，
 * 包了铜才会经历 铜 → 斑驳 → 锈蚀 → 氧化 四个阶段。
 *
 * <p><b>氧化和涂蜡是整个方块共用的</b>，不像形态那样一格一个。理由是方块状态的数量：
 * 四格形态本来就有 7⁴ = 2401 种，氧化度再按格拆开就是 2401 × 4⁴ ≈ 61 万，
 * 那才是真的会卡。现在一共 7⁴ × 4 × 2 = 19208 种，和「特别卡」还有距离。
 *
 * <p>所以往一堆里加月饼时，氧化度和涂蜡状态必须和这一堆一致 ——
 * 否则"塞进去一块新鲜的、拿出来变成锈的"这种亏谁都受不了。
 */
public class CopperMooncakeBlock extends PlainMooncakeBlock {
    /** 整堆月饼的氧化程度。 */
    public static final EnumProperty<MooncakeOxidation> OXIDATION =
            EnumProperty.create("oxidation", MooncakeOxidation.class);

    /** 整堆月饼有没有涂蜡。涂了就不再氧化。 */
    public static final BooleanProperty WAXED = BooleanProperty.create("waxed");

    /** 和铜块一样的随机刻氧化概率。 */
    private static final float OXIDATION_CHANCE = 0.05688889F;

    public CopperMooncakeBlock(Properties properties) {
        super(properties);
        // 父类已经注册了四格形态的默认值，这里补上铜月饼自己的两个属性
        registerDefaultState(defaultBlockState()
                .setValue(OXIDATION, MooncakeOxidation.DEFAULT)
                .setValue(WAXED, Boolean.FALSE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(CopperMooncakeBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OXIDATION, WAXED);
    }

    /** 放置时把物品上的形态 / 氧化度 / 涂蜡状态一起写进方块。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        ItemStack stack = context.getItemInHand();
        return super.getStateForPlacement(context)
                .setValue(OXIDATION, MooncakeData.oxidationOf(stack))
                .setValue(WAXED, MooncakeData.isWaxed(stack));
    }

    /**
     * 随机刻氧化 —— 和铜块同一套节奏（平均几十分钟一档）。
     *
     * <p>用随机刻而不是 {@code tick()}，是因为随机刻是原版自己的调度器在管，
     * 我们不需要为此维护任何每刻遍历。
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!Config.blockOxidationEnabled) {
            return;
        }
        MooncakeOxidation oxidation = state.getValue(OXIDATION);
        if (state.getValue(WAXED) || oxidation.isFullyOxidized()) {
            return;
        }
        if (random.nextFloat() < OXIDATION_CHANCE) {
            level.setBlockAndUpdate(pos, state.setValue(OXIDATION, oxidation.next()));
        }
    }

    /**
     * 蜜脾涂蜡 / 斧头刮。
     *
     * <p>原版的涂蜡是「把方块换成另一个方块」（走 {@code HoneycombItem.WAXABLES} 那张表），
     * 我们这里涂蜡**不换方块也不换外观**，所以只能在方块自己的交互里处理。
     * 斧头刮蜡、刮氧化同理。
     *
     * <p>好消息是交互顺序对我们有利：原版是先调**方块的** {@code useItemOn}，
     * 只有它没吃掉这次交互才轮到 {@code ItemStack#useOn}。
     * 所以我们返回 {@code SUCCESS} 就能把原版的蜜脾 / 斧头逻辑整个挡掉，不会双重处理。
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                         Player player, InteractionHand hand, BlockHitResult hit) {
        boolean waxed = state.getValue(WAXED);

        // 蜜脾 → 涂蜡
        if (stack.is(Items.HONEYCOMB) && !waxed) {
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.setBlockAndUpdate(pos, state.setValue(WAXED, Boolean.TRUE));
                serverLevel.playSound(null, pos, SoundEvents.HONEYCOMB_WAX_ON, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (!stack.is(ItemTags.AXES)) {
            return InteractionResult.PASS;
        }

        // 斧头 → 先刮蜡
        if (waxed) {
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.setBlockAndUpdate(pos, state.setValue(WAXED, Boolean.FALSE));
                serverLevel.playSound(null, pos, SoundEvents.AXE_WAX_OFF, SoundSource.BLOCKS, 1.0F, 1.0F);
                stack.hurtAndBreak(1, player, hand);
            }
            return InteractionResult.SUCCESS;
        }

        // 没蜡 → 氧化度往回退一档
        MooncakeOxidation oxidation = state.getValue(OXIDATION);
        if (oxidation == MooncakeOxidation.DEFAULT) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.setBlockAndUpdate(pos, state.setValue(OXIDATION, oxidation.previous()));
            serverLevel.playSound(null, pos, SoundEvents.AXE_SCRAPE, SoundSource.BLOCKS, 1.0F, 1.0F);
            stack.hurtAndBreak(1, player, hand);
        }
        return InteractionResult.SUCCESS;
    }

    /** 掉出来的也是铜月饼，且带着这一堆的氧化度和涂蜡状态。 */
    @Override
    protected ItemStack dropFor(BlockState state, MooncakeKind kind) {
        return MooncakeData.copper(kind, state.getValue(OXIDATION), state.getValue(WAXED));
    }

    /** 造一块指定形态的铜月饼（默认氧化度、没涂蜡）。 */
    public static ItemStack stackOf(MooncakeKind kind) {
        return MooncakeData.copper(kind, MooncakeOxidation.DEFAULT, false);
    }
}
