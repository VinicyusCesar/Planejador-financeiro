import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: number;
  message: string;
  kind: 'success' | 'error';
}
@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly items = signal<Toast[]>([]);
  private sequence = 0;
  show(message: string, kind: 'success' | 'error' = 'success'): void {
    const id = ++this.sequence;
    this.items.update((items) => [...items, { id, message, kind }]);
    setTimeout(() => this.dismiss(id), kind === 'error' ? 9000 : 5000);
  }
  dismiss(id: number): void {
    this.items.update((items) => items.filter((item) => item.id !== id));
  }
}
