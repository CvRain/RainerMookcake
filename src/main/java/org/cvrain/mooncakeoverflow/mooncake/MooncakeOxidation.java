package org.cvrain.mooncakeoverflow.mooncake;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import javax.annotation.Nullable;

/**
 * 月饼的氧化程度 —— 模组的正题，致敬铜块那个段子。
 *
 * <p>四个阶段和铜块一一对应：铜 → 斑驳 → 锈蚀 → 氧化。
 * <ul>
 *   <li>方块放在地上会像铜块一样**随机刻氧化**</li>
 *   <li>物品放在背包里也会**慢慢变质**（月饼真的会放坏）</li>
 *   <li>用蜜脾涂蜡可以永久锁死</li>
 * </ul>
 */
public enum MooncakeOxidation implements StringRepresentable {
    COPPER("copper"),
    TARNISHED("tarnished"),
    RUSTED("rusted"),
    OXIDIZED("oxidized");

    /** 刚出炉的状态，也是"没有组件"时的默认值。 */
    public static final MooncakeOxidation DEFAULT = COPPER;

    public static final Codec<MooncakeOxidation> CODEC =
            StringRepresentable.fromEnum(MooncakeOxidation::values);

    public static final StreamCodec<ByteBuf, MooncakeOxidation> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(id -> MooncakeOxidation.values()[id], MooncakeOxidation::ordinal);

    private final String name;

    MooncakeOxidation(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /** 下一阶段，已经到顶就返回自身。 */
    public MooncakeOxidation next() {
        MooncakeOxidation[] values = values();
        int i = this.ordinal() + 1;
        return i < values.length ? values[i] : this;
    }

    /** 上一阶段（斧头刮一下就往回退一格），已经到底就返回自身。 */
    public MooncakeOxidation previous() {
        MooncakeOxidation[] values = values();
        int i = this.ordinal() - 1;
        return i >= 0 ? values[i] : this;
    }

    public boolean isFullyOxidized() {
        return this == OXIDIZED;
    }

    /** 名字前缀，例如"斑驳的"；铜阶段没有前缀。 */
    @Nullable
    public String prefixKey() {
        return this == COPPER ? null : "mooncake_overflow.oxidation." + this.name;
    }

    /** 模型 / 贴图文件名后缀。 */
    public String modelSuffix() {
        return this.name;
    }

    /** 从 0～3 的索引安全取值。 */
    public static MooncakeOxidation byIndex(int index) {
        MooncakeOxidation[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }
}
