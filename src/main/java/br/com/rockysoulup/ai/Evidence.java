package br.com.rockysoulup.ai;

public record Evidence(byte[] jpeg, String hash, String visualHash) {
    public Evidence(byte[] jpeg,String hash) { this(jpeg,hash,null); }
    @Override public String toString() { return "Evidence[redacted]"; }
}
