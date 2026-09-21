-- A source is a feed by default, and a plain web page where the site offers no feed. What the
-- type decides is only which reader touches the source: both hand back the same articles, so
-- nothing downstream knows the difference.
--
-- Everything already in a catalog was entered as a feed URL, so the default is right for it and
-- stays in place for the same reason: a source without a stated type is a feed.
ALTER TABLE feeds
    ADD COLUMN type TEXT NOT NULL DEFAULT 'FEED'
        CHECK (type IN ('FEED', 'PAGE'));
