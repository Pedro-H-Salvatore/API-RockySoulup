package br.com.rockysoulup.ai;

public record AiVerdict(String parecer, int input, int output, long costMicros, String providerModel) {
    public AiVerdict {
        if (!java.util.Set.of("COMPATIVEL","INCOMPATIVEL","INCONCLUSIVO").contains(parecer) ||
                input<1 || input>8192 || output<1 || output>512 || costMicros<0 ||
                !validModel(providerModel)) throw new ProviderException("INVALID_RESPONSE");
    }
    public static boolean validModel(String model) {
        return model!=null && model.length()<=40 && model.matches("gemini-3\\.5-flash-lite(?:-[a-zA-Z0-9.-]{1,16})?");
    }
    public String safeSummary() { return parecer; }
}
