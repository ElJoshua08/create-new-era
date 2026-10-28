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
        lang("create_new_era.editor.flows_soon", "Once the data bus arrives, this shows what moves between your machines and programs, live.");
        lang("create_new_era.editor.line_col", "Ln %s, Col %s");
        lang("create_new_era.editor.panel.files", "Files (Ctrl+B)");
        lang("create_new_era.editor.panel.redstone", "Redstone");
        lang("create_new_era.editor.panel.flows", "Flows");
        // Create-style tooltips: summary always, conditionN/behaviourN on Shift, controlN/actionN on Ctrl
        lang("block.create_new_era.brass_computer.tooltip.summary", "Runs _Lua_ programs you write in its built-in _editor_. For now they work with _redstone_; the _data bus_ comes next.");
        lang("block.create_new_era.brass_computer.tooltip.condition1", "When running");
        lang("block.create_new_era.brass_computer.tooltip.behaviour1", "The screen lights up. The program gets a _slice of time_ every tick and keeps going while nobody watches.");
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
        lang("block.create_new_era.universal_adapter.tooltip.behaviour2", "Arrives with the _data bus_. Until then it doesn't do anything.");
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
        lang("block.create_new_era.panel_button.tooltip.condition2", "Coming soon");
        lang("block.create_new_era.panel_button.tooltip.behaviour2", "Programs will see each _press_.");
        lang("block.create_new_era.panel_switch.tooltip.summary", "An _on/off_ lever a program can read.");
        lang("block.create_new_era.panel_switch.tooltip.condition1", "When placed");
        lang("block.create_new_era.panel_switch.tooltip.behaviour1", "Sits on the face you click: _floors, walls or ceilings_. Put several together to build a _control desk_.");
        lang("block.create_new_era.panel_switch.tooltip.condition2", "Coming soon");
        lang("block.create_new_era.panel_switch.tooltip.behaviour2", "Programs will read its _position_.");
        lang("block.create_new_era.panel_knob.tooltip.summary", "A rotary knob that dials in a _value_.");
        lang("block.create_new_era.panel_knob.tooltip.condition1", "When placed");
        lang("block.create_new_era.panel_knob.tooltip.behaviour1", "Sits on the face you click: _floors, walls or ceilings_. Put several together to build a _control desk_.");
        lang("block.create_new_era.panel_knob.tooltip.condition2", "Coming soon");
        lang("block.create_new_era.panel_knob.tooltip.behaviour2", "Programs will read its _value_.");
        lang("block.create_new_era.panel_slider.tooltip.summary", "A fader for a smooth _0 to 100%_ value.");
        lang("block.create_new_era.panel_slider.tooltip.condition1", "When placed");
        lang("block.create_new_era.panel_slider.tooltip.behaviour1", "Sits on the face you click: _floors, walls or ceilings_. Put several together to build a _control desk_.");
        lang("block.create_new_era.panel_slider.tooltip.condition2", "Coming soon");
        lang("block.create_new_era.panel_slider.tooltip.behaviour2", "Programs will read its _position_.");
        lang("block.create_new_era.panel_lamp.tooltip.summary", "An indicator light a program can switch and _colour_.");
        lang("block.create_new_era.panel_lamp.tooltip.condition1", "When placed");
        lang("block.create_new_era.panel_lamp.tooltip.behaviour1", "Sits on the face you click: _floors, walls or ceilings_. Put several together to build a _control desk_.");
        lang("block.create_new_era.panel_lamp.tooltip.condition2", "Coming soon");
        lang("block.create_new_era.panel_lamp.tooltip.behaviour2", "Programs will switch it and pick its _colour_.");
        lang("block.create_new_era.nixie_digit.tooltip.summary", "One glowing _digit_; place several in a row to show whole numbers.");
        lang("block.create_new_era.nixie_digit.tooltip.condition1", "When placed");
        lang("block.create_new_era.nixie_digit.tooltip.behaviour1", "Sits on the face you click: _floors, walls or ceilings_. Put several together to build a _control desk_.");
        lang("block.create_new_era.nixie_digit.tooltip.condition2", "Coming soon");
        lang("block.create_new_era.nixie_digit.tooltip.behaviour2", "Programs will show _digits_ on it.");
        lang("block.create_new_era.panel_gauge.tooltip.summary", "A needle dial that shows any _value_.");
        lang("block.create_new_era.panel_gauge.tooltip.condition1", "When placed");
        lang("block.create_new_era.panel_gauge.tooltip.behaviour1", "Sits on the face you click: _floors, walls or ceilings_. Put several together to build a _control desk_.");
        lang("block.create_new_era.panel_gauge.tooltip.condition2", "Coming soon");
        lang("block.create_new_era.panel_gauge.tooltip.behaviour2", "Programs will move its _needle_.");
        lang("block.create_new_era.panel_keypad.tooltip.summary", "Twelve keys to _type numbers_ into a program.");
        lang("block.create_new_era.panel_keypad.tooltip.condition1", "When placed");
        lang("block.create_new_era.panel_keypad.tooltip.behaviour1", "Sits on the face you click: _floors, walls or ceilings_. Put several together to build a _control desk_.");
        lang("block.create_new_era.panel_keypad.tooltip.condition2", "Coming soon");
        lang("block.create_new_era.panel_keypad.tooltip.behaviour2", "Programs will get the _numbers_ typed in.");
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
