# QA manual (E2E) — rp-app

Pruebas que no se pueden automatizar del todo: requieren un teléfono real, un relay y un agente Pi con el plugin `rp`.
Marca cada caso con ✅/❌ y anota el dispositivo (modelo, versión de Android) y el sabor de build (`direct` / `fcm`).

**Preparación**
1. Relay en marcha (`/healthz` responde) y un agente Pi con `rp-plugin` emparejado (`/rp pair …`).
2. Instalar el APK (`app-direct-debug` o `app-fcm-debug`), emparejar con el QR (`rp-relay pair device`).
3. Conceder notificaciones y (recomendado) excluir RP de la optimización de batería.
4. Para el agente: un comando que dispare una autorización, p. ej. pedir a Pi que ejecute `rm -rf build/`.

## 1. Notificaciones (requisito central)

| # | Caso | Pasos | Resultado esperado |
|---|---|---|---|
| N1 | Autorización con la app **cerrada** y pantalla **bloqueada** | Cierra la app (deslizar en recientes), bloquea el teléfono, provoca una autorización en el agente | Llega la notificación heads‑up con título "🏢 PC · agente" y botones **[Permitir] [Denegar]**; pulsar **Permitir** desbloquea al agente en < 2 s sin abrir la app; la notificación pasa a "Enviando…" y desaparece |
| N2 | Responder en la **terminal** | Provoca una autorización y respóndela en la terminal del agente | La notificación del teléfono desaparece sola (push `cancel` / `prompt.resolved`) |
| N3 | Responder en **otro dispositivo** | Con dos teléfonos emparejados, responde en uno | En el otro la notificación se retira; si se pulsa a tiempo, 409 → se quita igualmente |
| N4 | **Modo avión** | Activa modo avión, provoca/recibe la notificación (o deja una abierta), pulsa **Permitir** | Muestra "Sin conexión · se reintentará"; al desactivar el modo avión la respuesta sale sola (WorkManager) y la notificación se retira |
| N5 | Pregunta con opciones (≤ 3) | `ask_user` con 3 opciones | Un botón por opción |
| N6 | Pregunta con > 3 opciones o cuestionario | `ask_user_question` (gentle‑pi) con 2 preguntas | Notificación con **[Abrir]**; la hoja muestra páginas con progreso y envía al final |
| N7 | Texto libre | Pregunta de tipo `input` | Acción **Responder** con campo inline (RemoteInput); el texto llega al agente |
| N8 | Agente terminó | Deja que un agente termine un turno | Notificación en el canal "Agente terminó" con **Responder** (RemoteInput) que envía un mensaje |
| N9 | Desbloqueo para aprobar | Ajustes → Seguridad → "Requerir desbloqueo para aprobar" | Los botones de la notificación piden desbloqueo (Android 12+); la hoja pide biometría en autorizaciones críticas |
| N10 | Dos canales sin duplicados | Sabor `fcm` con canal directo activo | Una única notificación por `promptId` |
| N11 | Reinicio | Reinicia el teléfono con el canal directo activo | El servicio "RP conectado · N PCs" vuelve a arrancar y las notificaciones siguen llegando |
| N12 | Doze | Pantalla apagada y quieto 1 h (spike A0) | La notificación llega (directo y FCM) y se mide el consumo de batería del canal directo |
| N13 | Grupos por PC | Ajustes del sistema → RP → Notificaciones | Un grupo por PC ("🏠 …", "🏢 …") con 5 canales; silenciar uno no afecta al otro |

## 2. Chat

| # | Caso | Resultado esperado |
|---|---|---|
| C1 | Abrir un agente | Historial reciente + streaming en vivo (texto, razonamiento plegable, herramientas con estado) |
| C2 | Bash / edit | El bash muestra el comando; el edit, el diff; resultado truncado con "ver más" |
| C3 | Enviar mensaje (agente ocioso) | Burbuja con 📱 y respuesta en streaming |
| C4 | Agente trabajando | Aparecen **Encolar (follow‑up)** / **Interrumpir y dirigir (steer)**; ⏹ interrumpe |
| C5 | Modo AUTO de gentle‑pi | Chip "AUTO · tus mensajes se envían como tecleados 🔒"; enviar pide biometría (válida 5 min) y el mensaje cuenta como tecleado |
| C6 | Sin entrega interactiva | Banner "En Auto este mensaje no autoriza acciones" con **[Pasar a Normal]** (envía `/gentle:mode normal`) |
| C7 | Comando `/…` | Con texto que empieza por `/` se habilita "Comando /…" |
| C8 | Diálogo no remoto | Aparece el banner "Esperando en la terminal (diálogo no remoto)" |
| C9 | Reconexión | Corta la red 30 s y recupérala: el chat reanuda con `sinceSeq` sin duplicados |

## 3. Adjuntos

| # | Caso | Resultado esperado |
|---|---|---|
| A1 | Foto de la galería / cámara | Se comprime (JPEG 85 %, 2048 px), sube con progreso y el agente la recibe como imagen |
| A2 | PDF / archivo | Sube con progreso; el agente recibe `[Archivo adjunto: /ruta (mime, tamaño)]` |
| A3 | > 25 MB | Error "Supera 25 MB" en el adjunto |
| A4 | Sin red al enviar | "Se enviará al volver la red"; llega una sola vez (idempotencia por `clientMsgId`) |

