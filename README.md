# RockySoulup API

API REST para registro de ações sustentáveis, administração de usuários e pontos,
histórico, cartas, fragmentos, selos e recompensas. A integração Gemini analisa
imagens de evidência e encaminha o resultado para revisão administrativa.

**Java 25 · Spring Boot 4.1.1 · Spring MVC · Spring Security/JWT · JDBC · Oracle**

Este guia explica como configurar, executar e consumir as 52 rotas da API.
Os exemplos usam dados fictícios; substitua os IDs pelos valores retornados
pelo seu ambiente. Nenhum exemplo contém credenciais reais.

> A IA fica desabilitada por padrão. Resultado favorável da IA não aprova uma
> ação automaticamente: somente ADMIN pode revisar e conceder os pontos.
> Publicar este código no GitHub não hospeda a API.

## Sumário

- [1. Configuração do ambiente local](#1-configuração-do-ambiente-local)
- [2. Preparação e execução](#2-preparação-e-execução)
- [3. Convenções HTTP e JSON](#3-convenções-http-e-json)
- [4. Autenticação e identificação do usuário](#4-autenticação-e-identificação-do-usuário)
- [5. Endpoints de autenticação e CRUD](#5-endpoints-de-autenticação-e-crud)
- [6. Paginação e propriedade](#6-paginação-e-propriedade)
- [7. Corpos de cadastro e atualização das entidades](#7-corpos-de-cadastro-e-atualização-das-entidades)
- [8. Controle de versão, repetição e exclusão](#8-controle-de-versão-repetição-e-exclusão)
- [9. Erros e reação do cliente/IA](#9-erros-e-reação-do-clienteia)
- [10. Exemplo de cliente JavaScript](#10-exemplo-de-cliente-javascript)
- [11. Administração e contas legadas](#11-administração-e-contas-legadas)
- [12. Verificação de ações sustentáveis com IA](#12-verificação-de-ações-sustentáveis-com-ia)
- [13. Postman e solução de problemas](#13-postman-e-solução-de-problemas)
- [14. Estrutura, testes e publicação](#14-estrutura-testes-e-publicação)
- [15. Documentação complementar](#15-documentação-complementar)

## 1. Configuração do ambiente local

| Configuração | Valor |
|---|---|
| Base URL | `http://localhost:8080` |
| Alternativa de acesso na mesma máquina | `http://127.0.0.1:8080` |
| Interface de escuta | `127.0.0.1`, acesso somente na própria máquina |
| Porta | `8080` |
| Emissor JWT | `http://localhost:8080` |
| Audiência JWT | `rockysoulup-api-local` |
| Algoritmo aceito | `HS256` |
| Validade do token | `300` segundos, sem refresh token |
| HTTPS obrigatório localmente | `false` |
| Origem de frontend permitida | `http://localhost:3000` |
| Origem de frontend permitida | `http://127.0.0.1:3000` |
| Origem de frontend permitida | `http://localhost:5173` |
| Origem de frontend permitida | `http://127.0.0.1:5173` |

Crie ou preserve as configurações privadas em:

```text
config/local-secrets.properties
```

O arquivo é ignorado pelo Git. Em uma instalação nova, gere JWT_SECRET com pelo
menos 32 bytes criptograficamente aleatórios codificados em Base64. Não leia, copie ou exponha o segredo para integrar
um cliente: somente o backend precisa da chave. Não coloque credenciais Oracle ou
JWT_SECRET no frontend, no Markdown, em logs ou em arquivos publicados.

O Spring importa esse arquivo por caminho relativo à pasta de execução. Execute a
aplicação com o diretório de trabalho `<pasta-do-repositorio>`.
Variáveis de ambiente externas podem substituir configurações locais. Se o
comportamento divergir, confira as variáveis na configuração de execução do IntelliJ.

Esta configuração HTTP e de loopback é exclusiva para desenvolvimento local.
Quando houver hospedagem, configurar HTTPS, novas origens CORS, segredos por
ambiente e endereço de escuta apropriado; não publicar o arquivo local.


### Arquivo privado de configuração

Para quem clonou o repositório pela primeira vez, crie a pasta `config` e o
arquivo acima com este modelo. Substitua os placeholders; se o arquivo já existe,
preserve os valores que já estão configurados. O Spring não lê `.env` automaticamente.

```properties
DB_URL=jdbc:oracle:thin:@//<host>:<porta>/<servico>
DB_USERNAME=<usuario_de_execucao>
DB_PASSWORD=<senha_do_banco>
JWT_SECRET=<32_bytes_aleatorios_em_base64>
JWT_ISSUER=http://localhost:8080
JWT_AUDIENCE=rockysoulup-api-local
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000,http://localhost:5173,http://127.0.0.1:5173
REQUIRE_HTTPS=false
server.address=127.0.0.1
server.port=8080
AI_ENABLED=false
```

GEMINI_API_KEY é opcional enquanto AI_ENABLED=false. Quando
ativar a integração, acrescente-a somente à configuração privada do backend.
Ela não é necessária para cadastro/login e CRUD.

Para gerar a chave JWT no PowerShell 7+, execute a expressão abaixo na sua
máquina e guarde o resultado somente no campo JWT_SECRET do arquivo privado:

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Não compartilhe a saída nem a chave. Use segredos diferentes entre ambientes.
Variáveis de ambiente podem substituir propriedades do arquivo privado.

## 2. Preparação e execução

Pré-requisitos:

1. JDK 25 configurado no IntelliJ/terminal.
2. Oracle acessível, com o schema original do RockySoulup.
3. Migrações abaixo revisadas e aplicadas por um operador autorizado:
   - `db/migration/V001__identity_and_versions.sql`
   - `db/migration/V002__login_limits_and_provisioning.sql`
   - `db/migration/V003__verification_ai_controls.sql`
4. Configuração local descrita acima preservada.

Os scripts são migrações das tabelas originais, não um instalador de banco vazio.
O schema H2 de `src/test/resources` é exclusivo dos testes. Use uma conta de
runtime com privilégios mínimos e uma conta de operador separada para migrar.

**As migrações não foram executadas durante este trabalho e não são automáticas.**
Não as execute contra um banco compartilhado ou real apenas por ler este guia;
obtenha autorização específica para alterar o schema. Oracle DDL tem commits
implícitos; o operador deve preparar backup e homologação.

PowerShell, a partir da raiz do projeto:

```powershell
# Execute a partir da pasta que contém pom.xml.
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

No IntelliJ, execute a classe `br.com.rockysoulup.RockySoulupApplication` com JDK 25
e a raiz do projeto como diretório de trabalho. Não habilite o perfil `provision`
na execução normal da API.

O comando `test` usa H2 descartável e dados fictícios, sem acessar o Oracle real.
A validação completa da implementação aprovou 97 testes com dados fictícios,
H2 e mocks dos provedores; isso não substitui homologação Oracle ou testes da conta
do provedor. Nenhuma chamada paga foi usada nessa validação.
Não existe endpoint público de health check. Sem token, `GET /acao` responde 401
se a API estiver atendendo; conexão recusada significa que o servidor não está acessível.


Para gerar e executar o JAR:

```powershell
.\mvnw.cmd package
java -jar .\target\rockySoulup-0.0.1-SNAPSHOT.jar
```

No Linux/macOS, use `./mvnw`. O Wrapper acompanha o repositório e pode baixar
dependências na primeira execução. Confira o JDK com `java -version` e a JVM do
Maven com `.\mvnw.cmd -version`. O projeto exige JDK 25.

No IntelliJ, importe `pom.xml` como projeto Maven e configure Project SDK/JRE 25.
Em Run → Edit Configurations, confirme o Working directory e as variáveis de
ambiente. Aguarde a inicialização do servidor antes de chamar a API.

```powershell
curl.exe -i http://localhost:8080/acao
```

Sem Bearer, 401 é esperado. Não há Swagger UI, página inicial ou endpoint público
de health check implementado. Abrir `/` no navegador não consulta o catálogo.

## 3. Convenções HTTP e JSON

- Enviar `Content-Type: application/json` em chamadas com corpo JSON.
- Em rotas protegidas, enviar `Authorization: Bearer <accessToken>`.
- Token em cookie ou query string não autentica. Não usar HTTP Basic.
- Não enviar corpos em GET/DELETE/logout. DELETE de CRUD usa a query `versao`;
  DELETE de evidência não usa versão.
- JSON usa nomes em camelCase, com maiúsculas/minúsculas exatas.
- Campos desconhecidos são rejeitados com 400. Não enviar o objeto de resposta
  completo em DTOs de perfil/cadastro/login.
- IDs são inteiros positivos de 64 bits; números de domínio são inteiros de 32 bits.
  Não arredondar IDs grandes ao processar JSON no cliente.
- `versao` é inteiro de 64 bits, maior ou igual a zero.
- Strings obrigatórias não podem ser vazias nem conter somente espaços.
- Campos opcionais de texto aceitam omissão ou `null`.
- Conteúdo com corpo deve ser `application/json`; formulário gera 415.
- Corpo máximo: 16 KiB nas rotas usuais; POST `/verificacoes` permite 720.000 bytes. Cabeçalhos: 8 KiB.
- Respostas 204 não têm corpo: não tentar chamar `response.json()` nelas.
- Não há envelopamento geral das respostas: listas são arrays JSON e registros
  são objetos JSON. Erros usam `status` e `mensagem`.

## 4. Autenticação e identificação do usuário

### POST /auth/cadastro — público

Corpo permitido, exclusivamente:

```json
{
  "nome": "Pessoa Exemplo",
  "email": "pessoa@example.com",
  "senha": "<senha-forte-do-usuario>"
}
```

Validação:

| Campo | Tipo | Regras |
|---|---|---|
| nome | string | obrigatório, máximo 100 |
| email | string | obrigatório, email válido, máximo 150 |
| senha | string | obrigatória, mínimo 12 e máximo 72 caracteres, máximo 72 bytes UTF-8 |

O servidor normaliza o email para minúsculas, define perfil USER e inicializa
pontos/resgatados em zero. Não enviar `perfil`, `role`, `ADMIN`, `id`, `pontos`,
`resgatados`, `senhaHash` ou `versao`. O cadastro não promove administradores.

Resposta 201:

```json
{
  "id": 101,
  "nome": "Pessoa Exemplo",
  "email": "pessoa@example.com",
  "pontos": 0,
  "resgatados": 0,
  "versao": 0
}
```

O cadastro não retorna token. Guarde o ID retornado e faça login em seguida.
Email duplicado normalizado retorna 409.

### POST /auth/login — público

```json
{
  "email": "pessoa@example.com",
  "senha": "<senha-do-usuario>"
}
```

Email: válido, máximo 150. Senha: não vazia, máximo 72 caracteres/bytes UTF-8.
Resposta 200:

```json
{
  "accessToken": "<jwt-emitido-pela-api>",
  "tokenType": "Bearer",
  "expiresIn": 300
}
```

Use exatamente `accessToken`, `tokenType` e `expiresIn`; não supor `access_token`.
Login inválido ou conta inativa retorna mensagem genérica e 401.

O login não retorna objeto de usuário nem perfil. Não existem `/me` ou `/auth/me`.
Para consultar o perfil, use um ID conhecido e autorizado, por exemplo o retornado
no cadastro. O claim `sub` do JWT contém o ID; se um cliente o ler para montar uma
URL, trate-o somente como informação de interface, nunca como autorização.
Não deduza perfil ADMIN a partir do email e não invente um claim de perfil.
O backend consulta o perfil no banco a cada requisição.

As credenciais Oracle são diferentes de uma conta da API. O login da API exige
email/senha de uma conta cadastrada ou ativada; não usar o usuário do banco no login.

### POST /auth/logout — autenticado

Sem corpo. Retorna 204. Invalida todas as sessões do próprio usuário, inclusive
tokens já emitidos para outros dispositivos. Remova também o token do cliente.
Não existe revogação individual de um único token nem endpoint de refresh.

Tokens expiram em cinco minutos. Em 401, interrompa as chamadas protegidas e
solicite novo login; não mantenha um ciclo de tentativas com o mesmo token.
Alterar nome/email ou desativar a conta também invalida tokens anteriores.
Não registrar senha ou token em console, logs, telemetria ou mensagens da IA.

Limite compartilhado por login/cadastro: 10 tentativas por email e 50 por endereço
remoto a cada janela fixa de 15 minutos. Sucesso não zera os contadores.
429 inclui `Retry-After: 900`. Aguardar o prazo, sem variar IDs/IPs para contornar o limite.


### Primeiro fluxo no PowerShell

O exemplo cria uma conta nova, faz login e consulta catálogo e perfil. Se o email
já existir, pule o cadastro e faça login com a conta existente, usando seu ID
conhecido para consultar o perfil. A senha é solicitada sem eco no terminal.

```powershell
$baseUrl = 'http://localhost:8080'
$email = 'pessoa@example.com'
$senhaSegura = Read-Host 'Senha da conta (12–72 caracteres)' -AsSecureString
$senha = [Net.NetworkCredential]::new('', $senhaSegura).Password
$cadastroJson = @{ nome = 'Pessoa Exemplo'; email = $email; senha = $senha } | ConvertTo-Json
$usuario = Invoke-RestMethod -Method Post -Uri "$baseUrl/auth/cadastro" `
    -ContentType 'application/json; charset=utf-8' -Body $cadastroJson
$loginJson = @{ email = $email; senha = $senha } | ConvertTo-Json
$sessao = Invoke-RestMethod -Method Post -Uri "$baseUrl/auth/login" `
    -ContentType 'application/json; charset=utf-8' -Body $loginJson
$headers = @{ Authorization = "Bearer $($sessao.accessToken)" }
Remove-Variable senha, senhaSegura, cadastroJson, loginJson

Invoke-RestMethod -Uri "$baseUrl/acao?page=0&size=20" -Headers $headers
Invoke-RestMethod -Uri "$baseUrl/usuario/$($usuario.id)" -Headers $headers
```

Catálogo vazio retorna `[]`. ADMIN precisa cadastrar as ações; USER não pode
fazê-lo. Para encerrar todas as sessões da conta:

```powershell
Invoke-RestMethod -Method Post -Uri "$baseUrl/auth/logout" -Headers $headers
Remove-Variable sessao, headers
```

## 5. Endpoints de autenticação e CRUD

Todas as rotas abaixo são relativas à base URL. Não adicionar sufixos `/save`,
`/findAll`, `/Update` ou `/Delete`: os nomes Java não fazem parte das URLs.

| Método | Rota | Permissão | Sucesso |
|---|---|---|---|
| POST | /auth/cadastro | público | 201, UsuarioResponse |
| POST | /auth/login | público | 200, TokenResponse |
| POST | /auth/logout | USER/ADMIN | 204 |
| GET | /usuario | ADMIN | 200, array UsuarioResponse |
| GET | /usuario/{id} | USER próprio; ADMIN qualquer | 200, UsuarioResponse |
| PUT | /usuario/{id} | USER próprio; ADMIN qualquer | 200, UsuarioResponse |
| PUT | /usuario/{id}/saldo | ADMIN | 200, UsuarioResponse |
| DELETE | /usuario/{id}?versao={v} | ADMIN | 204, desativa a conta |
| GET | /acao | USER/ADMIN | 200, array Acao |
| GET | /acao/{id} | USER/ADMIN | 200, Acao |
| POST | /acao | ADMIN | 201, Acao |
| PUT | /acao/{id} | ADMIN | 200, Acao |
| DELETE | /acao/{id}?versao={v} | ADMIN | 204 |
| GET | /carta | USER/ADMIN | 200, array Carta |
| GET | /carta/{id} | USER/ADMIN | 200, Carta |
| POST | /carta | ADMIN | 201, Carta |
| PUT | /carta/{id} | ADMIN | 200, Carta |
| DELETE | /carta/{id}?versao={v} | ADMIN | 204 |
| GET | /selo | USER/ADMIN | 200, array Selo |
| GET | /selo/{id} | USER/ADMIN | 200, Selo |
| POST | /selo | ADMIN | 201, Selo |
| PUT | /selo/{id} | ADMIN | 200, Selo |
| DELETE | /selo/{id}?versao={v} | ADMIN | 204 |
| GET | /recompensa | USER/ADMIN | 200, array Recompensa |
| GET | /recompensa/{id} | USER/ADMIN | 200, Recompensa |
| POST | /recompensa | ADMIN | 201, Recompensa |
| PUT | /recompensa/{id} | ADMIN | 200, Recompensa |
| DELETE | /recompensa/{id}?versao={v} | ADMIN | 204 |
| GET | /historico | USER próprio; ADMIN geral | 200, array Historico |
| GET | /historico/{id} | USER proprietário; ADMIN qualquer | 200, Historico |
| POST | /historico | ADMIN | 201, Historico |
| PUT | /historico/{id} | ADMIN, proprietário imutável | 200, Historico |
| DELETE | /historico/{id}?versao={v} | ADMIN | 204 |
| GET | /usuario-carta | USER próprio; ADMIN geral | 200, array UsuarioCarta |
| GET | /usuario-carta/{usuarioId}/{cartaId} | USER próprio; ADMIN qualquer | 200, UsuarioCarta |
| POST | /usuario-carta | ADMIN | 201, UsuarioCarta |
| PUT | /usuario-carta/{usuarioId}/{cartaId} | ADMIN, chave imutável | 200, UsuarioCarta |
| DELETE | /usuario-carta/{usuarioId}/{cartaId}?versao={v} | ADMIN | 204 |
| GET | /usuario-fragmento | USER próprio; ADMIN geral | 200, array UsuarioFragmento |
| GET | /usuario-fragmento/{usuarioId}/{raridade} | USER próprio; ADMIN qualquer | 200, UsuarioFragmento |
| POST | /usuario-fragmento | ADMIN | 201, UsuarioFragmento |
| PUT | /usuario-fragmento/{usuarioId}/{raridade} | ADMIN, chave imutável | 200, UsuarioFragmento |
| DELETE | /usuario-fragmento/{usuarioId}/{raridade}?versao={v} | ADMIN | 204 |

Outras combinações de rota/método são negadas. Preflight OPTIONS é atendido para
origens CORS autorizadas; não representa acesso público aos dados.

## 6. Paginação e propriedade

Os GET de lista de registros aceitam a paginação abaixo (consumo administrativo
é um objeto único, não uma lista):

```http
GET /historico?page=0&size=20
Authorization: Bearer <accessToken>
```

- `page`: inteiro de 0 a 10000, padrão 0.
- `size`: inteiro de 1 a 100, padrão 20.
- Retorno: array JSON; não há `content`, `totalElements`, `totalPages` ou cursor.
- Lista vazia: `[]`, status 200.
- Para percorrer, incrementar page e parar quando vier uma página com menos de size
  elementos. Não há snapshot estável entre páginas durante escritas concorrentes.
- Não existem busca textual, ordenação configurável, filtro por email ou filtro
  administrativo por usuarioId. Não inventar parâmetros para essas funções.
- Para USER, `/historico`, `/usuario-carta` e `/usuario-fragmento` já filtram o próprio
  usuário no SQL. Acrescentar `?usuarioId=outro` não altera o escopo.
- Registro de outro titular retorna 404. Não usar diferenças de resposta para
  enumerar usuários ou tentar descobrir registros alheios.

## 7. Corpos de cadastro e atualização das entidades

Para entidades com chave simples, omita `id` no POST: o Oracle gera o valor e a
resposta o inclui. `versao` inicia em 0; pode ser omitida no POST. No PUT, envie
todos os campos obrigatórios e a versão lida; não há PATCH nem atualização parcial.
O ID da URL prevalece sobre `id` no corpo.

Campos opcionais omitidos no PUT podem ser gravados como null; não assumir que
omissão preserva o valor anterior. Para preservar, reenvie o valor lido.
Não enviar `versao: null`. Nas associações, reenvie as chaves no corpo, idênticas à URL.

### Acao

```json
{"nome":"Reciclar plástico","pontos":10,"versao":0}
```

| Campo | Regras |
|---|---|
| nome | string obrigatória, máximo 100 |
| pontos | inteiro obrigatório, de 1 a 100 |
| id | inteiro positivo na resposta; omitir no POST |
| versao | inteiro >= 0; enviar a versão atual no PUT |

### Carta

```json
{"nome":"Guardião da floresta","descricao":"Carta de exemplo","conjunto":"Floresta","raridade":"COMUM","versao":0}
```

| Campo | Regras |
|---|---|
| nome | string obrigatória, máximo 100 |
| descricao | string opcional, máximo 200 |
| conjunto | string obrigatória, máximo 60 |
| raridade | string obrigatória: COMUM, INCOMUM, RARA, EPICA ou LENDARIA |
| id / versao | mesmas convenções de Acao |

Raridades exigem exatamente essas maiúsculas, sem acentos.

### Selo

```json
{"nome":"Primeiros passos","descricao":"Selo de exemplo","pontosMin":100,"versao":0}
```

| Campo | Regras |
|---|---|
| nome | string obrigatória, máximo 100 |
| descricao | string opcional, máximo 200 |
| pontosMin | inteiro obrigatório >= 0 |
| id / versao | mesmas convenções de Acao |

### Recompensa

```json
{"titulo":"Ecobag","descricao":"Sacola reutilizável","custo":50,"estoque":10,"categoria":"Acessórios","destaque":"NOVO","versao":0}
```

| Campo | Regras |
|---|---|
| titulo | string obrigatória, máximo 100 |
| descricao | string opcional, máximo 200 |
| custo | inteiro obrigatório > 0 |
| estoque | inteiro obrigatório >= 0 |
| categoria | string opcional, máximo 30 |
| destaque | string opcional, máximo 20; não é boolean |
| id / versao | mesmas convenções de Acao |

### Historico

```json
{"usuarioId":101,"descricao":"Registro administrativo","pontos":10,"dataAcao":"2025-01-01T10:00:00","versao":0}
```

| Campo | Regras |
|---|---|
| usuarioId | inteiro positivo obrigatório, usuário existente |
| descricao | string obrigatória, máximo 150 |
| pontos | inteiro obrigatório de 1 a 100 |
| dataAcao | string ISO LocalDateTime obrigatória, passado ou presente |
| id / versao | mesmas convenções de Acao |

`dataAcao` não possui offset/fuso: usar `YYYY-MM-DDTHH:mm:ss`, sem Z ou +03:00.
Datas futuras são rejeitadas conforme o relógio/fuso do servidor. Não enviar valores
de outro usuário para mudar o proprietário no PUT; essa operação retorna 409.
Criar histórico não credita pontos nem comprova uma ação real.

### UsuarioCarta

```json
{"usuarioId":101,"cartaId":401,"quantidade":1,"versao":0}
```

| Campo | Regras |
|---|---|
| usuarioId | inteiro positivo obrigatório, usuário existente |
| cartaId | inteiro positivo obrigatório, carta existente |
| quantidade | inteiro obrigatório > 0 |
| versao | inteiro >= 0, obrigatório enviar a versão atual no PUT |

Não há campo `id`: a chave é `(usuarioId, cartaId)`. No PUT, corpo e URL precisam
ter as mesmas duas chaves. Cadastro duplicado retorna 409; não há MERGE nem soma
automática. O PUT define a quantidade absoluta, não incrementa a quantidade atual.

### UsuarioFragmento

```json
{"usuarioId":101,"raridade":"COMUM","quantidade":3,"versao":0}
```

| Campo | Regras |
|---|---|
| usuarioId | inteiro positivo obrigatório, usuário existente |
| raridade | COMUM, INCOMUM, RARA, EPICA ou LENDARIA |
| quantidade | inteiro obrigatório >= 0 |
| versao | inteiro >= 0, obrigatório enviar a versão atual no PUT |

Não há campo `id`: a chave é `(usuarioId, raridade)`. Corpo e URL devem coincidir.
Quantidade é absoluta. Não há endpoint de gastar fragmentos ou fabricar cartas.

### Perfil de usuário — PUT /usuario/{id}

```json
{"nome":"Nome atualizado","email":"pessoa@example.com","versao":0}
```

Somente esses três campos são aceitos. Nome: obrigatório, máximo 100; email:
obrigatório, válido, máximo 150; versao: obrigatória e >= 0.
Nunca enviar pontos, resgatados, perfil, senha, id ou hash nesse DTO.
A resposta é UsuarioResponse e a alteração revoga os tokens anteriores.

### Saldo administrativo — PUT /usuario/{id}/saldo

```json
{"pontos":50,"resgatados":10,"versao":0}
```

Todos os campos são obrigatórios; inteiros >= 0. Somente ADMIN. Define valores
absolutos, não soma pontos nem executa resgate. USER não pode chamar essa rota.
Não usar esse ajuste como substituto de uma operação de premiação inexistente.

## 8. Controle de versão, repetição e exclusão

Sequência correta de atualização:

1. GET do registro.
2. Guardar a `versao` retornada.
3. PUT com o corpo completo permitido e essa versão.
4. Guardar a nova versão da resposta; ela aumenta em 1.

Exemplo de ADMIN:

```http
GET /acao/701
Authorization: Bearer <token-admin>
```

Resposta 200:

```json
{"id":701,"nome":"Reciclar plástico","pontos":10,"versao":0}
```

Atualização:

```http
PUT /acao/701
Authorization: Bearer <token-admin>
Content-Type: application/json

{"nome":"Reciclar papel","pontos":15,"versao":0}
```

Resposta 200:

```json
{"id":701,"nome":"Reciclar papel","pontos":15,"versao":1}
```

Exclusão:

```http
DELETE /acao/701?versao=1
Authorization: Bearer <token-admin>
```

Retorno 204 sem corpo. Versão antiga retorna 409; registro inexistente retorna 404.
Dependências de chave estrangeira podem impedir exclusões com 409. Não inventar
exclusão em cascata para contornar o conflito.

DELETE /usuario/{id} desativa a conta, revoga sessões e preserva histórico/coleção.
Não apaga o usuário fisicamente e não há endpoint de reativação.

Nos CRUDs, não repetir POST automaticamente após timeout: o cliente pode não saber se o
cadastro foi concluído. Os CRUDs não possuem chave de idempotência de criação; a submissão de
verificação possui idempotência própria, descrita na seção de IA. No PUT, uma
repetição com a mesma versão conflita após o primeiro sucesso. Não obter uma nova
versão e reaplicar alterações sem revisar o estado/confirmar a intenção.

## 9. Erros e reação do cliente/IA

Formato esperado, sem detalhes internos:

```json
{"status":403,"mensagem":"Acesso negado."}
```

A ordem das propriedades pode variar. Tratar pela combinação HTTP/status, não
por correspondência exata da mensagem. Não há mapa público de erros por campo.

| HTTP | Significado | Ação recomendada |
|---|---|---|
| 400 | JSON, campo ou parâmetro inválido | Corrigir o contrato; não repetir o mesmo corpo |
| 401 | Token ausente/inválido/expirado/revogado ou login inválido | Solicitar autenticação novamente |
| 403 | Perfil sem permissão, CORS negado ou HTTPS exigido fora da configuração local | Verificar permissão/origem/ambiente; não contornar |
| 404 | Ausente ou pertencente a outro usuário | Tratar como indisponível; não enumerar IDs |
| 409 | Email/chave duplicada, referência inválida ou versão divergente | Reconsultar e revisar a operação |
| 413 | Corpo acima do limite da rota (16 KiB usual; 720.000 bytes na submissão de verificação) | Reduzir o corpo |
| 415 | Corpo fora de application/json | Corrigir Content-Type e serialização |
| 429 | Limite de tentativas | Respeitar Retry-After; interromper tentativas |
| 500 | Falha interna genérica | Informar indisponibilidade; não expor detalhes |
| 503 | Persistência indisponível ou IA desabilitada/suspensa/circuito aberto | Aguardar/restabelecer serviço; não repetir escritas cegamente |

Indisponibilidade do banco durante a verificação de identidade pode aparecer
como 401, pois a autenticação falha de forma fechada. Não tentar compensar isso
com credenciais diferentes ou flexibilização da segurança.

No navegador, CORS pode impedir a leitura do corpo de uma rejeição de origem.
Confira a origem exata do frontend; não desative CORS e não use wildcard.
O backend expõe Retry-After por CORS. Login/cadastro usam 900 segundos; limites
de IA usam 60 segundos como sugestão de nova consulta, sem garantir liberação
de uma cota diária nesse prazo. Idempotency-Key é permitido nos headers CORS.

## 10. Exemplo de cliente JavaScript

Exemplo para um frontend local autorizado. O token fica em memória; a aplicação
deve implementar sua própria interface para solicitar credenciais e novo login.

```javascript
const API_BASE_URL = 'http://localhost:8080';
let accessToken = null;

async function api(path, { method = 'GET', body, authenticated = true, idempotencyKey } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (authenticated) {
    if (!accessToken) throw new Error('Login necessário');
    headers.Authorization = `Bearer ${accessToken}`;
  }
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
  const response = await fetch(API_BASE_URL + path, {
    method,
    headers,
    credentials: 'omit',
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = response.status === 204 ? '' : await response.text();
  const isJson = response.headers.get('content-type')?.includes('application/json');
  let payload = null;
  if (text && isJson) {
    try { payload = JSON.parse(text); } catch { /* erro de resposta do servidor */ }
  }
  if (!response.ok) {
    if (response.status === 401) accessToken = null;
    const error = new Error(payload?.mensagem ?? `Falha HTTP ${response.status}`);
    error.status = response.status;
    error.retryAfter = response.headers.get('Retry-After') ??
      (response.status === 429 ? (path.startsWith('/auth/') ? '900' : '60') : null);
    throw error;
  }
  return payload;
}

async function login(email, senha) {
  const result = await api('/auth/login', {
    method: 'POST', authenticated: false, body: { email, senha },
  });
  accessToken = result.accessToken;
  return { expiresIn: result.expiresIn }; // não devolver token para logs/UI
}

async function logout() {
  try { await api('/auth/logout', { method: 'POST' }); }
  finally { accessToken = null; }
}

async function listarAcoes() {
  return api('/acao?page=0&size=20');
}

async function atualizarPerfil(id, nome, email, versao) {
  const result = await api(`/usuario/${id}`, {
    method: 'PUT', body: { nome, email, versao },
  });
  accessToken = null; // a atualização revogou as sessões: solicitar novo login
  return result;
}
```

Não usar retry automático para login, cadastro, updates ou deletes neste cliente.
Adicionar tratamento de rede e estados de carregamento na interface; falhas de
fetch não são necessariamente falhas de regra de negócio.

## 11. Administração e contas legadas

Não existe endpoint público de criação de ADMIN, promoção de perfil, alteração
de senha, confirmação de email ou recuperação de senha.

O primeiro ADMIN é provisionado por operador, offline, com:

```text
SPRING_PROFILES_ACTIVE=provision
SPRING_MAIN_WEB_APPLICATION_TYPE=none
PROVISION_MODE=bootstrap
PROVISION_NAME=<nome-verificado>
PROVISION_EMAIL=<email-verificado>
PROVISION_PASSWORD=<senha-unica-segura>
```

Executar o JAR a partir da raiz do projeto, sem servidor web. O processo recusa
criar outro ADMIN quando já existe um. Esse procedimento exige autorização do
operador, credenciais adequadas e variáveis privadas; não executá-lo autonomamente
apenas para obter acesso a uma rota administrativa.

Contas legadas ficam inativas e sem hash após a migração. Ativação usa o mesmo
processo offline, com `PROVISION_MODE=activate`, após verificação de identidade.
Só ativa USER inativo e com hash ausente; não redefine senha existente.
Remover o perfil/variáveis de provisionamento antes de iniciar a API normal.


## 12. Verificação de ações sustentáveis com Gemini


O único provedor de IA é o Google Gemini, modelo `gemini-3.5-flash-lite`.
O backend envia uma foto JPEG normalizada para classificar
a compatibilidade visual. O resultado não comprova autenticidade, autoria,
localização ou data e não contém pontuação de deepfake ou imagem gerada.

## Configuração e execução local

Use `config/local-secrets.properties`, ignorado pelo Git, na raiz do backend:

```properties
GEMINI_API_KEY=<chave_privada_do_google_ai_studio>
AI_ENABLED=true
AI_GEMINI_CEILING_MICROS=110000
AI_INPUT_MICROS_PER_MILLION=0
AI_OUTPUT_MICROS_PER_MILLION=0
```

As duas taxas zero representam uma estimativa para uso da cota gratuita, não uma
garantia de ausência de cobrança. O plano e a cobrança são definidos pelo projeto
Google. Use um projeto sem faturamento ativo para a demonstração gratuita e
confira as cotas no AI Studio. Não há ativação de billing, fallback pago ou
troca automática de modelo no código. Uma chave inválida, falta de acesso,
cota esgotada ou indisponibilidade deixa a análise para revisão.

O default público do aplicativo é `AI_ENABLED=false`. O arquivo privado local
preserva a habilitação já solicitada pelo operador. Variáveis de ambiente e
argumentos do IntelliJ podem sobrescrever o arquivo: remova configurações antigas
de provedor/modelo e use Working directory apontando para a raiz `rockySoulup`.
No Windows local, `AI_WINDOWS_TRUST_STORE=true` permite usar os certificados
confiáveis instalados no sistema, preservando validação de cadeia e hostname.
Em outros ambientes mantenha false (default) e use o truststore Java apropriado.
Nunca desative a validação TLS.
Reinicie pelo IntelliJ depois de compilar. Esta alteração não inicia servidor
em segundo plano e não altera as configurações de banco, JWT ou CORS.

Não coloque a chave no frontend, em parâmetros de URL, exemplos, testes ou Git.
As credenciais dos provedores removidos não são mais necessárias e foram
retiradas da configuração local. Revogue chaves antigas nos respectivos painéis
se não forem usadas em outros projetos. Uma chave compartilhada em chat deve
ser substituída; atualize somente `GEMINI_API_KEY` no arquivo privado.

## Privacidade das fotos

A cota gratuita dos serviços Google pode usar entradas e respostas para melhorar
produtos e envolver revisão humana. Os termos orientam não enviar informações
pessoais, sensíveis ou confidenciais nesses serviços. Para demonstração, use
fotos sem pessoas identificáveis, documentos ou dados privados. O fato de a
evidência ser privada dentro da API não impede o processamento externo pelo Google.

O backend reduz a imagem, recodifica em JPEG, remove metadados do arquivo e não
inclui nome de usuário, email, saldo, JWT ou chave no prompt. Não há upload para
armazenamento público, Files API, ferramentas, busca, URLs arbitrárias ou cache
persistente de conversas. Os dados são enviados em `inlineData` por HTTPS.

## Fluxo e endpoints preservados

Todos os endpoints exigem JWT. USER acessa somente as próprias verificações;
ADMIN pode revisar outras contas. UUID de terceiros retorna 404 para USER.

| Método e rota | Uso |
| --- | --- |
| POST `/verificacoes/desafio` | Emitir desafio temporário para usuário e acaoId |
| POST `/verificacoes` | Foto base64, descrição, acaoId, desafioId e Idempotency-Key; 202 |
| GET `/verificacoes?page=0&size=20` | Lista paginada com escopo da sessão |
| GET `/verificacoes/{uuid}` | Estado e metadados |
| GET `/verificacoes/{uuid}/evidencia` | JPEG privado, no-store |
| DELETE `/verificacoes/{uuid}/evidencia` | Exclusão da evidência e descrição |
| POST `/admin/verificacoes/{uuid}/revisao` | Aprovação/rejeição por ADMIN com justificativa e versão |
| POST `/admin/verificacoes/{uuid}/reprocessar` | No máximo um reprocessamento manual, sujeito a cotas/custos |
| POST `/admin/ia/suspensao` | Suspender/retomar novas análises |
| GET `/admin/ia/consumo` | Limites, reservas e estimativas locais |

```json
{"acaoId":21,"desafioId":"<uuid_do_desafio>","descricao":"Preparei uma composteira.","imagemBase64":"<BASE64_JPEG_OU_PNG>"}
```

Imagem: JPEG/PNG até 512 KiB, de 32 a 2048 pixels por lado, normalizada no servidor.
Descrição: 1–1000 caracteres. Idempotency-Key: 16–64 caracteres alfanuméricos,
`_` ou `-`, uma por operação. Nunca envie usuário, pontos, aprovação ou modelo.
Repita a mesma operação com a mesma chave após resultado incerto; não crie outra
submissão. Uma repetição não dispara outra chamada de IA automaticamente.

```json
{
  "id":"<uuid>","usuarioId":101,"acaoId":21,
  "estado":"PENDENTE_REVISAO","versao":2,
  "possivelReuso":false,"evidenciaDisponivel":true,
  "parecer":"COMPATIVEL","modelo":"gemini-3.5-flash-lite",
  "regras":"sustentavel-capture-v3","tentativas":1,"pontosCreditados":0
}
```

Pareceres novos: COMPATIVEL, INCOMPATIVEL e INCONCLUSIVO. Erros locais ou de
provedor recebem códigos seguros, como HTTP_FAILURE, INVALID_RESPONSE ou
INVALID_USAGE. Um HTTP 202 indica registro para acompanhamento, nunca aprovação.

Estados: ENVIADA → EM_ANALISE → PENDENTE_REVISAO → APROVADA/REJEITADA.
Casos com desafio confirmado podem ser aprovados automaticamente; ADMIN revisa os pendentes com justificativa. O servidor captura os pontos
do catálogo ACAO, mantém histórico e ledger único, e impede crédito duplicado.
O Gemini não decide saldo. Reuso de hash SHA-256 é apenas um sinal, não prova fraude.

## Limites e falhas

Mantidos JWT, controle de versão, isolamento por usuário, suspensão, circuito,
admissão transacional, orçamento, idempotência, retenção e limites de imagens.
Cada tentativa aceita faz no máximo uma chamada ao Gemini; sem retries internos.

| Variável | Padrão |
| --- | ---: |
| AI_USER_MINUTE | 2 |
| AI_IP_MINUTE | 5 |
| AI_GLOBAL_MINUTE | 10 |
| AI_USER_DAILY | 10 |
| AI_GLOBAL_DAILY | 100 |
| AI_CONCURRENCY | 2 |
| AI_PENDING_LIMIT | 100 |
| AI_DAILY_MICROS | 2000000 |
| AI_MONTHLY_MICROS | 20000000 |
| AI_USER_DAILY_MICROS | 500000 |
| AI_GEMINI_CEILING_MICROS | 110000 |
| AI_INPUT_MICROS_PER_MILLION | 0 |
| AI_OUTPUT_MICROS_PER_MILLION | 0 |
| AI_RETENTION_DAYS | 7 |

Valores monetários: 1000000 micros = US$ 1. A reserva conservadora de 110000 micros
é mantida para tentativas incertas, mesmo no modo de estimativa gratuita, para
preservar os bloqueios existentes. Não é uma cobrança medida nem uma tarifa Google.
Com resposta válida, o custo estimado usa tokens e taxas configuradas. Tokens de
pensamento também contam na saída. Limites por usuário/minuto/dia continuam
ativos mesmo com taxas zero. As cotas reais do Google são independentes e podem
ser menores que as locais.

Para projeto com faturamento, configure taxas vigentes e teto por tentativa; zero
não deve ser interpretado como custo real zero. O painel local não substitui o
faturamento Google. O construtor rejeita teto abaixo do máximo calculado de 8192
tokens de entrada e 512 de saída. Falhas preservam reservas incertas; não há
reembolso automático nem liberação por timeout. O circuito abre após falhas
repetidas e exige nova tentativa manual após a janela definida no código.

## Transporte e validação

Destino fixo: `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent`.
Autenticação somente no cabeçalho `x-goog-api-key`. Redirecionamentos proibidos,
timeout de 20 segundos, corpo de saída limitado e resposta limitada a 64 KiB.
Uma única resposta candidata, JSON com campo parecer e enum fechado, término
STOP, modelo permitido e contadores numéricos coerentes são exigidos. Texto da imagem é dado não confiável; a descrição do usuário não é encaminhada ao modelo.
Não são aceitas instruções do modelo, SQL, pontos ou ferramentas executáveis.

## Banco e operação

A captura exige V004; as tabelas V001–V003 continuam preservadas.
Histórico, pareceres antigos e reservas anteriores permanecem para auditoria;
nenhuma evidência, conta, ação ou saldo é apagado. Não reenvie uma operação antiga
com outra imagem/modelo usando a mesma chave. A regra nova é versionada como
`sustentavel-gemini-v2`. Novas operações registram a versão de modelo retornada.

Retenção da evidência/descrição: até sete dias e limpeza pela manutenção enquanto
a API e o banco estiverem disponíveis. Metadados, reservas, crédito e auditoria
seguem a política de retenção existente. Exclusão na API não remove cópias já
processadas pelo Google ou backups externos.

## Validação

Os testes usam H2, chaves fictícias e gateway/transporte simulados, sem Oracle
real nem geração de imagens/texto. Cobrem respostas bloqueadas/truncadas, JSON
inválido, prompt injection, modelos e tokens fechados, pensamento contabilizado,
custo zero estimado, cotas, concorrência, reservas, revisão e crédito único.
Uma suíte mockada não valida a chave real, acesso da conta, precisão do modelo
ou disponibilidade de cota. A execução real deve ocorrer pelo IntelliJ, após
conferir a chave e o projeto no AI Studio.

## Referências

- [Modelo](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite)
- [REST generateContent](https://ai.google.dev/api/generate-content)
- [Preços e cota gratuita](https://ai.google.dev/gemini-api/docs/pricing)
- [Limites](https://ai.google.dev/gemini-api/docs/rate-limits)
- [Tratamento de dados](https://ai.google.dev/gemini-api/terms)


## 13. Postman e solução de problemas

No Postman/Insomnia, crie `baseUrl=http://localhost:8080`. Envie cadastro/login
como Body → raw → JSON. Guarde accessToken em variável local privada e escolha
Authorization → Bearer Token nas rotas protegidas. Para IA, adicione
Idempotency-Key e imagemBase64 no JSON; não use form-data/multipart.
Não exporte ambientes com tokens ou senhas.

| Sintoma | Verificação |
|---|---|
| Conexão recusada | API iniciada, porta correta e interface de escuta |
| Placeholder DB/JWT não resolvido | Arquivo privado, variáveis e Working directory |
| Tabela Oracle inexistente | Schema original, migrações e privilégios da conta |
| 401 depois de alguns minutos | Token de cinco minutos expirado; faça login |
| 401 depois de alterar perfil | A atualização revogou os tokens antigos |
| 403 ao criar catálogo | A conta precisa ser ADMIN |
| 409 ao alterar | Consulte a versão atual e revise a operação |
| 503 na IA | AI_ENABLED, chaves/permissões, suspensão, circuito e banco |
| 429 na IA | Orçamento, cotas, vagas simultâneas ou pendências atingidos |
| Erro CORS | Origem exata com protocolo/host/porta, sem wildcard |
| Erro ao ler 204 como JSON | A resposta não possui corpo |

Para frontend local, use servidor HTTP, não file://. localhost e 127.0.0.1 são
origens diferentes. Respeite os limites de entrada também no cliente, mantendo
as verificações obrigatórias do backend.

## 14. Estrutura, testes e publicação

```text
src/main/java/br/com/rockysoulup/
  resource/   Controllers e contratos HTTP
  bo/         Autorização, regras e coordenação transacional
  dao/        Persistência das entidades com JDBC
  to/         DTOs e Bean Validation
  security/   JWT, autorização, HTTP e provisionamento
  ai/         Clientes Gemini, imagens e componentes auxiliares
src/main/resources/application.yaml
src/test/     Testes e schema H2 descartável
db/migration/ Migrações Oracle para revisão e execução offline
db/manual/    Reconciliação operacional de reservas incertas
config-examples/ Configuração de referência sem segredos
docs/         Contratos e relatórios técnicos
```

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

A validação registrada aprovou 97 testes sem falhas, erros ou skips, em cópia sem
configuração privada. Use os testes com H2/mocks para validar o código sem custos.
Homologação Oracle, implantação e precisão real dos provedores são verificações
separadas. Nenhuma migração Oracle ou chamada paga foi feita na implementação.

Ao publicar, mantenha fora dos commits config/local-secrets.properties, .env,
arquivos de IDE, evidências privadas e target. Confira git status e git diff
--cached antes do commit. O gitignore não remove segredos de commits anteriores:
credenciais já expostas precisam ser revogadas e o histórico correspondente revisto.

Para hospedagem, configure HTTPS, CORS exato, segredos por ambiente, conta Oracle
de privilégio mínimo e políticas de retenção/backup. Não exponha o HTTP local
diretamente à internet. SQL parametrizado, BCrypt, autorização, reservas e
idempotência são controles complementares; nenhum deles garante proteção absoluta.

## 15. Documentação complementar

| Documento | Conteúdo |
|---|---|
| [Contrato para integração por IA](docs/GUIA_API_PARA_IA.md) | Contratos detalhados para clientes e agentes |
| [Segurança e operação da IA](docs/SEGURANCA-IA.md) | Orçamento, retenção e riscos residuais |
| [Validação da integração](docs/VALIDACAO-IA.md) | Resultado da suíte de testes |
| [Diagnóstico inicial](docs/DIAGNOSTICO.md) | Análise dos controles da API |
| [Diagnóstico da IA](docs/DIAGNOSTICO-IA.md) | Riscos e decisões da integração |
| [Permissões de CRUD](docs/PERMISSOES.md) | Rotas originais; rotas de IA estão neste guia |
| [Mudanças para clientes](docs/MUDANCAS-CLIENTES.md) | Alterações em autenticação e CRUD |
| [Configuração de IA](config-examples/ai-local.properties.example) | Variáveis com placeholders |


## Captura com desafio e aprovação automática

O fluxo novo exige câmera no aplicativo e um desafio de uso único emitido pelo
backend. É uma política de risco limitado, não prova de autenticidade, autoria ou
de que a ação realmente aconteceu. Câmeras virtuais, cenas preparadas e imagens
sintéticas ainda podem enganar uma verificação visual. Não há atestação de hardware.

1. Autentique e solicite `POST /verificacoes/desafio` com `{"acaoId":21}`.
2. A resposta contém `id`, `acaoId`, `codigo` e `expiraEm`. O código de seis
   caracteres vale cinco minutos e fica vinculado ao usuário e à ação.
3. Escreva o código em papel físico e enquadre-o junto da ação, com letras grandes.
   O aplicativo captura somente pixels da câmera; não imprime o código na foto.
4. Envie POST `/verificacoes` com `acaoId`, `desafioId` (UUID do desafio),
   `descricao`, `imagemBase64` e o cabeçalho Idempotency-Key.
5. O Gemini recebe o nome da ação e a foto; a descrição fica no banco e não vai
   ao modelo. O código esperado nunca é enviado ao modelo. A saída fechada tem
   `parecer` e `codigoVisivel` (seis caracteres ou `NONE`). O backend compara o
   código lido com o emitido e valida prazo, usuário, ação e uso único.

```json
{"acaoId":21,"desafioId":"<uuid_do_desafio>","descricao":"Preparei uma composteira.","imagemBase64":"<BASE64_DA_CAPTURA>"}
```

O backend aceita a submissão somente com um desafio válido; não confia em flags
do cliente alegando captura real. A câmera do navegador é uma restrição do fluxo
da interface, que clientes modificados podem contornar. O desafio na própria
imagem e a comparação no servidor são controles independentes dessa interface.

### Decisão

- COMPATIVEL + código correto + captura inédita + primeira tentativa + conta ativa
  + limites respeitados: APROVADA e crédito uma única vez.
- INCOMPATIVEL com desafio confirmado e sem suspeita de reuso: REJEITADA, sem crédito.
- Código ausente/incorreto, foto repetida/parecida, INCONCLUSIVO, falha de provedor,
  limite de aprovação ou resultado inesperado: PENDENTE_REVISAO, sem crédito automático.
- Reprocessamentos e verificações antigas sem desafio continuam em revisão manual.

As decisões automáticas usam ator 0 (sistema) na auditoria. Aprovação e rejeição
manuais por ADMIN permanecem para os casos pendentes, com justificativa. Ambas
as formas de aprovação compartilham a mesma transação e ledger de chave única.
Nenhum campo enviado pelo usuário ou pelo Gemini define a quantidade de pontos.

### Limites adicionais

- Crédito automático: até 100 pontos e três ações por usuário/dia em UTC.
- Uma mesma ação recebe crédito automático no máximo uma vez por usuário/dia.
- Desafios: até cinco novos por usuário/minuto, dez por IP/minuto, vinte globais
  por minuto; trinta por usuário/dia e trezentos globais por dia.
- Pedir novamente um desafio ainda ativo para a mesma ação devolve o existente.
- Os limites anteriores de análise, concorrência e orçamento também continuam.
- `AI_AUTO_APPROVAL_ENABLED=false` desliga decisões automáticas e mantém revisão.

Fotos são comparadas por SHA-256 da imagem normalizada e por dHash visual de 64
bits. Distância de até quatro bits em capturas dos últimos trinta dias gera
suspeita. O hash exato considera também verificações antigas. Similaridade pode
gerar falsos positivos e não detecta todas as alterações; ela encaminha para
revisão e não acusa fraude. Desafios não usados são limpos sete dias após expirar.
Metadados dos desafios usados são preservados para auditoria e prevenção de replay.

O servidor verifica seus próprios horários, vínculos e estado do desafio. GPS e
EXIF não são usados como prova. A normalização continua removendo metadados do
arquivo. Não prometemos identificar toda imagem gerada por IA ou deepfake.

### Migração e interface

Aplicar `db/migration/V004__capture_challenges.sql` uma única vez após V003.
O script cria apenas CAPTURA_DESAFIO e seus índices; não edita contas, ações,
saldos ou verificações existentes. Oracle confirma DDL implicitamente.

O frontend pede câmera somente ao pressionar o botão de início. Permissão
negada mostra orientação, sem substituir por upload de galeria. Requer HTTPS
ou localhost. A câmera é desligada após captura, ao fechar e ao expirar o desafio.
O saldo é reconsultado após a decisão. Falhas de rede repetem a mesma chave,
foto e desafio, sem criar outra tentativa. A API não foi iniciada em segundo plano.

Os testes de captura usam câmera sintética e API simulada. Os testes de decisão
usam H2 e gateway simulado; não comprovam precisão real do Gemini. Nenhuma imagem
real foi enviada ao provedor nesta implementação. Fotos de demonstração devem
evitar pessoas identificáveis e dados pessoais, conforme as condições da cota gratuita.


## Avaliação automática por nota de compatibilidade (v4)

O fluxo atual usa nota de compatibilidade visual de 0 a 100, sem apresentá-la como
probabilidade de autenticidade. Abaixo de 50 e claramente incompatível: rejeição
automática. Entre 50 e 80, inclusive: revisão. Acima de 80 e com todas as verificações
obrigatórias atendidas: aprovação e crédito único. Falhas, suspeitas, imagens
reutilizadas e desafios não confirmados permanecem para revisão, sem pontos.
As notas e justificativas aparecem nos detalhes das verificações.

Esta seção substitui a configuração inicial de hospedagem com aprovação desligada.
Configurar `AI_AUTO_APPROVAL_ENABLED=true` e aplicar V007 antes de publicar.
Veja [regras e implantação da avaliação automática](docs/AVALIACAO-AUTOMATICA.md).


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
