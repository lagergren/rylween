#ifndef SNAPSHOT_H__
#define SNAPSHOT_H__

/* State-snapshot gate. When the RYL_SNAPSHOT environment variable is
   set to a path, snapshot_init() registers an atexit handler that
   writes a canonical text dump of state to that path on clean exit.
   No-op when the env var is unset.

   The dump covers the gameplay-mutable surface that the longplay
   fixture needs to detect regressions across text-format / UI
   reshuffles: the active party (state.p[state.cpl]) plus the
   session-level fields on struct ryl_state (hour_of_day, cpl, twn,
   banished). Output is a stable key=value line format in a fixed
   field order so byte-for-byte comparison works as a gate signal;
   external tooling can hash or diff it as needed.

   Must run after res_load_world (safe either way; the handler reads
   only struct ryl_state, not the world tree). Call from main before
   gameplay starts. */
void snapshot_init(void);

#endif
