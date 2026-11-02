# Planejador Financeiro — Frontend

Interface Angular 21 com identidade Liquid Glass, integrada à API REST Spring Boot existente. Não usa dados de demonstração durante a execução normal.

## Requisitos

- Node.js 24.x; execução validada com 24.21.0 e npm 11.19.0.
- Angular 21.2.25 e Angular CLI 21.2.29, mantidos na versão principal 21.
- TypeScript 5.9.3, compatível com Angular 21.
- Backend iniciado e conectado ao PostgreSQL para usar os dados financeiros.

As versões instaladas estão no package-lock.json. Use npm ci para reproduzi-las. Referência: [compatibilidade oficial Angular](https://angular.dev/reference/versions).

## Executar no VS Code

No primeiro terminal, a partir da raiz do repositório, inicie o backend seguindo [suas instruções](../backend/README.md). A senha DB_PASSWORD pertence ao processo do backend; não deve ser colocada no frontend.

No segundo terminal:

~~~powershell
cd frontend
npm.cmd ci
npm.cmd start
~~~

Abra `http://localhost:4200`. O proxy encaminha `/api` para `http://127.0.0.1:8080`, sem alterar o código Java nem exigir senha no navegador. Interrompa com Ctrl+C.

Se a API estiver em outra porta local, ajuste API_TARGET na sessão antes de iniciar o Angular:

~~~powershell
$env:API_TARGET = 'http://127.0.0.1:8081'
npm.cmd start
~~~

O frontend mostra um estado de reconexão quando o backend ou seu banco não estão disponíveis. Não apresenta saldo zero como se a consulta tivesse funcionado.

## Build e prévia do artefato

~~~powershell
npm.cmd run build
npm.cmd run preview
~~~

O build fica em `dist/frontend/browser`. A prévia usa a porta 4200, escuta somente em 127.0.0.1 e possui fallback para rotas Angular. Ela também encaminha /api para o backend local.

Para apontar a prévia para a API isolada de testes:

~~~powershell
node scripts/preview.mjs http://127.0.0.1:18082
~~~

A prévia aceita apenas uma API HTTP em localhost ou 127.0.0.1. Ela adapta o Origin de requisições da própria página para o proxy local, preservando outras origens para a política CORS do backend. Não é uma configuração de publicação pública.

## Funcionalidades e rotas

| Rota Angular | Uso |
| --- | --- |
| / | Saldo em contas ativas, resumo mensal, gastos semanais, categorias e movimentos recentes. |
| /transacoes | Cadastro, consulta, edição e cancelamento; filtros da API, paginação e parcelas. |
| /contas | Cadastro, edição, desativação e saldos calculados pelo backend. |
| /cartoes | Cartões, limites, calendário e acesso às faturas. |
| /faturas | Filtros, compras/parcelas, pagamentos parciais/totais e histórico. |
| /transferencias | Registro, consulta, confirmação e cancelamento entre contas. |
| /orcamentos | Planejamento mensal por categoria, previsto x realizado e edição dos itens. |
| /metas | Objetivos, datas e situação declarada, sem progresso fictício. |
| /categorias | Cadastro, filtros por tipo, edição e exclusão permitida. |
| /perfis | Cadastro, seleção e edição do perfil atual. |

A busca do cabeçalho encontra páginas. A busca da tela de transações considera os registros da página atual; para ampliar a consulta, utilize os filtros de período e os demais filtros da API.

## Integração e regras

- HttpClient envia X-Usuario-Id nos recursos da API para o perfil selecionado.
- A escolha de perfil é local e não equivale a autenticação.
- Signals controlam estados locais. RxJS/switchMap cancela consultas antigas ao trocar filtros ou perfil.
- Formulários são Reactive Forms, com validações locais e mensagens retornadas pelo servidor.
- Valores digitados em português, como 1.250,50, são enviados como decimais canônicos. O backend continua responsável por saldos, parcelas, faturas e regras financeiras.
- Crédito exige cartão e despesa; operações diretas exigem conta. PENDENTE não é exibida como liquidação.
- O saldo consolidado é a soma dos saldos retornados para contas ativas. Contas inativas mantêm histórico e podem ser consultadas.
- Consumo mensal considera a data e o valor total da compra, conforme o backend. Pagamento de fatura não vira nova despesa.
- A interface respeita conflitos 409, incluindo compras vinculadas a faturas com pagamento, sem apagar os dados do formulário.
- Metas não têm aportes no schema atual. O progresso fica indisponível, sem percentual inventado.
- O botão de privacidade oculta indicadores monetários. Formulários continuam mostrando os valores necessários para editar ou pagar.

Consulte o [contrato do backend](../docs/api-backend.md) e a [arquitetura do frontend](../docs/frontend.md).

## Testes unitários e navegador com API simulada

~~~powershell
npm.cmd test
npx.cmd playwright install chromium
npm.cmd run build
npm.cmd run e2e
~~~

Vitest executa testes de conversão de valores/datas, interceptação HTTP, propriedade, descarte de respostas antigas e formulários. Playwright verifica interface, acessibilidade funcional, erros, foco, navegação, perfis, redução de movimento e dimensões de desktop, tablet e celular.

Os mocks existem exclusivamente em e2e/mock-api.ts e nos testes unitários. Nenhum deles é importado pela aplicação.

## Testes com backend e PostgreSQL reais, isolados

Com os binários locais do PostgreSQL instalados, em um terminal dentro de frontend:

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\scripts\Test-Stack.ps1 -PostgresBin 'E:\postgres\18\bin'
~~~

O script cria um cluster temporário em `frontend/.runtime`, na porta 55434, e inicia o JAR do backend na porta 18082 usando o banco `planejador_financeiro_test`. Aplica o V1.sql somente nessa instância nova. Não altera fontes, configurações ou dados do banco de desenvolvimento.

Em outro terminal, sem uma prévia de desenvolvimento ocupando 4200:

~~~powershell
npm.cmd run build
npm.cmd run e2e:real
~~~

O Playwright inicia a prévia necessária se a porta estiver livre. Se já houver uma prévia, ela precisa apontar para a API isolada. Antes de gravar qualquer dado pela interface, a suíte confirma que tanto a API direta quanto o proxy do frontend estão conectados a planejador_financeiro_test com usuário backend_test. Caso contrário, falha antes das gravações.

Ao terminar:

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\scripts\Test-Stack.ps1 -Stop
~~~

O script recusa as portas de desenvolvimento 5432 e 8080. A autenticação trust existe somente no cluster temporário em loopback. Os dados, capturas e logs de teste ficam em .runtime, ignorada pelo Git. Nenhum teste financeiro deve usar o banco real.

Se o JAR não existir, o script gera o artefato Maven em backend/target, sem alterar os arquivos-fonte do backend. Para usar portas alternativas, configure também a prévia e E2E_API_URL de forma correspondente.

## Design e acessibilidade

O mockup HTML citado na tarefa não estava no repositório. A interface segue a especificação fornecida: fundo atmosférico escuro, superfícies de vidro em intensidades diferentes, paleta verde/ciano/azul e fontes Inter/Inter Tight locais.

Componentes são próprios, sem Bootstrap, Tailwind ou Angular Material. Gráficos usam SVG/CSS com dados da API. Animações de entrada são escalonadas e respeitam prefers-reduced-motion. Modais nativos mantêm o foco; há navegação por teclado, link de salto, rótulos e mensagens de estado.

As tabelas podem deslizar horizontalmente em telas estreitas, preservando as colunas e ações. A navegação mobile oferece acesso a todas as páginas por Mais.

## Documentação da entrega

[Relatório da Tarefa 03](../docs/relatorios/tarefa-03-frontend-completo.md), incluindo comandos executados, resultados, capturas e limitações.
