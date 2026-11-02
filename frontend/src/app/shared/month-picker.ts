import { Component, input, output } from '@angular/core';
import { IconComponent } from './icon';
import { shiftMonth } from '../core/format';

@Component({
  selector: 'app-month-picker',
  imports: [IconComponent],
  template: `<div class="period-control">
    <button
      type="button"
      class="icon-button"
      aria-label="Mês anterior"
      [disabled]="value() <= '2000-01'"
      (click)="changed.emit(shift(-1))"
    >
      <app-icon name="left" />
    </button>
    <label class="sr-only" for="reference-month">Mês de referência</label
    ><input
      id="reference-month"
      type="month"
      min="2000-01"
      max="9998-12"
      [value]="value()"
      (change)="change($event)"
    />
    <button
      type="button"
      class="icon-button"
      aria-label="Próximo mês"
      [disabled]="value() >= '9998-12'"
      (click)="changed.emit(shift(1))"
    >
      <app-icon name="right" />
    </button>
  </div>`,
})
export class MonthPickerComponent {
  readonly value = input.required<string>();
  readonly changed = output<string>();
  shift(delta: number): string {
    return shiftMonth(this.value(), delta);
  }
  change(event: Event): void {
    const element = event.target as HTMLInputElement;
    if (
      /^\d{4}-(0[1-9]|1[0-2])$/.test(element.value) &&
      element.value >= '2000-01' &&
      element.value <= '9998-12'
    )
      this.changed.emit(element.value);
    else element.value = this.value();
  }
}
