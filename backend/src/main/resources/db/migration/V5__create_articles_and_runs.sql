-- What a run brings back, and the run itself.

-- One story, hanging on the source it came from rather than on a person: two users who read the
-- same source share the article and with it the cost of having the AI look at it once.
--
-- positive_summary, ranking and category_id stay empty until that happens. They are created here
-- rather than added later because the step that fills them adds no table of its own, and an
-- ALTER TABLE for three columns that were always going to exist is wear without purpose.
CREATE TABLE articles (
    id               UUID        PRIMARY KEY,
    feed_id          UUID        NOT NULL REFERENCES feeds (id) ON DELETE CASCADE,
    category_id      UUID        REFERENCES categories (id) ON DELETE SET NULL,
    guid             TEXT        NOT NULL,
    title            TEXT        NOT NULL,
    link             TEXT        NOT NULL,
    published_at     TIMESTAMPTZ,
    teaser           TEXT        NOT NULL,
    positive_summary TEXT,
    ranking          INTEGER     CHECK (ranking BETWEEN 0 AND 10),
    processed_at     TIMESTAMPTZ,
    fetched_at       TIMESTAMPTZ NOT NULL
);

-- The guid is what the source calls the entry, the link where it names nothing else. Together
-- with the source it decides whether a story is one we already have.
CREATE UNIQUE INDEX articles_guid_key ON articles (feed_id, guid);
CREATE INDEX articles_feed_id_idx ON articles (feed_id);
CREATE INDEX articles_category_id_idx ON articles (category_id);

-- An empty category_id is the story that has not been rated yet, or the one the AI could not
-- place. The board shows both under "Sonstiges", so the index is worth having.
CREATE INDEX articles_unplaced_idx ON articles (feed_id) WHERE category_id IS NULL;

-- A run belongs to the tenant, not to whoever pressed the button: it works through every source
-- the tenant has, and what it brings back belongs to all of them. Two people pressing at once
-- share one run and one progress bar.
CREATE TABLE news_runs (
    id                 UUID        PRIMARY KEY,
    tenant_id          UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    status             TEXT        NOT NULL CHECK (status IN ('RUNNING', 'DONE', 'FAILED')),
    total_articles     INTEGER     NOT NULL,
    processed_articles INTEGER     NOT NULL,
    started_at         TIMESTAMPTZ NOT NULL,
    finished_at        TIMESTAMPTZ,
    error              TEXT
);

CREATE INDEX news_runs_tenant_id_idx ON news_runs (tenant_id);

-- Unique rather than plain, and partial so only running rows collide: a tenant can have any
-- number of finished runs behind it and at most one in flight. Two people pressing the button in
-- the same instant is otherwise a race that no amount of checking beforehand can close, and this
-- is also the query that happens on every press.
CREATE UNIQUE INDEX news_runs_running_key ON news_runs (tenant_id) WHERE status = 'RUNNING';
