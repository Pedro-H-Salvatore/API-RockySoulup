package br.com.rockysoulup.to;

import jakarta.validation.constraints.*;

public record RevisaoRequest(@NotNull Boolean aprovar, @NotBlank @Size(max=500) String justificativa,
        @NotNull @PositiveOrZero Long versao) {}
