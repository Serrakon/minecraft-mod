#!/usr/bin/env bash
# Сборка jar мода в build/libs/.
source "$(dirname "$0")/lib.sh"

gradle build "$@"

jar="$(ls -t "$ROOT"/build/libs/"$MOD_ID"-*.jar | grep -v -- '-sources' | head -1)"
info "Собрано: ${jar#"$ROOT"/}"
