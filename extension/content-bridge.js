// Läuft in der EXTENSION-Welt – hat Zugriff auf chrome.runtime
// Brücke zwischen Page-Welt (postMessage) und Background Script (chrome.runtime)

window.addEventListener("message", async (event) => {
  if (event.source !== window) return;
  if (event.data?.type !== "HTP_TOKEN_REQUEST") return;

  const id = event.data.id;
  console.log("HTP Bridge: Token-Anfrage empfangen, frage Background...");

  try {
    const response = await chrome.runtime.sendMessage({ type: "GET_HTP_TOKEN" });
    window.postMessage({
      type: "HTP_TOKEN_RESPONSE",
      id,
      token: response?.token || null
    }, "*");
  } catch (err) {
    console.error("HTP Bridge: Background-Kommunikation fehlgeschlagen:", err);
    window.postMessage({ type: "HTP_TOKEN_RESPONSE", id, token: null }, "*");
  }
});

console.log("HTP content-bridge.js aktiv.");
