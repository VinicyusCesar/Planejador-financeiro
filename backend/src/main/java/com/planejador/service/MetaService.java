package com.planejador.service;

import com.planejador.dto.MetaDto;
import com.planejador.enums.SituacaoMeta;
import com.planejador.exception.ApiException;
import com.planejador.model.MetaFinanceira;
import com.planejador.repository.MetaFinanceiraRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MetaService {
    private final MetaFinanceiraRepository metas;
    private final ReferenciaService referencias;
    private final CalculosFinanceirosService calculos;

    public MetaService(MetaFinanceiraRepository metas, ReferenciaService referencias, CalculosFinanceirosService calculos) {
        this.metas = metas;
        this.referencias = referencias;
        this.calculos = calculos;
    }

    public List<MetaDto.Saida> listar(UUID usuarioId) {
        referencias.usuario(usuarioId);
        return metas.findByUsuarioIdOrderByDataInicioDesc(usuarioId).stream().map(this::saida).toList();
    }

    public MetaDto.Saida consultar(UUID usuarioId, UUID id) {
        return saida(obter(usuarioId, id));
    }

    @Transactional
    public MetaDto.Saida criar(UUID usuarioId, MetaDto.Entrada entrada) {
        MetaFinanceira meta = new MetaFinanceira();
        meta.setUsuario(referencias.bloquearUsuario(usuarioId));
        preencher(meta, entrada);
        return saida(metas.save(meta));
    }

    @Transactional
    public MetaDto.Saida atualizar(UUID usuarioId, UUID id, MetaDto.Entrada entrada) {
        referencias.bloquearUsuario(usuarioId);
        MetaFinanceira meta = obter(usuarioId, id);
        preencher(meta, entrada);
        return saida(meta);
    }

    private MetaFinanceira obter(UUID usuarioId, UUID id) {
        return metas.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> ApiException.naoEncontrado("Meta"));
    }

    private void preencher(MetaFinanceira meta, MetaDto.Entrada entrada) {
        calculos.periodo(entrada.dataInicio(), entrada.dataLimite());
        meta.setNome(entrada.nome().trim());
        meta.setValorObjetivo(calculos.moeda(entrada.valorObjetivo(), false));
        meta.setDataInicio(entrada.dataInicio());
        meta.setDataLimite(entrada.dataLimite());
        meta.setSituacao(entrada.situacao() == null ? SituacaoMeta.ATIVA : entrada.situacao());
    }

    private MetaDto.Saida saida(MetaFinanceira meta) {
        return new MetaDto.Saida(meta.getId(), meta.getUsuario().getId(), meta.getNome(), meta.getValorObjetivo(),
                meta.getDataInicio(), meta.getDataLimite(), meta.getSituacao(), false, null, null,
                "O schema não vincula aportes ou saldo acumulado à meta. A situação é informada pelo usuário.");
    }
}
