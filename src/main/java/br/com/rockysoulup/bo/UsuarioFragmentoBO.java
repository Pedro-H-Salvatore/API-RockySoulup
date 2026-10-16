package br.com.rockysoulup.bo;

import br.com.rockysoulup.dao.UsuarioFragmentoDAO;
import br.com.rockysoulup.to.UsuarioFragmentoTO;
import br.com.rockysoulup.security.AccessPolicy;
import br.com.rockysoulup.exception.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class UsuarioFragmentoBO {
    private final UsuarioFragmentoDAO dao;
    private final AccessPolicy access;
    public UsuarioFragmentoBO(UsuarioFragmentoDAO dao, AccessPolicy access) { this.dao = dao; this.access = access; }

    @Transactional(readOnly = true)
    public List<UsuarioFragmentoTO> findAll(int page, int size) {
        access.requireAuthenticated();
        return dao.findAll(access.scope(), access.offset(page, size), size);
    }
    @Transactional(readOnly = true)
    public UsuarioFragmentoTO findAllById(Long usuarioId, String raridade) {
        access.requireAuthenticated();
        access.requireOwner(usuarioId);
        return dao.findAllById(usuarioId, raridade, access.scope());
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UsuarioFragmentoTO save(UsuarioFragmentoTO value) {
        access.requireAdmin();
        access.requireOwner(value.getUsuarioId());
        var result = dao.save(value);
        access.audit("CREATE_USUARIOFRAGMENTO");
        return result;
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UsuarioFragmentoTO Update(UsuarioFragmentoTO value) {
        access.requireAdmin();
        access.requireOwner(value.getUsuarioId());
        UsuarioFragmentoTO result = dao.Update(value);
        access.audit("UPDATE_USUARIO_FRAGMENTO");
        return result;
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void Delete(Long usuarioId, String raridade, long versao) {
        access.requireAdmin();
        dao.Delete(usuarioId, raridade, versao);
        access.audit("DELETE_USUARIO_FRAGMENTO");
    }
}

