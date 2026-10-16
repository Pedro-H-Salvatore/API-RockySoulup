package br.com.rockysoulup.dao;

import br.com.rockysoulup.to.SeloTO;
import br.com.rockysoulup.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.util.List;

@Repository
public class SeloDAO {
    private final JdbcTemplate jdbc;
    public SeloDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<SeloTO> findAll(Long scope, int offset, int limit) {
        return jdbc.query("SELECT * FROM SELO ORDER BY ID_SELO OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", this::mapear, offset, limit);
    }
    public SeloTO findAllById(Long id, Long scope) {
        return one(jdbc.query("SELECT * FROM SELO WHERE ID_SELO = ?", this::mapear, id));
    }
    public SeloTO save(SeloTO value) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO SELO (NM_SELO, DS_SELO, NR_PONTOS_MIN, NR_VERSAO) VALUES (?, ?, ?, 0)", new String[] {"ID_SELO"});
            ps.setString(1, value.getNome());
            ps.setString(2, value.getDescricao());
            ps.setInt(3, value.getPontosMin());
            return ps;
        }, key);
        value.setId(key.getKey().longValue());
        value.setVersao(0L);
        return value;
    }
    public SeloTO Update(SeloTO value) {
        int affected = jdbc.update("UPDATE SELO SET NM_SELO = ?, DS_SELO = ?, NR_PONTOS_MIN = ?, NR_VERSAO = NR_VERSAO + 1 WHERE ID_SELO = ? AND NR_VERSAO = ?", value.getNome(), value.getDescricao(), value.getPontosMin(), value.getId(), value.getVersao());
        if (affected == 0) { findAllById(value.getId(), null); throw new ConflictException(); }
        value.setVersao(value.getVersao() + 1);
        return value;
    }
    public void Delete(Long id, long versao) {
        int affected = jdbc.update("DELETE FROM SELO WHERE ID_SELO = ? AND NR_VERSAO = ?", id, versao);
        if (affected == 0) { findAllById(id, null); throw new ConflictException(); }
    }
    private SeloTO one(List<SeloTO> rows) {
        if (rows.isEmpty()) throw new NotFoundException();
        return rows.getFirst();
    }
    private SeloTO mapear(ResultSet rs, int rowNum) throws SQLException {
        SeloTO value = new SeloTO();
        value.setId(rs.getLong("ID_SELO"));
        value.setNome(rs.getString("NM_SELO"));
        value.setDescricao(rs.getString("DS_SELO"));
        value.setPontosMin(rs.getInt("NR_PONTOS_MIN"));
        value.setVersao(rs.getLong("NR_VERSAO"));
        return value;
    }
}
