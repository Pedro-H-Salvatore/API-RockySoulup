package br.com.rockysoulup.resource;

import br.com.rockysoulup.bo.UsuarioCartaBO;
import br.com.rockysoulup.to.UsuarioCartaTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/usuario-carta")
public class UsuarioCartaResource {
    private final UsuarioCartaBO bo;
    public UsuarioCartaResource(UsuarioCartaBO bo) { this.bo = bo; }
    @GetMapping
    public List<UsuarioCartaTO> findAll(@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return bo.findAll(page, size); }
    @GetMapping("/{usuarioId}/{cartaId}")
    public UsuarioCartaTO findAllById(@PathVariable("usuarioId") @Positive Long usuarioId, @PathVariable("cartaId") @Positive Long cartaId) { return bo.findAllById(usuarioId, cartaId); }
    @PostMapping
    public ResponseEntity<UsuarioCartaTO> save(@Valid @RequestBody UsuarioCartaTO value) { return ResponseEntity.status(201).body(bo.save(value)); }
    @PutMapping("/{usuarioId}/{cartaId}")
    public UsuarioCartaTO Update(@PathVariable("usuarioId") @Positive Long usuarioId, @PathVariable("cartaId") @Positive Long cartaId, @Valid @RequestBody UsuarioCartaTO value) {
        if (!value.getUsuarioId().equals(usuarioId)) throw new br.com.rockysoulup.exception.ConflictException();
        if (!value.getCartaId().equals(cartaId)) throw new br.com.rockysoulup.exception.ConflictException();
        value.setUsuarioId(usuarioId);
        value.setCartaId(cartaId);
        return bo.Update(value);
    }
    @DeleteMapping("/{usuarioId}/{cartaId}")
    public ResponseEntity<Void> Delete(@PathVariable("usuarioId") @Positive Long usuarioId, @PathVariable("cartaId") @Positive Long cartaId, @RequestParam @PositiveOrZero long versao) {
        bo.Delete(usuarioId, cartaId, versao);
        return ResponseEntity.noContent().build();
    }
}
