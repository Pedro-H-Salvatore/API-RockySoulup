# Diagnóstico inicial

O projeto contém somente CRUDs; não há operação de premiação, resgate, compra
ou fabricação de cartas que calcule saldo no servidor. Essas operações não serão
inventadas. Escritas de histórico, coleção e fragmentos serão administrativas.

Achados confirmados no código de partida:

- Todas as rotas estavam públicas e não havia identidade autenticada.
- ConnectionFactory continha credenciais e uma Connection estática compartilhada.
- Resources e BOs usavam new, sem proxies de autorização ou transação.
- UsuarioTO recebia saldo e resgatados; IDs de proprietário eram confiados ao cliente.
- SQLException virava null/false, indistinguível de ausência de registros.
- Não existiam paginação, limite de corpo, revogação ou limitação de login.

Plano: primeiro configurar dependências gerenciadas pelo BOM 4.1.1 e JDBC
transacional; depois identidade, autorização por rota e registro, erros e limites;
por último validar com banco H2 descartável, sem acessar Oracle real.

Compatibilidade verificada: o BOM local do Spring Boot 4.1.1 gerencia Spring
Security 7.1.1, HikariCP 7.0.2 e H2 2.4.240. Serão usadas versões gerenciadas,
mantendo Java 25, Spring MVC, Oracle, Lombok e os pacotes resource/bo/dao/to.

Referências oficiais:
- https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html
- https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- https://docs.spring.io/spring-framework/reference/data-access/transaction/tx-resource-synchronization.html
- https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html
