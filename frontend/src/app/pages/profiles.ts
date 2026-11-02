import { Component, inject } from '@angular/core';
import { WorkspaceService } from '../core/workspace.service';
import { EditorsService } from '../core/editors.service';
import { IconComponent } from '../shared/icon';

@Component({
  imports: [IconComponent],
  template: `<div class="page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">SEU ESPAÇO, SUA ORGANIZAÇÃO</p>
        <div class="page-title-mask"><h1>Perfis</h1></div>
        <p class="muted">
          Escolha de quem é este espaço. Cada perfil tem suas contas, movimentos e planos.
        </p>
      </div>
      <button class="button" type="button" (click)="editors.user()">
        <app-icon name="plus" />Novo perfil
      </button>
    </div>
    <div class="card-grid">
      @for (user of workspace.users(); track user.id) {
        <article class="glass profile-card">
          <div class="card-top">
            <span class="avatar" aria-hidden="true">{{
              user.nome.charAt(0).toLocaleUpperCase('pt-BR')
            }}</span>
            @if (workspace.context.userId() === user.id) {
              <span class="badge ATIVA">Perfil atual</span>
            }
          </div>
          <h3>{{ user.nome }}</h3>
          <p class="muted">{{ user.email }}</p>
          <div class="card-footer">
            <button
              type="button"
              class="text-button"
              [disabled]="workspace.context.userId() === user.id"
              (click)="workspace.select(user.id)"
            >
              <app-icon name="check" />{{
                workspace.context.userId() === user.id ? 'Selecionado' : 'Usar este perfil'
              }}
            </button>
            <button
              type="button"
              class="icon-button"
              [attr.aria-label]="'Editar perfil de ' + user.nome"
              [disabled]="workspace.context.userId() !== user.id"
              title="Selecione o perfil para editar"
              (click)="editors.user(user)"
            >
              <app-icon name="edit" />
            </button>
          </div>
        </article>
      }
      <button class="create-card" type="button" (click)="editors.user()">
        <app-icon name="users" /><span>Um novo espaço para organizar.</span
        ><span class="subtle">Adicionar perfil</span>
      </button>
    </div>
    <p class="note">
      <app-icon name="info" />A seleção de perfil organiza os dados neste ambiente local. Ela não é
      um login com senha.
    </p>
  </div>`,
})
export class ProfilesPage {
  readonly workspace = inject(WorkspaceService);
  readonly editors = inject(EditorsService);
}
