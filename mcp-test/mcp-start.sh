#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# aiohttp installieren falls nicht vorhanden
python3 -c "import aiohttp" 2>/dev/null || pip3 install aiohttp

exec python3 "$SCRIPT_DIR/server.py"
