package com.planejador.service;

import com.planejador.dto.UsuarioDto;
import com.planejador.exception.ApiException;
import com.planejador.model.Usuario;
import com.planejador.repository.UsuarioRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UsuarioService {
    private final UsuarioRepository usuarios;
    private final Clock clock;

    public UsuarioService(UsuarioRepository usuarios, Clock clock) {
        this.usuarios = usuarios;
        this.clock = clock;
    }

    public List<UsuarioDto.Saida> listar() {
        return usuarios.findAll(Sort.by("nome", "id")).stream().map(this::saida).toList();
    }

    public UsuarioDto.Saida consultar(UUID id) {
        return saida(usuarios.findById(id).orElseThrow(() -> ApiException.naoEncontrado("Usuário")));
    }

    @Transactional
    public UsuarioDto.Saida criar(UsuarioDto.Entrada entrada) {
        String email = entrada.email().trim().toLowerCase(Locale.ROOT);
        if (usuarios.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflito("EMAIL_DUPLICADO", "Já existe um usuário com esse email.");
        }
        Usuario usuario = new Usuario();
        usuario.setNome(entrada.nome().trim());
        usuario.setEmail(email);
        usuario.setCriadoEm(LocalDateTime.now(clock));
        return saida(usuarios.saveAndFlush(usuario));
    }

    @Transactional
    public UsuarioDto.Saida atualizar(UUID usuarioId, UUID id, UsuarioDto.Entrada entrada) {
        if (!usuarioId.equals(id)) throw ApiException.naoEncontrado("Usuário");
        Usuario usuario = usuarios.bloquear(id).orElseThrow(() -> ApiException.naoEncontrado("Usuário"));
        String email = entrada.email().trim().toLowerCase(Locale.ROOT);
        if (usuarios.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw ApiException.conflito("EMAIL_DUPLICADO", "Já existe um usuário com esse email.");
        }
        usuario.setNome(entrada.nome().trim());
        usuario.setEmail(email);
        return saida(usuarios.saveAndFlush(usuario));
    }

    private UsuarioDto.Saida saida(Usuario usuario) {
        return new UsuarioDto.Saida(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getCriadoEm());
    }
}
