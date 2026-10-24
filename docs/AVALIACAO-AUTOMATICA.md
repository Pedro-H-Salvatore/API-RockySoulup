# Avaliação automática de ações — v4

A nota de 0 a 100 representa compatibilidade visual com a ação, não probabilidade
de autenticidade nem detecção garantida de imagem gerada por IA.

* 0–49 e parecer INCOMPATIVEL: rejeição automática, sem pontos.
* 50–80 (inclusive): revisão administrativa com nota e feedback do Gemini.
* 81–100 e parecer COMPATIVEL: aprovação automática e crédito único.

Todas as decisões automáticas exigem desafio válido e confirmado pela leitura
do código na foto, evidência disponível, ausência de possível reutilização,
ausência de suspeita de manipulação, evidência suficiente, primeira tentativa
e a versão sustentavel-score-v4. Divergências entre nota e parecer, respostas
inválidas, falhas de rede e análise inconclusiva ficam em revisão sem pontos.
A aprovação ainda respeita os limites existentes: 3 ações/100 pontos por dia UTC
e uma aprovação da mesma ação por dia. Reprocessamentos e registros anteriores
continuam sujeitos à revisão; esta alteração não reavalia registros existentes.

Campos adicionais de /verificacoes: notaCompatibilidade (inteiro ou null),
feedback (texto não confiável do modelo, até 400 caracteres), evidenciaInsuficiente
e suspeitaManipulacao. Renderizar feedback como texto, nunca como HTML ou comando.
Falhas não recebem nota fabricada. A autorização de revisão continua exclusiva
do administrador. O mesmo ledger transacional impede crédito e giro duplicados.

## Publicação

1. Aplicar db/migration/V007__verification_compatibility_score.sql no Oracle.
   Apenas adiciona colunas opcionais e restrições; DDL tem commit implícito.
2. Publicar backend e definir AI_ENABLED=true, AI_AUTO_APPROVAL_ENABLED=true
   no ambiente Render. Chave Gemini permanece somente no backend.
3. Publicar frontend. A nota, o feedback e o motivo ficam nos detalhes existentes.
4. Consultar GET /admin/ia/consumo autenticado como administrador: habilitado=true,
   decisaoAutomatica=true, regras=sustentavel-score-v4, suspenso=false.

Manter orçamentos, limites e suspensão disponíveis. Não fazer retries automáticos
nem reenviar foto com outra chave para contornar falhas. Para suspender decisões
automáticas, definir AI_AUTO_APPROVAL_ENABLED=false e reiniciar o serviço.


### Correção v5: incompatibilidade clara e resultado do envio

Foto nítida de mão isolada, teclado ou cena claramente alheia à ação é
INCOMPATIVEL, com nota abaixo de 50 e evidenciaInsuficiente=false. Imagem
ilegível ou conteúdo impossível de julgar continua inconclusivo e em revisão.
A rejeição por incompatibilidade clara acontece antes de verificar presença
do código ou reutilização: não concede pontos e não precisa validar o código.
A aprovação continua exigindo todas as verificações de desafio, reuso, segurança
e limites. Suspeita de manipulação, falhas e evidência insuficiente continuam
em revisão. A mudança usa sustentavel-score-v5 e vale para novos envios;
não reavalia decisões existentes. Não há migração de banco.
O resultado do envio exibe nota e explicação no modal existente. Somente uma
resposta APROVADA aciona o callback de missão verificada no frontend.
