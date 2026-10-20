#!/usr/bin/env bash
# Генерация JSON-ресурсов (модели, рецепты, лут, теги, переводы) в src/main/generated.
source "$(dirname "$0")/lib.sh"

gradle runDatagen
info "Сгенерировано в src/main/generated"
