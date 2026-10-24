package br.com.rockysoulup.to;

public record VerificacaoResponse(String id, long usuarioId, long acaoId, String estado,
        long versao, boolean possivelReuso, boolean evidenciaDisponivel, String parecer,
        String modelo, String regras, int tentativas, int pontosCreditados,
        Integer notaCompatibilidade, String feedback, Boolean evidenciaInsuficiente, Boolean suspeitaManipulacao) {
    public VerificacaoResponse withoutAssessment() {
        return new VerificacaoResponse(id,usuarioId,acaoId,estado,versao,possivelReuso,evidenciaDisponivel,parecer,modelo,regras,tentativas,pontosCreditados,null,null,null,null);
    }
    public VerificacaoResponse(String id,long user,long action,String state,long version,boolean reused,boolean evidence,
            String summary,String model,String rules,int attempts,int points) {
        this(id,user,action,state,version,reused,evidence,summary,model,rules,attempts,points,null,null,null,null);
    }
}
