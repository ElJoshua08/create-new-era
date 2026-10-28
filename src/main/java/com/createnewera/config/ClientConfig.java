package com.createnewera.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public enum PixelShape { SQUARE, DISC }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<PixelShape> SCREEN_PIXEL_SHAPE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("screen");
        SCREEN_PIXEL_SHAPE = builder
                .comment("Shape of each flip pixel on brass screens. Purely visual, so each player picks their own.")
                .translation("create_new_era.configuration.screenPixelShape")
                .defineEnum("pixelShape", PixelShape.SQUARE);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {}
}
