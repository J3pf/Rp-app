# Agent Mesh Screen & Redesign Architecture (v0.4.0)

This document describes the design, formatting rules, layout engine, and motion system implemented in the "Red de agentes" (Agent Mesh) screen of Rp-app.

---

## 1. UX & Visual Redesign Overview

The Agent Mesh screen visualizes the active network of coding agents connected through the relay. In v0.4.0, the UI was transformed from abstract, bare circles into informative, touch-friendly **Agent Cards** arranged in a **Hierarchical Top-Down** flow.

### Key Visual Elements
1. **Agent Cards**:
   - **Avatar**: Circular surface showing the first alphanumeric character of the agent's display name in uppercase, tinted with the machine's accent color; it falls back to `A` if the name contains no alphanumeric character.
   - **Display Name**: Human-friendly project or directory name formatted cleanly without raw IDs.
   - **Secondary Line**: Machine name paired with the current git branch or model name (e.g. `Omarchy · main` or `Croma · sonnet-3.5`).
   - **Role Badge**: Only the orchestrator card displays a role badge. Subordinate and solo-agent cards display no role badge. `Subagente` is a role label; subagents render as attached chips on their parent card, not as role-badged cards.
   - **Status Indicator**: Status dot + localized label (`Trabajando`, `Inactivo`, `Desconectado`, `Esperando respuesta`, `Error`, `Compactando`).
   - **Activity Preview**: Single-line snippet of `node.activity` or `node.todos.current`.
   - **TODO Progress Bar**: Linear progress indicator displaying completed vs total tasks.
   - **Attached Subagents**: Subagents displayed as small attached chips below the card with their own running spinners.

---

## 2. Display Name Resolution Rules

Raw identifiers (UUIDs, ULIDs, 64-bit hex strings, `subagent-chat-<hex>`, `agentId`) are strictly hidden from user-facing screens and only accessible in the collapsed "Detalles técnicos" drawer.

The pure formatting algorithm operates as follows:
1. **Candidate Validation**:
   - If `node.name` is non-empty, does not look like a UUID or ID hash, is not equal to `node.agentId` or `node.machineId`, and is not an English fallback sentinel (`"Agent"` or `"Mesh Group"`), `node.name` is accepted.
2. **CWD Basename Fallback**:
   - If `node.name` is an ID or missing, extract the trailing directory basename of `node.cwd` (e.g. `/home/drift/Workspace/RP/Rp-app` → `Rp-app`).
   - If the extracted basename is non-empty, not `"/"`, and not an ID, use it.
3. **Generic Fallback**:
   - If both fail, fallback to `"Agente"`.
4. **Duplicate Disambiguation**:
   - If multiple active nodes share the same base display name:
     - If on different machines: append `"${baseName} (${node.machineName})"`.
     - If on the same machine: append sequential index `"${baseName} (${node.machineName} · ${index + 1})"`.
     - If the name already contains a numbered suffix (e.g. `Rp-app (2)` from relay 0.5.3), the suffix is preserved without double-tagging.
5. **Group Chip Labels**:
   - If `group.isSolo`: `"Sin grupo"`.
   - If group has an orchestrator: uses the formatted display name of the orchestrator node.
   - Fallback: `"Grupo de agentes"`. English relay sentinel `"Mesh Group"` is treated as missing.

---

## 3. Top-Down Hierarchical Layout Engine

The layout engine (`MeshLayoutEngine.kt`) calculates deterministic card coordinates inside the measured viewport:
- **Level 0 (Top)**: Orchestrator node(s), centered horizontally.
- **Level 1..L (Below)**: Subordinates arranged in balanced horizontal rows below the orchestrator (`orch.y < sub.y`).
- **Bounding-Box Collision Avoidance**:
  - Every card defines an Axis-Aligned Bounding Box (AABB) using `(x, y, width, height)`.
  - Cards are spaced with horizontal and vertical margins such that `left.intersects(right) == false` for all pairs across 1..12 agents.
- **Viewport sizing and navigation**:
  - Cards have a minimum readable width of 180dp. Their heights are measured at runtime; an estimate is used until a measured height is available.
  - Auto-fit uses scale 1.0 in landscape viewports at least 600dp wide. Otherwise it fits the complete graph when possible, with a minimum auto-fit scale of 0.65.
  - If the graph still exceeds the viewport, it is top-anchored with 16dp of top padding, and pan/zoom lets the user reach the remaining content.
  - The canvas uses `Modifier.clipToBounds()` below the header and filter chips so graph content cannot draw over UI controls.
  - In compact layouts below 500dp height, the `Máquinas` and `Grupos` filters share one horizontally scrolling row.

---

## 4. Motion System & State Legend

Animations in the mesh diagram reflect **real state changes only**:

| Agent / Comm State | Visual Motion & Effect |
| --- | --- |
| **Working Agent** | Shimmering border glow + animated 3-dot typing indicator. |
| **Running Subagent** | Attached chip with active circular spinner. |
| **Comm in Flight** | Orthogonal bus connectors routed through row gaps; particles travel along the connector from sender to receiver, with a message speech bubble. |
| **Active Edge** | Glowing primary accent stroke (lasts up to 20 seconds after last message). |
| **Pending Ask** | Dashed animated orthogonal connector indicating an open question awaiting reply. |
| **Idle Agent** | Calm, static border with neutral outline (frame loop stopped to conserve CPU/battery). |
| **Offline Agent** | Muted opacity (`alpha = 0.65f`) and desaturated status indicator. |
| **Reduced Motion** | Disables continuous redraw, shimmer, and travelling particles; displays static indicators. |

Hierarchical edges use orthogonal bus connectors; a quadratic curve is only a fallback for non-hierarchical edges without a bus route. Conversation timestamps use the device locale and time zone (for example, Spanish-formatted dates and times on a Spanish-language device).

---

## 5. Technical Details Drawer (Copy Action)

Raw identifiers are secluded in a collapsed section inside `MeshNodeDetailSheet`:
- **ID de agente** (`node.agentId`) + One-tap Copy button
- **ID de máquina** (`node.machineId`) + One-tap Copy button
- **ID de grupo** (`node.groupId`) + One-tap Copy button
- **ID de agente padre** (`node.parentAgentId`) + One-tap Copy button
- **Directorio (cwd)** (`node.cwd`) + One-tap Copy button

When tapped, the button writes the value to the Android system clipboard and toggles to `"Copiado"`.
