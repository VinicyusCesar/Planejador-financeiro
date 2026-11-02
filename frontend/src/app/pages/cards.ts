import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { WorkspaceService } from '../core/workspace.service';
import { EditorsService } from '../core/editors.service';
import { ApiService } from '../core/api.service';
import { ResumoCartao } from '../core/models';
import { resource } from '../core/resource';
import { IconComponent } from '../shared/icon';
import { LoadStateComponent } from '../shared/load-state';
import { MoneyPipe } from '../shared/pipes';

@Component({
  imports: [RouterLink, RouterLinkActive, IconComponent, LoadStateComponent, MoneyPipe],
  template: `<div class="page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">ESCOLHAS DE HOJE, CLAREZA AMANHÃ</p>
        <div class="page-title-mask"><h1>Cartões e faturas</h1></div>
        <p class="muted">Conheça seus limites. Organize os próximos ciclos.</p>
      </div>
      <button type="button" class="button" (click)="editors.card()">
        <app-icon name="plus" />Novo cartão
      </button>
    </div>
    <nav class="subnav" aria-label="Cartões e faturas">
      <a routerLink="/cartoes" routerLinkActive="active">Meus cartões</a
      ><a routerLink="/faturas">Faturas</a>
    </nav>
    <div class="toolbar">
      <span class="subtle">{{ workspace.cards().length }} cartões cadastrados</span
      ><label class="check-control"
        ><input
          type="checkbox"
          [checked]="showInactive()"
          (change)="showInactive.set($any($event.target).checked)"
        />Mostrar inativos</label
      >
    </div>
    <app-load-state
      [loading]="workspace.lookups.loading() || summaries.loading()"
      [error]="summaries.error()"
      (retry)="summaries.reload()"
    />
    @if (!workspace.lookups.loading() && workspace.lookups.data()) {
      <div class="card-grid">
        @for (card of visible(); track card.id) {
          <article class="virtual-card">
            <div class="card-top">
              <h3>{{ card.nome }}</h3>
              <span class="badge" [class.ATIVA]="card.ativo">{{
                card.ativo ? 'Ativo' : 'Inativo'
              }}</span>
            </div>
            <span class="chip" aria-hidden="true"></span>
            <p class="card-digits">
              •••• &nbsp; •••• &nbsp; •••• &nbsp; {{ card.finalCartao || '••••' }}
            </p>
            <div class="credit-limits">
              <div>
                <small>Disponível</small
                ><span class="number">{{ card.limiteDisponivel | money }}</span>
              </div>
              <div>
                <small>Utilizado</small
                ><span class="number">{{ card.limiteUtilizado | money }}</span>
              </div>
            </div>
            <div class="progress" [class.warning]="card.limiteDisponivel < 0">
              <span [style.width.%]="usage(card.limiteUtilizado, card.limite)"></span>
            </div>
            <p class="subtle" style="margin-top:13px;font-size:.64rem;">
              Fecha dia {{ card.diaFechamento }} · Vence dia {{ card.diaVencimento }}
            </p>
            @if (summary(card.id); as info) {
              <div class="list-row" style="margin-top:16px;">
                <div>
                  Fatura atual<small>{{
                    info.faturaAtual
                      ? 'Vence em ' + formatDate(info.faturaAtual.dataVencimento)
                      : 'Nenhuma compra neste ciclo'
                  }}</small>
                </div>
                <span class="number">{{ info.faturaAtual?.saldoPendente | money }}</span>
              </div>
              <p class="subtle" style="font-size:.64rem;margin-top:10px;">
                Próximas faturas: {{ info.totalProximasFaturas | money }}
              </p>
            }
            <div class="card-footer">
              <a class="text-button" routerLink="/faturas" [queryParams]="{ cartaoId: card.id }"
                >Ver faturas<app-icon name="arrow"
              /></a>
              <div class="row-actions">
                <button
                  type="button"
                  class="icon-button plain"
                  [attr.aria-label]="'Editar ' + card.nome"
                  (click)="editors.card(card)"
                >
                  <app-icon name="edit" />
                </button>
                <button
                  type="button"
                  class="icon-button plain"
                  [attr.aria-label]="'Desativar ' + card.nome"
                  [disabled]="!card.ativo"
                  (click)="editors.deactivate('cartoes', card.id, card.nome)"
                >
                  <app-icon name="power" />
                </button>
              </div>
            </div>
          </article>
        }
        <button class="create-card" type="button" (click)="editors.card()">
          <app-icon name="card" /><span>Seu próximo cartão, organizado.</span
          ><span class="subtle">Adicionar cartão</span>
        </button>
      </div>
      <p class="note">
        <app-icon name="info" />Compras no crédito utilizam limite, sem debitar a conta. O saldo
        bancário muda quando você registra o pagamento da fatura.
      </p>
    }
  </div>`,
})
export class CardsPage {
  readonly workspace = inject(WorkspaceService);
  readonly editors = inject(EditorsService);
  private readonly api = inject(ApiService);
  readonly showInactive = signal(false);
  readonly visible = computed(() =>
    this.workspace.cards().filter((card) => this.showInactive() || card.ativo),
  );
  readonly summaries = resource(() => {
    this.workspace.context.revision();
    return this.workspace.context.userId()
      ? this.api.get<ResumoCartao[]>('dashboard/cartoes')
      : null;
  });
  summary(id: string): ResumoCartao | undefined {
    return this.summaries.data()?.find((item) => item.cartaoId === id);
  }
  usage(used: number, limit: number): number {
    return limit > 0 ? Math.min(100, Math.max(0, (used / limit) * 100)) : used > 0 ? 100 : 0;
  }
  formatDate(date: string): string {
    return new Intl.DateTimeFormat('pt-BR', {
      day: '2-digit',
      month: 'short',
      timeZone: 'UTC',
    }).format(new Date(date + 'T12:00:00Z'));
  }
}
