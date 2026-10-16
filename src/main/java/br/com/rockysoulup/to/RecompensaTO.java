package br.com.rockysoulup.to;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @ToString @NoArgsConstructor(onConstructor_ = @com.fasterxml.jackson.annotation.JsonCreator) @AllArgsConstructor @Builder
public class RecompensaTO {
    @Positive
    private Long id;

    @NotBlank @Size(max = 100)
    private String titulo;

    @Size(max = 200)
    private String descricao;

    @NotNull @Positive
    private Integer custo;

    @NotNull @PositiveOrZero
    private Integer estoque;

    @Size(max = 30)
    private String categoria;

    @Size(max = 20)
    private String destaque;

    @NotNull @PositiveOrZero
    @Builder.Default
    private Long versao = 0L;
}

