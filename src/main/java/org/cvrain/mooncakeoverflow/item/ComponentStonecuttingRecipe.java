package org.cvrain.mooncakeoverflow.item;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import org.cvrain.mooncakeoverflow.registry.ModRecipes;

/**
 * 会**保留输入物品组件**的切石机配方。
 *
 * <p>为什么需要它：原版的 {@code StonecutterRecipe}（其实是 {@code SingleItemRecipe}）
 * 的 {@code assemble} 就是 {@code result.create()} —— **完全不看输入那一摞东西**。
 * 拿它来切月饼的后果是"锈蚀的铜月饼一切，氧化度没了"：
 * 静默吞数据，还能被当成"洗氧化度"的漏洞。
 *
 * <p>所以继承 {@code StonecutterRecipe} 只改 {@code assemble}，
 * 用 {@link TransmuteRecipe#createWithOriginalComponents} 把输入的组件补丁搬到产物上
 * （先拿输入的补丁建栈，再盖模板自己的组件）。
 *
 * <p>关键在于 {@code getType()} 仍然继承自 {@code StonecutterRecipe}，
 * 也就是 {@code RecipeType.STONECUTTING} —— 所以**它照样出现在切石机界面里**
 * （{@code StonecutterMenu} 是按 {@code RecipeType.STONECUTTING} 收集配方的，
 * 而拿产物走的是 {@code assemble}，我们的覆写会生效）。
 *
 * <p>JSON 里写 {@code "type": "mooncake_overflow:component_stonecutting"}。
 */
public class ComponentStonecuttingRecipe extends StonecutterRecipe {
    // 注意这里类型写的是父类 StonecutterRecipe 而不是本类：
    // StonecutterRecipe#getSerializer() 把返回类型收窄成了 RecipeSerializer<StonecutterRecipe>，
    // 子类没法返回 RecipeSerializer<ComponentStonecuttingRecipe>（泛型不变）。
    // 反正 codec 的工厂方法引用返回的是本类实例，运行时 assemble 照样走我们的覆写。
    public static final MapCodec<StonecutterRecipe> MAP_CODEC =
            simpleMapCodec(ComponentStonecuttingRecipe::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, StonecutterRecipe> STREAM_CODEC =
            simpleStreamCodec(ComponentStonecuttingRecipe::new);

    public static final RecipeSerializer<StonecutterRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public ComponentStonecuttingRecipe(Recipe.CommonInfo commonInfo, Ingredient ingredient,
                                       ItemStackTemplate result) {
        super(commonInfo, ingredient, result);
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return TransmuteRecipe.createWithOriginalComponents(result(), input.getItem(0));
    }

    @Override
    public RecipeSerializer<StonecutterRecipe> getSerializer() {
        return ModRecipes.COMPONENT_STONECUTTING.get();
    }
}
