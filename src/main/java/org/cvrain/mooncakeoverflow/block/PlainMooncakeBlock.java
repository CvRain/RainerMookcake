package org.cvrain.mooncakeoverflow.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeData;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeKind;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeShape;
import org.cvrain.mooncakeoverflow.registry.ModDataComponents;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 摆在地上的**普通**月饼堆 —— 就是没包铜的那种，不会氧化。
 *
 * <p>一个方块分成 2×2 四个格子，**每格独立记录 {@link MooncakeKind}**（形状 × 纹样），
 * 所以可以混着摆：左上圆月饼、右上方纹花月饼、左下圆形纹方月饼……
 *
 * <p>几何按**形状**区分（和纹样无关）：
 * <ul>
 *   <li>方 → 完整的方块，四角是尖的</li>
 *   <li>圆 → 切掉四角（十字形）。原版模型的 {@code elements} 只支持轴对齐长方体，
 *       做不出真正的圆，只能这样近似</li>
 * </ul>
 *
 * <p>掉落不走战利品表，而是覆写 {@link #getDrops} —— 四格各是什么要逐一还原成物品，
 * 用战利品表写会变成几十个条件池。原版 {@code ShulkerBoxBlock} 也是这么做的。
 *
 * <p>{@link CopperMooncakeBlock} 继承它再加「氧化 + 涂蜡」那两个轴。
 * 之所以拆成两个方块而不是一个方块多两个属性：属性是**整块共用**的，
 * 普通月饼用不上它们，白背 4 × 2 倍的状态数是纯浪费。
 */
public class PlainMooncakeBlock extends Block {
    public static final EnumProperty<MooncakeKind> NW = EnumProperty.create("nw", MooncakeKind.class);
    public static final EnumProperty<MooncakeKind> NE = EnumProperty.create("ne", MooncakeKind.class);
    public static final EnumProperty<MooncakeKind> SW = EnumProperty.create("sw", MooncakeKind.class);
    public static final EnumProperty<MooncakeKind> SE = EnumProperty.create("se", MooncakeKind.class);

    /** 填充顺序：左上 → 右上 → 左下 → 右下。 */
    public static final List<EnumProperty<MooncakeKind>> SLOTS = List.of(NW, NE, SW, SE);

    private static final double H = 3 / 16.0;

    // 方形：整块 7×7
    private static final VoxelShape BOX_NW = box(1, 1, 8, 8);
    private static final VoxelShape BOX_NE = box(8, 1, 15, 8);
    private static final VoxelShape BOX_SW = box(1, 8, 8, 15);
    private static final VoxelShape BOX_SE = box(8, 8, 15, 15);

    // 圆形：切掉四角（横条 + 竖条）
    private static final VoxelShape ROUND_NW = Shapes.or(box(1, 2, 8, 7), box(2, 1, 7, 8));
    private static final VoxelShape ROUND_NE = Shapes.or(box(8, 2, 15, 7), box(9, 1, 14, 8));
    private static final VoxelShape ROUND_SW = Shapes.or(box(1, 9, 8, 14), box(2, 8, 7, 15));
    private static final VoxelShape ROUND_SE = Shapes.or(box(8, 9, 15, 14), box(9, 8, 14, 15));

    private static VoxelShape box(double x0, double z0, double x1, double z1) {
        return Shapes.box(x0 / 16.0, 0.0, z0 / 16.0, x1 / 16.0, H, z1 / 16.0);
    }

    private static VoxelShape shapeOf(EnumProperty<MooncakeKind> slot, MooncakeKind kind) {
        if (kind.isEmpty()) {
            return Shapes.empty();
        }
        boolean square = kind.shape() == MooncakeShape.SQUARE;
        if (slot == NW) return square ? BOX_NW : ROUND_NW;
        if (slot == NE) return square ? BOX_NE : ROUND_NE;
        if (slot == SW) return square ? BOX_SW : ROUND_SW;
        return square ? BOX_SE : ROUND_SE;
    }

    public PlainMooncakeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultStateForPile(stateDefinition.any()));
    }

    /** 让子类能给默认状态补上自己的属性。 */
    protected BlockState defaultStateForPile(BlockState state) {
        return state.setValue(NW, MooncakeKind.NONE)
                .setValue(NE, MooncakeKind.NONE)
                .setValue(SW, MooncakeKind.NONE)
                .setValue(SE, MooncakeKind.NONE);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(PlainMooncakeBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NW, NE, SW, SE);
    }

    /** 第一个空格子，满了返回 null。 */
    @Nullable
    public static EnumProperty<MooncakeKind> firstEmptySlot(BlockState state) {
        for (EnumProperty<MooncakeKind> slot : SLOTS) {
            if (state.getValue(slot).isEmpty()) {
                return slot;
            }
        }
        return null;
    }

    /** 放置时把物品上的形态放进第一格。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(NW, MooncakeData.kindOf(context.getItemInHand()));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = Shapes.empty();
        for (EnumProperty<MooncakeKind> slot : SLOTS) {
            shape = Shapes.or(shape, shapeOf(slot, state.getValue(slot)));
        }
        return shape.isEmpty() ? Shapes.block() : shape;
    }

    /** 四格逐一还原成物品，形态跟着走。 */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>(4);
        for (EnumProperty<MooncakeKind> slot : SLOTS) {
            MooncakeKind kind = state.getValue(slot);
            if (kind.isEmpty()) {
                continue;
            }
            drops.add(dropFor(state, kind));
        }
        return drops;
    }

    /** 子类换成铜月饼。 */
    protected ItemStack dropFor(BlockState state, MooncakeKind kind) {
        return MooncakeData.plain(kind);
    }

    /** 造一块指定形态的普通月饼（默认形态不写组件，保证能和熔炉烤出来的堆一起）。 */
    public static ItemStack stackOf(MooncakeKind kind) {
        return MooncakeData.plain(kind);
    }
}
