package br.com.rockysoulup.ai;

public class ProviderException extends RuntimeException {
    private final String code;
    public ProviderException(String code) { super("Provedor indisponível ou resposta inválida."); this.code=code; }
    public String code() { return code; }
}
