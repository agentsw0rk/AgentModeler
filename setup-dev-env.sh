#!/usr/bin/env bash
# Sets up a rootless dev/test environment inside a fresh (Debian, no sudo) container:
# JDK 21, Playwright + Chromium (incl. missing system libs), fontconfig, and a
# Linux copy of the frontend for browser tests. Everything lands in .devenv/.
#
# Usage:
#   ./setup-dev-env.sh            # install everything (idempotent, skips what exists)
#   ./setup-dev-env.sh frontend   # (re)sync the Linux frontend copy only
#   source .devenv/env.sh         # JAVA_HOME/PATH for ./gradlew
#
# Afterwards:
#   ./gradlew test                                    # backend tests
#   .devenv/bin/frontend-dev                          # Next dev server on :3000 (Linux copy)
#   .devenv/bin/pw-node my-test.mjs                   # run a Playwright script
#       (scripts: require("playwright") via createRequire(process.env.PW_DIR + "/node_modules/"))
#
# Gotchas this script works around (learned the hard way):
#   - No sudo: apt can't install, so .deb files are fetched straight from the Debian
#     index and unpacked with `dpkg -x`; Chromium gets them via LD_LIBRARY_PATH.
#   - No system fonts: Chromium crashes (FATAL remote_font_face_source / FontFallbackList)
#     on next/font pages. Fixed by a fonts.conf pointing at the unpacked DejaVu fonts.
#   - Chromium needs --disable-dev-shm-usage --no-sandbox --disable-gpu in the container
#     (use PW_CHROMIUM_ARGS from env.sh).
#   - frontend/node_modules is installed on the macOS host (darwin binaries only:
#     lightningcss, tailwind oxide), so Next can't build CSS here -> tests run against a
#     copy in .devenv/frontend with its own Linux node_modules.
#   - The container sets NODE_ENV=production, which makes npm skip devDependencies
#     (tailwind!). Hence NODE_ENV=development + --include=dev.
#   - Don't wait for "networkidle" in Playwright: the editor keeps an SSE stream open.
#   - Backend needs OPENAI_API_KEY set (any dummy value) or the Spring context fails.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEV="${DEVENV_DIR:-$ROOT/.devenv}"
PLAYWRIGHT_VERSION="1.63.0"
JDK_MAJOR=21

DEB_ARCH="$(dpkg --print-architecture)"            # arm64 | amd64
case "$DEB_ARCH" in
  arm64) JDK_ARCH=aarch64; TRIPLE=aarch64-linux-gnu ;;
  amd64) JDK_ARCH=x64;     TRIPLE=x86_64-linux-gnu ;;
  *) echo "Unsupported architecture: $DEB_ARCH" >&2; exit 1 ;;
esac

log() { echo "==> $*"; }
mkdir -p "$DEV/bin"

install_jdk() {
  if [ -x "$DEV/jdk/bin/java" ]; then log "JDK already installed"; return; fi
  log "Downloading Temurin JDK $JDK_MAJOR ($JDK_ARCH)"
  local tmp; tmp="$(mktemp -d)"
  curl -fsSL "https://api.adoptium.net/v3/binary/latest/$JDK_MAJOR/ga/linux/$JDK_ARCH/jdk/hotspot/normal/eclipse" -o "$tmp/jdk.tgz"
  tar xzf "$tmp/jdk.tgz" -C "$tmp"
  rm -rf "$DEV/jdk"
  mv "$tmp"/jdk-*/ "$DEV/jdk"
  rm -rf "$tmp"
}

install_playwright() {
  local pw="$DEV/playwright"
  if [ -d "$pw/node_modules/playwright" ] && ls "$pw/browsers" 2>/dev/null | grep -q '^chromium-'; then
    log "Playwright already installed"; return
  fi
  log "Installing Playwright $PLAYWRIGHT_VERSION + Chromium"
  mkdir -p "$pw"
  (cd "$pw" && { [ -f package.json ] || npm init -y >/dev/null; } \
    && npm i --silent --no-audit --no-fund "playwright@$PLAYWRIGHT_VERSION" \
    && PLAYWRIGHT_BROWSERS_PATH="$pw/browsers" npx playwright install chromium >/dev/null 2>&1 || true)
  # `playwright install` exits non-zero on missing host deps; the browser is downloaded anyway.
  ls "$pw/browsers" | grep -q '^chromium-' || { echo "Chromium download failed" >&2; exit 1; }
}

