package org.cvrain.mooncakeoverflow.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeShape;

/**
 * 平放在地上的饼（生月饼用）。
 *
 * <p>几何按**形状**区分：
 * <ul>
 *   <li>方 → 完整的 12×12</li>
 *   <li>圆 → 切掉四角。原版模型的 {@code elements} 只支持轴对齐长方体，做不出真正的圆</li>
 * </ul>
 */
public class PattyBlock extends Block {
    public static final EnumProperty<MooncakeShape> SHAPE =
            EnumProperty.create("shape", MooncakeShape.class);

    private static final VoxelShape SQUARE =
            Shapes.box(2 / 16.0, 0.0, 2 / 16.0, 14 / 16.0, 2 / 16.0, 14 / 16.0);
    private static final VoxelShape ROUND = Shapes.or(
            Shapes.box(2 / 16.0, 0.0, 3 / 16.0, 14 / 16.0, 2 / 16.0, 13 / 16.0),
            Shapes.box(3 / 16.0, 0.0, 2 / 16.0, 13 / 16.0, 2 / 16.0, 14 / 16.0));

    public PattyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SHAPE, MooncakeShape.DEFAULT));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(PattyBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHAPE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(SHAPE) == MooncakeShape.SQUARE ? SQUARE : ROUND;
    }
}
