package org.cvrain.mooncakeoverflow.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.cvrain.mooncakeoverflow.block.PattyBlock;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeShape;

/**
 * 生月饼的物品形态 —— 形状（圆/方）由**是哪个物品**决定，所以这里带一个形状字段。
 *
 * <p>生月饼之所以要拆成两个物品，是因为**熔炉配方没法读取输入物品的组件**：
 * 只有原料本身不同，才能让两条熔炉配方各自产出对应形状的成品。
 */
public class ShapeBlockItem extends BlockItem {
    private final MooncakeShape shape;

    public ShapeBlockItem(Block block, MooncakeShape shape, Properties properties) {
        super(block, properties);
        this.shape = shape;
    }

    public MooncakeShape shape() {
        return this.shape;
    }

    /** 放置时把形状写进方块状态。 */
    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = super.getPlacementState(context);
        if (state == null) {
            return null;
        }
        EnumProperty<MooncakeShape> property = PattyBlock.SHAPE;
        return state.hasProperty(property) ? state.setValue(property, this.shape) : state;
    }
}
