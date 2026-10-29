-- Runs once before every program. Turns the Java side (passed in as `native`)
-- into the public API, then lets it go out of scope so programs can't reach it.
-- Returns the kernel: the small scheduler the program runs inside.
local native = ...

local select, tostring, type, error, ipairs, next = select, tostring, type, error, ipairs, next
local concat, remove, pack, unpack = table.concat, table.remove, table.pack, table.unpack
local load = load
local ceil, max = math.ceil, math.max
local create, resume, status, yield = coroutine.create, coroutine.resume, coroutine.status, coroutine.yield

-- a value as print shows it: tables with what's in them ({speed = 64, items = {...}}), not their address
local function show(value, depth, seen)
    local kind = type(value)
    if kind == "string" then return depth > 0 and string.format("%q", value) or value end
    if kind == "function" then return "<function>" end
    if kind ~= "table" then return tostring(value) end
    if seen[value] or depth > 3 then return "{...}" end
    seen[value] = true
    local parts, n = {}, #value
    for i = 1, n do parts[#parts + 1] = show(value[i], depth + 1, seen) end
    local keys = {}
    for k in next, value do
        if not (type(k) == "number" and k >= 1 and k <= n and k % 1 == 0) then keys[#keys + 1] = k end
    end
    table.sort(keys, function(a, b) return tostring(a) < tostring(b) end)
    for _, k in ipairs(keys) do
        local key = type(k) == "string" and k:match("^[%a_][%w_]*$") and k or "[" .. show(k, depth + 1, seen) .. "]"
        parts[#parts + 1] = key .. " = " .. show(value[k], depth + 1, seen)
    end
    seen[value] = nil
    return "{" .. concat(parts, ", ") .. "}"
end

function print(...)
    local parts = {}
    for i = 1, select("#", ...) do
        parts[i] = show((select(i, ...)), 0, {})
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
    -- runs fn(value, device) every time anything about the device changes, until the program
    -- stops; value is what device.read() gives: a slider's position, a machine's whole state...
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
            device.onChange(function(pressed, d)
                if pressed then fn(d) end
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
            device.onChange(function(key, d)
                if key then fn(key, d) end
            end)
        end
    end
    -- a typewriter: runs fn(key, device) as each key goes down, or comes up
    if device.getPressedKeys then
        local function watchKeys(fn, down)
            local before = {}
            for _, k in ipairs(device.getPressedKeys()) do before[k] = true end
            device.onChange(function(_, d)
                local now = {}
                for _, k in ipairs(d.getPressedKeys()) do now[k] = true end
                local was, is = down and before or now, down and now or before
                for k in next, is do
                    if not was[k] then fn(k, d) end
                end
                before = now
            end)
        end
        function device.onKeyDown(a, b)
            local fn = a == device and b or a
            if type(fn) ~= "function" then
                error("bad argument #1 to 'onKeyDown' (function expected, got " .. type(fn) .. ")", 2)
            end
            watchKeys(fn, true)
        end
        function device.onKeyUp(a, b)
            local fn = a == device and b or a
            if type(fn) ~= "function" then
                error("bad argument #1 to 'onKeyUp' (function expected, got " .. type(fn) .. ")", 2)
            end
            watchKeys(fn, false)
        end
    end
    -- a chat log: runs fn(message, device) for every message it hears: {player, text, time}
    if device.getLast then
        function device.onMessage(a, b)
            local fn = a == device and b or a
            if type(fn) ~= "function" then
                error("bad argument #1 to 'onMessage' (function expected, got " .. type(fn) .. ")", 2)
            end
            device.onChange(function(_, d)
                local message = d.getLast()
                if message then fn(message, d) end
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
            -- what changed, read once for every function listening (nil if it can't be read)
            local ok, value = pcall(nativeBus.call, name, "read")
            for _, handler in ipairs(handlers[name] or {}) do
                spawn(handler.fn, ok and value or nil, handler.device)
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
