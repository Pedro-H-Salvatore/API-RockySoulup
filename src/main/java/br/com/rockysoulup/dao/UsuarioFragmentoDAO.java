package br.com.rockysoulup.dao;

import br.com.rockysoulup.to.UsuarioFragmentoTO;
import br.com.rockysoulup.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.util.List;

@Repository
public class UsuarioFragmentoDAO {
    private final JdbcTemplate jdbc;
    public UsuarioFragmentoDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<UsuarioFragmentoTO> findAll(Long scope, int offset, int limit) {
        if (scope != null) return jdbc.query("SELECT * FROM USUARIO_FRAGMENTO WHERE ID_USUARIO = ? ORDER BY ID_USUARIO, DS_RARIDADE OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", this::mapear, scope, offset, limit);
        return jdbc.query("SELECT * FROM USUARIO_FRAGMENTO ORDER BY ID_USUARIO, DS_RARIDADE OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", this::mapear, offset, limit);
    }
    public UsuarioFragmentoTO findAllById(Long usuarioId, String raridade, Long scope) {
        if (scope != null) return one(jdbc.query("SELECT * FROM USUARIO_FRAGMENTO WHERE ID_USUARIO = ? AND DS_RARIDADE = ? AND ID_USUARIO = ?", this::mapear, usuarioId, raridade, scope));
        return one(jdbc.query("SELECT * FROM USUARIO_FRAGMENTO WHERE ID_USUARIO = ? AND DS_RARIDADE = ?", this::mapear, usuarioId, raridade));
    }
    public UsuarioFragmentoTO save(UsuarioFragmentoTO value) {
        jdbc.update("INSERT INTO USUARIO_FRAGMENTO (ID_USUARIO, DS_RARIDADE, QT_FRAGMENTOS, NR_VERSAO) VALUES (?, ?, ?, 0)", value.getUsuarioId(), value.getRaridade(), value.getQuantidade());
        value.setVersao(0L);
        return value;
    }
    public UsuarioFragmentoTO Update(UsuarioFragmentoTO value) {
        int affected = jdbc.update("UPDATE USUARIO_FRAGMENTO SET QT_FRAGMENTOS = ?, NR_VERSAO = NR_VERSAO + 1 WHERE ID_USUARIO = ? AND DS_RARIDADE = ? AND NR_VERSAO = ?", value.getQuantidade(), value.getUsuarioId(), value.getRaridade(), value.getVersao());
        if (affected == 0) { findAllById(value.getUsuarioId(), value.getRaridade(), null); throw new ConflictException(); }
        value.setVersao(value.getVersao() + 1);
        return value;
    }
    public void Delete(Long usuarioId, String raridade, long versao) {
        int affected = jdbc.update("DELETE FROM USUARIO_FRAGMENTO WHERE ID_USUARIO = ? AND DS_RARIDADE = ? AND NR_VERSAO = ?", usuarioId, raridade, versao);
        if (affected == 0) { findAllById(usuarioId, raridade, null); throw new ConflictException(); }
    }
    private UsuarioFragmentoTO one(List<UsuarioFragmentoTO> rows) {
        if (rows.isEmpty()) throw new NotFoundException();
        return rows.getFirst();
    }
    private UsuarioFragmentoTO mapear(ResultSet rs, int rowNum) throws SQLException {
        UsuarioFragmentoTO value = new UsuarioFragmentoTO();
        value.setUsuarioId(rs.getLong("ID_USUARIO"));
        value.setRaridade(rs.getString("DS_RARIDADE"));
        value.setQuantidade(rs.getInt("QT_FRAGMENTOS"));
        value.setVersao(rs.getLong("NR_VERSAO"));
        return value;
    }
}
