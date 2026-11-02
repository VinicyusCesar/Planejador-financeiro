import { inject, Injectable } from '@angular/core';
import { ApiService } from './api.service';
import { DialogService, Field } from './dialog.service';
import { WorkspaceService } from './workspace.service';
import {
  Cartao,
  Categoria,
  Conta,
  Fatura,
  Meta,
  Usuario,
  TIPOS_CONTA,
  TIPOS_CATEGORIA,
  SITUACOES_META,
  SITUACOES,
} from './models';
import { cents, moneyDecimal, moneyEdit, todayIso } from './format';

@Injectable({ providedIn: 'root' })
export class EditorsService {
  private readonly api = inject(ApiService);
  private readonly dialog = inject(DialogService);
  private readonly workspace = inject(WorkspaceService);
  user(user?: Usuario): void {
    this.dialog.open({
      title: user ? 'Seu perfil, do seu jeito' : 'Vamos começar?',
      description: user
        ? 'Atualize como você aparece no planejador.'
        : 'Crie um perfil para organizar sua vida financeira.',
      fields: [
        {
          key: 'nome',
          label: 'Como você se chama?',
          required: true,
          maxLength: 100,
          value: user?.nome,
          wide: true,
        },
        {
          key: 'email',
          label: 'Email',
          kind: 'email',
          required: true,
          maxLength: 150,
          value: user?.email,
          wide: true,
        },
      ],
      submitLabel: user ? 'Salvar perfil' : 'Criar meu perfil',
      success: user ? 'Perfil atualizado.' : 'Seu espaço está pronto.',
      save: (values) =>
        user
          ? this.api.put<Usuario>('usuarios/' + user.id, values)
          : this.api.post<Usuario>('usuarios', values),
      saved: (result) => this.workspace.savedUser(result as Usuario, !user),
    });
  }
  account(account?: Conta): void {
    const fields: Field[] = [
      {
        key: 'nome',
        label: 'Nome da conta',
        required: true,
        maxLength: 100,
        value: account?.nome,
        wide: true,
      },
      {
        key: 'tipo',
        label: 'Tipo de conta',
        kind: 'select',
        required: true,
        options: TIPOS_CONTA,
        value: account?.tipo ?? 'CORRENTE',
      },
    ];
    if (!account)
      fields.push({
        key: 'saldoInicial',
        label: 'Saldo inicial',
        kind: 'money',
        required: true,
        zero: true,
        signed: true,
        value: '0,00',
        hint: 'O saldo atual será calculado pelas suas movimentações.',
      });
    this.dialog.open({
      title: account ? 'Editar conta' : 'Uma nova conta',
      fields,
      description: 'Seu dinheiro merece um lugar organizado.',
      success: account ? 'Conta atualizada.' : 'Conta criada.',
      save: (values) =>
        account
          ? this.api.put('contas/' + account.id, { nome: values['nome'], tipo: values['tipo'] })
          : this.api.post('contas', {
              nome: values['nome'],
              tipo: values['tipo'],
              saldoInicial: moneyDecimal(values['saldoInicial']),
            }),
    });
  }
  category(category?: Categoria): void {
    this.dialog.open({
      title: category ? 'Editar categoria' : 'Nova categoria',
      description: 'Dê um nome ao que faz parte da sua vida.',
      fields: [
        {
          key: 'nome',
          label: 'Nome',
          required: true,
          maxLength: 80,
          value: category?.nome,
          wide: true,
        },
        {
          key: 'tipo',
          label: 'Usar para',
          kind: 'select',
          required: true,
          options: TIPOS_CATEGORIA,
          value: category?.tipo ?? 'DESPESA',
          wide: true,
        },
      ],
      success: category ? 'Categoria atualizada.' : 'Categoria criada.',
      save: (values) =>
        category
          ? this.api.put('categorias/' + category.id, values)
          : this.api.post('categorias', values),
    });
  }
  card(card?: Cartao): void {
    this.dialog.open({
      title: card ? 'Editar cartão' : 'Seu próximo cartão',
      description: 'Acompanhe compras, faturas e o limite disponível.',
      fields: [
        {
          key: 'nome',
          label: 'Nome do cartão',
          required: true,
          maxLength: 100,
          value: card?.nome,
          wide: true,
        },
        {
          key: 'finalCartao',
          label: 'Últimos 4 dígitos',
          maxLength: 4,
          pattern: '[0-9]{4}',
          value: card?.finalCartao,
        },
        {
          key: 'limite',
          label: 'Limite do cartão',
          kind: 'money',
          required: true,
          zero: true,
          value: card ? moneyEdit(card.limite) : '',
        },
        {
          key: 'diaFechamento',
          label: 'Dia do fechamento',
          kind: 'number',
          required: true,
          min: 1,
          max: 31,
          value: card?.diaFechamento ?? 25,
        },
        {
          key: 'diaVencimento',
          label: 'Dia do vencimento',
          kind: 'number',
          required: true,
          min: 1,
          max: 31,
          value: card?.diaVencimento ?? 5,
        },
        {
          key: 'contaPagamentoId',
          label: 'Conta padrão para pagamento',
          kind: 'select',
          options: this.workspace
            .activeAccounts()
            .map((item) => ({ value: item.id, label: item.nome })),
          value: card?.contaPagamentoId,
          wide: true,
          hint: card
            ? 'Cartões com faturas mantêm o calendário original de fechamento e vencimento.'
            : 'Opcional. Você também pode escolher a conta ao pagar.',
        },
      ],
      success: card ? 'Cartão atualizado.' : 'Cartão cadastrado.',
      save: (values) => {
        const payload = {
          ...values,
          finalCartao: values['finalCartao'] || null,
          limite: moneyDecimal(values['limite']),
          contaPagamentoId: values['contaPagamentoId'] || null,
          diaFechamento: Number(values['diaFechamento']),
          diaVencimento: Number(values['diaVencimento']),
        };
        return card
          ? this.api.put('cartoes/' + card.id, payload)
          : this.api.post('cartoes', payload);
      },
    });
  }
  transfer(): void {
    this.dialog.open({
      title: 'Entre suas contas',
      description: 'Mova seu dinheiro sem transformar uma transferência em gasto.',
      fields: [
        {
          key: 'contaOrigemId',
          label: 'De qual conta?',
          kind: 'select',
          required: true,
          options: this.workspace
            .activeAccounts()
            .map((item) => ({ value: item.id, label: item.nome })),
        },
        {
          key: 'contaDestinoId',
          label: 'Para qual conta?',
          kind: 'select',
          required: true,
          options: this.workspace
            .activeAccounts()
            .map((item) => ({ value: item.id, label: item.nome })),
        },
        { key: 'valor', label: 'Valor', kind: 'money', required: true },
        {
          key: 'dataTransferencia',
          label: 'Data',
          kind: 'date',
          required: true,
          value: todayIso(),
        },
        {
          key: 'situacao',
          label: 'Situação',
          kind: 'select',
          required: true,
          options: SITUACOES,
          value: 'CONFIRMADA',
          wide: true,
        },
      ],
      success: 'Transferência registrada.',
      submitLabel: 'Registrar transferência',
      validate: (values) =>
        values['contaOrigemId'] === values['contaDestinoId']
          ? 'Escolha contas diferentes para a origem e o destino.'
          : values['situacao'] === 'CONFIRMADA' && values['dataTransferencia'] > todayIso()
            ? 'Transferências futuras devem ficar pendentes.'
            : null,
      save: (values) =>
        this.api.post('transferencias', { ...values, valor: moneyDecimal(values['valor']) }),
    });
  }
  goal(meta?: Meta): void {
    this.dialog.open({
      title: meta ? 'Ajustar seu objetivo' : 'O que você quer conquistar?',
      description: 'Transforme seus planos em um destino claro.',
      fields: [
        {
          key: 'nome',
          label: 'Seu objetivo',
          required: true,
          maxLength: 150,
          value: meta?.nome,
          wide: true,
        },
        {
          key: 'valorObjetivo',
          label: 'Valor do objetivo',
          kind: 'money',
          required: true,
          value: meta ? moneyEdit(meta.valorObjetivo) : '',
          wide: true,
        },
        {
          key: 'dataInicio',
          label: 'Data de início',
          kind: 'date',
          required: true,
          value: meta?.dataInicio ?? todayIso(),
        },
        { key: 'dataLimite', label: 'Prazo, se houver', kind: 'date', value: meta?.dataLimite },
        {
          key: 'situacao',
          label: 'Situação declarada',
          kind: 'select',
          required: true,
          value: meta?.situacao ?? 'ATIVA',
          options: SITUACOES_META,
          wide: true,
        },
      ],
      success: meta ? 'Objetivo atualizado.' : 'Novo objetivo criado.',
      validate: (values) =>
        values['dataLimite'] && values['dataLimite'] < values['dataInicio']
          ? 'O prazo deve ser posterior ou igual à data de início.'
          : null,
      save: (values) => {
        const payload = {
          ...values,
          valorObjetivo: moneyDecimal(values['valorObjetivo']),
          dataLimite: values['dataLimite'] || null,
        };
        return meta ? this.api.put('metas/' + meta.id, payload) : this.api.post('metas', payload);
      },
    });
  }
  payment(fatura: Fatura): void {
    this.dialog.open({
      title: 'Pagar fatura',
      description:
        'Saldo pendente: R$ ' + moneyEdit(fatura.saldoPendente) + '. O pagamento pode ser parcial.',
      fields: [
        {
          key: 'contaId',
          label: 'Conta de pagamento',
          kind: 'select',
          required: true,
          options: this.workspace
            .activeAccounts()
            .map((item) => ({ value: item.id, label: item.nome })),
          value:
            this.workspace.cards().find((item) => item.id === fatura.cartaoId)?.contaPagamentoId ??
            '',
          wide: true,
        },
        {
          key: 'valor',
          label: 'Quanto você vai pagar?',
          kind: 'money',
          required: true,
          value: moneyEdit(fatura.saldoPendente),
        },
        {
          key: 'dataPagamento',
          label: 'Data do pagamento',
          kind: 'date',
          required: true,
          maxDate: todayIso(),
          value: todayIso(),
        },
      ],
      success: 'Pagamento registrado. Sua fatura e seu saldo foram atualizados.',
      submitLabel: 'Confirmar pagamento',
      validate: (values) =>
        cents(moneyDecimal(values['valor'])!) > cents(fatura.saldoPendente.toFixed(2))
          ? 'O pagamento não pode ultrapassar o saldo pendente.'
          : null,
      save: (values) =>
        this.api.post('faturas/' + fatura.id + '/pagamentos', {
          ...values,
          valor: moneyDecimal(values['valor']),
        }),
    });
  }
  deactivate(kind: 'contas' | 'cartoes', id: string, name: string): void {
    this.dialog.open({
      title: 'Desativar ' + name + '?',
      description:
        'O histórico será preservado. Novas movimentações não poderão usar este registro.',
      fields: [],
      danger: true,
      success: 'Registro desativado.',
      submitLabel: 'Desativar',
      save: () => this.api.patch(kind + '/' + id + '/desativacao'),
    });
  }
  deleteCategory(category: Categoria): void {
    this.dialog.open({
      title: 'Excluir ' + category.nome + '?',
      description: 'Categorias com movimentações ou orçamento serão preservadas pelo sistema.',
      fields: [],
      danger: true,
      success: 'Categoria excluída.',
      submitLabel: 'Excluir categoria',
      save: () => this.api.delete('categorias/' + category.id),
    });
  }
  cancel(kind: 'transacoes' | 'transferencias', id: string): void {
    this.dialog.open({
      title: 'Cancelar este movimento?',
      description:
        'O registro será mantido no histórico. Seus efeitos financeiros serão removidos quando a operação for permitida.',
      fields: [],
      danger: true,
      success: 'Movimento cancelado.',
      submitLabel: 'Confirmar cancelamento',
      save: () => this.api.post(kind + '/' + id + '/cancelamento'),
    });
  }
  confirmTransfer(id: string): void {
    this.dialog.open({
      title: 'Confirmar transferência?',
      description: 'O valor passará a movimentar os saldos das duas contas.',
      fields: [],
      success: 'Transferência confirmada.',
      submitLabel: 'Confirmar transferência',
      save: () => this.api.post('transferencias/' + id + '/confirmacao'),
    });
  }
}
