package com.createnewera.client.control;

import java.lang.reflect.Method;
import java.util.Map;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/**
 * Glow that shader packs see. A pack decides what shines from the ids its block.properties
 * gives blocks, and it lists no modded ones, so a lit lamp or nixie came out as dull as stone,
 * with no bloom. Iris stamps every vertex a block entity draws with that block's id as it's
 * written; drawing the lit parts stamped as a glowing sign's text, which packs do make
 * emissive (Complementary: full-bright sign text gets emission), makes them shine there too,
 * and only them. Iris is reached by reflection, so nothing changes without it.
 */
public final class ShaderGlow {
    private static boolean looked;
    private static Object captured, settings, api;
    private static Method getCurrent, setCurrent, blockStateIds, packInUse;
    private static final BlockState GLOWING = Blocks.OAK_SIGN.defaultBlockState();

    /** Runs the drawing of a lit part, stamped so a shader pack makes it glow. */
    public static void draw(Runnable lit) {
        int id = glowId();
        if (id < 0) {
            lit.run();
            return;
        }
        try {
            int was = (int) getCurrent.invoke(captured);
            setCurrent.invoke(captured, id);
            try {
                lit.run();
            } finally {
                setCurrent.invoke(captured, was);
            }
        } catch (ReflectiveOperationException e) {
            looked = true;
            captured = null; // a different Iris: stop trying, draw plainly
            lit.run();
        }
    }

    /** The id the pack gives a glowing sign, or -1 without Iris, a shader pack, or such an id. */
    private static int glowId() {
        if (!looked) {
            looked = true;
            if (!ModList.get().isLoaded("iris"))
                return -1;
            try {
                Class<?> state = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState");
                captured = state.getField("INSTANCE").get(null);
                getCurrent = state.getMethod("getCurrentRenderedBlockEntity");
                setCurrent = state.getMethod("setCurrentBlockEntity", int.class);
                Class<?> rendering = Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings");
                settings = rendering.getField("INSTANCE").get(null);
                blockStateIds = rendering.getMethod("getBlockStateIds");
                Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = apiClass.getMethod("getInstance").invoke(null);
                packInUse = apiClass.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | RuntimeException e) {
                captured = null;
            }
        }
        if (captured == null)
            return -1;
        try {
            if (!(boolean) packInUse.invoke(api) || !(blockStateIds.invoke(settings) instanceof Map<?, ?> ids))
                return -1;
            Object id = ids.get(GLOWING);
            return id instanceof Integer i ? i : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    private ShaderGlow() {}
}
