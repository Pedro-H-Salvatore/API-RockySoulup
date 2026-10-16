package br.com.rockysoulup.dao;

import br.com.rockysoulup.to.RecompensaTO;
import br.com.rockysoulup.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.util.List;

@Repository
public class RecompensaDAO {
    private final JdbcTemplate jdbc;
    public RecompensaDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<RecompensaTO> findAll(Long scope, int offset, int limit) {
        return jdbc.query("SELECT * FROM RECOMPENSA ORDER BY ID_RECOMPENSA OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", this::mapear, offset, limit);
    }
    public RecompensaTO findAllById(Long id, Long scope) {
        return one(jdbc.query("SELECT * FROM RECOMPENSA WHERE ID_RECOMPENSA = ?", this::mapear, id));
    }
    public RecompensaTO save(RecompensaTO value) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO RECOMPENSA (NM_RECOMPENSA, DS_RECOMPENSA, NR_CUSTO, NR_ESTOQUE, DS_CATEGORIA, ST_DESTAQUE, NR_VERSAO) VALUES (?, ?, ?, ?, ?, ?, 0)", new String[] {"ID_RECOMPENSA"});
            ps.setString(1, value.getTitulo());
            ps.setString(2, value.getDescricao());
            ps.setInt(3, value.getCusto());
            ps.setInt(4, value.getEstoque());
            ps.setString(5, value.getCategoria());
            ps.setString(6, value.getDestaque());
            return ps;
        }, key);
        value.setId(key.getKey().longValue());
        value.setVersao(0L);
        return value;
    }
    public RecompensaTO Update(RecompensaTO value) {
        int affected = jdbc.update("UPDATE RECOMPENSA SET NM_RECOMPENSA = ?, DS_RECOMPENSA = ?, NR_CUSTO = ?, NR_ESTOQUE = ?, DS_CATEGORIA = ?, ST_DESTAQUE = ?, NR_VERSAO = NR_VERSAO + 1 WHERE ID_RECOMPENSA = ? AND NR_VERSAO = ?", value.getTitulo(), value.getDescricao(), value.getCusto(), value.getEstoque(), value.getCategoria(), value.getDestaque(), value.getId(), value.getVersao());
        if (affected == 0) { findAllById(value.getId(), null); throw new ConflictException(); }
        value.setVersao(value.getVersao() + 1);
        return value;
    }
    public void Delete(Long id, long versao) {
        int affected = jdbc.update("DELETE FROM RECOMPENSA WHERE ID_RECOMPENSA = ? AND NR_VERSAO = ?", id, versao);
        if (affected == 0) { findAllById(id, null); throw new ConflictException(); }
    }
    private RecompensaTO one(List<RecompensaTO> rows) {
        if (rows.isEmpty()) throw new NotFoundException();
        return rows.getFirst();
    }
    private RecompensaTO mapear(ResultSet rs, int rowNum) throws SQLException {
        RecompensaTO value = new RecompensaTO();
        value.setId(rs.getLong("ID_RECOMPENSA"));
        value.setTitulo(rs.getString("NM_RECOMPENSA"));
        value.setDescricao(rs.getString("DS_RECOMPENSA"));
        value.setCusto(rs.getInt("NR_CUSTO"));
        value.setEstoque(rs.getInt("NR_ESTOQUE"));
        value.setCategoria(rs.getString("DS_CATEGORIA"));
        value.setDestaque(rs.getString("ST_DESTAQUE"));
        value.setVersao(rs.getLong("NR_VERSAO"));
        return value;
    }
}
