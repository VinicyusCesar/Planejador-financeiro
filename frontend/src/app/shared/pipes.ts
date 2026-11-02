import { inject, Pipe, PipeTransform } from '@angular/core';
import { ContextService } from '../core/context.service';
import { dateLabel } from '../core/format';
import { LABELS } from '../core/models';

@Pipe({ name: 'money', pure: false })
export class MoneyPipe implements PipeTransform {
  private readonly context = inject(ContextService);
  transform(value: number | string | null | undefined): string {
    if (value == null) return '—';
    if (this.context.hidden()) return 'R$ •••••';
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(
      Number(value),
    );
  }
}
@Pipe({ name: 'brDate' })
export class BrDatePipe implements PipeTransform {
  transform(value: string | null | undefined, full = false): string {
    return dateLabel(value ?? null, full);
  }
}
@Pipe({ name: 'label' })
export class LabelPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return value ? (LABELS[value] ?? value) : '—';
  }
}
