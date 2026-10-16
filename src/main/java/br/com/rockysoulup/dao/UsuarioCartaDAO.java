package br.com.rockysoulup.dao;

import br.com.rockysoulup.to.UsuarioCartaTO;
import br.com.rockysoulup.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.util.List;

@Repository
public class UsuarioCartaDAO {
    private final JdbcTemplate jdbc;
    public UsuarioCartaDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<UsuarioCartaTO> findAll(Long scope, int offset, int limit) {
        if (scope != null) return jdbc.query("SELECT * FROM USUARIO_CARTA WHERE ID_USUARIO = ? ORDER BY ID_USUARIO, ID_CARTA OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", this::mapear, scope, offset, limit);
        return jdbc.query("SELECT * FROM USUARIO_CARTA ORDER BY ID_USUARIO, ID_CARTA OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", this::mapear, offset, limit);
    }
    public UsuarioCartaTO findAllById(Long usuarioId, Long cartaId, Long scope) {
        if (scope != null) return one(jdbc.query("SELECT * FROM USUARIO_CARTA WHERE ID_USUARIO = ? AND ID_CARTA = ? AND ID_USUARIO = ?", this::mapear, usuarioId, cartaId, scope));
        return one(jdbc.query("SELECT * FROM USUARIO_CARTA WHERE ID_USUARIO = ? AND ID_CARTA = ?", this::mapear, usuarioId, cartaId));
    }
    public UsuarioCartaTO save(UsuarioCartaTO value) {
        jdbc.update("INSERT INTO USUARIO_CARTA (ID_USUARIO, ID_CARTA, QT_CARTA, NR_VERSAO) VALUES (?, ?, ?, 0)", value.getUsuarioId(), value.getCartaId(), value.getQuantidade());
        value.setVersao(0L);
        return value;
    }
    public UsuarioCartaTO Update(UsuarioCartaTO value) {
        int affected = jdbc.update("UPDATE USUARIO_CARTA SET QT_CARTA = ?, NR_VERSAO = NR_VERSAO + 1 WHERE ID_USUARIO = ? AND ID_CARTA = ? AND NR_VERSAO = ?", value.getQuantidade(), value.getUsuarioId(), value.getCartaId(), value.getVersao());
        if (affected == 0) { findAllById(value.getUsuarioId(), value.getCartaId(), null); throw new ConflictException(); }
        value.setVersao(value.getVersao() + 1);
        return value;
    }
    public void Delete(Long usuarioId, Long cartaId, long versao) {
        int affected = jdbc.update("DELETE FROM USUARIO_CARTA WHERE ID_USUARIO = ? AND ID_CARTA = ? AND NR_VERSAO = ?", usuarioId, cartaId, versao);
        if (affected == 0) { findAllById(usuarioId, cartaId, null); throw new ConflictException(); }
    }
    private UsuarioCartaTO one(List<UsuarioCartaTO> rows) {
        if (rows.isEmpty()) throw new NotFoundException();
        return rows.getFirst();
    }
    private UsuarioCartaTO mapear(ResultSet rs, int rowNum) throws SQLException {
        UsuarioCartaTO value = new UsuarioCartaTO();
        value.setUsuarioId(rs.getLong("ID_USUARIO"));
        value.setCartaId(rs.getLong("ID_CARTA"));
        value.setQuantidade(rs.getInt("QT_CARTA"));
        value.setVersao(rs.getLong("NR_VERSAO"));
        return value;
    }
}
