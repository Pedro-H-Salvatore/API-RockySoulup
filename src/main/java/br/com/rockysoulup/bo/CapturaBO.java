package br.com.rockysoulup.bo;

import br.com.rockysoulup.ai.*;
import br.com.rockysoulup.dao.AcaoDAO;
import br.com.rockysoulup.security.AccessPolicy;
import br.com.rockysoulup.to.DesafioResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;

@Service
public class CapturaBO {
    private final JdbcTemplate jdbc;
    private final AiAdmissionBO admission;
    private final AcaoDAO actions;
    private final AccessPolicy access;
    private final Clock clock;
    private final SecureRandom random=new SecureRandom();
    public CapturaBO(JdbcTemplate jdbc,AiAdmissionBO admission,AcaoDAO actions,AccessPolicy access,Clock clock) {
        this.jdbc=jdbc;this.admission=admission;this.actions=actions;this.access=access;this.clock=clock;
    }
    @Transactional
    public DesafioResponse issue(long action,String ip) {
        access.requireAuthenticated();admission.lock();long user=access.userId();actions.findAllById(action,null);
        Instant now=clock.instant();Timestamp at=Timestamp.from(now),minute=Timestamp.from(now.minusSeconds(60));
        var active=jdbc.query("SELECT ID_DESAFIO, DS_CODIGO, DT_EXPIRA FROM CAPTURA_DESAFIO WHERE ID_USUARIO = ? AND ID_ACAO = ? AND ID_VERIFICACAO IS NULL AND DT_EXPIRA > ? ORDER BY DT_CRIADO",(rs,n)->new DesafioResponse(rs.getString(1),action,rs.getString(2),rs.getTimestamp(3).toInstant()),user,action,at);
        if(!active.isEmpty()) return active.getFirst();
        var day=Timestamp.from(now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant());
        String ipHash=AiAdmissionBO.digest(ip);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM CAPTURA_DESAFIO WHERE ID_USUARIO = ? AND DT_CRIADO >= ?",Long.class,user,minute)>=5 ||
                jdbc.queryForObject("SELECT COUNT(*) FROM CAPTURA_DESAFIO WHERE DS_IP_HASH = ? AND DT_CRIADO >= ?",Long.class,ipHash,minute)>=10 ||
                jdbc.queryForObject("SELECT COUNT(*) FROM CAPTURA_DESAFIO WHERE DT_CRIADO >= ?",Long.class,minute)>=20 ||
                jdbc.queryForObject("SELECT COUNT(*) FROM CAPTURA_DESAFIO WHERE ID_USUARIO = ? AND DT_CRIADO >= ?",Long.class,user,day)>=30 ||
                jdbc.queryForObject("SELECT COUNT(*) FROM CAPTURA_DESAFIO WHERE DT_CRIADO >= ?",Long.class,day)>=300) throw new AiLimitException();
        String alphabet="ABCDEFGHJKMNPQRSTUVWXYZ23456789";var code=new StringBuilder();
        for(int i=0;i<6;i++) code.append(alphabet.charAt(random.nextInt(alphabet.length())));
        String id=UUID.randomUUID().toString();Instant expires=now.plusSeconds(300);
        jdbc.update("INSERT INTO CAPTURA_DESAFIO (ID_DESAFIO,ID_USUARIO,ID_ACAO,DS_CODIGO,DS_IP_HASH,DT_CRIADO,DT_EXPIRA) VALUES (?,?,?,?,?,?,?)",id,user,action,code.toString(),ipHash,at,Timestamp.from(expires));
        return new DesafioResponse(id,action,code.toString(),expires);
    }
}
