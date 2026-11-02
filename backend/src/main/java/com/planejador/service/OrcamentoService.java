package com.planejador.service;

import com.planejador.dto.DashboardDto;
import com.planejador.dto.OrcamentoDto;
import com.planejador.enums.TipoCategoria;
import com.planejador.exception.ApiException;
import com.planejador.model.Categoria;
import com.planejador.model.ItemOrcamento;
import com.planejador.model.Orcamento;
import com.planejador.repository.ConsultasFinanceirasRepository;
import com.planejador.repository.ItemOrcamentoRepository;
import com.planejador.repository.OrcamentoRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrcamentoService {
    private final OrcamentoRepository orcamentos;
    private final ItemOrcamentoRepository itens;
    private final ConsultasFinanceirasRepository consultas;
    private final ReferenciaService referencias;
    private final CalculosFinanceirosService calculos;

    public OrcamentoService(OrcamentoRepository orcamentos, ItemOrcamentoRepository itens,
            ConsultasFinanceirasRepository consultas, ReferenciaService referencias, CalculosFinanceirosService calculos) {
        this.orcamentos = orcamentos;
        this.itens = itens;
        this.consultas = consultas;
        this.referencias = referencias;
        this.calculos = calculos;
    }

    public List<OrcamentoDto.Saida> listar(UUID usuarioId) {
        referencias.usuario(usuarioId);
        return orcamentos.findByUsuarioIdOrderByAnoDescMesDesc(usuarioId).stream().map(this::saida).toList();
    }

    public OrcamentoDto.Saida consultar(UUID usuarioId, UUID id) {
        return saida(obter(usuarioId, id));
    }

    @Transactional
    public OrcamentoDto.Saida criar(UUID usuarioId, OrcamentoDto.Entrada entrada) {
        Orcamento orcamento = new Orcamento();
        orcamento.setUsuario(referencias.bloquearUsuario(usuarioId));
        preencher(usuarioId, orcamento, entrada);
        return saida(orcamento);
    }

    @Transactional
    public OrcamentoDto.Saida atualizar(UUID usuarioId, UUID id, OrcamentoDto.Entrada entrada) {
        referencias.bloquearUsuario(usuarioId);
        Orcamento orcamento = obter(usuarioId, id);
        preencher(usuarioId, orcamento, entrada);
        return saida(orcamento);
    }

    private void preencher(UUID usuarioId, Orcamento orcamento, OrcamentoDto.Entrada entrada) {
        orcamentos.findByUsuarioIdAndAnoAndMes(usuarioId, entrada.ano(), entrada.mes()).ifPresent(existente -> {
            if (!existente.getId().equals(orcamento.getId())) {
                throw ApiException.conflito("ORCAMENTO_DUPLICADO", "Já existe orçamento para esse mês e usuário.");
            }
        });
        var categoriasUsadas = new HashSet<UUID>();
        List<ItemOrcamento> novosItens = entrada.itens().stream().map(entradaItem -> {
            if (!categoriasUsadas.add(entradaItem.categoriaId())) {
                throw ApiException.invalido("CATEGORIA_DUPLICADA", "Uma categoria não pode aparecer duas vezes no orçamento.");
            }
            Categoria categoria = referencias.categoria(usuarioId, entradaItem.categoriaId());
            if (categoria.getTipo() == TipoCategoria.RECEITA) {
                throw ApiException.invalido("CATEGORIA_INCOMPATIVEL", "O orçamento de gastos exige categorias DESPESA ou AMBOS.");
            }
            ItemOrcamento item = new ItemOrcamento();
            item.setOrcamento(orcamento);
            item.setCategoria(categoria);
            item.setValorPrevisto(calculos.moeda(entradaItem.valorPrevisto(), true));
            return item;
        }).toList();
        orcamento.setAno(entrada.ano());
        orcamento.setMes(entrada.mes());
        orcamento.setMetaEconomia(entrada.metaEconomia() == null ? null : calculos.moeda(entrada.metaEconomia(), true));
        if (orcamento.getId() != null) {
            itens.deleteAll(itens.findByOrcamentoId(orcamento.getId()));
            itens.flush();
        }
        orcamentos.saveAndFlush(orcamento);
        itens.saveAllAndFlush(novosItens);
    }

    private Orcamento obter(UUID usuarioId, UUID id) {
        return orcamentos.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> ApiException.naoEncontrado("Orçamento"));
    }

    private OrcamentoDto.Saida saida(Orcamento o) {
        UUID usuarioId = o.getUsuario().getId();
        YearMonth mes = YearMonth.of(o.getAno(), o.getMes());
        Map<UUID, BigDecimal> gastos = consultas.categorias(usuarioId, mes.atDay(1), mes.plusMonths(1).atDay(1))
                .stream().collect(Collectors.toMap(DashboardDto.CategoriaGasto::categoriaId, DashboardDto.CategoriaGasto::total));
        List<OrcamentoDto.ItemSaida> listaItens = itens.findByOrcamentoId(o.getId()).stream().map(item -> {
            BigDecimal realizado = gastos.getOrDefault(item.getCategoria().getId(), CalculosFinanceirosService.ZERO);
            return new OrcamentoDto.ItemSaida(item.getId(), item.getCategoria().getId(), item.getCategoria().getNome(),
                    item.getValorPrevisto(), realizado, item.getValorPrevisto().subtract(realizado));
        }).sorted(java.util.Comparator.comparing(OrcamentoDto.ItemSaida::categoria)).toList();
        BigDecimal previsto = listaItens.stream().map(OrcamentoDto.ItemSaida::previsto)
                .reduce(CalculosFinanceirosService.ZERO, BigDecimal::add);
        BigDecimal realizado = gastos.values().stream().reduce(CalculosFinanceirosService.ZERO, BigDecimal::add);
        BigDecimal realizadoOrcado = listaItens.stream().map(OrcamentoDto.ItemSaida::realizado)
                .reduce(CalculosFinanceirosService.ZERO, BigDecimal::add);
        return new OrcamentoDto.Saida(o.getId(), usuarioId, o.getAno(), o.getMes(), o.getMetaEconomia(),
                previsto, realizado, previsto.subtract(realizado), realizado.subtract(realizadoOrcado), listaItens);
    }
}
