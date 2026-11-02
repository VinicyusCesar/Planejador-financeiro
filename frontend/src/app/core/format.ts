import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export function todayIso(): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Sao_Paulo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date());
  const value = (type: string) => parts.find((part) => part.type === type)?.value ?? '';
  return value('year') + '-' + value('month') + '-' + value('day');
}
export function monthBounds(month: string): { inicio: string; fim: string } {
  const [year, number] = month.split('-').map(Number);
  const last = new Date(Date.UTC(year, number, 0)).getUTCDate();
  return { inicio: month + '-01', fim: month + '-' + String(last).padStart(2, '0') };
}
export function shiftMonth(month: string, delta: number): string {
  const [year, number] = month.split('-').map(Number);
  const date = new Date(Date.UTC(year, number - 1 + delta, 1));
  return date.getUTCFullYear() + '-' + String(date.getUTCMonth() + 1).padStart(2, '0');
}
export function monthLabel(month: string, short = false): string {
  const [year, number] = month.split('-').map(Number);
  return new Intl.DateTimeFormat('pt-BR', {
    month: short ? 'short' : 'long',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(Date.UTC(year, number - 1, 1)));
}
export function dateLabel(iso: string | null, full = false): string {
  if (!iso) return 'Sem prazo';
  const [year, month, day] = iso.slice(0, 10).split('-').map(Number);
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: full ? 'long' : 'short',
    ...(full ? { year: 'numeric' as const } : {}),
    timeZone: 'UTC',
  }).format(new Date(Date.UTC(year, month - 1, day)));
}
export function moneyEdit(value: number | null | undefined): string {
  return value == null
    ? ''
    : new Intl.NumberFormat('pt-BR', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
        useGrouping: true,
      }).format(value);
}
// Conversão exata do texto brasileiro para decimal canônico, sem cálculo financeiro em ponto flutuante.
export function moneyDecimal(text: string): string | null {
  const raw = text
    .trim()
    .replace(/^R\$\s*/, '')
    .replace(/\u00a0/g, '');
  if (!/^-?(?:\d+|\d{1,3}(?:\.\d{3})+)(?:,\d{1,2})?$/.test(raw)) return null;
  const negative = raw.startsWith('-');
  const [whole, fraction = ''] = raw.replace('-', '').replace(/\./g, '').split(',');
  const integer = whole.replace(/^0+(?=\d)/, '');
  if (integer.length > 13) return null;
  return (negative ? '-' : '') + integer + '.' + fraction.padEnd(2, '0');
}
export function cents(decimal: string): bigint {
  return BigInt(decimal.replace('.', ''));
}
export function sumMoney(values: number[]): string {
  const total = values.reduce((sum, value) => sum + BigInt(Math.round(value * 100)), 0n);
  const absolute = total < 0n ? -total : total;
  return (
    (total < 0n ? '-' : '') +
    (absolute / 100n).toString() +
    '.' +
    (absolute % 100n).toString().padStart(2, '0')
  );
}
export function moneyValidator(allowZero = false, signed = false): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    if (control.value === '' || control.value == null) return null;
    const value = moneyDecimal(String(control.value));
    if (value == null) return { money: true };
    const amount = cents(value);
    if (!signed && (allowZero ? amount < 0n : amount <= 0n))
      return { positive: !allowZero, nonnegative: allowZero };
    return null;
  };
}
export function fieldError(errors: ValidationErrors | null): string {
  if (!errors) return '';
  if (errors['server']) return String(errors['server']);
  if (errors['required']) return 'Preencha este campo.';
  if (errors['email']) return 'Informe um email válido.';
  if (errors['money']) return 'Use um valor brasileiro com até 2 casas decimais, como 1.250,50.';
  if (errors['positive']) return 'O valor deve ser maior que zero.';
  if (errors['nonnegative']) return 'O valor não pode ser negativo.';
  if (errors['min']) return 'Valor mínimo: ' + errors['min'].min + '.';
  if (errors['max']) return 'Valor máximo: ' + errors['max'].max + '.';
  if (errors['maxlength'])
    return 'Utilize até ' + errors['maxlength'].requiredLength + ' caracteres.';
  if (errors['pattern']) return 'Verifique o formato informado.';
  if (errors['maxDate']) return 'A data não pode estar no futuro.';
  return 'Verifique o valor informado.';
}
