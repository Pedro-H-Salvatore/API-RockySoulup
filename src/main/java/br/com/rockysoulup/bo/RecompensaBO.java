package br.com.rockysoulup.bo;

import br.com.rockysoulup.dao.RecompensaDAO;
import br.com.rockysoulup.to.RecompensaTO;
import br.com.rockysoulup.security.AccessPolicy;
import br.com.rockysoulup.exception.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class RecompensaBO {
    private final RecompensaDAO dao;
    private final AccessPolicy access;
    public RecompensaBO(RecompensaDAO dao, AccessPolicy access) { this.dao = dao; this.access = access; }

    @Transactional(readOnly = true)
    public List<RecompensaTO> findAll(int page, int size) {
        access.requireAuthenticated();
        return dao.findAll(null, access.offset(page, size), size);
    }
    @Transactional(readOnly = true)
    public RecompensaTO findAllById(Long id) {
        access.requireAuthenticated();
        return dao.findAllById(id, null);
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public RecompensaTO save(RecompensaTO value) {
        access.requireAdmin();
        var result = dao.save(value);
        access.audit("CREATE_RECOMPENSA");
        return result;
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public RecompensaTO Update(RecompensaTO value) {
        access.requireAdmin();
        RecompensaTO result = dao.Update(value);
        access.audit("UPDATE_RECOMPENSA");
        return result;
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void Delete(Long id, long versao) {
        access.requireAdmin();
        dao.Delete(id, versao);
        access.audit("DELETE_RECOMPENSA");
    }
}

