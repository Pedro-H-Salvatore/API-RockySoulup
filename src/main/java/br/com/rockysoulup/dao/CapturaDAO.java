package br.com.rockysoulup.dao;

import br.com.rockysoulup.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.sql.Timestamp;

@Repository
public class CapturaDAO {
    private final JdbcTemplate jdbc;
    public CapturaDAO(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void validate(String id,long user,long action,Instant now) {
        var rows=jdbc.queryForList("SELECT ID_ACAO, DT_EXPIRA, ID_VERIFICACAO FROM CAPTURA_DESAFIO WHERE ID_DESAFIO = ? AND ID_USUARIO = ?",id,user);
        if(rows.isEmpty()) throw new NotFoundException();
        var row=rows.getFirst();
        if(((Number)row.get("ID_ACAO")).longValue()!=action || row.get("ID_VERIFICACAO")!=null || !((Timestamp)row.get("DT_EXPIRA")).toInstant().isAfter(now)) throw new ConflictException();
    }
    public void consume(String id,long user,String verification,String visualHash,Instant now) {
        if(jdbc.update("UPDATE CAPTURA_DESAFIO SET ID_VERIFICACAO = ?, DT_USADO = ?, DS_HASH_VISUAL = ? WHERE ID_DESAFIO = ? AND ID_USUARIO = ? AND ID_VERIFICACAO IS NULL AND DT_EXPIRA > ?",verification,Timestamp.from(now),visualHash,id,user,Timestamp.from(now))!=1) throw new ConflictException();
    }
    public boolean matches(String verification,String visible) {
        if(visible==null || !visible.matches("[A-Z2-9]{6}")) return false;
        return jdbc.queryForObject("SELECT COUNT(*) FROM CAPTURA_DESAFIO WHERE ID_VERIFICACAO = ? AND DS_CODIGO = ? AND DT_USADO < DT_EXPIRA",Long.class,verification,visible)==1;
    }
    public boolean similar(String visualHash,Instant since) {
        if(visualHash==null || !visualHash.matches("[0-9a-f]{16}")) return true;
        long candidate=Long.parseUnsignedLong(visualHash,16);
        var hashes=jdbc.queryForList("SELECT DS_HASH_VISUAL FROM CAPTURA_DESAFIO WHERE DS_HASH_VISUAL IS NOT NULL AND DT_USADO >= ?",String.class,Timestamp.from(since));
        return hashes.stream().anyMatch(hash->Long.bitCount(candidate^Long.parseUnsignedLong(hash,16))<=4);
    }
}
