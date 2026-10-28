package com.createnewera.computer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.jetbrains.annotations.Nullable;
import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaThread;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.ValueFactory;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.function.LuaFunction;
import org.squiddev.cobalt.function.RegisteredFunction;
import org.squiddev.cobalt.interrupt.InterruptAction;
import org.squiddev.cobalt.lib.Bit32Lib;
import org.squiddev.cobalt.lib.CoreLibraries;

/**
 * One running program. It lives on the server thread and gets a slice of time each tick:
 * when the slice runs out the watchdog suspends it mid-instruction and the next tick picks
 * up where it stopped, so a busy loop slows its own program down instead of the server.
 */
public final class LuaComputer {
    /** What the program can touch in the world. */
    public interface Host {
        void print(String line);

        int getInput(ComputerSide side);

        int getOutput(ComputerSide side);

        void setOutput(ComputerSide side, int strength);

        /** For require: the file a module name resolves to, as {name, contents}, or null. */
        @Nullable String[] readFile(String module);

        /** Rotation speed in RPM, negative when turning the other way. */
        float speed();

        /** Seconds since the program started, in game time. */
        double clock();
    }

    public enum Status { RUNNING, SLEEPING, FINISHED }

    private static final String PRELUDE = loadPrelude();

    private final LuaState state;
    private final LuaThread program;
    private long deadline;
    private long wakeTick;
    // null resumes a program the watchdog suspended; NONE resumes after a yield
    private @Nullable Varargs resumeArgs = Constants.NONE;

    private LuaComputer(LuaState state, LuaThread program) {
        this.state = state;
        this.program = program;
    }

    /**
     * Compiles and prepares a program; it starts running on the first {@link #tick}.
     *
     * @param name file name, shown in error messages ("startup.lua:3: ...")
     */
    public static LuaComputer start(String source, String name, Host host) throws LuaError {
        var holder = new LuaComputer[1];
        LuaState state = LuaState.builder()
                .interruptHandler(() -> System.nanoTime() > holder[0].deadline ? InterruptAction.SUSPEND : InterruptAction.CONTINUE)
                .build();
        CoreLibraries.standardGlobals(state);
        Bit32Lib.add(state);

        LuaTable globals = state.globals();
        LuaFunction prelude = compile(state, PRELUDE, "=prelude", globals);
        LuaThread.runMain(state, prelude, native_(host));

        LuaFunction main = compile(state, source, "=" + name, globals);
        holder[0] = new LuaComputer(state, new LuaThread(state, main));
        return holder[0];
    }

    /**
     * Runs the program for at most {@code budgetNanos}.
     *
     * @throws LuaError the program failed; the message already says where
     */
    public Status tick(long gameTime, long budgetNanos) throws LuaError {
        if (gameTime < wakeTick)
            return Status.SLEEPING;

        deadline = System.nanoTime() + budgetNanos;
        Varargs result;
        LuaWatchdog.watch(state, deadline);
        try {
            result = LuaThread.run(program, resumeArgs);
        } finally {
            LuaWatchdog.release();
        }

        if (result == null) {
            resumeArgs = null;
            return Status.RUNNING;
        }
        if (program.getStatus() == LuaThread.Status.DEAD)
            return Status.FINISHED;

        resumeArgs = Constants.NONE;
        if (result.first().isString() && "sleep".equals(result.first().toString())) {
            double seconds = Math.max(0, result.arg(2).toDouble());
            // even sleep(0) gives the rest of the world a tick
            wakeTick = gameTime + Math.max(1, (long) Math.ceil(seconds * 20));
            return Status.SLEEPING;
        }
        return Status.RUNNING;
    }

    private static LuaFunction compile(LuaState state, String source, String name, LuaTable env) throws LuaError {
        try {
            return LoadState.load(state, new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), name, env);
        } catch (CompileException e) {
            throw new LuaError(e.getMessage());
        }
    }

    /** Java side of the API; the prelude wraps it into print, sleep, os and redstone, then drops it. */
    private static LuaTable native_(Host host) {
        LuaTable redstone = RegisteredFunction.bind(new RegisteredFunction[] {
                RegisteredFunction.of("getInput", (state, side) -> ValueFactory.valueOf(host.getInput(side(side)) > 0)),
                RegisteredFunction.of("getAnalogInput", (state, side) -> ValueFactory.valueOf(host.getInput(side(side)))),
                RegisteredFunction.of("getOutput", (state, side) -> ValueFactory.valueOf(host.getOutput(side(side)) > 0)),
                RegisteredFunction.of("getAnalogOutput", (state, side) -> ValueFactory.valueOf(host.getOutput(side(side)))),
                RegisteredFunction.of("setOutput", (state, side, on) -> {
                    host.setOutput(side(side), on.checkBoolean() ? 15 : 0);
                    return Constants.NIL;
                }),
                RegisteredFunction.of("setAnalogOutput", (state, side, strength) -> {
                    int value = strength.checkInteger();
                    if (value < 0 || value > 15)
                        throw new LuaError("bad argument #2 (strength must be between 0 and 15, got " + value + ")");
                    host.setOutput(side(side), value);
                    return Constants.NIL;
                }),
                RegisteredFunction.of("getSides", state -> {
                    LuaTable sides = new LuaTable();
                    for (int i = 0; i < ComputerSide.values().length; i++)
                        sides.rawset(i + 1, ValueFactory.valueOf(ComputerSide.values()[i].luaName()));
                    return sides;
                }),
        });
        LuaTable table = RegisteredFunction.bind(new RegisteredFunction[] {
                RegisteredFunction.of("write", (state, line) -> {
                    host.print(line.checkString());
                    return Constants.NIL;
                }),
                RegisteredFunction.of("clock", state -> ValueFactory.valueOf(host.clock())),
                RegisteredFunction.of("speed", state -> ValueFactory.valueOf(host.speed())),
                RegisteredFunction.ofV("read", (state, args) -> {
                    String[] file = host.readFile(args.arg(1).checkString());
                    return file == null ? Constants.NIL
                            : ValueFactory.varargsOf(ValueFactory.valueOf(file[1]), ValueFactory.valueOf(file[0]));
                }),
        });
        table.rawset("redstone", redstone);
        return table;
    }

    private static ComputerSide side(LuaValue value) throws LuaError {
        String name = value.checkString();
        ComputerSide side = ComputerSide.byLuaName(name);
        if (side == null)
            throw new LuaError("bad argument #1 (unknown side '" + name + "', expected front, back, left, right, top or bottom)");
        return side;
    }

    private static String loadPrelude() {
        try (InputStream in = LuaComputer.class.getResourceAsStream("/data/create_new_era/lua/prelude.lua")) {
            if (in == null)
                throw new IllegalStateException("prelude.lua missing from the mod jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
