package br.com.rockysoulup.resource;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import java.util.Map;

@RestController
public class HealthResource {
    private final JdbcTemplate jdbc;
    public HealthResource(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @GetMapping("/health")
    public ResponseEntity<Map<String,String>> health() {
        try {
            boolean valid=Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection -> connection.isValid(2)));
            return ResponseEntity.status(valid?200:503).body(Map.of("status",valid?"UP":"DOWN"));
        } catch(RuntimeException e) {
            return ResponseEntity.status(503).body(Map.of("status","DOWN"));
        }
    }
}
