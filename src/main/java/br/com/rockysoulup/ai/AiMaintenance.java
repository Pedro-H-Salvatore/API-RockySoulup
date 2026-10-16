package br.com.rockysoulup.ai;
import br.com.rockysoulup.bo.AiControlBO;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.ai.maintenance-enabled",havingValue="true",matchIfMissing=true)
public class AiMaintenance {
    private final AiControlBO control;
    public AiMaintenance(AiControlBO control) { this.control=control; }
    @Scheduled(initialDelay=60000,fixedDelay=60000)
    public void run() {
        try { control.maintain(); }
        catch(RuntimeException e) { org.slf4j.LoggerFactory.getLogger("security.events").error("event=AI_MAINTENANCE_UNAVAILABLE"); }
    }
}
