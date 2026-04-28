const NOTARY_URLS = [
  { name: "Alpha", url: "http://127.0.0.1:4000/sign" },
  { name: "Beta",  url: "http://127.0.0.1:4001/sign" },
  { name: "Gamma", url: "http://127.0.0.1:4002/sign" },
];

// Browser-seitige "DeviceID" – UUID die einmalig generiert und gecacht wird
async function getDeviceId() {
  const stored = await chrome.storage.local.get("htpDeviceId");
  if (stored.htpDeviceId) return stored.htpDeviceId;
  const id = crypto.randomUUID();
  await chrome.storage.local.set({ htpDeviceId: id });
  return id;
}

async function getThresholdToken() {
  const deviceId  = await getDeviceId();
  const timestamp = Math.floor(Date.now() / 1000);
  const nonce     = Math.random().toString(36).slice(2, 18);

  // Payload als base64url
  const payloadObj = { deviceId, timestamp, nonce };
  const payloadB64 = btoa(JSON.stringify(payloadObj))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

  // Alle Notare parallel anfragen
  const requests = NOTARY_URLS.map(({ name, url }) =>
    fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ payload: payloadB64 })
    })
    .then(r => r.ok ? r.json() : null)
    .catch(() => null)
  );

  const results = (await Promise.all(requests)).filter(r => r?.signature);

  if (results.length < 2) {
    console.error(`HTP Quorum fehlgeschlagen: Nur ${results.length} Notare erreichbar.`);
    return null;
  }

  // Token zusammenbauen: HTP-T3-{payload}.{Notar}:{sig}.{Notar}:{sig}
  const sigParts = results.map(r => `${r.notary}:${r.signature}`).join(".");
  return `HTP-T3-${payloadB64}.${sigParts}`;
}

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === "GET_HTP_TOKEN") {
    getThresholdToken()
      .then(token => sendResponse({ token }))
      .catch(() => sendResponse({ token: null }));
    return true;
  }
});
