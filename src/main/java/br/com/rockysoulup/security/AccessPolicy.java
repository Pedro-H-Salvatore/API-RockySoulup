package br.com.rockysoulup.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import br.com.rockysoulup.exception.NotFoundException;

@Component
public class AccessPolicy {
    private static final Logger log = LoggerFactory.getLogger("security.events");
    private Authentication authentication() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || !(a instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken))
            throw new AuthenticationCredentialsNotFoundException("Autenticação necessária");
        return a;
    }
    public void requireAuthenticated() { authentication(); }
    public boolean admin() { return authentication().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")); }
    public long userId() { return Long.parseLong(authentication().getName()); }
    public Long scope() { return admin() ? null : userId(); }
    public void requireAdmin() { if (!admin()) throw new AccessDeniedException("Acesso negado"); }
    public void requireOwner(Long id) { if (!admin() && !Long.valueOf(userId()).equals(id)) throw new NotFoundException(); }
    public int offset(int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 100) throw new IllegalArgumentException();
        return Math.multiplyExact(page, size);
    }
    public void audit(String operation) { auditFor(operation,userId()); }
    public void auditFor(String operation,long actor) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { log.info("event={} actor={}",operation,actor); }
            });
        } else log.info("event={} actor={}",operation,actor);
    }
}
