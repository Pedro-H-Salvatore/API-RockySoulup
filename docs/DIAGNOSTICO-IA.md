# Diagnóstico da integração Gemini

O backend usa exclusivamente GeminiGateway com destino Google fixo. O arquivo
privado local fornece GEMINI_API_KEY; as configurações anteriores foram removidas.
Autenticação, autorização, catálogo, tabelas e saldos permanecem preservados.
Esta etapa não inicia servidor em segundo plano nem executa migrações Oracle.

Se não analisar uma foto, confira AI_ENABLED, presença da chave, acesso ao modelo,
cotas do projeto, conectividade, suspensão e reservas incertas. Uma tentativa
que chega ao provedor pode ficar PENDENTE_REVISAO mesmo quando a geração falha.
Nunca apague reservas ou repita automaticamente uma chamada após timeout.

Não publique fotos pessoais ou confidenciais na cota gratuita. Consulte
SEGURANCA-IA.md para os limites e o tratamento de dados do provedor.
