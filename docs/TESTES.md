# Resultado da validação

Verificado em 14/10/2026, horário de São Paulo, com JDK 25.0.4.
Comando final: `mvn -o verify`. Também foi executado `clean verify` antes da
adição dos três testes finais de HTTP. Build e empacotamento concluídos.

| Suíte | Testes | Falhas | Erros | Ignorados |
|---|---:|---:|---:|---:|
|br.com.rockysoulup.HttpGuardTest|3|0|0|0|
|br.com.rockysoulup.OfflineProvisionTest|1|0|0|0|
|br.com.rockysoulup.SecurityIntegrationTest|52|0|0|0|

**Total: 56 testes, zero falhas, zero erros, zero ignorados.**

Cobertura efetiva: cadeia HTTP com JWT real; anônimo/USER/ADMIN; tokens malformados,
expirados, adulterados, algoritmo/emissor/audiência/duração incorretos; dados de
outro usuário; manipulação de proprietário no corpo; cadastro sem promoção de
perfil; proteção de pontos/resgatados; DTOs sem senha/hash; login e limites;
logout/desativação; CRUDs administrativos; versões/repetição; rollback real de
JDBC; concorrência de saldo, estoque e limitador; bootstrap/ativação offline;
CORS, paginação, HTTPS obrigatório e limite de corpo sem Content-Length.

Os testes usam H2 em memória, modo Oracle, dados fictícios e chave JWT aleatória
por execução. Não foram usadas credenciais de Oracle nem conexões ao banco real.

Limitações: scripts Oracle revisáveis, ainda não homologados/executados;
infraestrutura de HTTPS, segredos, privilégios mínimos e cluster não implantados;
sem análise completa de CVEs. Testes H2 não substituem integração Oracle.
Recuperação de senha e confirmação de email exigem processo operacional externo.
Não há operação USER de resgate/premiação/fabricação, porque as regras não existem.
Revogação vale para chamadas subsequentes; não cancela operações já autorizadas
que estejam em andamento. Limites de login usam janela fixa e banco compartilhado.
