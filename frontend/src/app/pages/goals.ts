import { Component, inject } from '@angular/core';
import { ApiService } from '../core/api.service';
import { ContextService } from '../core/context.service';
import { EditorsService } from '../core/editors.service';
import { Meta } from '../core/models';
import { resource } from '../core/resource';
import { IconComponent } from '../shared/icon';
import { LoadStateComponent } from '../shared/load-state';
import { MoneyPipe, BrDatePipe, LabelPipe } from '../shared/pipes';

@Component({
  imports: [IconComponent, LoadStateComponent, MoneyPipe, BrDatePipe, LabelPipe],
  template: `<div class="page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">UM DESTINO PARA OS SEUS PLANOS</p>
        <div class="page-title-mask"><h1>Metas e objetivos</h1></div>
        <p class="muted">O que você quer viver amanhã começa com uma intenção hoje.</p>
      </div>
      <button class="button" type="button" (click)="editors.goal()">
        <app-icon name="plus" />Novo objetivo
      </button>
    </div>
    <app-load-state [loading]="goals.loading()" [error]="goals.error()" (retry)="goals.reload()" />
    @if (goals.data(); as items) {
      <div class="card-grid">
        @for (goal of items; track goal.id) {
          <article class="glass goal-card">
            <div class="card-top">
              <span class="stat-icon"><app-icon name="target" /></span
              ><span class="badge" [class]="goal.situacao">{{ goal.situacao | label }}</span>
            </div>
            <h3>{{ goal.nome }}</h3>
            <p class="number">{{ goal.valorObjetivo | money }}</p>
            <div class="goal-dates">
              <app-icon name="calendar" /><span
                >{{ goal.dataLimite ? 'Até ' : '' }}{{ goal.dataLimite | brDate: true }}</span
              >
            </div>
            @if (goal.progressoDisponivel && goal.percentual !== null) {
              <div class="progress">
                <span [style.width.%]="Math.min(100, Math.max(0, goal.percentual))"></span>
              </div>
              <p class="subtle">
                {{ goal.percentual }}% · {{ goal.valorAcumulado | money }} acumulados
              </p>
            } @else {
              <p class="goal-info">
                Este objetivo guarda seu valor e prazo. O acompanhamento de aportes ainda não está
                disponível.
              </p>
            }
            <div class="card-footer">
              <span class="subtle">Desde {{ goal.dataInicio | brDate }}</span
              ><button class="text-button" type="button" (click)="editors.goal(goal)">
                <app-icon name="edit" />Ajustar objetivo
              </button>
            </div>
          </article>
        }
        <button class="create-card" type="button" (click)="editors.goal()">
          <app-icon name="plus" /><span>Qual é o seu próximo plano?</span
          ><span class="subtle">Criar objetivo</span>
        </button>
      </div>
      @if (items.length === 0) {
        <p class="note">
          <app-icon name="spark" />Uma viagem, uma reserva ou um novo começo. Escolha um objetivo
          que faça sentido para você.
        </p>
      }
    }
  </div>`,
})
export class GoalsPage {
  private readonly api = inject(ApiService);
  private readonly context = inject(ContextService);
  readonly editors = inject(EditorsService);
  readonly Math = Math;
  readonly goals = resource(() => {
    this.context.revision();
    return this.context.userId() ? this.api.get<Meta[]>('metas') : null;
  });
}
