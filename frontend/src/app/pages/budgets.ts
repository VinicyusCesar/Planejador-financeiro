import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { WorkspaceService } from '../core/workspace.service';
import { Orcamento } from '../core/models';
import { monthLabel, todayIso } from '../core/format';
import { resource } from '../core/resource';
import { IconComponent } from '../shared/icon';
import { LoadStateComponent } from '../shared/load-state';
import { MoneyPipe } from '../shared/pipes';
import { MonthPickerComponent } from '../shared/month-picker';
import { BudgetDialogComponent } from '../shared/budget-dialog';

@Component({
  imports: [
    RouterLink,
    IconComponent,
    LoadStateComponent,
    MoneyPipe,
    MonthPickerComponent,
    BudgetDialogComponent,
  ],
  template: `<div class="page">
      <div class="page-heading">
        <div>
          <p class="eyebrow">UM MÊS COM MAIS INTENÇÃO</p>
          <div class="page-title-mask"><h1>Planeje suas escolhas</h1></div>
          <p class="muted">
            Defina o que faz sentido para você e acompanhe o previsto e o realizado.
          </p>
        </div>
        <div class="page-actions">
          <app-month-picker [value]="month()" (changed)="month.set($event)" /><button
            class="button"
            type="button"
            (click)="editing.set(true)"
          >
            <app-icon [name]="current() ? 'edit' : 'plus'" />{{
              current() ? 'Ajustar plano' : 'Criar orçamento'
            }}
          </button>
        </div>
      </div>
      <app-load-state [loading]="data.loading()" [error]="data.error()" (retry)="data.reload()" />
      @if (current(); as budget) {
        <section class="stat-grid">
          <article class="stat glass">
            <div class="stat-top">
              <span>Planejado</span><span class="stat-icon"><app-icon name="target" /></span>
            </div>
            <p class="number">{{ budget.totalPrevisto | money }}</p>
            <p class="stat-caption">{{ monthTitle() }}</p>
          </article>
          <article class="stat glass">
            <div class="stat-top">
              <span>Realizado</span><span class="stat-icon expense"><app-icon name="down" /></span>
            </div>
            <p class="number">{{ budget.totalRealizado | money }}</p>
            <p class="stat-caption">Despesas confirmadas do mês</p>
          </article>
          <article class="stat glass">
            <div class="stat-top">
              <span>Diferença</span><span class="stat-icon neutral"><app-icon name="chart" /></span>
            </div>
            <p class="number" [class.red]="budget.diferenca < 0">{{ budget.diferenca | money }}</p>
            <p class="stat-caption">
              {{ budget.diferenca < 0 ? 'Acima da sua previsão' : 'Espaço dentro da sua previsão' }}
            </p>
          </article>
        </section>
        <section class="glass panel">
          <div class="section-top">
            <div>
              <h2>Cada categoria, uma direção</h2>
              <p>O consumo entra pela data e pelo valor total da compra.</p>
            </div>
            <app-icon name="chart" />
          </div>
          @for (item of budget.itens; track item.id) {
            <div class="budget-row">
              <div class="budget-row-top">
                <span>{{ item.categoria }}</span
                ><span class="number" [class.red]="item.diferenca < 0"
                  >{{ item.realizado | money }}
                  <span class="subtle">de {{ item.previsto | money }}</span></span
                >
              </div>
              <div class="progress" [class.warning]="item.realizado > item.previsto">
                <span [style.width.%]="percent(item.realizado, item.previsto)"></span>
              </div>
              <div class="budget-values">
                <span>{{
                  item.previsto === 0 ? 'Sem valor previsto' : 'Planejado para o mês'
                }}</span
                ><span
                  >{{ item.diferenca < 0 ? 'Acima em ' : 'Disponível na previsão: '
                  }}{{ (item.diferenca < 0 ? -item.diferenca : item.diferenca) | money }}</span
                >
              </div>
            </div>
          }
          @if (!budget.itens.length) {
            <div class="panel-empty">
              <p>
                Seu orçamento ainda não tem categorias. Adicione previsões para acompanhar cada
                parte do mês.
              </p>
              <button class="button secondary" type="button" (click)="editing.set(true)">
                Definir categorias
              </button>
            </div>
          }
          @if (budget.realizadoSemPrevisao > 0) {
            <p class="note warning">
              <app-icon name="info" />{{ budget.realizadoSemPrevisao | money }} foram gastos em
              categorias sem previsão neste orçamento.
            </p>
          }
          @if (budget.metaEconomia !== null) {
            <div class="glass summary-inline">
              <div>
                <p>Intenção de economia</p>
                <span class="number">{{ budget.metaEconomia | money }}</span>
              </div>
              <span class="subtle">Meta declarada para este mês</span>
            </div>
          }
        </section>
      } @else if (!data.loading() && !data.error() && data.data()) {
        <section class="glass panel-empty" style="max-width:none;">
          <span class="empty-icon"><app-icon name="chart" /></span>
          <h2>Seu mês pode ter uma direção.</h2>
          <p>
            Ainda não há orçamento para {{ monthTitle() }}. Escolha quanto faz sentido gastar em
            cada categoria.
          </p>
          <button class="button" type="button" (click)="editing.set(true)">
            Planejar este mês
          </button>
        </section>
      }
      <p class="note">
        <app-icon name="info" />Compras no crédito entram no mês da compra. Pagar uma fatura não
        repete essas despesas. <a routerLink="/">Ver visão geral</a>
      </p>
    </div>
    @if (editing()) {
      <app-budget-dialog [budget]="current()" [month]="month()" (closed)="editing.set(false)" />
    }`,
})
export class BudgetsPage {
  private readonly api = inject(ApiService);
  readonly workspace = inject(WorkspaceService);
  readonly month = signal(todayIso().slice(0, 7));
  readonly editing = signal(false);
  readonly monthTitle = computed(() => monthLabel(this.month()));
  readonly data = resource(() => {
    this.workspace.context.revision();
    return this.workspace.context.userId() ? this.api.get<Orcamento[]>('orcamentos') : null;
  });
  readonly current = computed(() => {
    const [ano, mes] = this.month().split('-').map(Number);
    return this.data.data()?.find((item) => item.ano === ano && item.mes === mes) ?? null;
  });
  percent(realizado: number, previsto: number): number {
    return previsto > 0
      ? Math.min(100, Math.max(0, (realizado / previsto) * 100))
      : realizado > 0
        ? 100
        : 0;
  }
}
