package com.createnewera.ponder;

import com.createnewera.block.BrassScreenBlock;
import com.createnewera.computer.BrassComputerBlock;
import com.simibubi.create.AllItems;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.phys.Vec3;

/** Positions match the structures written by tools/ponder/gen_ponder.py. */
public final class NewEraPonderScenes {
    public static void computer(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("brass_computer", "Programming with the Brass Computer");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos computer = util.grid().at(2, 1, 2);
        BlockPos lamp = util.grid().at(1, 1, 2);
        BlockPos lever = util.grid().at(3, 1, 2);
        Vec3 front = util.vector().blockSurface(computer, Direction.SOUTH);

        scene.world().showSection(util.select().position(computer), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("The Brass Computer runs programs written in Lua")
                .placeNearTarget()
                .pointAt(front);
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(computer), Pointing.DOWN, 50).rightClick();
        scene.idle(10);
        scene.overlay().showText(80)
                .text("Right-click it to open the editor. Write a program there, then press Run or Ctrl+R")
                .placeNearTarget()
                .pointAt(front);
        scene.idle(90);

        scene.world().showSection(util.select().position(lamp), Direction.EAST);
        scene.idle(15);
        scene.world().modifyBlock(computer, s -> s.setValue(BrassComputerBlock.RUNNING, true), false);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Programs send redstone out of any side: redstone.setOutput(\"left\", true)")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(lamp, Direction.UP));
        for (int i = 0; i < 4; i++) {
            scene.world().modifyBlock(lamp, s -> s.setValue(RedstoneLampBlock.LIT, true), false);
            scene.effects().indicateRedstone(lamp);
            scene.idle(12);
            scene.world().modifyBlock(lamp, s -> s.setValue(RedstoneLampBlock.LIT, false), false);
            scene.idle(12);
        }
        scene.idle(10);

        scene.world().showSection(util.select().position(lever), Direction.WEST);
        scene.idle(15);
        scene.overlay().showControls(util.vector().topOf(lever), Pointing.DOWN, 30).rightClick();
        scene.idle(7);
        scene.world().modifyBlock(lever, s -> s.setValue(LeverBlock.POWERED, true), false);
        scene.effects().indicateRedstone(lever);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("...and read what comes in: redstone.getInput(\"right\")")
                .placeNearTarget()
                .pointAt(util.vector().topOf(lever));
        scene.idle(100);

        scene.overlay().showText(90)
                .text("Sides are named from the computer's front: front, back, left, right, top and bottom")
                .placeNearTarget()
                .pointAt(front);
        scene.idle(100);

