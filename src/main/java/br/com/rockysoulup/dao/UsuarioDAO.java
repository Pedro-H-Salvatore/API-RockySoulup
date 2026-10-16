package br.com.rockysoulup.dao;

import br.com.rockysoulup.to.*;
import br.com.rockysoulup.security.Account;
import br.com.rockysoulup.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.util.*;

@Repository
public class UsuarioDAO {
    private final JdbcTemplate jdbc;
    public UsuarioDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public static String normalize(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    private Account account(ResultSet rs, int row) throws SQLException {
        return new Account(rs.getLong("ID_USUARIO"), rs.getString("DS_SENHA_HASH"), rs.getString("DS_PERFIL"),
                rs.getInt("ST_ATIVO") == 1, rs.getLong("NR_TOKEN_VERSION"));
    }
    public Optional<Account> byEmail(String email) {
        return jdbc.query("SELECT ID_USUARIO, DS_SENHA_HASH, DS_PERFIL, ST_ATIVO, NR_TOKEN_VERSION FROM USUARIO WHERE DS_EMAIL = ?", this::account, normalize(email)).stream().findFirst();
    }
    public Optional<Account> identity(long id) {
        return jdbc.query("SELECT ID_USUARIO, DS_SENHA_HASH, DS_PERFIL, ST_ATIVO, NR_TOKEN_VERSION FROM USUARIO WHERE ID_USUARIO = ?", this::account, id).stream().findFirst();
    }
    public UsuarioResponse register(CadastroRequest request, String hash) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO USUARIO (NM_USUARIO, DS_EMAIL, NR_PONTOS, NR_PONTOS_RESGATADOS, DS_SENHA_HASH, DS_PERFIL, ST_ATIVO, NR_TOKEN_VERSION, NR_VERSAO) VALUES (?, ?, 0, 0, ?, 'USER', 1, 0, 0)", new String[]{"ID_USUARIO"});
            ps.setString(1, request.nome().strip()); ps.setString(2, normalize(request.email())); ps.setString(3, hash);
            return ps;
        }, key);
        return findAllById(key.getKey().longValue());
    }
    public UsuarioResponse findAllById(long id) {
        var values = jdbc.query("SELECT ID_USUARIO, NM_USUARIO, DS_EMAIL, NR_PONTOS, NR_PONTOS_RESGATADOS, NR_VERSAO FROM USUARIO WHERE ID_USUARIO = ?",
                (rs, row) -> new UsuarioResponse(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5), rs.getLong(6)), id);
        if (values.isEmpty()) throw new NotFoundException();
        return values.getFirst();
    }
    public List<UsuarioResponse> findAll(int offset, int limit) {
        return jdbc.query("SELECT ID_USUARIO, NM_USUARIO, DS_EMAIL, NR_PONTOS, NR_PONTOS_RESGATADOS, NR_VERSAO FROM USUARIO ORDER BY ID_USUARIO OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                (rs,row) -> new UsuarioResponse(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getInt(4),rs.getInt(5),rs.getLong(6)), offset, limit);
    }
    public UsuarioResponse Update(long id, PerfilRequest request) {
        changed(id, jdbc.update("UPDATE USUARIO SET NM_USUARIO = ?, DS_EMAIL = ?, NR_VERSAO = NR_VERSAO + 1, NR_TOKEN_VERSION = NR_TOKEN_VERSION + 1 WHERE ID_USUARIO = ? AND NR_VERSAO = ?",
                request.nome().strip(), normalize(request.email()), id, request.versao()));
        return findAllById(id);
    }
    public UsuarioResponse saldo(long id, SaldoRequest request) {
        changed(id, jdbc.update("UPDATE USUARIO SET NR_PONTOS = ?, NR_PONTOS_RESGATADOS = ?, NR_VERSAO = NR_VERSAO + 1 WHERE ID_USUARIO = ? AND NR_VERSAO = ?",
                request.pontos(), request.resgatados(), id, request.versao()));
        return findAllById(id);
    }
    public void Delete(long id, long versao) {
        changed(id, jdbc.update("UPDATE USUARIO SET ST_ATIVO = 0, NR_TOKEN_VERSION = NR_TOKEN_VERSION + 1, NR_VERSAO = NR_VERSAO + 1 WHERE ID_USUARIO = ? AND NR_VERSAO = ?", id, versao));
    }
    public void revoke(long id) {
        if (jdbc.update("UPDATE USUARIO SET NR_TOKEN_VERSION = NR_TOKEN_VERSION + 1 WHERE ID_USUARIO = ?", id) == 0) throw new NotFoundException();
    }
    private void changed(long id, int affected) { if (affected == 0) { findAllById(id); throw new ConflictException(); } }
}
