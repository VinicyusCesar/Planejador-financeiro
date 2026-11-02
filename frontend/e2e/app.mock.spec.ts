import { test, expect } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import { mockApi, ids } from './mock-api';

let errors: string[] = [];
test.beforeEach(async ({ page }) => {
  errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await mockApi(page);
});
test.afterEach(() => expect(errors).toEqual([]));

test('dashboard brasileiro, gráficos e layout responsivo', async ({ page }, info) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Seu dinheiro. Sob controle.' })).toBeVisible();
  await expect(page.getByText('Saldo nas contas', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Seu ritmo na semana' })).toBeVisible();
  await expect(page.getByText(/4.804,30/)).toBeVisible();
  expect(await page.evaluate(() => document.body.scrollWidth <= window.innerWidth)).toBe(true);
  await mkdir('.runtime/screenshots', { recursive: true });
  await page.screenshot({
    animations: 'disabled',
    path: '.runtime/screenshots/dashboard-' + info.project.name + '.png',
    fullPage: true,
  });
});

test('todas as áreas possuem rotas funcionais', async ({ page }) => {
  const routes = [
    ['/contas', 'Minhas contas'],
    ['/transacoes', 'Transações'],
    ['/cartoes', 'Cartões e faturas'],
    ['/faturas', 'Faturas'],
    ['/transferencias', 'Transferências'],
    ['/orcamentos', 'Planeje suas escolhas'],
    ['/metas', 'Metas e objetivos'],
    ['/categorias', 'Categorias'],
    ['/perfis', 'Perfis'],
  ];
  for (const [url, title] of routes) {
    await page.goto(url);
    await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.body.scrollWidth <= window.innerWidth)).toBe(true);
  }
});

test('privacidade oculta valores e pode ser revertida', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByText(/4.804,30/)).toBeVisible();
  await page.getByRole('button', { name: 'Ocultar valores', exact: true }).click();
  await expect(page.getByText(/R\$ •••••/).first()).toBeVisible();
  await expect(page.getByText(/4.804,30/)).toHaveCount(0);
  await page.getByRole('button', { name: 'Mostrar valores', exact: true }).click();
  await expect(page.getByText(/4.804,30/)).toBeVisible();
});

test('modal mantém foco, fecha por Escape e restaura o botão', async ({ page }) => {
  await page.goto('/');
  const button = page.getByRole('button', { name: 'Nova transação', exact: true });
  await expect(button).toBeEnabled();
  await button.click();
  const modal = page.getByRole('dialog');
  await expect(modal).toBeVisible();
  await expect(page.getByLabel('Qual é o valor?')).toBeFocused();
  await page.keyboard.press('Tab');
  expect(await page.evaluate(() => !!document.activeElement?.closest('dialog'))).toBe(true);
  await page.keyboard.press('Escape');
  await expect(modal).toHaveCount(0);
  await expect(button).toBeFocused();
});

test('crédito pede cartão e impede parcela de zero centavos', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Nova transação', exact: true }).click();
  await page.getByLabel('Qual é o valor?').fill('0,01');
  await page.getByLabel('Descrição', { exact: true }).fill('Compra parcelada');
  await page.getByLabel('Forma de pagamento').selectOption('CREDITO');
  await page.getByLabel('Categoria', { exact: true }).selectOption(ids.category);
  await expect(page.getByLabel('Conta', { exact: true })).toHaveCount(0);
  await page.getByLabel('Cartão', { exact: true }).selectOption(ids.card);
  await page.getByLabel('Número de parcelas').fill('3');
  await page.getByRole('button', { name: 'Registrar despesa', exact: true }).click();
  await expect(page.getByText('Cada parcela deve ter pelo menos um centavo.')).toBeVisible();
});

test('conflito do backend preserva formulário e não duplica envio', async ({ page }) => {
  let posts = 0;
  await page.route('**/api/transacoes', async (route) => {
    if (route.request().method() === 'POST') {
      posts++;
      await route.fulfill({
        status: 409,
        contentType: 'application/json',
        body: JSON.stringify({
          codigo: 'FATURA_COM_PAGAMENTO',
          mensagem: 'A fatura já recebeu pagamentos.',
        }),
      });
    } else await route.fallback();
  });
  await page.goto('/');
  await page.getByRole('button', { name: 'Nova transação', exact: true }).click();
  await page.getByLabel('Qual é o valor?').fill('100,00');
  await page.getByLabel('Descrição', { exact: true }).fill('Movimento preservado');
  await page.getByLabel('Categoria', { exact: true }).selectOption(ids.category);
  await page.getByLabel('Conta', { exact: true }).selectOption(ids.account);
  await page.getByRole('button', { name: 'Registrar despesa', exact: true }).click();
  await expect(page.getByText('A fatura já recebeu pagamentos.')).toBeVisible();
  await expect(page.getByLabel('Descrição', { exact: true })).toHaveValue('Movimento preservado');
  expect(posts).toBe(1);
});

