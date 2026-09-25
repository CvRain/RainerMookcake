package org.cvrain.mooncakeoverflow.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.cvrain.mooncakeoverflow.Config;
import org.cvrain.mooncakeoverflow.mooncake.CellShape;
import org.cvrain.mooncakeoverflow.mooncake.PileCell;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 摆在地上的月饼堆 —— 一个方块，2×2 四格，**每格可以放任意一块月饼**。
 *
 * <p>这里有一条 26.1 的硬约束，整个类的结构都是被它逼出来的：
 * {@code BlockStateBase} 会给**每个方块状态**缓存一份碰撞箱 / 遮挡箱
 * （内部 {@code Cache}，用 {@code EmptyBlockGetter} + {@code BlockPos.ZERO} 算一次）。
 * 也就是说 **{@code getCollisionShape} 不能依赖坐标或方块实体** ——
 * 想按格区分碰撞箱，几何就必须能从方块状态本身推出来。
 *
 * <p>所以这儿的属性只有四个 {@link CellShape}（空 / 圆 / 方），3⁴ = 81 种状态，
 * 只负责**几何**：碰撞箱、准星轮廓、踩上去的高度。
 * 每格真正是什么月饼（纹样 / 是不是铜的 / 氧化到哪一步 / 涂没涂蜡）住在
 * {@link MooncakePileBlockEntity} 里，那些只影响外观和掉落。
 *
 * <p>方块自己不画东西：{@code getRenderShape()} 返回 {@link RenderShape#INVISIBLE}，
 * 四块月饼由 {@code MooncakePileRenderer} 按方块实体的内容逐格画。
 * 渲染需要「按形态 + 氧化度取一个模型」，而 26.1 只能通过方块状态查模型，
 * 那张模型表放在一个从不出现的方块 {@link MooncakePieceBlock} 上。
 */
public class MooncakePileBlock extends Block implements EntityBlock {
    /** 左上那一格的几何形状。 */
    public static final EnumProperty<CellShape> NW = EnumProperty.create("nw", CellShape.class);
    public static final EnumProperty<CellShape> NE = EnumProperty.create("ne", CellShape.class);
    public static final EnumProperty<CellShape> SW = EnumProperty.create("sw", CellShape.class);
    public static final EnumProperty<CellShape> SE = EnumProperty.create("se", CellShape.class);

    /** 四格属性，下标和 {@link PileCell#index()} 对齐。 */
    public static final List<EnumProperty<CellShape>> SLOTS = List.of(NW, NE, SW, SE);

    /** 和铜块一样的随机刻氧化概率。 */
    private static final float OXIDATION_CHANCE = 0.05688889F;

    public MooncakePileBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(NW, CellShape.NONE)
                .setValue(NE, CellShape.NONE)
                .setValue(SW, CellShape.NONE)
                .setValue(SE, CellShape.NONE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(MooncakePileBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NW, NE, SW, SE);
    }

    /** 自己什么都不画，画月饼的是渲染器。 */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    // ------------------------------------------------------------------ 几何

    /** 把四格的几何写进方块状态（加 / 取月饼时调用）。 */
    public static BlockState withShapes(BlockState state, MooncakePileBlockEntity pile) {
        BlockState result = state;
        for (int i = 0; i < SLOTS.size(); i++) {
            result = result.setValue(SLOTS.get(i), CellShape.of(pile.piece(i).kind()));
        }
        return result;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = Shapes.empty();
        for (int i = 0; i < SLOTS.size(); i++) {
            shape = Shapes.or(shape, state.getValue(SLOTS.get(i)).shapeAt(i));
        }
        return shape;
    }

    /**
     * 遮挡箱必须是空的。
     *
     * <p>这个形状参与环境光遮蔽（AO）和光照，而它和碰撞箱一样是**按状态缓存**的。
     * 月饼只有 3/16 高，本来就不该在邻居方块上投下阴影。
     */
    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return Shapes.empty();
    }

    // ------------------------------------------------------------------ 方块实体

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MooncakePileBlockEntity(pos, state);
    }

    @Nullable
    public static MooncakePileBlockEntity pileAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MooncakePileBlockEntity pile ? pile : null;
    }

    /** 摆下去的时候把手里那块月饼放进第一格。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        addPiece(level, pos, MooncakePileBlockEntity.Piece.of(stack));
    }

    /** 往这一堆里加一块：方块实体和方块状态的几何一起更新。 */
    public static boolean addPiece(Level level, BlockPos pos, MooncakePileBlockEntity.Piece piece) {
        MooncakePileBlockEntity pile = pileAt(level, pos);
        if (pile == null || !pile.add(piece)) {
            return false;
        }
        syncShapes(level, pos, pile);
        return true;
    }

    private static void syncShapes(Level level, BlockPos pos, MooncakePileBlockEntity pile) {
        BlockState state = level.getBlockState(pos);
        BlockState updated = withShapes(state, pile);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------ 交互

    /**
     * 蜜脾涂蜡 / 斧头刮 / 空手让位。
     *
     * <p>涂蜡和刮除都是**整堆**一起作用，而且涂蜡**不换方块也不换外观**
     * （原版是换一个"涂蜡铜块"，我们只是把 {@code waxed} 记进方块实体）。
     *
     * <p>交互顺序对我们有利：原版先调**方块的** {@code useItemOn}，
     * 只有它没吃掉这次交互才轮到 {@code ItemStack#useOn}。
     * 所以返回 {@code SUCCESS} 就能把原版的 {@code HoneycombItem} / {@code AxeItem} 整个挡掉。
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                         Player player, InteractionHand hand, BlockHitResult hit) {
        MooncakePileBlockEntity pile = pileAt(level, pos);
        if (pile == null) {
            return InteractionResult.PASS;
        }

        // 蜜脾 → 整堆涂蜡
        if (stack.is(Items.HONEYCOMB) && pile.waxAll(true)) {
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, pos, SoundEvents.HONEYCOMB_WAX_ON, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return InteractionResult.SUCCESS;
        }

        // 斧头 → 有蜡先刮蜡，没蜡则氧化度往回退一档
        if (stack.is(ItemTags.AXES)) {
            if (pile.anyWaxed()) {
                pile.waxAll(false);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.playSound(null, pos, SoundEvents.AXE_WAX_OFF, SoundSource.BLOCKS, 1.0F, 1.0F);
                    stack.hurtAndBreak(1, player, hand);
                }
                return InteractionResult.SUCCESS;
            }
            if (pile.scrapeAll()) {
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.playSound(null, pos, SoundEvents.AXE_SCRAPE, SoundSource.BLOCKS, 1.0F, 1.0F);
                    stack.hurtAndBreak(1, player, hand);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        // 空手时把交互让给 useWithoutItem
        // （原版只在返回 TRY_WITH_EMPTY_HAND 时才会调它，返回 PASS 的话那段代码永远不执行）
        return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
    }

    /** Shift + 空手右键 → 拿走准星指着的那一块。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        return takeOne(level, pos, player, hit) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /**
     * 拿走玩家**正指着的那一格**，而不是固定拿"最上面那块"。
     *
     * <p>格子是靠命中点算的：把世界坐标转成方块内的相对坐标，
     * x 和 z 各以 0.5 为界，正好对应左上 / 右上 / 左下 / 右下。
     * 拿不到命中点（比如左键那条路）时退回"最上面那块"。
     *
     * @return 有没有拿到东西
     */
    public static boolean takeOne(Level level, BlockPos pos, Player player, @Nullable BlockHitResult hit) {
        MooncakePileBlockEntity pile = pileAt(level, pos);
        if (pile == null || pile.isEmpty()) {
            return false;
        }

        HitResult aimed = hit != null ? hit : player.pick(6.0, 1.0F, false);
        int index = aimed instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos)
                ? cellIndexAt(blockHit, pos)
                : pile.topIndex();

        if (level instanceof ServerLevel serverLevel) {
            MooncakePileBlockEntity.Piece piece = pile.removeAt(index);
            if (piece == null) {
                return false;
            }
            // 拿空了就把方块也拆掉。留着的话会变成一个看不见又拆不掉的占位方块
            // （形状是空的、方块又是 INVISIBLE，但那个位置已经不可放置 —— 幽灵方块）
            if (pile.isEmpty()) {
                level.removeBlock(pos, false);
            } else {
                syncShapes(level, pos, pile);
            }
            ItemStack stack = piece.toStack();
            if (!player.getInventory().add(stack)) {
                Block.popResource(serverLevel, pos, stack);
            }
            serverLevel.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.2F);
        }
        return true;
    }

    /** 命中点在方块的哪一格：0～3，顺序和 {@link PileCell#SLOTS} 一致（左上→右上→左下→右下）。 */
    public static int cellIndexAt(BlockHitResult hit, BlockPos pos) {
        var point = hit.getLocation();
        int x = point.x - pos.getX() < 0.5 ? 0 : 1;
        int z = point.z - pos.getZ() < 0.5 ? 0 : 1;
        return z * 2 + x;
    }

    // ------------------------------------------------------------------ 氧化

    /**
     * 随机刻氧化 —— 和铜块同一套节奏（平均几十分钟一档）。
     *
     * <p>整堆一起抽一次，抽中了每块能氧化的铜月饼各进一档。
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!Config.blockOxidationEnabled) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof MooncakePileBlockEntity pile
                && pile.anyOxidizable()
                && random.nextFloat() < OXIDATION_CHANCE) {
            pile.oxidizeAll();
        }
    }

    // ------------------------------------------------------------------ 掉落

    /**
     * 掉落不走战利品表，而是照着方块实体四格逐一还原。
     *
     * <p>方块实体是从 {@code LootContextParams.BLOCK_ENTITY} 拿的
     * （原版 {@code Block.getDrops} 会把它塞进参数里）。
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>(4);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof MooncakePileBlockEntity pile) {
            for (MooncakePileBlockEntity.Piece piece : pile.contents()) {
                drops.add(piece.toStack());
            }
        }
        return drops;
    }
}
