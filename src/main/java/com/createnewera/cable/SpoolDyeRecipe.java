package com.createnewera.cable;

import com.createnewera.registry.ModComponents;
import com.createnewera.registry.ModRecipes;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * A spool in the middle of the grid with the same dye above, below and to each side: the spool
 * comes out that colour, with all the cable it had.
 */
public class SpoolDyeRecipe extends CustomRecipe {
    private static final int[] SIDES = {1, 3, 5, 7};
    private static final int[] CORNERS = {0, 2, 6, 8};

    public SpoolDyeRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return input.width() == 3 && input.height() == 3 && dye(input) != null;
    }

    private static DyeColor dye(CraftingInput input) {
        if (!(input.getItem(4).getItem() instanceof CableSpoolItem))
            return null;
        for (int i : CORNERS)
            if (!input.getItem(i).isEmpty())
                return null;
        DyeColor color = null;
        for (int i : SIDES) {
            if (!(input.getItem(i).getItem() instanceof DyeItem dye) || (color != null && dye.getDyeColor() != color))
                return null;
            color = dye.getDyeColor();
        }
        return color == CableSpoolItem.color(input.getItem(4)) ? null : color;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack spool = input.getItem(4).copyWithCount(1);
        spool.set(ModComponents.CABLE_COLOR.get(), dye(input));
        spool.remove(ModComponents.CABLE_START.get());
        return spool;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.SPOOL_DYEING.get();
    }
}
