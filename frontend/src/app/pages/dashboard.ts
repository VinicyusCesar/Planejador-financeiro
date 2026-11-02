import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { ApiService } from '../core/api.service';
import { ContextService } from '../core/context.service';
import { WorkspaceService } from '../core/workspace.service';
import { EditorsService } from '../core/editors.service';
import { Mensal, Semanal, Pagina, Transacao, Saldo } from '../core/models';
import { monthBounds, monthLabel, sumMoney, todayIso } from '../core/format';
import { resource } from '../core/resource';
import { IconComponent } from '../shared/icon';
import { LoadStateComponent } from '../shared/load-state';
import { MoneyPipe } from '../shared/pipes';
import { MonthPickerComponent } from '../shared/month-picker';
import { WeeklyChartComponent } from '../shared/weekly-chart';
import { TransactionTableComponent } from '../shared/transaction-table';
import { TransactionEditorService } from '../core/transaction-editor.service';

@Component({
  imports: [
    RouterLink,
    IconComponent,
    LoadStateComponent,
    MoneyPipe,
    MonthPickerComponent,
    WeeklyChartComponent,
    TransactionTableComponent,
  ],
  templateUrl: './dashboard.html',
})
export class DashboardPage {
  private readonly api = inject(ApiService);
  readonly context = inject(ContextService);
  readonly workspace = inject(WorkspaceService);
  readonly editors = inject(EditorsService);
  readonly editor = inject(TransactionEditorService);
  private readonly router = inject(Router);
  readonly month = signal(todayIso().slice(0, 7));
  readonly week = signal(todayIso());
  readonly monthTitle = computed(() => monthLabel(this.month()));
  readonly monthly = resource(() => {
    this.context.revision();
    const [ano, mes] = this.month().split('-').map(Number);
    return this.context.userId()
      ? this.api.get<Mensal>('dashboard/resumo-mensal', { ano, mes })
      : null;
  });
  readonly weekly = resource(() => {
    this.context.revision();
    return this.context.userId()
      ? this.api.get<Semanal>('dashboard/resumo-semanal', { dataReferencia: this.week() })
      : null;
  });
  readonly movements = resource(() => {
    this.context.revision();
    return this.context.userId()
      ? this.api.get<Pagina<Transacao>>('transacoes', {
          ...monthBounds(this.month()),
          tamanho: 5,
          pagina: 0,
        })
      : null;
  });
  readonly balances = resource(() => {
    this.context.revision();
    if (!this.context.userId() || !this.workspace.lookups.data()) return null;
    const accounts = this.workspace.activeAccounts();
    return accounts.length
      ? forkJoin(accounts.map((account) => this.api.get<Saldo>('contas/' + account.id + '/saldo')))
      : of<Saldo[]>([]);
  });
  readonly totalBalance = computed(() =>
    this.balances.data() ? sumMoney(this.balances.data()!.map((item) => item.saldoAtual)) : null,
  );
  readonly colors = ['#9ef3c0', '#74d9d1', '#86b7cf', '#c1d7a6', '#e2c798', '#dca9a3', '#b9c8d1'];
  readonly categories = computed(() => this.monthly.data()?.gastosPorCategoria ?? []);
  readonly donut = computed(() => {
    const data = this.categories();
    const total = data.reduce((sum, item) => sum + item.total, 0);
    let start = 0;
    const stops = data.map((item, index) => {
      const end = start + (item.total / total) * 100;
      const value = this.colors[index % this.colors.length] + ' ' + start + '% ' + end + '%';
      start = end;
      return value;
    });
    return total > 0 ? 'conic-gradient(' + stops.join(',') + ')' : 'none';
  });
  readonly hasWeeklyExpense = computed(
    () => this.weekly.data()?.gastosPorDia.some((day) => day.total > 0) ?? false,
  );
  inspect(transaction: Transacao): void {
    void this.router.navigate(['/transacoes'], { queryParams: { abrir: transaction.id } });
  }
}
