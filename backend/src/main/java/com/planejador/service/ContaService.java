package com.planejador.service;

import com.planejador.dto.ContaDto;
import com.planejador.model.Conta;
import com.planejador.repository.ContaRepository;
import com.planejador.repository.ConsultasFinanceirasRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ContaService {
    private final ContaRepository contas;
    private final ReferenciaService referencias;
    private final ConsultasFinanceirasRepository consultas;
    private final CalculosFinanceirosService calculos;

    public ContaService(ContaRepository contas, ReferenciaService referencias,
            ConsultasFinanceirasRepository consultas, CalculosFinanceirosService calculos) {
        this.contas = contas;
        this.referencias = referencias;
        this.consultas = consultas;
        this.calculos = calculos;
    }

    public List<ContaDto.Saida> listar(UUID usuarioId) {
        referencias.usuario(usuarioId);
        return contas.findByUsuarioIdOrderByNomeAsc(usuarioId).stream().map(this::saida).toList();
    }

    public ContaDto.Saida consultar(UUID usuarioId, UUID id) {
        return saida(referencias.conta(usuarioId, id, false));
    }

    public ContaDto.Saldo saldo(UUID usuarioId, UUID id) {
        referencias.conta(usuarioId, id, false);
        return new ContaDto.Saldo(id, consultas.saldo(usuarioId, id, calculos.hoje()));
    }

    @Transactional
    public ContaDto.Saida criar(UUID usuarioId, ContaDto.Entrada entrada) {
        Conta conta = new Conta();
        conta.setUsuario(referencias.bloquearUsuario(usuarioId));
        conta.setNome(entrada.nome().trim());
        conta.setTipo(entrada.tipo());
        BigDecimal inicial = calculos.moeda(entrada.saldoInicial().abs(), true);
        conta.setSaldoInicial(entrada.saldoInicial().signum() < 0 ? inicial.negate() : inicial);
        conta.setAtiva(true);
        return saida(contas.save(conta));
    }

    @Transactional
    public ContaDto.Saida atualizar(UUID usuarioId, UUID id, ContaDto.Atualizacao entrada) {
        referencias.bloquearUsuario(usuarioId);
        Conta conta = referencias.conta(usuarioId, id, false);
        conta.setNome(entrada.nome().trim());
        conta.setTipo(entrada.tipo());
        return saida(contas.save(conta));
    }

    @Transactional
    public void desativar(UUID usuarioId, UUID id) {
        referencias.bloquearUsuario(usuarioId);
        Conta conta = referencias.conta(usuarioId, id, false);
        conta.setAtiva(false);
    }

    private ContaDto.Saida saida(Conta conta) {
        return new ContaDto.Saida(conta.getId(), conta.getUsuario().getId(), conta.getNome(),
                conta.getTipo(), conta.getSaldoInicial(), conta.getAtiva());
    }
}
