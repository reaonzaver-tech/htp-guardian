const http = require("http");
const https = require("https");
const fs = require("fs");
const path = require("path");

// Notare mit ihren Namen und Ports
const NOTARIES = [
  { name: "Alpha", url: "http://127.0.0.1:4000" },
  { name: "Beta",  url: "http://127.0.0.1:4001" },
  { name: "Gamma", url: "http://127.0.0.1:4002" },
];

const TOKEN_MAX_AGE_SEC = 90; // Token älter als 90s werden abgelehnt

// HTTP POST Helper
function postJson(url, body) {
  return new Promise((resolve) => {
    const data = JSON.stringify(body);
    const parsed = new URL(url);
    const options = {
      hostname: parsed.hostname,
      port: parsed.port,
      path: parsed.pathname,
      method: "POST",
      headers: { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(data) }
    };
    const req = http.request(options, (res) => {
      let body = "";
      res.on("data", chunk => body += chunk);
      res.on("end", () => {
        try { resolve(JSON.parse(body)); }
        catch { resolve(null); }
      });
    });
    req.on("error", () => resolve(null));
    req.setTimeout(3000, () => { req.destroy(); resolve(null); });
    req.write(data);
    req.end();
  });
}

// Token-Format: HTP-T3-{payloadB64}.{notaryName}:{sigB64}.{notaryName}:{sigB64}
async function verifyToken(token) {
  if (!token || !token.startsWith("HTP-T3-")) return { valid: false, reason: "Ungültiges Token-Format" };

  const rest = token.slice("HTP-T3-".length);
  const parts = rest.split(".");
  if (parts.length < 3) return { valid: false, reason: "Token unvollständig" };

  const payloadB64 = parts[0];

  // Payload dekodieren und Timestamp prüfen
  let payloadObj;
  try {
    payloadObj = JSON.parse(Buffer.from(payloadB64, "base64url").toString());
  } catch {
    return { valid: false, reason: "Payload nicht lesbar" };
  }

  const age = Math.floor(Date.now() / 1000) - payloadObj.timestamp;
  if (age > TOKEN_MAX_AGE_SEC) return { valid: false, reason: `Token abgelaufen (${age}s alt)` };
  if (age < -10)               return { valid: false, reason: "Token aus der Zukunft" };

  // Signaturen extrahieren: "NotaryName:base64sig"
  const signatures = parts.slice(1).map(p => {
    const idx = p.indexOf(":");
    if (idx === -1) return null;
    return { notary: p.slice(0, idx), signature: p.slice(idx + 1) };
  }).filter(Boolean);

  if (signatures.length < 2) return { valid: false, reason: "Zu wenige Signaturen" };

  // Jeden Notar fragen ob seine Signatur stimmt
  let confirmed = 0;
  const results = [];

  for (const sig of signatures) {
    const notary = NOTARIES.find(n => n.name === sig.notary);
    if (!notary) continue;

    const res = await postJson(`${notary.url}/verify`, {
      payload:   payloadB64,
      signature: sig.signature,
    });

    if (res?.valid === true) {
      confirmed++;
      results.push(`✅ ${sig.notary}`);
    } else {
      results.push(`❌ ${sig.notary}`);
    }
  }

  const quorum = confirmed >= 2;
  return {
    valid: quorum,
    confirmed,
    total: signatures.length,
    results,
    deviceId: payloadObj.deviceId,
    reason: quorum ? "Quorum erreicht" : `Nur ${confirmed} von ${signatures.length} Notaren bestätigt`
  };
}

const server = http.createServer(async (req, res) => {

  if (req.url === "/api/vault") {
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Content-Type", "application/json");

    if (req.method === "OPTIONS") {
      res.setHeader("Access-Control-Allow-Headers", "X-HTP-Token");
      res.writeHead(200); res.end(); return;
    }

    const token = req.headers["x-htp-token"];
    const result = await verifyToken(token);

    if (!result.valid) {
      console.log(`[Server] Zugriff verweigert: ${result.reason}`);
      res.writeHead(403);
      res.end(JSON.stringify({ status: "error", message: result.reason }));
      return;
    }

    console.log(`[Server] Zugriff OK – Device: ${result.deviceId} – ${result.results?.join(", ")}`);
    res.writeHead(200);
    res.end(JSON.stringify({
      status: "success",
      message: "Identität verifiziert. Tresor geöffnet.",
      device_id: result.deviceId,
      quorum: `${result.confirmed}/${result.total}`,
      secret_data: "Dies ist der streng geheime Payload."
    }));
    return;
  }

  // HTML ausliefern
  const filePath = path.join(__dirname, req.url === "/" ? "test.html" : req.url);
  fs.readFile(filePath, (err, content) => {
    if (err) { res.writeHead(404); res.end("404"); return; }
    res.writeHead(200, { "Content-Type": "text/html" });
    res.end(content);
  });
});

server.listen(3000, () => console.log("Webserver läuft auf http://localhost:3000"));
