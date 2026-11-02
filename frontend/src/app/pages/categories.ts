import { Component, computed, inject, signal } from '@angular/core';
import { WorkspaceService } from '../core/workspace.service';
import { EditorsService } from '../core/editors.service';
import { TipoCategoria } from '../core/models';
import { IconComponent } from '../shared/icon';
import { LoadStateComponent } from '../shared/load-state';
import { LabelPipe } from '../shared/pipes';

@Component({
  imports: [IconComponent, LoadStateComponent, LabelPipe],
  template: `<div class="page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">CADA ESCOLHA, UM LUGAR</p>
        <div class="page-title-mask"><h1>Categorias</h1></div>
        <p class="muted">Dê significado aos seus movimentos e entenda o que importa para você.</p>
      </div>
      <button class="button" type="button" (click)="editors.category()">
        <app-icon name="plus" />Nova categoria
      </button>
    </div>
    <div class="toolbar">
      <div class="tabs" aria-label="Filtrar categorias">
        <button type="button" [class.active]="filter() === ''" (click)="filter.set('')">
          Todas
        </button>
        <button
          type="button"
          [class.active]="filter() === 'DESPESA'"
          (click)="filter.set('DESPESA')"
        >
          Despesas
        </button>
        <button
          type="button"
          [class.active]="filter() === 'RECEITA'"
          (click)="filter.set('RECEITA')"
        >
          Receitas
        </button>
        <button type="button" [class.active]="filter() === 'AMBOS'" (click)="filter.set('AMBOS')">
          Ambos
        </button>
      </div>
      <span class="subtle">{{ visible().length }} categorias</span>
    </div>
    <app-load-state [loading]="workspace.lookups.loading()" />
    @if (!workspace.lookups.loading() && workspace.lookups.data()) {
      <div class="glass table-panel">
        @if (visible().length) {
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Categoria</th>
                  <th>Usar para</th>
                  <th class="align-right">Ações</th>
                </tr>
              </thead>
              <tbody>
                @for (category of visible(); track category.id) {
                  <tr>
                    <td>
                      <div class="card-title">
                        <span class="stat-icon"><app-icon name="tag" /></span
                        ><span>{{ category.nome }}</span>
                      </div>
                    </td>
                    <td>
                      <span class="badge">{{ category.tipo | label }}</span>
                    </td>
                    <td>
                      <div class="row-actions">
                        <button
                          class="icon-button"
                          type="button"
                          [attr.aria-label]="'Editar ' + category.nome"
                          (click)="editors.category(category)"
                        >
                          <app-icon name="edit" />
                        </button>
                        <button
                          class="icon-button"
                          type="button"
                          [attr.aria-label]="'Excluir ' + category.nome"
                          (click)="editors.deleteCategory(category)"
                        >
                          <app-icon name="trash" />
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
            <span class="empty-icon"><app-icon name="tag" /></span>
            <h2>Organize do seu jeito.</h2>
            <p>Crie categorias para separar suas receitas e despesas com mais clareza.</p>
            <button class="button secondary" type="button" (click)="editors.category()">
              Criar categoria
            </button>
          </div>
        }
      </div>
    }
    <p class="note">
      <app-icon name="info" />Categorias usadas em movimentos ou orçamento não podem ser excluídas.
      Seu histórico vem primeiro.
    </p>
  </div>`,
})
export class CategoriesPage {
  readonly workspace = inject(WorkspaceService);
  readonly editors = inject(EditorsService);
  readonly filter = signal<TipoCategoria | ''>('');
  readonly visible = computed(() =>
    this.workspace
      .categories()
      .filter((category) => !this.filter() || category.tipo === this.filter()),
  );
}
