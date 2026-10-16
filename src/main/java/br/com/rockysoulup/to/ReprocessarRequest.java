package br.com.rockysoulup.to;

import jakarta.validation.constraints.*;

public record ReprocessarRequest(@AssertTrue boolean confirmarPossivelCobranca,
        @NotNull @PositiveOrZero Long versao) {}
