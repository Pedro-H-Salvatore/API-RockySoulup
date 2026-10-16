package br.com.rockysoulup.bo;

import br.com.rockysoulup.dao.CartaDAO;
import br.com.rockysoulup.to.CartaTO;
import br.com.rockysoulup.security.AccessPolicy;
import br.com.rockysoulup.exception.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class CartaBO {
    private final CartaDAO dao;
    private final AccessPolicy access;
    public CartaBO(CartaDAO dao, AccessPolicy access) { this.dao = dao; this.access = access; }

    @Transactional(readOnly = true)
    public List<CartaTO> findAll(int page, int size) {
        access.requireAuthenticated();
        return dao.findAll(null, access.offset(page, size), size);
    }
    @Transactional(readOnly = true)
    public CartaTO findAllById(Long id) {
        access.requireAuthenticated();
        return dao.findAllById(id, null);
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CartaTO save(CartaTO value) {
        access.requireAdmin();
        var result = dao.save(value);
        access.audit("CREATE_CARTA");
        return result;
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CartaTO Update(CartaTO value) {
        access.requireAdmin();
        CartaTO result = dao.Update(value);
        access.audit("UPDATE_CARTA");
        return result;
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void Delete(Long id, long versao) {
        access.requireAdmin();
        dao.Delete(id, versao);
        access.audit("DELETE_CARTA");
    }
}

