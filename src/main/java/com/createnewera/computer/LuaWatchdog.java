package com.createnewera.computer;

import java.util.concurrent.locks.LockSupport;

import org.squiddev.cobalt.LuaState;

/**
 * Cobalt only checks for interrupts when another thread asks it to. Programs run one at a
 * time on the server thread, so a single daemon thread watching a single slot is enough.
 */
final class LuaWatchdog {
    private static volatile LuaState current;
    private static volatile long deadline;

    static {
        Thread thread = new Thread(LuaWatchdog::run, "Create: New Era Lua watchdog");
        thread.setDaemon(true);
        thread.start();
    }

    static void watch(LuaState state, long deadlineNanos) {
        deadline = deadlineNanos;
        current = state;
    }

    static void release() {
        current = null;
    }

    private static void run() {
        while (true) {
            LockSupport.parkNanos(100_000);
            LuaState state = current;
            if (state != null && System.nanoTime() > deadline)
                state.interrupt();
        }
    }

    private LuaWatchdog() {}
}
