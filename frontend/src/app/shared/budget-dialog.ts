import {
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  OnInit,
  output,
  signal,
} from '@angular/core';
import {
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ApiService, apiError } from '../core/api.service';
import { WorkspaceService } from '../core/workspace.service';
import { ToastService } from '../core/toast.service';
import { Orcamento } from '../core/models';
import {
  cents,
  fieldError,
  moneyDecimal,
  moneyEdit,
  moneyValidator,
  monthLabel,
} from '../core/format';
import { ModalComponent } from './modal';
import { IconComponent } from './icon';
import { MoneyPipe } from './pipes';

type Row = FormGroup<{ categoriaId: FormControl<string>; valorPrevisto: FormControl<string> }>;
@Component({
  selector: 'app-budget-dialog',
  imports: [ReactiveFormsModule, RouterLink, ModalComponent, IconComponent, MoneyPipe],
  template: `<app-modal
    [title]="budget() ? 'Ajustar planejamento' : 'Um plano para seu mês'"
    [description]="monthTitle()"
    [busy]="saving()"
    (closed)="closed.emit()"
  >
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <div class="field" style="margin-bottom:24px;">
        <label for="budget-savings">Quanto você quer economizar? (opcional)</label
        ><input
          id="budget-savings"
          formControlName="metaEconomia"
          inputmode="decimal"
          placeholder="0,00"
        /><small class="field-hint"
          >Uma intenção para orientar suas escolhas. Não movimenta suas contas.</small
        >
        @if (form.controls.metaEconomia.touched && form.controls.metaEconomia.invalid) {
          <small class="field-error">{{ fieldError(form.controls.metaEconomia.errors) }}</small>
        }
      </div>
      <div class="section-top">
        <h3>Planejado por categoria</h3>
        <button class="text-button" type="button" (click)="add()">
          <app-icon name="plus" />Adicionar item
        </button>
      </div>
      <div formArrayName="itens">
        @for (row of items.controls; track row; let index = $index) {
          <div class="budget-editor-row" [formGroupName]="index">
            <div class="field">
              <label [for]="'budget-category-' + index">Categoria {{ index + 1 }}</label
              ><select [id]="'budget-category-' + index" formControlName="categoriaId">
                <option value="" disabled>Selecione</option>
                @for (category of categories(); track category.id) {
                  <option [value]="category.id">{{ category.nome }}</option>
                }
              </select>
              @if (row.controls.categoriaId.touched && row.controls.categoriaId.invalid) {
                <small class="field-error">Selecione uma categoria.</small>
              }
            </div>
            <div class="field">
              <label [for]="'budget-amount-' + index">Previsão {{ index + 1 }}</label
              ><input
                [id]="'budget-amount-' + index"
                inputmode="decimal"
                formControlName="valorPrevisto"
                placeholder="0,00"
              />
              @if (row.controls.valorPrevisto.touched && row.controls.valorPrevisto.invalid) {
                <small class="field-error">{{
                  fieldError(row.controls.valorPrevisto.errors)
                }}</small>
              }
            </div>
            <button
              class="icon-button plain"
              type="button"
              [attr.aria-label]="'Remover item ' + (index + 1)"
              (click)="items.removeAt(index)"
            >
              <app-icon name="trash" />
            </button>
          </div>
        }
      </div>
      @if (!categories().length) {
        <p class="note warning">
          Crie categorias de despesa para detalhar seu planejamento.
          <a routerLink="/categorias" (click)="closed.emit()">Ir para categorias</a>
        </p>
      }
      <div class="glass summary-inline">
        <p>Previsão total dos itens</p>
        <span class="number">{{ proposal() | money }}</span>
      </div>
      @if (error()) {
        <p class="form-error" role="alert">{{ error() }}</p>
      }
      <div class="modal-actions">
        <button
          class="button secondary"
          type="button"
          [disabled]="saving()"
          (click)="closed.emit()"
        >
          Voltar</button
        ><button class="button" type="submit" [disabled]="saving()">
          @if (saving()) {
            <span class="spinner small"></span>
          }
          {{ saving() ? 'Salvando…' : 'Salvar planejamento' }}
        </button>
      </div>
    </form>
  </app-modal>`,
})
export class BudgetDialogComponent implements OnInit {
  readonly budget = input<Orcamento | null>(null);
  readonly month = input.required<string>();
  readonly closed = output<void>();
  readonly workspace = inject(WorkspaceService);
  private readonly api = inject(ApiService);
  private readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly categories = computed(() =>
    this.workspace.categories().filter((item) => item.tipo !== 'RECEITA'),
  );
  readonly form = this.fb.nonNullable.group({
    metaEconomia: ['', [moneyValidator(true)]],
    itens: new FormArray<Row>([]),
  });
  readonly fieldError = fieldError;
  get items(): FormArray<Row> {
    return this.form.controls.itens;
  }
  ngOnInit(): void {
    const budget = this.budget();
    if (budget) {
      this.form.controls.metaEconomia.setValue(moneyEdit(budget.metaEconomia));
      for (const item of budget.itens) this.add(item.categoriaId, moneyEdit(item.previsto));
    } else this.add();
  }
  monthTitle(): string {
    return monthLabel(this.month()) + '. Um limite pensado por você.';
  }
  add(category = '', amount = ''): void {
    this.items.push(
      this.fb.nonNullable.group({
        categoriaId: [category, Validators.required],
        valorPrevisto: [amount, [Validators.required, moneyValidator(true)]],
      }),
    );
  }
  proposal(): string | null {
    let total = 0n;
    for (const row of this.items.controls) {
      const amount = moneyDecimal(row.controls.valorPrevisto.value);
      if (!amount) return null;
      total += cents(amount);
    }
    return (total / 100n).toString() + '.' + (total % 100n).toString().padStart(2, '0');
  }
  submit(): void {
    if (this.saving()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const values = this.form.getRawValue();
    const ids = values.itens.map((item) => item.categoriaId);
    if (new Set(ids).size !== ids.length) {
      this.error.set('Use cada categoria apenas uma vez no orçamento.');
      return;
    }
    const [ano, mes] = this.month().split('-').map(Number);
    const payload = {
      ano,
      mes,
      metaEconomia: values.metaEconomia ? moneyDecimal(values.metaEconomia) : null,
      itens: values.itens.map((item) => ({
        categoriaId: item.categoriaId,
        valorPrevisto: moneyDecimal(item.valorPrevisto),
      })),
    };
    this.saving.set(true);
    this.error.set(null);
    const budget = this.budget();
    (budget
      ? this.api.put<Orcamento>('orcamentos/' + budget.id, payload)
      : this.api.post<Orcamento>('orcamentos', payload)
    )
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.workspace.context.refresh();
          this.toasts.show('Planejamento salvo. Seu mês ganhou uma direção.');
          this.closed.emit();
        },
        error: (error) => this.error.set(apiError(error)),
      });
  }
}
