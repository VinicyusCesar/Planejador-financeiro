import { Component, computed, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ApiService, apiError } from '../core/api.service';
import { TransactionEditorService } from '../core/transaction-editor.service';
import { WorkspaceService } from '../core/workspace.service';
import { ToastService } from '../core/toast.service';
import {
  Condicao,
  MEIOS,
  MeioPagamento,
  Situacao,
  TipoTransacao,
  Transacao,
  TransacaoEntrada,
} from '../core/models';
import {
  cents,
  fieldError,
  moneyDecimal,
  moneyEdit,
  moneyValidator,
  todayIso,
} from '../core/format';
import { ModalComponent } from './modal';
import { IconComponent } from './icon';

@Component({
  selector: 'app-transaction-dialog',
  imports: [ReactiveFormsModule, ModalComponent, IconComponent, RouterLink],
  templateUrl: './transaction-dialog.html',
})
export class TransactionDialogComponent implements OnInit {
  readonly transaction = input<Transacao | null>(null);
  readonly editor = inject(TransactionEditorService);
  readonly workspace = inject(WorkspaceService);
  private readonly api = inject(ApiService);
  private readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly credit = signal(false);
  readonly type = signal<TipoTransacao>('DESPESA');
  readonly pending = signal(false);
  readonly today = todayIso();
  readonly methods = MEIOS;
  readonly categories = computed(() =>
    this.workspace
      .categories()
      .filter((item) => item.tipo === 'AMBOS' || item.tipo === this.type()),
  );
  readonly form = this.fb.nonNullable.group({
    tipo: this.fb.nonNullable.control<TipoTransacao>('DESPESA', Validators.required),
    meioPagamento: this.fb.nonNullable.control<MeioPagamento>('PIX', Validators.required),
    descricao: ['', [Validators.required, Validators.maxLength(200)]],
    valor: ['', [Validators.required, moneyValidator()]],
    categoriaId: ['', Validators.required],
    contaId: [''],
    cartaoId: [''],
    quantidadeParcelas: [1, [Validators.required, Validators.min(1), Validators.max(600)]],
    dataTransacao: [todayIso(), Validators.required],
    dataVencimento: [''],
    dataLiquidacao: [''],
    situacao: this.fb.nonNullable.control<Exclude<Situacao, 'CANCELADA'>>(
      'CONFIRMADA',
      Validators.required,
    ),
  });
  ngOnInit(): void {
    const transaction = this.transaction();
    if (transaction)
      this.form.patchValue({
        ...transaction,
        valor: moneyEdit(transaction.valor),
        contaId: transaction.contaId ?? '',
        cartaoId: transaction.cartaoId ?? '',
        dataVencimento: transaction.dataVencimento ?? '',
        dataLiquidacao: transaction.dataLiquidacao ?? '',
        situacao: transaction.situacao === 'PENDENTE' ? 'PENDENTE' : 'CONFIRMADA',
      });
    else this.form.controls.tipo.setValue(this.editor.preset());
    this.type.set(this.form.controls.tipo.value);
    this.pending.set(this.form.controls.situacao.value === 'PENDENTE');
    this.configurePayment(this.form.controls.meioPagamento.value);
    this.form.controls.meioPagamento.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.configurePayment(value));
    this.form.controls.tipo.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => {
        this.type.set(value);
        const selected = this.workspace
          .categories()
          .find((item) => item.id === this.form.controls.categoriaId.value);
        if (selected && selected.tipo !== 'AMBOS' && selected.tipo !== value)
          this.form.controls.categoriaId.setValue('');
      });
    this.form.controls.situacao.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => {
        this.pending.set(value === 'PENDENTE');
        if (value === 'PENDENTE') this.form.controls.dataLiquidacao.setValue('');
      });
  }
  private configurePayment(method: MeioPagamento): void {
    const credit = method === 'CREDITO';
    this.credit.set(credit);
    this.form.controls.contaId.setValidators(credit ? [] : [Validators.required]);
    this.form.controls.cartaoId.setValidators(credit ? [Validators.required] : []);
    if (credit) {
      this.form.controls.tipo.setValue('DESPESA');
      this.type.set('DESPESA');
      this.form.controls.contaId.setValue('');
      this.form.controls.dataVencimento.setValue('');
      this.form.controls.dataLiquidacao.setValue('');
    } else {
      this.form.controls.cartaoId.setValue('');
      this.form.controls.quantidadeParcelas.setValue(1);
    }
    this.form.controls.contaId.updateValueAndValidity({ emitEvent: false });
    this.form.controls.cartaoId.updateValueAndValidity({ emitEvent: false });
  }
  invalid(key: string): boolean {
    const control = this.form.get(key);
    return !!control?.invalid && control.touched;
  }
  fieldError(key: string): string {
    return fieldError(this.form.get(key)?.errors ?? null);
  }
  formatAmount(): void {
    const value = moneyDecimal(this.form.controls.valor.value);
    if (value) this.form.controls.valor.setValue(moneyEdit(Number(value)));
  }
  submit(): void {
    if (this.saving()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    const amount = moneyDecimal(value.valor)!;
    if (value.situacao === 'CONFIRMADA' && value.dataTransacao > this.today) {
      this.error.set('Movimentações futuras devem permanecer pendentes.');
      return;
    }
    if (
      !this.credit() &&
      value.dataLiquidacao &&
      (value.dataLiquidacao < value.dataTransacao || value.dataLiquidacao > this.today)
    ) {
      this.error.set('A liquidação deve estar entre a data da transação e hoje.');
      return;
    }
    if (this.credit() && cents(amount) < BigInt(value.quantidadeParcelas)) {
      this.error.set('Cada parcela deve ter pelo menos um centavo.');
      return;
    }
    const payload: TransacaoEntrada = {
      ...value,
      valor: amount,
      contaId: this.credit() ? null : value.contaId,
      cartaoId: this.credit() ? value.cartaoId : null,
      quantidadeParcelas: this.credit() ? Number(value.quantidadeParcelas) : 1,
      condicao: (this.credit() && value.quantidadeParcelas > 1
        ? 'PARCELADO'
        : 'AVISTA') as Condicao,
      dataVencimento: this.credit() ? null : value.dataVencimento || null,
      dataLiquidacao:
        this.credit() || value.situacao === 'PENDENTE' ? null : value.dataLiquidacao || null,
    };
    this.saving.set(true);
    this.error.set(null);
    const transaction = this.transaction();
    (transaction
      ? this.api.put<Transacao>('transacoes/' + transaction.id, payload)
      : this.api.post<Transacao>('transacoes', payload)
    )
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.workspace.context.refresh();
          this.toasts.show(
            transaction ? 'Transação atualizada.' : 'Transação registrada. Tudo no seu lugar.',
          );
          this.editor.close();
        },
        error: (error) => {
          this.error.set(apiError(error));
          if (error.error?.campos)
            for (const [key, message] of Object.entries(error.error.campos))
              this.form.get(key)?.setErrors({ server: message });
        },
      });
  }
}
