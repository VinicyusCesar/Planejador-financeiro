import { Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { Opcao } from './models';

export interface Field {
  key: string;
  label: string;
  kind?: 'text' | 'email' | 'date' | 'number' | 'money' | 'select';
  required?: boolean;
  value?: string | number | null;
  placeholder?: string;
  hint?: string;
  options?: Opcao[];
  min?: number;
  max?: number;
  maxLength?: number;
  pattern?: string;
  zero?: boolean;
  signed?: boolean;
  wide?: boolean;
  readonly?: boolean;
  maxDate?: string;
}
export interface EditorConfig {
  title: string;
  description?: string;
  fields: Field[];
  submitLabel?: string;
  success: string;
  danger?: boolean;
  validate?: (values: Record<string, string>) => string | null;
  save: (values: Record<string, string>) => Observable<unknown>;
  saved?: (result: unknown) => void;
}
@Injectable({ providedIn: 'root' })
export class DialogService {
  readonly active = signal<EditorConfig | null>(null);
  open(config: EditorConfig): void {
    this.active.set(config);
  }
  close(): void {
    this.active.set(null);
  }
}
