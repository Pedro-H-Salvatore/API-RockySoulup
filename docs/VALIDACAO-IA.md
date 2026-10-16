# Validação da integração de IA

`mvn -o verify`, Java 25. Resultado: 97 testes aprovados; zero falhas, erros ou skips.

H2 em memória, gateway/transporte simulados e chaves fictícias. Sem conexão Oracle ou chamadas pagas.

| Suíte | Testes |
|---|---:|
| EvidenceValidatorTest | 2 |
| ProviderSafetyTest | 17 |
| AiSecurityIntegrationTest | 22 |
| HttpGuardTest | 3 |
| OfflineProvisionTest | 1 |
| SecurityIntegrationTest | 52 |

V003 validada estruturalmente em H2/Oracle mode; homologação Oracle real continua necessária.
