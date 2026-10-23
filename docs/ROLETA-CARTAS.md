# Roleta após aprovação

Cada verificação aprovada com crédito confirmado libera um giro gratuito para seu
próprio usuário. Pendentes/rejeitadas não liberam giro. A API sorteia com SecureRandom,
salva o resultado em transação e usa a verificação como chave única. Repetir a mesma
requisição retorna o resultado anterior, inclusive após perder, sem novo sorteio.

GET /roletas?page=0&size=20 lista giros disponíveis. GET /verificacoes/{id}/roleta
consulta prévia/resultado. POST na mesma rota gira. Exige JWT do dono, inclusive
para ADMIN. Não aceita chance, carta, usuário, pontos nem resultado do cliente.

As chances são comum 75%, incomum 55%, rara 35%, épica 20% e lendária 10%.
Vitória entrega a carta; repetida vira 3/5/10/20/40 fragmentos conforme raridade.
Não há débito de pontos para esse giro. Foil, giros pagos, fabricação e bônus
monetários de coleção não são concedidos por este fluxo.

Aplicar V005 uma única vez após V004, com autorização do operador. Cria ACAO_CARTA
e ROLETA_RESULTADO, completa cartas ausentes e vincula as 20 ações ao catálogo.
Preserva cartas existentes, contas, saldos e coleções. Aprovações antigas com ledger
também podem resgatar seu primeiro giro; nenhuma carta é sorteada durante a migração.
