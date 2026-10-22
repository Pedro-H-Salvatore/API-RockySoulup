package br.com.rockysoulup.bo;

import br.com.rockysoulup.dao.*;
import br.com.rockysoulup.to.*;
import br.com.rockysoulup.exception.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.sql.Timestamp;
import java.util.UUID;

@Service
public class VerificacaoDecisionBO {
    private final JdbcTemplate jdbc;private final HistoricoDAO history;private final VerificacaoDAO dao;
    private final AiAdmissionBO admission;private final Clock clock;
    public VerificacaoDecisionBO(JdbcTemplate jdbc,HistoricoDAO history,VerificacaoDAO dao,AiAdmissionBO admission,Clock clock) {
        this.jdbc=jdbc;this.history=history;this.dao=dao;this.admission=admission;this.clock=clock;
    }
    // The same locked transaction and unique ledger are used by manual and automatic approval.
    @Transactional
    public VerificacaoResponse decide(String id,boolean approve,long actor,String event,String reason) {
        admission.lock();var row=dao.get(id,null,true);
        if(row.response().estado().equals(approve?"APROVADA":"REJEITADA")) return row.response();
        if(!row.response().estado().equals("PENDENTE_REVISAO") || (approve && row.evidence()==null)) throw new ConflictException();
        if(approve) {
            long user=row.response().usuarioId();int points=row.pontos();
            if(jdbc.update("UPDATE USUARIO SET NR_PONTOS = NR_PONTOS + ?, NR_VERSAO = NR_VERSAO + 1 WHERE ID_USUARIO = ? AND ST_ATIVO = 1 AND NR_PONTOS <= ?",points,user,Integer.MAX_VALUE-points)!=1) throw new ConflictException();
            var value=new HistoricoTO();value.setUsuarioId(user);value.setDescricao(event.equals("AUTO_APPROVE")?"Ação aprovada automaticamente com desafio de captura":"Verificação sustentável aprovada");value.setPontos(points);value.setDataAcao(LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));history.save(value);
            jdbc.update("INSERT INTO VERIFICACAO_CREDITO (ID_VERIFICACAO,ID_USUARIO,ID_HISTORICO,NR_PONTOS,DT_CREDITO) VALUES (?,?,?,?,?)",id,user,value.getId(),points,Timestamp.from(clock.instant()));
        }
        jdbc.update("UPDATE VERIFICACAO SET DS_ESTADO = ?, NR_PONTOS_CREDITADOS = ?, NR_VERSAO = NR_VERSAO + 1 WHERE ID_VERIFICACAO = ?",approve?"APROVADA":"REJEITADA",approve?row.pontos():0,id);
        jdbc.update("INSERT INTO VERIFICACAO_AUDIT (ID_AUDIT,ID_VERIFICACAO,ID_ATOR,DS_EVENTO,DS_JUSTIFICATIVA,DT_EVENTO) VALUES (?,?,?,?,?,?)",UUID.randomUUID().toString(),id,actor,event,reason,Timestamp.from(clock.instant()));
        return dao.get(id,null,false).response();
    }
    public boolean withinAutomaticLimits(long user,long action,int points) {
        var day=Timestamp.from(clock.instant().atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant());
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM VERIFICACAO_CREDITO WHERE ID_USUARIO = ? AND DT_CREDITO >= ?",Long.class,user,day);
        long earned=jdbc.queryForObject("SELECT COALESCE(SUM(NR_PONTOS),0) FROM VERIFICACAO_CREDITO WHERE ID_USUARIO = ? AND DT_CREDITO >= ?",Long.class,user,day);
        long same=jdbc.queryForObject("SELECT COUNT(*) FROM VERIFICACAO_CREDITO c JOIN VERIFICACAO v ON v.ID_VERIFICACAO = c.ID_VERIFICACAO WHERE c.ID_USUARIO = ? AND v.ID_ACAO = ? AND c.DT_CREDITO >= ?",Long.class,user,action,day);
        return count<3 && earned+points<=100 && same==0;
    }
}
