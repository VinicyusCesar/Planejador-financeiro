# Tarefa 03 — Frontend completo do Planejador Financeiro

Data: 02/11/2026, horário de São Paulo.

## 1. Resumo

Foi criado o projeto Angular 21 em frontend/, com componentes standalone, Router, HttpClient, Reactive Forms, Signals e RxJS. A interface cobre as operações efetivamente disponíveis no backend e utiliza a identidade Liquid Glass definida na tarefa.

Foram entregues dashboard, transações, contas, cartões, faturas/pagamentos, transferências, orçamento, metas, categorias e perfis. O build de produção passou. A validação final teve **70 testes aprovados: 36 unitários, 26 de navegador com API simulada e 8 cenários com Spring Boot/PostgreSQL reais isolados**.

O backend e seu schema foram preservados. Nenhuma biblioteca completa de UI foi utilizada. Não houve Docker, publicação pública, commit ou push.

O arquivo planejador_financeiro_liquid_glass.html não estava disponível. Foi aplicada a alternativa visual explicitamente descrita no anexo: paleta, fontes, superfícies, composição e animações. O texto recebido terminava no início da seção 5.1; a implementação seguiu os requisitos disponíveis e os contratos reais da API, conforme mantido pelo usuário.

## 2. Inspeção e preservação

Antes da implementação, foram verificados repositório, inexistência de frontend, documentação da API, regras financeiras, relatório anterior, controllers e DTOs Java. As operações não foram presumidas apenas pelos nomes das tarefas anteriores.

O frontend foi criado com Angular CLI 21, sem recriar ou editar o projeto Spring Boot. A inspeção e a conferência final dos **99 arquivos do backend fora de target** resultaram no mesmo digest agregado:

~~~text
14329EE62D837F993AB453393B76597083F76BE2F4D57A943E84F0D8F2668386
~~~

O V1.sql também manteve seu SHA-256:

~~~text
2BC31F5245FD4C4529B0534D5A12E5C4980E44A04B2EF71D564BDEE99A626BD7
~~~

O serviço PostgreSQL de desenvolvimento continuou ativo. Não houve gravação de teste no banco real nem mudança em credenciais, tabelas, índices ou configurações do backend.

## 3. Tecnologias e versões

| Tecnologia | Versão validada / uso |
| --- | --- |
| Angular | 21.2.25; versão principal 21 preservada. |
| Angular CLI / build | 21.2.29. |
| Node.js | 24.21.0. |
| npm | 11.19.0. |
| TypeScript | 5.9.3. |
| RxJS | 7.8.2. |
| Inter / Inter Tight | Fontes variáveis empacotadas localmente. |
| Vitest | 4.1.12, pelo builder de testes Angular. |
| Playwright | 1.64.1, com Chromium para desktop e mobile. |
| Backend de integração | JAR Spring Boot existente com PostgreSQL 18.6 isolado. |

As dependências são reproduzidas pelo package-lock.json. Não foram usados Angular 22, Bootstrap, Tailwind, Angular Material ou biblioteca de componentes equivalente.

