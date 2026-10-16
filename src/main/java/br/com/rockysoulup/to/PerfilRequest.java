package br.com.rockysoulup.to;

import jakarta.validation.constraints.*;

public record PerfilRequest(@NotBlank @Size(max=100) String nome,
        @NotBlank @Email @Size(max=150) String email, @NotNull @PositiveOrZero Long versao) {}
