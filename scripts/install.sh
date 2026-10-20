#!/usr/bin/env bash
# Сборка и установка мода в настоящую игру (официальный лаунчер + Fabric Loader).
source "$(dirname "$0")/lib.sh"

[[ -d "$MC_DIR" ]] || die "Нет $MC_DIR — поставь лаунчер и один раз запусти Minecraft $MC_VERSION"

"$ROOT/scripts/build.sh"
jar="$(ls -t "$ROOT"/build/libs/"$MOD_ID"-*.jar | grep -v -- '-sources' | head -1)"

mods="$MC_DIR/mods"
mkdir -p "$mods"
rm -f "$mods/$MOD_ID"-*.jar
cp "$jar" "$mods/"
info "Установлено: $mods/$(basename "$jar")"

ls "$mods" | grep -q '^fabric-api-' \
	|| warn "В mods нет Fabric API — скачай версию для $MC_VERSION: https://modrinth.com/mod/fabric-api"
ls "$MC_DIR/versions" | grep -q "fabric-loader-.*-$MC_VERSION" \
	|| warn "Не найден профиль Fabric для $MC_VERSION — поставь его через https://fabricmc.net/use/installer/"
