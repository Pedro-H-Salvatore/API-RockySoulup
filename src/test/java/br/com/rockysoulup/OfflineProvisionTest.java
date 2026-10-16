package br.com.rockysoulup;

import br.com.rockysoulup.bo.ProvisionBO;
import br.com.rockysoulup.dao.UsuarioDAO;
import br.com.rockysoulup.security.OfflineProvision;
import br.com.rockysoulup.to.CadastroRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;

class OfflineProvisionTest {
    @Test void bootstrapAndLegacyActivationAreOfflineAndControlled() {
        byte[] secret=new byte[32]; new java.security.SecureRandom().nextBytes(secret);
        new ApplicationContextRunner().withUserConfiguration(RockySoulupApplication.class)
            .withInitializer(context->context.getEnvironment().setActiveProfiles("provision"))
            .withInitializer(new org.springframework.boot.test.context.ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.main.web-application-type=none",
                "spring.datasource.url=jdbc:h2:mem:offline;MODE=Oracle;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa","spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.sql.init.mode=never","app.security.jwt-secret="+java.util.Base64.getEncoder().encodeToString(secret),
                "app.security.issuer=https://test.invalid","app.security.audience=tests","app.security.token-seconds=300",
                "app.security.allowed-origins=https://frontend.test.invalid","app.security.require-https=true",
                "PROVISION_MODE=bootstrap","PROVISION_NAME=Operador teste","PROVISION_EMAIL=operator@test.invalid",
                "PROVISION_PASSWORD=Test-only-provision-password")
            .run(context->{
                assertNull(context.getStartupFailure());
                new ResourceDatabasePopulator(new ClassPathResource("schema-test.sql")).execute(context.getBean(DataSource.class));
                var runner=context.getBean(OfflineProvision.class);
                runner.run(new DefaultApplicationArguments());
                UsuarioDAO users=context.getBean(UsuarioDAO.class);
                assertEquals("ADMIN",users.byEmail("operator@test.invalid").orElseThrow().role());
                assertThrows(IllegalStateException.class,()->runner.run(new DefaultApplicationArguments()));
                JdbcTemplate jdbc=context.getBean(JdbcTemplate.class);
                jdbc.update("INSERT INTO USUARIO (NM_USUARIO,DS_EMAIL) VALUES ('Legado','legacy@test.invalid')");
                assertFalse(users.byEmail("legacy@test.invalid").orElseThrow().active());
                var provisioning=context.getBean(ProvisionBO.class);
                provisioning.provision("activate",new CadastroRequest("Legado","legacy@test.invalid","Test-only-legacy-password"));
                var account=users.byEmail("legacy@test.invalid").orElseThrow();
                assertEquals("USER",account.role()); assertTrue(account.active()); assertNotNull(account.passwordHash());
                assertThrows(IllegalStateException.class,()->provisioning.provision("activate",new CadastroRequest("Legado","legacy@test.invalid","Test-only-other-password")));
            });
    }
}
