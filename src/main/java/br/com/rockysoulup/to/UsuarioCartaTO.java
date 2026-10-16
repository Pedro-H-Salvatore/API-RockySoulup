package br.com.rockysoulup.to;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @ToString @NoArgsConstructor(onConstructor_ = @com.fasterxml.jackson.annotation.JsonCreator) @AllArgsConstructor @Builder
public class UsuarioCartaTO {
    @NotNull @Positive
    private Long usuarioId;

    @NotNull @Positive
    private Long cartaId;

    @NotNull @Positive
    private Integer quantidade;

    @NotNull @PositiveOrZero
    @Builder.Default
    private Long versao = 0L;
}

