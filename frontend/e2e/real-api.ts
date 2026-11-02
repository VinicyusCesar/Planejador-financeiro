import { APIRequestContext, expect, Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';

export const apiUrl = process.env['E2E_API_URL'] || 'http://127.0.0.1:18082/api';
export const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(
  new Date(),
);
export async function guard(request: APIRequestContext) {
  const url = new URL(apiUrl);
  expect(url.hostname).toBe('127.0.0.1');
  expect(url.port).not.toBe('8080');
  const response = await request.get(apiUrl + '/health/database');
  expect(response.ok()).toBe(true);
  const body = await response.json();
  expect(body).toMatchObject({
    success: true,
    database: 'planejador_financeiro_test',
    user: 'backend_test',
  });
}
export async function post(request: APIRequestContext, path: string, body: unknown, user?: string) {
  const response = await request.post(apiUrl + '/' + path, {
    data: body,
    headers: user ? { 'X-Usuario-Id': user } : {},
  });
  expect(response.status(), await response.text()).toBe(201);
  return response.json();
}
export async function get(request: APIRequestContext, path: string, user: string) {
  const response = await request.get(apiUrl + '/' + path, { headers: { 'X-Usuario-Id': user } });
  expect(response.ok(), await response.text()).toBe(true);
  return response.json();
}
export async function seed(request: APIRequestContext) {
  await guard(request);
  const user = await post(request, 'usuarios', {
    nome: 'Marina Almeida',
    email: randomUUID() + '@e2e.local',
  });
  const account = await post(
    request,
    'contas',
    { nome: 'Conta principal', tipo: 'CORRENTE', saldoInicial: 1000 },
    user.id,
  );
  const reserve = await post(
    request,
    'contas',
    { nome: 'Reserva', tipo: 'POUPANCA', saldoInicial: 0 },
    user.id,
  );
  const expense = await post(
    request,
    'categorias',
    { nome: 'Alimentação', tipo: 'DESPESA' },
    user.id,
  );
  const income = await post(request, 'categorias', { nome: 'Trabalho', tipo: 'RECEITA' }, user.id);
  const card = await post(
    request,
    'cartoes',
    {
      nome: 'Cartão pessoal',
      finalCartao: '4832',
      limite: 5000,
      diaFechamento: 25,
      diaVencimento: 5,
      contaPagamentoId: account.id,
    },
    user.id,
  );
  return { user, account, reserve, expense, income, card };
}
export type Seed = Awaited<ReturnType<typeof seed>>;
export async function open(page: Page, fixture: Seed, path = '/') {
  await page.addInitScript((id) => {
    if (!localStorage.getItem('planejador.user')) localStorage.setItem('planejador.user', id);
  }, fixture.user.id);
  await page.goto(path);
  await expect(page.getByRole('button', { name: 'Nova transação', exact: true })).toBeEnabled();
}
export async function saveDialog(page: Page, label = 'Salvar') {
  const dialog = page.getByRole('dialog').last();
  const titleId = await dialog.getAttribute('aria-labelledby');
  const stableDialog = page.locator('dialog[aria-labelledby="' + titleId + '"]');
  const pending = page.waitForResponse(
    (response) =>
      ['POST', 'PUT', 'PATCH', 'DELETE'].includes(response.request().method()) &&
      response.url().includes('/api/'),
  );
  await dialog.getByRole('button', { name: label, exact: true }).click();
  const response = await pending;
  expect(response.ok(), await response.text()).toBe(true);
  await expect(stableDialog).toHaveCount(0);
  return response.status() === 204 ? null : response.json();
}
export async function movement(
  page: Page,
  fixture: Seed,
  description: string,
  amount: string,
  options: { income?: boolean; credit?: boolean; parts?: number; pending?: boolean } = {},
) {
  await page.getByRole('button', { name: 'Nova transação', exact: true }).click();
  const dialog = page.getByRole('dialog').last();
  if (options.income) await dialog.getByRole('button', { name: 'Receita', exact: true }).click();
  await dialog.getByLabel('Qual é o valor?').fill(amount);
  await dialog.getByLabel('Descrição', { exact: true }).fill(description);
  if (options.credit) await dialog.getByLabel('Forma de pagamento').selectOption('CREDITO');
  await dialog
    .getByLabel('Categoria', { exact: true })
    .selectOption(options.income ? fixture.income.id : fixture.expense.id);
  if (options.credit) {
    await dialog.getByLabel('Cartão', { exact: true }).selectOption(fixture.card.id);
    await dialog.getByLabel('Número de parcelas').fill(String(options.parts || 1));
  } else await dialog.getByLabel('Conta', { exact: true }).selectOption(fixture.account.id);
  if (options.pending)
    await dialog.getByLabel('Situação', { exact: true }).selectOption('PENDENTE');
  return saveDialog(page, options.income ? 'Registrar receita' : 'Registrar despesa');
}
