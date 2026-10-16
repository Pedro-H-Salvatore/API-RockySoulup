package br.com.rockysoulup.resource;

import br.com.rockysoulup.bo.HistoricoBO;
import br.com.rockysoulup.to.HistoricoTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/historico")
public class HistoricoResource {
    private final HistoricoBO bo;
    public HistoricoResource(HistoricoBO bo) { this.bo = bo; }
    @GetMapping
    public List<HistoricoTO> findAll(@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return bo.findAll(page, size); }
    @GetMapping("/{id}")
    public HistoricoTO findAllById(@PathVariable("id") @Positive Long id) { return bo.findAllById(id); }
    @PostMapping
    public ResponseEntity<HistoricoTO> save(@Valid @RequestBody HistoricoTO value) { return ResponseEntity.status(201).body(bo.save(value)); }
    @PutMapping("/{id}")
    public HistoricoTO Update(@PathVariable("id") @Positive Long id, @Valid @RequestBody HistoricoTO value) {

        value.setId(id);
        return bo.Update(value);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> Delete(@PathVariable("id") @Positive Long id, @RequestParam @PositiveOrZero long versao) {
        bo.Delete(id, versao);
        return ResponseEntity.noContent().build();
    }
}
