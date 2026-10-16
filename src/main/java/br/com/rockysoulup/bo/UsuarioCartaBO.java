package br.com.rockysoulup.bo;

import br.com.rockysoulup.dao.UsuarioCartaDAO;
import br.com.rockysoulup.to.UsuarioCartaTO;
import br.com.rockysoulup.security.AccessPolicy;
import br.com.rockysoulup.exception.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class UsuarioCartaBO {
    private final UsuarioCartaDAO dao;
    private final AccessPolicy access;
    public UsuarioCartaBO(UsuarioCartaDAO dao, AccessPolicy access) { this.dao = dao; this.access = access; }

    @Transactional(readOnly = true)
    public List<UsuarioCartaTO> findAll(int page, int size) {
        access.requireAuthenticated();
        return dao.findAll(access.scope(), access.offset(page, size), size);
    }
    @Transactional(readOnly = true)
    public UsuarioCartaTO findAllById(Long usuarioId, Long cartaId) {
        access.requireAuthenticated();
        access.requireOwner(usuarioId);
        return dao.findAllById(usuarioId, cartaId, access.scope());
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UsuarioCartaTO save(UsuarioCartaTO value) {
        access.requireAdmin();
        access.requireOwner(value.getUsuarioId());
        var result = dao.save(value);
        access.audit("CREATE_USUARIOCARTA");
        return result;
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UsuarioCartaTO Update(UsuarioCartaTO value) {
        access.requireAdmin();
        access.requireOwner(value.getUsuarioId());
        UsuarioCartaTO result = dao.Update(value);
        access.audit("UPDATE_USUARIO_CARTA");
        return result;
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void Delete(Long usuarioId, Long cartaId, long versao) {
        access.requireAdmin();
        dao.Delete(usuarioId, cartaId, versao);
        access.audit("DELETE_USUARIO_CARTA");
    }
}

