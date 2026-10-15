# Feature: Experiencia de Agentes Enriquecida en RP (TODOs, Subagentes Gentle Shell y Comandos Slash)
Implementación integral y coordinada a través de Rp-plugin, Rp-relay y Rp-app para soporte de TODOs por agente, visualización completa de subagentes estilo Gentle Shell y ejecución transparente de comandos slash en cualquier agente.

## Specs
S1: "Ver los todos y el avance que tiene cada agente"
S2: "Ver cuando se cree un subagente los datos como los muestra gentle shell, modelo, tiempo, thinking, etc etc"
S3: "Poder ejecutar comandos / en cualquier agente sin problema"
S4: "debes coordinarte con tus compas de RP (el de app, el de plugin, el de relay) para que cada uno haga los ajustes que considere en su propio dominio(repo) USANDO ODD y SUS subagentes y tu eres el que me va a responder a mi por que todos hagan todo bien, con evidencias y pruebas, SI ALGO QUEDA MAL O INCOMPLETO O DE BAJA CALIDAD, SERA TÚ CULPA"

## Tasks
- T1: Alineación de especificación y contrato de protocolo entre repositorios [S1, S2, S3, S4] -> route: orchestrator
- T2: Rp-plugin: Extracción y emisión de TODOs (`agent.todos`), telemetría de subagentes (`agent.subagent_update`) y despacho nativo de comandos slash con `expandPromptTemplates: true` [S1, S2, S3, S4] -> route: peer-Rp-plugin
- T3: Rp-relay: Persistencia en `PERSISTED_EVENT_TYPES` para `todo_update` y `subagent_update`, soporte en historial y paso de comandos [S1, S2, S3, S4] -> route: peer-Rp-relay
- T4: Rp-app: Contrato en `protocol/Models.kt`, procesamiento en `ChatReducer`, autodespacho de comandos en `ChatViewModel` [S1, S2, S3, S4] -> route: peer-Rp-app (COMPLETED - Models, Reducer y ViewModel listos con tests unitarios)
- T5: Rp-app: Componentes UI Compose (Checklist/avance de TODOs en `AgentsScreen` y `ChatScreen`, `SubagentCard` estilo Gentle Shell en `ChatMessages.kt`) [S1, S2, S3] -> route: peer-Rp-app (COMPLETED - SubagentCard, TodoBanner, suggestions y chip slash)
- T6: Verificación cruzada integral, compilación, suites de tests y recolección de evidencias [S1, S2, S3, S4] -> route: orchestrator (Rp-app: Gradle compileDirectDebugKotlin y ./gradlew test BUILD SUCCESSFUL)

## Log
- L1: "Te voy a pedir un cambio bastante grande, necesito: -Ver los todos y el avance que tiene cada agente -Ver cuando se cree un subagente los datos como los muestra gentle shell, modelo, tiempo, thinking, etc etc - Poder ejecutar comandos / en cualquier agente sin problema debes coordinarte con tus compas de RP (el de app, el de plugin, el de relay) para que cada uno haga los ajustes que considere en su propio dominio(repo) USANDO ODD y SUS subagentes y tu eres el que me va a responder a mi por que todos hagan todo bien, con evidencias y pruebas, SI ALGO QUEDA MAL O INCOMPLETO O DE BAJA CALIDAD, SERA TÚ CULPA"
- L2: "T4 y T5 implementados en Rp-app: Modelos TodoItem/TodoState/SubagentInfo/TokenUsage, reducción reactiva en ChatReducer, autodespacho en ChatViewModel, SubagentCard estilo Gentle Shell, TodoBanner expandible en ChatScreen y chip de avance en AgentsScreen. Verificado con ./gradlew test y compileDirectDebugKotlin con éxito."
