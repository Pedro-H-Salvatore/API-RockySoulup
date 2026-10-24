package br.com.rockysoulup;

import br.com.rockysoulup.bo.*;
import br.com.rockysoulup.dao.*;
import br.com.rockysoulup.security.*;
import br.com.rockysoulup.to.*;
import br.com.rockysoulup.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@ActiveProfiles("test")
class SecurityIntegrationTest {
    private static final String PASSWORD="Test-only-password-2026";
    private static final String HASH=new BCryptPasswordEncoder(12).encode(PASSWORD);
    private static final String SECRET;
    static { byte[] bytes=new byte[32]; new java.security.SecureRandom().nextBytes(bytes); SECRET=Base64.getEncoder().encodeToString(bytes); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("app.security.jwt-secret",()->SECRET); }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired UsuarioBO users;
    @Autowired HistoricoBO history;
    @Autowired RecompensaBO rewards;
    @Autowired RecompensaDAO rewardDAO;
    @Autowired UsuarioDAO userDAO;
    @Autowired LoginLimiter limiter;
    @Autowired PlatformTransactionManager transactions;
    private String a,b,admin;

    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        for (String table:List.of("AUTH_LOGIN_ATTEMPT","USUARIO_CARTA","USUARIO_FRAGMENTO","HISTORICO","CARTA","ACAO","SELO","RECOMPENSA","USUARIO")) jdbc.update("DELETE FROM "+table);
        for (long id:List.of(101L,202L,303L)) jdbc.update("INSERT INTO USUARIO (ID_USUARIO,NM_USUARIO,DS_EMAIL,NR_PONTOS,NR_PONTOS_RESGATADOS,DS_SENHA_HASH,DS_PERFIL,ST_ATIVO,NR_TOKEN_VERSION,NR_VERSAO) VALUES (?,?,?,20,0,?,?,1,0,0)",id,"Pessoa "+id,"user"+id+"@test.invalid",HASH,id==303?"ADMIN":"USER");
        jdbc.update("INSERT INTO ACAO (ID_ACAO,NM_ACAO,NR_PONTOS) VALUES (701,'Reciclar',10)");
        jdbc.update("INSERT INTO CARTA (ID_CARTA,NM_CARTA,NM_CONJUNTO,DS_RARIDADE) VALUES (401,'Carta','Coleção','COMUM')");
        jdbc.update("INSERT INTO RECOMPENSA (ID_RECOMPENSA,NM_RECOMPENSA,NR_CUSTO,NR_ESTOQUE) VALUES (601,'Prêmio',10,5)");
        for (long id:List.of(101L,202L)) {
            jdbc.update("INSERT INTO HISTORICO (ID_HISTORICO,ID_USUARIO,DS_ACAO,NR_PONTOS,DT_ACAO) VALUES (?,?,?,10,CURRENT_TIMESTAMP)",id==101?501:502,id,"Histórico "+id);
            jdbc.update("INSERT INTO USUARIO_CARTA (ID_USUARIO,ID_CARTA,QT_CARTA) VALUES (?,401,1)",id);
            jdbc.update("INSERT INTO USUARIO_FRAGMENTO (ID_USUARIO,DS_RARIDADE,QT_FRAGMENTOS) VALUES (?,'COMUM',3)",id);
        }
        a=token(101,0,Instant.now(),300,"https://test.invalid","security-tests");
        b=token(202,0,Instant.now(),300,"https://test.invalid","security-tests");
        admin=token(303,0,Instant.now(),300,"https://test.invalid","security-tests");
    }
    @Test void loginReturnsPublicProfileAndCapabilityWithoutExposingSecrets() throws Exception {
        for(long user:List.of(101L,303L)) {
            String body="{\"email\":\"user"+user+"@test.invalid\",\"senha\":\""+PASSWORD+"\"}";
            var result=mvc.perform(post("/auth/login").contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString())
                    .andExpect(jsonPath("$.usuario.id").value(user)).andExpect(jsonPath("$.usuario.pontos").value(20))
                    .andExpect(jsonPath("$.administrador").value(user==303)).andReturn().getResponse().getContentAsString();
            assertFalse(result.contains(HASH));assertFalse(result.contains(PASSWORD));assertFalse(result.contains("passwordHash"));
        }
    }
    @Test void sessionRestorationUsesCurrentOwnerRoleAndRevocation() throws Exception {
        mvc.perform(get("/auth/sessao")).andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/sessao").header("Authorization",bearer(a))).andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.id").value(101)).andExpect(jsonPath("$.administrador").value(false));
        mvc.perform(get("/auth/sessao").header("Authorization",bearer(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.administrador").value(true));
        jdbc.update("UPDATE USUARIO SET DS_PERFIL='USER' WHERE ID_USUARIO=303");
        mvc.perform(get("/auth/sessao").header("Authorization",bearer(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.administrador").value(false));
        jdbc.update("UPDATE USUARIO SET NR_TOKEN_VERSION=1 WHERE ID_USUARIO=101");
        mvc.perform(get("/auth/sessao").header("Authorization",bearer(a))).andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/sessao").header("Authorization",bearer(b))).andExpect(status().isOk()).andExpect(jsonPath("$.usuario.id").value(202));
    }
    @Test void sharedIpLimitStillBlocksAcrossDifferentAccounts() {
        for(int i=0;i<50;i++) assertTrue(limiter.consume("user"+i+"@test.invalid","192.0.2.10"));
        assertFalse(limiter.consume("last@test.invalid","192.0.2.10"));
        assertTrue(limiter.consume("unrelated@test.invalid","192.0.2.11"));
    }
    @Test void limiterMaintenanceDeletesOnlyExpiredWindows() {
        long window=Instant.now().getEpochSecond()/900;
        jdbc.update("INSERT INTO AUTH_LOGIN_ATTEMPT (DS_KEY,NR_WINDOW,NR_ATTEMPTS) VALUES ('old',?,10)",window-2);
        jdbc.update("INSERT INTO AUTH_LOGIN_ATTEMPT (DS_KEY,NR_WINDOW,NR_ATTEMPTS) VALUES ('current',?,10)",window);
        limiter.prune();assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM AUTH_LOGIN_ATTEMPT WHERE DS_KEY='old'",Integer.class));
        assertEquals(10,jdbc.queryForObject("SELECT NR_ATTEMPTS FROM AUTH_LOGIN_ATTEMPT WHERE DS_KEY='current'",Integer.class));
    }
    @Test void healthIsPublicAndExposesOnlyDatabaseStatus() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk())
            .andExpect(content().json("{\"status\":\"UP\"}"));
        mvc.perform(post("/health")).andExpect(status().isUnauthorized());
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private String token(long id,long version,Instant issued,long duration,String issuer,String audience) {
        JwtClaimsSet claims=JwtClaimsSet.builder().issuer(issuer).subject(Long.toString(id)).audience(List.of(audience))
                .issuedAt(issued).notBefore(issued).expiresAt(issued.plusSeconds(duration)).claim("ver",version).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }
    private String bearer(String token) { return "Bearer "+token; }
    private void authenticate(long id,boolean isAdmin) {
        Jwt jwt=Jwt.withTokenValue("test-internal").header("alg","HS256").subject(Long.toString(id)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,List.of(new SimpleGrantedAuthority(isAdmin?"ROLE_ADMIN":"ROLE_USER")),Long.toString(id)));
    }
    @ParameterizedTest @ValueSource(strings={"/usuario","/usuario/101","/acao","/carta","/selo","/recompensa","/historico","/usuario-carta","/usuario-fragmento","/unknown"})
    void anonymousCannotRead(String path) throws Exception { mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/json")); }
    @ParameterizedTest @ValueSource(strings={"/acao","/carta","/selo","/recompensa"})
    void catalogReadAllowedButUserWriteDenied(String path) throws Exception {
        mvc.perform(get(path).header("Authorization",bearer(a))).andExpect(status().isOk());
        mvc.perform(post(path).header("Authorization",bearer(a)).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }
    @Test void adminCanMaintainCatalogAndStaleVersionConflicts() throws Exception {
        mvc.perform(post("/acao").header("Authorization",bearer(admin)).contentType("application/json").content("{\"nome\":\"Nova ação\",\"pontos\":15}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber());
        String body="{\"nome\":\"Atualizada\",\"pontos\":11,\"versao\":0}";
        mvc.perform(put("/acao/701").header("Authorization",bearer(admin)).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.versao").value(1));
        mvc.perform(put("/acao/701").header("Authorization",bearer(admin)).contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(delete("/acao/701?versao=1").header("Authorization",bearer(admin))).andExpect(status().isNoContent());
    }
    @Test void userListRestrictedToAdmin() throws Exception {
        mvc.perform(get("/usuario").header("Authorization",bearer(a))).andExpect(status().isForbidden());
        mvc.perform(get("/usuario").header("Authorization",bearer(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].senha").doesNotExist()).andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }
    @Test void profileOwnerEnforcedForReadAndWrite() throws Exception {
        mvc.perform(get("/usuario/202").header("Authorization",bearer(a))).andExpect(status().isNotFound());
        mvc.perform(put("/usuario/202").header("Authorization",bearer(a)).contentType("application/json")
                .content("{\"nome\":\"Alterado\",\"email\":\"user202@test.invalid\",\"versao\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/usuario/202?versao=0").header("Authorization",bearer(a))).andExpect(status().isForbidden());
        mvc.perform(get("/usuario/101").header("Authorization",bearer(a))).andExpect(status().isOk());
    }
    @Test void profileCannotChangeBalancesOrRole() throws Exception {
        mvc.perform(put("/usuario/101").header("Authorization",bearer(a)).contentType("application/json")
                .content("{\"nome\":\"Nome\",\"email\":\"user101@test.invalid\",\"versao\":0,\"pontos\":999,\"resgatados\":999,\"perfil\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(20,userDAO.findAllById(101).pontos());
        assertEquals("USER",userDAO.identity(101).orElseThrow().role());
    }
    @Test void normalProfileUpdateRevokesOldTokens() throws Exception {
        mvc.perform(put("/usuario/101").header("Authorization",bearer(a)).contentType("application/json")
                .content("{\"nome\":\"Novo nome\",\"email\":\"USER101@test.invalid\",\"versao\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pontos").value(20));
        mvc.perform(get("/acao").header("Authorization",bearer(a))).andExpect(status().isUnauthorized());
    }
    @Test void registrationRejectsMassAssignment() throws Exception {
        mvc.perform(post("/auth/cadastro").contentType("application/json").content("{\"nome\":\"Novo\",\"email\":\"new@test.invalid\",\"senha\":\""+PASSWORD+"\",\"perfil\":\"ADMIN\",\"pontos\":100}"))
                .andExpect(status().isBadRequest());
        assertTrue(userDAO.byEmail("new@test.invalid").isEmpty());
    }
    @Test void registrationHashesPasswordSetsServerValuesAndNormalizesEmail() throws Exception {
        String result=mvc.perform(post("/auth/cadastro").contentType("application/json").content("{\"nome\":\"Novo\",\"email\":\"NEW@Test.invalid\",\"senha\":\""+PASSWORD+"\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.pontos").value(0)).andExpect(jsonPath("$.resgatados").value(0))
                .andReturn().getResponse().getContentAsString();
        assertFalse(result.contains(PASSWORD)); assertFalse(result.contains("senha")); assertFalse(result.contains("$2"));
        Account account=userDAO.byEmail("new@test.invalid").orElseThrow();
        assertEquals("USER",account.role()); assertNotEquals(PASSWORD,account.passwordHash());
        assertTrue(new BCryptPasswordEncoder().matches(PASSWORD,account.passwordHash()));
        mvc.perform(post("/auth/cadastro").contentType("application/json").content("{\"nome\":\"Duplicado\",\"email\":\"new@test.invalid\",\"senha\":\""+PASSWORD+"\"}"))
                .andExpect(status().isConflict());
    }
    @Test void bcryptByteLimitAndShortPasswordsRejected() throws Exception {
        for(String password:List.of("short","é".repeat(40))) mvc.perform(post("/auth/cadastro").contentType("application/json")
                .content("{\"nome\":\"Novo\",\"email\":\"new@test.invalid\",\"senha\":\""+password+"\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void loginWorksAndFailuresAreGeneric() throws Exception {
        mvc.perform(post("/auth/login").contentType("application/json").content("{\"email\":\"USER101@test.invalid\",\"senha\":\""+PASSWORD+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.expiresIn").value(300));
        String first=mvc.perform(post("/auth/login").contentType("application/json").content("{\"email\":\"user101@test.invalid\",\"senha\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String second=mvc.perform(post("/auth/login").contentType("application/json").content("{\"email\":\"missing@test.invalid\",\"senha\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertEquals(first,second);
    }
    @Test void loginLimitSharedInDatabase() throws Exception {
        for(int i=0;i<10;i++) assertTrue(limiter.consume("user101@test.invalid","127.0.0.1"));
        mvc.perform(post("/auth/login").contentType("application/json").content("{\"email\":\"user101@test.invalid\",\"senha\":\""+PASSWORD+"\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","900"));
        assertFalse(limiter.consume("user101@test.invalid","different-ip"));
    }
    @Test void malformedExpiredTamperedWrongIssuerAndAudienceTokensDenied() throws Exception {
        String tampered=a.substring(0,a.lastIndexOf('.')+1)+"A".repeat(43);
        for(String token:List.of("not-a-token",tampered,
                token(101,0,Instant.now().minusSeconds(600),300,"https://test.invalid","security-tests"),
                token(101,0,Instant.now(),300,"https://wrong.invalid","security-tests"),
                token(101,0,Instant.now(),300,"https://test.invalid","wrong"),
                token(101,0,Instant.now(),600,"https://test.invalid","security-tests")))
            mvc.perform(get("/acao").header("Authorization",bearer(token))).andExpect(status().isUnauthorized());
    }
    @Test void wrongAlgorithmAndUnsignedTokenDenied() throws Exception {
        String none=Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes())+"."+a.split("\\.")[1]+".";
        mvc.perform(get("/acao").header("Authorization",bearer(none))).andExpect(status().isUnauthorized());
        JwtEncoder other=new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(new byte[64]));
        var claims=JwtClaimsSet.builder().subject("101").issuer("https://test.invalid").audience(List.of("security-tests")).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).claim("ver",0).build();
        String hs512=other.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS512).build(),claims)).getTokenValue();
        mvc.perform(get("/acao").header("Authorization",bearer(hs512))).andExpect(status().isUnauthorized());
    }
    @Test void logoutAndDisableRevokeImmediately() throws Exception {
        mvc.perform(post("/auth/logout").header("Authorization",bearer(a))).andExpect(status().isNoContent());
        mvc.perform(get("/acao").header("Authorization",bearer(a))).andExpect(status().isUnauthorized());
        mvc.perform(delete("/usuario/202?versao=0").header("Authorization",bearer(admin))).andExpect(status().isNoContent());
        mvc.perform(get("/acao").header("Authorization",bearer(b))).andExpect(status().isUnauthorized());
    }
    @ParameterizedTest @ValueSource(strings={"/historico","/usuario-carta","/usuario-fragmento"})
    void ownerListsFilteredAndUserCannotWrite(String path) throws Exception {
        mvc.perform(get(path+"?usuarioId=202").header("Authorization",bearer(a))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].usuarioId").value(101));
        mvc.perform(post(path).header("Authorization",bearer(a)).contentType("application/json").content("{\"usuarioId\":202,\"quantidade\":999}"))
                .andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings={"/historico/502","/usuario-carta/202/401","/usuario-fragmento/202/COMUM"})
    void foreignRecordsCannotBeReadChangedOrDeleted(String path) throws Exception {
        mvc.perform(get(path).header("Authorization",bearer(a))).andExpect(status().isNotFound());
        mvc.perform(put(path).header("Authorization",bearer(a)).contentType("application/json").content("{\"usuarioId\":101}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(path+"?versao=0").header("Authorization",bearer(a))).andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization",bearer(admin))).andExpect(status().isOk());
    }
    @Test void adminCannotChangeHistoricalOwner() throws Exception {
        mvc.perform(put("/historico/501").header("Authorization",bearer(admin)).contentType("application/json")
                .content("{\"usuarioId\":202,\"descricao\":\"Fabricado\",\"pontos\":10,\"dataAcao\":\"2025-01-01T10:00:00\",\"versao\":0}"))
                .andExpect(status().isConflict());
        assertEquals(101L,jdbc.queryForObject("SELECT ID_USUARIO FROM HISTORICO WHERE ID_HISTORICO = 501",Long.class));
    }
    @Test void adminCannotChangeAssociationOwnerViaBody() throws Exception {
        mvc.perform(put("/usuario-carta/101/401").header("Authorization",bearer(admin)).contentType("application/json")
                .content("{\"usuarioId\":202,\"cartaId\":401,\"quantidade\":2,\"versao\":0}"))
                .andExpect(status().isConflict());
    }
    @Test void boAuthorizationCannotBeBypassedThroughDirectInvocation() {
        authenticate(101,false);
        assertThrows(NotFoundException.class,()->users.findAllById(202));
        assertThrows(NotFoundException.class,()->history.findAllById(502L));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->users.saldo(101,new SaldoRequest(999,0,0L)));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->history.save(new HistoricoTO()));
    }
    @Test void userCannotAdjustOwnBalance() throws Exception {
        mvc.perform(put("/usuario/101/saldo").header("Authorization",bearer(a)).contentType("application/json").content("{\"pontos\":999,\"resgatados\":0,\"versao\":0}"))
                .andExpect(status().isForbidden());
    }
    @Test void paginationBodyCorsAndDenyByDefault() throws Exception {
        mvc.perform(get("/acao?size=101").header("Authorization",bearer(a))).andExpect(status().isBadRequest());
        mvc.perform(get("/acao?page=-1").header("Authorization",bearer(a))).andExpect(status().isBadRequest());
        mvc.perform(get("/not-allowed").header("Authorization",bearer(admin))).andExpect(status().isForbidden());
        mvc.perform(post("/auth/login").contentType("application/json").content(" ".repeat(17000))).andExpect(status().isPayloadTooLarge());
        mvc.perform(options("/acao").header("Origin","https://frontend.test.invalid").header("Access-Control-Request-Method","GET"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","https://frontend.test.invalid"));
        mvc.perform(options("/acao").header("Origin","https://evil.invalid").header("Access-Control-Request-Method","GET"))
                .andExpect(status().isForbidden());
    }
    @Test void rollbackUndoesBalanceAndStockChanges() {
        TransactionTemplate tx=new TransactionTemplate(transactions);
        authenticate(303,true);
        assertThrows(IllegalStateException.class,()->tx.execute(status->{
            users.saldo(101,new SaldoRequest(30,1,0L));
            var reward=rewardDAO.findAllById(601L,null); reward.setEstoque(4); rewards.Update(reward);
            throw new IllegalStateException("rollback test");
        }));
        assertEquals(20,userDAO.findAllById(101).pontos());
        assertEquals(5,rewardDAO.findAllById(601L,null).getEstoque());
    }
    @Test void concurrentBalanceAndStockUpdatesHaveOnlyOneWinner() throws Exception {
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            for(boolean balance:List.of(true,false)) {
                CyclicBarrier barrier=new CyclicBarrier(2);
                Callable<Boolean> operation=()->{
                    authenticate(303,true);
                    try {
                        var reward=rewardDAO.findAllById(601L,null);
                        barrier.await(5,TimeUnit.SECONDS);
                        if(balance) users.saldo(101,new SaldoRequest(25,0,0L));
                        else { reward.setEstoque(4); rewards.Update(reward); }
                        return true;
                    } catch(ConflictException e) { return false; }
                    finally { SecurityContextHolder.clearContext(); }
                };
                var first=executor.submit(operation); var second=executor.submit(operation);
                assertNotEquals(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
            }
        } finally { executor.shutdownNow(); }
        assertEquals(25,userDAO.findAllById(101).pontos());
        assertEquals(4,rewardDAO.findAllById(601L,null).getEstoque());
    }
    @Test void replayedAdministrativeAdjustmentConflictsAndNegativeValuesRejected() throws Exception {
        String body="{\"pontos\":25,\"resgatados\":1,\"versao\":0}";
        mvc.perform(put("/usuario/101/saldo").header("Authorization",bearer(admin)).contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(put("/usuario/101/saldo").header("Authorization",bearer(admin)).contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(put("/usuario/101/saldo").header("Authorization",bearer(admin)).contentType("application/json").content("{\"pontos\":-1,\"resgatados\":0,\"versao\":1}"))
                .andExpect(status().isBadRequest());
    }
    @Test void sensitiveToStringValuesAreRedacted() {
        assertFalse(new CadastroRequest("Nome","mail@test.invalid",PASSWORD).toString().contains(PASSWORD));
        assertFalse(new LoginRequest("mail@test.invalid",PASSWORD).toString().contains(PASSWORD));
        assertFalse(new TokenResponse(a,"Bearer",300).toString().contains(a));
        assertFalse(new Account(101,HASH,"USER",true,0).toString().contains(HASH));
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> crudExamples() {
        return java.util.stream.Stream.of(
            org.junit.jupiter.params.provider.Arguments.of("/carta","{\"nome\":\"Nova carta\",\"descricao\":\"Descrição\",\"conjunto\":\"Coleção\",\"raridade\":\"RARA\"}",""),
            org.junit.jupiter.params.provider.Arguments.of("/selo","{\"nome\":\"Novo selo\",\"descricao\":\"Descrição\",\"pontosMin\":100}",""),
            org.junit.jupiter.params.provider.Arguments.of("/recompensa","{\"titulo\":\"Nova recompensa\",\"descricao\":\"Descrição\",\"custo\":10,\"estoque\":4,\"categoria\":\"Categoria\",\"destaque\":\"Ativo\"}",""),
            org.junit.jupiter.params.provider.Arguments.of("/historico","{\"usuarioId\":303,\"descricao\":\"Registro administrativo\",\"pontos\":10,\"dataAcao\":\"2025-01-01T10:00:00\"}",""),
            org.junit.jupiter.params.provider.Arguments.of("/usuario-carta","{\"usuarioId\":303,\"cartaId\":401,\"quantidade\":2}","/303/401"),
            org.junit.jupiter.params.provider.Arguments.of("/usuario-fragmento","{\"usuarioId\":303,\"raridade\":\"COMUM\",\"quantidade\":2}","/303/COMUM")
        );
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("crudExamples")
    void completeAdministrativeCrudAndUserRestrictions(String route,String body,String suffix) throws Exception {
        String created=mvc.perform(post(route).header("Authorization",bearer(admin)).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String record=route+(suffix.isEmpty()?"/"+((Number)com.jayway.jsonpath.JsonPath.read(created,"$.id")).longValue():suffix);
        mvc.perform(get(record).header("Authorization",bearer(admin))).andExpect(status().isOk());
        mvc.perform(put(record).header("Authorization",bearer(a)).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(delete(record+"?versao=0").header("Authorization",bearer(a))).andExpect(status().isForbidden());
        mvc.perform(put(record).header("Authorization",bearer(admin)).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versao").value(1));
        mvc.perform(delete(record+"?versao=1").header("Authorization",bearer(admin))).andExpect(status().isNoContent());
        mvc.perform(get(record).header("Authorization",bearer(admin))).andExpect(status().isNotFound());
        assertEquals(20,userDAO.findAllById(303).pontos()); // CRUD history never awards points.
    }
    @Test void loginLimiterCountsConcurrentRequestsWithoutLostUpdates() throws Exception {
        ExecutorService executor=Executors.newFixedThreadPool(6);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(int i=0;i<16;i++) results.add(executor.submit(()->limiter.consume("same@test.invalid","127.0.0.1")));
            int permitted=0;
            for(var result:results) if(result.get(15,TimeUnit.SECONDS)) permitted++;
            assertEquals(10,permitted);
        } finally { executor.shutdownNow(); }
    }
    @Test void inactiveLegacyAccountsCannotLogin() throws Exception {
        jdbc.update("UPDATE USUARIO SET DS_SENHA_HASH = NULL, ST_ATIVO = 0 WHERE ID_USUARIO = 101");
        mvc.perform(post("/auth/login").contentType("application/json").content("{\"email\":\"user101@test.invalid\",\"senha\":\""+PASSWORD+"\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void cookieAndQueryTokensAreNeverAuthentication() throws Exception {
        mvc.perform(get("/acao").param("access_token",a)).andExpect(status().isUnauthorized());
        mvc.perform(get("/acao").cookie(new jakarta.servlet.http.Cookie("access_token",a))).andExpect(status().isUnauthorized());
    }
    @Test void errorsDoNotRevealSqlOrConnectionDetails() throws Exception {
        String response=mvc.perform(post("/historico").header("Authorization",bearer(admin)).contentType("application/json")
                .content("{\"usuarioId\":9999,\"descricao\":\"Inexistente\",\"pontos\":10,\"dataAcao\":\"2025-01-01T10:00:00\"}"))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("INSERT")); assertFalse(response.contains("SQLException")); assertFalse(response.contains("jdbc:"));
        var handler=new ApiExceptionHandler();
        assertEquals(503,handler.database().getStatusCode().value());
    }
}
