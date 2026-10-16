# Diagnóstico inicial da integração de IA

Confirmado no código atual: Java 25, Spring Boot 4.1.1, Spring MVC,
Spring Security/JWT, JDBC com JdbcTemplate/Hikari, Oracle, Lombok e testes H2.
Não existem endpoints de verificação, uploads, chamadas OpenAI/Hive ou premiação
automática. A existência de chaves privadas não ativa essas funções.

| Arquivo/área | Risco/impacto | Correção desta etapa |
|---|---|---|
| resource (sem verificação) | Improvisar upload via CRUD histórico permitiria fraude | Fluxo separado, usuário obtido do JWT |
| security/HttpGuard.java | 16 KiB impede evidência; aumentar globalmente ampliaria abuso | Limite específico apenas na submissão JSON de imagem |
| integrações inexistentes | Chamadas sem orçamento/limites poderiam gerar gastos | Admissão transacional compartilhada antes de HTTP |
| bo/UsuarioBO.java, HistoricoBO.java | CRUD administrativo não representa premiação idempotente | Ledger de crédito único por verificação, transação e revisão manual |
| config/local-secrets.properties | Chaves privadas adicionadas pelo operador | Não copiar, registrar ou usar em testes/chamadas pagas |
| consultas de verificação novas | SQL dinâmico e propriedade indevida | Parâmetros vinculados e escopo obrigatório do usuário |

Preservar JWT, contas ativas/revogação, autorização nos BOs, PreparedStatements,
CORS explícito, CSRF compatível com Bearer e tratamento centralizado de erros.
Novas tabelas serão fornecidas como V003 para revisão, sem execução Oracle.
Não serão feitos testes pagos nem conexões com credenciais reais.

Decisão de negócio: não há critérios suficientes para aprovação automática.
Toda análise segue para revisão; somente ADMIN pode aprovar manualmente com
justificativa. Pontos vêm do catálogo ACAO, capturados no servidor na submissão.
Crédito não depende de pontuação/confiança fornecida pelo cliente ou pelo modelo.
