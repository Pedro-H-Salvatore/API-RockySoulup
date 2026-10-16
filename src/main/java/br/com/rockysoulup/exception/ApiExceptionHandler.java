package br.com.rockysoulup.exception;

import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.*;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private ResponseEntity<?> error(int status,String message) { return ResponseEntity.status(status).body(Map.of("status",status,"mensagem",message)); }
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<?> missing() { return error(404,"Recurso não encontrado."); }
    @ExceptionHandler({ConflictException.class,DataIntegrityViolationException.class})
    public ResponseEntity<?> conflict() { return error(409,"Conflito de dados ou versão. Consulte o registro antes de repetir."); }
    @ExceptionHandler({MethodArgumentNotValidException.class,HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class,ConstraintViolationException.class,IllegalArgumentException.class})
    public ResponseEntity<?> invalid() { return error(400,"Entrada inválida."); }
    @ExceptionHandler({LoginFailureException.class,AuthenticationException.class})
    public ResponseEntity<?> login() { return error(401,"Autenticação necessária ou inválida."); }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> denied() { LoggerFactory.getLogger("security.events").info("event=AUTHORIZATION_DENIED"); return error(403,"Acesso negado."); }
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<?> limit() { return ResponseEntity.status(429).header("Retry-After","900").body(Map.of("status",429,"mensagem","Limite de tentativas atingido.")); }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> database() { LoggerFactory.getLogger("security.events").error("event=PERSISTENCE_FAILURE"); return error(503,"Serviço temporariamente indisponível."); }
    @ExceptionHandler(br.com.rockysoulup.ai.AiLimitException.class)
    public ResponseEntity<?> aiLimit() { return ResponseEntity.status(429).header("Retry-After","60").body(Map.of("status",429,"mensagem","Limite de análise ou orçamento atingido. Consulte novamente antes de reenviar.")); }
    @ExceptionHandler(br.com.rockysoulup.ai.AiUnavailableException.class)
    public ResponseEntity<?> aiUnavailable() { return error(503,"Análise temporariamente indisponível."); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> internal() { LoggerFactory.getLogger("security.events").error("event=INTERNAL_FAILURE"); return error(500,"Não foi possível processar a solicitação."); }
}
