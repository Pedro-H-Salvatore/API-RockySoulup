# Mudanças que afetam os clientes

- Todas as chamadas, salvo POST /auth/login e /auth/cadastro, exigem Authorization: Bearer.
- POST /usuario foi retirado: cadastro agora é /auth/cadastro, com nome/email/senha.
- Cadastro não aceita perfil, pontos, resgatados, id, hash ou estado ativo.
- PUT /usuario/{id} recebe somente nome, email e versao. Campos extras retornam 400.
- Respostas de usuário usam UsuarioResponse; UsuarioTO foi retirado e substituído por DTOs específicos.
- Saldos administrativos usam /usuario/{id}/saldo; USER não pode ajustar o próprio saldo.
- Escrita nos demais CRUDs exige ADMIN; USER só lê catálogos e seus próprios registros.
- A listagem de usuários exige ADMIN. Listas de histórico/cartas/fragmentos de USER são próprias,
  mesmo que usuarioId seja fornecido em query. Consultas a registro alheio retornam 404.
- Listas são paginadas por page/size. Atualize clientes que esperavam uma lista ilimitada.
- Registros incluem versao. Reenvie a versão lida no PUT; DELETE exige query versao.
- POST retorna ID gerado nas entidades de chave simples. HTTP 201 só ocorre após inserção.
- Atualizações de perfil invalidam tokens anteriores; o cliente precisa fazer novo login.
- Logout revoga todas as sessões. Desativação administrativa impede novos logins.
- DELETE /usuario desativa a conta, preservando registros associados; exige ADMIN e versao.
- Banco indisponível não equivale a 404: retorna 503, ou 401 na verificação de identidade
  da cadeia de autenticação, sempre com acesso negado. Não use esses erros para inferir existência.
- 409 indica conflito de chave, referência ou versão; não repetir automaticamente sem revisão.
- Senhas obedecem ao mínimo de 12 caracteres e teto de 72 bytes UTF-8.
- Corpos acima de 16 KiB e origens CORS não configuradas são bloqueados.
- Contas antigas precisam de ativação offline antes de usar login.
