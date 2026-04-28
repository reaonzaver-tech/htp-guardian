#!/bin/bash

# Terminal-Erkennung: erste gefundene wird genutzt
detect_terminal() {
  for term in cosmic-term kgx gnome-terminal xterm x-terminal-emulator; do
    if command -v "$term" &>/dev/null; then
      echo "$term"
      return
    fi
  done
  echo ""
}

TERM_CMD=$(detect_terminal)

if [ -z "$TERM_CMD" ]; then
  echo "Kein Terminal gefunden. Starte Prozesse im Hintergrund mit Log-Dateien."
  node server.js > /tmp/htp-webserver.log 2>&1 &
  go run ~/Dokumente/htp-extension/notar.go -port=4000 -name=Alpha > /tmp/htp-alpha.log 2>&1 &
  go run ~/Dokumente/htp-extension/notar.go -port=4001 -name=Beta  > /tmp/htp-beta.log  2>&1 &
  go run ~/Dokumente/htp-extension/notar.go -port=4002 -name=Gamma > /tmp/htp-gamma.log 2>&1 &
  echo "Alle Prozesse gestartet. Logs: /tmp/htp-*.log"
  exit 0
fi

echo "Terminal erkannt: $TERM_CMD"

# Unterschiedliche Syntax je nach Terminal
launch() {
  local title="$1"
  local cmd="$2"
  case "$TERM_CMD" in
    gnome-terminal)
      gnome-terminal --title="$title" -- bash -c "$cmd; exec bash" &
      ;;
    xterm)
      xterm -title "$title" -e bash -c "$cmd; exec bash" &
      ;;
    *)
      # cosmic-term, kgx und die meisten modernen Terminals
      "$TERM_CMD" -- bash -c "$cmd; exec bash" &
      ;;
  esac
}

launch "HTP-Webserver" "node server.js"
launch "Notar Alpha"   "go run ~/Dokumente/htp-extension/notar.go -port=4000 -name=Alpha"
launch "Notar Beta"    "go run ~/Dokumente/htp-extension/notar.go -port=4001 -name=Beta"
launch "Notar Gamma"   "go run ~/Dokumente/htp-extension/notar.go -port=4002 -name=Gamma"