## 4. PCs, agentes y ajustes

| # | Caso | Resultado esperado |
|---|---|---|
| P1 | Selector de PC | Chips Todas / Personal / Empresa con color y contadores 🔔; filtra Agentes y Pendientes |
| P2 | Editar PC | Cambiar nombre/contexto/color se refleja en cabeceras, chips y grupos de canales |
| P3 | Nuevo agente | PC con rp‑host → carpeta → lanzador → herdr (workspace/pestaña/panel) o tmux/Windows Terminal → aparece en la lista |
| P4 | Añadir máquina | Ajustes → "Añadir máquina" muestra `/rp pair URL CÓDIGO` copiable |
| P5 | Revocar dispositivo/PC | Desaparece y deja de conectar |
| P6 | Horas silenciosas | Configuradas por PC; las autorizaciones críticas "siempre" las ignoran si el ajuste está activo |
| P7 | Bloqueo de la app / FLAG_SECURE | Pide biometría al abrir; capturas bloqueadas |
| P8 | Cerrar sesión | Borra credenciales, base de datos y canales; vuelve al onboarding |
| P9 | Notificación de prueba | Ajustes → Notificaciones → "Enviar notificación de prueba" |

## 5. Agent Mesh (Topología animada y comunicaciones)

| # | Caso | Pasos | Resultado esperado |
|---|---|---|---|
| M1 | Acceso y navegación | Abrir el menú inferior y pulsar la pestaña **Mesh** con icono de red | Se abre `MeshScreen` sin romper la navegación; el botón de la barra se resalta y muestra el diagrama o vista de lista |
| M2 | Diagrama radial y satélites | Observar la pantalla con agentes de una máquina/grupo | El orquestador se posiciona al centro más grande y con badge de rol; los subordinados se distribuyen en un anillo concéntrico; los subagentes orbitan como satélites alrededor de su agente padre |
| M3 | Aristas y estado de actividad | Provocar comunicación reciente entre agentes o una pregunta pendiente | Las aristas activas (últimos 20 s) brillan con trazo engrosado y translúcido; la arista con `pendingAsk` parpadea con trazo discontinuo; el brillo se apaga puntualmente a los 20 s |
| M4 | Partículas y globos de diálogo | Enviar un mensaje entre agentes (`mesh.comm`) | Una partícula luminosa viaja por la curva desde el emisor hasta el receptor; aparece un globo con el texto (elipsado o "Contenido oculto" si está redactado) durante ~6 s junto a la arista |
| M5 | Gestos de zoom y desplazamiento | Pellizcar la pantalla (pinch-to-zoom), arrastrar con un dedo y hacer doble toque | El zoom escala suavemente entre 0.3x y 3x; el arrastre desplaza la cámara; el doble toque restablece zoom a 1x y centra la vista |
| M6 | Selectores de máquina y grupo | Seleccionar chips superiores de máquina y de grupo | Filtra los nodos y aristas visibles según la máquina o grupo elegido (incluyendo el grupo "Solo"); la selección se persiste; al abrir sin selección previa se preselecciona automáticamente el grupo con mayor actividad reciente |
| M7 | Hoja de detalle de nodo | Tocar un nodo en el diagrama o en la lista | Se abre `ModalBottomSheet` con nombre, rol, máquina, cwd, rama git, modelo, estado, actividad, resumen de TODOs (con tarea actual resaltada y bloqueadas señaladas), lista de subagentes (estado, tokens, herramientas) y conversaciones recientes; incluye botones **Abrir chat** y **Ver conversación con X** |
| M8 | Hoja de conversación entre agentes | Tocar una arista entre dos agentes | Se abre `ModalBottomSheet` con el historial de mensajes ordenados por dirección, badges de acción (`ask`, `reply`, `handover`, etc.), marcas de tiempo y paginación con **Cargar mensajes anteriores** vía `nextBefore` |
| M9 | Tira "En vivo" inferior | Recibir mensajes en vivo | La tira inferior colapsable muestra los últimos mensajes en formato `Emisor → Receptor: texto` (máx. 20) |
| M10 | Estado desconectado | Desconectar el relay o activar modo avión en la pantalla de Mesh | Muestra el banner superior "Desconectado — mostrando datos obsoletos" manteniendo la topología visible y ofreciendo botón **Reintentar** |
| M11 | Accesibilidad y vista de lista | Pulsar el botón **Vista de lista** en la barra superior | Cambia a una lista accesible estructurada por tarjetas táctiles con touch targets ≥48 dp y descripciones semánticas completas ("Orchestrator X, working, 3 of 7 tasks") |
| M12 | Reducción de movimiento | Habilitar `ANIMATOR_DURATION_SCALE=0` en ajustes de desarrollador de Android | Las partículas de viaje y pulsaciones de trabajo se desactivan completamente, manteniendo la visualización estática de nodos y aristas |
| M13 | Ciclo de vida en segundo plano | Minimizar la app o cambiar a otra app | Se cancela la suscripción de WebSocket (`mesh.unsubscribe`); al reabrir la app (ON_START) se reconecta y resuscribe automáticamente |
