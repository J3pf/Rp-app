# Spike A0 / S2 — push con la app cerrada (canal directo vs FCM)

> Estado: **pendiente de ejecutar en hardware real**. La app ya incluye ambos canales (`DirectPushService` y
> `RpMessagingService`, sabores `direct` y `fcm`); este documento es el protocolo de medición y la plantilla de
> resultados. Las demás comprobaciones manuales están en [`../QA.md`](../QA.md).

## Qué se mide
1. **Latencia** relay → notificación visible, con la app cerrada (deslizada de recientes) y la pantalla apagada.
2. **Acción de la notificación**: pulsar **Permitir** → `POST /v1/prompts/{id}/answer` → el agente se desbloquea (objetivo < 2 s).
3. **Doze**: pantalla apagada y teléfono quieto durante 1 h; se envía un prompt cada 10 min.
4. **Batería**: consumo del canal directo en reposo durante 1 h (Ajustes → Batería → uso por app) frente a FCM.
5. **Fabricantes agresivos** (Xiaomi/Huawei/Oppo/Samsung): con y sin exclusión de la optimización de batería.

## Procedimiento
1. Instala `app-direct-debug.apk` (solo canal directo). Empareja, concede notificaciones y excluye RP de la batería.
2. Cierra la app, apaga la pantalla y, desde un agente de prueba, provoca un prompt cada 10 min durante 1 h.
3. Anota para cada prompt: hora de emisión (relay), hora de aparición, resultado de **Permitir**.
4. Repite con `app-fcm-debug.apk` desactivando "Canal directo" en Ajustes → Notificaciones, y luego con ambos activos
   (debe aparecer **una sola** notificación por prompt).
5. Repite sin exclusión de batería para medir la degradación.

## Resultados (rellenar)
| Dispositivo / Android | Sabor | Canal | Excl. batería | Prompts enviados | Entregados | Latencia p50 / p95 | Batería 1 h |
|---|---|---|---|---|---|---|---|
| | | | | | | | |

## Criterio de "hecho"
Los 6 prompts de la hora llegan por cada canal por separado; "Permitir" desbloquea al agente en < 2 s; con ambos
canales activos no hay duplicados; el consumo del canal directo es comparable al de una app de chat.
