# Feature: Rediseño del logo RP — direcciones visuales
Branch: `feat/logo-rebrand`
Status: In Progress (esperando la elección del usuario)

## Specs
S1 "rediseñar el logo por que no me gusta para nada, debe ser minimalista y moderno"
- 2-3 direcciones autónomas bajo `design/<slug>/`, cada una con el icono de lanzador a tamaño pequeño, en
  claro y oscuro, y la marca dentro de la app.
- Minimalista de verdad: pocas formas, sin gradientes ni sombras, legible a 16-24 px.
- Moderno: monocromo primero, apto para icono adaptativo de Android (foreground/background/monochrome) y
  para la máscara de iOS a sangre.

S2 "no tocar recursos de icono todavía"
- Esta fase es solo `design/`: no se modifican `app/src/main/res/**` ni el proyecto de Xcode.

## Tasks
- [x] T1 (S1) Gate de gentle-design: `list` sin fuentes de diseño ni `design/system.md` → diseñar libre. Commit: —
- [x] T2 (S1) Tres direcciones con estrategias distintas (pictograma, monograma, sistema) + `design.json`. Commit: —
- [x] T3 (S1) Abrir el canvas y reportar la URL para la elección del usuario. Commit: —
- [x] T4 (S2) Parar aquí hasta la elección: no se toca ningún recurso de icono. Commit: 3c1f88d
- [x] T5 (S2) Android: capas del icono adaptativo (foreground con caret+nodo, background noche, `monochrome` reutilizando el foreground), colores `rp_launcher_bg/fg`, e icono de barra de estado en silueta. Commit: este commit
- [x] T6 (S2) iOS `AppIcon.appiconset`: 1024 px opaco (sin alfa) + `Contents.json` con el filename. Commit: este commit
- [x] T7 (S2) Test estructural del cableado (`BrandingAssetsTest`) + gates + PR. Commit: este commit

## Direcciones
1. `01-caret-nodo.html` — **Caret + nodo** (pictograma). Compra: se entiende de un vistazo. Paga: el más literal.
2. `02-monograma-r.html` — **Monograma R** (tipografía). Compra: escala impecable y monochrome perfecto. Paga: no dice «agentes» sin el wordmark.
3. `03-nodos.html` — **Nodos** (sistema/org chart). Compra: cuenta la malla y los subagentes. Paga: el más frágil a 24 px.

## Log
- L1: "2) rediseñar el logo por que no me gusta para nada, debe ser minimalista y moderno"
- L2: Orquestador: "Phase B: 2-3 minimalist modern directions under `design/<slug>/` with the gentle-design skill, each shown as launcher icon at small size + dark/light + the in-app mark. Open the canvas and report the URL to me for the user's choice. Do not touch icon resources yet."
- L3: Estado de partida del icono actual: `app/src/main/res/drawable/ic_launcher_foreground.xml` dibuja una burbuja de chat con «RP» recortado (3 paths) sobre `#4F378B`; esa acumulación de detalle es justo lo que no aguanta 24 px. Las direcciones nuevas van en la dirección contraria: menos formas, más contraste.
- L4: Tres artboards escritos y `design.json` publicado. Cada uno muestra el icono a 168 px, la tira a tamaño real (48/32/24/16), el fondo noche/día, la capa monocroma y la marca dentro de la app con contenido real («¿Permitir rm -rf dist?», «Terminó el refactor de notificaciones»). Nada de recursos de icono tocados: S2 sigue intacta.
- L5: Elección del usuario: **dirección 01, Caret + nodo** (handoff `rp-icon/01-caret-nodo.html`). Aplicada tal como está dibujada, con **una sola transformación declarada**: traslación `dx = -6.5` en la rejilla de 108dp. Motivo numérico: la caja del artboard va de x 35 a 86 (centro 60.5) y el icono adaptativo se recorta con máscara circular centrada en 54; el artboard se pinta sobre una loseta cuadrada donde ese desfase no se ve. Tamaños, trazo (10), radio del nodo (8) y separación caret-nodo son los del artboard. Caja resultante x 28.5..79.5, y 27..81; punto más lejano al centro 25.5 < 33 (radio de la zona segura de 66dp), así que ninguna máscara recorta la marca.
- L6: Observación honesta a tamaño pequeño, no "arreglada" en silencio: con la geometría del artboard el nodo roza el vértice del caret (se solapan 1 unidad, igual que en el artboard), así que a 24-32 px se lee como un `>` con un punto pegado. Una versión con 2.5 unidades de separación se lee algo mejor a 32 px, pero es otro dibujo: queda a decisión del usuario, no aplicada. Render de la versión fiel: 1024 opaco verificado con `sips` (`hasAlpha: no`).
- L7: Gates observados con la marca aplicada: `:app:testDirectDebugUnitTest` 222/222 (incluye `BrandingAssetsTest` 2/2), `:shared-ui:jvmTest` 102/102, `:app:ktlintCheck` y `:shared-ui:ktlintCheck` verdes, `:app:lintDirectDebug` BUILD SUCCESSFUL con los mismos 8 avisos preexistentes (3 de versión de AGP, 5 de recursos sin usar), ninguno nuevo. Sin pirámide de PNG legacy: `minSdk = 29` y el icono adaptativo cubre todas las densidades. Firma de release intacta, sin subir versión, sin tag.
