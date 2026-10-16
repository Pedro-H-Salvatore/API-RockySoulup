package br.com.rockysoulup.resource;

import br.com.rockysoulup.bo.RecompensaBO;
import br.com.rockysoulup.to.RecompensaTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/recompensa")
public class RecompensaResource {
    private final RecompensaBO bo;
    public RecompensaResource(RecompensaBO bo) { this.bo = bo; }
    @GetMapping
    public List<RecompensaTO> findAll(@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return bo.findAll(page, size); }
    @GetMapping("/{id}")
    public RecompensaTO findAllById(@PathVariable("id") @Positive Long id) { return bo.findAllById(id); }
    @PostMapping
    public ResponseEntity<RecompensaTO> save(@Valid @RequestBody RecompensaTO value) { return ResponseEntity.status(201).body(bo.save(value)); }
    @PutMapping("/{id}")
    public RecompensaTO Update(@PathVariable("id") @Positive Long id, @Valid @RequestBody RecompensaTO value) {

        value.setId(id);
        return bo.Update(value);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> Delete(@PathVariable("id") @Positive Long id, @RequestParam @PositiveOrZero long versao) {
        bo.Delete(id, versao);
        return ResponseEntity.noContent().build();
    }
}
