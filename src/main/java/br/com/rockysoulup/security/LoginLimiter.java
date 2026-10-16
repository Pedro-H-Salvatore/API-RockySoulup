package br.com.rockysoulup.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class LoginLimiter {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public LoginLimiter(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean consume(String email, String remoteAddress) {
        long window = clock.instant().getEpochSecond() / 900;
        jdbc.update("DELETE FROM AUTH_LOGIN_ATTEMPT WHERE NR_WINDOW < ?", window - 1);
        int account = hit("email:" + email,window);
        int address = hit("ip:" + remoteAddress,window);
        return account <= 10 && address <= 50;
    }
    private int hit(String subject, long window) {
        String key;
        try { key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((subject + ":" + window).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        int updated = jdbc.update("UPDATE AUTH_LOGIN_ATTEMPT SET NR_ATTEMPTS = NR_ATTEMPTS + 1 WHERE DS_KEY = ?",key);
        if (updated == 0) {
            try { jdbc.update("INSERT INTO AUTH_LOGIN_ATTEMPT (DS_KEY, NR_WINDOW, NR_ATTEMPTS) VALUES (?, ?, 1)",key,window); }
            catch (DuplicateKeyException e) { jdbc.update("UPDATE AUTH_LOGIN_ATTEMPT SET NR_ATTEMPTS = NR_ATTEMPTS + 1 WHERE DS_KEY = ?",key); }
        }
        return jdbc.queryForObject("SELECT NR_ATTEMPTS FROM AUTH_LOGIN_ATTEMPT WHERE DS_KEY = ?",Integer.class,key);
    }
}
