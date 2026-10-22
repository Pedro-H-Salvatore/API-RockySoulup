package br.com.rockysoulup.to;

import jakarta.validation.constraints.*;

public record VerificacaoRequest(@NotNull @Positive Long acaoId,
        @NotBlank @Size(max=1000) String descricao,
        @NotBlank @Size(max=700000) String imagemBase64,
        @NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String desafioId) {
    @Override public String toString() { return "VerificacaoRequest[redacted]"; }
}
