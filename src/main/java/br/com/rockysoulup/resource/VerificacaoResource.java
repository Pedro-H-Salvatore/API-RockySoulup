package br.com.rockysoulup.resource;

import br.com.rockysoulup.bo.*;
import br.com.rockysoulup.to.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.*;

@RestController
public class VerificacaoResource {
    private final VerificacaoBO bo;
    private final AiControlBO control;
    private final CapturaBO capture;
    public VerificacaoResource(VerificacaoBO bo,AiControlBO control,CapturaBO capture) { this.bo=bo; this.control=control; this.capture=capture; }
    @PostMapping("/verificacoes/desafio")
    public DesafioResponse challenge(@Valid @RequestBody DesafioRequest value,HttpServletRequest request) { return capture.issue(value.acaoId(),request.getRemoteAddr()); }
    @PostMapping("/verificacoes")
    public ResponseEntity<VerificacaoResponse> submit(@Valid @RequestBody VerificacaoRequest value,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request) {
        return ResponseEntity.accepted().body(bo.submit(value,key,request.getRemoteAddr()));
    }
    @GetMapping("/verificacoes")
    public List<VerificacaoResponse> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return bo.list(page,size); }
    @GetMapping("/verificacoes/{id}")
    public VerificacaoResponse get(@PathVariable UUID id) { return bo.get(id.toString()); }
    @GetMapping("/verificacoes/{id}/evidencia")
    public ResponseEntity<byte[]> evidence(@PathVariable UUID id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).header("Content-Disposition","inline; filename=evidencia.jpg").body(bo.evidence(id.toString()));
    }
    @DeleteMapping("/verificacoes/{id}/evidencia")
    public ResponseEntity<Void> delete(@PathVariable UUID id) { bo.deleteEvidence(id.toString()); return ResponseEntity.noContent().build(); }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/verificacoes/{id}/revisao")
    public VerificacaoResponse review(@PathVariable UUID id,@Valid @RequestBody RevisaoRequest request) { return bo.review(id.toString(),request); }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/verificacoes/{id}/reprocessar")
    public VerificacaoResponse reprocess(@PathVariable UUID id,@Valid @RequestBody ReprocessarRequest value,HttpServletRequest request) { return bo.reprocess(id.toString(),value.versao(),request.getRemoteAddr()); }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/ia/suspensao")
    public ResponseEntity<Void> suspend(@Valid @RequestBody SuspensaoRequest value) { control.suspend(value.suspenso()); return ResponseEntity.noContent().build(); }
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/ia/consumo")
    public Map<String,Object> usage() { return control.usage(); }
}
