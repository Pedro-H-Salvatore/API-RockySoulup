package br.com.rockysoulup.bo;

import br.com.rockysoulup.dao.UsuarioDAO;
import br.com.rockysoulup.to.CadastroRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;

@Service
@Profile("provision")
public class ProvisionBO {
    private final JdbcTemplate jdbc;
    private final UsuarioDAO users;
    private final PasswordEncoder encoder;
    public ProvisionBO(JdbcTemplate jdbc,UsuarioDAO users,PasswordEncoder encoder) { this.jdbc=jdbc; this.users=users; this.encoder=encoder; }
    @Transactional
    public void provision(String mode,CadastroRequest request) {
        jdbc.queryForObject("SELECT ID_LOCK FROM AUTH_BOOTSTRAP_LOCK WHERE ID_LOCK = 1 FOR UPDATE",Long.class);
        if (mode.equals("bootstrap")) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM USUARIO WHERE DS_PERFIL = 'ADMIN'",Long.class) != 0)
                throw new IllegalStateException("Um administrador já existe.");
            long id=users.register(request,encoder.encode(request.senha())).id();
            jdbc.update("UPDATE USUARIO SET DS_PERFIL = 'ADMIN' WHERE ID_USUARIO = ?",id);
        } else if (mode.equals("activate")) {
            int count=jdbc.update("UPDATE USUARIO SET DS_SENHA_HASH = ?, ST_ATIVO = 1, NR_TOKEN_VERSION = NR_TOKEN_VERSION + 1, NR_VERSAO = NR_VERSAO + 1 WHERE DS_EMAIL = ? AND DS_SENHA_HASH IS NULL AND ST_ATIVO = 0 AND DS_PERFIL = 'USER'",
                    encoder.encode(request.senha()),UsuarioDAO.normalize(request.email()));
            if (count != 1) throw new IllegalStateException("Conta não elegível para ativação.");
        } else throw new IllegalArgumentException("Modo de provisionamento inválido.");
    }
}
