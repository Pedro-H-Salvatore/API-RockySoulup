package br.com.rockysoulup;

import br.com.rockysoulup.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class HttpGuardTest {
    private SecurityProperties properties(boolean https) {
        return new SecurityProperties("test-not-used","https://test.invalid","tests",300,List.of("https://frontend.test.invalid"),https);
    }
    @Test void productionRequiresSecureTransport() throws Exception {
        var request=new MockHttpServletRequest("GET","/acao");
        var response=new MockHttpServletResponse();
        AtomicBoolean reached=new AtomicBoolean();
        new HttpGuard(properties(true)).doFilter(request,response,(req,res)->reached.set(true));
        assertEquals(403,response.getStatus()); assertFalse(reached.get());
        assertTrue(response.getContentType().startsWith("application/json"));
    }
    @Test void onlyExactHealthGetAllowsInternalHttpProbe() throws Exception {
        for (String[] route : List.of(new String[]{"GET","/health"},new String[]{"POST","/health"},new String[]{"GET","/health/"},new String[]{"GET","/acao"})) {
            var request=new MockHttpServletRequest(route[0],route[1]);
            var response=new MockHttpServletResponse();
            AtomicBoolean reached=new AtomicBoolean();
            new HttpGuard(properties(true)).doFilter(request,response,(req,res)->reached.set(true));
            assertEquals(route[0].equals("GET") && route[1].equals("/health"),reached.get());
        }
    }
    @Test void bodiesWithoutContentLengthAreBounded() throws Exception {
        var request=new MockHttpServletRequest("POST","/auth/login") {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        request.setContentType("application/json"); request.setContent(new byte[17000]);
        var response=new MockHttpServletResponse();
        AtomicBoolean reached=new AtomicBoolean();
        new HttpGuard(properties(false)).doFilter(request,response,(req,res)->reached.set(true));
        assertEquals(413,response.getStatus()); assertFalse(reached.get());
    }
    @Test void formContentIsNotAcceptedForAuthentication() throws Exception {
        var request=new MockHttpServletRequest("POST","/auth/login");
        request.setContentType("application/x-www-form-urlencoded"); request.setContent("email=test".getBytes());
        var response=new MockHttpServletResponse();
        AtomicBoolean reached=new AtomicBoolean();
        new HttpGuard(properties(false)).doFilter(request,response,(req,res)->reached.set(true));
        assertEquals(415,response.getStatus()); assertFalse(reached.get());
    }
}
