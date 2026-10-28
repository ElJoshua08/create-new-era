package com.createnewera.bus;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.ValueFactory;

/** Plain Java values (what device drivers return) as Lua values. */
public final class LuaValues {
    public static LuaValue of(@Nullable Object value) {
        if (value == null)
            return Constants.NIL;
        if (value instanceof Boolean b)
            return ValueFactory.valueOf(b);
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte)
            return ValueFactory.valueOf(((Number) value).intValue());
        if (value instanceof Number n)
            return ValueFactory.valueOf(n.doubleValue());
        if (value instanceof String s)
            return ValueFactory.valueOf(s);
        if (value instanceof List<?> list) {
            LuaTable table = new LuaTable();
            for (int i = 0; i < list.size(); i++)
                table.rawset(i + 1, of(list.get(i)));
            return table;
        }
        if (value instanceof Map<?, ?> map) {
            LuaTable table = new LuaTable();
            map.forEach((k, v) -> table.rawset(String.valueOf(k), of(v)));
            return table;
        }
        return ValueFactory.valueOf(value.toString());
    }

    private LuaValues() {}
}
