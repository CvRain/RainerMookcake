package org.cvrain.mooncakeoverflow.item;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.cvrain.mooncakeoverflow.block.MooncakePileBlockEntity;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeData;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeFilling;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeOxidation;
import org.cvrain.mooncakeoverflow.registry.ModItems;
import org.cvrain.mooncakeoverflow.registry.ModRecipes;

import java.util.ArrayList;
import java.util.List;

/**
 * 把四片月饼拼成一整块「五仁月饼」—— 四个角各来自一块不同的月饼。
 *
 * <p>四片的**形态和氧化度可以完全不一样**，四格会原样记住
 * （{@code mooncake_filling} 组件），名字变成一串很长的东西，
 * 比如「缝合月饼（左上·圆月饼 + 右上·氧化的方铜月饼·花形纹 + …）」——
 * 这正是这个模组的主题：**名称溢出**。
 *
 * <p>哪一片落在哪一格由**在工作台里的摆放顺序**决定（按行优先读，
 * 左上 → 右上 → 左下 → 右下）。
 *
 * <p>为什么必须自己写配方：原版配方的产物是固定的模板，
 * **读不到原料的组件**（`Ingredient` 只是个 `HolderSet<Item>`），
 * 只有 `CustomRecipe` 能在 {@code assemble} 里自己拼数据。
 *
 * <p>这是个"特殊配方"（{@code isSpecial()} 为 true），所以不会出现在配方书里，
 * 也不需要任何 JSON 参数 —— 序列化器用 {@code MapCodec.unit} 就够了。
 */
public class CompositeMooncakeRecipe extends CustomRecipe {
    public static final CompositeMooncakeRecipe INSTANCE = new CompositeMooncakeRecipe();

    public static final MapCodec<CompositeMooncakeRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, CompositeMooncakeRecipe> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<CompositeMooncakeRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public CompositeMooncakeRecipe() {
    }

    /** 正好四片月饼就行。 */
    @Override
    public boolean matches(CraftingInput input, Level level) {
        List<ItemStack> quarters = quartersIn(input);
        return quarters != null && MooncakeQuarterItem.isFourQuarters(quarters);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        List<ItemStack> quarters = quartersIn(input);
        if (quarters == null || !MooncakeQuarterItem.isFourQuarters(quarters)) {
            return ItemStack.EMPTY;
        }

        // 工作台里按行优先读出来的顺序 = 左上 → 右上 → 左下 → 右下
        List<MooncakePileBlockEntity.Piece> pieces = new ArrayList<>(4);
        for (ItemStack quarter : quarters) {
            pieces.add(MooncakeQuarterItem.pieceOf(quarter));
        }

        // 产物是**一个月饼**（和普通月饼同一个物品），不是"装着四块的月饼堆"。
        // 四个角只记在 mooncake_filling 组件里，用来拼名字和换物品模型。
        //
        // 四片属性不一样时，取最"保守"的那一份，免得能靠拼合洗掉氧化度：
        //   是不是铜的 —— 有一片是铜的，成品就是铜的
        //   氧化度     —— 取最锈的那一片（枚举顺序就是 铜→斑驳→锈蚀→氧化）
        //   涂蜡       —— 四片都涂过才算涂过
        //   形态       —— 取第一片的
        boolean copper = pieces.stream().anyMatch(MooncakePileBlockEntity.Piece::copper);
        MooncakeOxidation oxidation = pieces.stream()
                .filter(MooncakePileBlockEntity.Piece::copper)
                .map(MooncakePileBlockEntity.Piece::oxidation)
                .max(java.util.Comparator.comparingInt(Enum::ordinal))
                .orElse(MooncakeOxidation.DEFAULT);
        boolean waxed = pieces.stream().allMatch(MooncakePileBlockEntity.Piece::waxed);

        ItemStack result = copper
                ? MooncakeData.copper(pieces.getFirst().kind(), oxidation, waxed)
                : MooncakeData.plain(pieces.getFirst().kind());

        // 名字用**原版的自定义名**（就是铁砧改名用的那个组件）。
        //
        // 为什么不自己定义一个组件：自定义组件在"服务端设了、客户端收不到"这件事上
        // 踩了两次坑（List<Piece> 一次、纯字符串一次），而 custom_name 是原版到处在用、
        // 一定有同步的组件。这里存的还是一个可翻译组件树，所以照样跟着语言走。
        //
        // 副作用：自定义名在提示框里是斜体（原版就这么渲染的）。
        result.set(DataComponents.CUSTOM_NAME, MooncakeFilling.describe(pieces));
        return result;
    }

    @Override
    public RecipeSerializer<CompositeMooncakeRecipe> getSerializer() {
        return ModRecipes.COMPOSITE_MOONCAKE.get();
    }

    /** 输入栏里那四块；不是"恰好四块四分之一月饼"就返回 null。 */
    private static List<ItemStack> quartersIn(CraftingInput input) {
        List<ItemStack> quarters = new ArrayList<>(4);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (!(stack.getItem() instanceof MooncakeQuarterItem)) {
                return null;
            }
            quarters.add(stack);
        }
        return quarters.size() == 4 ? quarters : null;
    }
}
