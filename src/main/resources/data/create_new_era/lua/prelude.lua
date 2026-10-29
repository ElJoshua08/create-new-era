-- Runs once before every program. Turns the Java side (passed in as `native`)
-- into the public API, then lets it go out of scope so programs can't reach it.
-- Returns the kernel: the small scheduler the program runs inside.
local native = ...

local select, tostring, type, error, ipairs, next = select, tostring, type, error, ipairs, next
local concat, remove, pack, unpack = table.concat, table.remove, table.pack, table.unpack
local load = load
local ceil, max = math.ceil, math.max
local create, resume, status, yield = coroutine.create, coroutine.resume, coroutine.status, coroutine.yield

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

-- ---- the kernel: every running piece of the program is a task ----

local tasks = {}     -- { co, wake (game tick), args }
local handlers = {}  -- device name -> list of { fn, device }

local function spawn(fn, ...)
    tasks[#tasks + 1] = { co = create(fn), wake = 0, args = pack(...) }
end

-- ---- the bus: devices on this computer's network ----

local nativeBus = native.bus
bus = {}

-- every device the computer can reach, nearest first: { name, type, distance, readable, writable }
function bus.list()
    return nativeBus.list()
end

-- a device by name; its functions depend on its ports (output: read, input: give orders)
function bus.get(name)
    if type(name) ~= "string" then
        error("bad argument #1 to 'get' (string expected, got " .. type(name) .. ")", 2)
    end
    local info = nativeBus.info(name)
    local device = { name = info.name, type = info.type }
    for _, method in ipairs(info.methods) do
        device[method] = function(...)
            -- works as device.getSpeed() and as device:getSpeed()
            if (...) == device then
                return nativeBus.call(name, method, select(2, ...))
            end
            return nativeBus.call(name, method, ...)
        end
    end
    -- runs fn(device) every time anything about the device changes, until the program stops
    function device.onChange(a, b)
        local fn = a == device and b or a
        if type(fn) ~= "function" then
            error("bad argument #1 to 'onChange' (function expected, got " .. type(fn) .. ")", 2)
        end
        local list = handlers[name]
        if not list then
            list = {}
            handlers[name] = list
            nativeBus.watch(name)
        end
        list[#list + 1] = { fn = fn, device = device }
    end
    -- a panel button: runs fn(device) each time it's pressed down
    if device.isPressed then
        function device.onPress(a, b)
            local fn = a == device and b or a
            if type(fn) ~= "function" then
                error("bad argument #1 to 'onPress' (function expected, got " .. type(fn) .. ")", 2)
            end
            device.onChange(function(d)
                if d.isPressed() then fn(d) end
            end)
        end
    end
    -- a keypad: runs fn(key, device) for every key pressed, key being "0" to "9", "*" or "#"
    if device.getKey then
        function device.onKey(a, b)
            local fn = a == device and b or a
            if type(fn) ~= "function" then
                error("bad argument #1 to 'onKey' (function expected, got " .. type(fn) .. ")", 2)
            end
            device.onChange(function(d)
                local key = d.getKey()
                if key then fn(key, d) end
            end)
        end
    end
    return device
end

-- Runs the program until it has nothing left to do: no tasks waiting, no handlers listening.
local function kernel(main)
    spawn(main)
    local changed = {}
    while true do
        for _, name in ipairs(changed) do
            for _, handler in ipairs(handlers[name] or {}) do
                spawn(handler.fn, handler.device)
            end
        end

        local now = native.now()
        local i = 1
        while i <= #tasks do
            local task = tasks[i]
            if task.wake <= now then
                local args = task.args or { n = 0 }
                task.args = nil
                local ok, what, arg = resume(task.co, unpack(args, 1, args.n))
                -- handed back rather than rethrown, so the console shows the program's error, not ours
                if not ok then return "error", what end
                if status(task.co) == "dead" then
                    remove(tasks, i)
                else
                    -- sleep(s) waits s seconds of game time; even sleep(0) gives the world a tick
                    local ticks = what == "sleep" and ceil((arg or 0) * 20) or 1
                    task.wake = now + max(1, ticks)
                    i = i + 1
                end
            else
                i = i + 1
            end
        end

        if #tasks == 0 and next(handlers) == nil then return end
        local wake
        for _, task in ipairs(tasks) do
            if not wake or task.wake < wake then wake = task.wake end
        end
        changed = yield("wait", wake) or {}
    end
end

return kernel
