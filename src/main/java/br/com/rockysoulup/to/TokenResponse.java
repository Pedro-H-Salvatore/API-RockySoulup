package br.com.rockysoulup.to;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, UsuarioResponse usuario, boolean administrador) {
    public TokenResponse(String token,String type,long expires) { this(token,type,expires,null,false); }
    @Override public String toString() { return "TokenResponse[redacted]"; }
}
