# RockySoulup API

API REST para registro de ações sustentáveis, administração de usuários e pontos,
histórico, cartas, fragmentos, selos e recompensas. A integração OpenAI/Hive analisa
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

OPENAI_API_KEY e HIVE_API_KEY são opcionais enquanto AI_ENABLED=false. Quando
ativar a integração, acrescente-as somente à configuração privada do backend.
Não são necessárias para cadastro/login e CRUD.

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


## 12. Verificação de ações sustentáveis com IA

Depois de aplicar V003 e conferir os limites financeiros, configure as chaves
privadas OPENAI_API_KEY e HIVE_API_KEY, confirme acesso ao modelo `gpt-6-luna`
na OpenAI e à API Hive V3 `hive/ai-generated-and-deepfake-content-detection`.
Chave Hive V2 não substitui uma chave V3.

Defina `AI_ENABLED=true` no ambiente da execução e reinicie a API. Essa ativação
permite chamadas cobradas. A presença das chaves sozinha não ativa a integração;
com IA desabilitada, análise nova retorna 503. A API usa modelo e parâmetros
definidos no servidor, sem escolha livre pelo cliente.

### Rotas e contratos

Todos exigem `Authorization: Bearer <JWT>`. USER consulta apenas suas próprias
verificações e evidências; ADMIN pode consultar o conjunto para revisão.
Tentativa de acessar registro alheio retorna 404.

| Método e rota | Perfil | Contrato |
|---|---|---|
| POST `/verificacoes` | USER/ADMIN | Cabeçalho `Idempotency-Key`; JSON abaixo; 202 |
| GET `/verificacoes?page=0&size=20` | USER/ADMIN | Lista com escopo e paginação (size 1–100) |
| GET `/verificacoes/{uuid}` | USER/ADMIN | Metadados sem descrição ou bytes privados |
| GET `/verificacoes/{uuid}/evidencia` | USER/ADMIN | JPEG privado; `Cache-Control: no-store` |
| DELETE `/verificacoes/{uuid}/evidencia` | USER/ADMIN | Exclui imagem/descrição; 204; em análise retorna 409 |
| POST `/admin/verificacoes/{uuid}/revisao` | ADMIN | `{"aprovar":true,"justificativa":"Conferência independente","versao":2}` |
| POST `/admin/verificacoes/{uuid}/reprocessar` | ADMIN | `{"confirmarPossivelCobranca":true,"versao":2}` |
| POST `/admin/ia/suspensao` | ADMIN | `{"suspenso":true}`; 204; false retoma admissões |
| GET `/admin/ia/consumo` | ADMIN | Consumo estimado/reservas, limites e suspensão, em micros USD |

Submissão:

```json
{
  "acaoId": 701,
  "descricao": "Separei os resíduos para reciclagem.",
  "imagemBase64": "<BASE64_PURO_DE_UMA_IMAGEM_JPEG_OU_PNG>"
}
```

Descrição: 1–1000 caracteres; `acaoId` positivo e existente no catálogo.
Idempotency-Key: 16–64 caracteres alfanuméricos, `_` ou `-`; gere uma chave
aleatória por operação e reutilize-a após falha de rede. A chave é vinculada ao
conteúdo e ao usuário. Mesmo conteúdo (imagem normalizada, ação, descrição,
modelo, regras) com outra chave retorna a operação existente do mesmo usuário.
Mesma chave com conteúdo diferente retorna 409. Não existe cache entre usuários.
Não enviar `usuarioId`, pontos, aprovação, modelo, URLs ou parâmetros da IA:
campos desconhecidos são rejeitados. O backend usa o usuário autenticado.

202 significa que a operação foi registrada, **não que foi aprovada**. A
requisição espera até duas chamadas limitadas de provedor; pode retornar
EM_ANALISE numa repetição concorrente ou PENDENTE_REVISAO após finalização.
Consulte GET com o mesmo UUID; não crie novas operações para contornar falhas.

Resposta de exemplo:

```json
{
  "id":"<uuid>", "usuarioId":101, "acaoId":701,
  "estado":"PENDENTE_REVISAO", "versao":2,
  "possivelReuso":false, "evidenciaDisponivel":true,
  "parecer":"COMPATIVEL", "modelo":"gpt-6-luna",
  "regras":"sustentavel-review-v1", "tentativas":1, "pontosCreditados":0
}
```

