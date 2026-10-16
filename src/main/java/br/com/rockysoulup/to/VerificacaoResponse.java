package br.com.rockysoulup.to;

public record VerificacaoResponse(String id, long usuarioId, long acaoId, String estado,
        long versao, boolean possivelReuso, boolean evidenciaDisponivel, String parecer,
        String modelo, String regras, int tentativas, int pontosCreditados) {}
