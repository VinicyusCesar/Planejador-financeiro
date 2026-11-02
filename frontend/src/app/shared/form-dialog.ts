import { Component, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
  ValidatorFn,
} from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { DialogService, EditorConfig, Field } from '../core/dialog.service';
import { ContextService } from '../core/context.service';
import { ToastService } from '../core/toast.service';
import { apiError } from '../core/api.service';
import { fieldError, moneyDecimal, moneyEdit, moneyValidator } from '../core/format';
import { ModalComponent } from './modal';

@Component({
  selector: 'app-form-dialog',
  imports: [ModalComponent, ReactiveFormsModule],
  template: `
    <app-modal
      [title]="config().title"
      [description]="config().description || ''"
      [busy]="saving()"
      (closed)="dialogs.close()"
    >
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="form-grid">
          @for (field of config().fields; track field.key) {
            <div class="field" [class.wide]="field.wide">
              <label [for]="'edit-' + field.key"
                >{{ field.label }}
                @if (field.required) {
                  <span class="required" aria-hidden="true">*</span>
                }
              </label>
              @if (field.kind === 'select') {
                <select
                  [id]="'edit-' + field.key"
                  [formControlName]="field.key"
                  [attr.aria-label]="field.label"
                  [attr.aria-required]="field.required || null"
                  [attr.aria-invalid]="invalid(field)"
                >
                  @if (!field.required) {
                    <option value="">Nenhuma</option>
                  } @else {
                    <option value="" disabled>Selecione</option>
                  }
                  @for (option of field.options || []; track option.value) {
                    <option [value]="option.value">{{ option.label }}</option>
                  }
                </select>
              } @else {
                <input
                  [id]="'edit-' + field.key"
                  [formControlName]="field.key"
                  [attr.aria-label]="field.label"
                  [attr.aria-required]="field.required || null"
                  [type]="field.kind === 'money' ? 'text' : field.kind || 'text'"
                  [attr.inputmode]="field.kind === 'money' ? 'decimal' : null"
                  [placeholder]="field.placeholder || (field.kind === 'money' ? '0,00' : '')"
                  [attr.min]="field.min ?? null"
                  [attr.max]="field.maxDate || field.max || null"
                  [attr.maxlength]="field.maxLength || null"
                  [readonly]="field.readonly || false"
                  [attr.aria-invalid]="invalid(field)"
                  (blur)="formatMoney(field)"
                />
              }
              @if (invalid(field)) {
                <small class="field-error" role="alert">{{ errorFor(field) }}</small>
              } @else if (field.hint) {
                <small class="field-hint">{{ field.hint }}</small>
              }
            </div>
          }
        </div>
        @if (error()) {
          <p class="form-error" role="alert">{{ error() }}</p>
        }
        <div class="modal-actions">
          <button
            class="button secondary"
            type="button"
            [disabled]="saving()"
            (click)="dialogs.close()"
          >
            Voltar
          </button>
          <button
            class="button"
            [class.danger]="config().danger"
            [disabled]="saving()"
            type="submit"
          >
            @if (saving()) {
              <span class="spinner small"></span>
            }
            {{ saving() ? 'Salvando…' : config().submitLabel || 'Salvar' }}
          </button>
        </div>
      </form>
    </app-modal>
  `,
})
export class FormDialogComponent implements OnInit {
  readonly config = input.required<EditorConfig>();
  readonly dialogs = inject(DialogService);
  private readonly context = inject(ContextService);
  private readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  form = new FormGroup<Record<string, FormControl<string>>>({});
  ngOnInit(): void {
    const controls: Record<string, FormControl<string>> = {};
    for (const field of this.config().fields) {
      const validators: ValidatorFn[] = [];
      if (field.required) validators.push(Validators.required);
      if (field.kind === 'email') validators.push(Validators.email);
      if (field.maxLength) validators.push(Validators.maxLength(field.maxLength));
      if (field.pattern) validators.push(Validators.pattern(field.pattern));
      if (field.min != null) validators.push(Validators.min(field.min));
      if (field.max != null) validators.push(Validators.max(field.max));
      if (field.kind === 'money') validators.push(moneyValidator(field.zero, field.signed));
      if (field.maxDate)
        validators.push((control: AbstractControl) =>
          control.value && control.value > field.maxDate! ? { maxDate: true } : null,
        );
      controls[field.key] = new FormControl(String(field.value ?? ''), {
        nonNullable: true,
        validators,
      });
    }
    this.form = new FormGroup(controls);
  }
  invalid(field: Field): boolean {
    const control = this.form.controls[field.key];
    return !!control?.touched && control.invalid;
  }
  errorFor(field: Field): string {
    return fieldError(this.form.controls[field.key]?.errors ?? null);
  }
  formatMoney(field: Field): void {
    if (field.kind !== 'money') return;
    const control = this.form.controls[field.key];
    const value = moneyDecimal(control.value);
    if (value != null) control.setValue(moneyEdit(Number(value)));
  }
  submit(): void {
    if (this.saving()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const values = this.form.getRawValue();
    const validation = this.config().validate?.(values);
    if (validation) {
      this.error.set(validation);
      return;
    }
    this.error.set(null);
    this.saving.set(true);
    this.config()
      .save(values)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: (result) => {
          this.config().saved?.(result);
          this.context.refresh();
          this.toasts.show(this.config().success);
          this.dialogs.close();
        },
        error: (error) => {
          this.error.set(apiError(error));
          if (error instanceof HttpErrorResponse && error.error?.campos) {
            for (const [key, message] of Object.entries(error.error.campos))
              this.form.get(key)?.setErrors({ server: message });
          }
        },
      });
  }
}
