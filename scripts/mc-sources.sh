#!/usr/bin/env bash
# Декомпилирует Minecraft и распаковывает исходники в .mc-sources/ (~50 МБ),
# чтобы искать по ним настоящие имена классов и методов текущей версии.
source "$(dirname "$0")/lib.sh"

DEST="$ROOT/.mc-sources"

if [[ -f "$DEST/.version" && "$(cat "$DEST/.version")" == "$MC_VERSION" ]]; then
	info "Исходники Minecraft $MC_VERSION уже в .mc-sources/"
	exit 0
fi

gradle genSources

rm -rf "$DEST"
mkdir -p "$DEST"
find "$ROOT/.gradle/loom-cache/minecraftMaven" -path "*/$MC_VERSION/*-sources.jar" -print0 \
	| while IFS= read -r -d '' jar; do unzip -qo "$jar" -d "$DEST"; done
echo "$MC_VERSION" > "$DEST/.version"
info "Исходники Minecraft $MC_VERSION: .mc-sources/ ($(du -sh "$DEST" | cut -f1))"
