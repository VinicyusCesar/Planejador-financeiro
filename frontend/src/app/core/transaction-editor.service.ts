import { Injectable, signal } from '@angular/core';
import { TipoTransacao, Transacao } from './models';

@Injectable({ providedIn: 'root' })
export class TransactionEditorService {
  readonly active = signal<Transacao | null | undefined>(undefined);
  readonly preset = signal<TipoTransacao>('DESPESA');
  create(type: TipoTransacao = 'DESPESA'): void {
    this.preset.set(type);
    this.active.set(null);
  }
  edit(transaction: Transacao): void {
    this.active.set(transaction);
  }
  close(): void {
    this.active.set(undefined);
  }
}
