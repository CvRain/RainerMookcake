package org.cvrain.mooncakeoverflow.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.block.MovingBlockRenderState;

/**
 * 渲染器用的临时快照 —— 每格一个「要画什么方块」。
 *
 * <p>26.1 的渲染管线把「抽状态」和「提交绘制」拆成了两步：
 * {@code extractRenderState} 在主线程上把方块实体的数据抄进这个状态对象，
 * {@code submit} 再拿它去画。抄的时候不能碰世界，所以这里存的是
 * 已经算好的 {@link MovingBlockRenderState}（原版活塞推方块就是这么干的）。
 */
public class MooncakePileRenderState extends BlockEntityRenderState {
    /** 四格，空的是 null。 */
    public final MovingBlockRenderState[] pieces = new MovingBlockRenderState[4];

    public MooncakePileRenderState() {
        java.util.Arrays.fill(pieces, null);
    }
}
