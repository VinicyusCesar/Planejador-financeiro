CREATE TABLE usuario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE conta (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    nome VARCHAR(100) NOT NULL,
    tipo VARCHAR(30) NOT NULL CHECK (tipo IN ('CORRENTE', 'POUPANCA', 'DINHEIRO', 'CARTEIRA', 'OUTRA')),
    saldo_inicial NUMERIC(15,2) NOT NULL DEFAULT 0,
    ativa BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE categoria (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    nome VARCHAR(80) NOT NULL,
    tipo VARCHAR(15) NOT NULL CHECK (tipo IN ('RECEITA', 'DESPESA', 'AMBOS')),
    UNIQUE (usuario_id, nome, tipo)
);

CREATE TABLE cartao_credito (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    conta_pagamento_id UUID REFERENCES conta(id),
    nome VARCHAR(100) NOT NULL,
    final_cartao CHAR(4),
    limite NUMERIC(15,2) NOT NULL CHECK (limite >= 0),
    dia_fechamento INT NOT NULL CHECK (dia_fechamento BETWEEN 1 AND 31),
    dia_vencimento INT NOT NULL CHECK (dia_vencimento BETWEEN 1 AND 31),
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE transacao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    conta_id UUID REFERENCES conta(id),
    cartao_id UUID REFERENCES cartao_credito(id),
    categoria_id UUID NOT NULL REFERENCES categoria(id),
    descricao VARCHAR(200) NOT NULL,
    valor NUMERIC(15,2) NOT NULL CHECK (valor > 0),
    tipo VARCHAR(10) NOT NULL CHECK (tipo IN ('RECEITA', 'DESPESA')),
    meio_pagamento VARCHAR(20) NOT NULL CHECK (meio_pagamento IN ('PIX', 'DINHEIRO', 'DEBITO', 'CREDITO', 'TRANSFERENCIA', 'BOLETO')),
    condicao VARCHAR(10) NOT NULL DEFAULT 'AVISTA' CHECK (condicao IN ('AVISTA', 'PARCELADO')),
    quantidade_parcelas INT NOT NULL DEFAULT 1,
    data_transacao DATE NOT NULL,
    data_vencimento DATE,
    data_liquidacao DATE,
    situacao VARCHAR(15) NOT NULL DEFAULT 'CONFIRMADA' CHECK (situacao IN ('PENDENTE', 'CONFIRMADA', 'CANCELADA')),
    CHECK ((condicao = 'AVISTA' AND quantidade_parcelas = 1) OR (condicao = 'PARCELADO' AND quantidade_parcelas > 1)),
    CHECK ((meio_pagamento = 'CREDITO' AND tipo = 'DESPESA' AND cartao_id IS NOT NULL AND conta_id IS NULL)
       OR (meio_pagamento <> 'CREDITO' AND cartao_id IS NULL AND conta_id IS NOT NULL)),
    CHECK (condicao <> 'PARCELADO' OR meio_pagamento = 'CREDITO')
);

CREATE TABLE fatura (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cartao_id UUID NOT NULL REFERENCES cartao_credito(id),
    mes_referencia INT NOT NULL CHECK (mes_referencia BETWEEN 1 AND 12),
    ano_referencia INT NOT NULL CHECK (ano_referencia >= 2000),
    data_fechamento DATE NOT NULL,
    data_vencimento DATE NOT NULL,
    situacao VARCHAR(10) NOT NULL DEFAULT 'ABERTA' CHECK (situacao IN ('ABERTA', 'FECHADA', 'PAGA', 'ATRASADA')),
    UNIQUE (cartao_id, mes_referencia, ano_referencia)
);

CREATE TABLE parcela (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transacao_id UUID NOT NULL REFERENCES transacao(id),
    fatura_id UUID NOT NULL REFERENCES fatura(id),
    numero INT NOT NULL CHECK (numero > 0),
    valor NUMERIC(15,2) NOT NULL CHECK (valor > 0),
    UNIQUE (transacao_id, numero)
);

CREATE TABLE pagamento_fatura (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fatura_id UUID NOT NULL REFERENCES fatura(id),
    conta_id UUID NOT NULL REFERENCES conta(id),
    valor NUMERIC(15,2) NOT NULL CHECK (valor > 0),
    data_pagamento DATE NOT NULL
);

CREATE TABLE transferencia (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    conta_origem_id UUID NOT NULL REFERENCES conta(id),
    conta_destino_id UUID NOT NULL REFERENCES conta(id),
    valor NUMERIC(15,2) NOT NULL CHECK (valor > 0),
    data_transferencia DATE NOT NULL,
    situacao VARCHAR(15) NOT NULL DEFAULT 'CONFIRMADA' CHECK (situacao IN ('PENDENTE', 'CONFIRMADA', 'CANCELADA')),
    CHECK (conta_origem_id <> conta_destino_id)
);

CREATE TABLE orcamento (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    mes INT NOT NULL CHECK (mes BETWEEN 1 AND 12),
    ano INT NOT NULL CHECK (ano >= 2000),
    meta_economia NUMERIC(15,2) CHECK (meta_economia >= 0),
    UNIQUE (usuario_id, mes, ano)
);

CREATE TABLE item_orcamento (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    orcamento_id UUID NOT NULL REFERENCES orcamento(id),
    categoria_id UUID NOT NULL REFERENCES categoria(id),
    valor_previsto NUMERIC(15,2) NOT NULL CHECK (valor_previsto >= 0),
    UNIQUE (orcamento_id, categoria_id)
);

CREATE TABLE meta_financeira (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    nome VARCHAR(150) NOT NULL,
    valor_objetivo NUMERIC(15,2) NOT NULL CHECK (valor_objetivo > 0),
    data_inicio DATE NOT NULL,
    data_limite DATE,
    situacao VARCHAR(15) NOT NULL DEFAULT 'ATIVA' CHECK (situacao IN ('ATIVA', 'CONCLUIDA', 'CANCELADA'))
);

CREATE INDEX idx_transacao_usuario_data ON transacao(usuario_id, data_transacao);
CREATE INDEX idx_transacao_categoria ON transacao(categoria_id);
CREATE INDEX idx_transacao_conta ON transacao(conta_id);
CREATE INDEX idx_transacao_cartao ON transacao(cartao_id);
CREATE INDEX idx_parcela_fatura ON parcela(fatura_id);
CREATE INDEX idx_pagamento_fatura ON pagamento_fatura(fatura_id);
CREATE INDEX idx_transferencia_origem ON transferencia(conta_origem_id);
CREATE INDEX idx_transferencia_destino ON transferencia(conta_destino_id);
