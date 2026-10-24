package br.com.rockysoulup.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@Service
public class LoginLimiter {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public LoginLimiter(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean consume(String email, String remoteAddress) {
        long window = clock.instant().getEpochSecond() / 900;
        String account = hit("email:" + email,window);
        String address = hit("ip:" + remoteAddress,window);
        // Both counters remain locked until commit; one read preserves the same limits.
        var counts=jdbc.query("SELECT DS_KEY, NR_ATTEMPTS FROM AUTH_LOGIN_ATTEMPT WHERE DS_KEY IN (?,?)",
                (rs,row)->Map.entry(rs.getString(1),rs.getInt(2)),account,address);
        return counts.size()==2 && counts.stream().allMatch(entry->entry.getValue()<=(entry.getKey().equals(account)?10:50));
    }
    @org.springframework.scheduling.annotation.Scheduled(initialDelay=900000,fixedDelay=900000)
    public void prune() {
        try { jdbc.update("DELETE FROM AUTH_LOGIN_ATTEMPT WHERE NR_WINDOW < ?",clock.instant().getEpochSecond()/900-1); }
        catch(RuntimeException e) { org.slf4j.LoggerFactory.getLogger("security.events").error("event=LOGIN_LIMIT_MAINTENANCE_UNAVAILABLE"); }
    }
    private String hit(String subject, long window) {
        String key;
        try { key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((subject + ":" + window).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        int updated = jdbc.update("UPDATE AUTH_LOGIN_ATTEMPT SET NR_ATTEMPTS = NR_ATTEMPTS + 1 WHERE DS_KEY = ?",key);
        if (updated == 0) {
            try { jdbc.update("INSERT INTO AUTH_LOGIN_ATTEMPT (DS_KEY, NR_WINDOW, NR_ATTEMPTS) VALUES (?, ?, 1)",key,window); }
            catch (DuplicateKeyException e) { jdbc.update("UPDATE AUTH_LOGIN_ATTEMPT SET NR_ATTEMPTS = NR_ATTEMPTS + 1 WHERE DS_KEY = ?",key); }
        }
        return key;
    }
}