install_system_libs() {
  local libroot="$DEV/libroot"
  if [ -d "$libroot/usr/lib/$TRIPLE" ] && [ -f "$libroot/.complete" ]; then
    log "Browser system libraries already installed"; return
  fi
  log "Resolving Chromium system libraries from the Debian index (no root needed)"
  local work="$DEV/debs"; mkdir -p "$work" "$libroot"
  . /etc/os-release
  curl -fsSL "http://deb.debian.org/debian/dists/$VERSION_CODENAME/main/binary-$DEB_ARCH/Packages.xz" -o "$work/Packages.xz"

  cat > "$work/resolve.py" <<'PY'
import lzma, subprocess, sys
pk, cur = {}, {}
for line in lzma.open(sys.argv[1], "rt"):
    line = line.rstrip("\n")
    if not line:
        if "Package" in cur: pk.setdefault(cur["Package"], cur)
        cur = {}; continue
    if line[0] in " \t": continue
    k, _, v = line.partition(": "); cur[k] = v
st = subprocess.run(["dpkg-query", "-W", "-f", "${db:Status-Abbrev} ${Package}\n"], capture_output=True, text=True).stdout
installed = {l.split()[1].split(":")[0] for l in st.splitlines() if l.startswith("ii")}
prov = {}
for n, p in pk.items():
    for x in p.get("Provides", "").split(","):
        x = x.strip().split(" ")[0]
        if x: prov.setdefault(x, n)
want, seen, out = list(sys.argv[2:]), set(), []
while want:
    n = want.pop()
    if n in seen or n in installed: continue
    seen.add(n)
    if n not in pk:
        if n in prov: want.append(prov[n])
        continue
    out.append(pk[n]["Filename"])
    for alt in pk[n].get("Depends", "").split(","):
        opts = [a.strip().split(" ")[0].split(":")[0] for a in alt.split("|") if a.strip()]
        if opts and not any(o in installed or o in seen for o in opts): want.append(opts[0])
print("\n".join(out))
PY

  # Package list from `playwright install-deps chromium` (bookworm) + fonts.
  python3 -I "$work/resolve.py" "$work/Packages.xz" \
    libglib2.0-0 libnss3 libnspr4 libdbus-1-3 libatk1.0-0 libatk-bridge2.0-0 libcups2 \
    libdrm2 libxcb1 libxkbcommon0 libx11-6 libxcomposite1 libxdamage1 libxext6 libxfixes3 \
    libxrandr2 libgbm1 libpango-1.0-0 libcairo2 libasound2 libatspi2.0-0 \
    fonts-dejavu-core fontconfig > "$work/urls.txt"

  while read -r f; do
    [ -n "$f" ] || continue
    local deb="$work/$(basename "$f")"
    [ -f "$deb" ] || curl -fsSL "http://deb.debian.org/debian/$f" -o "$deb"
    dpkg -x "$deb" "$libroot"
  done < "$work/urls.txt"

  local chrome; chrome="$(find "$DEV/playwright/browsers" -path '*chromium-*' -name chrome -type f | head -1)"
  local missing; missing="$(LD_LIBRARY_PATH="$libroot/usr/lib/$TRIPLE:$libroot/lib/$TRIPLE" ldd "$chrome" | grep 'not found' || true)"
  if [ -n "$missing" ]; then echo "Still missing libraries:"$'\n'"$missing" >&2; exit 1; fi
  touch "$libroot/.complete"
}

