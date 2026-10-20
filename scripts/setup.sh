#!/usr/bin/env bash
# Первичная настройка: JDK 25 (без sudo), расширения VSCode, загрузка зависимостей,
# исходники Minecraft в .mc-sources/. Повторный запуск ставит только недостающее.
set -euo pipefail

JDK_DIR="$HOME/Library/Java/JavaVirtualMachines"

if ! /usr/libexec/java_home -v 25 >/dev/null 2>&1; then
	echo "==> Ставлю Temurin JDK 25 в $JDK_DIR (~300 МБ)"
	tmp="$(mktemp -d)"
	trap 'rm -rf "$tmp"' EXIT
	meta="$(curl -fsSL "https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=aarch64&image_type=jdk&os=mac&vendor=eclipse")"
	link="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])[0]["binary"]["package"]["link"])' "$meta")"
	sum="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])[0]["binary"]["package"]["checksum"])' "$meta")"
	curl -fsSL -o "$tmp/jdk.tar.gz" "$link"
	echo "$sum  $tmp/jdk.tar.gz" | shasum -a 256 -c -
	mkdir -p "$JDK_DIR"
	tar -xzf "$tmp/jdk.tar.gz" -C "$JDK_DIR"
fi

source "$(dirname "$0")/lib.sh"
info "JDK: $JAVA_HOME"

if command -v code >/dev/null; then
	for ext in redhat.java vscjava.vscode-java-debug; do
		code --list-extensions | grep -qx "$ext" || code --install-extension "$ext"
	done
else
	warn "Команда 'code' не найдена — расширения VSCode не проверены"
fi

info "Скачиваю Gradle, Minecraft $MC_VERSION и зависимости"
gradle --quiet classes

"$ROOT/scripts/mc-sources.sh"

info "Готово, $(free_space)"
