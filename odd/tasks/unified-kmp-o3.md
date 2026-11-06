# unified-kmp-o3 — chat slice (Obrero 3)

Feature doc of worker O3 (session subagent-chat-01a1af49-2949-74e2) inside the migration
specified in `odd/tasks/unified-kmp.md` (read by O3 down to `## Log`). Branch `unify/o3-chat`,
worktree `.worktrees/o3`. Mirror: Engram `odd/unified-kmp-o3/tasks`.

## Specs

Quoted verbatim from `odd/tasks/unified-kmp.md` (parent spec):

- **S5** "asegurando que no se pierda ni una funcionalidad ni que se introduzcan bugs"
- **S11** — "Source of truth for behavior is the Android code on `main`. Behavior = what the Android app does today (README "Main features", CHANGELOG, `docs/QA.md`, `docs/MESH.md`, tests). The `:app` module is migrated into shared code; it is the oracle."
- **S15** — "Every observable behavior of the Android app maps to a row in a parity matrix with a proof… Existing tests are never weakened, deleted or re-recorded to get green. Roborazzi goldens… are not re-recorded without orchestrator approval."
- **S19** "Diff viewer keeps its user-driven discard confirmation before `host.git_discard`."
- **S6** "QUE LOS 4 usean ODD y sus sub agentes SI o SI"
- **S16** — KMP purity: no `android.*`, `androidx.*` (Android-only), `javax.crypto.*` imports in commonMain.
- **S20** — Done means the four Gradle test tasks have zero failures.

Slice briefs from the orchestrator (T1 and T5), paraphrase of the assignment:
T1 must produce `docs/parity/o3-chat.md` (columns: ID | behavior incl. edge cases/exact strings |
Android source file:line | proof test | status) covering every README "Main features"/CHANGELOG/
docs/QA.md item of the chat slice, plus characterization tests on UNCHANGED Android code for rows
lacking a proof, all green, no production change; plus a "Platform ports required" section.
T5 (later, after orchestrator go) migrates the slice through shared code.

Behaviors to cover (orchestrator brief): live streaming + bottom-anchored list (reverseLayout) +
animated "More recent (N)" button preserving read position, message history, tool tracking,
attachments, diffs + discard confirmation + biometric interaction (S19), slash commands and
autocomplete (/clear /compact /model /thinking /quit /undo /sdd-status /help) + thinking-level
chips (off, minimal, low, medium, high, xhigh, max), PromptBottomSheet select mode, review consent
surface (danger: high renders terminal body verbatim in monospace, keeps three options and
declared effects in order, labels the session-wide grant, sends {cancelled:true} when dismissed),
pending questions/authorizations screen, new-agent flow, 48dp touch targets and reduced-motion.

## Tasks

| ID | Specs | Task | Commit |
|---|---|---|---|
| O3-T1 | S5 S11 S15 | Parity matrix `docs/parity/o3-chat.md` + characterization tests on unchanged Android code + platform-ports section | |
| O3-T5 | S15 S19 | Migrate chat slice through shared code (waits for orchestrator go) | |

## Log

- **L1** (orchestrator, verbatim brief): "YOUR SLICE (T1 now, then T5): Android packages
  dev.j3pf.rp.ui.{chat, promptsheet, pending, newagent, components} and shared-ui
  ui/{chat, diff, promptsheet, pending, newagent, components}, with all their tests." Full text in
  the orchestrator message of 2026-11-06 (intercom seq 3, id e561f674…).
- **L2** (O3): consulting gentle-ai-explore `mw4jqhsg-1-refq` to inventory the chat slice before
  writing the parity matrix.
- **L3** (O3): explore subagent failed on provider quota; compensated with direct bounded reads of
  README/QA/CHANGELOG and the slice sources (matrix still cites every row).
- **L4** (O3): parity matrix `docs/parity/o3-chat.md` written: 39 rows, headers, exact strings,
  ports P-PICK/P-BIO/P-VOICE/P-HAPTICS/P-IME requested from O1.
- **L5** (orchestrator correction, verbatim): characterization tests must be GREEN on first run
  (T1 is on unchanged Android code); RED-first applies to T5+. If a new test is RED on unchanged
  code, fix the test or record the row as `defect` with file:line — never fix production in T1.
  Observable behavior only (exact strings, wire payloads, nothing changes on rejection).
- **L7** (orchestrator, verdict Gate A rev 1 = FAIL): rebase first; strengthen the two files (path assertions, dialog-close assertions, real S19 vm→wire chain, app wrapper title); pin option order/effects/chip/monospace/wire JSON; migrate to the jslive junit4.v2 rule; recompute the summary from rows; add ~14 missing rows (QA C1/C3/C5/C9, A1/A2/A4, N4-N7, CHANGELOG density + settled subagents + v0.6.1 close failures + U6/U8 + BiometricGate freshness); close the pure-UI group then the harness group; no Roborazzi (host drift on unchanged `main`, 17/23 failing — verifier confirmed); mark screenshot rows `golden - unverified on this host`; at resumed READY run verbs: `READY T1 (rev 2)`.
- **L8** (O3): rev 2 executed on unchanged code: rebase done (57aad06); DiffDiscardConfirmationTest strengthened + DiffSheetWrapperTest + ReviewConsentCharacterizationTest strengthened (order/pairing/chip/no-reflow/wire JSON, junit4.v2) → c90de9e; harness group inline (worker launch refused by writer rule; recorded verbatim per orchestrator note): ChatViewModelCharacterizationTest (18 tests: CH17/18/19/21/22/24/25/26/31 + S19 vm→wire + close-failure v0.6.1) + PromptSheetViewModelCharacterizationTest (2 tests CH34) → cfe3828; pure-UI group ChatUiCharacterizationTest (12 tests) → 828ea81; matrix rev-2 rewrite with grep-verified citations and computed summary; A1 compression probe removed (Robolectric decode null — host drift documented; row stays gap with QA step). Focused final run: 59 chat-prompt tests / 0 failures.
- **L6** (O3): T1 characterization added on the two high-risk surfaces, green-first, no production
  edits: `DiffDiscardConfirmationTest` (S19, CH30: dialog-first, exactly-once with path, cancel
  keeps file) + `ReviewConsentCharacterizationTest` (CH33: verbatim order/effects, session-wide
  chip on id 2 only, Cancelar → `Answer.Cancelled`, option → `Answer.option(id)`). Focused run
  `:app:testDirectDebugUnitTest --tests chat.* --tests promptsheet.*` → 22 tests, 0 failures.
  No test weakening, no Roborazzi touches, no production change; remaining rows need O1's fake
  repos (T5) or device/manual E2E (QA cases).
