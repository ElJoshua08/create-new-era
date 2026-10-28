package com.createnewera.registry;

import java.util.function.Supplier;

import com.createnewera.CreateNewEra;
import com.createnewera.cable.SpoolDyeRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, CreateNewEra.MOD_ID);

    public static final Supplier<RecipeSerializer<SpoolDyeRecipe>> SPOOL_DYEING =
            SERIALIZERS.register("spool_dyeing", () -> new SimpleCraftingRecipeSerializer<>(SpoolDyeRecipe::new));

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }

    private ModRecipes() {}
}
