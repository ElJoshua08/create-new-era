package com.createnewera.client.editor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.createnewera.CreateNewEra;

import net.neoforged.fml.loading.FMLPaths;

/** Where the player left the editor: window, splitters and side panel. Remembered across games. */
final class EditorLayout {
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("create_new_era-editor.properties");

    /** Window in GUI pixels; width 0 means "pick a default for this screen". */
    int x, y, width, height;
    boolean maximized;
    int consoleHeight = 48;
    int sideWidth = 104;
    /** FILES, REDSTONE, FLOWS, or empty when the side panel is closed. */
    String panel = "FILES";

    static EditorLayout load() {
        EditorLayout layout = new EditorLayout();
        if (!Files.exists(FILE))
            return layout;
        Properties p = new Properties();
        try (Reader in = Files.newBufferedReader(FILE)) {
            p.load(in);
            layout.x = Integer.parseInt(p.getProperty("x", "0"));
            layout.y = Integer.parseInt(p.getProperty("y", "0"));
            layout.width = Integer.parseInt(p.getProperty("width", "0"));
            layout.height = Integer.parseInt(p.getProperty("height", "0"));
            layout.maximized = Boolean.parseBoolean(p.getProperty("maximized", "false"));
            layout.consoleHeight = Integer.parseInt(p.getProperty("consoleHeight", "48"));
            layout.sideWidth = Integer.parseInt(p.getProperty("sideWidth", "104"));
            layout.panel = p.getProperty("panel", "FILES");
        } catch (IOException | NumberFormatException e) {
            CreateNewEra.LOGGER.warn("Couldn't read {}, using the default editor layout", FILE, e);
            return new EditorLayout();
        }
        return layout;
    }

    void save() {
        Properties p = new Properties();
        p.setProperty("x", Integer.toString(x));
        p.setProperty("y", Integer.toString(y));
        p.setProperty("width", Integer.toString(width));
        p.setProperty("height", Integer.toString(height));
        p.setProperty("maximized", Boolean.toString(maximized));
        p.setProperty("consoleHeight", Integer.toString(consoleHeight));
        p.setProperty("sideWidth", Integer.toString(sideWidth));
        p.setProperty("panel", panel);
        try (Writer out = Files.newBufferedWriter(FILE)) {
            p.store(out, "Create: New Era computer editor layout");
        } catch (IOException e) {
            CreateNewEra.LOGGER.warn("Couldn't save the editor layout to {}", FILE, e);
        }
    }
}
