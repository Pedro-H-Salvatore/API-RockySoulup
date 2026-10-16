# RockySoulup — API com controles de segurança

Spring Boot 4.1.1 / Java 25 / Spring MVC / JDBC / Oracle / Lombok.
Organização preservada: resource, bo, dao, to; componentes novos em security.
Diagnóstico em [docs/DIAGNOSTICO.md](docs/DIAGNOSTICO.md) e matriz completa em
[docs/PERMISSOES.md](docs/PERMISSOES.md).

## Antes de executar

1. Revise os scripts `db/migration/V001__identity_and_versions.sql` e `V002__login_limits_and_provisioning.sql`.
   Execute-os em ordem, primeiro em um ambiente Oracle isolado, com backup e usuário de migração.
   **Nenhuma migração é executada automaticamente pela aplicação.** O trabalho de implementação
   não executou esses scripts nem conectou ao Oracle real. DDL Oracle tem commits implícitos:
   rollback de migração exige restauração/revisão operacional.
2. Defina as variáveis de `.env.example` usando um gerenciador de segredos. O Spring não lê
   `.env` automaticamente: injete as variáveis no processo ou na configuração de execução do IntelliJ.
   Não coloque valores reais no Git, nos argumentos de linha de comando ou nos exemplos.
3. Gere JWT_SECRET com um gerador criptográfico: pelo menos 32 bytes aleatórios codificados em Base64.
   Não existe chave, credencial, emissor ou origem padrão. Todas as instâncias do mesmo ambiente
   devem usar a mesma chave/emissor/audiência. Ambientes diferentes precisam de valores diferentes.
4. Configure HTTPS antes de expor a API. `REQUIRE_HTTPS` é verdadeiro por padrão. Para desenvolvimento
   local apenas, pode ser explicitamente falso. Configure TLS no conector do servidor com certificado
   gerenciado e `server.ssl.*`. Em uma infraestrutura com proxy, use TLS também até a aplicação ou
   configure encaminhamento e proxies confiáveis de forma explícita; o projeto ignora headers
   encaminhados por padrão. Não confie livremente em X-Forwarded-For/Proto enviados pelo cliente.
5. Use JDK 25 e execute `mvnw.cmd test`, `mvnw.cmd package` e, após configuração/migração,
   `mvnw.cmd spring-boot:run`.

As dependências novas não fixam versões independentes: o BOM do Boot 4.1.1 gerencia
Security 7.1.1, Framework 7.0.9, HikariCP 7.0.2 e H2 2.4.240.
JdbcTemplate mantém consultas parametrizadas/PreparedStatement e integra as conexões do pool
às transações dos BOs. Falhas de banco não são convertidas em null/false.

## Primeiro administrador e usuários existentes

Não há endpoint público para promover perfil. O cadastro sempre grava USER, saldo zero,
resgatados zero, conta ativa e hash BCrypt. Campos extras, incluindo perfil, pontos,
resgatados ou hash, são rejeitados.

Contas anteriores à migração ficam inativas, sem hash e sem senha padrão. Para ativá-las,
um operador deve verificar a identidade por um canal confiável, entregar uma senha inicial
única por canal protegido e executar o provisionamento offline. Nunca substitua uma senha
existente usando esse fluxo. Redefinição de senha self-service não está disponível nesta versão;
recuperação exige processo operacional revisado, sem ativação baseada apenas em conhecer o email.

O primeiro administrador é criado apenas se não existir nenhum ADMIN. Um lock no banco
serializa o provisionamento entre instâncias. Use credenciais de operador separadas das
credenciais de runtime, em um processo controlado e sem servidor web:

```text
SPRING_PROFILES_ACTIVE=provision
SPRING_MAIN_WEB_APPLICATION_TYPE=none
PROVISION_MODE=bootstrap
PROVISION_NAME=<nome-verificado>
PROVISION_EMAIL=<email-verificado>
PROVISION_PASSWORD=<senha-inicial-unica-segura>
```

Com as demais variáveis obrigatórias injetadas, execute:

```shell
java -jar target/rockySoulup-0.0.1-SNAPSHOT.jar
```

