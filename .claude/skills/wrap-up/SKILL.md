---
name: wrap-up
description: End-of-task handoff for the Groceries app. Use when Josh says "wrap up", "let's stop here", or is about to start a new session. Records durable changes in CLAUDE.md so the next session starts with the right context.
---

# Wrap up a work session

The next session only knows what's in the repo, `CLAUDE.md`, and git history. Make sure anything durable
from this session lands in one of those, and keep `CLAUDE.md` short, since it loads into every session.

1. **Review the session.** Run `git status` and `git log --oneline` since the last wrap-up, and recall what
   was discussed. List: what shipped, decisions Josh made, new gotchas or workflow changes, open questions,
   and anything waiting on Josh (Firebase/Vercel/Apple steps, rules to publish).

2. **Update `CLAUDE.md`**, editing in place rather than appending:
   - New stable facts or gotchas go in the matching section (Backend facts, How to build and ship, …).
   - Decisions Josh made that future work must respect go in "Working with Josh" or next to the feature.
   - Replace the **Status** section with the current state: latest released versions, what's in progress,
     what's blocked on Josh, open questions. Drop items that are done; git history has them.
   - Keep it lean. If a section turns into a step-by-step procedure, move it into a skill
     (like `release-android`) and leave a one-line pointer.
   - Refer to Josh by name; don't use gendered pronouns.

3. **Check for uncommitted work** and tell Josh what's uncommitted. Don't commit or push unless Josh asks;
   if asked, check that no config, key, or signing files are staged first (see `CLAUDE.md`).

4. **Report briefly:** what changed in `CLAUDE.md`, anything Josh still needs to do, and a suggested
   opening line for the next session if a task is mid-way.
