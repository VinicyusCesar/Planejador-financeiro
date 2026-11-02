import { Page } from '@playwright/test';
export const ids = {
  user: '00000000-0000-4000-8000-000000000001',
  other: '00000000-0000-4000-8000-000000000002',
  account: '00000000-0000-4000-8000-000000000010',
  reserve: '00000000-0000-4000-8000-000000000011',
  category: '00000000-0000-4000-8000-000000000020',
  income: '00000000-0000-4000-8000-000000000021',
  card: '00000000-0000-4000-8000-000000000030',
  invoice: '00000000-0000-4000-8000-000000000040',
  transaction: '00000000-0000-4000-8000-000000000050',
};
export const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(
  new Date(),
);
const month = today.slice(0, 7);
const [year, number] = month.split('-').map(Number);
export const transaction = {
  id: ids.transaction,
  usuarioId: ids.user,
  categoriaId: ids.category,
  contaId: ids.account,
  cartaoId: null,
  descricao: 'Mercado da semana',
  valor: 245.7,
  tipo: 'DESPESA',
  meioPagamento: 'PIX',
  condicao: 'AVISTA',
  quantidadeParcelas: 1,
  dataTransacao: today,
  dataVencimento: null,
  dataLiquidacao: today,
  situacao: 'CONFIRMADA',
};
export async function mockApi(page: Page) {
  const requests: { path: string; method: string; owner: string | undefined; body: unknown }[] = [];
  const users = [
    {
      id: ids.user,
      nome: 'Marina Almeida',
      email: 'marina@example.com',
      criadoEm: today + 'T10:00:00',
    },
    {
      id: ids.other,
      nome: 'Rafael Lima',
      email: 'rafael@example.com',
      criadoEm: today + 'T10:00:00',
    },
  ];
  const accounts = [
    {
      id: ids.account,
      usuarioId: ids.user,
      nome: 'Conta principal',
      tipo: 'CORRENTE',
      saldoInicial: 5000,
      ativa: true,
    },
    {
      id: ids.reserve,
      usuarioId: ids.user,
      nome: 'Minha reserva',
      tipo: 'POUPANCA',
      saldoInicial: 2000,
      ativa: true,
    },
  ];
  const categories = [
    { id: ids.category, usuarioId: ids.user, nome: 'Alimentação', tipo: 'DESPESA' },
    { id: ids.income, usuarioId: ids.user, nome: 'Trabalho', tipo: 'RECEITA' },
  ];
  const cards = [
    {
      id: ids.card,
      usuarioId: ids.user,
      nome: 'Cartão do dia a dia',
      finalCartao: '4832',
      contaPagamentoId: ids.account,
      limite: 5000,
      diaFechamento: 25,
      diaVencimento: 5,
      ativo: true,
      limiteUtilizado: 500,
      limiteDisponivel: 4500,
    },
  ];
  const invoice = {
    id: ids.invoice,
    cartaoId: ids.card,
    mesReferencia: number,
    anoReferencia: year,
    dataFechamento: month + '-25',
    dataVencimento: month + '-28',
    situacao: 'ABERTA',
    total: 500,
    totalPago: 0,
    saldoPendente: 500,
  };
  await page.route('**/api/**', async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    const path = url.pathname.replace('/api/', '');
    const owner = req.headers()['x-usuario-id'];
    requests.push({
      path,
      method: req.method(),
      owner,
      body: req.method() === 'GET' ? null : req.postDataJSON(),
    });
    let body: unknown = {};
    if (path === 'health/database')
      body = {
        success: true,
        database: 'planejador_financeiro_test',
        schema: 'public',
        user: 'backend_test',
      };
    else if (path === 'usuarios' && req.method() === 'GET') body = users;
    else if (path === 'usuarios' && req.method() === 'POST') {
      body = {
        id: '00000000-0000-4000-8000-000000000099',
        ...req.postDataJSON(),
        criadoEm: today + 'T10:00:00',
      };
      users.push(body as (typeof users)[number]);
    } else if (path === 'contas')
      body =
        owner === ids.other
          ? [
              {
                ...accounts[0],
                id: '00000000-0000-4000-8000-000000000015',
                usuarioId: ids.other,
                nome: 'Conta Rafael',
              },
            ]
          : accounts;
    else if (path.endsWith('/saldo'))
      body = { contaId: path.split('/')[1], saldoAtual: path.includes(ids.reserve) ? 2000 : 5000 };
    else if (path === 'categorias') body = categories;
    else if (path === 'cartoes') body = cards;
    else if (path === 'dashboard/resumo-mensal')
      body = {
        ano: year,
        mes: number,
        receitas: 7150,
        despesas: 2345.7,
        saldoDoMes: 4804.3,
        orcamentoPrevisto: 3000,
        orcamentoRealizado: 2345.7,
        diferencaOrcamento: 654.3,
        gastosPorCategoria: [
          { categoriaId: ids.category, categoria: 'Alimentação', total: 1245.7 },
          { categoriaId: 'moradia', categoria: 'Moradia', total: 800 },
          { categoriaId: 'transporte', categoria: 'Transporte', total: 300 },
        ],
      };
    else if (path === 'dashboard/resumo-semanal') {
      const date = new Date(today + 'T12:00:00Z');
      date.setUTCDate(date.getUTCDate() - ((date.getUTCDay() + 6) % 7));
      const values = [145, 380, 210, 420, 190, 510, 245.7];
      body = {
        inicio: date.toISOString().slice(0, 10),
        fim: today,
        totalSemana: 2100.7,
        totalSemanaAnterior: 1900,
        diferenca: 200.7,
        variacaoPercentual: 10.56,
        comparacaoPercentualDisponivel: true,
        gastosPorDia: values.map((total, index) => {
          const day = new Date(date);
          day.setUTCDate(day.getUTCDate() + index);
          return { data: day.toISOString().slice(0, 10), total };
        }),
      };
    } else if (path === 'dashboard/cartoes')
      body = [
        {
          cartaoId: ids.card,
          nome: cards[0].nome,
          faturaAtual: invoice,
          faturaSeguinte: null,
          totalProximasFaturas: 0,
          limiteUtilizado: 500,
          limiteDisponivel: 4500,
        },
      ];
    else if (path === 'transacoes') {
      let items = [transaction];
      const type = url.searchParams.get('tipo');
      if (type && type !== transaction.tipo) items = [];
      body = {
        conteudo: items,
        pagina: 0,
        tamanho: 20,
        totalElementos: items.length,
        totalPaginas: items.length ? 1 : 0,
      };
    } else if (path.endsWith('/parcelas')) body = [];
    else if (path.startsWith('transacoes/')) body = transaction;
    else if (path === 'faturas') body = [invoice];
    else if (path.startsWith('faturas/')) body = { resumo: invoice, compras: [], pagamentos: [] };
    else if (path === 'transferencias')
      body = { conteudo: [], pagina: 0, tamanho: 20, totalElementos: 0, totalPaginas: 0 };
    else if (path === 'orcamentos') body = [];
    else if (path === 'metas')
      body = [
        {
          id: 'goal',
          usuarioId: ids.user,
          nome: 'Uma viagem especial',
          valorObjetivo: 10000,
          dataInicio: today,
          dataLimite: null,
          situacao: 'ATIVA',
          progressoDisponivel: false,
          valorAcumulado: null,
          percentual: null,
          observacao: 'Sem aportes vinculados.',
        },
      ];
    if (req.method() !== 'GET' && path !== 'usuarios')
      body = { id: ids.transaction, ...(req.postDataJSON() || {}) };
    await route.fulfill({
      status: req.method() === 'POST' ? 201 : 200,
      contentType: 'application/json',
      body: JSON.stringify(body),
    });
  });
  return requests;
}
