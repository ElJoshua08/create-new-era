-- Runs once before every program. Turns the Java side (passed in as `native`)
-- into the public API, then lets it go out of scope so programs can't reach it.
local native = ...

local select, tostring, type, error = select, tostring, type, error
local concat = table.concat
local yield = coroutine.yield

function print(...)
    local parts = {}
    for i = 1, select("#", ...) do
        parts[i] = tostring((select(i, ...)))
    end
    native.write(concat(parts, "\t"))
end

function sleep(seconds)
    if seconds == nil then seconds = 0 end
    if type(seconds) ~= "number" then
        error("bad argument #1 to 'sleep' (number expected, got " .. type(seconds) .. ")", 2)
    end
    yield("sleep", seconds)
end

os = {
    clock = native.clock,
    sleep = sleep,
}

redstone = native.redstone
rs = redstone
