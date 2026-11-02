import { test, expect } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { apiUrl, get, movement, open, post, saveDialog, seed, Seed, today } from './real-api';

let fixture: Seed;
let errors: string[] = [];
test.beforeEach(async ({ page, request }) => {
  errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  const previewHealth = await request.get('/api/health/database');
  expect(previewHealth.ok()).toBe(true);
  expect(await previewHealth.json()).toMatchObject({
    success: true,
    database: 'planejador_financeiro_test',
    user: 'backend_test',
  });
  fixture = await seed(request);
});
test.afterEach(() => expect(errors).toEqual([]));

test('cadastros, receita, despesa, pendência e cancelamento pela interface', async ({
  page,
  request,
}) => {
  await open(page, fixture, '/perfis');
  await page.getByRole('button', { name: 'Novo perfil', exact: true }).click();
  await page.getByLabel('Como você se chama?').fill('Meu espaço de teste');
  await page.getByLabel('Email', { exact: true }).fill(randomUUID() + '@frontend.local');
  const user = await saveDialog(page, 'Criar meu perfil');
  await page.goto('/contas');
  await page.getByRole('button', { name: 'Nova conta', exact: true }).click();
  await page.getByLabel('Nome da conta', { exact: true }).fill('Conta que vai mudar');
  await page.getByLabel('Saldo inicial', { exact: true }).fill('500,00');
  const account = await saveDialog(page);
  await expect(
    page.getByRole('heading', { name: 'Conta que vai mudar', exact: true }),
  ).toBeVisible();
  await page.getByRole('button', { name: 'Editar conta', exact: true }).click();
  await page.getByLabel('Nome da conta', { exact: true }).fill('Conta principal');
  await saveDialog(page);
  expect((await get(request, 'contas/' + account.id, user.id)).saldoInicial).toBe(500);
  await page.goto('/categorias');
  await page.getByRole('button', { name: 'Nova categoria', exact: true }).click();
  await page.getByLabel('Nome', { exact: true }).fill('Alimentação');
  const expense = await saveDialog(page);
  await page.getByRole('button', { name: 'Nova categoria', exact: true }).click();
  await page.getByLabel('Nome', { exact: true }).fill('Trabalho');
  await page.getByLabel('Usar para', { exact: true }).selectOption('RECEITA');
  const income = await saveDialog(page);
  const current = { ...fixture, user, account, expense, income };
  await page.goto('/');
  await movement(page, current, 'Receita de teste', '250,50', { income: true });
  expect((await get(request, 'contas/' + account.id + '/saldo', user.id)).saldoAtual).toBe(750.5);
  const purchase = await movement(page, current, 'Mercado do teste', '120,25');
  expect((await get(request, 'contas/' + account.id + '/saldo', user.id)).saldoAtual).toBe(630.25);
  await page.goto('/transacoes');
  await page.getByRole('button', { name: 'Cancelar Mercado do teste', exact: true }).click();
  await saveDialog(page, 'Confirmar cancelamento');
  expect((await get(request, 'transacoes/' + purchase.id, user.id)).situacao).toBe('CANCELADA');
  expect((await get(request, 'contas/' + account.id + '/saldo', user.id)).saldoAtual).toBe(750.5);
  const pending = await movement(page, current, 'Despesa pendente', '50,00', { pending: true });
  expect((await get(request, 'contas/' + account.id + '/saldo', user.id)).saldoAtual).toBe(750.5);
  await page.getByRole('button', { name: 'Editar Despesa pendente', exact: true }).click();
  await page.getByRole('dialog').getByLabel('Situação', { exact: true }).selectOption('CONFIRMADA');
  await saveDialog(page, 'Salvar alterações');
  expect((await get(request, 'transacoes/' + pending.id, user.id)).situacao).toBe('CONFIRMADA');
  expect((await get(request, 'contas/' + account.id + '/saldo', user.id)).saldoAtual).toBe(700.5);
  await page.goto('/categorias');
  await page.getByRole('button', { name: 'Excluir Alimentação', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('button', { name: 'Excluir categoria', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText('transações');
  await dialog.getByRole('button', { name: 'Voltar', exact: true }).click();
  await page.goto('/contas');
  await page.getByRole('button', { name: 'Desativar Conta principal', exact: true }).click();
  await saveDialog(page, 'Desativar');
  expect((await get(request, 'contas/' + account.id, user.id)).ativa).toBe(false);
});

test('crédito parcelado, pagamento parcial e total sem duplicar despesas', async ({
  page,
  request,
}) => {
  await open(page, fixture);
  const purchase = await movement(page, fixture, 'Compra parcelada de teste', '100,00', {
    credit: true,
    parts: 3,
  });
  const parcels = await get(request, 'transacoes/' + purchase.id + '/parcelas', fixture.user.id);
  expect(parcels.map((item: { valor: number }) => item.valor)).toEqual([33.34, 33.33, 33.33]);
  expect(
    (await get(request, 'contas/' + fixture.account.id + '/saldo', fixture.user.id)).saldoAtual,
  ).toBe(1000);
  expect((await get(request, 'cartoes/' + fixture.card.id, fixture.user.id)).limiteUtilizado).toBe(
    100,
  );
  await page.goto('/faturas?abrir=' + parcels[0].faturaId);
  await page.getByRole('button', { name: 'Registrar pagamento', exact: true }).click();
  let payment = page.getByRole('dialog', { name: 'Pagar fatura', exact: true });
  await expect(payment).toBeVisible();
  await payment.getByLabel('Quanto você vai pagar?', { exact: true }).fill('40,00');
  await payment.getByRole('button', { name: 'Confirmar pagamento', exact: true }).click();
  await expect(payment.getByRole('alert')).toContainText('não pode ultrapassar');
  await payment.getByLabel('Quanto você vai pagar?', { exact: true }).fill('20,00');
  await saveDialog(page, 'Confirmar pagamento');
  let invoice = await get(request, 'faturas/' + parcels[0].faturaId, fixture.user.id);
  expect(invoice.resumo.saldoPendente).toBe(13.34);
  expect(
    (await get(request, 'contas/' + fixture.account.id + '/saldo', fixture.user.id)).saldoAtual,
  ).toBe(980);
  await page.getByRole('button', { name: 'Registrar pagamento', exact: true }).click();
  payment = page.getByRole('dialog', { name: 'Pagar fatura', exact: true });
  await expect(payment.getByLabel('Quanto você vai pagar?', { exact: true })).toHaveValue('13,34');
  await saveDialog(page, 'Confirmar pagamento');
  invoice = await get(request, 'faturas/' + parcels[0].faturaId, fixture.user.id);
  expect(invoice.resumo.situacao).toBe('PAGA');
  expect(invoice.resumo.saldoPendente).toBe(0);
  expect(
    (await get(request, 'contas/' + fixture.account.id + '/saldo', fixture.user.id)).saldoAtual,
  ).toBe(966.66);
  expect((await get(request, 'cartoes/' + fixture.card.id, fixture.user.id)).limiteUtilizado).toBe(
    66.66,
  );
  const [ano, mes] = today.slice(0, 7).split('-');
  expect(
    (await get(request, 'dashboard/resumo-mensal?ano=' + ano + '&mes=' + mes, fixture.user.id))
      .despesas,
  ).toBe(100);
  await page.getByRole('dialog').getByRole('button', { name: 'Fechar', exact: true }).click();
  await page.goto('/transacoes');
  await page.getByRole('button', { name: 'Editar Compra parcelada de teste', exact: true }).click();
  await page.getByLabel('Qual é o valor?').fill('90,00');
  await page.getByRole('button', { name: 'Salvar alterações', exact: true }).click();
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('pagamentos');
  expect((await get(request, 'transacoes/' + purchase.id, fixture.user.id)).valor).toBe(100);
});

test('transferências, orçamento e metas usam as respostas reais do servidor', async ({
  page,
  request,
}) => {
  await open(page, fixture, '/transferencias');
  await page.getByRole('button', { name: 'Nova transferência', exact: true }).click();
  await page.getByLabel('De qual conta?', { exact: true }).selectOption(fixture.account.id);
  await page.getByLabel('Para qual conta?', { exact: true }).selectOption(fixture.reserve.id);
  await page.getByLabel('Valor', { exact: true }).fill('500,00');
  const transfer = await saveDialog(page, 'Registrar transferência');
  expect(
    (await get(request, 'contas/' + fixture.account.id + '/saldo', fixture.user.id)).saldoAtual,
  ).toBe(500);
  expect(
    (await get(request, 'contas/' + fixture.reserve.id + '/saldo', fixture.user.id)).saldoAtual,
  ).toBe(500);
  await page.getByRole('button', { name: 'Cancelar transferência', exact: true }).click();
  await saveDialog(page, 'Confirmar cancelamento');
  expect((await get(request, 'transferencias/' + transfer.id, fixture.user.id)).situacao).toBe(
    'CANCELADA',
  );
  expect(
    (await get(request, 'contas/' + fixture.account.id + '/saldo', fixture.user.id)).saldoAtual,
  ).toBe(1000);
  await page.goto('/orcamentos');
  await page.getByRole('button', { name: 'Criar orçamento', exact: true }).click();
  await page.getByLabel('Quanto você quer economizar? (opcional)').fill('200,00');
  await page.getByLabel('Categoria 1', { exact: true }).selectOption(fixture.expense.id);
  await page.getByLabel('Previsão 1', { exact: true }).fill('300,00');
  const budget = await saveDialog(page, 'Salvar planejamento');
  expect((await get(request, 'orcamentos/' + budget.id, fixture.user.id)).totalPrevisto).toBe(300);
  await movement(page, fixture, 'Despesa planejada', '100,00');
  await expect(
    page
      .locator('.stat')
      .nth(1)
      .getByText(/100,00/),
  ).toBeVisible();
  const current = await get(request, 'orcamentos/' + budget.id, fixture.user.id);
  expect(current.totalRealizado).toBe(100);
  expect(current.diferenca).toBe(200);
  await page.getByRole('button', { name: 'Ajustar plano', exact: true }).click();
  await page.getByLabel('Previsão 1', { exact: true }).fill('400,00');
  await saveDialog(page, 'Salvar planejamento');
  expect((await get(request, 'orcamentos/' + budget.id, fixture.user.id)).totalPrevisto).toBe(400);
  await page.goto('/metas');
  await page.getByRole('button', { name: 'Novo objetivo', exact: true }).click();
  await page.getByLabel('Seu objetivo', { exact: true }).fill('Reserva de emergência');
  await page.getByLabel('Valor do objetivo', { exact: true }).fill('5.000,00');
  const goal = await saveDialog(page);
  expect(goal.progressoDisponivel).toBe(false);
  expect(goal.valorAcumulado).toBeNull();
  expect(goal.percentual).toBeNull();
  await expect(page.locator('.goal-card .progress')).toHaveCount(0);
  await page.getByRole('button', { name: 'Ajustar objetivo', exact: true }).click();
  await page.getByLabel('Situação declarada', { exact: true }).selectOption('CONCLUIDA');
  await saveDialog(page);
  expect((await get(request, 'metas/' + goal.id, fixture.user.id)).situacao).toBe('CONCLUIDA');
});

test('cartões e perfil podem ser mantidos e o dashboard exibe dados do PostgreSQL', async ({
  page,
  request,
}, info) => {
  await open(page, fixture, '/cartoes');
  await page.getByRole('button', { name: 'Novo cartão', exact: true }).click();
  await page.getByLabel('Nome do cartão', { exact: true }).fill('Cartão da viagem');
  await page.getByLabel('Últimos 4 dígitos', { exact: true }).fill('2222');
  await page.getByLabel('Limite do cartão', { exact: true }).fill('2.500,00');
  const card = await saveDialog(page);
  await page.getByRole('button', { name: 'Editar Cartão da viagem', exact: true }).click();
  await page.getByLabel('Limite do cartão', { exact: true }).fill('2.600,00');
  await saveDialog(page);
  expect((await get(request, 'cartoes/' + card.id, fixture.user.id)).limite).toBe(2600);
  await page.getByRole('button', { name: 'Desativar Cartão da viagem', exact: true }).click();
  await saveDialog(page, 'Desativar');
  expect((await get(request, 'cartoes/' + card.id, fixture.user.id)).ativo).toBe(false);
  await page.getByLabel('Mostrar inativos').check();
  await expect(page.getByRole('heading', { name: 'Cartão da viagem', exact: true })).toBeVisible();
  await page.goto('/perfis');
  const profile = page
    .locator('.profile-card')
    .filter({ has: page.getByText('Perfil atual', { exact: true }) });
  await profile.getByRole('button', { name: /Editar perfil/ }).click();
  await page.getByLabel('Como você se chama?').fill('Marina da Costa');
  await saveDialog(page, 'Salvar perfil');
  expect((await get(request, 'usuarios/' + fixture.user.id, fixture.user.id)).nome).toBe(
    'Marina da Costa',
  );
  await post(
    request,
    'transacoes',
    {
      categoriaId: fixture.income.id,
      contaId: fixture.account.id,
      descricao: 'Receita do mês',
      valor: 4500,
      tipo: 'RECEITA',
      meioPagamento: 'PIX',
      dataTransacao: today,
    },
    fixture.user.id,
  );
  const categoryNames = ['Moradia', 'Transporte', 'Lazer', 'Saúde'];
  const categories = [fixture.expense];
  for (const name of categoryNames)
    categories.push(
      await post(request, 'categorias', { nome: name, tipo: 'DESPESA' }, fixture.user.id),
    );
  const amounts = [480, 1200, 180, 150, 90];
  for (let index = 0; index < categories.length; index++)
    await post(
      request,
      'transacoes',
      {
        categoriaId: categories[index].id,
        contaId: fixture.account.id,
        descricao: categories[index].nome,
        valor: amounts[index],
        tipo: 'DESPESA',
        meioPagamento: 'PIX',
        dataTransacao: today,
      },
      fixture.user.id,
    );
  const first = new Date(today + 'T12:00:00Z');
  first.setUTCDate(first.getUTCDate() - 7);
  const weeklyAmounts = [145, 380, 210, 420, 190, 510, 245];
  for (let index = 0; index < 7; index++) {
    const date = new Date(first);
    date.setUTCDate(date.getUTCDate() + index);
    await post(
      request,
      'transacoes',
      {
        categoriaId: categories[index % categories.length].id,
        contaId: fixture.account.id,
        descricao: 'Movimento semanal ' + index,
        valor: weeklyAmounts[index],
        tipo: 'DESPESA',
        meioPagamento: 'PIX',
        dataTransacao: date.toISOString().slice(0, 10),
      },
      fixture.user.id,
    );
  }
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Seu dinheiro. Sob controle.' })).toBeVisible();
  await page.getByLabel('Data da semana').fill(first.toISOString().slice(0, 10));
  await expect(page.getByRole('img', { name: 'Gastos por dia na semana' })).toBeVisible();
  await mkdir('.runtime/screenshots', { recursive: true });
  await page.evaluate(() => {
    (document.activeElement as HTMLElement | null)?.blur();
    window.scrollTo(0, 0);
  });
  await page.screenshot({
    animations: 'disabled',
    path: '.runtime/screenshots/integracao-real-' + info.project.name + '.png',
    fullPage: true,
  });
});
