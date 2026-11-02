package com.planejador.service;

import com.planejador.dto.PaginaDto;
import com.planejador.dto.TransferenciaDto;
import com.planejador.enums.SituacaoTransacao;
import com.planejador.exception.ApiException;
import com.planejador.model.Conta;
import com.planejador.model.Transferencia;
import com.planejador.repository.TransferenciaRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransferenciaService {
    private final TransferenciaRepository transferencias;
    private final ReferenciaService referencias;
    private final CalculosFinanceirosService calculos;

    public TransferenciaService(TransferenciaRepository transferencias, ReferenciaService referencias,
            CalculosFinanceirosService calculos) {
        this.transferencias = transferencias;
        this.referencias = referencias;
        this.calculos = calculos;
    }

    public PaginaDto<TransferenciaDto.Saida> listar(UUID usuarioId, LocalDate inicio, LocalDate fim,
            int pagina, int tamanho) {
        referencias.usuario(usuarioId);
        calculos.periodo(inicio, fim);
        Specification<Transferencia> filtro = (root, query, cb) -> {
            var predicados = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicados.add(cb.equal(root.get("usuario").get("id"), usuarioId));
            if (inicio != null) predicados.add(cb.greaterThanOrEqualTo(root.get("dataTransferencia"), inicio));
            if (fim != null) predicados.add(cb.lessThanOrEqualTo(root.get("dataTransferencia"), fim));
            return cb.and(predicados.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return PaginaDto.de(transferencias.findAll(filtro,
                PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "dataTransferencia", "id"))).map(this::saida));
    }

    public TransferenciaDto.Saida consultar(UUID usuarioId, UUID id) {
        return saida(obter(usuarioId, id));
    }

    @Transactional
    public TransferenciaDto.Saida criar(UUID usuarioId, TransferenciaDto.Entrada entrada) {
        Transferencia transferencia = new Transferencia();
        transferencia.setUsuario(referencias.bloquearUsuario(usuarioId));
        if (entrada.contaOrigemId().equals(entrada.contaDestinoId())) {
            throw ApiException.invalido("CONTAS_IGUAIS", "A origem e o destino devem ser diferentes.");
        }
        Conta origem = referencias.conta(usuarioId, entrada.contaOrigemId(), true);
        Conta destino = referencias.conta(usuarioId, entrada.contaDestinoId(), true);
        SituacaoTransacao situacao = entrada.situacao() == null ? SituacaoTransacao.CONFIRMADA : entrada.situacao();
        if (situacao == SituacaoTransacao.CANCELADA) {
            throw ApiException.invalido("SITUACAO_INVALIDA", "Utilize a operação de cancelamento.");
        }
        validarData(entrada.dataTransferencia(), situacao);
        transferencia.setContaOrigem(origem);
        transferencia.setContaDestino(destino);
        transferencia.setValor(calculos.moeda(entrada.valor(), false));
        transferencia.setDataTransferencia(entrada.dataTransferencia());
        transferencia.setSituacao(situacao);
        return saida(transferencias.saveAndFlush(transferencia));
    }

    @Transactional
    public void cancelar(UUID usuarioId, UUID id) {
        referencias.bloquearUsuario(usuarioId);
        obter(usuarioId, id).setSituacao(SituacaoTransacao.CANCELADA);
        transferencias.flush();
    }

    @Transactional
    public TransferenciaDto.Saida confirmar(UUID usuarioId, UUID id) {
        referencias.bloquearUsuario(usuarioId);
        Transferencia transferencia = obter(usuarioId, id);
        if (transferencia.getSituacao() == SituacaoTransacao.CANCELADA) {
            throw ApiException.conflito("TRANSFERENCIA_CANCELADA", "Uma transferência cancelada não pode ser confirmada.");
        }
        referencias.conta(usuarioId, transferencia.getContaOrigem().getId(), true);
        referencias.conta(usuarioId, transferencia.getContaDestino().getId(), true);
        validarData(transferencia.getDataTransferencia(), SituacaoTransacao.CONFIRMADA);
        transferencia.setSituacao(SituacaoTransacao.CONFIRMADA);
        transferencias.flush();
        return saida(transferencia);
    }

    private void validarData(LocalDate data, SituacaoTransacao situacao) {
        if (data == null || (situacao == SituacaoTransacao.CONFIRMADA && data.isAfter(calculos.hoje()))) {
            throw ApiException.invalido("DATA_INVALIDA", "Transferências futuras devem permanecer pendentes.");
        }
    }

    private Transferencia obter(UUID usuarioId, UUID id) {
        return transferencias.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> ApiException.naoEncontrado("Transferência"));
    }

    private TransferenciaDto.Saida saida(Transferencia t) {
        return new TransferenciaDto.Saida(t.getId(), t.getUsuario().getId(), t.getContaOrigem().getId(),
                t.getContaDestino().getId(), t.getValor(), t.getDataTransferencia(), t.getSituacao());
    }
}
