import { Component, input, output } from '@angular/core';
import { IconComponent } from './icon';

@Component({
  selector: 'app-load-state',
  imports: [IconComponent],
  template: `
    @if (loading()) {
      <div class="loading-state" role="status">
        <span class="spinner"></span><span>Buscando seus dados…</span>
      </div>
    } @else if (error()) {
      <div class="inline-error" role="alert">
        <app-icon name="info" />
        <div>
          <strong>Não conseguimos atualizar esta área.</strong>
          <p>{{ error() }}</p>
        </div>
        <button class="button secondary small" type="button" (click)="retry.emit()">
          Tentar novamente
        </button>
      </div>
    }
  `,
})
export class LoadStateComponent {
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly retry = output<void>();
}
