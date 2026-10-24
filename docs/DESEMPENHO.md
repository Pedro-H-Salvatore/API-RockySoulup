# Otimizações de desempenho

O login devolve accessToken, tokenType, expiresIn, usuario (perfil público) e
administrador. O frontend aproveita esses campos para evitar as chamadas sequenciais
de perfil e consumo administrativo. GET /auth/sessao permite restaurar uma sessão
existente com perfil e capacidade administrativa em uma única chamada autenticada.
Os campos são somente para interface: o backend continua consultando a conta em
cada requisição, validando revogação, perfil atual, propriedade e validade do JWT.
O hash da senha nunca é devolvido. BCrypt permanece com custo 12.

O controle de tentativas continua persistente e transacional, por email e IP.
Dois incrementos e uma leitura conjunta substituem duas leituras separadas; a
limpeza de janelas expiradas passa para manutenção a cada 15 minutos. Os limites
de 10 tentativas por conta e 50 por IP a cada janela de 15 minutos são preservados.

Na análise de ações, gastos diário/mensal/por usuário, contagens e concorrência
são agregados em uma única consulta, sob o mesmo bloqueio de admissão. Reservas
incertas continuam contando mesmo quando antigas. Os contadores por minuto são
lidos juntos, e sua limpeza continua na manutenção existente. O limite diário de
créditos também usa uma agregação única. Parecer/modelo são persistidos juntos,
sem alterar faixas de aprovação, proteção de duplicidade, suspensão e orçamento.

Não há migração de schema para estas otimizações. O Gemini continua sendo chamado
sincronamente, sem retry automático e fora da transação Oracle. Seu tempo de
resposta ainda faz parte do envio da foto. A suspensão por inatividade do plano
gratuito da Render também permanece; código não elimina o tempo de retomada.
