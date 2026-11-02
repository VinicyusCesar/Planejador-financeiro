import { Component, computed, inject, signal } from '@angular/core';
import { forkJoin, of } from 'rxjs';
import { ApiService } from '../core/api.service';
import { WorkspaceService } from '../core/workspace.service';
import { EditorsService } from '../core/editors.service';
import { resource } from '../core/resource';
import { Saldo } from '../core/models';
import { sumMoney } from '../core/format';
import { IconComponent } from '../shared/icon';
import { LoadStateComponent } from '../shared/load-state';
import { MoneyPipe, LabelPipe } from '../shared/pipes';

@Component({
  imports: [IconComponent, LoadStateComponent, MoneyPipe, LabelPipe],
  template: `<div class="page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">SEU DINHEIRO, BEM ORGANIZADO</p>
        <div class="page-title-mask"><h1>Minhas contas</h1></div>
        <p class="muted">Cada conta tem seu lugar. O saldo se organiza com seus movimentos.</p>
      </div>
      <button class="button" type="button" (click)="editors.account()">
        <app-icon name="plus" />Nova conta
      </button>
    </div>
    <app-load-state
      [loading]="workspace.lookups.loading() || balances.loading()"
      [error]="balances.error()"
      (retry)="balances.reload()"
    />
    @if (balances.data(); as data) {
      <div class="glass summary-inline">
        <div>
          <p>Saldo em contas ativas</p>
          <span class="number">{{ total() | money }}</span>
        </div>
        <label class="check-control"
          ><input
            type="checkbox"
            [checked]="showInactive()"
            (change)="showInactive.set($any($event.target).checked)"
          />Mostrar contas inativas</label
        >
      </div>
      <div class="card-grid">
        @for (account of visible(); track account.id) {
          <article class="glass account-card">
            <div class="card-top">
              <div class="card-title">
                <span class="stat-icon"><app-icon name="wallet" /></span>
                <div>
                  <h3>{{ account.nome }}</h3>
                  <p>{{ account.tipo | label }}</p>
                </div>
              </div>
              <span class="badge" [class.ATIVA]="account.ativa">{{
                account.ativa ? 'Ativa' : 'Inativa'
              }}</span>
            </div>
            <p class="subtle">Saldo atual</p>
            <p class="number">{{ balanceOf(account.id) | money }}</p>
            <p class="subtle">Inicial: {{ account.saldoInicial | money }}</p>
            <div class="card-footer">
              <button class="text-button" type="button" (click)="editors.account(account)">
                <app-icon name="edit" />Editar conta
              </button>
              <button
                class="icon-button plain"
                type="button"
                [attr.aria-label]="'Desativar ' + account.nome"
                [disabled]="!account.ativa"
                (click)="editors.deactivate('contas', account.id, account.nome)"
              >
                <app-icon name="power" />
              </button>
            </div>
          </article>
        }
        <button class="create-card" type="button" (click)="editors.account()">
          <app-icon name="plus" /><span>Um lugar para outra conta.</span
          ><span class="subtle">Adicionar conta</span>
        </button>
      </div>
      @if (workspace.accounts().length === 0) {
        <p class="note">
          <app-icon name="info" />Comece cadastrando uma conta e seu saldo inicial. As próximas
          movimentações atualizam seu saldo automaticamente.
        </p>
      }
    }
  </div>`,
})
export class AccountsPage {
  readonly workspace = inject(WorkspaceService);
  readonly editors = inject(EditorsService);
  private readonly api = inject(ApiService);
  readonly showInactive = signal(false);
  readonly visible = computed(() =>
    this.workspace.accounts().filter((account) => this.showInactive() || account.ativa),
  );
  readonly balances = resource(() => {
    this.workspace.context.revision();
    if (!this.workspace.context.userId() || !this.workspace.lookups.data()) return null;
    const accounts = this.workspace.accounts();
    return accounts.length
      ? forkJoin(accounts.map((account) => this.api.get<Saldo>('contas/' + account.id + '/saldo')))
      : of<Saldo[]>([]);
  });
  readonly total = computed(() =>
    sumMoney(
      (this.balances.data() ?? [])
        .filter((item) =>
          this.workspace.activeAccounts().some((account) => account.id === item.contaId),
        )
        .map((item) => item.saldoAtual),
    ),
  );
  balanceOf(id: string): number | null {
    return this.balances.data()?.find((item) => item.contaId === id)?.saldoAtual ?? null;
  }
}
