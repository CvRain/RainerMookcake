package org.cvrain.mooncakeoverflow;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.cvrain.mooncakeoverflow.block.MooncakePileBlock;
import org.cvrain.mooncakeoverflow.registry.ModBlockEntities;
import org.cvrain.mooncakeoverflow.registry.ModBlocks;
import org.cvrain.mooncakeoverflow.registry.ModCreativeTabs;
import org.cvrain.mooncakeoverflow.registry.ModDataComponents;
import org.cvrain.mooncakeoverflow.registry.ModItems;
import org.slf4j.Logger;

/**
 * Mooncake Overflow —— 347 Variants, None of Them Good.
 *
 * <p>阶段一：月饼本体的完整工艺链。
 * <pre>
 *   可可豆 + 装水的炼药锅 → 可可豆豆沙
 *   4 小麦 + 糖 + 鸡蛋     → 4× 月饼面团
 *   4 月饼面团 + 豆沙      → 带馅面饼（可放置成方块）
 *   放置后手持月饼模具右键 → 压印
 *   空手右键压印好的面团   → 生月饼
 *   熔炉烤生月饼          → 月饼（可食用）
 * </pre>
 */
@Mod(MooncakeOverflow.MODID)
public final class MooncakeOverflow {
    public static final String MODID = "mooncake_overflow";

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 答应过要道的歉。 */
    private static final String[] APOLOGY = {
            "对不起。",
            "我们本来打算加 347 个变体。",
            "但那样你的帧率会掉。所以我们决定慢慢来。",
            "这是一封道歉信。",
    };

    public MooncakeOverflow(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();

        FMLCommonSetupEvent.getBus(modBusGroup).addListener(this::commonSetup);

        // 方块必须先于物品注册：带馅面饼的 BlockItem 需要方块实例
        ModBlocks.BLOCKS.register(modBusGroup);
        ModItems.ITEMS.register(modBusGroup);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modBusGroup);
        ModDataComponents.DATA_COMPONENTS.register(modBusGroup);
        ModBlockEntities.BLOCK_ENTITIES.register(modBusGroup);

        // 炼药锅交互（原版的 CauldronInteraction.Dispatcher.put 是包私有的，所以走事件）
        PlayerInteractEvent.RightClickBlock.BUS.addListener(MooncakeOverflow::onRightClickBlock);

        // Shift + 左键 = 只拿走一块月饼，而不是把整堆挖掉
        PlayerInteractEvent.LeftClickBlock.BUS.addListener(MooncakeOverflow::onLeftClickBlock);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Mooncake Overflow is loading. 347 variants planned.");

        if (Config.logApology) {
            // 用 ERROR 是为了让它在日志里显眼一点，这是模组的灵魂
            for (String line : APOLOGY) {
                LOGGER.error(line);
            }
        }
    }

    /**
     * Shift + 左键点月饼堆 → 只拿走一块。
     *
     * <p>不按 Shift 时是原版行为：整堆挖掉，四块一起掉。
     *
     * <p>这里必须用 {@code LeftClickBlock} 而不是 {@code BlockEvent.BreakEvent}：
     * 后者是在「已经开始挖」之后才取消的，客户端已经预测过一次破坏，
     * 方块会先消失再被服务端同步回来 —— 取四块就是闪四次。
     * {@code setUseBlock(DENY)} 是**在挖之前**拦下来，客户端就不会预测，也就不会闪。
     */
    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(ModBlocks.MOONCAKE_BLOCK.get())) {
            return;
        }
        if (!MooncakePileBlock.takeOne(level, pos, player, null)) {
            return; // 空堆，按原版处理
        }
        event.setUseBlock(Result.DENY);
        event.setUseItem(Result.DENY);
    }

    /**
     * 可可豆 + 装水的炼药锅 → 可可豆豆沙。
     *
     * <p>这里刻意不取消事件：原版的 {@code CauldronInteractions.WATER} 里没有可可豆，
     * 所以原版对它本来就是空操作，我们只做副作用即可。
     */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (stack.getItem() != Items.COCOA_BEANS) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = serverLevel.getBlockState(pos);
        if (!state.is(Blocks.WATER_CAULDRON)) {
            return;
        }

        Player player = event.getEntity();

        // 消耗一颗可可豆（创造模式不消耗）
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        // 炼药锅水位下降一级，空了就变回空锅
        LayeredCauldronBlock.lowerFillLevel(state, serverLevel, pos);

        ItemStack paste = new ItemStack(ModItems.COCOA_BEAN_PASTE.get());
        if (!player.getInventory().add(paste)) {
            player.drop(paste, false);
        }

        serverLevel.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.6F, 1.4F);
        player.swing(event.getHand(), true);
    }
}
