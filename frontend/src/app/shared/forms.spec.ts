import { beforeEach, describe, expect, it, vi } from 'vitest';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';
import { TransactionDialogComponent } from './transaction-dialog';
import { BudgetDialogComponent } from './budget-dialog';
import { ApiService } from '../core/api.service';
import { WorkspaceService } from '../core/workspace.service';
import { TransactionEditorService } from '../core/transaction-editor.service';
import { ToastService } from '../core/toast.service';
import { Categoria, TipoTransacao } from '../core/models';
import { todayIso } from '../core/format';

describe('formulários financeiros', () => {
  const api = { post: vi.fn(), put: vi.fn() };
  const workspace = {
    categories: signal<Categoria[]>([
      { id: 'despesa', usuarioId: 'pessoa', nome: 'Mercado', tipo: 'DESPESA' },
      { id: 'receita', usuarioId: 'pessoa', nome: 'Salário', tipo: 'RECEITA' },
    ]),
    activeAccounts: signal([{ id: 'conta', nome: 'Principal' }]),
    activeCards: signal([{ id: 'cartao', nome: 'Cartão' }]),
    context: { refresh: vi.fn() },
  };
  const editor = { preset: signal<TipoTransacao>('DESPESA'), close: vi.fn() };
  beforeEach(() => {
    vi.clearAllMocks();
    api.post.mockReturnValue(of({ id: 'registro' }));
    api.put.mockReturnValue(of({ id: 'registro' }));
    TestBed.configureTestingModule({
      imports: [TransactionDialogComponent, BudgetDialogComponent],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: WorkspaceService, useValue: workspace },
        { provide: TransactionEditorService, useValue: editor },
        { provide: ToastService, useValue: { show: vi.fn() } },
      ],
    });
    TestBed.overrideComponent(TransactionDialogComponent, { set: { template: '', imports: [] } });
    TestBed.overrideComponent(BudgetDialogComponent, { set: { template: '', imports: [] } });
  });
  function transaction() {
    const fixture = TestBed.createComponent(TransactionDialogComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.form.patchValue({
      descricao: 'Mercado',
      valor: '100,00',
      categoriaId: 'despesa',
      contaId: 'conta',
      dataTransacao: todayIso(),
    });
    return component;
  }
  it('registra PIX com conta e sem cartão', () => {
    const component = transaction();
    component.submit();
    expect(api.post).toHaveBeenCalledWith(
      'transacoes',
      expect.objectContaining({
        valor: '100.00',
        contaId: 'conta',
        cartaoId: null,
        quantidadeParcelas: 1,
        condicao: 'AVISTA',
      }),
    );
  });
  it('crédito exige cartão, força despesa e remove conta', () => {
    const component = transaction();
    component.form.controls.meioPagamento.setValue('CREDITO');
    expect(component.credit()).toBe(true);
    expect(component.form.controls.contaId.value).toBe('');
    expect(component.form.controls.tipo.value).toBe('DESPESA');
    component.submit();
    expect(api.post).not.toHaveBeenCalled();
    component.form.patchValue({ cartaoId: 'cartao', quantidadeParcelas: 3 });
    component.submit();
    expect(api.post).toHaveBeenCalledWith(
      'transacoes',
      expect.objectContaining({
        contaId: null,
        cartaoId: 'cartao',
        quantidadeParcelas: 3,
        condicao: 'PARCELADO',
        dataLiquidacao: null,
        dataVencimento: null,
      }),
    );
  });
  it('não registra valor zero', () => {
    const component = transaction();
    component.form.controls.valor.setValue('0,00');
    component.submit();
    expect(api.post).not.toHaveBeenCalled();
  });
  it('não admite parcelas de zero centavos', () => {
    const component = transaction();
    component.form.patchValue({
      meioPagamento: 'CREDITO',
      cartaoId: 'cartao',
      quantidadeParcelas: 3,
      valor: '0,01',
    });
    component.submit();
    expect(component.error()).toContain('um centavo');
    expect(api.post).not.toHaveBeenCalled();
  });
  it('pendência direta não envia data de liquidação', () => {
    const component = transaction();
    component.form.controls.situacao.setValue('PENDENTE');
    component.submit();
    expect(api.post).toHaveBeenCalledWith(
      'transacoes',
      expect.objectContaining({ situacao: 'PENDENTE', dataLiquidacao: null }),
    );
  });
  it('filtra categorias pelo tipo de receita', () => {
    const component = transaction();
    component.form.controls.tipo.setValue('RECEITA');
    expect(component.categories().map((item) => item.id)).toEqual(['receita']);
    expect(component.form.controls.categoriaId.value).toBe('');
  });
  it('previne submissões duplicadas enquanto aguarda a API', () => {
    const response = new Subject<unknown>();
    api.post.mockReturnValue(response);
    const component = transaction();
    component.submit();
    component.submit();
    expect(api.post).toHaveBeenCalledTimes(1);
    response.next({ id: 'registro' });
    response.complete();
  });
  it('mantém dados preenchidos e explica conflito do backend', () => {
    api.post.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { mensagem: 'A fatura já recebeu pagamentos.' },
          }),
      ),
    );
    const component = transaction();
    component.submit();
    expect(component.error()).toContain('pagamentos');
    expect(component.form.controls.descricao.value).toBe('Mercado');
    expect(editor.close).not.toHaveBeenCalled();
  });
  it('orçamento rejeita categorias repetidas', () => {
    const fixture = TestBed.createComponent(BudgetDialogComponent);
    fixture.componentRef.setInput('month', '2026-11');
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.items.at(0).setValue({ categoriaId: 'despesa', valorPrevisto: '100,00' });
    component.add('despesa', '50,00');
    component.submit();
    expect(component.error()).toContain('apenas uma vez');
    expect(api.post).not.toHaveBeenCalled();
  });
  it('orçamento envia itens e valores canônicos ao endpoint real', () => {
    const fixture = TestBed.createComponent(BudgetDialogComponent);
    fixture.componentRef.setInput('month', '2026-11');
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.items.at(0).setValue({ categoriaId: 'despesa', valorPrevisto: '1.200,50' });
    component.submit();
    expect(api.post).toHaveBeenCalledWith('orcamentos', {
      ano: 2026,
      mes: 11,
      metaEconomia: null,
      itens: [{ categoriaId: 'despesa', valorPrevisto: '1200.50' }],
    });
  });
});
