# API RockySoulup na Render

Serviço Docker com Java 25, perfil `render`, porta `${PORT:10000}` e health check
`GET /health`. O endpoint informa somente UP/DOWN e verifica a conexão ao banco;
não revela configurações. Somente esse GET permite HTTP para sondas internas.
As demais rotas continuam exigindo HTTPS. O Tomcat interpreta cabeçalhos de
encaminhamento somente de proxies internos confiáveis; a porta deve permanecer
acessível apenas pelo ingresso da Render. Verificar HTTPS e IP real após o deploy.

O Docker não copia config/local-secrets.properties, arquivos .env nem chaves.
Configurar DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET (32 bytes aleatórios ou
mais, codificados em Base64), JWT_AUDIENCE, CORS_ALLOWED_ORIGINS e GEMINI_API_KEY
no painel. A Render fornece RENDER_EXTERNAL_URL como issuer, salvo JWT_ISSUER
explícito. Usar JWT próprio deste ambiente; não reutilizar o segredo local.
Não ativar AI_WINDOWS_TRUST_STORE no Linux.

O Blueprint render.yaml usa o plano Free e publicação manual. Não cria banco:
mantém o Oracle externo e exige V001 a V004 previamente aplicadas. Não executar
as migrações novamente no banco já preparado. O serviço precisa alcançar o
Oracle por sua rede de saída; firewall ou restrições do provedor podem impedir.

A partir da avaliação v4, usar AI_ENABLED=true e AI_AUTO_APPROVAL_ENABLED=true.
Aplicar a migração V007 antes de publicar; veja AVALIACAO-AUTOMATICA.md para as
faixas, verificações obrigatórias e revisão de casos inconclusivos. Nunca inserir
chaves em Git/Docker. Manter a chave Gemini somente nas variáveis do serviço.

CORS inicial autoriza o front local na porta 5173. Para front hospedado, adicionar
o domínio HTTPS exato e definir VITE_API_BASE_URL no build do front com a URL
do serviço. Câmera requer HTTPS ou localhost. No Free pode haver suspensão por
inatividade e demora na primeira resposta. Não usar como garantia de disponibilidade.

Validação: GET /health deve retornar 200 UP; GET /acao sem token deve retornar
401; login/cadastro, CORS e captura devem ser testados com a URL pública. Nunca
registrar senhas, tokens ou imagens nos logs de deploy. Arquivos de evidência
persistem no Oracle, não no disco efêmero da Render.
