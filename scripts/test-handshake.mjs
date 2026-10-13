const relayUrl = process.argv[2] || "http://127.0.0.1:8787";
const deviceToken = process.argv[3];
const deviceId = process.argv[4] || "01TESTDEVICE0000000000000";

if (!deviceToken) {
  console.error("Uso: node scripts/test-handshake.mjs <relayUrl> <deviceToken> [deviceId]");
  process.exit(1);
}

const wsUrl = relayUrl.replace(/^http/, "ws") + "/ws/app?mode=foreground";
console.log(`Conectando WebSocket a ${wsUrl}...`);

const ws = new WebSocket(wsUrl, {
  headers: {
    Authorization: `Bearer ${deviceToken}`,
  },
});

ws.addEventListener("open", () => {
  console.log("WebSocket conectado. Enviando app.hello...");
  const hello = {
    v: 1,
    t: "app.hello",
    id: "01TESTHELLO0000000000000",
    d: {
      deviceId,
      app: { version: "0.1.0" },
      subscriptions: "all",
    },
  };
  ws.send(JSON.stringify(hello));
});

ws.addEventListener("message", (event) => {
  try {
    const msg = JSON.parse(event.data);
    if (msg.t === "app.welcome") {
      console.log("Recibido app.welcome exitosamente:");
      console.log("- Relay:", msg.d.relayName, `(${msg.d.relayId})`);
      console.log("- Máquinas registradas:", msg.d.machines.length);
      console.log("- Agentes activos:", msg.d.agents.length);
      console.log("- Prompts abiertos:", msg.d.openPrompts.length);
      setTimeout(() => {
        ws.close(1000, "test ok");
        process.exit(0);
      }, 500);
    } else {
      console.log("Mensaje recibido:", msg.t);
    }
  } catch (err) {
    console.error("Error al procesar mensaje:", err);
  }
});

ws.addEventListener("error", (err) => {
  console.error("Error en WebSocket:", err);
  process.exit(1);
});

ws.addEventListener("close", (event) => {
  console.log(`WebSocket cerrado (code=${event.code}, reason=${event.reason})`);
});
