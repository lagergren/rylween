#ifndef RUNTIME_CFG_H__
#define RUNTIME_CFG_H__

/* §24.F runtime config — defaults for the always-on RYL_* knobs.
   Lookup order:
     1. environment variable (`getenv(key)`) — wins if non-empty
     2. parsed runtime.json5 (cached on first call)
     3. NULL

   Pointers are owned by the runtime_cfg cache (or by libc's environ
   for env hits) and are stable for the lifetime of the process — the
   caller MUST NOT free the result.

   File search path:
     $HOME/.rylween/runtime.json5     personal default (preferred)
     data/runtime.json5               project default (optional)

   A missing file is silently OK; the cache stays empty and rcfg_get
   degrades to plain getenv. Schema is a flat JSON5 object whose keys
   are RYL_* names and whose values are scalars (string / int / bool —
   ints and bools are stringified). Personal $HOME config wins over
   project data/ config (first-writer-wins inside the cache).

   The env-vs-file priority is fixed so the replay harness's per-
   fixture env exports stay authoritative even if a developer's
   personal config sets the same key. Replay harness fixtures run
   under HOME=$(mktemp -d), so the personal config never resolves
   during a fixture; only data/runtime.json5 could leak in, and that
   file is intentionally absent from the repo for the same reason. */

const char *rcfg_get(const char *key);

#endif
