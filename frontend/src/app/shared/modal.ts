import {
  afterNextRender,
  Component,
  ElementRef,
  input,
  OnDestroy,
  output,
  viewChild,
} from '@angular/core';
import { IconComponent } from './icon';

let sequence = 0;

@Component({
  selector: 'app-modal',
  imports: [IconComponent],
  template: `
    <dialog
      #dialog
      class="glass-dialog"
      [attr.aria-labelledby]="titleId"
      (cancel)="cancel($event)"
      (click)="backdrop($event)"
    >
      <div class="modal-heading">
        <div>
          <p class="eyebrow">SEU PLANEJADOR</p>
          <h2 [id]="titleId">{{ title() }}</h2>
          @if (description()) {
            <p class="muted">{{ description() }}</p>
          }
        </div>
        <button
          type="button"
          class="icon-button"
          aria-label="Fechar janela"
          [disabled]="busy()"
          (click)="closed.emit()"
        >
          <app-icon name="close" />
        </button>
      </div>
      <ng-content />
    </dialog>
  `,
})
export class ModalComponent implements OnDestroy {
  readonly titleId = 'dialog-title-' + ++sequence;
  readonly title = input.required<string>();
  readonly description = input('');
  readonly busy = input(false);
  readonly closed = output<void>();
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly previous = typeof document === 'undefined' ? null : document.activeElement;
  constructor() {
    afterNextRender(() => {
      const element = this.dialog().nativeElement;
      if (!element.open) element.showModal();
      element.querySelector<HTMLElement>('[autofocus], input, select, textarea')?.focus();
    });
  }
  cancel(event: Event): void {
    event.preventDefault();
    if (!this.busy()) this.closed.emit();
  }
  backdrop(event: MouseEvent): void {
    if (event.target !== this.dialog().nativeElement || this.busy()) return;
    const rect = this.dialog().nativeElement.getBoundingClientRect();
    if (
      event.clientX < rect.left ||
      event.clientX > rect.right ||
      event.clientY < rect.top ||
      event.clientY > rect.bottom
    )
      this.closed.emit();
  }
  ngOnDestroy(): void {
    if (this.dialog().nativeElement.open) this.dialog().nativeElement.close();
    if (this.previous instanceof HTMLElement && this.previous.isConnected) this.previous.focus();
  }
}
