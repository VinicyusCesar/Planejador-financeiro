import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'Visão geral · Planejador',
    loadComponent: () => import('./pages/dashboard').then((module) => module.DashboardPage),
  },
  {
    path: 'transacoes',
    title: 'Transações · Planejador',
    loadComponent: () => import('./pages/transactions').then((module) => module.TransactionsPage),
  },
  {
    path: 'contas',
    title: 'Contas · Planejador',
    loadComponent: () => import('./pages/accounts').then((module) => module.AccountsPage),
  },
  {
    path: 'cartoes',
    title: 'Cartões · Planejador',
    loadComponent: () => import('./pages/cards').then((module) => module.CardsPage),
  },
  {
    path: 'faturas',
    title: 'Faturas · Planejador',
    loadComponent: () => import('./pages/invoices').then((module) => module.InvoicesPage),
  },
  {
    path: 'transferencias',
    title: 'Transferências · Planejador',
    loadComponent: () => import('./pages/transfers').then((module) => module.TransfersPage),
  },
  {
    path: 'orcamentos',
    title: 'Orçamentos · Planejador',
    loadComponent: () => import('./pages/budgets').then((module) => module.BudgetsPage),
  },
  {
    path: 'metas',
    title: 'Metas · Planejador',
    loadComponent: () => import('./pages/goals').then((module) => module.GoalsPage),
  },
  {
    path: 'categorias',
    title: 'Categorias · Planejador',
    loadComponent: () => import('./pages/categories').then((module) => module.CategoriesPage),
  },
  {
    path: 'perfis',
    title: 'Perfis · Planejador',
    loadComponent: () => import('./pages/profiles').then((module) => module.ProfilesPage),
  },
  {
    path: '**',
    title: 'Página não encontrada · Planejador',
    loadComponent: () => import('./pages/not-found').then((module) => module.NotFoundPage),
  },
];