        scene.overlay().showText(80)
                .text("While a program runs the screen lights up, and it keeps running when nobody is watching")
                .placeNearTarget()
                .pointAt(front);
        scene.idle(90);
    }

    public static void screen(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("brass_screen", "Building displays from Brass Screens");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        // bottom row first, left to right as the camera sees it, joining up as each one lands
        int[][] order = {{3, 1}, {2, 1}, {1, 1}, {3, 2}, {2, 2}, {1, 2}};
        for (int i = 0; i < order.length; i++) {
            BlockPos pos = util.grid().at(order[i][0], order[i][1], 2);
            scene.world().showSection(util.select().position(pos), Direction.DOWN);
            scene.idle(6);
            joinScreens(scene, util, order, i + 1);
            scene.idle(4);
            if (i == 1)
                scene.overlay().showText(70)
                        .attachKeyFrame()
                        .text("Brass Screens facing the same way join into one display")
                        .placeNearTarget()
                        .pointAt(util.vector().blockSurface(pos, Direction.SOUTH));
        }
        scene.idle(60);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("The brass frame between them disappears, so any rectangle reads as a single pane")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(util.grid().at(2, 2, 2), Direction.SOUTH));
        scene.idle(100);

        scene.overlay().showText(100)
                .text("Every block holds 48 by 48 flip pixels that turn over when they change. Square plates or round discs: pick one in the client config")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(util.grid().at(1, 1, 2), Direction.SOUTH));
        scene.idle(110);

        scene.overlay().showText(70)
                .colored(PonderPalette.BLUE)
                .text("Coming soon: programs will draw on them")
                .independent(40);
        scene.idle(80);
    }

    /** Sets the joins of the first {@code shown} screens as if they'd been placed in that order. */
    private static void joinScreens(SceneBuilder scene, SceneBuildingUtil util, int[][] order, int shown) {
        for (int i = 0; i < shown; i++) {
            int x = order[i][0], y = order[i][1];
            // facing south, the viewer's left is west (-x)
            boolean left = isShown(order, shown, x - 1, y), right = isShown(order, shown, x + 1, y);
            boolean up = isShown(order, shown, x, y + 1), down = isShown(order, shown, x, y - 1);
            scene.world().modifyBlock(util.grid().at(x, y, 2), s -> s
                    .setValue(BrassScreenBlock.LEFT, left).setValue(BrassScreenBlock.RIGHT, right)
                    .setValue(BrassScreenBlock.UP, up).setValue(BrassScreenBlock.DOWN, down), false);
        }
    }

    private static boolean isShown(int[][] order, int shown, int x, int y) {
        for (int i = 0; i < shown; i++)
            if (order[i][0] == x && order[i][1] == y)
                return true;
        return false;
    }

    public static void adapter(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("universal_adapter", "Attaching Universal Adapters");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos barrel = util.grid().at(2, 1, 2);
        BlockPos side = util.grid().at(1, 1, 2);
        BlockPos top = util.grid().at(2, 2, 2);
        scene.world().showSection(util.select().position(barrel), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showControls(util.vector().blockSurface(barrel, Direction.WEST), Pointing.RIGHT, 30).rightClick();
        scene.idle(7);
        scene.world().showSection(util.select().position(side), Direction.EAST);
        scene.idle(10);
        scene.world().showSection(util.select().position(top), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Universal Adapters stick to whichever face you click, on any block")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(side, Direction.WEST));
        scene.idle(90);

        scene.overlay().showText(90)
                .colored(PonderPalette.BLUE)
                .text("Coming soon: each adapter puts the block it touches on the data bus, with its speed, stress, items and fluids")
                .placeNearTarget()
                .pointAt(util.vector().topOf(top));
        scene.idle(100);
    }

    public static void servo(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("bearing_servo", "Steering contraptions with the Bearing Servo");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos servo = util.grid().at(2, 1, 2);
        scene.world().showSection(util.select().position(servo), Direction.DOWN);
        scene.idle(10);
        ElementLink<WorldSectionElement> arm = scene.world().showIndependentSection(util.select().fromTo(1, 2, 2, 3, 2, 2), Direction.DOWN);
        scene.world().configureCenterOfRotation(arm, util.vector().centerOf(2, 2, 2));
        scene.idle(15);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("The Bearing Servo carries a contraption on its rotor, like a Mechanical Bearing")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(servo, Direction.WEST));
        scene.idle(90);

        scene.overlay().showText(90)
                .colored(PonderPalette.BLUE)
                .text("Coming soon: instead of spinning, it turns to the exact angle a program asks for")
                .placeNearTarget()
                .pointAt(util.vector().topOf(2, 2, 2));
        scene.world().rotateSection(arm, 0, 90, 0, 30);
        scene.idle(45);
        scene.world().rotateSection(arm, 0, -45, 0, 20);
        scene.idle(35);
        scene.world().rotateSection(arm, 0, -45, 0, 20);
        scene.idle(40);
    }

    public static void controls(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("panel_controls", "Building a control desk");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        scene.world().showSection(util.select().fromTo(1, 1, 2, 3, 1, 2), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(1, 1, 3, 3, 3, 3), Direction.DOWN);
        scene.idle(15);

        BlockPos button = util.grid().at(1, 2, 2);
        scene.overlay().showControls(util.vector().topOf(1, 1, 2), Pointing.DOWN, 30).rightClick();
        scene.idle(7);
        scene.world().showSection(util.select().fromTo(1, 2, 2, 3, 2, 2), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Panel controls sit on whichever face you click...")
                .placeNearTarget()
                .pointAt(util.vector().topOf(button));
        scene.idle(90);

        scene.world().showSection(util.select().fromTo(1, 3, 2, 3, 3, 2), Direction.NORTH);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(1, 1, 1, 3, 1, 1), Direction.NORTH);
        scene.idle(10);
        scene.overlay().showText(80)
                .text("...tops, walls, even ceilings. Mix them to build a control desk")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(util.grid().at(2, 3, 2), Direction.SOUTH));
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(util.grid().at(3, 2, 2)), Pointing.DOWN, 30)
                .rightClick().withItem(AllItems.WRENCH.asStack());
        scene.idle(7);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("Turn them with the Wrench")
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(3, 2, 2)));
        scene.idle(80);

        scene.overlay().showText(100)
                .colored(PonderPalette.BLUE)
                .text("Coming soon: programs read buttons, switches, knobs, sliders and keypads, and drive lamps, nixie digits and gauges")
                .independent(40);
        scene.idle(110);
    }

    private NewEraPonderScenes() {}
}
