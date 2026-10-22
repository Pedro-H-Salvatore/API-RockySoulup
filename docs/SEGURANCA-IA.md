# Verificação de ações com Gemini

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
