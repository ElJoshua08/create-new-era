package com.createnewera.registry;

import static com.createnewera.CreateNewEra.REGISTRATE;

import com.createnewera.CreateNewEra;
import com.createnewera.ponder.NewEraPonderPlugin;
import com.tterrag.registrate.providers.ProviderType;

import net.createmod.ponder.foundation.PonderIndex;

/** English strings that don't belong to a single registry entry. Spanish lives in es_es.json. */
public final class ModLang {
    public static void register() {
        ponder();
        lang("itemGroup.create_new_era.main", "Create: New Era");

        lang("create_new_era.configuration.title", "Create: New Era");
        lang("create_new_era.configuration.screen", "Screens");
        lang("create_new_era.configuration.screenPixelShape", "Pixel shape");
        lang("create_new_era.configuration.screenPixelShape.tooltip", "Flip pixels on brass screens: square plates or round discs.");

        // computer editor and console
        lang("create_new_era.editor.console_empty", "print() output shows up here");
        lang("create_new_era.editor.redstone", "Redstone");
        lang("create_new_era.editor.in", "in");
        lang("create_new_era.editor.out", "out");
        lang("create_new_era.editor.running", "Running");
        lang("create_new_era.editor.stopped", "Stopped");
        lang("create_new_era.editor.saved", "Saved");
        lang("create_new_era.editor.run", "Run this file (Ctrl+R)");
        lang("create_new_era.editor.stop", "Stop (Ctrl+T)");
        lang("create_new_era.console.running", "Running");
        lang("create_new_era.console.restarted", "Restarted after the world loaded");
        lang("create_new_era.console.stopped", "Stopped");
        lang("create_new_era.console.finished", "Program finished");

        lang("create_new_era.editor.files", "Files");
        lang("create_new_era.editor.new_file", "name.lua");
        lang("create_new_era.editor.delete_confirm", "Click × to delete");
        lang("create_new_era.editor.bad_name", "Names can use letters, numbers, _ - . and /");
        lang("create_new_era.editor.file_exists", "%s already exists");
        lang("create_new_era.editor.flows", "Flows");
        lang("create_new_era.editor.flows_soon", "Coming soon: what moves between your machines and programs, live.");
        lang("create_new_era.editor.line_col", "Ln %s, Col %s");
        lang("create_new_era.editor.panel.files", "Files (Ctrl+B)");
        lang("create_new_era.editor.panel.redstone", "Redstone");
        lang("create_new_era.editor.panel.flows", "Flows");
        lang("create_new_era.editor.paused_rotation", "Paused: needs rotation");
        lang("create_new_era.editor.paused_stress", "Paused: overstressed");
        lang("create_new_era.editor.rpm", "%s RPM");
        lang("create_new_era.editor.device.ports_both", "Output and input port.");
        lang("create_new_era.editor.device.ports_out", "Output port.");
        lang("create_new_era.editor.device.ports_in", "Input port.");
        lang("create_new_era.editor.device.reads", "Reads:");
        lang("create_new_era.editor.device.orders", "Orders:");
        // data bus
        lang("item.create_new_era.bus_port.tooltip.summary", "A _data port_ for the bus. Sticks to _any face_ of _any block_, Create machines included, without changing how the block works.");
        lang("item.create_new_era.bus_port.tooltip.condition1", "Output (amber light)");
        lang("item.create_new_era.bus_port.tooltip.behaviour1", "The block _publishes_ what it's doing: programs can _read_ it.");
        lang("item.create_new_era.bus_port.tooltip.condition2", "Input (green light)");
        lang("item.create_new_era.bus_port.tooltip.behaviour2", "The block _takes orders_: programs can _control_ it.");
        lang("item.create_new_era.bus_port.tooltip.control1", "When R-Clicked on a face");
        lang("item.create_new_era.bus_port.tooltip.action1", "Sticks a port where you click.");
        lang("item.create_new_era.bus_port.tooltip.control2", "Wrench on a port");
        lang("item.create_new_era.bus_port.tooltip.action2", "Turns it between _input_ and _output_. _Sneak_ to take it off.");
        lang("item.create_new_era.port_expansion.tooltip.summary", "Stick it on a _Brass Computer_ for _4 more ports_. Up to 3 per computer.");
        lang("item.create_new_era.port_expansion.tooltip.control1", "When R-Clicked on a computer");
        lang("item.create_new_era.port_expansion.tooltip.action1", "Sticks the expansion where you click.");
        lang("create_new_era.bus.no_room", "No room for a port there");
        lang("create_new_era.bus.expansion_needs_computer", "Port expansions go on a Brass Computer, up to 3");
        lang("create_new_era.bus.use_sockets", "The computer's own ports go in the sockets on its sides");
        lang("create_new_era.bus.computer_full", "No ports left: a Port Expansion adds 4 more");
        lang("create_new_era.bus.port_now_out", "Port set to output: programs can read this block");
        lang("create_new_era.bus.port_now_in", "Port set to input: programs can give this block orders");
        // the editor's description of every function a device offers (DeviceDrivers.Fn.doc)
        lang("create_new_era.api.kinetic.getSpeed", "How fast it turns, in RPM. Negative when it turns the other way; 0 when it's still or overstressed.");
        lang("create_new_era.api.kinetic.isOverstressed", "True when its kinetic network asks for more stress than it has, so it's stopped.");
        lang("create_new_era.api.kinetic.getStress", "Its network's stress: used is what the machines ask for, capacity what the sources give, both in SU.");
        lang("create_new_era.api.kinetic.getTargetSpeed", "The speed it's set to, in RPM (a creative motor or a rotation speed controller).");
        lang("create_new_era.api.kinetic.setSpeed", "Sets the speed, from -256 to 256 RPM, like turning its value with the mouse wheel.");
        lang("create_new_era.api.items.items", "Everything it holds: each item id with how many there are, across all its slots.");
        lang("create_new_era.api.items.count", "How many of one item it holds, e.g. count(\"minecraft:iron_ingot\").");
        lang("create_new_era.api.items.getSlots", "How many slots it has.");
        lang("create_new_era.api.fluids.fluids", "Each of its tanks: the fluid's id (\"\" when empty), how much is in it and how much fits, in mB.");
        lang("create_new_era.api.machine.read", "Everything readable about it in one table: speed, overstressed, stress, items, fluids... whichever it has.");
        lang("create_new_era.api.panel_button.isPressed", "True while it's held down: half a second after each press.");
        lang("create_new_era.api.panel_button.read", "Whether it's pressed right now.");
        lang("create_new_era.api.panel_switch.isOn", "Which way the lever is: true for on.");
        lang("create_new_era.api.panel_switch.setOn", "Flips the lever from the program, as if a player had.");
        lang("create_new_era.api.panel_switch.read", "Whether it's on.");
        lang("create_new_era.api.panel_knob.getValue", "Where the knob is set, from 0 to 15.");
        lang("create_new_era.api.panel_knob.setValue", "Turns the knob to a value from 0 to 15.");
        lang("create_new_era.api.panel_knob.read", "Its value, from 0 to 15.");
        lang("create_new_era.api.panel_slider.getValue", "Where the handle is, from 0 (one end) to 15 (the other).");
        lang("create_new_era.api.panel_slider.setValue", "Slides the handle to a value from 0 to 15.");
        lang("create_new_era.api.panel_slider.read", "Its value, from 0 to 15.");
        lang("create_new_era.api.panel_lamp.get", "Its colour now, or \"off\".");
        lang("create_new_era.api.panel_lamp.set", "Lights it \"amber\", \"green\" or \"red\", or turns it \"off\". true is amber, false off.");
        lang("create_new_era.api.panel_lamp.read", "Its colour, or \"off\".");
        lang("create_new_era.api.nixie_digit.getDigit", "The digit it shows, or nil when it's dark.");
        lang("create_new_era.api.nixie_digit.setDigit", "Shows a digit from 0 to 9; nil turns it dark.");
        lang("create_new_era.api.nixie_digit.read", "Its digit, or nil when it's dark.");
        lang("create_new_era.api.panel_gauge.get", "Where the needle is, from 0 (empty) to 1 (full).");
        lang("create_new_era.api.panel_gauge.set", "Moves the needle: 0 is empty, 1 full, 0.5 halfway. It moves in sixteen steps.");
        lang("create_new_era.api.panel_gauge.read", "Where the needle is, from 0 to 1.");
        lang("create_new_era.api.panel_keypad.getKey", "The last key pressed: \"0\" to \"9\", \"*\" or \"#\"; nil before the first.");
        lang("create_new_era.api.panel_keypad.read", "The last key pressed.");
        lang("create_new_era.api.on_change", "Runs your function every time the device changes, until the program stops. It's given value, then device. value is %s");
        lang("create_new_era.api.on_press", "Runs your function each time the button is pressed down (not when it pops back up).");
        lang("create_new_era.api.on_key", "Runs your function for every key pressed, with the key: \"0\" to \"9\", \"*\" or \"#\".");
        lang("create_new_era.api.kinetic.getMinValue", "The lowest speed it can be set to: -256 RPM.");
        lang("create_new_era.api.kinetic.getMaxValue", "The highest speed it can be set to: 256 RPM.");
        lang("create_new_era.api.panel_knob.getMinValue", "Its lowest value: 0.");
        lang("create_new_era.api.panel_knob.getMaxValue", "Its highest value: 15.");
        lang("create_new_era.api.panel_slider.getMinValue", "Its lowest value: 0.");
        lang("create_new_era.api.panel_slider.getMaxValue", "Its highest value: 15.");
        lang("create_new_era.api.panel_gauge.getMinValue", "The needle's lowest position: 0, empty.");
        lang("create_new_era.api.panel_gauge.getMaxValue", "The needle's highest position: 1, full.");
        lang("create_new_era.api.nixie_digit.getMinValue", "The lowest digit it shows: 0.");
        lang("create_new_era.api.nixie_digit.getMaxValue", "The highest digit it shows: 9.");
        lang("item.create_new_era.brass_port.tooltip.summary", "A _data port_ in brass that does both jobs: programs can _read_ the block and _give it orders_ through the same port.");
        lang("item.create_new_era.brass_port.tooltip.control1", "When R-Clicked on a face");
        lang("item.create_new_era.brass_port.tooltip.action1", "Sticks a port where you click. No mode to pick with the wrench.");
        lang("block.create_new_era.port_hub.tooltip.summary", "Twenty-four data sockets, _four on every face_, all joined inside: where a network's cables meet.");
        lang("block.create_new_era.port_hub.tooltip.condition1", "With a Cable Spool");
        lang("block.create_new_era.port_hub.tooltip.behaviour1", "Click any of its sockets. Cables of _different colours_ still don't join through it; bare copper joins any.");
        // under engineer's goggles, and the knob and slider while they're dragged
        lang("create_new_era.dial.value", "Value: %s/%s");
        lang("create_new_era.dial.hint", "Hold and drag to adjust");
        lang("create_new_era.goggles.cabled", "Cabled to the bus");
        lang("create_new_era.goggles.not_cabled", "No cable yet");
        lang("create_new_era.goggles.pressed", "Pressed");
        lang("create_new_era.goggles.released", "Not pressed");
        lang("create_new_era.goggles.on", "On");
        lang("create_new_era.goggles.off", "Off");
        lang("create_new_era.goggles.value", "Value: %s/%s");
        lang("create_new_era.goggles.light.off", "Off");
        lang("create_new_era.goggles.light.amber", "Lit amber");
        lang("create_new_era.goggles.light.green", "Lit green");
        lang("create_new_era.goggles.light.red", "Lit red");
        lang("create_new_era.goggles.dark", "Dark");
        lang("create_new_era.goggles.digit", "Showing %s");
        lang("create_new_era.goggles.needle", "Needle at %s%%");
        lang("create_new_era.goggles.no_key", "No key pressed yet");
        lang("create_new_era.goggles.key", "Last key: %s");
        lang("create_new_era.goggles.computer", "Computer:");
        lang("create_new_era.goggles.idle", "No program running");
        lang("create_new_era.goggles.program", "Running %s");
        lang("create_new_era.goggles.pace", "At %s%% speed");
        lang("create_new_era.goggles.paused_rotation", "Paused: needs rotation");
        lang("create_new_era.goggles.paused_stress", "Paused: overstressed");
        // cables
        lang("item.create_new_era.cable_spool.dyed", "%s Cable Spool");
        lang("item.create_new_era.cable_spool.tooltip.summary", "Data cable on a reel. The bar is the _metres left_; an empty spool is wound again, not thrown away.");
        lang("item.create_new_era.cable_spool.tooltip.control1", "R-Click a port, then another");
        lang("item.create_new_era.cable_spool.tooltip.action1", "Hangs a cable between them, up to _24 blocks_.");
        lang("item.create_new_era.cable_spool.tooltip.control2", "Sneak on the second click");
        lang("item.create_new_era.cable_spool.tooltip.action2", "Runs it _flat along the faces_ in between instead. Red when they aren't joined.");
        lang("item.create_new_era.cable_spool.tooltip.condition1", "Dyed");
        lang("item.create_new_era.cable_spool.tooltip.behaviour1", "Same dye on all four sides in a crafting grid. Cables of _different colours never join_: separate networks. Bare copper joins any.");
        lang("item.create_new_era.cable_splice.tooltip.summary", "Joins cables _in the middle of a run_. Sticks to any face like a port, but it isn't a device.");
        lang("item.create_new_era.cable_splice.tooltip.control1", "When R-Clicked on a face");
        lang("item.create_new_era.cable_splice.tooltip.action1", "Sticks a splice where you click.");
        lang("create_new_era.cable.started", "Cable started: click the other end (sneak to run it along faces)");
        lang("create_new_era.cable.cancelled", "Cable let go");
        lang("create_new_era.cable.laid", "Laid %s m of cable");
        lang("create_new_era.cable.metres_left", "%s / %s m of cable");
        lang("create_new_era.cable.not_enough", "Needs %s m, the spool has %s m");
        lang("create_new_era.cable.color_clash", "That part already has cable of another colour");
        lang("create_new_era.cable.same_part", "Pick a different part");
        lang("create_new_era.cable.too_far", "Too far to hang a cable (24 blocks at most)");
        lang("create_new_era.cable.no_route", "No way along the faces between them");
        lang("create_new_era.cable.already_joined", "Those two are already joined");
        lang("create_new_era.cable.port_taken", "That port already has its cable: bring several together at a Port Hub");

        // Create-style tooltips: summary always, conditionN/behaviourN on Shift, controlN/actionN on Ctrl
        lang("block.create_new_era.brass_computer.tooltip.summary", "Runs _Lua_ programs you write in its built-in _editor_. They drive _redstone_ and, through _data ports_ and _cables_, the machines on the _data bus_.");
        lang("block.create_new_era.brass_computer.tooltip.condition1", "When rotated");
        lang("block.create_new_era.brass_computer.tooltip.behaviour1", "Takes a _shaft_ in its back. The _faster_ it spins, the faster programs run. Without rotation they _pause_ and carry on when it turns again.");
        lang("block.create_new_era.brass_computer.tooltip.condition2", "Redstone");
        lang("block.create_new_era.brass_computer.tooltip.behaviour2", "Every face is a _side_: front, back, left, right, top and bottom. Read signals with _redstone.getInput_, send them with _redstone.setOutput_.");
        lang("block.create_new_era.brass_computer.tooltip.condition3", "When the world loads");
        lang("block.create_new_era.brass_computer.tooltip.behaviour3", "A program that was running _starts again_ from the top.");
        lang("block.create_new_era.brass_computer.tooltip.control1", "When R-Clicked");
        lang("block.create_new_era.brass_computer.tooltip.action1", "Opens the _editor_. _Ctrl+R_ runs, _Ctrl+T_ stops.");
        lang("block.create_new_era.brass_computer.tooltip.control2", "When Wrenched");
        lang("block.create_new_era.brass_computer.tooltip.action2", "Turns the computer. The sides turn with its _front_.");
        lang("block.create_new_era.brass_screen.tooltip.summary", "Joins with its neighbours into one big display made of _flip pixels_ that turn when they change.");
        lang("block.create_new_era.brass_screen.tooltip.condition1", "Next to another screen");
        lang("block.create_new_era.brass_screen.tooltip.behaviour1", "Screens facing the _same way_ merge into _one pane_ and the brass frame between them disappears.");
        lang("block.create_new_era.brass_screen.tooltip.condition2", "Pixels");
        lang("block.create_new_era.brass_screen.tooltip.behaviour2", "_48 by 48_ flip pixels per block. Pick _square plates_ or _round discs_ in the client config.");
        lang("block.create_new_era.brass_screen.tooltip.control1", "When Wrenched");
        lang("block.create_new_era.brass_screen.tooltip.action1", "Turns the screen. It joins whatever screens it now lines up with.");
        lang("block.create_new_era.universal_adapter.tooltip.summary", "Attach to any block to expose what it can do, _kinetics_, _items_ or _fluids_, on the data bus.");
        lang("block.create_new_era.universal_adapter.tooltip.condition1", "When placed");
        lang("block.create_new_era.universal_adapter.tooltip.behaviour1", "Sticks to the face you click, so it fits on _any side_ of a machine.");
        lang("block.create_new_era.universal_adapter.tooltip.condition2", "Coming soon");
        lang("block.create_new_era.universal_adapter.tooltip.behaviour2", "Not wired up yet: put a _Data Port_ on a block to bring it onto the bus.");
        lang("block.create_new_era.bearing_servo.tooltip.summary", "Turns an attached _contraption_ to an exact angle on command.");
        lang("block.create_new_era.bearing_servo.tooltip.condition1", "When placed");
        lang("block.create_new_era.bearing_servo.tooltip.behaviour1", "Faces you, like Create's _bearings_.");
        lang("block.create_new_era.bearing_servo.tooltip.condition2", "Coming soon");
        lang("block.create_new_era.bearing_servo.tooltip.behaviour2", "Programs will set its _angle_ in degrees, for gimbals, turrets and flaps.");
        lang("item.create_new_era.control_plate.tooltip.summary", "Andesite base for every _panel control_.");
        lang("item.create_new_era.control_plate.tooltip.condition1", "Crafting");
        lang("item.create_new_era.control_plate.tooltip.behaviour1", "Combine with a part, like a _lever_ or a _nixie tube_, to make a control.");
        lang("block.create_new_era.panel_button.tooltip.summary", "A momentary _push button_ for programs to react to.");
        lang("block.create_new_era.panel_button.tooltip.condition1", "When placed");
        lang("block.create_new_era.panel_button.tooltip.behaviour1", "Sits on the face you click: _floors, walls or ceilings_. Put several together to build a _control desk_.");
        lang("block.create_new_era.panel_button.tooltip.condition2", "On the bus");
        lang("block.create_new_era.panel_button.tooltip.behaviour2", "_isPressed()_, and _onPress(fn)_ runs fn on every press. It pops back up after half a second.");
        lang("block.create_new_era.panel_switch.tooltip.summary", "An _on/off_ lever a program can read.");
        lang("block.create_new_era.panel_switch.tooltip.condition1", "When placed");
        lang("block.create_new_era.panel_switch.tooltip.behaviour1", "Sits on the face you click: _floors, walls or ceilings_. Put several together to build a _control desk_.");
        lang("block.create_new_era.panel_switch.tooltip.condition2", "On the bus");
        lang("block.create_new_era.panel_switch.tooltip.behaviour2", "_isOn()_ reads it; _setOn(b)_ flips it from a program.");
        lang("block.create_new_era.panel_knob.tooltip.summary", "A rotary knob that dials in a value from _0 to 15_.");
        lang("block.create_new_era.panel_knob.tooltip.condition1", "When placed");
        lang("block.create_new_era.panel_knob.tooltip.behaviour1", "Sits on the face you click: _floors, walls or ceilings_. Put several together to build a _control desk_.");
        lang("block.create_new_era.panel_knob.tooltip.condition2", "On the bus");
        lang("block.create_new_era.panel_knob.tooltip.behaviour2", "_getValue()_ reads it, _setValue(n)_ turns it. Hold R-Click on it and drag the mouse sideways to turn it.");
        lang("block.create_new_era.panel_slider.tooltip.summary", "A fader for a value from _0 to 15_.");
        lang("block.create_new_era.panel_slider.tooltip.condition1", "When placed");
        lang("block.create_new_era.panel_slider.tooltip.behaviour1", "Sits on the face you click: _floors, walls or ceilings_. Put several together to build a _control desk_.");
        lang("block.create_new_era.panel_slider.tooltip.condition2", "On the bus");
        lang("block.create_new_era.panel_slider.tooltip.behaviour2", "_getValue()_ reads it, _setValue(n)_ slides it. Hold R-Click on it and drag the mouse sideways to slide it.");
        lang("block.create_new_era.panel_lamp.tooltip.summary", "An indicator light a program can switch and _colour_.");
        lang("block.create_new_era.panel_lamp.tooltip.condition1", "When placed");
        lang("block.create_new_era.panel_lamp.tooltip.behaviour1", "Sits on the face you click: _floors, walls or ceilings_. Put several together to build a _control desk_.");
        lang("block.create_new_era.panel_lamp.tooltip.condition2", "On the bus");
        lang("block.create_new_era.panel_lamp.tooltip.behaviour2", "_set(\"amber\")_, _\"green\"_, _\"red\"_ or _\"off\"_; _get()_ says which.");
        lang("block.create_new_era.nixie_digit.tooltip.summary", "One glowing _digit_; place several in a row to show whole numbers.");
        lang("block.create_new_era.nixie_digit.tooltip.condition1", "When placed");
        lang("block.create_new_era.nixie_digit.tooltip.behaviour1", "Sits on the face you click: _floors, walls or ceilings_. Put several together to build a _control desk_.");
        lang("block.create_new_era.nixie_digit.tooltip.condition2", "On the bus");
        lang("block.create_new_era.nixie_digit.tooltip.behaviour2", "_setDigit(n)_ shows 0 to 9, _setDigit(nil)_ turns it dark; _getDigit()_ reads it.");
        lang("block.create_new_era.panel_gauge.tooltip.summary", "A needle dial that shows any _value_.");
        lang("block.create_new_era.panel_gauge.tooltip.condition1", "When placed");
        lang("block.create_new_era.panel_gauge.tooltip.behaviour1", "Sits on the face you click: _floors, walls or ceilings_. Put several together to build a _control desk_.");
        lang("block.create_new_era.panel_gauge.tooltip.condition2", "On the bus");
        lang("block.create_new_era.panel_gauge.tooltip.behaviour2", "_set(0.25)_ moves the needle a quarter of the way, from 0 to 1; _get()_ reads it.");
        lang("block.create_new_era.panel_keypad.tooltip.summary", "Twelve keys to _type numbers_ into a program.");
        lang("block.create_new_era.panel_keypad.tooltip.condition1", "When placed");
        lang("block.create_new_era.panel_keypad.tooltip.behaviour1", "Sits on the face you click: _floors, walls or ceilings_. Put several together to build a _control desk_.");
        lang("block.create_new_era.panel_keypad.tooltip.condition2", "On the bus");
        lang("block.create_new_era.panel_keypad.tooltip.behaviour2", "_onKey(fn)_ runs fn(key) for every key pressed; _getKey()_ gives the last one.");
    }

    /** Ponder scene text is written in the scenes; datagen collects it into en_us. */
    private static void ponder() {
        REGISTRATE.addDataGenerator(ProviderType.LANG, provider -> {
            // client setup doesn't run during datagen, so register the plugin here too
            PonderIndex.addPlugin(new NewEraPonderPlugin());
            PonderIndex.getLangAccess().provideLang(CreateNewEra.MOD_ID, provider::add);
        });
    }

    private static void lang(String key, String value) {
        REGISTRATE.addRawLang(key, value);
    }

    private ModLang() {}
}
