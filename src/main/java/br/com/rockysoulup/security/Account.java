package br.com.rockysoulup.security;

public record Account(long id, String passwordHash, String role, boolean active, long tokenVersion, br.com.rockysoulup.to.UsuarioResponse usuario) {
    public Account(long id,String hash,String role,boolean active,long version) { this(id,hash,role,active,version,null); }
    @Override public String toString() { return "Account[redacted]"; }
}
