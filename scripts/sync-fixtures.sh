#!/usr/bin/env bash
# Copia los fixtures del contrato desde rp-relay a protocol/src/test/resources/fixtures/relay/.
# Uso: scripts/sync-fixtures.sh [ruta-a-rp-relay]   (por defecto ../rp-relay)
set -euo pipefail
cd "$(dirname "$0")/.."
SRC="${1:-../rp-relay}/packages/protocol/fixtures"
DST="protocol/src/test/resources/fixtures/relay"
if [[ ! -d "$SRC" ]]; then
  echo "No existe $SRC. Clona https://github.com/j3pf/rp-relay o pasa la ruta como argumento." >&2
  exit 1
fi
mkdir -p "$DST"
rm -f "$DST"/*.json
cp "$SRC"/*.json "$DST"/
echo "Fixtures sincronizados: $(ls "$DST" | wc -l) archivos → $DST"
