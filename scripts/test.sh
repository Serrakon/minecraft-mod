#!/usr/bin/env bash
# Клиентские игровые тесты (src/gametest): запускают игру, создают мир, гоняют сценарии.
# Окно игры откроется на время теста. Скриншоты — build/run/clientGameTest/screenshots.
source "$(dirname "$0")/lib.sh"

gradle runClientGameTest "$@"

shots="$ROOT/build/run/clientGameTest/screenshots"
[[ -d "$shots" ]] && info "Скриншоты: ${shots#"$ROOT"/}"
