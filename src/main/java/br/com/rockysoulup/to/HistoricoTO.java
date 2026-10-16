package br.com.rockysoulup.to;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter @Setter @ToString @NoArgsConstructor(onConstructor_ = @com.fasterxml.jackson.annotation.JsonCreator) @AllArgsConstructor @Builder
public class HistoricoTO {
    @Positive
    private Long id;

    @NotNull @Positive
    private Long usuarioId;

    @NotBlank @Size(max = 150)
    private String descricao;

    @NotNull @Min(1) @Max(100)
    private Integer pontos;

    @NotNull @PastOrPresent
    private LocalDateTime dataAcao;

    @NotNull @PositiveOrZero
    @Builder.Default
    private Long versao = 0L;
}

