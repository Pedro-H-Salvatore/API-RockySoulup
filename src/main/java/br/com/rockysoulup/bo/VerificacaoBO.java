package br.com.rockysoulup.bo;

import br.com.rockysoulup.ai.*;
import br.com.rockysoulup.dao.*;
import br.com.rockysoulup.to.*;
import br.com.rockysoulup.security.AccessPolicy;
import br.com.rockysoulup.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;

@Service
public class VerificacaoBO {
    private final AccessPolicy access;
    private final EvidenceValidator images;
    private final AiAdmissionBO admission;
    private final AiControlBO control;
    private final AiProviderGateway provider;
    private final VerificacaoDAO dao;
    private final HistoricoDAO history;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final VerificacaoDecisionBO decisions;
    public VerificacaoBO(AccessPolicy access,EvidenceValidator images,AiAdmissionBO admission,AiControlBO control,AiProviderGateway provider,VerificacaoDAO dao,HistoricoDAO history,JdbcTemplate jdbc,Clock clock,VerificacaoDecisionBO decisions) {
        this.access=access; this.images=images; this.admission=admission; this.control=control; this.provider=provider; this.dao=dao; this.history=history; this.jdbc=jdbc; this.clock=clock; this.decisions=decisions;
    }
    public VerificacaoResponse submit(VerificacaoRequest request,String key,String ip) {
        access.requireAuthenticated();
        if(key==null || !key.matches("[A-Za-z0-9_-]{16,64}")) throw new IllegalArgumentException();
        return execute(admission.submit(access.userId(),ip,key,request,images.validate(request.imagemBase64())));
    }
    public VerificacaoResponse reprocess(String id,long version,String ip) {
        access.requireAdmin();
        access.audit("AI_REPROCESS");
        return execute(admission.reprocess(id,ip,version));
    }
    // Admission commits before the first paid request. No database transaction spans network I/O.
    private VerificacaoResponse execute(Admission operation) {
        if(!operation.execute()) return operation.row().response();
        long start=System.nanoTime();
        AiVerdict result=null; String failure=null;
        try { result=provider.evaluate(operation.row(),()->control.beforeDispatch(operation.attemptId())); }
        catch(ProviderException e) { failure=e.code(); }
        catch(RuntimeException e) { failure="INVALID_RESPONSE"; }
        control.finish(operation,result,failure,(System.nanoTime()-start)/1000000);
        return dao.metadata(operation.row().response().id(),access.scope());
    }
    @Transactional(readOnly=true)
    public VerificacaoResponse get(String id) { access.requireAuthenticated(); return dao.metadata(id,access.scope()); }
    @Transactional(readOnly=true)
    public List<VerificacaoResponse> list(int page,int size) { access.requireAuthenticated(); return dao.list(access.scope(),access.offset(page,size),size); }
    @Transactional(readOnly=true)
    public byte[] evidence(String id) {
        access.requireAuthenticated(); var row=dao.get(id,access.scope(),false);
        if(row.evidence()==null) throw new NotFoundException(); return row.evidence();
    }
    @Transactional
    public void deleteEvidence(String id) {
        access.requireAuthenticated(); admission.lock(); var row=dao.get(id,access.scope(),true);
        if(row.response().estado().equals("EM_ANALISE")) throw new ConflictException();
        jdbc.update("UPDATE VERIFICACAO SET BL_EVIDENCIA = NULL, DS_DESCRICAO = '[excluida]', NR_VERSAO = NR_VERSAO + 1 WHERE ID_VERIFICACAO = ?",id);
        audit(id,"EVIDENCE_DELETE",null);
    }
    @Transactional
    public VerificacaoResponse review(String id,RevisaoRequest request) {
        access.requireAdmin(); admission.lock(); var row=dao.get(id,null,true);
        if(row.response().estado().equals(request.aprovar()?"APROVADA":"REJEITADA")) return row.response();
        if(row.response().versao()!=request.versao() || !row.response().estado().equals("PENDENTE_REVISAO") || (request.aprovar() && row.evidence()==null)) throw new ConflictException();
        var result=decisions.decide(id,request.aprovar(),access.userId(),request.aprovar()?"MANUAL_APPROVE":"MANUAL_REJECT",request.justificativa());
        access.audit(request.aprovar()?"MANUAL_APPROVE":"MANUAL_REJECT");
        return result;
    }

    private void audit(String id,String event,String reason) {
        jdbc.update("INSERT INTO VERIFICACAO_AUDIT (ID_AUDIT,ID_VERIFICACAO,ID_ATOR,DS_EVENTO,DS_JUSTIFICATIVA,DT_EVENTO) VALUES (?,?,?,?,?,?)",UUID.randomUUID().toString(),id,access.userId(),event,reason,Timestamp.from(clock.instant()));
        access.audit(event);
    }
}