O modelo retornado pela OpenAI, incluindo sufixo de snapshot quando presente,
é registrado. Hive usa o identificador fixo V3 acima; o contrato público de
classificação não fornece um snapshot imutável garantido. Mudanças do modelo
Hive exigem revisão de contrato e avaliação; não foi inventado um número de
versão. As regras de integração são registradas em cada verificação.

### Limites e configuração de custos

Valores em micros USD: 1.000.000 = US$ 1. Períodos financeiros e diários em UTC.
Configuração via ambiente/properties do servidor; não há API pública para
editar limites ou permitir outro modelo.

| Variável | Padrão | Efeito |
|---|---:|---|
| AI_USER_MINUTE | 2 | Análises novas por usuário/minuto fixo |
| AI_IP_MINUTE | 5 | Análises por IP/minuto fixo |
| AI_GLOBAL_MINUTE | 10 | Análises totais/minuto fixo |
| AI_USER_DAILY | 10 | Tentativas por usuário/dia, incluindo reprocessamentos |
| AI_GLOBAL_DAILY | 100 | Tentativas totais/dia |
| AI_CONCURRENCY | 2 | Tentativas simultâneas globais; por usuário sempre 1 |
| AI_PENDING_LIMIT | 100 | Verificações pendentes globais; por usuário sempre 5 |
| AI_DAILY_MICROS | 2000000 | US$ 2/dia global |
| AI_MONTHLY_MICROS | 20000000 | US$ 20/mês global |
| AI_USER_DAILY_MICROS | 500000 | US$ 0,50/dia por usuário |
| AI_OPENAI_CEILING_MICROS | 100000 | Reserva OpenAI de US$ 0,10 por tentativa |
| AI_HIVE_CEILING_MICROS | 10000 | Reserva Hive de US$ 0,01 por tentativa |
| AI_INPUT_MICROS_PER_MILLION | 750000 | Hipótese de cálculo: US$ 0,75/milhão tokens de entrada |
| AI_OUTPUT_MICROS_PER_MILLION | 4500000 | Hipótese de cálculo: US$ 4,50/milhão tokens de saída |
| AI_RETENTION_DAYS | 7 | Retenção de imagem e descrição; permitido 1–7 dias |

**Taxas são hipóteses configuráveis, não uma cotação nem garantia da fatura.**
A reserva de US$ 0,11 é conservadora para os parâmetros definidos, mas depende
do contrato/tarifa efetivos. A configuração recusa teto OpenAI menor que o
custo calculado para 8192 tokens de entrada e 512 de saída nas taxas informadas.
Uma análise aceita pode fazer no máximo uma chamada OpenAI e uma Hive.
Imagem 512 px, detalhe low, modelo fechado, saída 512 tokens, sem ferramentas.
Os limites financeiros consideram reserva e consumo estimado, não só chamadas.

Os contadores são tabelas Oracle compartilhadas. Uma linha `AI_CONTROL`
bloqueada com `SELECT FOR UPDATE` serializa a admissão, reserva e mudanças de
estado entre instâncias; transações e índices únicos impedem ultrapassagem por
corrida. Não há transação de banco aberta durante HTTP. É apropriado à carga
local limitada; para alto volume, migre o mecanismo atômico a um serviço próprio
ou Redis mantendo também a integridade transacional do ledger financeiro.
Falha do armazenamento aborta a admissão antes da chamada paga.

429 inclui Retry-After: 60. Esse tempo é uma sugestão para consultar novamente,
não a promessa de que cotas diárias ou orçamento estarão liberados em 60 s.
503 indica integração desabilitada, suspensa, circuito aberto ou armazenamento
indisponível. Erros não expõem SQL, chave ou detalhes do provedor.

### Reservas, falhas e suspensão

A reserva é confirmada no banco **antes** do HTTP. Em sucesso, os tokens de
entrada/saída OpenAI são validados e o custo é estimado nas taxas configuradas.
Sem medição monetária verificável da Hive V3, mantém-se o teto Hive inteiro.
Preço atualizado, outras cobranças ou mudança contratual exigem ajuste manual.
Se a reconciliação ultrapassar a reserva, o consumo é registrado e a integração
é suspensa para novas operações.

