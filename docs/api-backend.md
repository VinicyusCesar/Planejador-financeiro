# Contrato da API do backend

Base local: `http://127.0.0.1:8080/api`. Conteúdo JSON, IDs UUID, datas `AAAA-MM-DD` e valores monetários com até duas casas decimais.

## Identificação e propriedade

Os recursos financeiros exigem o cabeçalho `X-Usuario-Id: UUID`. O backend aplica esse proprietário às consultas e valida que contas, cartões, categorias e faturas relacionados pertençam a ele. Um ID fora do escopo responde 404.

O cabeçalho identifica o usuário selecionado na aplicação local; não é autenticação. Cadastro, listagem e consulta de usuários permitem escolher o usuário inicial. A atualização de usuário exige que o cabeçalho corresponda ao ID da rota. A aplicação escuta somente em `127.0.0.1` e o CORS permite `http://localhost:4200`, incluindo `Content-Type` e `X-Usuario-Id`.

## Rotas implementadas

Todas as rotas abaixo usam o prefixo `/api`.

| Método | Rota | Operação |
| --- | --- | --- |
| GET | /health | Estado da aplicação. |
| GET | /health/database | Consulta real de banco, schema e usuário. |
| GET / POST | /usuarios | Listar / cadastrar usuários. |
| GET / PUT | /usuarios/{id} | Consultar / atualizar nome e email. |
| GET / POST | /contas | Listar / cadastrar contas do usuário. |
| GET / PUT | /contas/{id} | Consultar / atualizar nome e tipo. |
| PATCH | /contas/{id}/desativacao | Desativar preservando histórico. |
| GET | /contas/{id}/saldo | Saldo bancário calculado até hoje. |
| GET / POST | /categorias | Listar / cadastrar categorias. |
| GET / PUT / DELETE | /categorias/{id} | Consultar / editar / excluir sem dependências. |
| GET / POST | /transacoes | Listar com filtros / cadastrar receita ou despesa. |
| GET / PUT | /transacoes/{id} | Consultar / editar transação permitida. |
| POST | /transacoes/{id}/cancelamento | Cancelar sem apagar o histórico. |
| GET | /transacoes/{id}/parcelas | Parcelas e vencimentos da compra. |
| GET / POST | /cartoes | Listar / cadastrar cartões, com limites calculados. |
| GET / PUT | /cartoes/{id} | Consultar / editar cartão. |
| PATCH | /cartoes/{id}/desativacao | Desativar cartão. |
| GET | /cartoes/{id}/faturas | Listar faturas do cartão por período. |
| GET | /faturas | Listar faturas do usuário, com filtros. |
| GET | /faturas/{id} | Resumo, compras/parcelas e pagamentos. |
| POST | /faturas/{id}/pagamentos | Registrar pagamento parcial ou total. |
| GET / POST | /transferencias | Listar / registrar transferência. |
| GET | /transferencias/{id} | Consultar transferência. |
| POST | /transferencias/{id}/confirmacao | Confirmar transferência pendente. |
| POST | /transferencias/{id}/cancelamento | Cancelar transferência. |
| GET / POST | /orcamentos | Listar / criar orçamento mensal. |
| GET / PUT | /orcamentos/{id} | Consultar / substituir dados e itens. |
| GET / POST | /metas | Listar / criar metas. |
| GET / PUT | /metas/{id} | Consultar / atualizar meta e situação declarada. |
| GET | /dashboard/resumo-mensal | Receitas, despesas, competência e orçamento. |
| GET | /dashboard/resumo-semanal | Comparação semanal e gastos por dia. |
| GET | /dashboard/cartoes | Faturas atual/seguinte, dívida futura e limites. |

## Filtros e paginação

- `GET /categorias?tipo=DESPESA` filtra o tipo exato: RECEITA, DESPESA ou AMBOS.
- `GET /transacoes` aceita `inicio`, `fim`, `tipo`, `contaId`, `cartaoId`, `categoriaId` e `situacao`. Datas inclusivas, sobre `dataTransacao`.
- Transações e transferências aceitam `pagina` (padrão 0) e `tamanho` (padrão 20, de 1 a 100). A ordenação é por data e ID decrescentes.
- `GET /transferencias` também aceita `inicio` e `fim` inclusivos.
- Faturas aceitam `inicio` e `fim` inclusivos sobre o vencimento. `/faturas` aceita `cartaoId`.
- `periodo=ATUAL|ANTERIORES|PROXIMAS` exige cartão e compara o mês da fatura ao ciclo da data atual. Não cria faturas em GET.
- `GET /dashboard/resumo-mensal?ano=2026&mes=10` exige ano entre 2000 e 9998 e mês entre 1 e 12.
- `GET /dashboard/resumo-semanal?dataReferencia=2026-10-26` usa a semana dessa data; sem parâmetro, usa hoje. Segunda-feira inicia a semana.

