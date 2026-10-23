package br.com.rockysoulup.to;
public record RoletaResponse(String verificacaoId, CartaTO carta, int chance, boolean girada, boolean ganhou, boolean duplicada, int fragmentos) {}
