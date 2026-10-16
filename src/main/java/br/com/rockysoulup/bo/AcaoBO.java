package br.com.rockysoulup.bo;

import br.com.rockysoulup.dao.AcaoDAO;
import br.com.rockysoulup.to.AcaoTO;
import br.com.rockysoulup.security.AccessPolicy;
import br.com.rockysoulup.exception.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class AcaoBO {
    private final AcaoDAO dao;
    private final AccessPolicy access;
    public AcaoBO(AcaoDAO dao, AccessPolicy access) { this.dao = dao; this.access = access; }

    @Transactional(readOnly = true)
    public List<AcaoTO> findAll(int page, int size) {
        access.requireAuthenticated();
        return dao.findAll(null, access.offset(page, size), size);
    }
    @Transactional(readOnly = true)
    public AcaoTO findAllById(Long id) {
        access.requireAuthenticated();
        return dao.findAllById(id, null);
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AcaoTO save(AcaoTO value) {
        access.requireAdmin();
        var result = dao.save(value);
        access.audit("CREATE_ACAO");
        return result;
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AcaoTO Update(AcaoTO value) {
        access.requireAdmin();
        AcaoTO result = dao.Update(value);
        access.audit("UPDATE_ACAO");
        return result;
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void Delete(Long id, long versao) {
        access.requireAdmin();
        dao.Delete(id, versao);
        access.audit("DELETE_ACAO");
    }
}

