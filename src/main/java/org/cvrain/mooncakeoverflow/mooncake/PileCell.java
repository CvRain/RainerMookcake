package org.cvrain.mooncakeoverflow.mooncake;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * 月饼堆里的一个格子（左上 / 右上 / 左下 / 右下）。
 *
 * <p>这个枚举有两个身份：
 * <ul>
 *   <li>方块实体里 4 个格的**下标**（{@link #SLOTS}，{@link #index()} 给出 0～3）</li>
 *   <li>方块状态里的 {@code cell} 属性 —— 那纯粹是给渲染器当**模型表**用
 *       （26.1 没有"按名字查方块模型"的接口，只能借方块状态来查），
 *       所以多了一个 {@link #NONE}。真正摆在世界里的月饼堆永远是 {@code cell=none}，
 *       方块本身 {@code getRenderShape()} 返回 INVISIBLE，画东西的全是渲染器</li>
 * </ul>
 */
public enum PileCell implements StringRepresentable {
    NONE("none"),
    NW("nw"),
    NE("ne"),
    SW("sw"),
    SE("se");

    /** 真正能放月饼的格子，顺序就是填充顺序。 */
    public static final PileCell[] SLOTS = {NW, NE, SW, SE};

    public static final Codec<PileCell> CODEC = StringRepresentable.fromEnum(PileCell::values);

    public static final StreamCodec<ByteBuf, PileCell> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(id -> PileCell.values()[id], PileCell::ordinal);

    private final String name;

    PileCell(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /** 在方块实体里的下标；{@code none} 返回 -1。 */
    public int index() {
        return this == NONE ? -1 : ordinal() - 1;
    }

    public static PileCell byIndex(int index) {
        return index >= 0 && index < SLOTS.length ? SLOTS[index] : NONE;
    }

    /** 语言键：{@code mooncake_overflow.corner.<name>}（左上 / 右上 / 左下 / 右下）。 */
    public String translationKey() {
        return "mooncake_overflow.corner." + this.name;
    }
}
