package br.com.rockysoulup.bo;

import br.com.rockysoulup.dao.UsuarioDAO;
import br.com.rockysoulup.to.*;
import br.com.rockysoulup.security.AccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class UsuarioBO {
    private final UsuarioDAO dao;
    private final AccessPolicy access;
    public UsuarioBO(UsuarioDAO dao, AccessPolicy access) { this.dao = dao; this.access = access; }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly=true)
    public List<UsuarioResponse> findAll(int page, int size) {
        access.requireAdmin(); return dao.findAll(access.offset(page,size), size);
    }
    @Transactional(readOnly=true)
    public UsuarioResponse findAllById(long id) { access.requireOwner(id); return dao.findAllById(id); }
    @Transactional
    public UsuarioResponse Update(long id, PerfilRequest request) {
        access.requireOwner(id);
        UsuarioResponse response = dao.Update(id, request);
        access.audit("PROFILE_UPDATE"); return response;
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UsuarioResponse saldo(long id, SaldoRequest request) {
        access.requireAdmin();
        UsuarioResponse response = dao.saldo(id, request);
        access.audit("ADMIN_BALANCE_UPDATE"); return response;
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void Delete(long id, long versao) { access.requireAdmin(); dao.Delete(id,versao); access.audit("ACCOUNT_DISABLED"); }
}