Timeout, erro HTTP, resposta inválida, falha parcial e processo interrompido
mantêm a reserva inteira como incerta. Ela conta também nos períodos seguintes,
até conferência explícita da cobrança pelo operador. Não há reembolso por TTL.
Depois de 120 s sem conclusão, a manutenção libera apenas a vaga simultânea e
move a verificação para pendência, conservando os valores reservados. Resultado
tardio não altera a operação vencida. O script de reconciliação manual exige
custo confirmado, tentativa inativa e auditabilidade; não execute sem fatura.

Não há retry automático nem fallback: repetir POST sem garantia de idempotência
do provedor pode cobrar duas vezes. Assim, backoff/jitter não são usados. ADMIN
pode solicitar uma única nova tentativa (máximo 2 totais), com confirmação de
possível cobrança, versão atual, imagem existente e nova reserva completa.
Idempotência interna não equivale a uma garantia de cobrança única externa em
crashes/falhas de rede; as reservas incertas representam essa diferença.

Conexão HTTP: 5 s; resposta completa: 20 s, guarda adicional 21 s, limite 64 KiB.
Redirecionamentos nunca são seguidos. Após 3 falhas consecutivas, circuito aberto
por 60 s. Falha de armazenamento durante finalização mantém a reserva anterior;
a manutenção recupera o estado. Um alerta é gerado a 80% do orçamento diário.

ADMIN suspende novas admissões e o backend verifica suspensão antes de cada
chamada. Uma chamada já enviada, ou concorrente na curta janela entre a checagem
e o envio, não pode ter cobrança cancelada com garantia. Para interrupção externa
imediata, revogue a chave/bloqueie tráfego também no provedor e na rede.

### Imagens, dados e pontos

Somente 1 JPEG/PNG real, até 512 KiB binários, 700.000 caracteres Base64 puro e
720.000 bytes JSON na rota de submissão; outras rotas continuam em 16 KiB.
Resolução 32–2048 por lado e até 4.194.304 pixels, verificada antes da rasterização.
Há no máximo dois decodificadores de imagem simultâneos por instância, sem fila;
excesso retorna 429. A imagem é reencodada como JPEG RGB até 512 px, removendo
metadados. Não se aceitam SVG, vídeo, PDF, múltiplas imagens ou URL de entrada.

O backend envia somente ação, descrição e imagem normalizada aos provedores,
sem usuário, email, saldo, JWT ou chaves no contexto. A OpenAI recebe `store:false`;
isso não equivale a retenção zero pelo provedor. Revise os contratos e controles
de dados de ambas as empresas antes de enviar imagens de pessoas reais.

As imagens são BLOBs privados; consultas HTTP exigem propriedade/ADMIN. A
manutenção remove imagem e descrição até um minuto após a retenção vencer,
enquanto a API está em execução e o banco disponível; após período offline,
retoma na próxima execução. A exclusão manual é disponível ao dono e ADMIN.
UUID/hash/ledger financeiro e auditoria permanecem para idempotência e prevenção
de crédito duplicado; não devem ser apagados como se fossem um cache. Defina
com o responsável pela privacidade a retenção desses metadados e dos backups,
criptografia Oracle, acesso de operadores e exclusão no provedor. A entrega não
apaga cópias de backup nem conteúdo já encaminhado aos provedores.

Estados: ENVIADA → EM_ANALISE → PENDENTE_REVISAO → APROVADA ou REJEITADA.
Reprocessamento autorizado retorna PENDENTE_REVISAO a EM_ANALISE. Nenhuma
resposta de IA aprova automaticamente. Falhas, respostas inconclusivas e sinais
de manipulação permanecem para revisão. Não existem critérios completos de
negócio para automação; essa concessão fica bloqueada.

ADMIN deve examinar a evidência pelo endpoint privado e obter confirmação
independente antes de aprovar, fornecendo justificativa. Pontos vêm do catálogo
ACAO, congelados no servidor na submissão. A transação atualiza saldo/histórico,
insere um ledger com chave única da verificação e altera o estado. Aprovações
repetidas retornam o estado final sem novo crédito. Versão impede edição
concorrente indevida; dados de crédito e justificativa são auditados.

Reuso de SHA-256 da imagem normalizada gera somente um sinal. Cortes, pequenas
edições, reencodificações e imagens visualmente semelhantes podem escapar;
imagens iguais legítimas podem disparar o sinal. Redimensionar também pode
reduzir sinais forenses e remover C2PA. A integração privilegia minimização e
custo limitado; não é ferramenta de perícia nem prova de veracidade/autoria.

### Validação e riscos residuais

