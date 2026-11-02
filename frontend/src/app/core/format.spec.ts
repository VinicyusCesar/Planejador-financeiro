import { describe, expect, it } from 'vitest';
import { FormControl } from '@angular/forms';
import {
  cents,
  dateLabel,
  moneyDecimal,
  moneyValidator,
  monthBounds,
  shiftMonth,
  sumMoney,
} from './format';

describe('valores monetários e datas brasileiras', () => {
  it.each([
    ['1.250,50', '1250.50'],
    ['12,3', '12.30'],
    ['0', '0.00'],
    ['-85,20', '-85.20'],
    ['R$ 9.999,99', '9999.99'],
    ['9999999999999,99', '9999999999999.99'],
  ])('converte %s sem alterar centavos', (input, expected) => {
    expect(moneyDecimal(input)).toBe(expected);
  });
  it.each(['', '12.34', '1,001', 'NaN', '10000000000000,00'])(
    'rejeita formato inválido %s',
    (input) => {
      expect(moneyDecimal(input)).toBeNull();
    },
  );
  it('preserva o limite NUMERIC(15,2) em centavos inteiros', () => {
    expect(cents('9999999999999.99')).toBe(999999999999999n);
  });
  it('rejeita zero em operações e permite zero em orçamento', () => {
    expect(moneyValidator()(new FormControl('0,00'))).not.toBeNull();
    expect(moneyValidator(true)(new FormControl('0,00'))).toBeNull();
  });
  it('permite saldo inicial negativo sem aceitar despesa negativa', () => {
    expect(moneyValidator(true, true)(new FormControl('-100,00'))).toBeNull();
    expect(moneyValidator()(new FormControl('-100,00'))).not.toBeNull();
  });
  it('soma somente saldos retornados pela API com centavos exatos', () => {
    expect(sumMoney([0.1, 0.2, -0.05])).toBe('0.25');
  });
  it('respeita fevereiro e anos bissextos', () => {
    expect(monthBounds('2028-02')).toEqual({ inicio: '2028-02-01', fim: '2028-02-29' });
    expect(monthBounds('2026-02').fim).toBe('2026-02-28');
  });
  it('troca dezembro e janeiro corretamente', () => {
    expect(shiftMonth('2026-12', 1)).toBe('2027-01');
    expect(shiftMonth('2027-01', -1)).toBe('2026-12');
  });
  it('não desloca LocalDate para o dia anterior', () => {
    expect(dateLabel('2026-11-02', true)).toContain('02');
  });
});
