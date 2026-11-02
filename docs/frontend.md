# Frontend — arquitetura e decisões

## Organização

~~~text
frontend/
├── src/app/core/          Tipos, API, contexto, estados e coordenação de formulários
├── src/app/pages/         Páginas standalone carregadas por rota
├── src/app/shared/        Modais, campos, gráficos, tabela e componentes reutilizáveis
├── src/styles.scss       Tokens visuais, vidro, animações e responsividade
├── public/favicon.svg    Marca vetorial própria
├── e2e/                  Cenários Playwright simulados e reais
└── scripts/              Prévia local e infraestrutura isolada de testes
~~~

## Fluxo de leitura

~~~text
Rota Angular
  → componente standalone
  → resource (Signals + RxJS)
  → ApiService / HttpClient
  → interceptor com X-Usuario-Id
  → API Spring Boot
  → PostgreSQL
  → DTO JSON
  → estado de sucesso, vazio ou erro
  → componentes e gráficos
~~~

resource usa switchMap para cancelar a assinatura da consulta anterior quando o perfil, o filtro ou a revisão global muda. Durante a nova consulta, os dados anteriores são removidos. Falha de leitura mantém um estado de erro explícito, sem produzir saldo zero ou um sucesso simulado.

WorkspaceService valida a conexão pelo endpoint health/database, carrega usuários e referências de contas/categorias/cartões. ContextService mantém o perfil selecionado e a preferência de privacidade; apenas essas preferências são persistidas em localStorage. Valores financeiros não são tratados como um banco local.

## Fluxo de gravação

~~~text
Formulário reativo
  → validação de formato e campos
  → confirmação quando necessária
  → conversão do texto monetário brasileiro
  → POST / PUT / PATCH / DELETE da API existente
  → validação e transação no backend
  → DTO ou erro HTTP
  → atualização dos recursos e mensagem ao usuário
~~~

O botão de envio fica bloqueado enquanto a requisição está em andamento. Erros preservam os dados preenchidos. Respostas 400/404/409/503 são apresentadas sem stack trace ou detalhes internos. Regras financeiras definitivas permanecem no servidor.

O formulário de transação é específico para seus relacionamentos condicionais. O editor de orçamento usa FormArray para os itens. Os cadastros simples compartilham um editor configurado por campos, reduzindo repetição sem criar um framework de formulários externo.

Modais usam dialog/showModal, com foco inicial, retorno do foco, Escape e nomes acessíveis únicos, inclusive ao abrir o pagamento sobre os detalhes da fatura.

## Valores e datas

O formulário aceita o padrão brasileiro e transforma o valor em decimal canônico com duas casas. Centavos são conferidos com BigInt nas validações locais e na soma de saldos já calculados pela API. Não existe um segundo algoritmo de geração de parcelas, faturas ou saldo no frontend.

O consolidado da visão geral considera contas ativas, identificado na própria interface. O saldo de cada conta vem de /contas/{id}/saldo. Indicadores de consumo, previsão, realização, dívida e limites vêm dos DTOs respectivos.

LocalDate é formatado sem deslocamento de fuso. A data atual usa America/Sao_Paulo. Não é atribuído progresso a uma meta que o backend informa como indisponível.

## Identidade visual

A referência HTML não estava disponível. A alternativa especificada na tarefa foi aplicada com tokens de cor, gradientes CSS e superfícies transparentes com backdrop-filter. Fontes variáveis Inter e Inter Tight são empacotadas localmente.

A composição separa navegação lateral, ferramentas, mensagem principal, saldo destacado, indicadores, gráficos e movimentos recentes. No mobile, o conteúdo vira uma coluna e a navegação inferior abre o menu completo.

riseIn, slideL, slideR, popIn, lineUp, wipeRight, wipeDown, drawLine e sheen aparecem em entradas e mudanças de contexto. Não há animação contínua decorativa. O indicador de carregamento só gira durante uma consulta. prefers-reduced-motion reduz animações e transições.

## Limitações herdadas do contrato

- Não há autenticação por senha; seleção de perfil não é controle de identidade.
- Não há endpoint de busca textual global de transações; a busca textual é identificada como local à página.
- O calendário de cartões com faturas não pode ser alterado.
- Uma fatura com qualquer pagamento bloqueia alteração/cancelamento das compras e inclusão de novas compras nela.
- Não há estorno de pagamentos nem aportes em metas na API. A interface não oferece ações sem suporte.
- A integração com o banco de desenvolvimento exige credenciais válidas no backend. Os testes reais desta entrega usam uma instância isolada.

Os contratos completos permanecem em [api-backend.md](api-backend.md). Esta tarefa não altera esses contratos nem o schema.
