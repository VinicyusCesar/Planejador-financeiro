import { Injectable, signal } from '@angular/core';

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}
function write(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* A aplicação também funciona sem armazenamento local. */
  }
}
@Injectable({ providedIn: 'root' })
export class ContextService {
  readonly userId = signal<string | null>(null);
  readonly revision = signal(0);
  readonly hidden = signal(read('planejador.hidden') === 'true');
  readonly preferredUser = read('planejador.user');
  selectUser(id: string): void {
    this.userId.set(id);
    write('planejador.user', id);
    this.refresh();
  }
  refresh(): void {
    this.revision.update((value) => value + 1);
  }
  togglePrivacy(): void {
    this.hidden.update((value) => !value);
    write('planejador.hidden', String(this.hidden()));
  }
}
