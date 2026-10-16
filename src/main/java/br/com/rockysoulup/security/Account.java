package br.com.rockysoulup.security;

public record Account(long id, String passwordHash, String role, boolean active, long tokenVersion) {
    @Override public String toString() { return "Account[redacted]"; }
}