O processo termina após provisionar. Para uma conta legada, use `PROVISION_MODE=activate`;
esse modo só ativa USER com hash NULL e conta inativa. Ele não promove perfis e não altera
contas já ativadas. Limpe as variáveis de provisionamento e o perfil antes de iniciar a API.
O provisionamento recusa modo web. Habilitar esse perfil é uma ação privilegiada de operação.

## Login e chamadas

Exemplos usam somente placeholders. Não registre corpos de login nem Authorization.

```shell
curl -X POST https://<api>/auth/cadastro -H "Content-Type: application/json" \
  -d '{"nome":"<nome>","email":"<email>","senha":"<senha-segura>"}'

curl -X POST https://<api>/auth/login -H "Content-Type: application/json" \
  -d '{"email":"<email>","senha":"<senha>"}'

curl https://<api>/acao?page=0\&size=20 -H "Authorization: Bearer <accessToken>"
curl https://<api>/historico -H "Authorization: Bearer <accessToken>"
curl -X POST https://<api>/auth/logout -H "Authorization: Bearer <accessToken>"
```

Login retorna `accessToken`, `tokenType: Bearer` e `expiresIn: 300`.
Os tokens duram cinco minutos, sem refresh token. Assinatura, HS256 permitido,
expiração sem tolerância extra, nbf, iat, emissor, audiência e duração máxima são validados
por Spring Security/Nimbus e validadores de claims. Sincronize os relógios das instâncias.
O token identifica o usuário; perfil, estado ativo e versão de revogação são consultados
no banco em cada chamada. Perfis enviados em tokens não determinam privilégios.

Logout incrementa a versão de revogação da conta e invalida **todas** as sessões.
Mudança de nome/email também exige novo login; desativação administrativa revoga imediatamente.
Se uma credencial/chave for comprometida, desative a conta/revogue sua versão ou troque
a chave em todas as instâncias. Não há lista de revogação individual por jti; a política
implementada é revogação global por conta. A chave HS256 permite emitir tokens a qualquer
serviço que a possua: restrinja o segredo somente às instâncias desta API.

## Proteções de HTTP e login

Somente Bearer no cabeçalho Authorization é aceito; não há sessão, login por formulário,
HTTP Basic, token em query string ou autenticação por cookie. CSRF foi desativado por esse
motivo. Se cookies forem introduzidos, a política CSRF deve ser revista antes da liberação.
No navegador, evite armazenamento persistente de tokens e proteja a aplicação contra XSS.

CORS usa apenas a lista explícita `CORS_ALLOWED_ORIGINS`, separada por vírgulas; sem wildcard
e sem credenciais/cookies. Origem CORS não substitui autenticação. Preflights de origens
não autorizadas retornam 403 JSON. Corpos são limitados a 16 KiB, inclusive com leitura
sem Content-Length; conteúdo com corpo deve ser application/json. Cabeçalhos: 8 KiB.
Paginação: page 0–10000, size 1–100, padrão 20. Há timeout de conexão de 10 s;
configure também limites de conexões, taxa global e tempo de upload no proxy.

O limitador compartilha `AUTH_LOGIN_ATTEMPT` entre todas as instâncias e usa incrementos
atômicos em transação independente. A cada janela fixa UTC de 15 minutos, permite no máximo
10 tentativas por email normalizado e 50 por endereço remoto. Cadastro também consome os limites.
Sucesso não zera os contadores. Após o limite, retorna 429 com Retry-After: 900;
uma janela fixa pode admitir uma rajada na fronteira entre janelas.
Não aceite IP de um header arbitrário. Atrás de proxy sem configuração de confiança, o IP
será o do proxy, tornando o limite mais restritivo para os clientes compartilhados.
As instâncias precisam compartilhar banco e relógios sincronizados. Registros de janelas antigas
são removidos durante novas tentativas. Falha de banco bloqueia a autenticação, sem liberar acesso.
O limite por email permite bloqueio temporário deliberado de uma conta por terceiros;
monitore os eventos e use proteção adicional de borda para abuso distribuído.

Senhas novas: 12–72 caracteres e no máximo 72 bytes UTF-8, sem truncamento silencioso.
BCrypt custo 12. Senhas e hashes não aparecem em DTOs de resposta, toString ou eventos.
O token aparece somente na resposta explícita de login. Não habilite DEBUG/TRACE de JDBC,
Security ou logging de corpos/cabeçalhos em produção. Eventos de segurança contêm apenas
tipo e ID do ator quando aplicável; alterações são registradas após o commit.

