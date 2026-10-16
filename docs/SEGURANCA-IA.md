# Verificação sustentável com IA — operação e segurança

## Estado da entrega

Implementação local para Java 25/Spring Boot 4.1.1, MVC, Security/JWT,
JdbcTemplate/Hikari e Oracle. As camadas `resource`, `bo`, `dao`, `to` foram
preservadas; o pacote `ai` contém validação de imagens, configuração e clientes.
O diagnóstico está em `DIAGNOSTICO-IA.md`.

**IA desabilitada por padrão (`AI_ENABLED=false`).** Adicionar chaves não ativa
cobranças. Nenhuma migração Oracle ou chamada paga foi executada na entrega.
Os testes usam H2 e chaves fictícias, sem comunicação com provedores.

## Ativação local

1. Faça backup do banco e revise `db/migration/V003__verification_ai_controls.sql`
   com o responsável pelo schema, depois de V001/V002. O script não é executado
   pelo Spring. Oracle faz commit implícito de DDL; não existe rollback automático
   desta migração. Ela cria tabelas e índices, sem editar saldos existentes.
2. Use uma conta de execução com SELECT/INSERT/UPDATE/DELETE somente nas tabelas
   necessárias. Migrações/provisionamento devem usar credenciais separadas. Não
   dê CREATE, ALTER, DROP, acesso a outros schemas ou privilégios DBA à API.
   A conta acadêmica atual pode depender das permissões oferecidas pela FIAP;
   essa separação precisa ser realizada pelo administrador do banco.
3. Preserve `config/local-secrets.properties`, que é privado e ignorado pelo Git.
   As chaves já adicionadas são lidas como `OPENAI_API_KEY` e `HIVE_API_KEY`.
   O arquivo `config-examples/ai-local.properties.example` mostra os nomes e
   limites, sem credenciais. Spring não carrega um arquivo `.env` automaticamente.
4. Confirme acesso da sua conta ao modelo `gpt-6-luna` na OpenAI e à API Hive V3
   `hive/ai-generated-and-deepfake-content-detection`. Chave de projeto Hive V2
   não é intercambiável com uma chave V3. A entrega valida o contrato com mocks;
   a disponibilidade e as permissões reais da sua conta não foram testadas.
5. Confira preços/contrato dos provedores e ajuste reservas e taxas estimadas.
   Configure limites também no painel do provedor, verificando se são bloqueios
   efetivos ou somente alertas. Na OpenAI, habilite `Enforce a hard limit` nos
   spend limits da sua organização/projeto; alertas sozinhos não bloqueiam
   gastos. Considere atraso de contabilização/propagação e mantenha também o
   teto local. Restrinja permissões/modelos da chave e configure rate limits.
6. No IntelliJ, abra Run → Edit Configurations e defina Working directory como
   a raiz `rockySoulup`. Depois da revisão do banco e dos limites, adicione
   `AI_ENABLED=true` às Environment variables dessa execução. Reinicie a API.
   Isso permite cobranças em novas submissões autenticadas. Para continuar
   testando sem custos, mantenha `false` e use a suíte de testes.

As configurações locais existentes de JWT, CORS e bind em 127.0.0.1 continuam
válidas. Em hospedagem, configure HTTPS, origens exatas, secrets manager,
proteção de rede, limites no proxy e banco com TLS/criptografia em repouso.
Não exponha HTTP local diretamente à internet. Os cabeçalhos Forwarded e
X-Forwarded-For são ignorados; IP vem de `getRemoteAddr()`. Atrás de proxy,
isso pode limitar todos os usuários pelo IP do proxy; configure proxies
confiáveis conscientemente antes de alterar esse comportamento.

## Novos endpoints

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

## Limites padrão

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

## Reservas, falhas e suspensão

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

## Imagens, dados e pontos

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

## Validação e riscos residuais

Execute `mvn -o test` na cópia sem config privada e com Java 25. Os testes têm
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

## Referências de contrato

- [OpenAI Responses](https://developers.openai.com/api/reference/resources/responses/methods/create)
- [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
- [OpenAI spend limits](https://developers.openai.com/api/docs/guides/spend-limits)
- [Hive V3: AI-generated and deepfake content detection](https://docs.thehive.ai/docs/ai-generated-and-deepfake-content-detection-playground)

Documentação consultada para o contrato HTTP público. Nenhuma chave é incluída
nos exemplos ou nos arquivos de entrega.
