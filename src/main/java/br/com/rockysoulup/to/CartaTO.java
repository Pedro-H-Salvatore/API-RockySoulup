package br.com.rockysoulup.to;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @ToString @NoArgsConstructor(onConstructor_ = @com.fasterxml.jackson.annotation.JsonCreator) @AllArgsConstructor @Builder
public class CartaTO {
    @Positive
    private Long id;

    @NotBlank @Size(max = 100)
    private String nome;

    @Size(max = 200)
    private String descricao;

    @NotBlank @Size(max = 60)
    private String conjunto;

    @NotBlank @Size(max = 30) @Pattern(regexp = "COMUM|INCOMUM|RARA|EPICA|LENDARIA")
    private String raridade;

    @NotNull @PositiveOrZero
    @Builder.Default
    private Long versao = 0L;
}

