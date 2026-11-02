import { computed, inject, Injectable, signal } from '@angular/core';
import { forkJoin, Subscription } from 'rxjs';
import { ApiService, apiError } from './api.service';
import { ContextService } from './context.service';
import { Cartao, Categoria, Conta, DatabaseHealth, Usuario } from './models';
import { resource } from './resource';

@Injectable({ providedIn: 'root' })
export class WorkspaceService {
  readonly api = inject(ApiService);
  readonly context = inject(ContextService);
  readonly users = signal<Usuario[]>([]);
  readonly connecting = signal(true);
  readonly connectionError = signal<string | null>(null);
  readonly connected = signal(false);
  readonly checkedAt = signal<Date | null>(null);
  private connection?: Subscription;
  readonly selected = computed(
    () => this.users().find((user) => user.id === this.context.userId()) ?? null,
  );
  readonly lookups = resource(() => {
    this.context.revision();
    return this.context.userId()
      ? forkJoin({
          contas: this.api.get<Conta[]>('contas'),
          categorias: this.api.get<Categoria[]>('categorias'),
          cartoes: this.api.get<Cartao[]>('cartoes'),
        })
      : null;
  });
  readonly accounts = computed(() => this.lookups.data()?.contas ?? []);
  readonly categories = computed(() => this.lookups.data()?.categorias ?? []);
  readonly cards = computed(() => this.lookups.data()?.cartoes ?? []);
  readonly activeAccounts = computed(() => this.accounts().filter((account) => account.ativa));
  readonly activeCards = computed(() => this.cards().filter((card) => card.ativo));
  connect(): void {
    this.connection?.unsubscribe();
    this.connecting.set(true);
    this.connectionError.set(null);
    this.connection = forkJoin({
      health: this.api.get<DatabaseHealth>('health/database'),
      users: this.api.get<Usuario[]>('usuarios'),
    }).subscribe({
      next: (result) => {
        if (!result.health.success) {
          this.fail('Não foi possível confirmar a conexão com o banco.');
          return;
        }
        const desired = this.context.userId() ?? this.context.preferredUser;
        this.users.set(result.users);
        const selected = result.users.find((user) => user.id === desired) ?? result.users[0];
        if (selected) this.context.selectUser(selected.id);
        else this.context.userId.set(null);
        this.connected.set(true);
        this.checkedAt.set(new Date());
        this.connecting.set(false);
      },
      error: (error) => this.fail(apiError(error)),
    });
  }
  private fail(message: string): void {
    this.connected.set(false);
    this.connecting.set(false);
    this.connectionError.set(message);
  }
  select(id: string): void {
    if (this.users().some((user) => user.id === id)) this.context.selectUser(id);
  }
  savedUser(user: Usuario, select = false): void {
    this.users.update((users) =>
      users.some((item) => item.id === user.id)
        ? users.map((item) => (item.id === user.id ? user : item))
        : [...users, user],
    );
    if (select || !this.context.userId()) this.context.selectUser(user.id);
    else this.context.refresh();
  }
  accountName(id: string | null): string {
    return this.accounts().find((account) => account.id === id)?.nome ?? 'Conta';
  }
  cardName(id: string | null): string {
    return this.cards().find((card) => card.id === id)?.nome ?? 'Cartão';
  }
  categoryName(id: string): string {
    return this.categories().find((category) => category.id === id)?.nome ?? 'Categoria';
  }
}
