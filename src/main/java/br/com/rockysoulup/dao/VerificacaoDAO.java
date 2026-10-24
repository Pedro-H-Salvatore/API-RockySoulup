package br.com.rockysoulup.dao;

import br.com.rockysoulup.ai.*;
import br.com.rockysoulup.to.VerificacaoResponse;
import br.com.rockysoulup.exception.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.util.*;

@Repository
public class VerificacaoDAO {
    private static final String METADATA="SELECT ID_VERIFICACAO, ID_USUARIO, ID_ACAO, DS_ESTADO, NR_VERSAO, ST_REUSO, DS_PARECER, DS_MODELO, DS_REGRAS, NR_TENTATIVAS, NR_PONTOS_CREDITADOS, NR_NOTA_COMPATIBILIDADE, DS_FEEDBACK_IA, ST_EVIDENCIA_INSUFICIENTE, ST_SUSPEITA_MANIPULACAO, CASE WHEN BL_EVIDENCIA IS NULL THEN 0 ELSE 1 END AS ST_EVIDENCIA FROM VERIFICACAO";
    private final JdbcTemplate jdbc;
    public VerificacaoDAO(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private VerificationRow map(ResultSet rs,int row) throws SQLException {
        byte[] bytes=rs.getBytes("BL_EVIDENCIA");
        var response=response(rs,bytes!=null);
        return new VerificationRow(response,rs.getString("DS_FINGERPRINT"),rs.getString("DS_HASH"),bytes,rs.getString("DS_DESCRICAO"),rs.getString("NM_ACAO"),rs.getInt("NR_PONTOS"),rs.getTimestamp("DT_CRIADO").toInstant());
    }
    public VerificationRow get(String id,Long scope,boolean lock) {
        String suffix=lock?" FOR UPDATE":""; // closed server-controlled options only
        var rows=scope==null ? jdbc.query("SELECT * FROM VERIFICACAO WHERE ID_VERIFICACAO = ?"+suffix,this::map,id) :
                jdbc.query("SELECT * FROM VERIFICACAO WHERE ID_VERIFICACAO = ? AND ID_USUARIO = ?"+suffix,this::map,id,scope);
        if(rows.isEmpty()) throw new NotFoundException(); return rows.getFirst();
    }
    public Optional<VerificationRow> existing(long user,String key,String fingerprint) {
        var byKey=jdbc.query("SELECT * FROM VERIFICACAO WHERE ID_USUARIO = ? AND DS_IDEMPOTENCY = ?",this::map,user,key);
        if(!byKey.isEmpty()) return byKey.stream().findFirst();
        var rows=jdbc.query("SELECT * FROM VERIFICACAO WHERE ID_USUARIO = ? AND DS_FINGERPRINT = ?",this::map,user,fingerprint);
        return rows.stream().findFirst();
    }
    public List<VerificacaoResponse> list(Long scope,int offset,int size) {
        if(scope!=null) return jdbc.query(METADATA+" WHERE ID_USUARIO = ? ORDER BY DT_CRIADO, ID_VERIFICACAO OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",this::metadata,scope,offset,size);
        return jdbc.query(METADATA+" ORDER BY DT_CRIADO, ID_VERIFICACAO OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",this::metadata,offset,size);
    }
    public VerificacaoResponse metadata(String id,Long scope) {
        var rows=scope==null ? jdbc.query(METADATA+" WHERE ID_VERIFICACAO = ?",this::metadata,id) :
                jdbc.query(METADATA+" WHERE ID_VERIFICACAO = ? AND ID_USUARIO = ?",this::metadata,id,scope);
        if(rows.isEmpty()) throw new NotFoundException(); return rows.getFirst();
    }
    private VerificacaoResponse metadata(ResultSet rs,int row) throws SQLException { return response(rs,rs.getInt("ST_EVIDENCIA")==1); }
    private Boolean flag(ResultSet rs,String column) throws SQLException { int value=rs.getInt(column); return rs.wasNull()?null:value==1; }
    private VerificacaoResponse response(ResultSet rs,boolean evidence) throws SQLException {
        return new VerificacaoResponse(rs.getString("ID_VERIFICACAO"),rs.getLong("ID_USUARIO"),rs.getLong("ID_ACAO"),rs.getString("DS_ESTADO"),
                rs.getLong("NR_VERSAO"),rs.getInt("ST_REUSO")==1,evidence,rs.getString("DS_PARECER"),rs.getString("DS_MODELO"),rs.getString("DS_REGRAS"),rs.getInt("NR_TENTATIVAS"),rs.getInt("NR_PONTOS_CREDITADOS"),
                rs.getObject("NR_NOTA_COMPATIBILIDADE",Integer.class),rs.getString("DS_FEEDBACK_IA"),flag(rs,"ST_EVIDENCIA_INSUFICIENTE"),flag(rs,"ST_SUSPEITA_MANIPULACAO"));
    }
}
