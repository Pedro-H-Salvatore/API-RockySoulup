package br.com.rockysoulup.bo;

import br.com.rockysoulup.ai.*;
import br.com.rockysoulup.dao.*;
import br.com.rockysoulup.to.*;
import br.com.rockysoulup.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AiAdmissionBO {
    public static final String RULES="sustentavel-gemini-v2";
    private final JdbcTemplate jdbc;
    private final VerificacaoDAO dao;
    private final AcaoDAO actions;
    private final AiProperties p;
    private final Clock clock;
    public AiAdmissionBO(JdbcTemplate jdbc,VerificacaoDAO dao,AcaoDAO actions,AiProperties p,Clock clock) { this.jdbc=jdbc; this.dao=dao; this.actions=actions; this.p=p; this.clock=clock; }
    public void lock() { jdbc.queryForObject("SELECT ID_CONTROL FROM AI_CONTROL WHERE ID_CONTROL = 1 FOR UPDATE",Integer.class); }
    @Transactional
    public Admission submit(long user,String ip,String key,VerificacaoRequest request,Evidence evidence) {
        lock();
        String fingerprint=digest(request.acaoId()+"\n"+request.descricao().strip()+"\n"+evidence.hash()+"\n"+p.model()+"\n"+RULES);
        var existing=dao.existing(user,key,fingerprint);
        if(existing.isPresent()) {
            if(!existing.get().fingerprint().equals(fingerprint)) throw new ConflictException();
            return new Admission(existing.get(),null);
        }
        if(jdbc.queryForObject("SELECT COUNT(*) FROM VERIFICACAO WHERE DS_ESTADO IN ('ENVIADA','EM_ANALISE','PENDENTE_REVISAO')",Long.class)>=p.pendingLimit() ||
                jdbc.queryForObject("SELECT COUNT(*) FROM VERIFICACAO WHERE ID_USUARIO = ? AND DS_ESTADO IN ('ENVIADA','EM_ANALISE','PENDENTE_REVISAO')",Long.class,user)>=5) throw new AiLimitException();
        var action=actions.findAllById(request.acaoId(),null);
        String id=UUID.randomUUID().toString();
        boolean reused=jdbc.queryForObject("SELECT COUNT(*) FROM VERIFICACAO WHERE DS_HASH = ?",Long.class,evidence.hash())>0;
        jdbc.update("INSERT INTO VERIFICACAO (ID_VERIFICACAO,ID_USUARIO,ID_ACAO,DS_IDEMPOTENCY,DS_FINGERPRINT,DS_HASH,DS_DESCRICAO,BL_EVIDENCIA,DT_CRIADO,NM_ACAO,NR_PONTOS,DS_MODELO,DS_REGRAS,DS_ESTADO,ST_REUSO) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'ENVIADA',?)",
                id,user,request.acaoId(),key,fingerprint,evidence.hash(),request.descricao().strip(),evidence.jpeg(),Timestamp.from(clock.instant()),action.getNome(),action.getPontos(),p.model(),RULES,reused?1:0);
        return reserve(dao.get(id,null,true),ip);
    }
    @Transactional
    public Admission reprocess(String id,String ip,long version) {
        lock(); var row=dao.get(id,null,true);
        if(row.response().versao()!=version || !row.response().estado().equals("PENDENTE_REVISAO") || row.evidence()==null || row.response().tentativas()>=2) throw new ConflictException();
        return reserve(row,ip);
    }
    private Admission reserve(VerificationRow row,String ip) {
        if(!p.enabled() || p.geminiKey()==null || p.geminiKey().isBlank()) throw new AiUnavailableException();
        Instant now=clock.instant();
        var control=jdbc.queryForMap("SELECT ST_SUSPENSO, DT_OPEN_UNTIL FROM AI_CONTROL WHERE ID_CONTROL = 1");
        if(((Number)control.get("ST_SUSPENSO")).intValue()==1) throw new AiUnavailableException();
        Timestamp open=jdbc.queryForObject("SELECT DT_OPEN_UNTIL FROM AI_CONTROL WHERE ID_CONTROL = 1",Timestamp.class);
        if(open!=null && open.toInstant().isAfter(now)) throw new AiUnavailableException();
        long user=row.response().usuarioId();
        Instant day=now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant month=now.atZone(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        long ceiling=p.reservation();
        if(spend(day,null)+ceiling>p.dailyMicros() || spend(month,null)+ceiling>p.monthlyMicros() || spend(day,user)+ceiling>p.userDailyMicros()) throw new AiLimitException();
        if(count("SELECT COUNT(*) FROM AI_ATTEMPT WHERE DT_CRIADO >= ?",Timestamp.from(day))>=p.globalDaily() ||
                count("SELECT COUNT(*) FROM AI_ATTEMPT WHERE ID_USUARIO = ? AND DT_CRIADO >= ?",user,Timestamp.from(day))>=p.userDaily()) throw new AiLimitException();
        if(count("SELECT COUNT(*) FROM AI_ATTEMPT WHERE ST_ATIVO = 1")>=p.concurrency() ||
                count("SELECT COUNT(*) FROM AI_ATTEMPT WHERE ST_ATIVO = 1 AND ID_USUARIO = ?",user)>=1) throw new AiLimitException();
        long window=now.getEpochSecond()/60;
        jdbc.update("DELETE FROM AI_RATE WHERE NR_WINDOW < ?",window-1);
        hit("user:"+user,window,p.userMinute()); hit("ip:"+digest(ip),window,p.ipMinute()); hit("global",window,p.globalMinute());
        String attempt=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO AI_ATTEMPT (ID_ATTEMPT,ID_VERIFICACAO,ID_USUARIO,DT_CRIADO,DT_LEASE,NR_RESERVADO) VALUES (?,?,?,?,?,?)",
                attempt,row.response().id(),user,Timestamp.from(now),Timestamp.from(now.plusSeconds(120)),ceiling);
        jdbc.update("UPDATE VERIFICACAO SET DS_ESTADO = 'EM_ANALISE', NR_TENTATIVAS = NR_TENTATIVAS + 1, NR_VERSAO = NR_VERSAO + 1 WHERE ID_VERIFICACAO = ?",row.response().id());
        if(spend(day,null)>=p.dailyMicros()*8/10) org.slf4j.LoggerFactory.getLogger("security.events").warn("event=AI_BUDGET_80_PERCENT");
        return new Admission(dao.get(row.response().id(),null,false),attempt);
    }
    public long spend(Instant start,Long user) {
        if(user==null) return jdbc.queryForObject("SELECT COALESCE(SUM(COALESCE(NR_CUSTO,NR_RESERVADO)),0) FROM AI_ATTEMPT WHERE DT_CRIADO >= ? OR ST_INCERTO = 1",Long.class,Timestamp.from(start));
        return jdbc.queryForObject("SELECT COALESCE(SUM(COALESCE(NR_CUSTO,NR_RESERVADO)),0) FROM AI_ATTEMPT WHERE ID_USUARIO = ? AND (DT_CRIADO >= ? OR ST_INCERTO = 1)",Long.class,user,Timestamp.from(start));
    }
    private long count(String sql,Object... args) { return jdbc.queryForObject(sql,Long.class,args); }
    private void hit(String subject,long window,int max) {
        String key=subject+":"+window;
        int changed=jdbc.update("UPDATE AI_RATE SET NR_COUNT = NR_COUNT + 1 WHERE DS_KEY = ?",key);
        if(changed==0) jdbc.update("INSERT INTO AI_RATE (DS_KEY,NR_WINDOW,NR_COUNT) VALUES (?,?,1)",key,window);
        if(jdbc.queryForObject("SELECT NR_COUNT FROM AI_RATE WHERE DS_KEY = ?",Integer.class,key)>max) throw new AiLimitException();
    }
    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(); }
    }
}
