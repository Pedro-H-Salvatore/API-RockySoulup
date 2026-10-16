package br.com.rockysoulup.to;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @ToString @NoArgsConstructor(onConstructor_ = @com.fasterxml.jackson.annotation.JsonCreator) @AllArgsConstructor @Builder
public class UsuarioFragmentoTO {
    @NotNull @Positive
    private Long usuarioId;

    @NotBlank @Size(max = 30) @Pattern(regexp = "COMUM|INCOMUM|RARA|EPICA|LENDARIA")
    private String raridade;

    @NotNull @PositiveOrZero
    private Integer quantidade;

    @NotNull @PositiveOrZero
    @Builder.Default
    private Long versao = 0L;
}

