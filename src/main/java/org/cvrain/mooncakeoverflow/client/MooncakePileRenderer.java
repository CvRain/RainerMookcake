package org.cvrain.mooncakeoverflow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.cvrain.mooncakeoverflow.block.MooncakePieceBlock;
import org.cvrain.mooncakeoverflow.block.MooncakePileBlockEntity;
import org.cvrain.mooncakeoverflow.mooncake.PileCell;
import org.cvrain.mooncakeoverflow.registry.ModBlocks;

/**
 * 月饼堆的渲染器 —— 四格月饼全靠它画。
 *
 * <p>方块本身 {@code getRenderShape()} 是 {@code INVISIBLE}，所以正常那条路什么都不画；
 * 这里每格查一个「模型表状态」再画一次。
 *
 * <p>为什么要绕「模型表」这一圈：**26.1 没有按名字查方块模型的接口**
 * （{@code ModelManager} 只剩 {@code getItemModel(Identifier)}，
 * 方块几何只能通过 {@code BlockStateModelSet.get(BlockState)} 拿）。
 * 所以月饼堆方块带了一组只用于查表的方块状态（{@code cell} × {@code kind} ×
 * {@code copper} × {@code oxidation}），它们对应的 blockstate 文件把每种组合指向对应的单格模型。
 *
 * <p>画的时候用 {@link SubmitNodeCollector#submitMovingBlock} ——
 * 这是原版活塞推方块用的那条路，帮我们把模型查找、光照、渲染类型都处理好了，
 * 不用自己碰 {@code RenderType} 和 tint 数组。单格模型本身就烘焙在正确的格子位置上，
 * 所以这里连平移都不用做。
 */
public class MooncakePileRenderer
        implements BlockEntityRenderer<MooncakePileBlockEntity, MooncakePileRenderState> {

    public MooncakePileRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public MooncakePileRenderState createRenderState() {
        return new MooncakePileRenderState();
    }

    @Override
    public void extractRenderState(MooncakePileBlockEntity pile, MooncakePileRenderState state,
                                   float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
                .extractBase(pile, state, crumbling);
        java.util.Arrays.fill(state.pieces, null);

        if (!(pile.getLevel() instanceof ClientLevel level)) {
            return;
        }
        BlockPos pos = pile.getBlockPos();
        MooncakePieceBlock table = (MooncakePieceBlock) ModBlocks.MOONCAKE_PIECE.get();
        var biome = level.getBiome(pos);

        for (PileCell cell : PileCell.SLOTS) {
            MooncakePileBlockEntity.Piece piece = pile.piece(cell.index());
            if (piece.isEmpty()) {
                continue;
            }
            BlockState model = table.modelStateFor(
                    cell, piece.kind(), piece.copper(), piece.oxidation());
            state.pieces[cell.index()] = movingBlock(level, pos, model, biome);
        }
    }

    /** 照抄原版 {@code PistonHeadRenderer#createMovingBlock}。 */
    private static MovingBlockRenderState movingBlock(ClientLevel level, BlockPos pos, BlockState model,
                                                      net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        MovingBlockRenderState moving = new MovingBlockRenderState();
        moving.randomSeedPos = pos;
        moving.blockPos = pos;
        moving.blockState = model;
        moving.biome = biome;
        moving.cardinalLighting = level.cardinalLighting();
        moving.lightEngine = level.getLightEngine();
        return moving;
    }

    @Override
    public void submit(MooncakePileRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        for (MovingBlockRenderState piece : state.pieces) {
            if (piece != null) {
                collector.submitMovingBlock(poseStack, piece);
            }
        }
    }
}
