package br.com.rockysoulup.to;

import jakarta.validation.constraints.*;

public record SaldoRequest(@NotNull @PositiveOrZero Integer pontos,
        @NotNull @PositiveOrZero Integer resgatados, @NotNull @PositiveOrZero Long versao) {}
