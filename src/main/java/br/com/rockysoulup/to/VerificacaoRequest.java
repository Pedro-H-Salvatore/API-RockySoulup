package br.com.rockysoulup.to;

import jakarta.validation.constraints.*;

public record VerificacaoRequest(@NotNull @Positive Long acaoId,
        @NotBlank @Size(max=1000) String descricao,
        @NotBlank @Size(max=700000) String imagemBase64) {
    @Override public String toString() { return "VerificacaoRequest[redacted]"; }
}
