package com.planejador.service;

import com.planejador.dto.CategoriaDto;
import com.planejador.enums.TipoCategoria;
import com.planejador.exception.ApiException;
import com.planejador.model.Categoria;
import com.planejador.repository.CategoriaRepository;
import com.planejador.repository.ItemOrcamentoRepository;
import com.planejador.repository.TransacaoRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoriaService {
    private final CategoriaRepository categorias;
    private final TransacaoRepository transacoes;
    private final ItemOrcamentoRepository itens;
    private final ReferenciaService referencias;

    public CategoriaService(CategoriaRepository categorias, TransacaoRepository transacoes,
            ItemOrcamentoRepository itens, ReferenciaService referencias) {
        this.categorias = categorias;
        this.transacoes = transacoes;
        this.itens = itens;
        this.referencias = referencias;
    }

    public List<CategoriaDto.Saida> listar(UUID usuarioId, TipoCategoria tipo) {
        referencias.usuario(usuarioId);
        return categorias.findByUsuarioIdOrderByNomeAsc(usuarioId).stream()
                .filter(categoria -> tipo == null || categoria.getTipo() == tipo).map(this::saida).toList();
    }

    public CategoriaDto.Saida consultar(UUID usuarioId, UUID id) {
        return saida(referencias.categoria(usuarioId, id));
    }

    @Transactional
    public CategoriaDto.Saida criar(UUID usuarioId, CategoriaDto.Entrada entrada) {
        Categoria categoria = new Categoria();
        categoria.setUsuario(referencias.bloquearUsuario(usuarioId));
        preencher(usuarioId, categoria, entrada);
        return saida(categorias.saveAndFlush(categoria));
    }

    @Transactional
    public CategoriaDto.Saida atualizar(UUID usuarioId, UUID id, CategoriaDto.Entrada entrada) {
        referencias.bloquearUsuario(usuarioId);
        Categoria categoria = referencias.categoria(usuarioId, id);
        if (categoria.getTipo() != entrada.tipo() && possuiDependencias(id)) {
            throw ApiException.conflito("CATEGORIA_EM_USO", "Não é possível alterar o tipo de uma categoria em uso.");
        }
        preencher(usuarioId, categoria, entrada);
        return saida(categorias.saveAndFlush(categoria));
    }

    @Transactional
    public void excluir(UUID usuarioId, UUID id) {
        referencias.bloquearUsuario(usuarioId);
        Categoria categoria = referencias.categoria(usuarioId, id);
        if (possuiDependencias(id)) {
            throw ApiException.conflito("CATEGORIA_EM_USO", "A categoria possui transações ou itens de orçamento.");
        }
        categorias.delete(categoria);
        categorias.flush();
    }

    private boolean possuiDependencias(UUID id) {
        return transacoes.existsByCategoriaId(id) || itens.existsByCategoriaId(id);
    }

    private void preencher(UUID usuarioId, Categoria categoria, CategoriaDto.Entrada entrada) {
        categorias.findByUsuarioIdAndNomeAndTipo(usuarioId, entrada.nome().trim(), entrada.tipo()).ifPresent(existente -> {
            if (!existente.getId().equals(categoria.getId())) {
                throw ApiException.conflito("CATEGORIA_DUPLICADA", "Já existe uma categoria com esse nome e tipo.");
            }
        });
        categoria.setNome(entrada.nome().trim());
        categoria.setTipo(entrada.tipo());
    }

    private CategoriaDto.Saida saida(Categoria categoria) {
        return new CategoriaDto.Saida(categoria.getId(), categoria.getUsuario().getId(),
                categoria.getNome(), categoria.getTipo());
    }
}
