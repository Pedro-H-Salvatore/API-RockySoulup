package br.com.rockysoulup.resource;

import br.com.rockysoulup.bo.UsuarioFragmentoBO;
import br.com.rockysoulup.to.UsuarioFragmentoTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/usuario-fragmento")
public class UsuarioFragmentoResource {
    private final UsuarioFragmentoBO bo;
    public UsuarioFragmentoResource(UsuarioFragmentoBO bo) { this.bo = bo; }
    @GetMapping
    public List<UsuarioFragmentoTO> findAll(@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return bo.findAll(page, size); }
    @GetMapping("/{usuarioId}/{raridade}")
    public UsuarioFragmentoTO findAllById(@PathVariable("usuarioId") @Positive Long usuarioId, @PathVariable("raridade") @Pattern(regexp = "COMUM|INCOMUM|RARA|EPICA|LENDARIA") String raridade) { return bo.findAllById(usuarioId, raridade); }
    @PostMapping
    public ResponseEntity<UsuarioFragmentoTO> save(@Valid @RequestBody UsuarioFragmentoTO value) { return ResponseEntity.status(201).body(bo.save(value)); }
    @PutMapping("/{usuarioId}/{raridade}")
    public UsuarioFragmentoTO Update(@PathVariable("usuarioId") @Positive Long usuarioId, @PathVariable("raridade") @Pattern(regexp = "COMUM|INCOMUM|RARA|EPICA|LENDARIA") String raridade, @Valid @RequestBody UsuarioFragmentoTO value) {
        if (!value.getUsuarioId().equals(usuarioId)) throw new br.com.rockysoulup.exception.ConflictException();
        if (!value.getRaridade().equals(raridade)) throw new br.com.rockysoulup.exception.ConflictException();
        value.setUsuarioId(usuarioId);
        value.setRaridade(raridade);
        return bo.Update(value);
    }
    @DeleteMapping("/{usuarioId}/{raridade}")
    public ResponseEntity<Void> Delete(@PathVariable("usuarioId") @Positive Long usuarioId, @PathVariable("raridade") @Pattern(regexp = "COMUM|INCOMUM|RARA|EPICA|LENDARIA") String raridade, @RequestParam @PositiveOrZero long versao) {
        bo.Delete(usuarioId, raridade, versao);
        return ResponseEntity.noContent().build();
    }
}