Resposta paginada:

~~~json
{"conteudo":[],"pagina":0,"tamanho":20,"totalElementos":0,"totalPaginas":0}
~~~

## Cadastros

`POST /usuarios`:

~~~json
{"nome":"Pessoa de exemplo","email":"pessoa@example.com"}
~~~

Retorna 201, Location e `id`, `nome`, `email` e `criadoEm`. O email é normalizado para minúsculas.

`POST /contas`, já com o cabeçalho do usuário:

~~~json
{"nome":"Conta principal","tipo":"CORRENTE","saldoInicial":1000.00}
~~~

Tipos: CORRENTE, POUPANCA, DINHEIRO, CARTEIRA e OUTRA. O saldo inicial pode ser negativo, como permite o SQL. `PUT /contas/{id}` recebe apenas nome e tipo; saldo inicial não é editável.

`POST /categorias`:

~~~json
{"nome":"Alimentação","tipo":"DESPESA"}
~~~

`POST /cartoes` ou `PUT /cartoes/{id}`:

~~~json
{
  "nome":"Cartão principal",
  "finalCartao":"1234",
  "contaPagamentoId":null,
  "limite":5000.00,
  "diaFechamento":25,
  "diaVencimento":5
}
~~~

A conta padrão de pagamento é opcional. `finalCartao` é opcional e, quando informado, exige quatro dígitos. Fechamento e vencimento aceitam 1 a 31.

## Transações

Nos exemplos, substitua os marcadores de ID por UUIDs retornados nos cadastros.

Despesa PIX confirmada, `POST /transacoes`:

~~~json
{
  "categoriaId":"UUID_DA_CATEGORIA",
  "contaId":"UUID_DA_CONTA",
  "cartaoId":null,
  "descricao":"Mercado",
  "valor":150.00,
  "tipo":"DESPESA",
  "meioPagamento":"PIX",
  "dataTransacao":"2026-10-20"
}
~~~

Resposta 201 com `Location` e todos os campos do registro:

~~~json
{
  "id":"UUID_DA_TRANSACAO",
  "usuarioId":"UUID_DO_USUARIO",
  "categoriaId":"UUID_DA_CATEGORIA",
  "contaId":"UUID_DA_CONTA",
  "cartaoId":null,
  "descricao":"Mercado",
  "valor":150.00,
  "tipo":"DESPESA",
  "meioPagamento":"PIX",
  "condicao":"AVISTA",
  "quantidadeParcelas":1,
  "dataTransacao":"2026-10-20",
  "dataVencimento":null,
  "dataLiquidacao":"2026-10-20",
  "situacao":"CONFIRMADA"
}
~~~

Receitas usam `tipo=RECEITA` e categoria RECEITA ou AMBOS. Os meios aceitos são PIX, DINHEIRO, DEBITO, CREDITO, TRANSFERENCIA e BOLETO. Uma movimentação TRANSFERENCIA nesta rota representa receita/despesa externa; transferências internas usam a rota específica.

Para uma operação pendente, informe `situacao=PENDENTE` e nenhuma `dataLiquidacao`. Para confirmar ou liquidar posteriormente, use PUT com o corpo completo e `situacao=CONFIRMADA`. A data de liquidação pode ser informada para operações diretas; sem ela, assume a data da transação. Deve estar entre a transação e hoje.

Compra de R$ 1.200 em três parcelas:

~~~json
{
  "categoriaId":"UUID_DA_CATEGORIA",
  "contaId":null,
  "cartaoId":"UUID_DO_CARTAO",
  "descricao":"Computador",
  "valor":1200.00,
  "tipo":"DESPESA",
  "meioPagamento":"CREDITO",
  "condicao":"PARCELADO",
  "quantidadeParcelas":3,
  "dataTransacao":"2026-10-20"
}
~~~

