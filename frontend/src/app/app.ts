import { Component, computed, DestroyRef, HostListener, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet, NavigationEnd } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { ContextService } from './core/context.service';
import { WorkspaceService } from './core/workspace.service';
import { EditorsService } from './core/editors.service';
import { DialogService } from './core/dialog.service';
import { TransactionEditorService } from './core/transaction-editor.service';
import { ToastService } from './core/toast.service';
import { IconComponent } from './shared/icon';
import { FormDialogComponent } from './shared/form-dialog';
import { TransactionDialogComponent } from './shared/transaction-dialog';
import { LoadStateComponent } from './shared/load-state';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    IconComponent,
    FormDialogComponent,
    TransactionDialogComponent,
    LoadStateComponent,
  ],
  templateUrl: './app.html',
})
export class App {
  readonly context = inject(ContextService);
  readonly workspace = inject(WorkspaceService);
  readonly editors = inject(EditorsService);
  readonly dialogs = inject(DialogService);
  readonly transactions = inject(TransactionEditorService);
  readonly toasts = inject(ToastService);
  readonly router = inject(Router);
  readonly currentUrl = signal(this.router.url);
  readonly menuOpen = signal(false);
  readonly mobile = signal(window.matchMedia('(max-width: 900px)').matches);
  readonly search = signal('');
  readonly links = [
    { path: '/', label: 'Visão geral', icon: 'home', group: 'PRINCIPAL' },
    { path: '/transacoes', label: 'Transações', icon: 'activity', group: 'PRINCIPAL' },
    { path: '/contas', label: 'Contas', icon: 'wallet', group: 'PRINCIPAL' },
    { path: '/cartoes', label: 'Cartões e faturas', icon: 'card', group: 'PRINCIPAL' },
    { path: '/transferencias', label: 'Transferências', icon: 'transfer', group: 'PRINCIPAL' },
    { path: '/orcamentos', label: 'Orçamentos', icon: 'chart', group: 'PLANEJAMENTO' },
    { path: '/metas', label: 'Metas e objetivos', icon: 'target', group: 'PLANEJAMENTO' },
    { path: '/categorias', label: 'Categorias', icon: 'tag', group: 'ORGANIZAÇÃO' },
    { path: '/perfis', label: 'Perfis', icon: 'users', group: 'ORGANIZAÇÃO' },
  ];
  readonly matches = computed(() => {
    const query = this.search()
      .toLocaleLowerCase('pt-BR')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .trim();
    return query
      ? this.links.filter((link) =>
          link.label
            .toLocaleLowerCase('pt-BR')
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .includes(query),
        )
      : [];
  });
  readonly firstName = computed(() => this.workspace.selected()?.nome.split(' ')[0] ?? 'você');
  readonly initial = computed(
    () => this.workspace.selected()?.nome.charAt(0).toLocaleUpperCase('pt-BR') ?? 'P',
  );
  constructor() {
    this.workspace.connect();
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((event) => {
        this.currentUrl.set(event.urlAfterRedirects);
        this.menuOpen.set(false);
        this.search.set('');
      });
    const media = window.matchMedia('(max-width: 900px)');
    const listener = (event: MediaQueryListEvent) => {
      this.mobile.set(event.matches);
      if (!event.matches) this.menuOpen.set(false);
    };
    media.addEventListener('change', listener);
    inject(DestroyRef).onDestroy(() => media.removeEventListener('change', listener));
  }
  @HostListener('document:keydown.escape')
  escape(): void {
    this.menuOpen.set(false);
    this.search.set('');
  }
  @HostListener('document:click', ['$event'])
  click(event: MouseEvent): void {
    if (!(event.target as HTMLElement).closest('.search-box')) this.search.set('');
  }
  isActive(path: string): boolean {
    const current = this.currentUrl().split('?')[0];
    return path === '/'
      ? current === '/'
      : current.startsWith(path) || (path === '/cartoes' && current.startsWith('/faturas'));
  }
  toggleMenu(): void {
    this.menuOpen.update((value) => !value);
    if (this.menuOpen())
      setTimeout(() => document.querySelector<HTMLButtonElement>('.mobile-close')?.focus());
    else
      setTimeout(() => document.querySelector<HTMLButtonElement>('.mobile-menu-button')?.focus());
  }
}