Execute `.\mvnw.cmd test` na cópia sem config privada e com Java 25. Os testes têm
chaves fictícias e mocks de gateway/transporte; não executam migrações Oracle
nem chamadas reais. Cobrem JWT/propriedade, SQL como dados, prompt injection,
JSON inválido, modelos/tokens fechados, limites/quotas/orçamentos concorrentes,
idempotência concorrente, crédito único, falha de armazenamento, timeout sem
retry/reembolso, circuito/suspensão, retenção, tipos/tamanhos/resolução de imagem,
SSRF por entrada e saída, e ausência de segredos/conteúdo em respostas e logs.

H2 não comprova todo comportamento do Oracle. Revise V003 e realize homologação
Oracle separada com conta/banco de testes; valide permissões, BLOBs, locking,
índices e identidade do histórico antes de ativar. Testes mockados comprovam o
controle local, não a precisão do detector nem o contrato real da sua conta.

Nenhum prompt impede toda manipulação e nenhum detector confirma que a ação
ocorreu. Permanecem riscos de conta comprometida, abuso de ADMIN, cadastros em
massa, ataques distribuídos, imagens falsas não detectadas, limitações de hash,
preços alterados, acesso direto ao banco e disponibilidade da infraestrutura.
Os limites globais reduzem a exposição financeira mesmo com múltiplas contas;
moderação, verificação de cadastro e regras de negócio adicionais são decisões
pendentes. JWT ou rate limit isolados não oferecem proteção completa.


### Submeter e consultar no PowerShell

Faça login novamente se o token anterior expirou ou se você fez logout. Use
`$baseUrl` e `$headers` do fluxo de autenticação. A imagem deve respeitar os
limites acima e a ação deve existir no catálogo.

```powershell
$acoes = Invoke-RestMethod -Uri "$baseUrl/acao?page=0&size=20" -Headers $headers
if (-not $acoes.Count) { throw 'Um ADMIN precisa cadastrar uma ação primeiro.' }
$imagem = [IO.File]::ReadAllBytes((Resolve-Path '.\evidencia.jpg').Path)
$corpoVerificacao = @{
    acaoId = $acoes[0].id
    descricao = 'Separei os resíduos para reciclagem.'
    imagemBase64 = [Convert]::ToBase64String($imagem)
} | ConvertTo-Json -Compress
$chaveOperacao = [Guid]::NewGuid().ToString('N')
$headersIA = @{
    Authorization = $headers.Authorization
    'Idempotency-Key' = $chaveOperacao
}
$verificacao = Invoke-RestMethod -Method Post -Uri "$baseUrl/verificacoes" `
    -Headers $headersIA -ContentType 'application/json; charset=utf-8' `
    -Body $corpoVerificacao

$verificacao = Invoke-RestMethod `
    -Uri "$baseUrl/verificacoes/$($verificacao.id)" -Headers $headers

# A evidência é privada: salve fora do repositório.
$destino = Join-Path $env:TEMP 'evidencia-rockysoulup.jpg'
Invoke-WebRequest -Uri "$baseUrl/verificacoes/$($verificacao.id)/evidencia" `
    -Headers $headers -OutFile $destino
```

Após erro de rede, preserve a mesma chave e o mesmo corpo para consultar/repetir;
não gere outra chave para tentar forçar uma nova análise. Use `estado` para saber
se houve aprovação. `parecer` pode conter sinais ou códigos de falha e não é a
decisão final. A imagem responde como JPEG, não como JSON.

No frontend, carregue a evidência com fetch autenticado, leia `response.blob()`
e use uma Object URL temporária, revogando-a ao terminar. Uma tag img sozinha
não envia Bearer; não coloque token na URL da imagem.

O helper JavaScript da seção 10 aceita a chave de idempotência. Com uma imagem
já convertida em Base64 puro, preserve a chave e o payload antes de chamar:

```javascript
const chaveOperacao = crypto.randomUUID();
const payload = { acaoId: 701, descricao: 'Resíduos separados.', imagemBase64 };
const verificacao = await api('/verificacoes', {
  method: 'POST', body: payload, idempotencyKey: chaveOperacao,
});
```

Se usar FileReader.readAsDataURL no navegador, remova o prefixo anterior à vírgula
antes de enviar imagemBase64. Não exporte imagens privadas junto do repositório.


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
  ai/         Clientes OpenAI/Hive, imagens e componentes auxiliares
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
