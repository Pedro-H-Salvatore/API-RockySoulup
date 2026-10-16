package br.com.rockysoulup.dao;

import br.com.rockysoulup.to.HistoricoTO;
import br.com.rockysoulup.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.util.List;

@Repository
public class HistoricoDAO {
    private final JdbcTemplate jdbc;
    public HistoricoDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<HistoricoTO> findAll(Long scope, int offset, int limit) {
        if (scope != null) return jdbc.query("SELECT * FROM HISTORICO WHERE ID_USUARIO = ? ORDER BY ID_HISTORICO OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", this::mapear, scope, offset, limit);
        return jdbc.query("SELECT * FROM HISTORICO ORDER BY ID_HISTORICO OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", this::mapear, offset, limit);
    }
    public HistoricoTO findAllById(Long id, Long scope) {
        if (scope != null) return one(jdbc.query("SELECT * FROM HISTORICO WHERE ID_HISTORICO = ? AND ID_USUARIO = ?", this::mapear, id, scope));
        return one(jdbc.query("SELECT * FROM HISTORICO WHERE ID_HISTORICO = ?", this::mapear, id));
    }
    public HistoricoTO save(HistoricoTO value) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO HISTORICO (ID_USUARIO, DS_ACAO, NR_PONTOS, DT_ACAO, NR_VERSAO) VALUES (?, ?, ?, ?, 0)", new String[] {"ID_HISTORICO"});
            ps.setLong(1, value.getUsuarioId());
            ps.setString(2, value.getDescricao());
            ps.setInt(3, value.getPontos());
            ps.setTimestamp(4, Timestamp.valueOf(value.getDataAcao()));
            return ps;
        }, key);
        value.setId(key.getKey().longValue());
        value.setVersao(0L);
        return value;
    }
    public HistoricoTO Update(HistoricoTO value) {
        int affected = jdbc.update("UPDATE HISTORICO SET DS_ACAO = ?, NR_PONTOS = ?, DT_ACAO = ?, NR_VERSAO = NR_VERSAO + 1 WHERE ID_HISTORICO = ? AND NR_VERSAO = ?", value.getDescricao(), value.getPontos(), Timestamp.valueOf(value.getDataAcao()), value.getId(), value.getVersao());
        if (affected == 0) { findAllById(value.getId(), null); throw new ConflictException(); }
        value.setVersao(value.getVersao() + 1);
        return value;
    }
    public void Delete(Long id, long versao) {
        int affected = jdbc.update("DELETE FROM HISTORICO WHERE ID_HISTORICO = ? AND NR_VERSAO = ?", id, versao);
        if (affected == 0) { findAllById(id, null); throw new ConflictException(); }
    }
    private HistoricoTO one(List<HistoricoTO> rows) {
        if (rows.isEmpty()) throw new NotFoundException();
        return rows.getFirst();
    }
    private HistoricoTO mapear(ResultSet rs, int rowNum) throws SQLException {
        HistoricoTO value = new HistoricoTO();
        value.setId(rs.getLong("ID_HISTORICO"));
        value.setUsuarioId(rs.getLong("ID_USUARIO"));
        value.setDescricao(rs.getString("DS_ACAO"));
        value.setPontos(rs.getInt("NR_PONTOS"));
        value.setDataAcao(rs.getTimestamp("DT_ACAO").toLocalDateTime());
        value.setVersao(rs.getLong("NR_VERSAO"));
        return value;
    }
}
