# Hashiya

## No AI attribution

Never add anything that says or implies the work was written or assisted by an AI:

- Commits: the author is always `Fady <fady.fouad.a@gmail.com>` (a SessionStart hook in `.claude/settings.json` sets it; check `git config user.email` before the first commit). No `Co-Authored-By`, no `Claude-Session`, no `noreply@anthropic.com`.
- Branch names, PR titles and descriptions, comments, READMEs, docs, code comments and generated files: no AI credits, badges, links or "Generated with" lines.

Before pushing, check `git log --format='%an <%ae>' origin/main..HEAD` shows only that author.

## iOS work in cloud sessions

The implementation plans under `docs/` were written before later review fixes landed. When a plan gives a "(full file)" block for a file that already exists, merge its changes into the current file instead of replacing it, and diff against `origin/main` to confirm nothing already merged was removed. Cloud sessions cannot build or test iOS; say so rather than claiming it works.
