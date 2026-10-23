package br.com.rockysoulup;

import br.com.rockysoulup.ai.RoletaSorteio;
import br.com.rockysoulup.bo.RoletaBO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:roleta;MODE=Oracle;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "spring.sql.init.schema-locations=classpath:schema-test.sql,classpath:schema-ai.sql,classpath:schema-roleta.sql"})
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@ActiveProfiles("test")
class RoletaIntegrationTest {
    @DynamicPropertySource static void config(DynamicPropertyRegistry r){r.add("app.security.jwt-secret",()->Base64.getEncoder().encodeToString(new byte[32]));}
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired RoletaBO bo;
    @MockitoBean RoletaSorteio sorteio;
    String id;
    @BeforeEach void setup(){
        SecurityContextHolder.clearContext(); reset(sorteio);
        for(String table:List.of("ROLETA_RESULTADO","ACAO_CARTA","VERIFICACAO_CREDITO","VERIFICACAO","USUARIO_CARTA","USUARIO_FRAGMENTO","HISTORICO","CARTA","ACAO","USUARIO"))jdbc.update("DELETE FROM "+table);
        for(long user:List.of(101L,202L,303L))jdbc.update("INSERT INTO USUARIO(ID_USUARIO,NM_USUARIO,DS_EMAIL,NR_PONTOS,DS_PERFIL,ST_ATIVO) VALUES(?,?,?,50,?,1)",user,"Teste",user+"@test.invalid",user==303?"ADMIN":"USER");
        jdbc.update("INSERT INTO ACAO(ID_ACAO,NM_ACAO,NR_PONTOS) VALUES(701,'Reciclagem',10)");
        jdbc.update("INSERT INTO CARTA(ID_CARTA,NM_CARTA,NM_CONJUNTO,DS_RARIDADE) VALUES(801,'Reciclagem','Recursos','COMUM')");
        jdbc.update("INSERT INTO ACAO_CARTA VALUES(701,801,75,3)");
        id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO VERIFICACAO(ID_VERIFICACAO,ID_USUARIO,ID_ACAO,DS_IDEMPOTENCY,DS_FINGERPRINT,DS_HASH,DS_DESCRICAO,DT_CRIADO,NM_ACAO,NR_PONTOS,DS_MODELO,DS_REGRAS,DS_ESTADO) VALUES(?,101,701,'key','fingerprint','hash','Ação',CURRENT_TIMESTAMP,'Reciclagem',10,'teste','v1','APROVADA')",id);
        jdbc.update("INSERT INTO HISTORICO(ID_HISTORICO,ID_USUARIO,DS_ACAO,NR_PONTOS) VALUES(901,101,'Reciclagem',10)");
        jdbc.update("INSERT INTO VERIFICACAO_CREDITO(ID_VERIFICACAO,ID_USUARIO,ID_HISTORICO,NR_PONTOS,DT_CREDITO) VALUES(?,101,901,10,CURRENT_TIMESTAMP)",id);
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    String token(long user){var now=Instant.now();var claims=JwtClaimsSet.builder().subject(""+user).issuer("https://test.invalid").audience(List.of("security-tests")).issuedAt(now).expiresAt(now.plusSeconds(300)).claim("ver",0).build();return "Bearer "+encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();}
    String path(){return "/verificacoes/"+id+"/roleta";}
    long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    @Test void onlyOwnerCanClaimIncludingAdmins() throws Exception{
        mvc.perform(post(path())).andExpect(status().isUnauthorized());
        for(long user:List.of(202L,303L))mvc.perform(post(path()).header("Authorization",token(user))).andExpect(status().isNotFound());
        assertEquals(0,count("ROLETA_RESULTADO")); verifyNoInteractions(sorteio);
    }
    @Test void approvalRequiresActualCredit() throws Exception{
        for(String state:List.of("PENDENTE_REVISAO","REJEITADA")){jdbc.update("UPDATE VERIFICACAO SET DS_ESTADO=?",state);mvc.perform(post(path()).header("Authorization",token(101))).andExpect(status().isConflict());}
        jdbc.update("UPDATE VERIFICACAO SET DS_ESTADO='APROVADA'");jdbc.update("DELETE FROM VERIFICACAO_CREDITO");
        mvc.perform(post(path()).header("Authorization",token(101))).andExpect(status().isConflict());verifyNoInteractions(sorteio);
    }
    @Test void winGrantsOnceAndNeverDebitsPoints() throws Exception{
        when(sorteio.ganhou(75)).thenReturn(true);
        for(int n=0;n<2;n++)mvc.perform(post(path()).header("Authorization",token(101))).andExpect(status().isOk()).andExpect(jsonPath("$.ganhou").value(true)).andExpect(jsonPath("$.duplicada").value(false));
        assertEquals(1,count("USUARIO_CARTA"));assertEquals(0,count("USUARIO_FRAGMENTO"));assertEquals(1,count("ROLETA_RESULTADO"));
        assertEquals(50,jdbc.queryForObject("SELECT NR_PONTOS FROM USUARIO WHERE ID_USUARIO=101",Integer.class));verify(sorteio,times(1)).ganhou(75);
    }
    @Test void lostResultCannotBeRedrawn() throws Exception{
        when(sorteio.ganhou(75)).thenReturn(false,true);
        for(int n=0;n<2;n++)mvc.perform(post(path()).header("Authorization",token(101))).andExpect(status().isOk()).andExpect(jsonPath("$.ganhou").value(false));
        assertEquals(0,count("USUARIO_CARTA"));assertEquals(0,count("USUARIO_FRAGMENTO"));verify(sorteio,times(1)).ganhou(75);
    }
    @Test void duplicateBecomesFragmentsOnce() throws Exception{
        jdbc.update("INSERT INTO USUARIO_CARTA VALUES(101,801,1,0)");when(sorteio.ganhou(75)).thenReturn(true);
        for(int n=0;n<2;n++)mvc.perform(post(path()).header("Authorization",token(101))).andExpect(status().isOk()).andExpect(jsonPath("$.duplicada").value(true)).andExpect(jsonPath("$.fragmentos").value(3));
        assertEquals(3,jdbc.queryForObject("SELECT QT_FRAGMENTOS FROM USUARIO_FRAGMENTO",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT QT_CARTA FROM USUARIO_CARTA",Integer.class));
    }
    @Test void availableListIsPrivateAndConsumedOnlyOnPost() throws Exception{
        mvc.perform(get("/roletas").header("Authorization",token(202))).andExpect(content().json("[]"));
        mvc.perform(get("/roletas").header("Authorization",token(101))).andExpect(jsonPath("$[0].verificacaoId").value(id));
        mvc.perform(get(path()).header("Authorization",token(101))).andExpect(jsonPath("$.girada").value(false));assertEquals(0,count("ROLETA_RESULTADO"));
        mvc.perform(post(path()).header("Authorization",token(101))).andExpect(status().isOk());
        mvc.perform(get("/roletas").header("Authorization",token(101))).andExpect(content().json("[]"));
        mvc.perform(get("/verificacoes/invalido/roleta").header("Authorization",token(101))).andExpect(status().isBadRequest());
    }
    @Test void fragmentOverflowDoesNotConsumeTheSpin() throws Exception{
        jdbc.update("INSERT INTO USUARIO_CARTA VALUES(101,801,1,0)");
        jdbc.update("INSERT INTO USUARIO_FRAGMENTO VALUES(101,'COMUM',2147483647,0)");
        when(sorteio.ganhou(75)).thenReturn(true);
        mvc.perform(post(path()).header("Authorization",token(101))).andExpect(status().isConflict());
        assertEquals(0,count("ROLETA_RESULTADO"));
        assertEquals(2147483647,jdbc.queryForObject("SELECT QT_FRAGMENTOS FROM USUARIO_FRAGMENTO",Integer.class));
    }
    @Test void concurrentClicksHaveOnePersistedResult() throws Exception{
        when(sorteio.ganhou(75)).thenReturn(true);
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<Object> spin=()->{var jwt=Jwt.withTokenValue("test").header("alg","HS256").subject("101").build();SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,List.of(new SimpleGrantedAuthority("ROLE_USER")),"101"));try{return bo.girar(id);}finally{SecurityContextHolder.clearContext();}};
            var a=pool.submit(spin);var b=pool.submit(spin);assertEquals(a.get(10,TimeUnit.SECONDS).toString(),b.get(10,TimeUnit.SECONDS).toString());
        }
        assertEquals(1,count("ROLETA_RESULTADO"));assertEquals(1,count("USUARIO_CARTA"));verify(sorteio,times(1)).ganhou(75);
    }
}
