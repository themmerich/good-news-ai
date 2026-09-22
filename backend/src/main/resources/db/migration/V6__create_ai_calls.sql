-- What the AI cost, one row per call.

-- A call, not a story: ten stories go to the model in one bundle and are billed as one call.
-- Dividing that by the bundle size would put a number on a single story that nobody was ever
-- charged.
--
-- cost_usd holds what the call cost when it was made, not what it would cost today. Rates change,
-- and a page that quietly rewrote last month's total after a price change would be worse than no
-- page. Empty where the model has no rate configured: a new model should stand out rather than
-- look like free work.
CREATE TABLE ai_calls (
    id            UUID           PRIMARY KEY,
    tenant_id     UUID           NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    purpose       TEXT           NOT NULL CHECK (purpose IN ('RATING', 'KEY_TEST')),
    model         TEXT           NOT NULL,
    input_tokens  INTEGER        NOT NULL,
    output_tokens INTEGER        NOT NULL,
    cost_usd      NUMERIC(12, 6),
    called_at     TIMESTAMPTZ    NOT NULL
);

-- Both questions the page asks — the four running totals and the list, newest first — are this
-- one index. There is no other query on this table.
CREATE INDEX ai_calls_tenant_called_idx ON ai_calls (tenant_id, called_at DESC);
