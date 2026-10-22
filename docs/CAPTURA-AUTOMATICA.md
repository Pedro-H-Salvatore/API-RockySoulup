# Captura e decisão automática

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
