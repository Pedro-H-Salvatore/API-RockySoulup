package br.com.rockysoulup.to;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @ToString @NoArgsConstructor(onConstructor_ = @com.fasterxml.jackson.annotation.JsonCreator) @AllArgsConstructor @Builder
public class SeloTO {
    @Positive
    private Long id;

    @NotBlank @Size(max = 100)
    private String nome;

    @Size(max = 200)
    private String descricao;

    @NotNull @PositiveOrZero
    private Integer pontosMin;

    @NotNull @PositiveOrZero
    @Builder.Default
    private Long versao = 0L;
}

