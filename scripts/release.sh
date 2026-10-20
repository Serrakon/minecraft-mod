#!/usr/bin/env bash
# Выпуск версии в GitHub Releases: собирает jar и прикладывает его к релизу v<version>.
# Версия берётся из gradle.properties — перед новым релизом подними `version=`.
source "$(dirname "$0")/lib.sh"

VERSION="$(grep -E '^version=' "$ROOT/gradle.properties" | cut -d= -f2)"
FABRIC_API="$(grep -E '^fabric_api_version=' "$ROOT/gradle.properties" | cut -d= -f2)"
TAG="v$VERSION"

[[ -z "$(git -C "$ROOT" status --porcelain)" ]] || die "Есть незакоммиченные изменения — сначала закоммить"
(cd "$ROOT" && gh release view "$TAG" >/dev/null 2>&1) && die "Релиз $TAG уже есть — подними version в gradle.properties"

"$ROOT/scripts/build.sh"
jar="$ROOT/build/libs/$MOD_ID-$VERSION.jar"
[[ -f "$jar" ]] || die "Не найден $jar"

git -C "$ROOT" push -q

notes="$(cat <<EOF
Particle Studio $VERSION for Minecraft Java $MC_VERSION (Fabric). Client-side mod: no need to install it on a server.

**Requires:** Fabric Loader for $MC_VERSION and [Fabric API](https://modrinth.com/mod/fabric-api) ($FABRIC_API or newer).

**Modrinth App:** create a Fabric $MC_VERSION profile → Add content → install Fabric API → Add content → From file → pick \`$(basename "$jar")\`.

**Official launcher:** run the [Fabric Installer](https://fabricmc.net/use/installer/) for $MC_VERSION, put Fabric API and \`$(basename "$jar")\` into the \`mods\` folder (Windows: \`%APPDATA%\\.minecraft\\mods\`), start the Fabric profile.

**In game:** press **J**. "Preview" works without permissions; "Run" needs cheats or /op.
EOF
)"

(cd "$ROOT" && gh release create "$TAG" "$jar" --target main --title "Particle Studio $VERSION" --notes "$notes")
info "Релиз $TAG опубликован"
