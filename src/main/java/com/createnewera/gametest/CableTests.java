package com.createnewera.gametest;

import java.util.List;

import com.createnewera.CreateNewEra;
import com.createnewera.bus.Bus;
import com.createnewera.bus.BusData;
import com.createnewera.bus.BusPort;
import com.createnewera.bus.BusSpan;
import com.createnewera.bus.CableRoute;
import com.createnewera.bus.PartGeometry;
import com.createnewera.bus.PortMode;
import com.createnewera.cable.CableSpoolItem;
import com.createnewera.computer.BrassComputerBlock;
import com.createnewera.cable.SpoolDyeRecipe;
import com.createnewera.registry.ModComponents;
import com.createnewera.registry.ModBlocks;
import com.createnewera.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Cables: colours keeping networks apart, routes along faces, and the spool that lays them. */
@GameTestHolder(CreateNewEra.MOD_ID)
@PrefixGameTestTemplate(false)
public class CableTests {
    private static final String ROOM = "empty5";

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 5; x++)
            for (int z = 0; z < 5; z++)
                helper.setBlock(x, 0, z, Blocks.STONE);
    }

    private static BusPort part(GameTestHelper helper, BlockPos pos, BusPort.Kind kind) {
        helper.setBlock(pos, Blocks.STONE);
        BusPort part = Bus.placePart(helper.getLevel(), helper.absolutePos(pos), Direction.UP, 6, 6, kind, PortMode.OUT);
        if (part == null)
            helper.fail("couldn't place a " + kind + " on " + pos);
        return part;
    }

    private static BusSpan lay(GameTestHelper helper, BusPort a, BusPort b, DyeColor color) {
        return BusData.get(helper.getLevel()).connect(a.id(), b.id(), color, true,
                List.of(PartGeometry.anchor(a), PartGeometry.anchor(b)));
    }

    // a dyed cable fixes its parts' colour; bare copper still joins anything
    @GameTest(template = ROOM)
    public static void coloursKeepNetworksApart(GameTestHelper helper) {
        BusData data = BusData.get(helper.getLevel());
        BusPort red = part(helper, new BlockPos(0, 1, 0), BusPort.Kind.PORT);
        BusPort blue = part(helper, new BlockPos(4, 1, 0), BusPort.Kind.PORT);
        BusPort splice = part(helper, new BlockPos(2, 1, 2), BusPort.Kind.SPLICE);
        BusPort bare = part(helper, new BlockPos(2, 1, 4), BusPort.Kind.PORT);
        if (lay(helper, red, splice, DyeColor.RED) == null)
            helper.fail("a red cable into an empty splice was refused");
        if (lay(helper, blue, splice, DyeColor.BLUE) != null)
            helper.fail("a blue cable joined a red splice");
        if (data.network(List.of(red.id())).contains(blue.id()))
            helper.fail("red reaches blue");
        if (lay(helper, bare, splice, null) == null)
            helper.fail("bare copper didn't join the red splice");
        if (!data.network(List.of(red.id())).contains(bare.id()))
            helper.fail("red doesn't reach the bare cable's port");
        helper.succeed();
    }

    // dyeing a cable is held to the same rule as laying one
    @GameTest(template = ROOM)
    public static void dyeingFollowsTheColourRule(GameTestHelper helper) {
        BusData data = BusData.get(helper.getLevel());
        BusPort a = part(helper, new BlockPos(0, 1, 0), BusPort.Kind.PORT);
        BusPort splice = part(helper, new BlockPos(2, 1, 2), BusPort.Kind.SPLICE);
        BusPort b = part(helper, new BlockPos(4, 1, 4), BusPort.Kind.PORT);
        lay(helper, a, splice, DyeColor.RED);
        BusSpan second = lay(helper, splice, b, null);
        if (data.dye(second.id(), DyeColor.BLUE))
            helper.fail("dyed a cable blue into a red splice");
        if (!data.dye(second.id(), DyeColor.RED))
            helper.fail("couldn't dye it red to match");
        helper.succeed();
    }

    // along the faces: off the top of one block, across the floor, up the next
    @GameTest(template = ROOM)
    public static void surfaceRoutesFollowFaces(GameTestHelper helper) {
        floor(helper);
        BusPort a = part(helper, new BlockPos(0, 1, 0), BusPort.Kind.PORT);
        BusPort b = part(helper, new BlockPos(4, 1, 4), BusPort.Kind.PORT);
        List<Vec3> route = CableRoute.surface(helper.getLevel(), a, b);
        if (route == null)
            helper.fail("no route over the floor");
        int metres = CableRoute.metres(route, false);
        // down a side, across the floor on the diagonal, up the other: about 2 + 4.2 + 0.6
        if (metres < 6 || metres > 9)
            helper.fail("route is " + metres + " m, expected about 7");
        // every point lies in open space, on a face
        for (Vec3 p : route)
            if (!helper.getLevel().getBlockState(BlockPos.containing(p)).isAir())
                helper.fail("route goes through a block at " + p);

        // a block floating on its own: its faces don't reach the floor
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE);
        BusPort floating = Bus.placePort(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 2)), Direction.UP, 6, 6, PortMode.OUT);
        if (CableRoute.surface(helper.getLevel(), a, floating) != null)
            helper.fail("found a surface route to a floating block");
        helper.succeed();
    }

    // across one flat floor the cable is a straight line, not a detour through block centres
    @GameTest(template = ROOM)
    public static void surfaceRoutesRunStraightAcrossAFace(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        BusPort a = Bus.placePort(level, helper.absolutePos(new BlockPos(0, 0, 1)), Direction.UP, 3, 9, PortMode.OUT);
        BusPort b = Bus.placePort(level, helper.absolutePos(new BlockPos(4, 0, 3)), Direction.UP, 9, 2, PortMode.OUT);
        List<Vec3> route = CableRoute.surface(level, a, b);
        if (route == null || route.size() != 2)
            helper.fail("expected a straight two-point run, got " + route);
        // with a block in the way, it goes round it, still on the floor
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        List<Vec3> round = CableRoute.surface(level, a, b);
        if (round == null || round.size() < 3)
            helper.fail("expected a route round the block, got " + round);
        for (int i = 1; i < round.size(); i++)
            for (int k = 0; k <= 20; k++) {
                BlockPos at = BlockPos.containing(round.get(i - 1).lerp(round.get(i), k / 20.0));
                if (!level.getBlockState(at).isAir())
                    helper.fail("the route cuts through " + at);
            }
        helper.succeed();
    }

    private static void click(GameTestHelper helper, Player player, ItemStack spool, BusPort part) {
        player.setItemInHand(InteractionHand.MAIN_HAND, spool);
        Vec3 at = PartGeometry.anchor(part);
        spool.onItemUseFirst(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(at, part.face(), part.pos(), false)));
    }

    // two clicks lay the cable, and it comes off the spool by the metre
    @GameTest(template = ROOM)
    public static void spoolSpendsWhatItLays(GameTestHelper helper) {
        BusPort a = part(helper, new BlockPos(0, 1, 0), BusPort.Kind.PORT);
        BusPort b = part(helper, new BlockPos(4, 1, 4), BusPort.Kind.PORT);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack spool = ModItems.CABLE_SPOOL.asStack();
        click(helper, player, spool, a);
        if (!spool.has(ModComponents.CABLE_START.get()))
            helper.fail("the first click didn't start a cable");
        click(helper, player, spool, b);
        List<BusSpan> spans = BusData.get(helper.getLevel()).spansAt(a.id());
        if (spans.size() != 1)
            helper.fail("expected one cable, got " + spans);
        int used = spans.get(0).length();
        if (CableSpoolItem.metresLeft(spool) != CableSpoolItem.METRES - used || used < 6)
            helper.fail("spool has " + CableSpoolItem.metresLeft(spool) + " m left after laying " + used);
        if (spool.has(ModComponents.CABLE_START.get()))
            helper.fail("the spool still holds a cable start");
        helper.succeed();
    }

    // the ports a computer comes with, in its side sockets, take cables like any other
    @GameTest(template = ROOM)
    public static void cablesReachTheComputersSockets(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at, ModBlocks.BRASS_COMPUTER.getDefaultState().setValue(BrassComputerBlock.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos pc = helper.absolutePos(at);
        // it comes with its own ports, already in its sockets
        BusPort own = BusData.get(helper.getLevel()).portsAt(pc).stream().findFirst().orElse(null);
        if (own == null)
            helper.fail("the computer came without ports");
        BusPort chest = part(helper, new BlockPos(0, 1, 4), BusPort.Kind.PORT);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        ItemStack spool = ModItems.CABLE_SPOOL.asStack();
        click(helper, player, spool, own);
        if (!spool.has(ModComponents.CABLE_START.get()))
            helper.fail("clicking the socket port didn't start a cable");
        click(helper, player, spool, chest);
        if (BusData.get(helper.getLevel()).spansAt(own.id()).size() != 1)
            helper.fail("no cable from the socket port");
        helper.succeed();
    }

    // dye on all four sides, the spool keeps its cable
    @GameTest(template = ROOM)
    public static void dyeingASpoolKeepsItsCable(GameTestHelper helper) {
        ItemStack spool = ModItems.CABLE_SPOOL.asStack();
        spool.setDamageValue(20);
        ItemStack dye = new ItemStack(Items.LIME_DYE);
        CraftingInput grid = CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, dye, ItemStack.EMPTY, dye, spool, dye,
                ItemStack.EMPTY, dye, ItemStack.EMPTY));
        SpoolDyeRecipe recipe = new SpoolDyeRecipe(CraftingBookCategory.MISC);
        if (!recipe.matches(grid, helper.getLevel()))
            helper.fail("the dye recipe didn't match");
        ItemStack out = recipe.assemble(grid, helper.getLevel().registryAccess());
        if (CableSpoolItem.color(out) != DyeColor.LIME || out.getDamageValue() != 20)
            helper.fail("got " + CableSpoolItem.color(out) + " with damage " + out.getDamageValue());
        CraftingInput three = CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, dye, ItemStack.EMPTY, dye, spool, dye,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
        if (recipe.matches(three, helper.getLevel()))
            helper.fail("matched with only three dyes");
        helper.succeed();
    }
}
