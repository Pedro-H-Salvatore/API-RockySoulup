package br.com.rockysoulup.ai;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.ai")
public record AiProperties(boolean enabled, String geminiKey,
        @NotBlank @Pattern(regexp="gemini-3\\.5-flash-lite") String model,
        @Min(1) @Max(10) int userMinute, @Min(1) @Max(100) int ipMinute,
        @Min(1) @Max(100) int globalMinute, @Min(1) @Max(100) int userDaily,
        @Min(1) @Max(10000) int globalDaily,
        @Min(1) @Max(10) int concurrency, @Min(1) @Max(500) int pendingLimit,
        @Min(1) long dailyMicros, @Min(1) long monthlyMicros, @Min(1) long userDailyMicros,
        @Min(1) @Max(1000000) long geminiCeilingMicros,
        @Min(0) @Max(1000000000) long inputMicrosPerMillion,
        @Min(0) @Max(1000000000) long outputMicrosPerMillion,
        @Min(1) @Max(7) int retentionDays) {
    public AiProperties {
        long maximum=(Math.addExact(Math.multiplyExact(8192L,inputMicrosPerMillion),Math.multiplyExact(512L,outputMicrosPerMillion))+999999)/1000000;
        if(geminiCeilingMicros<maximum) throw new IllegalArgumentException("Teto Gemini insuficiente para os parâmetros configurados.");
    }
    public long reservation() { return geminiCeilingMicros; }
    @Override public String toString() { return "AiProperties[redacted]"; }
}
