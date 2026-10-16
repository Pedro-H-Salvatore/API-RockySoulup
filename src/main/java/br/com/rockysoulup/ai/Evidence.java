package br.com.rockysoulup.ai;

public record Evidence(byte[] jpeg, String hash) {
    @Override public String toString() { return "Evidence[redacted]"; }
}
