-- The whole schema in one baseline: tenants and the people in them, the sites
-- a company consists of, the images that brand both, a tenant's own AI access,
-- and the session store.

-- Multi-tenancy foundation: every customer is a tenant, and all business data
-- is scoped to one. Only the super-users stand outside.
CREATE TABLE tenants (
    id            UUID        PRIMARY KEY,
    name          TEXT        NOT NULL,
    -- The Kennung: what a person types on the login page to say which tenant
    -- they belong to, so the same username may exist in several tenants.
    slug          TEXT        NOT NULL,
    website       TEXT,
    -- How the sidebar brands the tenant: small logo beside the name, or one
    -- large logo filling the whole brand area.
    logo_display  TEXT        NOT NULL DEFAULT 'WITH_NAME'
                              CHECK (logo_display IN ('WITH_NAME', 'LOGO_ONLY')),
    -- Brand color as hex (#RRGGBB); the default primary color for this
    -- tenant's users. A user's own theme choice still wins.
    primary_color TEXT,
    reply_signature TEXT      NOT NULL DEFAULT '',
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX tenants_slug_key ON tenants (slug);

-- A company consists of a headquarters and any number of branches (Filialen);
-- the address and contact data live here, the tenant keeps its identity and
-- branding.
CREATE TABLE branches (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name            TEXT        NOT NULL,
    is_headquarters BOOLEAN     NOT NULL,
    street          TEXT,
    postal_code     TEXT,
    city            TEXT,
    country         TEXT,
    phone           TEXT,
    fax             TEXT,
    email           TEXT,
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX branches_tenant_id_idx ON branches (tenant_id);

-- Exactly one headquarters per tenant.
CREATE UNIQUE INDEX branches_headquarters_key ON branches (tenant_id) WHERE is_headquarters;

-- The name identifies the site in dropdowns, so it is unique per tenant.
CREATE UNIQUE INDEX branches_name_key ON branches (tenant_id, LOWER(name));

-- A person who can sign in. The table is named users, which SQL reserves, so
-- the entity is called AppUser; a super-user carries no tenant.
CREATE TABLE users (
    id            UUID        PRIMARY KEY,
    tenant_id     UUID        REFERENCES tenants (id) ON DELETE CASCADE,
    username      TEXT        NOT NULL,
    first_name    TEXT        NOT NULL,
    last_name     TEXT        NOT NULL,
    birth_date    DATE,
    joined_at     DATE,
    -- The site the user works at. Deleting a branch only unsets the
    -- assignment, it never blocks on assigned users.
    branch_id     UUID        REFERENCES branches (id) ON DELETE SET NULL,
    -- Contact address only; the login name is the username above.
    email         TEXT,
    phone         TEXT,
    fax           TEXT,
    -- The job title a signature names under the person: "Projektleiterin".
    position      TEXT,
    password_hash TEXT        NOT NULL,
    role          TEXT        NOT NULL CHECK (role IN ('ADMIN', 'USER', 'SUPERUSER')),
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX users_tenant_id_idx ON users (tenant_id);

-- Unique within the tenant; the login resolves the name inside the Kennung.
CREATE UNIQUE INDEX users_username_key ON users (tenant_id, LOWER(username));

-- Super-users belong to no tenant, so their names are unique among each other.
CREATE UNIQUE INDEX users_superuser_username_key ON users (LOWER(username)) WHERE tenant_id IS NULL;

-- Whose data an automatically written signature carries. Added after users
-- because the two tables point at each other.
ALTER TABLE tenants
    ADD COLUMN signature_user_id UUID REFERENCES users (id) ON DELETE SET NULL;

-- Profile pictures live in their own table, not as a column on users: the user
-- row is loaded on practically every request (auth, tenant scoping), and a
-- multi-megabyte image must not travel along.
CREATE TABLE user_avatars (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    image        BYTEA       NOT NULL,
    content_type TEXT        NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);

-- The company logo, for the same reason as the avatars above.
CREATE TABLE tenant_logos (
    id           UUID        PRIMARY KEY,
    tenant_id    UUID        NOT NULL UNIQUE REFERENCES tenants (id) ON DELETE CASCADE,
    image        BYTEA       NOT NULL,
    content_type TEXT        NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);

-- A tenant's own Anthropic access. Optional: without a key the platform's own
-- credentials are used, which is what every tenant starts on.
CREATE TABLE tenant_ai_settings (
    id         UUID        PRIMARY KEY,
    tenant_id  UUID        NOT NULL UNIQUE REFERENCES tenants (id) ON DELETE CASCADE,
    -- Encrypted by the application before it ever reaches this column; the
    -- stored value carries the marker that says so.
    api_key    TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Verbatim copy of schema-postgresql.sql from spring-session-jdbc 4.1.0.
-- Flyway owns the schema, so Spring Session's own initializer is disabled
-- (spring.session.jdbc.initialize-schema=never). Sessions live in the
-- database so they survive restarts and work across multiple instances.
CREATE TABLE SPRING_SESSION (
	PRIMARY_ID CHAR(36) NOT NULL,
	SESSION_ID CHAR(36) NOT NULL,
	CREATION_TIME BIGINT NOT NULL,
	LAST_ACCESS_TIME BIGINT NOT NULL,
	MAX_INACTIVE_INTERVAL INT NOT NULL,
	EXPIRY_TIME BIGINT NOT NULL,
	PRINCIPAL_NAME VARCHAR(100),
	CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
	SESSION_PRIMARY_ID CHAR(36) NOT NULL,
	ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
	ATTRIBUTE_BYTES BYTEA NOT NULL,
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);
