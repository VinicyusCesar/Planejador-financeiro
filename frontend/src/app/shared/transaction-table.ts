import { Component, inject, input, output } from '@angular/core';
import { Transacao } from '../core/models';
import { WorkspaceService } from '../core/workspace.service';
import { IconComponent } from './icon';
import { MoneyPipe, BrDatePipe, LabelPipe } from './pipes';

@Component({
  selector: 'app-transaction-table',
  imports: [IconComponent, MoneyPipe, BrDatePipe, LabelPipe],
  template: `<div class="table-scroll">
    <table>
      <thead>
        <tr>
          <th>Movimento</th>
          <th class="mobile-hide">Categoria</th>
          <th class="mobile-hide">Conta / cartão</th>
          <th>Data</th>
          <th class="align-right">Valor</th>
          <th class="mobile-hide">Situação</th>
          @if (actions()) {
            <th><span class="sr-only">Ações</span></th>
          }
        </tr>
      </thead>
      <tbody>
        @for (transaction of items(); track transaction.id) {
          <tr>
            <td>
              <div class="description-cell">
                <span class="transaction-symbol" [class.expense]="transaction.tipo === 'DESPESA'"
                  ><app-icon
                    [name]="
                      transaction.tipo === 'RECEITA'
                        ? 'up'
                        : transaction.meioPagamento === 'CREDITO'
                          ? 'card'
                          : 'down'
                    "
                /></span>
                <div>
                  <button type="button" class="row-link" (click)="inspected.emit(transaction)">
                    {{ transaction.descricao }}</button
                  ><small
                    >{{ transaction.meioPagamento | label }}
                    @if (transaction.quantidadeParcelas > 1) {
                      · {{ transaction.quantidadeParcelas }} parcelas
                    }
                  </small>
                </div>
              </div>
            </td>
            <td class="mobile-hide">
              <span class="badge">{{ workspace.categoryName(transaction.categoriaId) }}</span>
            </td>
            <td class="mobile-hide">
              {{
                transaction.cartaoId
                  ? workspace.cardName(transaction.cartaoId)
                  : workspace.accountName(transaction.contaId)
              }}
            </td>
            <td>{{ transaction.dataTransacao | brDate }}</td>
            <td class="amount align-right" [class.green]="transaction.tipo === 'RECEITA'">
              {{ transaction.tipo === 'RECEITA' ? '+' : '−' }} {{ transaction.valor | money }}
            </td>
            <td class="mobile-hide">
              <span class="badge" [class]="transaction.situacao">{{
                transaction.situacao | label
              }}</span>
            </td>
            @if (actions()) {
              <td>
                <div class="row-actions">
                  <button
                    class="icon-button plain"
                    type="button"
                    [attr.aria-label]="'Editar ' + transaction.descricao"
                    [disabled]="transaction.situacao === 'CANCELADA'"
                    (click)="edited.emit(transaction)"
                  >
                    <app-icon name="edit" />
                  </button>
                  <button
                    class="icon-button plain"
                    type="button"
                    [attr.aria-label]="'Cancelar ' + transaction.descricao"
                    [disabled]="transaction.situacao === 'CANCELADA'"
                    (click)="cancelled.emit(transaction)"
                  >
                    <app-icon name="close" />
                  </button>
                </div>
              </td>
            }
          </tr>
        }
      </tbody>
    </table>
  </div>`,
})
export class TransactionTableComponent {
  readonly items = input.required<Transacao[]>();
  readonly actions = input(false);
  readonly inspected = output<Transacao>();
  readonly edited = output<Transacao>();
  readonly cancelled = output<Transacao>();
  readonly workspace = inject(WorkspaceService);
}
