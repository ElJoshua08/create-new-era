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
 * Run through Gradle: runClient records the baseline, ./gradlew hotswap swaps.
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
        try {
            Map<ReferenceType, byte[]> redefine = new HashMap<>();
            int notLoaded = 0;
            for (var e : changed.entrySet()) {
                List<ReferenceType> loaded = vm.classesByName(e.getKey());
                if (loaded.isEmpty())
                    notLoaded++; // the game will load the new version from disk when it first needs it
                for (ReferenceType type : loaded)
                    redefine.put(type, e.getValue());
            }
            vm.redefineClasses(redefine);
            System.out.printf("hotswap: reloaded %d classes (%d not loaded yet)%n", redefine.size(), notLoaded);
            for (String name : changed.keySet().stream().sorted().toList())
                System.out.println("  " + name);
        } finally {
            vm.dispose();
        }
        save(state, now);
    }

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
