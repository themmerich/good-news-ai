-- The category moves from the source to the single story. One feed from a news site brings
-- politics, society and sport in one, so a source carries no category any more; where a story
-- belongs is decided per story, by the AI, in the same call that writes its summary and ranking.
-- The list of categories becomes flat, and what a user picks are categories rather than sources.

-- Lift every subcategory to the top level, giving it its former parent's name in front where two
-- names would otherwise collide. Nothing shipped creates subcategories, so this most likely
-- renames nothing — but a migration that stops halfway on a grown development database costs more
-- than the statement does. Kept to 100 characters, the limit the API validates against, so a
-- renamed category stays editable through the form.
UPDATE categories AS child
SET name = LEFT(parent.name || ' ' || child.name, 100)
FROM categories AS parent
WHERE child.parent_id = parent.id
  AND EXISTS (SELECT 1
              FROM categories AS other
              WHERE other.tenant_id = child.tenant_id
                AND other.id <> child.id
                AND LOWER(other.name) = LOWER(child.name));

-- Whatever still collides after that gets a counter. This should never fire; it is here so the
-- unique index below cannot be the thing that keeps the application from starting.
UPDATE categories AS c
SET name = LEFT(c.name, 96) || ' (' || d.seq || ')'
FROM (SELECT id,
             ROW_NUMBER() OVER (PARTITION BY tenant_id, LOWER(name) ORDER BY created_at, id) AS seq
      FROM categories) AS d
WHERE c.id = d.id
  AND d.seq > 1;

-- Lifting the children leaves their old position among their siblings behind, so a tenant that
-- had a tree ends up with four categories all claiming position 0 and a list whose order is
-- decided by whatever breaks the tie. Numbering the whole list from zero settles it, and doing so
-- while parent_id still exists lets the tree be flattened in the order it was read on screen: a
-- former top-level category, then the children that hung under it, then the next one.
UPDATE categories AS c
SET sort_order = d.seq
FROM (SELECT child.id,
             ROW_NUMBER() OVER (
                 PARTITION BY child.tenant_id
                 ORDER BY COALESCE(parent.sort_order, child.sort_order),
                          CASE WHEN child.parent_id IS NULL THEN 0 ELSE 1 END,
                          child.sort_order,
                          LOWER(child.name)
             ) - 1 AS seq
      FROM categories AS child
               LEFT JOIN categories AS parent ON parent.id = child.parent_id) AS d
WHERE c.id = d.id
  AND c.sort_order <> d.seq;

-- Both old indexes on the name were partial, because NULLs never collide in a unique index and a
-- tenant could otherwise have created "Sport" twice at the top level. Without parents there is
-- one row of siblings and one plain index.
DROP INDEX categories_name_key;
DROP INDEX categories_root_name_key;
DROP INDEX categories_parent_id_idx;
ALTER TABLE categories DROP COLUMN parent_id;

CREATE UNIQUE INDEX categories_name_key ON categories (tenant_id, LOWER(name));

DROP INDEX feeds_category_id_idx;
ALTER TABLE feeds DROP COLUMN category_id;

-- What a user wants to see. The rows the old table held said which sources to fetch, and that is
-- no longer a user's decision: a run works through everything the tenant has, because a story's
-- category is only known once it has been rated.
DROP TABLE user_feeds;

CREATE TABLE user_categories (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX user_categories_key ON user_categories (user_id, category_id);
CREATE INDEX user_categories_category_id_idx ON user_categories (category_id);
