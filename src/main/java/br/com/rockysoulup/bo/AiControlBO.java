package br.com.rockysoulup.bo;

import br.com.rockysoulup.ai.*;
import br.com.rockysoulup.dao.VerificacaoDAO;
import br.com.rockysoulup.security.AccessPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;

@Service
public class AiControlBO {
    private final JdbcTemplate jdbc;
    private final AiAdmissionBO admission;
    private final VerificacaoDAO dao;
    private final AiProperties properties;
    private final AccessPolicy access;
    private final Clock clock;
    private final br.com.rockysoulup.dao.CapturaDAO captures;
    private final VerificacaoDecisionBO decisions;
    private final boolean automatic;
    public AiControlBO(JdbcTemplate jdbc,AiAdmissionBO admission,VerificacaoDAO dao,AiProperties properties,AccessPolicy access,Clock clock,br.com.rockysoulup.dao.CapturaDAO captures,VerificacaoDecisionBO decisions,
            @org.springframework.beans.factory.annotation.Value("${app.ai.automatic-enabled:true}") boolean automatic) {
        this.jdbc=jdbc; this.admission=admission; this.dao=dao; this.properties=properties; this.access=access; this.clock=clock; this.captures=captures; this.decisions=decisions; this.automatic=automatic;
    }
    @Transactional
    public void beforeDispatch(String attempt) {
        admission.lock();
        var suspended=jdbc.queryForObject("SELECT ST_SUSPENSO FROM AI_CONTROL WHERE ID_CONTROL = 1",Integer.class);
        Timestamp open=jdbc.queryForObject("SELECT DT_OPEN_UNTIL FROM AI_CONTROL WHERE ID_CONTROL = 1",Timestamp.class);
        if(open!=null && open.toInstant().isAfter(clock.instant())) throw new ProviderException("DISPATCH_BLOCKED");
        if(!properties.enabled() || suspended==1 || jdbc.queryForObject("SELECT COUNT(*) FROM AI_ATTEMPT WHERE ID_ATTEMPT = ? AND ST_ATIVO = 1 AND DT_LEASE > ?",Long.class,attempt,Timestamp.from(clock.instant()))!=1) throw new ProviderException("DISPATCH_BLOCKED");
    }
    @Transactional
    public void finish(Admission operation,AiVerdict verdict,String failure,long duration) {
        admission.lock();
        String id=operation.row().response().id();
        var row=dao.get(id,null,true);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ATTEMPT WHERE ID_ATTEMPT = ? AND ST_ATIVO = 1",Long.class,operation.attemptId())!=1 || !row.response().estado().equals("EM_ANALISE")) return;
        boolean success=verdict!=null;
        String summary=success?verdict.safeSummary():failure;
        if(!Set.of("COMPATIVEL","INCOMPATIVEL","INCONCLUSIVO","SINAL_MANIPULACAO","HTTP_FAILURE","TIMEOUT_OR_NETWORK","INTERRUPTED","INVALID_RESPONSE","INVALID_USAGE","DISPATCH_BLOCKED","RESPONSE_TOO_LARGE","DESTINATION_BLOCKED").contains(summary)) summary="INVALID_RESPONSE";
        jdbc.update("UPDATE AI_ATTEMPT SET ST_ATIVO = 0, ST_INCERTO = ?, NR_CUSTO = ?, DS_RESULTADO = ?, NR_INPUT = ?, NR_OUTPUT = ?, NR_DURATION = ? WHERE ID_ATTEMPT = ?",
                success?0:1,success?verdict.costMicros():null,summary,success?verdict.input():0,success?verdict.output():0,Math.max(0,duration),operation.attemptId());
        jdbc.update("UPDATE VERIFICACAO SET DS_ESTADO = 'PENDENTE_REVISAO', DS_PARECER = ?, NR_NOTA_COMPATIBILIDADE = ?, DS_FEEDBACK_IA = ?, ST_EVIDENCIA_INSUFICIENTE = ?, ST_SUSPEITA_MANIPULACAO = ?, NR_VERSAO = NR_VERSAO + 1 WHERE ID_VERIFICACAO = ?",
                summary,success?verdict.notaCompatibilidade():null,success?verdict.feedback():null,success?(verdict.evidenciaInsuficiente()?1:0):null,success?(verdict.suspeitaManipulacao()?1:0):null,id);
        if(success) {
            jdbc.update("UPDATE AI_CONTROL SET NR_FAILURES = 0, DT_OPEN_UNTIL = NULL WHERE ID_CONTROL = 1");
            jdbc.update("UPDATE VERIFICACAO SET DS_MODELO = ? WHERE ID_VERIFICACAO = ?",verdict.providerModel(),id);
            long reserved=jdbc.queryForObject("SELECT NR_RESERVADO FROM AI_ATTEMPT WHERE ID_ATTEMPT = ?",Long.class,operation.attemptId());
            if(verdict.costMicros()>reserved) {
                jdbc.update("UPDATE AI_CONTROL SET ST_SUSPENSO = 1 WHERE ID_CONTROL = 1");
                org.slf4j.LoggerFactory.getLogger("security.events").error("event=AI_COST_CEILING_EXCEEDED");
            }
        } else {
            jdbc.update("UPDATE AI_CONTROL SET NR_FAILURES = NR_FAILURES + 1 WHERE ID_CONTROL = 1");
            if(jdbc.queryForObject("SELECT NR_FAILURES FROM AI_CONTROL WHERE ID_CONTROL = 1",Integer.class)>=3)
                jdbc.update("UPDATE AI_CONTROL SET DT_OPEN_UNTIL = ? WHERE ID_CONTROL = 1",Timestamp.from(clock.instant().plusSeconds(60)));
        }
        if(success && automatic && row.response().regras().equals(AiAdmissionBO.RULES) && row.response().tentativas()==1) {
            String reason=null;
            if(row.evidence()==null) reason="EVIDENCIA_INSUFICIENTE";
            else if(row.response().possivelReuso()) reason="CAPTURA_REPETIDA";
            else if(!captures.matches(id,verdict.codigoVisivel())) reason="DESAFIO_NAO_CONFIRMADO";
            else if(verdict.suspeitaManipulacao()) reason="SINAL_MANIPULACAO";
            else if(verdict.evidenciaInsuficiente()) reason="EVIDENCIA_INSUFICIENTE";
            else if(verdict.parecer().equals("INCONCLUSIVO")) reason="INCONCLUSIVO";
            else if(verdict.costMicros()>properties.reservation()) reason="LIMITE_AUTO";
            else if(verdict.notaCompatibilidade()==null) reason="NOTA_INDISPONIVEL";
            else if(verdict.notaCompatibilidade()<50 && verdict.parecer().equals("INCOMPATIVEL"))
                decisions.decide(id,false,0,"AUTO_REJECT","Compatibilidade abaixo de 50/100 e imagem incompatível; desafio confirmado.");
            else if(verdict.notaCompatibilidade()<=80 && verdict.notaCompatibilidade()>=50) reason="FAIXA_REVISAO";
            else if(verdict.notaCompatibilidade()>80 && verdict.parecer().equals("COMPATIVEL")) {
                if(!decisions.withinAutomaticLimits(row.response().usuarioId(),row.response().acaoId(),row.pontos())) reason="LIMITE_AUTO";
                else decisions.decide(id,true,0,"AUTO_APPROVE","Compatibilidade acima de 80/100, desafio confirmado e limites respeitados. Não é prova de autenticidade.");
            } else reason="DADOS_DIVERGENTES";
            if(reason!=null) jdbc.update("UPDATE VERIFICACAO SET DS_PARECER = ? WHERE ID_VERIFICACAO = ?",reason,id);
        }
        org.slf4j.LoggerFactory.getLogger("security.events").info("event=AI_FINISH user={} operation={} duration_ms={} input={} output={} result={}",
                row.response().usuarioId(),id,Math.max(0,duration),success?verdict.input():0,success?verdict.output():0,summary);
    }
    @Transactional
    public void suspend(boolean suspended) {
        access.requireAdmin(); admission.lock();
        jdbc.update("UPDATE AI_CONTROL SET ST_SUSPENSO = ? WHERE ID_CONTROL = 1",suspended?1:0);
        access.audit(suspended?"AI_SUSPEND":"AI_RESUME");
    }
    @Transactional(readOnly=true)
    public Map<String,Object> usage() {
        access.requireAdmin();
        Instant now=clock.instant();
        var day=now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        var month=now.atZone(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        return Map.of("habilitado",properties.enabled(),"decisaoAutomatica",automatic,"regras",AiAdmissionBO.RULES,"suspenso",jdbc.queryForObject("SELECT ST_SUSPENSO FROM AI_CONTROL WHERE ID_CONTROL = 1",Integer.class)==1,
                "consumoOuReservaDiaMicrosUSD",admission.spend(day,null),"limiteDiaMicrosUSD",properties.dailyMicros(),
                "consumoOuReservaMesMicrosUSD",admission.spend(month,null),"limiteMesMicrosUSD",properties.monthlyMicros(),
                "reservasIncertas",jdbc.queryForObject("SELECT COUNT(*) FROM AI_ATTEMPT WHERE ST_INCERTO = 1",Long.class));
    }
    @Transactional
    public void maintain() {
        admission.lock();
        Instant now=clock.instant();
        var stale=jdbc.queryForList("SELECT ID_ATTEMPT, ID_VERIFICACAO FROM AI_ATTEMPT WHERE ST_ATIVO = 1 AND DT_LEASE < ?",Timestamp.from(now));
        for(var item:stale) {
            jdbc.update("UPDATE AI_ATTEMPT SET ST_ATIVO = 0, ST_INCERTO = 1, DS_RESULTADO = 'LEASE_EXPIRED' WHERE ID_ATTEMPT = ?",item.get("ID_ATTEMPT"));
            jdbc.update("UPDATE VERIFICACAO SET DS_ESTADO = 'PENDENTE_REVISAO', DS_PARECER = 'LEASE_EXPIRED', NR_VERSAO = NR_VERSAO + 1 WHERE ID_VERIFICACAO = ? AND DS_ESTADO = 'EM_ANALISE'",item.get("ID_VERIFICACAO"));
        }
        jdbc.update("UPDATE VERIFICACAO SET BL_EVIDENCIA = NULL, DS_DESCRICAO = '[expirada]', NR_VERSAO = NR_VERSAO + 1 WHERE BL_EVIDENCIA IS NOT NULL AND DT_CRIADO < ? AND DS_ESTADO <> 'EM_ANALISE'",Timestamp.from(now.minus(Duration.ofDays(properties.retentionDays()))));
        jdbc.update("DELETE FROM AI_RATE WHERE NR_WINDOW < ?",now.getEpochSecond()/60-1);
        jdbc.update("DELETE FROM CAPTURA_DESAFIO WHERE ID_VERIFICACAO IS NULL AND DT_EXPIRA < ?",Timestamp.from(now.minus(Duration.ofDays(7))));
    }
}
