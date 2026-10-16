package br.com.rockysoulup.bo;

import br.com.rockysoulup.dao.SeloDAO;
import br.com.rockysoulup.to.SeloTO;
import br.com.rockysoulup.security.AccessPolicy;
import br.com.rockysoulup.exception.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class SeloBO {
    private final SeloDAO dao;
    private final AccessPolicy access;
    public SeloBO(SeloDAO dao, AccessPolicy access) { this.dao = dao; this.access = access; }

    @Transactional(readOnly = true)
    public List<SeloTO> findAll(int page, int size) {
        access.requireAuthenticated();
        return dao.findAll(null, access.offset(page, size), size);
    }
    @Transactional(readOnly = true)
    public SeloTO findAllById(Long id) {
        access.requireAuthenticated();
        return dao.findAllById(id, null);
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public SeloTO save(SeloTO value) {
        access.requireAdmin();
        var result = dao.save(value);
        access.audit("CREATE_SELO");
        return result;
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public SeloTO Update(SeloTO value) {
        access.requireAdmin();
        SeloTO result = dao.Update(value);
        access.audit("UPDATE_SELO");
        return result;
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void Delete(Long id, long versao) {
        access.requireAdmin();
        dao.Delete(id, versao);
        access.audit("DELETE_SELO");
    }
}

