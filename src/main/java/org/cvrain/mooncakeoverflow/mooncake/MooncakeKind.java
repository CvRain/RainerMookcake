package org.cvrain.mooncakeoverflow.mooncake;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import javax.annotation.Nullable;

/**
 * 一个月饼的完整形态 = **形状** × **纹样**。
 *
 * <p>这两个轴是独立的（形状 2 种 × 纹样 3 种 = 6 种），所以能做出
 * "方纹的圆月饼"这种组合。{@link #NONE} 只给月饼堆的空格子用。
 *
 * <p>做成一个枚举而不是两个组件，是因为：
 * <ul>
 *   <li>方块状态里一格只能是一个枚举值，两轴拆开会让四个格子的属性数量翻倍</li>
 *   <li>物品模型只需要一层 {@code select}，不用嵌套</li>
 * </ul>
 */
public enum MooncakeKind implements StringRepresentable {
    NONE("none", null, null),
    ROUND_ROUND("round_round", MooncakeShape.ROUND, MooncakePattern.ROUND),
    ROUND_SQUARE("round_square", MooncakeShape.ROUND, MooncakePattern.SQUARE),
    ROUND_FLOWER("round_flower", MooncakeShape.ROUND, MooncakePattern.FLOWER),
    SQUARE_ROUND("square_round", MooncakeShape.SQUARE, MooncakePattern.ROUND),
    SQUARE_SQUARE("square_square", MooncakeShape.SQUARE, MooncakePattern.SQUARE),
    SQUARE_FLOWER("square_flower", MooncakeShape.SQUARE, MooncakePattern.FLOWER);

    /** 默认形态：圆形状 + 圆纹样。烤出来就是这个，所以它不带组件。 */
    public static final MooncakeKind DEFAULT = ROUND_ROUND;

    public static final Codec<MooncakeKind> CODEC = StringRepresentable.fromEnum(MooncakeKind::values);

    public static final StreamCodec<ByteBuf, MooncakeKind> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(id -> MooncakeKind.values()[id], MooncakeKind::ordinal);

    private final String name;
    private final MooncakeShape shape;
    private final MooncakePattern pattern;

    MooncakeKind(String name, MooncakeShape shape, MooncakePattern pattern) {
        this.name = name;
        this.shape = shape;
        this.pattern = pattern;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public boolean isEmpty() {
        return this.shape == null;
    }

    @Nullable
    public MooncakeShape shape() {
        return this.shape;
    }

    @Nullable
    public MooncakePattern pattern() {
        return this.pattern;
    }

    /** 模型 / 贴图文件名后缀，例如 {@code round_square}。 */
    public String modelSuffix() {
        return this.name;
    }

    /** 语言键：{@code mooncake_overflow.mooncake.kind.<name>} */
    public String nameKey() {
        return "mooncake_overflow.mooncake.kind." + this.name;
    }

    public static MooncakeKind of(MooncakeShape shape, MooncakePattern pattern) {
        for (MooncakeKind kind : values()) {
            if (kind.shape == shape && kind.pattern == pattern) {
                return kind;
            }
        }
        return DEFAULT;
    }
}
