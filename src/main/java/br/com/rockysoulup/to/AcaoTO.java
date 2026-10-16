package br.com.rockysoulup.to;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @ToString @NoArgsConstructor(onConstructor_ = @com.fasterxml.jackson.annotation.JsonCreator) @AllArgsConstructor @Builder
public class AcaoTO {
    @Positive
    private Long id;

    @NotBlank @Size(max = 100)
    private String nome;

    @NotNull @Min(1) @Max(100)
    private Integer pontos;

    @NotNull @PositiveOrZero
    @Builder.Default
    private Long versao = 0L;
}

