package br.com.rockysoulup.security;

import jakarta.servlet.http.*;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class SecurityJson implements AuthenticationEntryPoint, AccessDeniedHandler {
    public static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control","no-store");
        response.getWriter().write("{\"status\":" + status + ",\"mensagem\":\"" + message + "\"}");
    }
    @Override public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException error) throws IOException {
        LoggerFactory.getLogger("security.events").info("event=AUTHENTICATION_DENIED");
        response.setHeader("WWW-Authenticate","Bearer"); write(response,401,"Autenticação necessária ou inválida.");
    }
    @Override public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException error) throws IOException {
        LoggerFactory.getLogger("security.events").info("event=AUTHORIZATION_DENIED");
        write(response,403,"Acesso negado.");
    }
}
