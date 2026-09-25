package org.cvrain.mooncakeoverflow.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.cvrain.mooncakeoverflow.item.MooncakeBlockItem;
import org.cvrain.mooncakeoverflow.item.MooncakeQuarterItem;
import org.cvrain.mooncakeoverflow.registry.ModItems;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeData;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeKind;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeOxidation;
import org.cvrain.mooncakeoverflow.mooncake.PileCell;
import org.cvrain.mooncakeoverflow.registry.ModBlockEntities;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 月饼堆的内容 —— 四格各放了一块什么月饼。
 *
 * <p><b>为什么这次用了方块实体</b>：每格要独立记「形态 × 是不是铜月饼 × 氧化度 × 涂蜡」。
 * 方块状态是四个格子相乘的，每多一条轴就是四次方：
 * 7⁴（形态）再乘上 4⁴（氧化度）约 39 万，涂蜡再乘 16 就是 980 万 —— 必炸。
 * 反过来，把内容放进方块实体之后，方块状态只剩下「给渲染器查模型用的表」（280 个），
 * 比之前两个方块合计的 21609 个还少两个数量级。
 *
 * <p>顺带一个大好处：**普通月饼和铜月饼可以放进同一堆**了 ——
 * 以前它们必须是两个方块，因为方块状态没法表达"这格是铜的那格不是"。
 */
public class MooncakePileBlockEntity extends BlockEntity {
    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();

    public static final int SLOTS = PileCell.SLOTS.length;

