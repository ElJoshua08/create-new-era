package com.createnewera.computer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;

/** A computer's disk: a handful of named text files, kept in the order they were created. */
public final class ComputerFiles {
    public static final int MAX_FILE = 16384;
    public static final int MAX_TOTAL = 65536;
    public static final int MAX_FILES = 32;
    public static final String STARTUP = "startup.lua";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_\\-][A-Za-z0-9_.\\-/]{0,47}");

    private final Map<String, String> files = new LinkedHashMap<>();

    public ComputerFiles() {
        files.put(STARTUP, BrassComputerBlockEntity.DEFAULT_PROGRAM);
    }

    public static boolean isValidName(String name) {
        return NAME.matcher(name).matches() && !name.contains("..") && !name.endsWith("/");
    }

    public List<String> names() {
        return List.copyOf(files.keySet());
    }

    public Map<String, String> asMap() {
        return Map.copyOf(files);
    }

    public @Nullable String read(String name) {
        return files.get(name);
    }

    /** Finds a module the way require does: the exact name, or with .lua added. */
    public @Nullable String resolve(String module) {
        if (files.containsKey(module))
            return module;
        String withExtension = module.replace('.', '/') + ".lua";
        return files.containsKey(withExtension) ? withExtension : null;
    }

    /** @return null when written, otherwise why not */
    public @Nullable String write(String name, String content) {
        if (!isValidName(name))
            return "bad file name";
        if (content.length() > MAX_FILE)
            return "file too big";
        if (!files.containsKey(name) && files.size() >= MAX_FILES)
            return "disk full (" + MAX_FILES + " files)";
        int total = content.length();
        for (var e : files.entrySet())
            if (!e.getKey().equals(name))
                total += e.getValue().length();
        if (total > MAX_TOTAL)
            return "disk full";
        files.put(name, content);
        return null;
    }

    public boolean delete(String name) {
        return files.remove(name) != null;
    }

    /** Keeps the file's place in the list. */
    public boolean rename(String from, String to) {
        if (!files.containsKey(from) || files.containsKey(to) || !isValidName(to))
            return false;
        Map<String, String> copy = new LinkedHashMap<>(files);
        files.clear();
        copy.forEach((k, v) -> files.put(k.equals(from) ? to : k, v));
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        // CompoundTag isn't ordered, so keep the order separately
        tag.putString("Order", String.join("\n", files.keySet()));
        CompoundTag contents = new CompoundTag();
        files.forEach(contents::putString);
        tag.put("Files", contents);
        return tag;
    }

    public void load(CompoundTag tag) {
        files.clear();
        CompoundTag contents = tag.getCompound("Files");
        for (String name : tag.getString("Order").split("\n"))
            if (contents.contains(name))
                files.put(name, contents.getString(name));
        for (String name : contents.getAllKeys())
            files.putIfAbsent(name, contents.getString(name));
    }
}
