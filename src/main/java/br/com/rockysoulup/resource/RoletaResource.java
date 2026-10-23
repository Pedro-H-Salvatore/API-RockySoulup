package br.com.rockysoulup.resource;
import br.com.rockysoulup.bo.RoletaBO;
import br.com.rockysoulup.to.RoletaResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
public class RoletaResource {
    private final RoletaBO bo;
    public RoletaResource(RoletaBO bo){this.bo=bo;}
    @GetMapping("/roletas") public List<RoletaResponse> findAll(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return bo.findAll(page,size);}
    @GetMapping("/verificacoes/{id}/roleta") public RoletaResponse consultar(@PathVariable String id){return bo.consultar(id);}
    @PostMapping("/verificacoes/{id}/roleta") public RoletaResponse girar(@PathVariable String id){return bo.girar(id);}
}
