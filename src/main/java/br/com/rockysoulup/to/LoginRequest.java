package br.com.rockysoulup.to;

import jakarta.validation.constraints.*;
import br.com.rockysoulup.security.Utf8Password;
import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginRequest(@NotBlank @Email @Size(max=150) String email,
        @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max=72) @Utf8Password String senha) {
    @Override public String toString() { return "LoginRequest[redacted]"; }
}
