package br.com.rockysoulup.security;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.util.List;

@ConfigurationProperties("app.security")
@Validated
public record SecurityProperties(@NotBlank String jwtSecret, @NotBlank String issuer,
        @NotBlank String audience, @Min(60) @Max(900) long tokenSeconds,
        @NotEmpty List<@NotBlank String> allowedOrigins, boolean requireHttps) {
    @Override public String toString() { return "SecurityProperties[redacted]"; }
}
