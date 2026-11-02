export type Id = string;
export type TipoConta = 'CORRENTE' | 'POUPANCA' | 'DINHEIRO' | 'CARTEIRA' | 'OUTRA';
export type TipoCategoria = 'RECEITA' | 'DESPESA' | 'AMBOS';
export type TipoTransacao = 'RECEITA' | 'DESPESA';
export type Situacao = 'PENDENTE' | 'CONFIRMADA' | 'CANCELADA';
export type MeioPagamento = 'PIX' | 'DINHEIRO' | 'DEBITO' | 'CREDITO' | 'TRANSFERENCIA' | 'BOLETO';
export type Condicao = 'AVISTA' | 'PARCELADO';
export type SituacaoFatura = 'ABERTA' | 'FECHADA' | 'PAGA' | 'ATRASADA';
export type PeriodoFatura = 'ATUAL' | 'ANTERIORES' | 'PROXIMAS';
export type SituacaoMeta = 'ATIVA' | 'CONCLUIDA' | 'CANCELADA';
export interface Usuario {
  id: Id;
  nome: string;
  email: string;
  criadoEm: string;
}
export interface Conta {
  id: Id;
  usuarioId: Id;
  nome: string;
  tipo: TipoConta;
  saldoInicial: number;
  ativa: boolean;
}
export interface Saldo {
  contaId: Id;
  saldoAtual: number;
}
export interface Categoria {
  id: Id;
  usuarioId: Id;
  nome: string;
  tipo: TipoCategoria;
}
export interface Cartao {
  id: Id;
  usuarioId: Id;
  contaPagamentoId: Id | null;
  nome: string;
  finalCartao: string | null;
  limite: number;
  diaFechamento: number;
  diaVencimento: number;
  ativo: boolean;
  limiteUtilizado: number;
  limiteDisponivel: number;
}
export interface Transacao {
  id: Id;
  usuarioId: Id;
  categoriaId: Id;
  contaId: Id | null;
  cartaoId: Id | null;
  descricao: string;
  valor: number;
  tipo: TipoTransacao;
  meioPagamento: MeioPagamento;
  condicao: Condicao;
  quantidadeParcelas: number;
  dataTransacao: string;
  dataVencimento: string | null;
  dataLiquidacao: string | null;
  situacao: Situacao;
}
export interface TransacaoEntrada {
  categoriaId: Id;
  contaId: Id | null;
  cartaoId: Id | null;
  descricao: string;
  valor: string;
  tipo: TipoTransacao;
  meioPagamento: MeioPagamento;
  condicao: Condicao;
  quantidadeParcelas: number;
  dataTransacao: string;
  dataVencimento: string | null;
  dataLiquidacao: string | null;
  situacao: Exclude<Situacao, 'CANCELADA'>;
}
export interface Parcela {
  id: Id;
  transacaoId: Id;
  faturaId: Id;
  numero: number;
  valor: number;
  dataVencimento: string;
}
export interface Fatura {
  id: Id;
  cartaoId: Id;
  mesReferencia: number;
  anoReferencia: number;
  dataFechamento: string;
  dataVencimento: string;
  situacao: SituacaoFatura;
  total: number;
  totalPago: number;
  saldoPendente: number;
}
export interface CompraFatura {
  parcelaId: Id;
  transacaoId: Id;
  descricao: string;
  dataCompra: string;
  numero: number;
  quantidadeParcelas: number;
  valor: number;
}
export interface Pagamento {
  id: Id;
  faturaId: Id;
  contaId: Id;
  valor: number;
  dataPagamento: string;
}
export interface DetalheFatura {
  resumo: Fatura;
  compras: CompraFatura[];
  pagamentos: Pagamento[];
}
export interface Transferencia {
  id: Id;
  usuarioId: Id;
  contaOrigemId: Id;
  contaDestinoId: Id;
  valor: number;
  dataTransferencia: string;
  situacao: Situacao;
}
export interface ItemOrcamento {
  id: Id;
  categoriaId: Id;
  categoria: string;
  previsto: number;
  realizado: number;
  diferenca: number;
}
export interface Orcamento {
  id: Id;
  usuarioId: Id;
  ano: number;
  mes: number;
  metaEconomia: number | null;
  totalPrevisto: number;
  totalRealizado: number;
  diferenca: number;
  realizadoSemPrevisao: number;
  itens: ItemOrcamento[];
}
export interface Meta {
  id: Id;
  usuarioId: Id;
  nome: string;
  valorObjetivo: number;
  dataInicio: string;
  dataLimite: string | null;
  situacao: SituacaoMeta;
  progressoDisponivel: boolean;
  valorAcumulado: number | null;
  percentual: number | null;
  observacao: string;
}
export interface CategoriaGasto {
  categoriaId: Id;
  categoria: string;
  total: number;
}
export interface DiaGasto {
  data: string;
  total: number;
}
export interface Mensal {
  ano: number;
  mes: number;
  receitas: number;
  despesas: number;
  saldoDoMes: number;
  gastosPorCategoria: CategoriaGasto[];
  orcamentoPrevisto: number;
  orcamentoRealizado: number;
  diferencaOrcamento: number;
}
export interface Semanal {
  inicio: string;
  fim: string;
  totalSemana: number;
  totalSemanaAnterior: number;
  diferenca: number;
  variacaoPercentual: number | null;
  comparacaoPercentualDisponivel: boolean;
  gastosPorDia: DiaGasto[];
}
export interface ResumoCartao {
  cartaoId: Id;
  nome: string;
  faturaAtual: Fatura | null;
  faturaSeguinte: Fatura | null;
  totalProximasFaturas: number;
  limiteUtilizado: number;
  limiteDisponivel: number;
}
export interface Pagina<T> {
  conteudo: T[];
  pagina: number;
  tamanho: number;
  totalElementos: number;
  totalPaginas: number;
}
export interface ApiErro {
  codigo?: string;
  mensagem?: string;
  message?: string;
  campos?: Record<string, string>;
}
export interface DatabaseHealth {
  success: boolean;
  database: string;
  schema: string;
  user: string;
}
export type Query = Record<string, string | number | boolean | null | undefined>;
export interface Opcao {
  value: string;
  label: string;
}
export const TIPOS_CONTA: Opcao[] = [
  { value: 'CORRENTE', label: 'Conta corrente' },
  { value: 'POUPANCA', label: 'Poupança' },
  { value: 'DINHEIRO', label: 'Dinheiro' },
  { value: 'CARTEIRA', label: 'Carteira' },
  { value: 'OUTRA', label: 'Outra' },
];
export const TIPOS_CATEGORIA: Opcao[] = [
  { value: 'DESPESA', label: 'Despesa' },
  { value: 'RECEITA', label: 'Receita' },
  { value: 'AMBOS', label: 'Receitas e despesas' },
];
export const MEIOS: Opcao[] = [
  { value: 'PIX', label: 'Pix' },
  { value: 'DINHEIRO', label: 'Dinheiro' },
  { value: 'DEBITO', label: 'Cartão de débito' },
  { value: 'CREDITO', label: 'Cartão de crédito' },
  { value: 'TRANSFERENCIA', label: 'Transferência externa' },
  { value: 'BOLETO', label: 'Boleto' },
];
export const SITUACOES: Opcao[] = [
  { value: 'CONFIRMADA', label: 'Confirmada' },
  { value: 'PENDENTE', label: 'Pendente' },
];
export const SITUACOES_META: Opcao[] = [
  { value: 'ATIVA', label: 'Em andamento' },
  { value: 'CONCLUIDA', label: 'Concluída' },
  { value: 'CANCELADA', label: 'Cancelada' },
];
export const LABELS: Record<string, string> = {
  CORRENTE: 'Conta corrente',
  POUPANCA: 'Poupança',
  DINHEIRO: 'Dinheiro',
  CARTEIRA: 'Carteira',
  OUTRA: 'Outra',
  RECEITA: 'Receita',
  DESPESA: 'Despesa',
  AMBOS: 'Ambos',
  PENDENTE: 'Pendente',
  CONFIRMADA: 'Confirmada',
  CANCELADA: 'Cancelada',
  ABERTA: 'Aberta',
  FECHADA: 'Fechada',
  PAGA: 'Paga',
  ATRASADA: 'Atrasada',
  ATIVA: 'Em andamento',
  CONCLUIDA: 'Concluída',
  AVISTA: 'À vista',
  PARCELADO: 'Parcelado',
  PIX: 'Pix',
  DEBITO: 'Débito',
  CREDITO: 'Crédito',
  TRANSFERENCIA: 'Transferência externa',
  BOLETO: 'Boleto',
};
