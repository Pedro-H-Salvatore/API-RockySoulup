# Validação da integração Gemini

Java 25, Maven offline verify: 102 testes aprovados; zero falhas, erros ou skips.

| Suíte | Testes |
| --- | ---: |
| EvidenceValidatorTest | 2 |
| ProviderSafetyTest | 22 |
| AiSecurityIntegrationTest | 22 |
| HttpGuardTest | 3 |
| OfflineProvisionTest | 1 |
| SecurityIntegrationTest | 52 |

H2 em memória, chaves fictícias, gateway e transporte simulados. Nenhuma chamada
de geração ou migração no Oracle real faz parte da suíte. As contas e os dados
do banco local não são usados pelos testes.

No Windows, a suite verifica a inicialização do contexto TLS com raízes do sistema.
Uma consulta GET separada de metadados confirmou HTTP 200 e acesso da chave ao
modelo, sem enviar imagens ou gerar conteúdo. Isso não valida disponibilidade
de cota de geração nem precisão visual.
