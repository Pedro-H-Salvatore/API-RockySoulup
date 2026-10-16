package br.com.rockysoulup.ai;

public record Admission(VerificationRow row, String attemptId) {
    public boolean execute() { return attemptId != null; }
    @Override public String toString() { return "Admission[redacted]"; }
}
