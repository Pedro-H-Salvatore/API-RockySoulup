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
    public static final String RULES="sustentavel-score-v5";
    private final JdbcTemplate jdbc;
    private final VerificacaoDAO dao;
    private final AcaoDAO actions;
    private final AiProperties p;
    private final Clock clock;
    private final CapturaDAO captures;
    public AiAdmissionBO(JdbcTemplate jdbc,VerificacaoDAO dao,AcaoDAO actions,AiProperties p,Clock clock,CapturaDAO captures) { this.jdbc=jdbc; this.dao=dao; this.actions=actions; this.p=p; this.clock=clock; this.captures=captures; }
    @org.springframework.beans.factory.annotation.Value("${app.ai.manual-review-only:true}")
    private boolean manualReviewOnly;
    public boolean manualOnly() { return manualReviewOnly; }
    public void lock() { jdbc.queryForObject("SELECT ID_CONTROL FROM AI_CONTROL WHERE ID_CONTROL = 1 FOR UPDATE",Integer.class); }
    @Transactional
    public Admission submit(long user,String ip,String key,VerificacaoRequest request,Evidence evidence) {
        lock();
        String fingerprint=digest(request.acaoId()+"\n"+request.descricao().strip()+"\n"+request.desafioId()+"\n"+evidence.hash()+"\n"+p.model()+"\n"+RULES);
        var existing=dao.existing(user,key,fingerprint);
        if(existing.isPresent()) {
            if(!existing.get().fingerprint().equals(fingerprint)) throw new ConflictException();
            return new Admission(existing.get(),null);
        }
        var pending=jdbc.queryForObject("SELECT COUNT(*), COALESCE(SUM(CASE WHEN ID_USUARIO = ? THEN 1 ELSE 0 END),0) FROM VERIFICACAO WHERE DS_ESTADO IN ('ENVIADA','EM_ANALISE','PENDENTE_REVISAO')",
                (rs,n)->new long[]{rs.getLong(1),rs.getLong(2)},user);
        if(pending[0]>=p.pendingLimit() || pending[1]>=5) throw new AiLimitException();
        captures.validate(request.desafioId(),user,request.acaoId(),clock.instant());
        var action=actions.findAllById(request.acaoId(),null);
        String id=UUID.randomUUID().toString();
        boolean reused=jdbc.queryForObject("SELECT COUNT(*) FROM VERIFICACAO WHERE DS_HASH = ?",Long.class,evidence.hash())>0 || captures.similar(evidence.visualHash(),clock.instant().minus(Duration.ofDays(30)));
        jdbc.update("INSERT INTO VERIFICACAO (ID_VERIFICACAO,ID_USUARIO,ID_ACAO,DS_IDEMPOTENCY,DS_FINGERPRINT,DS_HASH,DS_DESCRICAO,BL_EVIDENCIA,DT_CRIADO,NM_ACAO,NR_PONTOS,DS_MODELO,DS_REGRAS,DS_ESTADO,ST_REUSO) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'ENVIADA',?)",
                id,user,request.acaoId(),key,fingerprint,evidence.hash(),request.descricao().strip(),evidence.jpeg(),Timestamp.from(clock.instant()),action.getNome(),action.getPontos(),p.model(),RULES,reused?1:0);
        captures.consume(request.desafioId(),user,id,evidence.visualHash(),clock.instant());
        if(manualOnly()) {
            jdbc.update("UPDATE VERIFICACAO SET DS_ESTADO = 'PENDENTE_REVISAO', DS_MODELO = 'MANUAL', DS_REGRAS = 'revisao-manual-v1', NR_VERSAO = NR_VERSAO + 1 WHERE ID_VERIFICACAO = ?",id);
            return new Admission(dao.get(id,null,false),null);
        }
        return reserve(dao.get(id,null,true),ip);
    }
    @Transactional
    public Admission reprocess(String id,String ip,long version) {
        lock(); var row=dao.get(id,null,true);
        if(row.response().versao()!=version || !row.response().estado().equals("PENDENTE_REVISAO") || row.evidence()==null || row.response().tentativas()>=2) throw new ConflictException();
        return reserve(row,ip);
    }
    private Admission reserve(VerificationRow row,String ip) {
        if(manualOnly()) throw new AiUnavailableException();
        if(!p.enabled() || p.geminiKey()==null || p.geminiKey().isBlank()) throw new AiUnavailableException();
        Instant now=clock.instant();
        var control=jdbc.queryForMap("SELECT ST_SUSPENSO, DT_OPEN_UNTIL FROM AI_CONTROL WHERE ID_CONTROL = 1");
        if(((Number)control.get("ST_SUSPENSO")).intValue()==1) throw new AiUnavailableException();
        Timestamp open=(Timestamp)control.get("DT_OPEN_UNTIL");
        if(open!=null && open.toInstant().isAfter(now)) throw new AiUnavailableException();
        long user=row.response().usuarioId();
        Instant day=now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant month=now.atZone(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        long ceiling=p.reservation();
        var usage=snapshot(day,month,user);
        if(usage.dayMicros()+ceiling>p.dailyMicros() || usage.monthMicros()+ceiling>p.monthlyMicros() || usage.userMicros()+ceiling>p.userDailyMicros()) throw new AiLimitException();
        if(usage.daily()>=p.globalDaily() || usage.userDaily()>=p.userDaily()) throw new AiLimitException();
        if(usage.active()>=p.concurrency() || usage.userActive()>=1) throw new AiLimitException();
        long window=now.getEpochSecond()/60;
        String userKey=hit("user:"+user,window),ipKey=hit("ip:"+digest(ip),window),globalKey=hit("global",window);
        var rates=jdbc.query("SELECT DS_KEY, NR_COUNT FROM AI_RATE WHERE DS_KEY IN (?,?,?)",(rs,n)->Map.entry(rs.getString(1),rs.getLong(2)),userKey,ipKey,globalKey);
        if(rates.size()!=3 || rates.stream().anyMatch(entry->entry.getValue()>(entry.getKey().equals(userKey)?p.userMinute():entry.getKey().equals(ipKey)?p.ipMinute():p.globalMinute()))) throw new AiLimitException();
        String attempt=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO AI_ATTEMPT (ID_ATTEMPT,ID_VERIFICACAO,ID_USUARIO,DT_CRIADO,DT_LEASE,NR_RESERVADO) VALUES (?,?,?,?,?,?)",
                attempt,row.response().id(),user,Timestamp.from(now),Timestamp.from(now.plusSeconds(120)),ceiling);
        jdbc.update("UPDATE VERIFICACAO SET DS_ESTADO = 'EM_ANALISE', NR_TENTATIVAS = NR_TENTATIVAS + 1, NR_NOTA_COMPATIBILIDADE = NULL, DS_FEEDBACK_IA = NULL, ST_EVIDENCIA_INSUFICIENTE = NULL, ST_SUSPEITA_MANIPULACAO = NULL, NR_VERSAO = NR_VERSAO + 1 WHERE ID_VERIFICACAO = ?",row.response().id());
        if(usage.dayMicros()+ceiling>=p.dailyMicros()*8/10) org.slf4j.LoggerFactory.getLogger("security.events").warn("event=AI_BUDGET_80_PERCENT");
        return new Admission(dao.get(row.response().id(),null,false),attempt);
    }
    public long spend(Instant start,Long user) {
        if(user==null) return jdbc.queryForObject("SELECT COALESCE(SUM(COALESCE(NR_CUSTO,NR_RESERVADO)),0) FROM AI_ATTEMPT WHERE DT_CRIADO >= ? OR ST_INCERTO = 1",Long.class,Timestamp.from(start));
        return jdbc.queryForObject("SELECT COALESCE(SUM(COALESCE(NR_CUSTO,NR_RESERVADO)),0) FROM AI_ATTEMPT WHERE ID_USUARIO = ? AND (DT_CRIADO >= ? OR ST_INCERTO = 1)",Long.class,user,Timestamp.from(start));
    }
    // Read all budget and concurrency counters in one statement, under the existing admission lock.
    public record UsageSnapshot(long dayMicros,long monthMicros,long userMicros,long daily,long userDaily,long active,long userActive,long uncertain) {}
    public UsageSnapshot snapshot(Instant day,Instant month,long user) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN DT_CRIADO >= ? OR ST_INCERTO = 1 THEN COALESCE(NR_CUSTO,NR_RESERVADO) ELSE 0 END),0),
                       COALESCE(SUM(CASE WHEN DT_CRIADO >= ? OR ST_INCERTO = 1 THEN COALESCE(NR_CUSTO,NR_RESERVADO) ELSE 0 END),0),
                       COALESCE(SUM(CASE WHEN ID_USUARIO = ? AND (DT_CRIADO >= ? OR ST_INCERTO = 1) THEN COALESCE(NR_CUSTO,NR_RESERVADO) ELSE 0 END),0),
                       COALESCE(SUM(CASE WHEN DT_CRIADO >= ? THEN 1 ELSE 0 END),0),
                       COALESCE(SUM(CASE WHEN ID_USUARIO = ? AND DT_CRIADO >= ? THEN 1 ELSE 0 END),0),
                       COALESCE(SUM(CASE WHEN ST_ATIVO = 1 THEN 1 ELSE 0 END),0),
                       COALESCE(SUM(CASE WHEN ID_USUARIO = ? AND ST_ATIVO = 1 THEN 1 ELSE 0 END),0),
                       COALESCE(SUM(CASE WHEN ST_INCERTO = 1 THEN 1 ELSE 0 END),0)
                  FROM AI_ATTEMPT WHERE DT_CRIADO >= ? OR ST_INCERTO = 1 OR ST_ATIVO = 1
                """,(rs,n)->new UsageSnapshot(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getLong(5),rs.getLong(6),rs.getLong(7),rs.getLong(8)),
                Timestamp.from(day),Timestamp.from(month),user,Timestamp.from(day),Timestamp.from(day),user,Timestamp.from(day),user,Timestamp.from(month));
    }
    private String hit(String subject,long window) {
        String key=subject+":"+window;
        int changed=jdbc.update("UPDATE AI_RATE SET NR_COUNT = NR_COUNT + 1 WHERE DS_KEY = ?",key);
        if(changed==0) jdbc.update("INSERT INTO AI_RATE (DS_KEY,NR_WINDOW,NR_COUNT) VALUES (?,?,1)",key,window);
        return key;
    }
    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(); }
    }
}
