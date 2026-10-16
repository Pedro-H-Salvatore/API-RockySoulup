package br.com.rockysoulup.to;

public record UsuarioResponse(Long id, String nome, String email, int pontos, int resgatados, long versao) {}
