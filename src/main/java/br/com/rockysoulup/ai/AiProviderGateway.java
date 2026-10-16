package br.com.rockysoulup.ai;

public interface AiProviderGateway {
    AiVerdict evaluate(VerificationRow row, Runnable beforeDispatch);
}
