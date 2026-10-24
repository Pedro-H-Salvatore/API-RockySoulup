package br.com.rockysoulup.ai;

public record AiVerdict(String parecer, String codigoVisivel, int input, int output, long costMicros, String providerModel,
        Integer notaCompatibilidade, boolean evidenciaInsuficiente, boolean suspeitaManipulacao, String feedback) {
    public AiVerdict {
        if (codigoVisivel==null || !codigoVisivel.matches("(?:[A-Z2-9]{6}|NONE)?") || !java.util.Set.of("COMPATIVEL","INCOMPATIVEL","INCONCLUSIVO").contains(parecer) ||
                input<1 || input>8192 || output<1 || output>512 || costMicros<0 || !validModel(providerModel) ||
                (notaCompatibilidade!=null && (notaCompatibilidade<0 || notaCompatibilidade>100)) ||
                (notaCompatibilidade!=null && (feedback==null || feedback.isBlank() || feedback.length()>400)) ||
                (feedback!=null && feedback.codePoints().anyMatch(c->Character.isISOControl(c)))) throw new ProviderException("INVALID_RESPONSE");
    }
    // Legacy verdicts have no score and must always require manual review.
    public AiVerdict(String parecer,String code,int input,int output,long cost,String model) { this(parecer,code,input,output,cost,model,null,true,false,null); }
    public AiVerdict(String parecer,int input,int output,long cost,String model) { this(parecer,"",input,output,cost,model); }
    public static boolean validModel(String model) {
        return model!=null && model.length()<=40 && model.matches("gemini-3\\.5-flash-lite(?:-[a-zA-Z0-9.-]{1,16})?");
    }
    public String safeSummary() { return parecer; }
}
