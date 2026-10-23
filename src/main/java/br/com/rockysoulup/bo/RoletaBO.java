package br.com.rockysoulup.bo;
import br.com.rockysoulup.dao.RoletaDAO;
import br.com.rockysoulup.ai.RoletaSorteio;
import br.com.rockysoulup.security.AccessPolicy;
import br.com.rockysoulup.to.RoletaResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;
@Service
public class RoletaBO {
    private final RoletaDAO dao;private final AiAdmissionBO admission;private final AccessPolicy access;private final RoletaSorteio sorteio;private final Clock clock;
    public RoletaBO(RoletaDAO dao,AiAdmissionBO admission,AccessPolicy access,RoletaSorteio sorteio,Clock clock){this.dao=dao;this.admission=admission;this.access=access;this.sorteio=sorteio;this.clock=clock;}
    private void validar(String id){if(id==null||!id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw new IllegalArgumentException();}
    public RoletaResponse consultar(String id){validar(id);long user=access.userId();var d=dao.direito(id,user);return dao.resultado(id,user).orElseGet(()->dao.preview(id,d));}
    public List<RoletaResponse> findAll(int page,int size){long user=access.userId();return dao.disponiveis(user,access.offset(page,size),size).stream().map(id->consultar(id)).toList();}
    @Transactional
    public RoletaResponse girar(String id){
        validar(id);long user=access.userId();admission.lock();var d=dao.direito(id,user);var previous=dao.resultado(id,user);if(previous.isPresent())return previous.get();
        var preview=dao.preview(id,d);boolean won=sorteio.ganhou(d.chance());boolean duplicate=won&&dao.temCarta(user,d.cartaId());int fragments=duplicate?d.duplicada():0;
        if(won){if(duplicate)dao.fragmentos(user,preview.carta().getRaridade(),fragments);else dao.entregar(user,d.cartaId());}
        dao.save(id,user,d,won,duplicate,fragments,clock.instant());access.audit("ROLETA_CONCLUIDA");return dao.resultado(id,user).orElseThrow();
    }
}
