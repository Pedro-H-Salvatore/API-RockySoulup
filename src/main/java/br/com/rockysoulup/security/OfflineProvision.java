package br.com.rockysoulup.security;

import br.com.rockysoulup.bo.ProvisionBO;
import br.com.rockysoulup.to.CadastroRequest;
import jakarta.validation.Validator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("provision")
public class OfflineProvision implements ApplicationRunner {
    private final ProvisionBO bo;
    private final Environment environment;
    private final Validator validator;
    public OfflineProvision(ProvisionBO bo,Environment environment,Validator validator) { this.bo=bo; this.environment=environment; this.validator=validator; }
    public void run(ApplicationArguments args) {
        if (!"none".equals(environment.getProperty("spring.main.web-application-type")))
            throw new IllegalStateException("Provisionamento permitido somente em modo não web.");
        CadastroRequest request=new CadastroRequest(environment.getRequiredProperty("PROVISION_NAME"),
                environment.getRequiredProperty("PROVISION_EMAIL"),environment.getRequiredProperty("PROVISION_PASSWORD"));
        if (!validator.validate(request).isEmpty()) throw new IllegalStateException("Dados de provisionamento inválidos.");
        bo.provision(environment.getRequiredProperty("PROVISION_MODE"),request);
        org.slf4j.LoggerFactory.getLogger("security.events").info("event=OFFLINE_PROVISION_COMPLETED");
    }
}
