-- Runs once before every program. Turns the Java side (passed in as `native`)
-- into the public API, then lets it go out of scope so programs can't reach it.
local native = ...

local select, tostring, type, error = select, tostring, type, error
local concat = table.concat
local load = load
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

-- require("lib") runs lib.lua from this computer's disk once and hands back what it returned
local loaded = {}
function require(module)
    if type(module) ~= "string" then
        error("bad argument #1 to 'require' (string expected, got " .. type(module) .. ")", 2)
    end
    local source, file = native.read(module)
    if not source then error("module '" .. module .. "' not found on this computer", 2) end
    -- cached by file, so "lib.pid" and "lib/pid.lua" are the same module
    if loaded[file] ~= nil then return loaded[file] end
    local chunk, err = load(source, "=" .. file)
    if not chunk then error(err, 0) end
    local result = chunk(module)
    if result == nil then result = true end
    loaded[file] = result
    return result
end

os = {
    clock = native.clock,
    -- RPM the computer turns at; the faster it turns, the faster programs run
    speed = native.speed,
    sleep = sleep,
}

redstone = native.redstone
rs = redstone
