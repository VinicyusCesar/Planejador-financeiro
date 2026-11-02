import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { WorkspaceService } from '../core/workspace.service';
import { EditorsService } from '../core/editors.service';
import { Pagina, Transferencia, Query } from '../core/models';
import { monthBounds, todayIso } from '../core/format';
import { resource } from '../core/resource';
import { IconComponent } from '../shared/icon';
import { LoadStateComponent } from '../shared/load-state';
import { MoneyPipe, BrDatePipe, LabelPipe } from '../shared/pipes';

@Component({
  imports: [
    ReactiveFormsModule,
    IconComponent,
    LoadStateComponent,
    MoneyPipe,
    BrDatePipe,
    LabelPipe,
  ],
  template: `<div class="page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">O MESMO DINHEIRO, OUTRO LUGAR</p>
        <div class="page-title-mask"><h1>Transferências</h1></div>
        <p class="muted">
          Organize os valores entre suas contas, sem duplicar receitas ou despesas.
        </p>
      </div>
      <button type="button" class="button" (click)="editors.transfer()">
        <app-icon name="transfer" />Nova transferência
      </button>
    </div>
    <form class="filter-panel glass" [formGroup]="form" (ngSubmit)="apply()">
      <div class="field">
        <label for="transfer-start">De</label
        ><input id="transfer-start" type="date" formControlName="inicio" />
      </div>
      <div class="field">
        <label for="transfer-end">Até</label
        ><input id="transfer-end" type="date" formControlName="fim" />
      </div>
      <div class="filter-buttons">
        <button type="submit" class="button small">Aplicar período</button>
      </div>
    </form>
    @if (workspace.activeAccounts().length < 2) {
      <p class="note warning">
        <app-icon name="info" />Cadastre pelo menos duas contas ativas para registrar uma
        transferência.
      </p>
    }
    <app-load-state [loading]="data.loading()" [error]="data.error()" (retry)="data.reload()" />
    @if (data.data(); as result) {
      <section class="glass table-panel">
        @if (result.conteudo.length) {
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Origem → destino</th>
                  <th>Data</th>
                  <th class="align-right">Valor</th>
                  <th>Situação</th>
                  <th class="align-right">Ações</th>
                </tr>
              </thead>
              <tbody>
                @for (transfer of result.conteudo; track transfer.id) {
                  <tr>
                    <td>
                      <div class="description-cell">
                        <span class="transaction-symbol"><app-icon name="transfer" /></span>
                        <div>
                          {{ workspace.accountName(transfer.contaOrigemId)
                          }}<small>Para {{ workspace.accountName(transfer.contaDestinoId) }}</small>
                        </div>
                      </div>
                    </td>
                    <td>{{ transfer.dataTransferencia | brDate }}</td>
                    <td class="amount align-right">{{ transfer.valor | money }}</td>
                    <td>
                      <span class="badge" [class]="transfer.situacao">{{
                        transfer.situacao | label
                      }}</span>
                    </td>
                    <td>
                      <div class="row-actions">
                        @if (transfer.situacao === 'PENDENTE') {
                          <button
                            type="button"
                            class="icon-button"
                            aria-label="Confirmar transferência"
                            [disabled]="transfer.dataTransferencia > today"
                            [title]="
                              transfer.dataTransferencia > today
                                ? 'Aguarde a data da transferência'
                                : 'Confirmar transferência'
                            "
                            (click)="editors.confirmTransfer(transfer.id)"
                          >
                            <app-icon name="check" />
                          </button>
                        }
                        <button
                          type="button"
                          class="icon-button"
                          aria-label="Cancelar transferência"
                          [disabled]="transfer.situacao === 'CANCELADA'"
                          (click)="editors.cancel('transferencias', transfer.id)"
                        >
                          <app-icon name="close" />
                        </button>
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        } @else {
          <div class="panel-empty">
            <span class="empty-icon"><app-icon name="transfer" /></span>
            <h2>Cada valor, no lugar certo.</h2>
            <p>As transferências deste período vão aparecer aqui.</p>
            <button type="button" class="button secondary" (click)="editors.transfer()">
              Registrar transferência
            </button>
          </div>
        }
        <div class="paginator">
          <span>{{ result.totalElementos }} transferências</span>
          <div class="paginator-controls">
            <button
              type="button"
              class="icon-button"
              aria-label="Página anterior"
              [disabled]="page() === 0"
              (click)="page.set(page() - 1)"
            >
              <app-icon name="left" /></button
            ><span
              >{{ result.totalPaginas ? result.pagina + 1 : 0 }} de {{ result.totalPaginas }}</span
            ><button
              type="button"
              class="icon-button"
              aria-label="Próxima página"
              [disabled]="page() + 1 >= result.totalPaginas"
              (click)="page.set(page() + 1)"
            >
              <app-icon name="right" />
            </button>
          </div>
        </div>
      </section>
    }
  </div>`,
})
export class TransfersPage {
  private readonly api = inject(ApiService);
  readonly workspace = inject(WorkspaceService);
  readonly editors = inject(EditorsService);
  private readonly fb = inject(FormBuilder);
  readonly today = todayIso();
  readonly page = signal(0);
  readonly filters = signal<Query>(monthBounds(todayIso().slice(0, 7)));
  readonly form = this.fb.nonNullable.group(monthBounds(todayIso().slice(0, 7)));
  readonly data = resource(() => {
    this.workspace.context.revision();
    return this.workspace.context.userId()
      ? this.api.get<Pagina<Transferencia>>('transferencias', {
          ...this.filters(),
          pagina: this.page(),
          tamanho: 20,
        })
      : null;
  });
  apply(): void {
    this.page.set(0);
    this.filters.set(this.form.getRawValue());
  }
}