write_fontconfig() {
  mkdir -p "$DEV/fontconfig/cache"
  cat > "$DEV/fontconfig/fonts.conf" <<EOF
<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "fonts.dtd">
<fontconfig>
  <dir>$DEV/libroot/usr/share/fonts</dir>
  <cachedir>$DEV/fontconfig/cache</cachedir>
  <alias><family>sans-serif</family><prefer><family>DejaVu Sans</family></prefer></alias>
  <alias><family>serif</family><prefer><family>DejaVu Sans</family></prefer></alias>
  <alias><family>monospace</family><prefer><family>DejaVu Sans Mono</family></prefer></alias>
</fontconfig>
EOF
}

sync_frontend() {
  log "Syncing Linux copy of frontend/ to $DEV/frontend"
  mkdir -p "$DEV/frontend"
  (cd "$ROOT/frontend" && tar --exclude=node_modules --exclude=.next -cf - .) | (cd "$DEV/frontend" && tar xf -)
  local stamp="$DEV/frontend/.lock-installed"
  if [ ! -d "$DEV/frontend/node_modules" ] || ! cmp -s "$DEV/frontend/package-lock.json" "$stamp"; then
    log "Installing frontend dependencies (Linux, incl. devDependencies)"
    (cd "$DEV/frontend" && NODE_ENV=development npm ci --include=dev --no-audit --no-fund >/dev/null)
    cp "$DEV/frontend/package-lock.json" "$stamp"
  fi
}

write_env_and_helpers() {
  cat > "$DEV/env.sh" <<EOF
# source this file: JDK for ./gradlew, Playwright settings for test scripts
export JAVA_HOME="$DEV/jdk"
export PATH="\$JAVA_HOME/bin:\$PATH"
export PW_DIR="$DEV/playwright"
export PLAYWRIGHT_BROWSERS_PATH="$DEV/playwright/browsers"
export PW_CHROMIUM_ARGS="--disable-dev-shm-usage --no-sandbox --disable-gpu"
EOF

  # LD_LIBRARY_PATH/FONTCONFIG only for browser runs, not the whole shell.
  cat > "$DEV/bin/pw-node" <<EOF
#!/usr/bin/env bash
. "$DEV/env.sh"
export LD_LIBRARY_PATH="$DEV/libroot/usr/lib/$TRIPLE:$DEV/libroot/lib/$TRIPLE\${LD_LIBRARY_PATH:+:\$LD_LIBRARY_PATH}"
export FONTCONFIG_FILE="$DEV/fontconfig/fonts.conf"
exec node "\$@"
EOF

  cat > "$DEV/bin/frontend-dev" <<EOF
#!/usr/bin/env bash
# Next dev server from the Linux copy (run ./setup-dev-env.sh frontend after code changes)
cd "$DEV/frontend" && NODE_ENV=development exec npx next dev --webpack -p "\${PORT:-3000}"
EOF
  chmod +x "$DEV/bin/pw-node" "$DEV/bin/frontend-dev"
}

smoke_test() {
  log "Smoke test: Chromium renders a page"
  cat > "$DEV/smoke.mjs" <<'EOF'
import { createRequire } from "node:module";
const { chromium } = createRequire(process.env.PW_DIR + "/node_modules/")("playwright");
const b = await chromium.launch({ args: process.env.PW_CHROMIUM_ARGS.split(" ") });
const p = await b.newPage();
await p.setContent('<p style="font-family:sans-serif">ok</p>');
console.log("chromium", b.version(), await p.textContent("p"));
await b.close();
EOF
  "$DEV/bin/pw-node" "$DEV/smoke.mjs"
  . "$DEV/env.sh" && java -version 2>&1 | head -1
}

case "${1:-all}" in
  frontend) sync_frontend ;;
  all)
    install_jdk
    install_playwright
    install_system_libs
    write_fontconfig
    write_env_and_helpers
    sync_frontend
    smoke_test
    log "Done. Run: source .devenv/env.sh"
    ;;
  *) echo "Usage: $0 [all|frontend]" >&2; exit 1 ;;
esac
