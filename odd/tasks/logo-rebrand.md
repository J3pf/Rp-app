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
- [ ] T4 (S2) Parar aquí: no se toca ningún recurso de icono hasta la elección (Fase E). Commit: —

## Direcciones
1. `01-caret-nodo.html` — **Caret + nodo** (pictograma). Compra: se entiende de un vistazo. Paga: el más literal.
2. `02-monograma-r.html` — **Monograma R** (tipografía). Compra: escala impecable y monochrome perfecto. Paga: no dice «agentes» sin el wordmark.
3. `03-nodos.html` — **Nodos** (sistema/org chart). Compra: cuenta la malla y los subagentes. Paga: el más frágil a 24 px.

## Log
- L1: "2) rediseñar el logo por que no me gusta para nada, debe ser minimalista y moderno"
- L2: Orquestador: "Phase B: 2-3 minimalist modern directions under `design/<slug>/` with the gentle-design skill, each shown as launcher icon at small size + dark/light + the in-app mark. Open the canvas and report the URL to me for the user's choice. Do not touch icon resources yet."
- L3: Estado de partida del icono actual: `app/src/main/res/drawable/ic_launcher_foreground.xml` dibuja una burbuja de chat con «RP» recortado (3 paths) sobre `#4F378B`; esa acumulación de detalle es justo lo que no aguanta 24 px. Las direcciones nuevas van en la dirección contraria: menos formas, más contraste.
- L4: Tres artboards escritos y `design.json` publicado. Cada uno muestra el icono a 168 px, la tira a tamaño real (48/32/24/16), el fondo noche/día, la capa monocroma y la marca dentro de la app con contenido real («¿Permitir rm -rf dist?», «Terminó el refactor de notificaciones»). Nada de recursos de icono tocados: S2 sigue intacta.
