#!/usr/bin/env bash
# The dev client, kept running while code changes:
#   tools/dev/game.sh start     start it (fullscreen on dev_monitor) unless it's already up
#   tools/dev/game.sh reload    push changed code into it; restarts it if a change can't go in live
#   tools/dev/game.sh restart   stop and start again (new blocks, items or payloads need this)
#   tools/dev/game.sh stop      close it; the world is saved on the way out
#   tools/dev/game.sh status
set -euo pipefail
cd "$(dirname "$0")/../.."

PORT=5005
LOG=build/dev-client.log
prop() { sed -n "s/^$1=//p" gradle.properties | tail -1; }

client_pid() { ss -ltnp 2>/dev/null | grep "127.0.0.1:$PORT " | grep -o 'pid=[0-9]*' | head -1 | cut -d= -f2; }

hyprland() { [ -n "$(prop dev_monitor)" ] && command -v hyprctl >/dev/null; }

# once the window is up: onto dev_monitor, fullscreen (done to the window itself, since Hyprland
# rules can't make an XWayland window fullscreen)
place_window() {
    hyprland || return 0
    local address monitor; monitor=$(prop dev_monitor)
    address=$(hyprctl clients -j | python3 -c "import json,sys; print(next((c['address'] for c in json.load(sys.stdin) if c['class'].startswith('Minecraft')), ''))")
    [ -n "$address" ] || return 1
    hyprctl eval "hl.dispatch(hl.dsp.window.fullscreen_state({ internal = 0, client = 0, window = 'address:$address' }))" >/dev/null
    hyprctl eval "hl.dispatch(hl.dsp.window.move({ monitor = '$monitor', window = 'address:$address' }))" >/dev/null
    hyprctl eval "hl.dispatch(hl.dsp.window.fullscreen_state({ internal = 2, client = 2, window = 'address:$address' }))" >/dev/null
}

launching() { pgrep -f "gradle-wrapper.jar runClient" >/dev/null; }

start() {
    if [ -n "$(client_pid)" ] && ! launching; then echo "game: already running"; return; fi
    local since; since=$(date +%s)
    if launching; then
        echo "game: already starting, waiting for it"
    else
        mkdir -p build
        nohup ./gradlew runClient >"$LOG" 2>&1 &
        echo "game: starting (log in $LOG)"
        sleep 2
    fi
    local placed=0
    for _ in $(seq 1 240); do
        [ $placed = 1 ] || { place_window && placed=1; } || true
        # up once the player is in the world (runClient opens dev_world straight away)
        if [ -f run/logs/latest.log ] && [ "$(stat -c %Y run/logs/latest.log)" -ge "$since" ] \
                && grep -q "joined the game" run/logs/latest.log; then
            place_window || true
            echo "game: up, in the world"; return
        fi
        if ! launching && [ -z "$(client_pid)" ]; then
            echo "game: failed to start, see $LOG"; tail -20 "$LOG"; exit 1
        fi
        sleep 1
    done
    echo "game: still starting after 4 minutes, see $LOG"
}

stop() {
    local pid; pid=$(client_pid)
    if [ -z "$pid" ]; then echo "game: not running"; return; fi
    kill "$pid"
    # the gradle run that launched it has to finish too, or start would take it for a launch
    for _ in $(seq 1 60); do [ -z "$(client_pid)" ] && ! launching && { echo "game: stopped"; return; }; sleep 1; done
    echo "game: didn't stop in a minute"; exit 1
}

reload() {
    if [ -z "$(client_pid)" ]; then start; return; fi
    local out code
    set +e
    out=$(./gradlew hotswap -q 2>&1)
    code=$?
    set -e
    echo "$out" | grep -v "^$" | grep -v "Deprecated Gradle\|--warning-mode\|gradle.org" || true
    if [ $code -eq 0 ]; then
        echo "game: reloaded (resources too: F3+T in game for textures, models and lang)"
    elif echo "$out" | grep -q "a running game can't take"; then
        echo "game: that change needs a restart"; stop; start
    else
        echo "game: hotswap failed (doesn't compile?), the game is untouched"; exit 1
    fi
}

case "${1:-status}" in
    start) start ;;
    stop) stop ;;
    restart) stop; start ;;
    reload) reload ;;
    status) [ -n "$(client_pid)" ] && echo "game: running (pid $(client_pid))" || echo "game: not running" ;;
    *) sed -n '2,7p' "$0"; exit 2 ;;
esac
