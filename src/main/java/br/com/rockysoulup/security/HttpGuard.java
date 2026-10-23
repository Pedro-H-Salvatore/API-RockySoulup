package br.com.rockysoulup.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HttpGuard extends OncePerRequestFilter {
    private static final int MAX_BODY = 16384;
    private final SecurityProperties properties;
    public HttpGuard(SecurityProperties properties) { this.properties = properties; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        int maxBody = request.getMethod().equals("POST") && request.getRequestURI().equals("/verificacoes") ? 720000 : MAX_BODY;
        boolean healthProbe = request.getMethod().equals("GET") && request.getRequestURI().equals("/health");
        if (properties.requireHttps() && !request.isSecure() && !healthProbe) { SecurityJson.write(response,403,"HTTPS obrigatório."); return; }
        if (request.getContentLengthLong() > maxBody) { SecurityJson.write(response,413,"Corpo da requisição excede o limite."); return; }
        byte[] bytes = request.getInputStream().readNBytes(maxBody + 1);
        if (bytes.length > maxBody) { SecurityJson.write(response,413,"Corpo da requisição excede o limite."); return; }
        if (bytes.length > 0 && (request.getContentType() == null || !request.getContentType().split(";")[0].equalsIgnoreCase("application/json"))) {
            SecurityJson.write(response,415,"Use application/json."); return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                ByteArrayInputStream input = new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    public int read() { return input.read(); }
                    public boolean isFinished() { return input.available() == 0; }
                    public boolean isReady() { return true; }
                    public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8)); }
        },response);
    }
}
