package com.createnewera.registry;

import static com.createnewera.CreateNewEra.REGISTRATE;

/** English strings that don't belong to a single registry entry. Spanish lives in es_es.json. */
public final class ModLang {
    public static void register() {
        lang("itemGroup.create_new_era.main", "Create: New Era");

        lang("create_new_era.configuration.title", "Create: New Era");
        lang("create_new_era.configuration.screen", "Screens");
        lang("create_new_era.configuration.screenPixelShape", "Pixel shape");
        lang("create_new_era.configuration.screenPixelShape.tooltip", "Flip pixels on brass screens: square plates or round discs.");

        tooltip("block", "brass_computer", "Runs _Lua_ programs that read and control every machine on its _data bus_.");
        tooltip("block", "brass_screen", "Joins with its neighbours into one big display made of _flip pixels_ that turn when they change.");
        tooltip("block", "universal_adapter", "Attach to any block to expose what it can do, _kinetics_, _items_ or _fluids_, on the data bus.");
        tooltip("block", "bearing_servo", "Turns an attached _contraption_ to an exact angle on command.");
        tooltip("item", "control_plate", "Andesite base for every _panel control_.");
        tooltip("block", "panel_button", "A momentary _push button_ for programs to react to.");
        tooltip("block", "panel_switch", "An _on/off_ lever a program can read.");
        tooltip("block", "panel_knob", "A rotary knob that dials in a _value_.");
        tooltip("block", "panel_slider", "A fader for a smooth _0 to 100%_ value.");
        tooltip("block", "panel_lamp", "An indicator light a program can switch and _colour_.");
        tooltip("block", "nixie_digit", "One glowing _digit_; place several in a row to show whole numbers.");
        tooltip("block", "panel_gauge", "A needle dial that shows any _value_.");
        tooltip("block", "panel_keypad", "Twelve keys to _type numbers_ into a program.");
    }

    private static void lang(String key, String value) {
        REGISTRATE.addRawLang(key, value);
    }

    private static void tooltip(String type, String name, String summary) {
        REGISTRATE.addRawLang(type + ".create_new_era." + name + ".tooltip.summary", summary);
    }

    private ModLang() {}
}
