#!/usr/bin/env bash
# Общие переменные и функции для скриптов. Подключается через `source`.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MOD_ID="$(grep -E '^mod_id=' "$ROOT/gradle.properties" | cut -d= -f2)"
MC_VERSION="$(grep -E '^minecraft_version=' "$ROOT/gradle.properties" | cut -d= -f2)"
MC_DIR="$HOME/Library/Application Support/minecraft"
GRADLE_HOME_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}"

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!!\033[0m %s\n' "$*" >&2; }
die() { printf '\033[1;31mxx\033[0m %s\n' "$*" >&2; exit 1; }

# JDK 25 нужна Minecraft 26.x и Loom
if ! JAVA_HOME="$(/usr/libexec/java_home -v 25 2>/dev/null)"; then
	die "JDK 25 не найдена. Запусти ./scripts/setup.sh"
fi
export JAVA_HOME

gradle() {
	(cd "$ROOT" && ./gradlew "$@")
}

free_space() {
	df -h "$ROOT" | awk 'NR==2 {print $4 " свободно"}'
}
