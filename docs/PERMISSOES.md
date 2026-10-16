# Matriz de permissões

Somente as combinações abaixo são autorizadas. Outras rotas e métodos são
negados. OPTIONS de preflight é respondido apenas para origens CORS autorizadas.

| Método | Rota | Anônimo | USER | ADMIN |
|---|---|---|---|---|
| POST | /auth/login, /auth/cadastro | Sim, com limites | Sim | Sim |
| POST | /auth/logout | Não | Revoga todas as próprias sessões | Idem |
| GET | /usuario | Não | Não | Lista paginada |
| GET | /usuario/{id} | Não | Somente próprio | Qualquer |
| PUT | /usuario/{id} | Não | Próprio nome/email | Nome/email de qualquer usuário |
| PUT | /usuario/{id}/saldo | Não | Não | Ajuste explícito auditado com versão |
| DELETE | /usuario/{id} | Não | Não | Desativa e revoga, sem apagar histórico |
| GET | /acao, /carta, /selo, /recompensa e /{id} | Não | Sim | Sim |
| POST | /acao, /carta, /selo, /recompensa | Não | Não | Sim |
| PUT, DELETE | /acao/{id}, /carta/{id}, /selo/{id}, /recompensa/{id} | Não | Não | Sim |
| GET | /historico | Não | Lista própria filtrada no SQL | Lista paginada geral |
| GET | /historico/{id} | Não | Próprio registro, SQL filtrado | Qualquer |
| POST | /historico | Não | Não | Sim, sem premiação automática |
| PUT, DELETE | /historico/{id} | Não | Não | Sim, sem trocar proprietário |
| GET | /usuario-carta, /usuario-fragmento | Não | Lista própria filtrada no SQL | Lista paginada geral |
| GET | /usuario-carta/{usuarioId}/{cartaId}, /usuario-fragmento/{usuarioId}/{raridade} | Não | Somente próprio usuário | Qualquer |
| POST | /usuario-carta, /usuario-fragmento | Não | Não | Sim |
| PUT, DELETE | rotas de registro das associações | Não | Não | Sim, chave/proprietário imutáveis |

USER não pode escrever pontos, resgatados, quantidades, custo ou estoque.
DTOs de cadastro/perfil não contêm esses campos e rejeitam campos desconhecidos.
Perfil ADMIN nunca é recebido pelo cadastro. A administração inicial é offline.

Autorização é repetida nos BOs; DAOs recebem o escopo do usuário para leituras.
Recurso alheio consultado por ID retorna 404, sem confirmar sua existência;
rotas administrativas retornam 403 para USER. Exclusão de conta é desativação
administrativa: o titular deve solicitar ao operador por um canal autenticado.
Tratamento de retenção/anonimização é processo operacional separado.