    /**
     * 一格的内容。
     *
     * @param kind      形态，{@link MooncakeKind#NONE} 表示这格空着
     * @param copper    是不是铜月饼（只有铜月饼会氧化）
     * @param oxidation 氧化度，{@code copper} 为 false 时没有意义
     * @param waxed     涂没涂蜡
     * @param name      **自定义名**（目前只有五仁月饼有）。放在地上再拿起来要原样还回去，
     *                  否则"摆下去再挖出来"就是掉属性 —— 名字正是五仁月饼的全部特征。
     *                  <p>类型是 {@code Optional} 而不是可空的 {@code Component}：
     *                  DFU 的 {@code RecordCodecBuilder} 解码每个字段时会做
     *                  {@code Optional.of(值)}，**字段的 codec 一旦解码出 null 就 NPE**，
     *                  而且是在处理同步包时炸 —— 客户端直接崩。
     * @param partial   **这一格是不是某个月饼的一角**（切开的片、五仁月饼的四格都是）。
     *                  它决定渲染时画"整个纹样"还是"只画它那一象限的纹样"——
     *                  四个 partial 拼起来才是一个完整的月饼，而不是四个小月饼摆成 2×2。
     *                  掉落时也靠它区分：partial 掉"片"，否则掉"整块"
     */
    public record Piece(MooncakeKind kind, boolean copper, MooncakeOxidation oxidation, boolean waxed,
                        Optional<Component> name, boolean partial) {
        public static final Piece EMPTY = new Piece(
                MooncakeKind.NONE, false, MooncakeOxidation.DEFAULT, false, Optional.empty(), false);

        public static final Codec<Piece> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                MooncakeKind.CODEC.fieldOf("kind").forGetter(Piece::kind),
                Codec.BOOL.optionalFieldOf("copper", false).forGetter(Piece::copper),
                MooncakeOxidation.CODEC.optionalFieldOf("oxidation", MooncakeOxidation.DEFAULT)
                        .forGetter(Piece::oxidation),
                Codec.BOOL.optionalFieldOf("waxed", false).forGetter(Piece::waxed),
                ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(Piece::name),
                Codec.BOOL.optionalFieldOf("partial", false).forGetter(Piece::partial)
        ).apply(instance, Piece::new));

        public boolean isEmpty() {
            return kind.isEmpty();
        }

        /**
         * 从一整块月饼读出这一格内容。
         *
         * <p>整块月饼摆下去是**完整的一块**（{@code partial = false}），
         * 跟切开的片不一样 —— 四块整月饼摆一起是"四块月饼"，
         * 四个片摆一起才是"一个月饼"。
         */
        public static Piece of(ItemStack stack) {
            return new Piece(
                    MooncakeData.kindOf(stack),
                    MooncakeBlockItem.isCopper(stack),
                    MooncakeData.oxidationOf(stack),
                    MooncakeData.isWaxed(stack),
                    Optional.ofNullable(stack.get(DataComponents.CUSTOM_NAME)),
                    false);
        }

        /**
         * 还原成物品。
         *
         * <p>{@code partial} 的那一格还原成**一片**（四分之一块），
         * 否则还原成**一整块**。这条很重要：不然"1 块月饼 → 切 4 片 → 摆进堆里 → 挖掉"
         * 会掉出 4 块整月饼，凭空翻四倍。
         *
         * @param cell 这一格在哪 —— 片的名字里要带"左上/右上/左下/右下"
         */
        public ItemStack toStack(PileCell cell) {
            if (partial) {
                ItemStack slice = new ItemStack(ModItems.MOONCAKE_QUARTER.get());
                MooncakeData.setKind(slice, kind);
                MooncakeQuarterItem.setCopper(slice, copper);
                MooncakeData.setOxidation(slice, oxidation);
                MooncakeData.setWaxed(slice, waxed);
                MooncakeQuarterItem.setCorner(slice, cell);
                return slice;
            }
            ItemStack whole = copper
                    ? MooncakeData.copper(kind, oxidation, waxed)
                    : MooncakeData.plain(kind);
            // 五仁月饼的全部特征就是这个自定义名，摆下去再挖出来必须原样还回去
            name.ifPresent(custom -> whole.set(DataComponents.CUSTOM_NAME, custom));
            return whole;
        }
    }

    private final Piece[] pieces = new Piece[SLOTS];

    public MooncakePileBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOONCAKE_PILE.get(), pos, state);
        Arrays.fill(pieces, Piece.EMPTY);
    }

    // ------------------------------------------------------------------ 读

    public Piece piece(int index) {
        return pieces[index];
    }

    public boolean isEmpty() {
        for (Piece piece : pieces) {
            if (!piece.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int count() {
        int n = 0;
        for (Piece piece : pieces) {
            if (!piece.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** 最上面那一块（最后放进去的）的下标；空堆返回 -1。 */
    public int topIndex() {
        for (int i = SLOTS - 1; i >= 0; i--) {
            if (!pieces[i].isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /** 还有空格子吗。 */
    public boolean hasRoom() {
        for (Piece piece : pieces) {
            if (piece.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 所有非空格子，按填充顺序。 */
    public List<Piece> contents() {
        List<Piece> list = new ArrayList<>(SLOTS);
        for (Piece piece : pieces) {
            if (!piece.isEmpty()) {
                list.add(piece);
            }
        }
        return list;
    }

    /** 挖掉这一堆应该掉什么（每格还原成整块或片）。 */
    public List<ItemStack> drops() {
        List<ItemStack> drops = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            if (!pieces[i].isEmpty()) {
                drops.add(pieces[i].toStack(PileCell.byIndex(i)));
            }
        }
        return drops;
    }

    // ------------------------------------------------------------------ 写

    /** 塞一块进去；满了返回 false。 */
    public boolean add(Piece piece) {
        for (int i = 0; i < SLOTS; i++) {
            if (pieces[i].isEmpty()) {
                pieces[i] = piece;
                sync();
                return true;
            }
        }
        return false;
    }

    /**
     * 拿走指定格子里的那一块；那格是空的就返回 null。
     *
     * <p>不叫 {@code removeTop} 了：现在玩家瞄哪格拿哪格，不再固定拿最上面那块。
     */
    @Nullable
    public Piece removeAt(int index) {
        if (index < 0 || index >= SLOTS || pieces[index].isEmpty()) {
            return null;
        }
        Piece piece = pieces[index];
        pieces[index] = Piece.EMPTY;
        sync();
        return piece;
    }

    /**
     * 涂蜡 / 刮蜡 / 刮氧化，一次作用在**整堆**上。
     *
     * @return 有没有真的改动什么
     */
    public boolean waxAll(boolean waxed) {
        boolean changed = false;
        for (int i = 0; i < SLOTS; i++) {
            Piece piece = pieces[i];
            if (!piece.copper() || piece.waxed() == waxed) {
                continue;
            }
            pieces[i] = new Piece(piece.kind(), true, piece.oxidation(), waxed, piece.name(), piece.partial());
            changed = true;
        }
        if (changed) {
            sync();
        }
        return changed;
    }

    /** 整堆里有没有涂过蜡的铜月饼。 */
    public boolean anyWaxed() {
        for (Piece piece : pieces) {
            if (piece.copper() && piece.waxed()) {
                return true;
            }
        }
        return false;
    }

    /** 整堆里有没有还能氧化的铜月饼。 */
    public boolean anyOxidizable() {
        for (Piece piece : pieces) {
            if (piece.copper() && !piece.waxed() && !piece.oxidation().isFullyOxidized()) {
                return true;
            }
        }
        return false;
    }

    /** 把每块**能氧化**的铜月饼都推进一档。 */
    public void oxidizeAll() {
        boolean changed = false;
        for (int i = 0; i < SLOTS; i++) {
            Piece piece = pieces[i];
            if (!piece.copper() || piece.waxed() || piece.oxidation().isFullyOxidized()) {
                continue;
            }
            pieces[i] = new Piece(piece.kind(), true, piece.oxidation().next(), false, piece.name(), piece.partial());
            changed = true;
        }
        if (changed) {
            sync();
        }
    }

    /** 把每块铜月饼的氧化度往回退一档，砍过的返回 true。 */
    public boolean scrapeAll() {
        boolean changed = false;
        for (int i = 0; i < SLOTS; i++) {
            Piece piece = pieces[i];
            if (!piece.copper() || piece.oxidation() == MooncakeOxidation.DEFAULT) {
                continue;
            }
            pieces[i] = new Piece(piece.kind(), true, piece.oxidation().previous(), piece.waxed(), piece.name(), piece.partial());
            changed = true;
        }
        if (changed) {
            sync();
        }
        return changed;
    }

    /** 一整堆清空（方块被拆掉时用）。 */
    public void clear() {
        Arrays.fill(pieces, Piece.EMPTY);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ------------------------------------------------------------------ 存档 / 同步

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("pieces", Piece.CODEC.listOf(), Arrays.asList(pieces));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        List<Piece> loaded;
        try {
            loaded = input.read("pieces", Piece.CODEC.listOf()).orElse(List.of());
        } catch (RuntimeException e) {
            // 坏数据不能把游戏带走。区分一下两条路径：
            //   服务端从区块读 —— 原版会 catch 并记一条 ERROR
            //   客户端收到同步包 —— 原版**不 catch**，直接"Failed to handle packet"然后崩
            // 所以这里自己兜住，最差就是这一堆月饼变成空的。
            LOGGER.error("月饼堆的内容读不出来，当作空的处理", e);
            loaded = List.of();
        }

        for (int i = 0; i < SLOTS; i++) {
            pieces[i] = i < loaded.size() ? loaded.get(i) : Piece.EMPTY;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
