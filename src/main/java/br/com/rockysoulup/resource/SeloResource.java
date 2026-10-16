package br.com.rockysoulup.resource;

import br.com.rockysoulup.bo.SeloBO;
import br.com.rockysoulup.to.SeloTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/selo")
public class SeloResource {
    private final SeloBO bo;
    public SeloResource(SeloBO bo) { this.bo = bo; }
    @GetMapping
    public List<SeloTO> findAll(@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return bo.findAll(page, size); }
    @GetMapping("/{id}")
    public SeloTO findAllById(@PathVariable("id") @Positive Long id) { return bo.findAllById(id); }
    @PostMapping
    public ResponseEntity<SeloTO> save(@Valid @RequestBody SeloTO value) { return ResponseEntity.status(201).body(bo.save(value)); }
    @PutMapping("/{id}")
    public SeloTO Update(@PathVariable("id") @Positive Long id, @Valid @RequestBody SeloTO value) {

        value.setId(id);
        return bo.Update(value);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> Delete(@PathVariable("id") @Positive Long id, @RequestParam @PositiveOrZero long versao) {
        bo.Delete(id, versao);
        return ResponseEntity.noContent().build();
    }
}
