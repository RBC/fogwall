-- Persist parsed Co-authored-by: trailers per commit (#146, consolidating #114), mirroring the existing
-- signed_off_by column. Newline-joined values, in order of appearance. Additive and nullable — pre-existing
-- push_commits rows keep NULL (no back-fill: the raw commit message is not re-parsed retroactively).
ALTER TABLE push_commits ADD COLUMN IF NOT EXISTS co_authored_by TEXT;
