package br.com.rockysoulup.ai;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.ai")
public record AiProperties(boolean enabled, String openaiKey, String hiveKey,
        @Pattern(regexp="gpt-6-luna") String model,
        @Min(1) @Max(10) int userMinute, @Min(1) @Max(100) int ipMinute,
        @Min(1) @Max(100) int globalMinute, @Min(1) @Max(100) int userDaily,
        @Min(1) @Max(10000) int globalDaily,
        @Min(1) @Max(10) int concurrency, @Min(1) @Max(500) int pendingLimit,
        @Min(1) long dailyMicros, @Min(1) long monthlyMicros, @Min(1) long userDailyMicros,
        @Min(2000) @Max(1000000) long openaiCeilingMicros,
        @Min(10000) @Max(1000000) long hiveCeilingMicros,
        @Min(1) long inputMicrosPerMillion, @Min(1) long outputMicrosPerMillion,
        @Min(1) @Max(7) int retentionDays) {
    public AiProperties {
        // Refuse a reservation below the configured maximum token charge.
        long maximum=(Math.addExact(Math.multiplyExact(8192L,inputMicrosPerMillion),Math.multiplyExact(512L,outputMicrosPerMillion))+999999)/1000000;
        if(openaiCeilingMicros<maximum) throw new IllegalArgumentException("Teto OpenAI insuficiente para os parâmetros configurados.");
    }
    public long reservation() { return Math.addExact(openaiCeilingMicros,hiveCeilingMicros); }
    @Override public String toString() { return "AiProperties[redacted]"; }
}
