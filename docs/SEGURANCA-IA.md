# Verificação de ações com Gemini

O único provedor de IA é o Google Gemini, modelo `gemini-3.5-flash-lite`.
O backend envia uma foto JPEG normalizada e a descrição da ação para classificar
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
| POST `/verificacoes` | Foto base64, descrição, acaoId e cabeçalho Idempotency-Key; 202 |
| GET `/verificacoes?page=0&size=20` | Lista paginada com escopo da sessão |
| GET `/verificacoes/{uuid}` | Estado e metadados |
| GET `/verificacoes/{uuid}/evidencia` | JPEG privado, no-store |
| DELETE `/verificacoes/{uuid}/evidencia` | Exclusão da evidência e descrição |
| POST `/admin/verificacoes/{uuid}/revisao` | Aprovação/rejeição por ADMIN com justificativa e versão |
| POST `/admin/verificacoes/{uuid}/reprocessar` | No máximo um reprocessamento manual, sujeito a cotas/custos |
| POST `/admin/ia/suspensao` | Suspender/retomar novas análises |
| GET `/admin/ia/consumo` | Limites, reservas e estimativas locais |

```json
{"acaoId":21,"descricao":"Preparei uma composteira.","imagemBase64":"<BASE64_JPEG_OU_PNG>"}
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
  "regras":"sustentavel-gemini-v2","tentativas":1,"pontosCreditados":0
}
```

Pareceres novos: COMPATIVEL, INCOMPATIVEL e INCONCLUSIVO. Erros locais ou de
provedor recebem códigos seguros, como HTTP_FAILURE, INVALID_RESPONSE ou
INVALID_USAGE. Um HTTP 202 indica registro para acompanhamento, nunca aprovação.

Estados: ENVIADA → EM_ANALISE → PENDENTE_REVISAO → APROVADA/REJEITADA.
Somente ADMIN aprova manualmente com justificativa. O servidor captura os pontos
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
STOP, modelo permitido e contadores numéricos coerentes são exigidos. Texto
da imagem e descrição são dados não confiáveis, separados das instruções fixas.
Não são aceitas instruções do modelo, SQL, pontos ou ferramentas executáveis.

## Banco e operação

Não é necessária nova migração: as tabelas V001–V003 existentes são reutilizadas.
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
