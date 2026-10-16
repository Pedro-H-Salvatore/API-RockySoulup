package br.com.rockysoulup.bo;

import br.com.rockysoulup.dao.UsuarioDAO;
import br.com.rockysoulup.security.*;
import br.com.rockysoulup.to.*;
import br.com.rockysoulup.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.slf4j.LoggerFactory;
import java.time.*;
import java.util.*;

@Service
public class AuthBO {
    private final UsuarioDAO users;
    private final PasswordEncoder passwords;
    private final LoginLimiter limiter;
    private final JwtEncoder encoder;
    private final SecurityProperties properties;
    private final AccessPolicy access;
    private final Clock clock;
    private final String dummyHash;
    public AuthBO(UsuarioDAO users, PasswordEncoder passwords, LoginLimiter limiter, JwtEncoder encoder,
            SecurityProperties properties, AccessPolicy access, Clock clock) {
        this.users=users; this.passwords=passwords; this.limiter=limiter; this.encoder=encoder;
        this.properties=properties; this.access=access; this.clock=clock;
        this.dummyHash=passwords.encode(UUID.randomUUID().toString());
    }
    @Transactional
    public UsuarioResponse cadastro(CadastroRequest request, String address) {
        limited(UsuarioDAO.normalize(request.email()),address);
        UsuarioResponse result = users.register(request,passwords.encode(request.senha()));
        access.auditFor("ACCOUNT_REGISTERED",result.id());
        return result;
    }
    public TokenResponse login(LoginRequest request, String address) {
        limited(UsuarioDAO.normalize(request.email()),address);
        Account account = users.byEmail(request.email()).orElse(null);
        String hash = account == null || account.passwordHash() == null ? dummyHash : account.passwordHash();
        boolean matches = passwords.matches(request.senha(),hash);
        if (!matches || account == null || !account.active() || account.passwordHash() == null) {
            LoggerFactory.getLogger("security.events").info("event=LOGIN_FAILED"); throw new LoginFailureException();
        }
        Instant now=clock.instant();
        JwtClaimsSet claims=JwtClaimsSet.builder().issuer(properties.issuer()).subject(Long.toString(account.id()))
                .audience(List.of(properties.audience())).issuedAt(now).notBefore(now)
                .expiresAt(now.plusSeconds(properties.tokenSeconds())).id(UUID.randomUUID().toString())
                .claim("ver",account.tokenVersion()).build();
        String token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        LoggerFactory.getLogger("security.events").info("event=LOGIN_SUCCEEDED actor={}",account.id());
        return new TokenResponse(token,"Bearer",properties.tokenSeconds());
    }
    @Transactional
    public void logout() { users.revoke(access.userId()); access.audit("LOGOUT_ALL_SESSIONS"); }
    private void limited(String email,String address) {
        if (!limiter.consume(email,address)) {
            LoggerFactory.getLogger("security.events").info("event=LOGIN_RATE_LIMITED"); throw new RateLimitException();
        }
    }
}
