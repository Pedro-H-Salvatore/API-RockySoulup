package br.com.rockysoulup.resource;

import br.com.rockysoulup.bo.CartaBO;
import br.com.rockysoulup.to.CartaTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/carta")
public class CartaResource {
    private final CartaBO bo;
    public CartaResource(CartaBO bo) { this.bo = bo; }
    @GetMapping
    public List<CartaTO> findAll(@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return bo.findAll(page, size); }
    @GetMapping("/{id}")
    public CartaTO findAllById(@PathVariable("id") @Positive Long id) { return bo.findAllById(id); }
    @PostMapping
    public ResponseEntity<CartaTO> save(@Valid @RequestBody CartaTO value) { return ResponseEntity.status(201).body(bo.save(value)); }
    @PutMapping("/{id}")
    public CartaTO Update(@PathVariable("id") @Positive Long id, @Valid @RequestBody CartaTO value) {

        value.setId(id);
        return bo.Update(value);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> Delete(@PathVariable("id") @Positive Long id, @RequestParam @PositiveOrZero long versao) {
        bo.Delete(id, versao);
        return ResponseEntity.noContent().build();
    }
}
