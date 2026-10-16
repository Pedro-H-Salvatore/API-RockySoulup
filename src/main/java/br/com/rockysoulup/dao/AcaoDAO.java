package br.com.rockysoulup.dao;

import br.com.rockysoulup.to.AcaoTO;
import br.com.rockysoulup.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.util.List;

@Repository
public class AcaoDAO {
    private final JdbcTemplate jdbc;
    public AcaoDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<AcaoTO> findAll(Long scope, int offset, int limit) {
        return jdbc.query("SELECT * FROM ACAO ORDER BY ID_ACAO OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", this::mapear, offset, limit);
    }
    public AcaoTO findAllById(Long id, Long scope) {
        return one(jdbc.query("SELECT * FROM ACAO WHERE ID_ACAO = ?", this::mapear, id));
    }
    public AcaoTO save(AcaoTO value) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO ACAO (NM_ACAO, NR_PONTOS, NR_VERSAO) VALUES (?, ?, 0)", new String[] {"ID_ACAO"});
            ps.setString(1, value.getNome());
            ps.setInt(2, value.getPontos());
            return ps;
        }, key);
        value.setId(key.getKey().longValue());
        value.setVersao(0L);
        return value;
    }
    public AcaoTO Update(AcaoTO value) {
        int affected = jdbc.update("UPDATE ACAO SET NM_ACAO = ?, NR_PONTOS = ?, NR_VERSAO = NR_VERSAO + 1 WHERE ID_ACAO = ? AND NR_VERSAO = ?", value.getNome(), value.getPontos(), value.getId(), value.getVersao());
        if (affected == 0) { findAllById(value.getId(), null); throw new ConflictException(); }
        value.setVersao(value.getVersao() + 1);
        return value;
    }
    public void Delete(Long id, long versao) {
        int affected = jdbc.update("DELETE FROM ACAO WHERE ID_ACAO = ? AND NR_VERSAO = ?", id, versao);
        if (affected == 0) { findAllById(id, null); throw new ConflictException(); }
    }
    private AcaoTO one(List<AcaoTO> rows) {
        if (rows.isEmpty()) throw new NotFoundException();
        return rows.getFirst();
    }
    private AcaoTO mapear(ResultSet rs, int rowNum) throws SQLException {
        AcaoTO value = new AcaoTO();
        value.setId(rs.getLong("ID_ACAO"));
        value.setNome(rs.getString("NM_ACAO"));
        value.setPontos(rs.getInt("NR_PONTOS"));
        value.setVersao(rs.getLong("NR_VERSAO"));
        return value;
    }
}
