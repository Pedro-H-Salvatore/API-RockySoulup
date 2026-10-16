package br.com.rockysoulup.to;

import jakarta.validation.constraints.NotNull;

public record SuspensaoRequest(@NotNull Boolean suspenso) {}
