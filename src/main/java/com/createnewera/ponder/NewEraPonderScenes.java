package com.createnewera.ponder;

import java.util.List;

import com.createnewera.block.BrassScreenBlock;
import com.createnewera.block.control.NixieDigitBlock;
import com.createnewera.block.control.PanelControlBlockEntity;
import com.createnewera.block.control.PanelLampBlock;
import com.createnewera.block.screen.BrassScreenBlockEntity;
import com.createnewera.block.screen.ScreenCanvas;
import com.createnewera.block.screen.ScreenMarkup;
import com.createnewera.block.screen.ScreenPane;
import com.createnewera.bus.BusPort;
import com.createnewera.bus.BusSpan;
import com.createnewera.bus.PartGeometry;
import com.createnewera.bus.PortMode;
import com.createnewera.registry.ModItems;
import com.createnewera.computer.BrassComputerBlock;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

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
    public static void computer(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
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

        scene.world().showSection(util.select().fromTo(2, 1, 0, 2, 1, 1), Direction.SOUTH);
        scene.idle(10);
        scene.world().setKineticSpeed(util.select().fromTo(2, 1, 0, 2, 1, 2), 64);
        scene.effects().rotationSpeedIndicator(computer);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("It runs on rotational force through its back. The faster it spins, the faster programs run")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(util.grid().at(2, 1, 1), Direction.UP));
        scene.idle(100);
        scene.overlay().showText(70)
                .colored(PonderPalette.RED)
                .text("Without rotation, or when overstressed, the program pauses and carries on later")
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
                        .text("Screens facing the same way join into one display")
                        .placeNearTarget()
                        .pointAt(util.vector().blockSurface(pos, Direction.SOUTH));
        }
        scene.idle(60);

        // the pane's top left, as the camera sees it: its pixels live there
        BlockPos topLeft = util.grid().at(3, 2, 2);
        Vec3 glass = util.vector().blockSurface(util.grid().at(2, 2, 2), Direction.SOUTH);
        showMarkup(scene, util, topLeft, "0");
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("Every screen is a grid of 24 by 24 flip dots. When the picture changes, each dot turns over to show its new colour")
                .placeNearTarget()
                .pointAt(glass);
        scene.idle(110);

        scene.overlay().showText(110)
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .text("Lay a screen out in a .screen file on the computer, with tags like title, row, value, bar and button, then show it: screen.show(\"farm\")")
                .placeNearTarget()
                .pointAt(glass);
        scene.idle(120);

        showMarkup(scene, util, topLeft, "1234");
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("screen.set(\"iron\", 1234) changes one part. Only the dots that need to turn over do")
                .placeNearTarget()
                .pointAt(glass);
        scene.idle(100);

        scene.overlay().showControls(util.vector().blockSurface(util.grid().at(2, 1, 2), Direction.SOUTH), Pointing.DOWN, 40).rightClick();
        scene.idle(10);
        scene.overlay().showText(90)
                .text("Right-clicking a button on the screen runs whatever the program gave screen.onClick")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(util.grid().at(2, 1, 2), Direction.SOUTH));
        scene.idle(100);
    }

    /** Lays the farm screen out on the pane, as a program showing farm.screen would, and turns the dots. */
    private static void showMarkup(SceneBuilder scene, SceneBuildingUtil util, BlockPos topLeft, String iron) {
        int w = (3 * 16 - 2 * ScreenPane.FRAME) * ScreenPane.DENSITY, h = (2 * 16 - 2 * ScreenPane.FRAME) * ScreenPane.DENSITY;
        ScreenCanvas canvas = new ScreenCanvas(new byte[w * h], w, h);
        try {
            ScreenMarkup.draw(ScreenMarkup.parse("""
                    <screen>
                      <title>IRON FARM</title>
                      <row><label>Iron</label><value align="right">%s</value></row>
                      <bar value="0.7"/>
                    </screen>""".formatted(iron)), canvas);
        } catch (ScreenMarkup.MarkupException e) {
            return;
        }
        var tag = BrassScreenBlockEntity.shownTag(canvas, true);
        scene.world().modifyBlockEntityNBT(util.select().position(topLeft), BrassScreenBlockEntity.class, nbt -> nbt.merge(tag));
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
                .text("Panel controls go on whatever face you click: tops, walls, even ceilings")
                .placeNearTarget()
                .pointAt(util.vector().topOf(button));
        scene.idle(90);

        scene.world().showSection(util.select().fromTo(1, 1, 1, 3, 1, 1), Direction.NORTH);
        scene.idle(15);
        BlockPos knob = util.grid().at(1, 1, 1);
        scene.overlay().showControls(util.vector().blockSurface(knob, Direction.SOUTH), Pointing.DOWN, 50).rightClick();
        for (int notch = 2; notch <= 12; notch += 2) {
            int n = notch;
            scene.world().modifyBlockEntityNBT(util.select().position(knob), PanelControlBlockEntity.class, nbt -> {
                nbt.putDouble("min", 0);
                nbt.putDouble("max", 15);
                nbt.putDouble("step", 1);
                nbt.putInt("position", n);
            });
            scene.idle(5);
        }
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Turn a knob or slide a slider by holding right-click and moving the mouse sideways. Hold Ctrl for finer steps")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(knob, Direction.SOUTH));
        scene.idle(100);

        BlockPos lamp = util.grid().at(2, 1, 1);
        scene.world().modifyBlock(lamp, s -> s.setValue(PanelLampBlock.LIGHT, PanelLampBlock.Light.GREEN), false);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Every control has its own connection: cable it to a computer and programs can read it or drive it")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(lamp, Direction.SOUTH));
        scene.idle(100);

        scene.world().showSection(util.select().fromTo(1, 4, 3, 3, 4, 3), Direction.DOWN);
        scene.idle(15);
        String word = "HI!";
        for (int i = 0; i < 3; i++) {
            BlockPos tube = util.grid().at(1 + i, 4, 3);
            String glyph = word.substring(i, i + 1);
            scene.world().modifyBlock(tube, s -> s.setValue(NixieDigitBlock.LIT, true), false);
            scene.world().modifyBlockEntityNBT(util.select().position(tube), PanelControlBlockEntity.class, nbt -> {
                nbt.putString("glyph", glyph);
                nbt.putString("color", "orange");
            });
            scene.idle(4);
        }
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("Nixie tubes side by side make one display. Cable any of them and nixie.setText(\"HI!\") lights the whole row")
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(2, 4, 3)));
        scene.idle(110);

        scene.overlay().showControls(util.vector().topOf(util.grid().at(3, 2, 2)), Pointing.DOWN, 30)
                .rightClick().withItem(AllItems.WRENCH.asStack());
        scene.idle(7);
        scene.overlay().showText(70)
                .text("A Wrench turns them round")
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(3, 2, 2)));
        scene.idle(80);
    }

    public static void bus(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("data_bus", "Wiring machines to a computer");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos computer = util.grid().at(2, 1, 2), chest = util.grid().at(0, 1, 2), barrel = util.grid().at(4, 1, 2);
        scene.world().showSection(util.select().fromTo(2, 1, 0, 2, 1, 2), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().fromTo(2, 1, 0, 2, 1, 2), 64);
        scene.idle(10);
        scene.world().showSection(util.select().position(chest), Direction.DOWN);
        scene.idle(15);

        // a data port on the chest's south face, and the computer's own port on its west side
        BusPort onChest = new BusPort(1, chest, Direction.SOUTH, 6, 6, BusPort.Kind.PORT, PortMode.OUT);
        scene.overlay().showControls(util.vector().blockSurface(chest, Direction.SOUTH), Pointing.DOWN, 30)
                .rightClick().withItem(ModItems.BUS_PORT.asStack());
        scene.idle(7);
        show(scene, new BusElement(List.of(onChest), List.of()));
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Data ports stick to any face of any block. That block is now a device programs can reach")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(chest, Direction.SOUTH));
        scene.idle(100);

        BusPort onComputer = new BusPort(2, computer, Direction.WEST, 6, 6, BusPort.Kind.PORT, PortMode.IN);
        Vec3 from = PartGeometry.anchor(onChest), to = PartGeometry.anchor(onComputer);
        scene.overlay().showControls(util.vector().blockSurface(computer, Direction.WEST), Pointing.DOWN, 30)
                .rightClick().withItem(ModItems.CABLE_SPOOL.asStack());
        scene.idle(7);
        show(scene, new BusElement(List.of(), List.of(new BusSpan(1, 1, 2, null, BusSpan.Casing.NONE, true, List.of(from, to)))));
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("A Cable Spool joins two ports: click one, then the other. The computer has ports of its own on each side")
                .placeNearTarget()
                .pointAt(from.lerp(to, 0.5));
        scene.idle(110);

        scene.overlay().showText(100)
                .colored(PonderPalette.BLUE)
                .text("In a program: local chest = bus.get(\"chest\"), then chest.getItems(), chest.pushItems(...) and the rest")
                .placeNearTarget()
                .pointAt(util.vector().topOf(computer));
        scene.idle(110);

        scene.world().showSection(util.select().position(barrel), Direction.DOWN);
        scene.idle(15);
        BusPort brass = new BusPort(3, barrel, Direction.SOUTH, 6, 6, BusPort.Kind.BRASS_PORT, PortMode.OUT);
        BusPort east = new BusPort(4, computer, Direction.EAST, 6, 6, BusPort.Kind.PORT, PortMode.OUT);
        show(scene, new BusElement(List.of(brass), List.of(new BusSpan(2, 3, 4, net.minecraft.world.item.DyeColor.LIME,
                BusSpan.Casing.NONE, true, List.of(PartGeometry.anchor(brass), PartGeometry.anchor(east))))));
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("A Brass Port reads a block and gives it orders through the same port. Dye a cable to keep separate networks apart")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(barrel, Direction.SOUTH));
        scene.idle(110);

        scene.overlay().showControls(util.vector().blockSurface(chest, Direction.SOUTH), Pointing.DOWN, 30)
                .rightClick().withItem(AllItems.WRENCH.asStack());
        scene.idle(7);
        scene.overlay().showText(90)
                .text("A plain port does one thing. A Wrench flips it: out lets programs read the block, in lets them give it orders")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(chest, Direction.SOUTH));
        scene.idle(100);
    }

    public static void instruments(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("panel_instruments", "Flight instruments");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        scene.world().showSection(util.select().fromTo(0, 1, 2, 4, 1, 2), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(0, 2, 2, 4, 2, 2), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("The altimeter, gyroscope, compass and airspeed indicator read the ship they're built into")
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(1, 2, 2)));
        scene.idle(110);

        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("Twelve o'clock on each dial is the way the ship flies: away from you on a desk, into the wall on a wall")
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(3, 2, 2)));
        scene.idle(110);

        scene.overlay().showText(100)
                .colored(PonderPalette.BLUE)
                .text("Programs read the same numbers: getAltitude(), getPitch(), getHeading(), getSpeed()...")
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(4, 2, 2)));
        scene.idle(110);
    }

    /** Ports or cables appearing in the scene, fading in like blocks. */
    private static void show(SceneBuilder scene, BusElement element) {
        scene.addInstruction(new net.createmod.ponder.foundation.instruction.FadeIntoSceneInstruction<BusElement>(10, Direction.DOWN, element) {
            @Override
            protected Class<BusElement> getElementClass() {
                return BusElement.class;
            }
        });
    }

    private NewEraPonderScenes() {}
}
