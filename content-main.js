// Läuft in der PAGE-Welt – kann window.fetch der Seite überschreiben
const _originalFetch = window.fetch.bind(window);

window.fetch = async function(input, init = {}) {
  const url = typeof input === "string" ? input : input.url;

  if (url.includes("/api/vault")) {
    console.log("HTP: Vault-Request abgefangen, hole Token via Bridge...");

    const token = await new Promise((resolve) => {
      const id = Math.random().toString(36).slice(2);

      const handler = (event) => {
        if (
          event.source === window &&
          event.data?.type === "HTP_TOKEN_RESPONSE" &&
          event.data?.id === id
        ) {
          window.removeEventListener("message", handler);
          resolve(event.data.token || null);
        }
      };

      window.addEventListener("message", handler);
      window.postMessage({ type: "HTP_TOKEN_REQUEST", id }, "*");

      // Timeout nach 3 Sekunden
      setTimeout(() => {
        window.removeEventListener("message", handler);
        resolve(null);
        console.warn("HTP: Token-Anfrage Timeout.");
      }, 3000);
    });

    if (token) {
      console.log("HTP: Token erhalten und injiziert:", token);
      init.headers = { ...(init.headers || {}), "X-HTP-Token": token };
    } else {
      console.error("HTP: Kein Token – Quorum fehlgeschlagen oder Bridge tot.");
    }
  }

  return _originalFetch(input, init);
};

console.log("HTP content-main.js aktiv – fetch() überschrieben.");
