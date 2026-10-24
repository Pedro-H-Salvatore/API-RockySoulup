package br.com.rockysoulup.resource;

import br.com.rockysoulup.bo.AuthBO;
import br.com.rockysoulup.to.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthResource {
    private final AuthBO bo;
    public AuthResource(AuthBO bo) { this.bo=bo; }
    @PostMapping("/cadastro")
    public ResponseEntity<UsuarioResponse> cadastro(@Valid @RequestBody CadastroRequest request, HttpServletRequest servlet) {
        return ResponseEntity.status(201).body(bo.cadastro(request,servlet.getRemoteAddr()));
    }
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servlet) { return bo.login(request,servlet.getRemoteAddr()); }
    @GetMapping("/sessao")
    public SessaoResponse sessao() { return bo.sessao(); }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() { bo.logout(); return ResponseEntity.noContent().build(); }
}
