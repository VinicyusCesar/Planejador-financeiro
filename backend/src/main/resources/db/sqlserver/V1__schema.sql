-- SQL Server 2019+; executar apenas no banco novo.
CREATE TABLE dbo.usuario (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    nome VARCHAR(400) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL CHECK (LEN(nome + '#') - 1 <= 100),
    email VARCHAR(600) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL UNIQUE CHECK (LEN(email + '#') - 1 <= 150),
    criado_em DATETIME2(6) NOT NULL DEFAULT SYSDATETIME()
);

CREATE TABLE dbo.conta (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    usuario_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.usuario(id),
    nome VARCHAR(400) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL CHECK (LEN(nome + '#') - 1 <= 100),
    tipo VARCHAR(30) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL CHECK (tipo IN ('CORRENTE', 'POUPANCA', 'DINHEIRO', 'CARTEIRA', 'OUTRA')),
    saldo_inicial DECIMAL(15,2) NOT NULL DEFAULT 0,
    ativa BIT NOT NULL DEFAULT 1
);

CREATE TABLE dbo.categoria (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    usuario_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.usuario(id),
    nome VARCHAR(320) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL CHECK (LEN(nome + '#') - 1 <= 80),
    tipo VARCHAR(15) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL CHECK (tipo IN ('RECEITA', 'DESPESA', 'AMBOS')),
    UNIQUE (usuario_id, nome, tipo)
);

CREATE TABLE dbo.cartao_credito (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    usuario_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.usuario(id),
    conta_pagamento_id UNIQUEIDENTIFIER REFERENCES dbo.conta(id),
    nome VARCHAR(400) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL CHECK (LEN(nome + '#') - 1 <= 100),
    final_cartao CHAR(4),
    limite DECIMAL(15,2) NOT NULL CHECK (limite >= 0),
    dia_fechamento INT NOT NULL CHECK (dia_fechamento BETWEEN 1 AND 31),
    dia_vencimento INT NOT NULL CHECK (dia_vencimento BETWEEN 1 AND 31),
    ativo BIT NOT NULL DEFAULT 1
);

CREATE TABLE dbo.transacao (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    usuario_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.usuario(id),
    conta_id UNIQUEIDENTIFIER REFERENCES dbo.conta(id),
    cartao_id UNIQUEIDENTIFIER REFERENCES dbo.cartao_credito(id),
    categoria_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.categoria(id),
    descricao VARCHAR(800) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL CHECK (LEN(descricao + '#') - 1 <= 200),
    valor DECIMAL(15,2) NOT NULL CHECK (valor > 0),
    tipo VARCHAR(10) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL CHECK (tipo IN ('RECEITA', 'DESPESA')),
    meio_pagamento VARCHAR(20) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL CHECK (meio_pagamento IN ('PIX', 'DINHEIRO', 'DEBITO', 'CREDITO', 'TRANSFERENCIA', 'BOLETO')),
    condicao VARCHAR(10) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL DEFAULT 'AVISTA' CHECK (condicao IN ('AVISTA', 'PARCELADO')),
    quantidade_parcelas INT NOT NULL DEFAULT 1,
    data_transacao DATE NOT NULL,
    data_vencimento DATE,
    data_liquidacao DATE,
    situacao VARCHAR(15) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL DEFAULT 'CONFIRMADA' CHECK (situacao IN ('PENDENTE', 'CONFIRMADA', 'CANCELADA')),
    CHECK ((condicao = 'AVISTA' AND quantidade_parcelas = 1) OR (condicao = 'PARCELADO' AND quantidade_parcelas > 1)),
    CHECK ((meio_pagamento = 'CREDITO' AND tipo = 'DESPESA' AND cartao_id IS NOT NULL AND conta_id IS NULL)
       OR (meio_pagamento <> 'CREDITO' AND cartao_id IS NULL AND conta_id IS NOT NULL)),
    CHECK (condicao <> 'PARCELADO' OR meio_pagamento = 'CREDITO')
);

CREATE TABLE dbo.fatura (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    cartao_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.cartao_credito(id),
    mes_referencia INT NOT NULL CHECK (mes_referencia BETWEEN 1 AND 12),
    ano_referencia INT NOT NULL CHECK (ano_referencia >= 2000),
    data_fechamento DATE NOT NULL,
    data_vencimento DATE NOT NULL,
    situacao VARCHAR(10) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL DEFAULT 'ABERTA' CHECK (situacao IN ('ABERTA', 'FECHADA', 'PAGA', 'ATRASADA')),
    UNIQUE (cartao_id, mes_referencia, ano_referencia)
);

CREATE TABLE dbo.parcela (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    transacao_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.transacao(id),
    fatura_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.fatura(id),
    numero INT NOT NULL CHECK (numero > 0),
    valor DECIMAL(15,2) NOT NULL CHECK (valor > 0),
    UNIQUE (transacao_id, numero)
);

CREATE TABLE dbo.pagamento_fatura (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    fatura_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.fatura(id),
    conta_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.conta(id),
    valor DECIMAL(15,2) NOT NULL CHECK (valor > 0),
    data_pagamento DATE NOT NULL
);

CREATE TABLE dbo.transferencia (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    usuario_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.usuario(id),
    conta_origem_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.conta(id),
    conta_destino_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.conta(id),
    valor DECIMAL(15,2) NOT NULL CHECK (valor > 0),
    data_transferencia DATE NOT NULL,
    situacao VARCHAR(15) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL DEFAULT 'CONFIRMADA' CHECK (situacao IN ('PENDENTE', 'CONFIRMADA', 'CANCELADA')),
    CHECK (conta_origem_id <> conta_destino_id)
);

CREATE TABLE dbo.orcamento (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    usuario_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.usuario(id),
    mes INT NOT NULL CHECK (mes BETWEEN 1 AND 12),
    ano INT NOT NULL CHECK (ano >= 2000),
    meta_economia DECIMAL(15,2) CHECK (meta_economia >= 0),
    UNIQUE (usuario_id, mes, ano)
);

CREATE TABLE dbo.item_orcamento (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    orcamento_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.orcamento(id),
    categoria_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.categoria(id),
    valor_previsto DECIMAL(15,2) NOT NULL CHECK (valor_previsto >= 0),
    UNIQUE (orcamento_id, categoria_id)
);

CREATE TABLE dbo.meta_financeira (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    usuario_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.usuario(id),
    nome VARCHAR(600) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL CHECK (LEN(nome + '#') - 1 <= 150),
    valor_objetivo DECIMAL(15,2) NOT NULL CHECK (valor_objetivo > 0),
    data_inicio DATE NOT NULL,
    data_limite DATE,
    situacao VARCHAR(15) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL DEFAULT 'ATIVA' CHECK (situacao IN ('ATIVA', 'CONCLUIDA', 'CANCELADA'))
);

CREATE INDEX idx_transacao_usuario_data ON dbo.transacao(usuario_id, data_transacao);
CREATE INDEX idx_transacao_categoria ON dbo.transacao(categoria_id);
CREATE INDEX idx_transacao_conta ON dbo.transacao(conta_id);
CREATE INDEX idx_transacao_cartao ON dbo.transacao(cartao_id);
CREATE INDEX idx_parcela_fatura ON dbo.parcela(fatura_id);
CREATE INDEX idx_pagamento_fatura ON dbo.pagamento_fatura(fatura_id);
CREATE INDEX idx_transferencia_origem ON dbo.transferencia(conta_origem_id);
CREATE INDEX idx_transferencia_destino ON dbo.transferencia(conta_destino_id);
