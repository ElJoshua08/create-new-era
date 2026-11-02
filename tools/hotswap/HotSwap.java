import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;

/**
 * Pushes recompiled classes into a running game over JDWP.
 *
 * <pre>java HotSwap.java baseline CLASSES STATE        record what the game started with
 * java HotSwap.java swap CLASSES STATE PORT        redefine every class that changed since</pre>
 *
 * Run through Gradle: runClient records the baseline, ./gradlew hotswap swaps. Exits with 3
 * when a class changed in a way the running game can't take.
 */
public class HotSwap {
    public static void main(String[] args) throws Exception {
        Path classes = Path.of(args[1]), state = Path.of(args[2]).toAbsolutePath();
        Map<String, String> now = hashes(classes);
        if (args[0].equals("baseline")) {
            save(state, now);
            return;
        }

        Map<String, String> before = load(state);
        Map<String, byte[]> changed = new HashMap<>();
        for (var e : now.entrySet())
            if (!e.getValue().equals(before.get(e.getKey())))
                changed.put(e.getKey(), Files.readAllBytes(classes.resolve(e.getKey().replace('.', '/') + ".class")));
        if (changed.isEmpty()) {
            System.out.println("hotswap: nothing changed");
            return;
        }

        VirtualMachine vm = attach(Integer.parseInt(args[3]));
        Map<String, String> failed = new HashMap<>();
        int reloaded = 0, notLoaded = 0;
        try {
            Map<ReferenceType, byte[]> redefine = new HashMap<>();
            Map<ReferenceType, String> names = new HashMap<>();
            for (var e : changed.entrySet()) {
                List<ReferenceType> loaded = vm.classesByName(e.getKey());
                if (loaded.isEmpty())
                    notLoaded++; // the game will load the new version from disk when it first needs it
                for (ReferenceType type : loaded) {
                    // the VM takes a new static field but never runs its initialiser, so it would sit
                    // at null and crash the game the first time it's used
                    List<String> had = type.fields().stream().filter(f -> f.isStatic()).map(f -> f.name()).toList();
                    List<String> added = staticFields(e.getValue()).stream().filter(f -> !had.contains(f)).toList();
                    if (!added.isEmpty()) {
                        failed.put(e.getKey(), "new static field " + String.join(", ", added));
                        continue;
                    }
                    redefine.put(type, e.getValue());
                    names.put(type, e.getKey());
                }
            }
            try {
                vm.redefineClasses(redefine);
                reloaded = redefine.size();
            } catch (RuntimeException | LinkageError all) {
                // one class the VM can't take (say, a new superclass) fails the whole batch: go one by one
                for (var e : redefine.entrySet()) {
                    try {
                        vm.redefineClasses(Map.of(e.getKey(), e.getValue()));
                        reloaded++;
                    } catch (RuntimeException | LinkageError one) {
                        failed.put(names.get(e.getKey()), one.getMessage());
                    }
                }
            }
        } finally {
            vm.dispose();
        }

        System.out.printf("hotswap: reloaded %d classes (%d not loaded yet)%n", reloaded, notLoaded);
        for (String name : changed.keySet().stream().sorted().toList())
            System.out.println((failed.containsKey(name) ? "  ! " : "    ") + name);
        if (!failed.isEmpty()) {
            System.out.println();
            System.out.println("hotswap: " + failed.size() + " class(es) changed in a way a running game can't take,");
            System.out.println("for example a new superclass or a new block. Restart the game for those:");
            failed.forEach((name, why) -> System.out.println("  " + name + ": " + why));
            // leave them out of the state, so they're offered again until the game restarts
            failed.keySet().forEach(name -> now.put(name, before.getOrDefault(name, "")));
        }
        save(state, now);
        if (!failed.isEmpty())
            System.exit(RESTART_NEEDED);
    }

    /** Names of the static fields a class file declares. */
    static List<String> staticFields(byte[] bytes) throws IOException {
        var in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes));
        in.skipNBytes(8);
        int count = in.readUnsignedShort();
        String[] utf8 = new String[count];
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[i] = in.readUTF();
                case 7, 8, 16, 19, 20 -> in.skipNBytes(2);
                case 15 -> in.skipNBytes(3);
                case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipNBytes(4);
                case 5, 6 -> {
                    in.skipNBytes(8);
                    i++;
                }
                default -> throw new IOException("constant pool tag " + tag);
            }
        }
        in.skipNBytes(6);
        in.skipNBytes(2L * in.readUnsignedShort());
        List<String> out = new java.util.ArrayList<>();
        int fields = in.readUnsignedShort();
        for (int f = 0; f < fields; f++) {
            int access = in.readUnsignedShort();
            String name = utf8[in.readUnsignedShort()];
            in.skipNBytes(2);
            if ((access & 0x0008) != 0)
                out.add(name);
            int attributes = in.readUnsignedShort();
            for (int a = 0; a < attributes; a++) {
                in.skipNBytes(2);
                in.skipNBytes(in.readInt());
            }
        }
        return out;
    }

    /** Exit code when some classes need a restart, so a script can restart the game for them. */
    private static final int RESTART_NEEDED = 3;

    private static VirtualMachine attach(int port) throws Exception {
        AttachingConnector socket = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(c -> c.name().equals("com.sun.jdi.SocketAttach"))
                .findFirst().orElseThrow();
        Map<String, Connector.Argument> arguments = socket.defaultArguments();
        arguments.get("hostname").setValue("127.0.0.1");
        arguments.get("port").setValue(Integer.toString(port));
        try {
            return socket.attach(arguments);
        } catch (IOException e) {
            throw new IllegalStateException("no game listening on port " + port + ": start it with ./gradlew runClient", e);
        }
    }

    private static Map<String, String> hashes(Path root) throws Exception {
        Map<String, String> out = new HashMap<>();
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                String rel = root.relativize(file).toString();
                String name = rel.substring(0, rel.length() - ".class".length()).replace(java.io.File.separatorChar, '.');
                out.put(name, HexFormat.of().formatHex(sha.digest(Files.readAllBytes(file))));
            }
        }
        return out;
    }

    private static void save(Path state, Map<String, String> hashes) throws IOException {
        Properties p = new Properties();
        p.putAll(hashes);
        Files.createDirectories(state.getParent());
        try (var out = Files.newOutputStream(state)) {
            p.store(out, null);
        }
    }

    private static Map<String, String> load(Path state) throws IOException {
        Properties p = new Properties();
        if (Files.exists(state))
            try (var in = Files.newInputStream(state)) {
                p.load(in);
            }
        Map<String, String> out = new HashMap<>();
        p.forEach((k, v) -> out.put((String) k, (String) v));
        return out;
    }
}
