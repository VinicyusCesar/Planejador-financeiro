import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiService } from '../core/api.service';
import { WorkspaceService } from '../core/workspace.service';
import { EditorsService } from '../core/editors.service';
import { resource } from '../core/resource';
import { DetalheFatura, Fatura, Query } from '../core/models';
import { monthLabel } from '../core/format';
import { IconComponent } from '../shared/icon';
import { LoadStateComponent } from '../shared/load-state';
import { ModalComponent } from '../shared/modal';
import { MoneyPipe, BrDatePipe, LabelPipe } from '../shared/pipes';

@Component({
  imports: [
    ReactiveFormsModule,
    RouterLink,
    IconComponent,
    LoadStateComponent,
    ModalComponent,
    MoneyPipe,
    BrDatePipe,
    LabelPipe,
  ],
  templateUrl: './invoices.html',
})
export class InvoicesPage {
  private readonly api = inject(ApiService);
  readonly workspace = inject(WorkspaceService);
  readonly editors = inject(EditorsService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  readonly filters = signal<Query>({});
  readonly detailId = signal<string | null>(null);
  readonly form = this.fb.nonNullable.group({ cartaoId: '', periodo: '', inicio: '', fim: '' });
  readonly data = resource(() => {
    this.workspace.context.revision();
    return this.workspace.context.userId()
      ? this.api.get<Fatura[]>('faturas', this.filters())
      : null;
  });
  readonly detail = resource(() => {
    this.workspace.context.revision();
    const id = this.detailId();
    return this.workspace.context.userId() && id
      ? this.api.get<DetalheFatura>('faturas/' + id)
      : null;
  });
  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.form.controls.cartaoId.setValue(params.get('cartaoId') || '');
      this.apply();
      if (params.get('abrir')) this.detailId.set(params.get('abrir'));
    });
  }
  apply(): void {
    const value = this.form.getRawValue();
    this.filters.set({ ...value, periodo: value.cartaoId ? value.periodo : '' });
  }
  reference(fatura: Fatura): string {
    return monthLabel(fatura.anoReferencia + '-' + String(fatura.mesReferencia).padStart(2, '0'));
  }
  paidPercent(fatura: Fatura): number {
    return fatura.total > 0 ? Math.min(100, (fatura.totalPago / fatura.total) * 100) : 0;
  }
  changeCard(): void {
    if (!this.form.controls.cartaoId.value) this.form.controls.periodo.setValue('');
  }
}
