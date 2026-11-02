import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../shared/icon';

@Component({
  imports: [RouterLink, IconComponent],
  template: `<section class="glass onboarding">
    <span class="empty-icon"><app-icon name="search" /></span>
    <p class="eyebrow">PÁGINA NÃO ENCONTRADA</p>
    <h1>Vamos voltar<br />para o seu caminho?</h1>
    <p class="muted">Este endereço não está no seu planejador.</p>
    <a class="button" routerLink="/">Ir para a visão geral<app-icon name="arrow" /></a>
  </section>`,
})
export class NotFoundPage {}
