package br.com.rockysoulup.dao;

import br.com.rockysoulup.to.CartaTO;
import br.com.rockysoulup.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.util.List;

@Repository
public class CartaDAO {
    private final JdbcTemplate jdbc;
    public CartaDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<CartaTO> findAll(Long scope, int offset, int limit) {
        return jdbc.query("SELECT * FROM CARTA ORDER BY ID_CARTA OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", this::mapear, offset, limit);
    }
    public CartaTO findAllById(Long id, Long scope) {
        return one(jdbc.query("SELECT * FROM CARTA WHERE ID_CARTA = ?", this::mapear, id));
    }
    public CartaTO save(CartaTO value) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO CARTA (NM_CARTA, DS_CARTA, NM_CONJUNTO, DS_RARIDADE, NR_VERSAO) VALUES (?, ?, ?, ?, 0)", new String[] {"ID_CARTA"});
            ps.setString(1, value.getNome());
            ps.setString(2, value.getDescricao());
            ps.setString(3, value.getConjunto());
            ps.setString(4, value.getRaridade());
            return ps;
        }, key);
        value.setId(key.getKey().longValue());
        value.setVersao(0L);
        return value;
    }
    public CartaTO Update(CartaTO value) {
        int affected = jdbc.update("UPDATE CARTA SET NM_CARTA = ?, DS_CARTA = ?, NM_CONJUNTO = ?, DS_RARIDADE = ?, NR_VERSAO = NR_VERSAO + 1 WHERE ID_CARTA = ? AND NR_VERSAO = ?", value.getNome(), value.getDescricao(), value.getConjunto(), value.getRaridade(), value.getId(), value.getVersao());
        if (affected == 0) { findAllById(value.getId(), null); throw new ConflictException(); }
        value.setVersao(value.getVersao() + 1);
        return value;
    }
    public void Delete(Long id, long versao) {
        int affected = jdbc.update("DELETE FROM CARTA WHERE ID_CARTA = ? AND NR_VERSAO = ?", id, versao);
        if (affected == 0) { findAllById(id, null); throw new ConflictException(); }
    }
    private CartaTO one(List<CartaTO> rows) {
        if (rows.isEmpty()) throw new NotFoundException();
        return rows.getFirst();
    }
    private CartaTO mapear(ResultSet rs, int rowNum) throws SQLException {
        CartaTO value = new CartaTO();
        value.setId(rs.getLong("ID_CARTA"));
        value.setNome(rs.getString("NM_CARTA"));
        value.setDescricao(rs.getString("DS_CARTA"));
        value.setConjunto(rs.getString("NM_CONJUNTO"));
        value.setRaridade(rs.getString("DS_RARIDADE"));
        value.setVersao(rs.getLong("NR_VERSAO"));
        return value;
    }
}
