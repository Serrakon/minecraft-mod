#!/usr/bin/env bash
# Сколько места занимает всё, что связано с модом.
source "$(dirname "$0")/lib.sh"

size() {
	[[ -e "$1" ]] && printf '  %-8s %s\n' "$(du -sh "$1" 2>/dev/null | cut -f1)" "${2:-$1}"
	return 0
}

echo "Проект:"
size "$ROOT/build" "build/"
size "$ROOT/run" "run/ (миры, логи)"
size "$ROOT/.gradle" ".gradle/ (кэш Loom проекта)"
size "$ROOT/.mc-sources" ".mc-sources/"
size "$ROOT/src/main/generated" "src/main/generated/"

echo "Общие кэши:"
size "$GRADLE_HOME_DIR/caches/fabric-loom" "~/.gradle/caches/fabric-loom"
size "$GRADLE_HOME_DIR/caches/modules-2" "~/.gradle/caches/modules-2 (зависимости)"
size "$GRADLE_HOME_DIR/wrapper/dists" "~/.gradle/wrapper/dists (дистрибутивы Gradle)"
size "$GRADLE_HOME_DIR/daemon" "~/.gradle/daemon"
size "$JAVA_HOME/../.." "JDK 25"
for ext in "$HOME"/.vscode/extensions/redhat.java-* "$HOME"/.vscode/extensions/vscjava.vscode-java-debug-*; do
	size "$ext" "VSCode: $(basename "$ext")"
done
size "$MC_DIR" "Minecraft лаунчер"

echo
info "Диск: $(free_space)"
