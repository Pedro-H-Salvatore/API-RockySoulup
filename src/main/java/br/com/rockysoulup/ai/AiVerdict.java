package br.com.rockysoulup.ai;

public record AiVerdict(String parecer, double generated, double deepfake, int input, int output,
                        long costMicros, String providerModel) {
    public AiVerdict {
        if (!java.util.Set.of("COMPATIVEL","INCOMPATIVEL","INCONCLUSIVO").contains(parecer) ||
                !Double.isFinite(generated) || generated<0 || generated>1 || !Double.isFinite(deepfake) || deepfake<0 || deepfake>1 ||
                input<0 || input>8192 || output<0 || output>512 || costMicros<0 ||
                providerModel==null || !providerModel.matches("gpt-6-luna(?:-[0-9-]{1,20})?")) throw new ProviderException("INVALID_RESPONSE");
    }
    public String safeSummary() {
        if(generated>=0.5 || deepfake>=0.5) return "SINAL_MANIPULACAO";
        return parecer;
    }
}
