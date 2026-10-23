package br.com.rockysoulup.dao;
import br.com.rockysoulup.to.*;
import br.com.rockysoulup.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class RoletaDAO {
    private final JdbcTemplate jdbc; private final CartaDAO cards;
    public RoletaDAO(JdbcTemplate jdbc,CartaDAO cards) { this.jdbc=jdbc;this.cards=cards; }
    public record Direito(long cartaId,int chance,int duplicada) {}
    public Direito direito(String id,long user) {
        var owner=jdbc.query("SELECT DS_ESTADO FROM VERIFICACAO WHERE ID_VERIFICACAO=? AND ID_USUARIO=?",(r,n)->r.getString(1),id,user);
        if(owner.isEmpty())throw new NotFoundException();
        if(!owner.getFirst().equals("APROVADA"))throw new ConflictException();
        var rows=jdbc.query("SELECT m.ID_CARTA,m.NR_CHANCE,m.QT_DUPLICADA FROM VERIFICACAO v JOIN VERIFICACAO_CREDITO c ON c.ID_VERIFICACAO=v.ID_VERIFICACAO AND c.ID_USUARIO=v.ID_USUARIO JOIN ACAO_CARTA m ON m.ID_ACAO=v.ID_ACAO JOIN USUARIO u ON u.ID_USUARIO=v.ID_USUARIO WHERE v.ID_VERIFICACAO=? AND v.ID_USUARIO=? AND u.ST_ATIVO=1",(r,n)->new Direito(r.getLong(1),r.getInt(2),r.getInt(3)),id,user);
        if(rows.size()!=1)throw new ConflictException();return rows.getFirst();
    }
    public Optional<RoletaResponse> resultado(String id,long user) {
        return jdbc.query("SELECT ID_CARTA,NR_CHANCE,ST_GANHOU,ST_DUPLICADA,QT_FRAGMENTOS FROM ROLETA_RESULTADO WHERE ID_VERIFICACAO=? AND ID_USUARIO=?",(r,n)->new RoletaResponse(id,cards.findAllById(r.getLong(1),null),r.getInt(2),true,r.getInt(3)==1,r.getInt(4)==1,r.getInt(5)),id,user).stream().findFirst();
    }
    public RoletaResponse preview(String id,Direito d) {return new RoletaResponse(id,cards.findAllById(d.cartaId(),null),d.chance(),false,false,false,0);}
    public List<String> disponiveis(long user,int offset,int size) {
        return jdbc.query("SELECT v.ID_VERIFICACAO FROM VERIFICACAO v JOIN VERIFICACAO_CREDITO c ON c.ID_VERIFICACAO=v.ID_VERIFICACAO AND c.ID_USUARIO=v.ID_USUARIO JOIN ACAO_CARTA m ON m.ID_ACAO=v.ID_ACAO LEFT JOIN ROLETA_RESULTADO r ON r.ID_VERIFICACAO=v.ID_VERIFICACAO WHERE v.ID_USUARIO=? AND v.DS_ESTADO='APROVADA' AND r.ID_VERIFICACAO IS NULL ORDER BY c.DT_CREDITO DESC,v.ID_VERIFICACAO OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",(r,n)->r.getString(1),user,offset,size);
    }
    public boolean temCarta(long user,long card) {return jdbc.queryForObject("SELECT COUNT(*) FROM USUARIO_CARTA WHERE ID_USUARIO=? AND ID_CARTA=?",Long.class,user,card)>0;}
    public void entregar(long user,long card) {jdbc.update("INSERT INTO USUARIO_CARTA (ID_USUARIO,ID_CARTA,QT_CARTA,NR_VERSAO) VALUES (?,?,1,0)",user,card);}
    public void fragmentos(long user,String rarity,int quantity) {
        int count=jdbc.update("UPDATE USUARIO_FRAGMENTO SET QT_FRAGMENTOS=QT_FRAGMENTOS+?,NR_VERSAO=NR_VERSAO+1 WHERE ID_USUARIO=? AND DS_RARIDADE=? AND QT_FRAGMENTOS<=?",quantity,user,rarity,Integer.MAX_VALUE-quantity);
        if(count==0) {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM USUARIO_FRAGMENTO WHERE ID_USUARIO=? AND DS_RARIDADE=?",Long.class,user,rarity)!=0)throw new ConflictException();
            jdbc.update("INSERT INTO USUARIO_FRAGMENTO (ID_USUARIO,DS_RARIDADE,QT_FRAGMENTOS,NR_VERSAO) VALUES (?,?,?,0)",user,rarity,quantity);
        }
    }
    public void save(String id,long user,Direito d,boolean won,boolean duplicate,int fragments,Instant now) {
        jdbc.update("INSERT INTO ROLETA_RESULTADO (ID_VERIFICACAO,ID_USUARIO,ID_CARTA,NR_CHANCE,ST_GANHOU,ST_DUPLICADA,QT_FRAGMENTOS,DT_GIRO) VALUES (?,?,?,?,?,?,?,?)",id,user,d.cartaId(),d.chance(),won?1:0,duplicate?1:0,fragments,Timestamp.from(now));
    }
}
