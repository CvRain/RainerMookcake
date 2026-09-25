package org.cvrain.mooncakeoverflow.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.cvrain.mooncakeoverflow.MooncakeOverflow;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.cvrain.mooncakeoverflow.item.ComponentStonecuttingRecipe;
import org.cvrain.mooncakeoverflow.item.CompositeMooncakeRecipe;

/**
 * 配方序列化器注册表。
 *
 * <p>目前只有一个：会保留组件的切石机配方，见 {@link ComponentStonecuttingRecipe}。
 */
public final class ModRecipes {
    private ModRecipes() {
    }

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, MooncakeOverflow.MODID);

    public static final RegistryObject<RecipeSerializer<StonecutterRecipe>> COMPONENT_STONECUTTING =
            RECIPE_SERIALIZERS.register("component_stonecutting",
                    () -> ComponentStonecuttingRecipe.SERIALIZER);

    /** 四块拼回一整块（缝合月饼）。特殊配方，不需要 JSON。 */
    public static final RegistryObject<RecipeSerializer<CompositeMooncakeRecipe>> COMPOSITE_MOONCAKE =
            RECIPE_SERIALIZERS.register("composite_mooncake",
                    () -> CompositeMooncakeRecipe.SERIALIZER);
}
