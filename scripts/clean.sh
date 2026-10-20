#!/usr/bin/env bash
# Освобождение места на диске.
#   ./scripts/clean.sh           — сборка, логи, краш-репорты, остановка Gradle-демонов
#   ./scripts/clean.sh --deep    — + кэши Loom для других версий MC, логи демонов, build-cache, brew cleanup
#   ./scripts/clean.sh --worlds  — + миры из run/saves (необратимо)
# Флаги можно комбинировать. Кэши других Gradle-версий (их используют другие проекты)
# скрипт не трогает, только показывает.
source "$(dirname "$0")/lib.sh"

deep=false
worlds=false
for arg in "$@"; do
	case "$arg" in
		--deep) deep=true ;;
		--worlds) worlds=true ;;
		*) die "Неизвестный флаг '$arg' (--deep, --worlds)" ;;
	esac
done

before="$(free_space)"

info "Останавливаю Gradle-демоны"
gradle --stop >/dev/null 2>&1 || true

info "Удаляю build/, логи и краш-репорты"
rm -rf "$ROOT/build" "$ROOT/run/logs" "$ROOT/run/crash-reports" "$ROOT/run/debug" \
	"$ROOT/run/downloads" "$ROOT/.gradle/configuration-cache"
find "$ROOT" -maxdepth 2 \( -name 'hs_err_*.log' -o -name '*.hprof' \) -delete

if $worlds; then
	info "Удаляю миры run/saves"
	rm -rf "$ROOT/run/saves"
fi

if $deep; then
	loom="$GRADLE_HOME_DIR/caches/fabric-loom"
	if [[ -d "$loom" ]]; then
		for dir in "$loom"/*/; do
			name="$(basename "$dir")"
			# Папки версий Minecraft выглядят как 26.3, 1.21.11, 26.4-snapshot-1
			if [[ "$name" =~ ^[0-9]+\.[0-9] && "$name" != "$MC_VERSION" ]]; then
				info "Удаляю кэш Loom для Minecraft $name"
				rm -rf "$dir"
			fi
		done
		rm -rf "$loom/decompile"
	fi

	find "$ROOT/.gradle/loom-cache/minecraftMaven/net/minecraft" -mindepth 2 -maxdepth 2 -type d ! -name "$MC_VERSION" \
		-exec rm -rf {} + 2>/dev/null || true

	if [[ -f "$ROOT/.mc-sources/.version" && "$(cat "$ROOT/.mc-sources/.version")" != "$MC_VERSION" ]]; then
		info "Удаляю устаревшие .mc-sources/"
		rm -rf "$ROOT/.mc-sources"
	fi

	info "Удаляю логи Gradle-демонов и build-cache"
	find "$GRADLE_HOME_DIR/daemon" -name '*.log' -delete 2>/dev/null || true
	rm -rf "$GRADLE_HOME_DIR/caches/build-cache-1"

	if command -v brew >/dev/null; then
		info "brew cleanup"
		brew cleanup -s >/dev/null 2>&1 || true
	fi

	current_gradle="$(grep -oE 'gradle-[0-9.]+' "$ROOT/gradle/wrapper/gradle-wrapper.properties" | cut -d- -f2)"
	others="$(ls "$GRADLE_HOME_DIR/wrapper/dists" 2>/dev/null | grep -v "gradle-$current_gradle-" | grep '^gradle-' || true)"
	if [[ -n "$others" ]]; then
		warn "Другие версии Gradle (могут быть нужны другим проектам, удаляй вручную):"
		for d in $others; do
			v="$(echo "$d" | cut -d- -f2)"
			du -shc "$GRADLE_HOME_DIR/wrapper/dists/$d" "$GRADLE_HOME_DIR/caches/$v" 2>/dev/null | tail -1 \
				| awk -v v="$v" '{print "   Gradle " v ": " $1}'
		done
	fi
fi

info "Было: $before, стало: $(free_space)"
