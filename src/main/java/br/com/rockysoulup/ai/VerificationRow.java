package br.com.rockysoulup.ai;

import br.com.rockysoulup.to.VerificacaoResponse;
import java.time.Instant;

public record VerificationRow(VerificacaoResponse response, String fingerprint, String hash,
        byte[] evidence, String descricao, String acaoNome, int pontos, Instant created) {
    @Override public String toString() { return "VerificationRow[redacted]"; }
}
