package com.createnewera.client;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

import com.mojang.logging.LogUtils;

import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Dev builds only: photographs Ponder scenes so they can be checked without anyone at the keyboard.
 * Write "item scene time time ..." lines to run/dev-ponder.txt; each time in the scene becomes
 * run/screenshots/ponder_<item>_<scene>_<time>.png.
 */
public final class DevPonderShots {
    private static final Path REQUEST = Path.of("dev-ponder.txt");
    private record Shot(String item, int scene, int time) {}

    private static final Deque<Shot> queue = new ArrayDeque<>();
    private static Shot current;
    private static int wait;
    private static String opened;

    public static void register(IEventBus game) {
        if (!FMLEnvironment.production)
            game.addListener(RenderFrameEvent.Post.class, e -> frame());
    }

    private static void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (current == null) {
            if (queue.isEmpty() && !read())
                return;
            current = queue.poll();
            wait = 0;
        }
        if (!current.item.equals(opened) || !(mc.screen instanceof PonderUI)) {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(current.item));
            PonderUI ui = PonderUI.of(new ItemStack(item));
            ScreenOpener.open(ui);
            opened = current.item;
            wait = 0;
            return;
        }
        PonderUI ui = (PonderUI) mc.screen;
        wait++;
        if (wait == 5) {
            try {
                var index = PonderUI.class.getDeclaredField("index");
                index.setAccessible(true);
                var lazy = PonderUI.class.getDeclaredField("lazyIndex");
                lazy.setAccessible(true);
                index.setInt(ui, current.scene);
                ((net.createmod.catnip.animation.LerpedFloat) lazy.get(ui)).startWithValue(current.scene);
            } catch (ReflectiveOperationException e) {
                LogUtils.getLogger().warn("dev ponder: {}", e.toString());
            }
            ui.getActiveScene().begin();
            ui.seekToTime(current.time);
        }
        if (wait == 40) {
            String name = "ponder_" + current.item.replaceAll(".*:", "") + "_" + current.scene + "_" + current.time + ".png";
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), c -> {});
            current = null;
            if (queue.isEmpty()) {
                opened = null;
                mc.setScreen(null);
            }
        }
    }

    private static boolean read() {
        if (!Files.exists(REQUEST))
            return false;
        try {
            for (String line : Files.readAllLines(REQUEST)) {
                String[] p = line.trim().split("\\s+");
                if (p.length < 3)
                    continue;
                for (int i = 2; i < p.length; i++)
                    queue.add(new Shot(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[i])));
            }
            Files.delete(REQUEST);
        } catch (IOException | NumberFormatException e) {
            LogUtils.getLogger().warn("dev ponder: {}", e.toString());
        }
        return !queue.isEmpty();
    }

    private DevPonderShots() {}
}
