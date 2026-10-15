# Feature: Integración quirúrgica de piQuota en Rp-app
Implementación de cuotas por máquina en Rp-app mediante isla dinámica / pills en la parte inferior, desacoplándose de Moshi y tolerando la ausencia de piQuota.

## Specs
S1: "Integrar piquota a rp eliminando la integración con moshi"
S2: "integrar piquota (de manera quirúrgica ya que si el dev no desea usar piquota rp no debe fallar por esto)"
S3: "para que de forma muy ux/un y parecido a lo que hace moshi, nos muestre en la parte de abajo con pills o con algo similar a una isla dinámica, cada una de las quotas POR MAQUINA ya que cada maquina puede tener quotas y providers diferentes"
S4: "me gusta la idea de poder deslizar, elige tu lo mas bonito y mas responsable y respetuoso de las buenas practicas de UX/UI"
S5: "no debes seguir integrando codigo a la parte de moshi" y "no modifiques otros proyectos diferentes a este para eso estan los otros agentes"
S6: "NO hagas todo tu, usa odd y sus subagentes"

## Tasks
- T1: Modelado de protocolo y serialización (QuotaWindow, MachineQuota, MachineSummary.quotas) [S1, S2] -> route: gentle-ai-worker -> commit: 6df9abb
- T2: Persistencia en base de datos Room y mapeo de dominio [S2] -> route: gentle-ai-worker -> commit: aee1e64
- T3: Componente Compose QuotaDynamicIsland (pills, deslizamiento por máquina, detalles de cuota) [S3, S4] -> route: gentle-ai-worker -> commit: 6308cb5
- T4: Integración en AgentsScreen sobre barra de navegación [S3, S4] -> route: gentle-ai-worker -> commit: 1b49c7e
- T5: Pruebas unitarias y de Compose para verificación quirúrgica [S2, S3, S4] -> route: gentle-ai-verify -> commit: in_progress

## Log
- L1: "Intención: Integrar piquota a rp eliminando la integración con moshi. Detalles: piQuota es un monitor de cuotas y actualmente tiene una integración a medias con moshi, la idea es integrar piquota (de manera quirúrgica ya que si el dev no desea usar piquota rp no debe fallar por esto) para que de forma muy ux/un y parecido a lo que hace moshi, nos muestre en la parte de abajo con pills o con algo similar a una isla dinámica, cada una de las quotas POR MAQUINA ya que cada maquina puede tener quotas y providers diferentes. Ejemplos: moshi ya te lo nombre. Acción: integración quirúrgica de piQuota en rp debes charlar con los demás agentes segun sea donde requieras información o ajustes. Limites: no debes seguir integrando codigo a la parte de moshi. no modifiques otros proyectos diferentes a este para eso estan los otros agentes. NO hagas todo tu, usa odd y sus subagentes. si tienes dudas antes de arrancar, solo dime"
- L2: "me gusta la idea de poder deslizar, elige tu lo mas bonito y mas responsable y respetuoso de las buenas practicas de UX/UI"