Referências: [compatibilidade Angular](https://angular.dev/reference/versions) e [testes Angular](https://angular.dev/guide/testing).

## 4. Arquivos e organização

### Aplicação

| Local | Responsabilidade |
| --- | --- |
| frontend/src/app/app.ts e app.html | Shell, navegação desktop/mobile, perfil selecionado, busca de páginas, privacidade, conexão, mensagens e modais globais. |
| frontend/src/app/app.config.ts | Providers de Router/HttpClient, interceptação e locale pt-BR. |
| frontend/src/app/app.routes.ts | Rotas standalone com carregamento por página e títulos do documento. |
| frontend/src/app/core/models.ts | Tipos dos DTOs, enums, parâmetros e rótulos compatíveis com o backend. |
| frontend/src/app/core/api.service.ts | Acesso HTTP, X-Usuario-Id e mensagens de erro. |
| frontend/src/app/core/context.service.ts | Perfil e preferência de privacidade, revisão dos dados e preferências locais. |
| frontend/src/app/core/workspace.service.ts | Conexão, usuários e referências de contas, cartões e categorias. |
| frontend/src/app/core/resource.ts | Estados assíncronos, cancelamento de consultas antigas e limpeza de dados na troca de contexto. |
| frontend/src/app/core/format.ts | Valores brasileiros, centavos, validações e datas sem deslocamento indevido de fuso. |
| frontend/src/app/core/dialog.service.ts e editors.service.ts | Coordenação dos cadastros e das operações confirmadas. |
| frontend/src/app/core/transaction-editor.service.ts | Abertura do cadastro/edição de transação. |
| frontend/src/app/core/toast.service.ts | Mensagens de sucesso/erro e descarte. |
| frontend/src/app/pages/ | Onze páginas, incluindo dashboard e página não encontrada. |
| frontend/src/app/shared/ | Ícones, modais, editor de campos, transação, orçamento, seletor de mês, tabela, pipes e gráfico semanal. |
| frontend/src/styles.scss | Tokens Liquid Glass, responsividade, estados visuais e animações. |
| frontend/public/favicon.svg | Marca vetorial própria. |

### Configuração, testes e documentação

- package.json, package-lock.json, angular.json e tsconfigs: execução, compilação, testes e versões.
- proxy.conf.cjs: API local de desenvolvimento, padrão 127.0.0.1:8080.
- .nvmrc: linha Node 24.
- scripts/preview.mjs: prévia do build restrita ao loopback, com fallback de rotas e proxy local.
- scripts/Test-Stack.ps1: criação e encerramento da infraestrutura descartável para integração.
- src/app/core/*.spec.ts e shared/forms.spec.ts: 36 testes unitários.
- e2e/mock-api.ts e app.mock.spec.ts: contrato simulado e testes de interface.
- e2e/real-api.ts e finance.real.spec.ts: integração com a API e o PostgreSQL de teste.
- playwright.config.ts e playwright.real.config.ts: projetos desktop/mobile e diretórios independentes de resultados.
- frontend/README.md: requisitos, execução, build, testes e limitações.
- docs/frontend.md: arquitetura e decisões.
- Este relatório: evidências finais da entrega.

Foram removidos apenas o app.scss vazio e o favicon.ico padrão gerados pelo scaffold, substituído pela marca SVG. Não houve remoção de arquivos preexistentes do projeto.

## 5. Funcionalidades entregues

| Área | Funcionalidades |
| --- | --- |
| Visão geral | Soma dos saldos de contas ativas; receitas, despesas e saldo por competência; semana selecionada; gastos por categoria e últimos movimentos. |
| Transações | Receita/despesa direta e crédito; pendências; edição/cancelamento permitido; filtros da API, paginação, consulta e parcelas. |
| Contas | Cadastro com saldo inicial, edição de nome/tipo, desativação, consulta de saldo e exibição de inativas. |
| Cartões | Cadastro, edição, desativação, limites utilizado/disponível, calendário e acesso às faturas. |
| Faturas | Consulta e filtros por cartão/ciclo/vencimento; compras, parcelas, pagamentos, saldo pendente e pagamento parcial/total. |
| Transferências | Cadastro, período/paginação, confirmação de pendente e cancelamento. |
| Orçamento | Seleção mensal, cadastro/edição de itens, meta de economia, previsto/realizado e gastos sem previsão. |
| Metas | Cadastro, valor objetivo, datas, edição e situação declarada. Não inventa progresso. |
| Categorias | Cadastro, filtro de tipo, edição e exclusão quando permitida. |
| Perfis | Cadastro, seleção e edição do perfil atual. Referências financeiras respeitam o proprietário selecionado. |

Todos os botões de ação têm comportamento implementado. Operações não suportadas pela API, como aporte em meta e estorno de pagamento, não foram inventadas.

A busca do cabeçalho navega entre páginas. A busca textual de transações é identificada como local à página atual, pois o backend não possui busca textual global.

## 6. Integração e funcionamento

~~~text
Componente standalone
    → formulário reativo ou filtro
    → ApiService / HttpClient
    → interceptor X-Usuario-Id
    → Controller Spring Boot existente
    → Service / Repository
    → PostgreSQL
    → DTO JSON
    → estado da tela e mensagem ao usuário
~~~

Signals representam estados locais e revisões. resource usa RxJS/switchMap para descartar respostas antigas ao mudar perfil ou filtro. Durante a nova consulta, o resultado antigo é removido. Uma falha não é substituída por sucesso ou saldo fictício.

O frontend normaliza valores digitados no padrão brasileiro para decimais canônicos. Não implementa um segundo cálculo de parcelas, faturas ou saldo bancário. Os totais de domínio, limites e valores de orçamento realizado vêm do servidor. A soma consolidada utiliza os saldos retornados para contas ativas.

As transações no crédito exigem cartão e despesa, removem conta e liquidação direta, e usam a quantidade de parcelas. As diretas exigem conta. Pendências não são tratadas como pagamento realizado. O servidor continua sendo a autoridade final.

O envio é bloqueado enquanto a requisição está em andamento. Erros mantêm os campos preenchidos. Respostas de conflito são explicadas sem ocultar a regra ou tentar contorná-la.

A seleção de perfil é um contexto local, não autenticação. O interceptor só envia sua identificação à API configurada. Nenhuma senha PostgreSQL é armazenada ou solicitada pelo frontend.

## 7. Identidade visual e acessibilidade

A composição usa fundo escuro atmosférico com luz verde-petróleo/azul, bordas suaves e vidro com diferentes transparências. Não há superfícies brancas opacas ou gradiente roxo. Inter é a fonte de interface; Inter Tight destaca títulos e valores, com números tabulares.

Foram implementadas riseIn, slideL, slideR, popIn, lineUp, wipeRight, wipeDown, drawLine e sheen. A entrada segue a ordem de navegação, ferramentas, título, indicadores, gráficos e elementos secundários. O brilho especular ocorre na entrada, sem animação decorativa contínua.

A preferência prefers-reduced-motion é respeitada. Há estados de carregamento, hover, feedback de sucesso/erro e transições. O gráfico semanal e a distribuição por categoria usam dados reais da API, com SVG/CSS próprios.

A navegação mobile oferece o menu completo. As checagens incluíram larguras de 320, 360, 390, 768, 1280 e 1440 pixels. As tabelas podem deslizar dentro de seu próprio contêiner; a página não exige rolagem horizontal.

Modais usam dialog/showModal, mantêm foco e retornam ao controle de origem. Há Escape, rótulos, nomes acessíveis únicos, link para pular ao conteúdo e mensagens de estado. Esses comportamentos foram verificados no Chromium; não se afirma uma certificação completa de acessibilidade.

## 8. Testes executados

### Comandos principais

~~~powershell
npm.cmd install --no-fund --no-audit
npm.cmd run build
npm.cmd test
npx.cmd playwright install chromium
npm.cmd run e2e
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\scripts\Test-Stack.ps1
node scripts/preview.mjs http://127.0.0.1:18082
npm.cmd run e2e:real
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\scripts\Test-Stack.ps1 -Stop
~~~

A prévia respondeu HTTP 200 e o JAR confirmou banco, schema e usuário da instância isolada.

### Resultado final

| Validação | Resultado |
| --- | --- |
| Build Angular de produção | Aprovado, com páginas carregadas por rota e dentro do orçamento configurado. |
| Vitest — 4 arquivos | 36 aprovados. |
| Playwright com API simulada — desktop/mobile | 26 aprovados. |
| Playwright com Spring Boot/PostgreSQL isolados — desktop/mobile | 8 aprovados. |
| **Total de testes** | **70 aprovados, zero falhas ou ignorados nas execuções finais.** |

Os testes unitários cobrem valores/datas, cabeçalhos, ausência de perfil, prevenção de vazamento a outro servidor, filtros, descarte de resposta antiga, validação dos formulários, duplicidade de envio e preservação dos dados após erro.

Os testes de interface cobrem rotas, visualização brasileira, privacidade, teclado/foco, Escape, onboarding, falta de conexão, perfil, conflitos de categoria, crédito, metas sem progresso fictício, redução de movimento e responsividade.

Os oito cenários reais verificaram, pela interface:

1. Cadastro de perfil, conta e categorias; edição de conta; receita e despesa; pendência/confirmação; cancelamento e desativação.
2. Compra de 100,00 em três parcelas de 33,34/33,33/33,33; conta sem débito na compra; pagamento parcial/total; liberação proporcional de limite; ausência de despesa duplicada; bloqueio de edição de compra paga.
3. Transferência e cancelamento com saldos consistentes; orçamento previsto/realizado e edição; criação de meta sem progresso e atualização da situação.
4. Cadastro/edição/desativação de cartão; edição de perfil; dashboard, gráficos e movimentos alimentados pelo PostgreSQL.

Cada cenário foi executado em desktop e mobile. A repetição de assertivas dentro de um cenário não foi contada como testes adicionais.

### Isolamento dos dados

O PostgreSQL de teste escutou em 127.0.0.1:55434 e o backend em 127.0.0.1:18082. O banco era planejador_financeiro_test, usuário backend_test. O SQL original foi aplicado somente nesse cluster novo, dentro de frontend/.runtime.

Antes de gravações pela interface, os testes conferem a API direta e o proxy do frontend, exigindo o nome exato do banco e usuário de teste. Assim, uma prévia apontada ao banco de desenvolvimento provoca falha antes das ações de gravação.

As fixtures existem apenas nos testes. Não são dados de demonstração da aplicação. A instância isolada, o JAR de teste e a prévia local foram encerrados após a validação.

## 9. Capturas

Foram geradas e inspecionadas capturas desktop/mobile, com as animações finalizadas para registrar o estado estável. Arquivos locais em .runtime, ignorados pelo Git:

- [Dashboard com API real — desktop](../../frontend/.runtime/screenshots/integracao-real-desktop.png).
- [Dashboard com API real — mobile](../../frontend/.runtime/screenshots/integracao-real-mobile.png).
- [Dashboard com contrato simulado — desktop](../../frontend/.runtime/screenshots/dashboard-desktop.png).
- [Dashboard com contrato simulado — mobile](../../frontend/.runtime/screenshots/dashboard-mobile.png).

As imagens com API real mostram registros criados exclusivamente no banco descartável de integração, não as finanças do usuário.

## 10. Ajustes e limitações

- O mockup HTML não estava no repositório; a implementação usa a descrição visual fornecida, sem alegar reprodução pixel a pixel de um arquivo não disponível.
- A revisão automática bloqueou o comando da prévia Angular com a mensagem “blocked by policy”. A validação foi concluída por uma alternativa restrita ao loopback, servindo o build estático e a API local, sem alterar políticas.
- A primeira compilação encontrou um tipo de validador e namespaces SCSS de fontes que precisavam de ajuste. Ambos foram corrigidos; o build final passou.
- Rodadas intermediárias de interface encontraram seletores de nomes acessíveis e rótulos que foram corrigidos. A checagem adicional de 320 px revelou um ajuste no cabeçalho e na contenção da animação lateral, resolvido e verificado.
- Uma execução paralela dos dois runners compartilhava a pasta padrão de artefatos, causando erros de arquivos ausentes no encerramento. As saídas foram separadas em test-results e test-results-real; ambas as suítes passaram na execução final.
- Capturas iniciais ocorreram durante animações de entrada. As capturas finais registram o estado estável, com animações finalizadas.
- O script PostgreSQL foi ajustado para confirmar disponibilidade por pg_isready, evitando depender apenas do ExitCode do launcher no Windows.
- A validação utilizou Chromium e emulação mobile; não foi executada em aparelhos físicos nem em todos os navegadores.
- A conexão do banco real de desenvolvimento ainda exige DB_PASSWORD válida no processo Spring Boot. Ela não foi substituída pela instância de teste nem declarada como validada.
- As limitações do backend continuam válidas: ausência de autenticação, estorno de pagamentos e aportes em metas; restrição de calendário e alteração de compras com fatura paga.
- A interface não foi publicada. A entrega é o projeto local integrado, conforme o contexto do repositório e do backend local.

## 11. Execução e revisão

As instruções completas estão no [README do frontend](../../frontend/README.md). Para uso normal, iniciar o backend com suas credenciais e, em outro terminal:

~~~powershell
cd frontend
npm.cmd ci
npm.cmd start
~~~

Abrir http://localhost:4200. Com backend indisponível, a tela apresenta a opção de reconexão.

Os requisitos fornecidos de Angular 21, identidade Liquid Glass, componentes próprios, integração, formulários, responsividade, testes e documentação foram implementados. Nenhum trabalho adicional será iniciado antes da revisão do usuário.