O backend gera três parcelas de 400,00 em faturas consecutivas. Consulte `/transacoes/{id}/parcelas`. Compras no crédito não recebem `dataLiquidacao` nem `dataVencimento` na transação: os vencimentos pertencem às faturas.

`situacao` omitida significa CONFIRMADA; `quantidadeParcelas` omitida significa 1. `condicao` omitida é derivada da quantidade. Para crédito à vista, utilize uma parcela. Quantidade entre 1 e 600, respeitando o mínimo de um centavo por parcela. CANCELADA só pode ser aplicada pela operação de cancelamento.

## Faturas e pagamentos

`POST /faturas/{id}/pagamentos`:

~~~json
{"contaId":"UUID_DA_CONTA","valor":200.00,"dataPagamento":"2026-10-26"}
~~~

Retorna 201 com `id`, `faturaId`, `contaId`, `valor` e `dataPagamento`. O valor não pode exceder o saldo pendente nem ter data futura. O pagamento não cria transação de despesa.

`GET /faturas/{id}` retorna:

~~~json
{
  "resumo":{
    "id":"UUID_DA_FATURA","cartaoId":"UUID_DO_CARTAO",
    "mesReferencia":10,"anoReferencia":2026,
    "dataFechamento":"2026-10-25","dataVencimento":"2026-11-05",
    "situacao":"FECHADA","total":400.00,"totalPago":200.00,"saldoPendente":200.00
  },
  "compras":[],
  "pagamentos":[]
}
~~~

As listas no exemplo ilustram o formato; em uma fatura real, contêm as parcelas confirmadas e os pagamentos registrados. As compras incluem descrição, transação, data de compra, número e quantidade de parcelas. Parcelas canceladas não compõem consumo ou total devido.

## Transferências

`POST /transferencias`:

~~~json
{
  "contaOrigemId":"UUID_DA_ORIGEM",
  "contaDestinoId":"UUID_DO_DESTINO",
  "valor":500.00,
  "dataTransferencia":"2026-10-26",
  "situacao":"CONFIRMADA"
}
~~~

As contas devem ser diferentes, ativas e do mesmo proprietário. PENDENTE não movimenta saldo; CONFIRMADA movimenta na data informada, sem permitir data futura. Cancelar desfaz o efeito calculado, preservando o registro.

## Planejamento e metas

`POST /orcamentos` ou `PUT /orcamentos/{id}`:

~~~json
{
  "ano":2026,"mes":10,"metaEconomia":200.00,
  "itens":[{"categoriaId":"UUID_DA_CATEGORIA","valorPrevisto":800.00}]
}
~~~

A lista pode estar vazia. PUT substitui todos os itens. Não repetir categorias. Cada item usa DESPESA ou AMBOS. Existe no máximo um orçamento por usuário/mês/ano. A resposta inclui valores por categoria, total previsto, total realizado, diferença e realizado sem previsão.

`POST /metas` ou `PUT /metas/{id}`:

~~~json
{
  "nome":"Reserva de emergência","valorObjetivo":10000.00,
  "dataInicio":"2026-10-26","dataLimite":"2027-10-26","situacao":"ATIVA"
}
~~~

Situações: ATIVA, CONCLUIDA e CANCELADA. O retorno sempre informa `progressoDisponivel=false`, `valorAcumulado=null` e `percentual=null`, porque o schema não registra aportes vinculados à meta. A situação é uma declaração do usuário.

## Erros e códigos HTTP

Criações retornam 201; consultas e atualizações, 200; exclusão, desativação e cancelamento, 204 sem corpo.

~~~json
{"codigo":"PAGAMENTO_EXCEDENTE","mensagem":"O pagamento não pode ultrapassar o saldo pendente da fatura.","campos":{}}
~~~

- 400: JSON, parâmetros, valores ou relacionamentos obrigatórios inválidos.
- 404: recurso inexistente ou pertencente a outro usuário.
- 409: duplicidade, dependências, limite insuficiente ou fatura com pagamento que impede alteração.
- 500: falha interna não prevista, com mensagem genérica.
- 503: indisponibilidade de acesso ao banco.
- 405/415: método HTTP ou formato de conteúdo não suportado.

Erros de validação incluem o mapa `campos` com mensagens por campo. O health preserva o contrato da Tarefa 01: `success` e `message` em falhas de banco. Nenhuma resposta inclui senha, stack trace ou SQL interno.
