# HTP-Guardian – Human Token Protocol

> **Technischer Proof-of-Concept zur Bot-Prävention durch kryptographischen Menschlichkeitsnachweis.**

Ein System das beweist, dass ein Post, ein Klick oder eine API-Anfrage von einem echten Menschen kommt – ohne zu wissen *wer* dieser Mensch ist.

---

## Das Problem

Politische Institutionen und Plattformen nutzen die „Alles-Bots"-Ausrede um organischen Protest zu delegitimieren. Es gibt keinen technischen Standard der beweist: **Diese Stimme kommt von einem echten Menschen.**

HTP-Guardian ist dieser Standard.

---

## Wie es funktioniert

```
Gerät (App/Browser)
  → baut Payload: {deviceId, timestamp, nonce}
  → schickt an 3 unabhängige Notar-Server zum Signieren

Notar-Server (3 unabhängige Instanzen)
  → signieren den Payload mit RSA-PKCS1v15 / SHA-256
  → kennen NICHT die Identität des Nutzers (Blind Signature)

Token
  → HTP-T3-{base64url(payload)}.{Notar}:{Signatur}.{Notar}:{Signatur}
  → gültig 90 Sekunden, danach wertlos

Ziel-Server / Website
  → fragt Notare: "Habt ihr das signiert?" (/verify)
  → 2 von 3 bestätigen → Anfrage ist von einem Menschen
```

### Warum das Bots stoppt

| Angriff | Wirkung |
|---|---|
| Bot-Skript ohne Token | Wird vom Server mit 403 abgewiesen |
| Gestohlener Token | Verfällt nach 90 Sekunden |
| Token für andere Domain | Domain-Binding verhindert Wiederverwendung *(geplant)* |
| Click Farm | Außerhalb des Scopes – System löst Bot-Automatisierung, nicht organisierte Menschen |

---

## Stack

| Komponente | Technologie |
|---|---|
| Notar-Server | Go (3 unabhängige Instanzen) |
| Android App | Kotlin – AccessibilityService + Floating Overlay |
| Browser Extension | Chrome MV3 – content-main.js / content-bridge.js |
| Demo-Server | Node.js |

---

## Architektur

```
┌─────────────────────────────────────────────────┐
│  Gerät (Android App / Browser Extension)        │
│  deviceId + timestamp + nonce → Payload         │
└────────────────┬────────────────────────────────┘
                 │ POST /sign
       ┌─────────┼──────────┐
       ▼         ▼          ▼
  [Notar Alpha] [Notar Beta] [Notar Gamma]
  Port 4000     Port 4001    Port 4002
  RSA Sign      RSA Sign     RSA Sign
       └─────────┬──────────┘
                 │ Signaturen
                 ▼
         Token zusammengebaut
         HTP-T3-{payload}.Alpha:{sig}.Beta:{sig}
                 │
                 ▼ X-HTP-Token Header
         ┌───────────────┐
         │  Ziel-Server  │
         │  POST /verify │──→ Notare bestätigen
         │  2/3 OK       │
         │  → 200 OK     │
         └───────────────┘
```

---

## Setup (lokal / Entwicklung)

### Voraussetzungen

- Go 1.21+
- Node.js 18+
- Android Studio (für die App)
- Chrome (für die Extension)

### 1. Notare starten

```bash
# Drei Terminals oder das Startscript nutzen:
./startToken.sh

# Manuell:
go run notar.go -port=4000 -name=Alpha
go run notar.go -port=4001 -name=Beta
go run notar.go -port=4002 -name=Gamma
```

### 2. Demo-Server starten

```bash
node server.js
# → http://localhost:3000
```

### 3. Browser Extension laden

1. Chrome öffnen → `chrome://extensions`
2. "Entwicklermodus" aktivieren (oben rechts)
3. "Entpackte Erweiterung laden" → Ordner `extension/` auswählen

### 4. Android App installieren

APK aus `app/build/outputs/apk/debug/` auf Gerät oder Emulator installieren.

Nach dem Start:
1. **"Floating HTP-Button aktivieren"** → Overlay-Permission erlauben
2. Button erscheint als schwebendes `🔐`-Symbol über allen Apps
3. Tippen auf `🔐` → Handshake startet

---

## Projektstruktur

```
htp-guardian/
├── notary/
│   └── notar.go              # Go Notar-Server (3x starten)
├── server/
│   └── server.js             # Demo Node.js Ziel-Server
│   └── test.html             # Demo-Frontend
├── extension/
│   ├── manifest.json
│   ├── background.js         # Token-Logik
│   ├── content-bridge.js     # Extension-Welt Bridge
│   └── content-main.js       # Page-Welt fetch()-Override
├── android/
│   └── app/src/main/java/com/HTPGuardian/myapplication/
│       ├── FloatingHtpService.kt
│       ├── HtpAccessibilityService.kt
│       ├── HtpLogger.kt
│       └── MainActivity.kt
├── startToken.sh             # Alle Server auf einmal starten
├── LICENSE                   # AGPL-3.0
├── COMMERCIAL_LICENSE.md     # Kommerzielle Lizenz-Info
└── README.md
```

---

## Sicherheitshinweise

- Dieses Repository ist ein **Proof of Concept**. Nicht für Produktivumgebungen ohne Sicherheits-Audit einsetzen.
- Die Notar-Schlüssel werden aktuell bei jedem Start neu generiert. Für den Produktivbetrieb müssen persistente Schlüssel mit sicherer Verwahrung implementiert werden.
- Blind Signatures (echte kryptographische Entkopplung von Identität und Aktion) sind geplant aber noch nicht implementiert.

---

## Lizenz

Dieses Projekt steht unter der **GNU Affero General Public License v3.0 (AGPL-3.0)** für Open-Source-Verwendung.

Für kommerzielle Nutzung in proprietären Produkten ist eine separate kommerzielle Lizenz erforderlich.
Siehe [COMMERCIAL_LICENSE.md](./COMMERCIAL_LICENSE.md) für Details.

© 2025 Michael Rosenau – Rellingen, Deutschland
