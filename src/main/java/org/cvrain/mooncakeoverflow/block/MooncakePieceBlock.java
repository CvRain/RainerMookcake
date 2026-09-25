package org.cvrain.mooncakeoverflow.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeKind;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeOxidation;
import org.cvrain.mooncakeoverflow.mooncake.PileCell;

/**
 * **技术方块**：月饼堆的「模型表」，永远不会出现在世界里。
 *
 * <p>为什么需要它：26.1 删掉了「按 Identifier 取方块模型」的接口
 * （{@code ModelManager} 只剩 {@code getItemModel(Identifier)}，
 * 方块几何只能 {@code BlockStateModelSet.get(BlockState)}）。
 * 想画一块月饼，就得先有一个能查到那个模型的**方块状态**。
 *
 * <p>而月饼堆自己的方块状态已经被四格几何占满了（{@link CellShape}，
 * 碰撞箱必须能从状态推出来，见 {@link MooncakePileBlock}）。
 * 两者一乘就是 81 × 280 = 22,680 种状态和一份几 MB 的 blockstate 文件，
 * 所以干脆把模型表挪到这个从不被放置的方块上：
 *
 * <ul>
 *   <li>它没有对应的物品，创造栏里也没有，正常玩法拿不到</li>
 *   <li>它只有 280 个状态，blockstate 文件把每种组合指向对应的单格模型</li>
 *   <li>渲染器用它来查模型，查完在月饼堆的位置上画出来</li>
 * </ul>
 *
 * <p>它自己也不画东西（{@code INVISIBLE}）—— 真被 {@code /setblock} 摆出来只会是个隐形方块。
 */
public class MooncakePieceBlock extends Block {
    /** 哪一格。 */
    public static final EnumProperty<PileCell> CELL = EnumProperty.create("cell", PileCell.class);
    /** 什么形态。 */
    public static final EnumProperty<MooncakeKind> KIND = EnumProperty.create("kind", MooncakeKind.class);
    /** 是不是铜月饼（决定用普通贴图还是锈蚀贴图）。 */
    public static final BooleanProperty COPPER = BooleanProperty.create("copper");
    /** 铜月饼的氧化度。 */
    public static final EnumProperty<MooncakeOxidation> OXIDATION =
            EnumProperty.create("oxidation", MooncakeOxidation.class);

    public MooncakePieceBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(CELL, PileCell.NONE)
                .setValue(KIND, MooncakeKind.NONE)
                .setValue(COPPER, Boolean.FALSE)
                .setValue(OXIDATION, MooncakeOxidation.DEFAULT));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(MooncakePieceBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CELL, KIND, COPPER, OXIDATION);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** 渲染器用：造一个能查到「第 cell 格、这个形态、这个氧化度」的模型的状态。 */
    public BlockState modelStateFor(PileCell cell, MooncakeKind kind, boolean copper,
                                    MooncakeOxidation oxidation) {
        return defaultBlockState()
                .setValue(CELL, cell)
                .setValue(KIND, kind)
                .setValue(COPPER, copper)
                .setValue(OXIDATION, copper ? oxidation : MooncakeOxidation.DEFAULT);
    }
}
