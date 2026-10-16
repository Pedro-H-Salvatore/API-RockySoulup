package br.com.rockysoulup.resource;

import br.com.rockysoulup.bo.UsuarioBO;
import br.com.rockysoulup.to.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/usuario")
public class UsuarioResource {
    private final UsuarioBO bo;
    public UsuarioResource(UsuarioBO bo) { this.bo = bo; }
    @GetMapping
    public List<UsuarioResponse> findAll(@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return bo.findAll(page,size); }
    @GetMapping("/{id}")
    public UsuarioResponse findAllById(@PathVariable @Positive long id) { return bo.findAllById(id); }
    @PutMapping("/{id}")
    public UsuarioResponse Update(@PathVariable @Positive long id, @Valid @RequestBody PerfilRequest request) { return bo.Update(id,request); }
    @PutMapping("/{id}/saldo")
    public UsuarioResponse saldo(@PathVariable @Positive long id, @Valid @RequestBody SaldoRequest request) { return bo.saldo(id,request); }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> Delete(@PathVariable @Positive long id, @RequestParam @PositiveOrZero long versao) {
        bo.Delete(id,versao); return ResponseEntity.noContent().build();
    }
}
