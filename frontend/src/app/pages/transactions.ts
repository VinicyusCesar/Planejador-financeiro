import { Component, computed, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';
import { ApiService } from '../core/api.service';
import { WorkspaceService } from '../core/workspace.service';
import { EditorsService } from '../core/editors.service';
import { TransactionEditorService } from '../core/transaction-editor.service';
import { resource } from '../core/resource';
import { Pagina, Parcela, Transacao, Query } from '../core/models';
import { monthBounds, todayIso } from '../core/format';
import { IconComponent } from '../shared/icon';
import { LoadStateComponent } from '../shared/load-state';
import { TransactionTableComponent } from '../shared/transaction-table';
import { ModalComponent } from '../shared/modal';
import { MoneyPipe, BrDatePipe, LabelPipe } from '../shared/pipes';

@Component({
  imports: [
    ReactiveFormsModule,
    RouterLink,
    IconComponent,
    LoadStateComponent,
    TransactionTableComponent,
    ModalComponent,
    MoneyPipe,
    BrDatePipe,
    LabelPipe,
  ],
  templateUrl: './transactions.html',
})
export class TransactionsPage {
  private readonly api = inject(ApiService);
  readonly workspace = inject(WorkspaceService);
  readonly editors = inject(EditorsService);
  readonly editor = inject(TransactionEditorService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  readonly filtersOpen = signal(false);
  readonly search = signal('');
  readonly tab = signal('TODAS');
  readonly page = signal(0);
  readonly filters = signal<Query>(monthBounds(todayIso().slice(0, 7)));
  readonly detailId = signal<string | null>(null);
  readonly form = this.fb.nonNullable.group({
    ...monthBounds(todayIso().slice(0, 7)),
    tipo: '',
    contaId: '',
    cartaoId: '',
    categoriaId: '',
    situacao: '',
  });
  readonly data = resource(() => {
    this.workspace.context.revision();
    return this.workspace.context.userId()
      ? this.api.get<Pagina<Transacao>>('transacoes', {
          ...this.filters(),
          pagina: this.page(),
          tamanho: 20,
        })
      : null;
  });
  readonly visible = computed(() => {
    const query = this.search()
      .trim()
      .toLocaleLowerCase('pt-BR')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '');
    return (this.data.data()?.conteudo ?? []).filter(
      (item) =>
        !query ||
        (item.descricao + ' ' + this.workspace.categoryName(item.categoriaId))
          .toLocaleLowerCase('pt-BR')
          .normalize('NFD')
          .replace(/[\u0300-\u036f]/g, '')
          .includes(query),
    );
  });
  readonly detail = resource(() => {
    this.workspace.context.revision();
    const id = this.detailId();
    return this.workspace.context.userId() && id
      ? forkJoin({
          transaction: this.api.get<Transacao>('transacoes/' + id),
          parcelas: this.api.get<Parcela[]>('transacoes/' + id + '/parcelas'),
        })
      : null;
  });
  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const inicio = params.get('inicio');
      const fim = params.get('fim');
      if (inicio) this.form.controls.inicio.setValue(inicio);
      if (fim) this.form.controls.fim.setValue(fim);
      this.apply();
      if (params.get('abrir')) this.detailId.set(params.get('abrir'));
    });
  }
  apply(): void {
    this.page.set(0);
    this.filters.set(this.form.getRawValue());
  }
  reset(): void {
    this.form.reset({
      ...monthBounds(todayIso().slice(0, 7)),
      tipo: '',
      contaId: '',
      cartaoId: '',
      categoriaId: '',
      situacao: '',
    });
    this.tab.set('TODAS');
    this.apply();
  }
  chooseTab(value: string): void {
    this.tab.set(value);
    this.form.patchValue({
      tipo: value === 'RECEITA' || value === 'DESPESA' ? value : '',
      situacao: value === 'PENDENTE' ? 'PENDENTE' : '',
    });
    this.apply();
  }
  inspect(transaction: Transacao): void {
    this.detailId.set(transaction.id);
  }
  edit(transaction: Transacao): void {
    this.detailId.set(null);
    this.editor.edit(transaction);
  }
  cancel(transaction: Transacao): void {
    this.detailId.set(null);
    this.editors.cancel('transacoes', transaction.id);
  }
}