Erros JSON: 400 entrada inválida; 401 autenticação ausente/inválida; 403 permissão negada;
404 recurso ausente ou de outro titular; 409 conflito de chave/versão; 413 corpo grande;
415 mídia inválida; 429 limite de login; 503 falha de persistência; 500 falha interna genérica.
Respostas não expõem SQL, credenciais, stack traces ou valores rejeitados.

## Integridade e concorrência

CRUDs de catálogo, histórico e associações são exclusivos de ADMIN. Histórico administrativo
nunca credita pontos automaticamente. Não há rota USER de resgate, fabricação, premiação ou
ajuste de coleção: faltam regras de negócio e comprovação da operação, portanto estão bloqueadas.

Ajustes administrativos de saldo usam `PUT /usuario/{id}/saldo` com `pontos`, `resgatados`
e `versao`. Perfil usa `PUT /usuario/{id}` somente com `nome`, `email`, `versao`.
Todo registro expõe `versao`. Updates fazem `WHERE chave = ? AND NR_VERSAO = ?` e incrementam
a versão no mesmo comando. DELETE exige `?versao=<versao-atual>`. Repetir uma escrita com
a versão antiga retorna 409; leia o estado antes de decidir uma nova operação. Não repita
automaticamente usando uma versão atualizada, pois isso representa uma nova autorização.

Constraints de banco e validação impedem saldos, quantidades e estoques negativos.
Transações dos BOs compartilham a conexão do Spring e fazem rollback em exceções.
Histórico não permite trocar proprietário; associações não permitem trocar a chave/proprietário
pelo JSON de update. Consultas de USER são filtradas no SQL e novamente autorizadas no BO.

Exclusão de usuário é desativação administrativa, não remoção física. Registros de histórico
e coleção permanecem. Não há reativação pública, promoção pública ou exclusão definitiva de conta.
Defina retenção, anonimização e atendimento a solicitações do titular operacionalmente.

## Oracle: privilégios mínimos

Use um usuário de runtime diferente do dono do schema e do operador de provisionamento.
Conceda CREATE SESSION e somente SELECT/INSERT/UPDATE em USUARIO; SELECT/INSERT/UPDATE/DELETE
nas sete tabelas de catálogo/histórico/associações e em AUTH_LOGIN_ATTEMPT.
Não conceda DBA, DDL, ANY TABLE, GRANT OPTION ou acesso à AUTH_BOOTSTRAP_LOCK ao runtime.
O operador offline precisa de SELECT/UPDATE no lock e dos privilégios de USUARIO necessários.
Se runtime e schema owner forem distintos, use sinônimos privados ou CURRENT_SCHEMA configurado
pelo DBA; sinônimos não concedem privilégios. Proteja também o tráfego e o segredo do Oracle.
Revogue/rotacione as credenciais antes embutidas no código usando o procedimento do responsável
pelo banco; apagar o código não invalida cópias/ZIPs/histórico anteriores.

## Compatibilidade e limites da entrega

Veja [docs/MUDANCAS-CLIENTES.md](docs/MUDANCAS-CLIENTES.md) para mudanças incompatíveis.
Os testes usam H2 em memória, modo Oracle, com chaves JWT efêmeras e dados fictícios.
O schema de teste aproxima tipos/constraints e não substitui homologação dos scripts Oracle.
Não foi realizada verificação de implantação, TLS real, permissões do banco, operação em cluster
ou análise completa de dependências/CVEs. A suíte verifica os controles implementados;
JWT por si só não certifica que a implantação seja segura.

Backups e ZIPs antigos ainda podem conter as credenciais removidas; trate-os como sensíveis.
Não implemente operações financeiras futuras sem transação, validação de saldo/estoque,
identificação idempotente da operação e testes de concorrência específicos da regra.
# Integração segura de IA

Veja [docs/SEGURANCA-IA.md](docs/SEGURANCA-IA.md) para ativação, novos endpoints,
limites financeiros, migração V003 e decisões de revisão manual. A IA fica
desabilitada por padrão. As chaves pertencem ao arquivo privado de configuração.

