package br.com.rockysoulup.ai;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;
@Component
public class RoletaSorteio {
    private final SecureRandom random=new SecureRandom();
    public boolean ganhou(int chance) { return random.nextInt(100)<chance; }
}