test('sem usuários oferece onboarding sem valores fictícios', async ({ page }) => {
  await page.route('**/api/usuarios', async (route) => {
    if (route.request().method() === 'GET') await route.fulfill({ json: [] });
    else await route.fallback();
  });
  await page.goto('/');
  await expect(page.getByRole('button', { name: 'Criar meu perfil', exact: true })).toBeVisible();
  await expect(page.getByText('Saldo nas contas', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Criar meu perfil', exact: true }).click();
  await page.getByLabel('Como você se chama?').fill('Primeiro perfil');
  await page.getByLabel('Email', { exact: true }).fill('primeiro@example.com');
  await page
    .getByRole('dialog')
    .getByRole('button', { name: 'Criar meu perfil', exact: true })
    .click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Seu dinheiro. Sob controle.' })).toBeVisible();
});

test('falha de conexão é explícita e permite tentar novamente', async ({ page }) => {
  await page.route('**/api/health/database', (route) => route.abort('connectionrefused'));
  await page.goto('/');
  await expect(
    page.getByRole('heading', { name: 'Seu espaço está aqui. Falta a conexão.' }),
  ).toBeVisible();
  await expect(page.getByRole('button', { name: 'Tentar novamente', exact: true })).toBeVisible();
  await expect(page.getByText('R$ 0,00', { exact: true })).toHaveCount(0);
});

test('troca de perfil usa outro proprietário sem manter contas antigas', async ({ page }, info) => {
  const calls = await mockApi(page);
  await page.goto('/contas');
  await expect(page.getByRole('heading', { name: 'Conta principal', exact: true })).toBeVisible();
  if (info.project.name === 'mobile')
    await page.getByRole('button', { name: 'Abrir menu', exact: true }).click();
  await page.getByLabel('Perfil selecionado').selectOption(ids.other);
  if (info.project.name === 'mobile')
    await page.getByRole('button', { name: 'Fechar menu', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Conta Rafael', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Conta principal', exact: true })).toHaveCount(0);
  expect(calls.filter((call) => call.path === 'contas').at(-1)?.owner).toBe(ids.other);
});

test('categoria em uso mostra conflito sem apagar histórico', async ({ page }) => {
  await page.route('**/api/categorias/' + ids.category, (route) =>
    route.fulfill({
      status: 409,
      json: {
        codigo: 'CATEGORIA_EM_USO',
        mensagem: 'A categoria possui transações ou itens de orçamento.',
      },
    }),
  );
  await page.goto('/categorias');
  await page.getByRole('button', { name: 'Excluir Alimentação', exact: true }).click();
  await page.getByRole('button', { name: 'Excluir categoria', exact: true }).click();
  await expect(
    page.getByText('A categoria possui transações ou itens de orçamento.'),
  ).toBeVisible();
  await expect(page.getByRole('dialog')).toBeVisible();
});

test('metas não apresentam progresso inventado', async ({ page }) => {
  await page.goto('/metas');
  await expect(
    page.getByRole('heading', { name: 'Uma viagem especial', exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText(
      'Este objetivo guarda seu valor e prazo. O acompanhamento de aportes ainda não está disponível.',
    ),
  ).toBeVisible();
  await expect(page.locator('.goal-card .progress')).toHaveCount(0);
});

test('respeita redução de movimento', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Seu dinheiro. Sob controle.' })).toBeVisible();
  const seconds = await page
    .locator('.hero h1')
    .evaluate((element) => parseFloat(getComputedStyle(element).animationDuration));
  expect(seconds).toBeLessThanOrEqual(0.001);
});

test('cabe em telas compactas, tablets e desktop sem rolagem lateral da página', async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  for (const width of [320, 360, 768, 1280]) {
    await page.setViewportSize({ width, height: 800 });
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Seu dinheiro. Sob controle.' })).toBeVisible();
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
  }
});
