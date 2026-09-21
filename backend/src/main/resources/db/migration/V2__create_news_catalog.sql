-- The catalog a tenant's admin curates, and what each user picked from it.
-- The articles themselves and the runs that fetch them follow in a later migration.

-- Categories are two levels deep at most: an empty parent_id is a top-level
-- category, everything else hangs directly under one. A category without
-- children is a leaf, and only leaves carry feeds and become tabs.
CREATE TABLE categories (
    id         UUID        PRIMARY KEY,
    tenant_id  UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    parent_id  UUID        REFERENCES categories (id) ON DELETE CASCADE,
    name       TEXT        NOT NULL,
    sort_order INTEGER     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX categories_tenant_id_idx ON categories (tenant_id);
CREATE INDEX categories_parent_id_idx ON categories (parent_id);

-- The name is unique among its siblings. Two indexes rather than one, because
-- NULLs never collide in a unique index: without the second one, a tenant
-- could create "Sport" at the top level twice.
CREATE UNIQUE INDEX categories_name_key ON categories (tenant_id, parent_id, LOWER(name))
    WHERE parent_id IS NOT NULL;
CREATE UNIQUE INDEX categories_root_name_key ON categories (tenant_id, LOWER(name))
    WHERE parent_id IS NULL;

-- A source of news. last_fetched_at and last_error stay empty until the
-- fetching exists; the columns are here so that step adds no migration of its
-- own, and the entity picks them up when it has something to write into them.
CREATE TABLE feeds (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    category_id     UUID        NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    name            TEXT        NOT NULL,
    url             TEXT        NOT NULL,
    last_fetched_at TIMESTAMPTZ,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX feeds_tenant_id_idx ON feeds (tenant_id);
CREATE INDEX feeds_category_id_idx ON feeds (category_id);

-- The same source is fetched once per tenant, however many categories would
-- like to have it.
CREATE UNIQUE INDEX feeds_url_key ON feeds (tenant_id, LOWER(url));

-- What a user picked. A category counts as picked as soon as one of its feeds
-- is, so there is nothing to store about categories.
CREATE TABLE user_feeds (
    id      UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    feed_id UUID NOT NULL REFERENCES feeds (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX user_feeds_key ON user_feeds (user_id, feed_id);
CREATE INDEX user_feeds_feed_id_idx ON user_feeds (feed_id);
