package br.com.rockysoulup.bo;

import br.com.rockysoulup.dao.HistoricoDAO;
import br.com.rockysoulup.to.HistoricoTO;
import br.com.rockysoulup.security.AccessPolicy;
import br.com.rockysoulup.exception.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class HistoricoBO {
    private final HistoricoDAO dao;
    private final AccessPolicy access;
    public HistoricoBO(HistoricoDAO dao, AccessPolicy access) { this.dao = dao; this.access = access; }

    @Transactional(readOnly = true)
    public List<HistoricoTO> findAll(int page, int size) {
        access.requireAuthenticated();
        return dao.findAll(access.scope(), access.offset(page, size), size);
    }
    @Transactional(readOnly = true)
    public HistoricoTO findAllById(Long id) {
        access.requireAuthenticated();
        return dao.findAllById(id, access.scope());
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public HistoricoTO save(HistoricoTO value) {
        access.requireAdmin();
        access.requireOwner(value.getUsuarioId());
        var result = dao.save(value);
        access.audit("CREATE_HISTORICO");
        return result;
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public HistoricoTO Update(HistoricoTO value) {
        access.requireAdmin();
        access.requireOwner(value.getUsuarioId());
        if (!dao.findAllById(value.getId(), null).getUsuarioId().equals(value.getUsuarioId())) throw new ConflictException();
        HistoricoTO result = dao.Update(value);
        access.audit("UPDATE_HISTORICO");
        return result;
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void Delete(Long id, long versao) {
        access.requireAdmin();
        dao.Delete(id, versao);
        access.audit("DELETE_HISTORICO");
    }
}

