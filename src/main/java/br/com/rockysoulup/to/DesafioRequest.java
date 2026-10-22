package br.com.rockysoulup.to;
import jakarta.validation.constraints.*;
public record DesafioRequest(@NotNull @Positive Long acaoId) {}
