#!/usr/bin/env bash
# Запуск игры с модом в dev-окружении (лицензия не нужна).
#   ./scripts/run.sh          — клиент
#   ./scripts/run.sh server   — выделенный сервер (консоль; остановка: stop)
source "$(dirname "$0")/lib.sh"

case "${1:-client}" in
	client) gradle runClient ;;
	server)
		mkdir -p "$ROOT/run"
		echo "eula=true" > "$ROOT/run/eula.txt"
		gradle runServer
		;;
	*) die "Неизвестный режим '$1' (client|server)" ;;
esac
