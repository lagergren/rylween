# Rylween Port Plan — Active and Reference Sections

This file collects every section of `port-plan.md` that is in-progress, planned, or pure reference / vision material. Completed lifts live in `port-plan-complete.md`; the cross-section index lives in the master `port-plan.md`. Section numbers and `§N` cross-references are preserved verbatim — sections appear in their original §-order regardless of which file they live in.

## Top of queue (status snapshot, 2026-04-28 #13b)

A pointer-only triage of every open item below. The bodies still describe each task in detail — this list is just for "what should we pick up next?". **Session #13b closed every item from #13's recommended-next-session queue**: WLD location-slot cap-flip (last static cap in struct ryl_state), priest spell-list bound from data, rumour() table → DSL (3-line trampoline + 2 latent bugs caught), §20 L2 confirmed already-closed (+ 2 latent bugs caught), temp-damage doc clarification. Six commits this slice (`855c138..e7cbd71`) ran in parallel with two background agents (rumour + L2 survey). Cumulative session #13 + #13b = twenty commits (`376f6bf..e7cbd71`):

  - **`855c138` — WLD_LOCATION_SLOT_MAX / RANDOM_LOCATION_MAX cap-flip**. state.dx[] / state.dy[] flip from inline `int32_t [15]` to heap pointers sized to res_location_slot_max(). Two new world.json5 knobs (`limits.location_slot_max` default 15, `limits.random_location_max` default 9). `WLD_LOCATION_SLOT_MAX` / `WLD_RANDOM_LOCATION_MAX` enum constants in wld.h retired. Smoke-tested with `location_slot_max: 20` / `random_location_max: 12`. **No struct ryl_state field is sized to a compile-time constant after this commit.**
  - **`fdb3d42` — subs.c::spell: priest-spell list bound from data**. New `res_class_spell_max(class_name)` accessor walks the priest_spells / mage_spells tables and returns the highest occupied slot. subs.c's loop bound + abort-key follow it; `'1'..'8'` literals become `'1'..('0'+abort_key)`.
  - **`21c9821..c7454ef` — rumour: 33-case switch → DSL** (background agent, 2 commits). New `rumour_roll` engine_int (preserves `RYL_REPLAY_RUMOUR` semantics + the same RNG draw). New `data/world/scripts/towne.json5 rumour:` script with `emit_indexed { fmt: 'r{}', value: pick }` default branch + 4 bound arms reusing existing `npc_name_at_role` / `npc_loc_at_role` engine_bind helpers. **`rumour()` collapses to a 3-line trampoline** `play_script("towne.rumour", NULL); tx("\r\n");`, down from 64 lines. The agent's patch also dropped the now-unused `runtime_cfg.h` include from towne.c.
  - **`fdcae1d` — temp_low_damage doc clarification**. Settles port-plan §20.5's "possible bug" tracking item: the equality is authored-by-design, not a code defect. Author can change either knob independently.
  - **`e7cbd71` — §20 L2 closure** (background agent's findings). The §20 L2 lift was already finished by `fc599b3` on 2026-04-25 — every dungeon-encounter case is in DSL (`src/dungeon.c` is 182 LOC of pure dispatch; 28 scripts in `data/world/scripts/dungeon.json5`). The agent's survey at `/tmp/dungeon-l2-survey.md` documents 29/29 cases. **Two latent bugs caught and fixed inline**: (a) `enums.dungeon_encounter[1]` was `'object'` while the script is `find_object`, so a slot-1 fill from `ryl_random(1, 9)` would have died on `dungeon.object` resolution (unreachable in the 44 fixtures because seed 42 rolls slot 2 = gold on the only cave-descent fixture); (b) the script's `emit: 'find_object.found'` / `.ok` / `.cant_carry` entries had leading dots that bypassed the `<script_id>.<name>` two-step, resolving as absolute lookups → blank screens. Both fixed; stale comments retired in `data/world.json5` enum block + `dungeon.json5` script header.

Earlier session #13 detail (kept for context): fourteen commits (`376f6bf..72d8cc8`) closed §20.14 S10 (map dims to data), §12.3 final >26 unlock, the 8-commit ydarg DSL lift, time_left = 175 → data, plus fold_map_dims validation + clangd cleanup. See "Closed since the previous snapshot (2026-04-28 #12)" below.

  - **`376f6bf` — §20.14 S10 map dims to data — `map.rows_max` / `map.cols_max`**. state.map flips from `char (*map)[MAP_COLS_HARD_MAX]` to a flat `char *map` 1D buffer of res_map_rows_max() × res_map_cols_max() bytes; tile (x, y) lives at offset `y * cols + x` and wld.c is the sole consumer that knows the stride. New `fold_map_dims(map_node, path)` validates `map.rows_max` / `map.cols_max` against MAP_*_HARD_MAX and stashes them in static caches; `res_map_rows_max()` / `res_map_cols_max()` are the public accessors. wld_rows / wld_cols / in_bounds / wld_tile_at / wld_set_tile_at / wld_is_passable / wld_load_row all source dims from those accessors. The lone direct-access site in fold_npcs's random NPC placer (`state.map[n->y][n->x]`) flips to `wld_tile_at(n->x, n->y)`. replay_knobs.c's RYL_FORCE_POS bounds check stops referencing MAP_*_HARD_MAX directly (now `wld_cols()` / `wld_rows()`). MAP_ROWS_HARD_MAX / MAP_COLS_HARD_MAX (state.h) remain as defensive ceilings. 44 fixtures byte-identical at default 42×82.
  - **`bfd2265` — §12.3 final cap-flip: limits.party_slot_max unbounded above**. fold_limits's clamp drops from `psm->u.iv > PARTY_SLOT_HARD_MAX → res_die` to `>= 2` only — every party-slot-indexed buffer + bounds check resolves through `res_party_slot_max()`. subs.c::scorelist's seven on-stack arrays heap-alloc to `res_party_slot_max()` slots with a `cleanup:` label + four-line free block at every exit. party.c::party_at, snapshot.c party-slot guard, and state.h:317 `pm_cur` cpl assert all flip to `res_party_slot_max()`. **Smoke-tested with `limits.party_slot_max: 30`** — boots cleanly, 44 fixtures byte-identical, `--check-world` clean. PARTY_SLOT_HARD_MAX is now just the default value; NPC_SLOT_HARD_MAX still functions as a defensive ceiling (random-NPC RNG identity depends on the bin count).
  - **`1ea80b2` — plan move-out**: §12.3 + §20.14 S10/S11 bodies relocated to `port-plan-complete.md`; incomplete-plan stubs point at the closure section.
  - **`d9b9c6b` — fold_map_dims: `map.rows >= map.rows_max` defensive check + clangd cleanup**. Catches misconfiguration at fold time so a bad world.json5 (e.g. rows_max=10, rows=40) fails loud rather than silently scribbling past the state.map calloc. Smoke-tested by deliberately setting rows_max=30: `--check-world` exits 1 with the new error message. Plus drops unused `ryl.h` from gfx.c (adds `<stdint.h>` / `<stdio.h>` directly) and interact.c (state.h was already there).
  - **`5778669` — driv.c: `time_left = 175` daily-rollover budget → data**. Adds `driv.daily.time_left: 175` knob in world.json5 (sibling of the existing `driv.start.time_left: 200`); driv.c::init's first-of-day rollover loop reads it via `res_int_path("driv.daily.time_left")`. The two values are intentionally different — a fresh party gets a 200-tick bonus on day 0, the daily refresh after that is 175. Last item from the `feedback_battle_data_extraction.md` queue.
  - **`c3ea341..db43423` — Ydarg DSL lift (8 commits, background agent)**. The full lift continues from #11's first four commits; cases 2 (pitfall), 3 (treasure), 50 (stairs), the per-floor `while(1)` driver, the character-command dispatch (T/P/L/W/R/Q/Z/V), and the navigation-render block all moved to DSL scripts. Hoist shape: module-level `struct ydarg_state g_ydarg` (xl/yl/lvl/map_size/room[8][8]/exit_requested) accessed by 11 new engine_call/engine_int backers. New scripts: `ydarg.case_pitfall` / `case_treasure` / `case_stairs` / `nav_render` / `cmd_dispatch` / `main`. New text records: `ydarg.room.trap.springs` / `ydarg.room.trap.tries_disarm` / `ydarg.nav.coords` / `ydarg.nav.level`. **`ydarg()` is now a one-line trampoline** `play_script("ydarg.main", NULL)`. src/ydarg.c 423 → 354 lines (originally 456 before #11's lifts began). 44 fixtures byte-identical, no rebless.
  - **`72d8cc8` — ydarg.c clangd unused-includes sweep**. Drops `ryl.h` (zero pic-constant references post-lift) + `towne.h` (zero `enter`/`rumour`/`object`/`obdamage` callsites — `object()` moved into a treasure-script engine_call backer); adds `compat.h` and `<ctype.h>` directly. ryl.h now retains only its real users: battle.c / dungeon.c / subs.c / towne.c (each reaches for the pic constants) plus ryl.c itself.

Earlier session #12 detail (kept for context): two commits (`16e0ee3..a70feab`):

  - **`16e0ee3` — §12.3 inner per-record byte arrays heap-alloc off res_party_slot_max()**. struct party.attack_today and struct person.visited flip from inline `[PARTY_SLOT_HARD_MAX]` arrays to `unsigned char *` pointers. attack_today is allocated by party_realize (alongside members[]); freed by party_release_members; NULL'd by party_clear_heap_pointers on the duplicated tail slot post-memmove. visited is allocated by a per-NPC loop inside res_load_world right after `state.npc = calloc(...)`; freed by a symmetric loop at the head of res_shutdown (before npc_slot_max_invalidate so for_each_npc still resolves). npc_visited / npc_visited_set asserts stop using `sizeof n.p->visited` (was 1, becomes 8 after the pointer flip) — bound against `res_party_slot_max()` now. driv.c's two save/load width args flip from PARTY_SLOT_HARD_MAX to res_party_slot_max(). 44 fixtures byte-identical at the default cap (26 == PARTY_SLOT_HARD_MAX); save format only changes if a future world raises `limits.party_slot_max` past it. Static caps remaining: locals in subs.c::scorelist + bounds asserts in party.c::party_at / state.h:303 (state.cpl) / snapshot.c — those would all need lifting before fold_limits could safely accept `limits.party_slot_max > PARTY_SLOT_HARD_MAX`.
  - **`a70feab` — clangd unused-includes follow-up — compat.c, primitives.c**. Same pattern as 7671ea5 / 83814b7. compat.c drops `ryl.h` (only depends on compat.h + runtime_cfg.h's `rcfg_get`; pulls `<assert.h>` directly to replace the lost transit). primitives.c drops `ryl.h` and adds `compat.h` directly so `ryl_random` / `stricmp` aren't pulled through the (now-gone) ryl.h chain. 44 fixtures byte-identical on `make check` + `make testasan`; format clean.

Earlier session #11 detail (kept for context): nine commits (`467e4c6..eb999d1`):

  - **`467e4c6` — §12.3 state.p[26] triple cap-flip — PARTY_SLOT_HARD_MAX + res_party_slot_max()**. Plumbs the party-slot cap through state.h's PARTY_SLOT_HARD_MAX define + a new res_party_slot_max() runtime accessor (world.json5 limits.party_slot_max knob, clamped to PARTY_SLOT_HARD_MAX). Replaces raw `26` literals across struct person.visited / struct party.attack_today / pm_cur assert / driv.c create_party gate (now `>= res_party_slot_max() - 1`) / driv.c save+load attack_today width arg / subs.c::scorelist seven local arrays. No save format change — same emit bytes. 43 fixtures byte-identical.
  - **`d374c8d` — §12.3 map[42][82] cap-flip — MAP_ROWS_HARD_MAX/MAP_COLS_HARD_MAX**. Names the magic 42 / 82 dimensions on state.map[][] in state.h. wld.c WLD_ROWS / WLD_COLS sources from the same defines (single source of truth) and the lone raw bounds-check in replay_knobs.c flips to use them too. 43 fixtures byte-identical.
  - **`93548c4` — ydarg-arrow-keys fixture (43 → 44)**. Mirrors ydarg-confrontation verbatim except the four floor-traversal `66662222` blocks become ESC[C ESC[C ESC[C ESC[C ESC[B ESC[B ESC[B ESC[B (4 east + 4 south arrows). tty_getch's peek_byte_timed translates the CSI tail upstream, so .expect / .expect_snapshot come out byte-identical to ydarg-confrontation's (verified via cmp -s). Sister fixture: wilderness-arrow-keys (gates the wilderness loop); this one closes the same gap for the ydarg navigation path.
  - **`c758874` — ydarg: lift no_key + ruin gate arms to MG_SPECIAL_EQ triggers** (background agent, commit 1/4). Adds a new `MG_SPECIAL_EQ` mission-trigger guard kind that combines tile-at-pos with a `pa_special()` equality test, resolving the special-tag slug to PS_* via `inv_special_id_for_tag`. Splits the castle-Ydarg gate into three triggers: silverkey == 2 (ydarg.ruin script), silverkey == 0 (ydarg.no_key script), default (existing C ydarg() — silverkey == 1 legitimate entry). The two early-return blocks at the top of ydarg() are gone; the C body only runs for silverkey == 1. Both no_key and ruin scripts emit the legacy text records so existing fixtures stay byte-identical.
  - **`fa7681d` — ydarg: lift intro narration into ydarg.intro DSL script** (background agent, commit 2/4). The clr_map_window / blitgfx + 5 paused screens + door_slams + 2-second delay block at the top of ydarg() became `ydarg.intro` play_script. Pure narration lift; no behaviour change.
  - **`f061616` — ydarg: lift confrontation() into ydarg.confrontation DSL script** (background agent, commit 3/4). The entire ~55-line confrontation() function moved into `ydarg.confrontation` play_script with three new engine_call backers (`ydarg_run_seq`, `ydarg_record_completion`, `ydarg_finalize`) and a new generic `{narrate: '<path>'}` DSL op wrapping `tx_narrate`.
  - **`914b8c1` — ydarg: lift case 1 (monster room) into ydarg.room_monster script** (background agent, commit 4/4). The case-1 dispatcher arm in the room-grid switch became `ydarg.room_monster` play_script. Cases 2 (pitfall), 3 (treasure), 50 (stairs) remain in C — they share function-local state (`xl`, `yl`, `lvl`, `room[][]`) that needs hoisting to module-level + new DSL state primitives before they can move; documented in the agent's report. src/ydarg.c 456 → 423 lines (−33).
  - **`3af207b` — §12.3 heap-allocate state.p[] off res_party_slot_max()**. Drops the static `struct party p[PARTY_SLOT_HARD_MAX]` from struct ryl_state in favour of a heap-allocated `struct party *p` sized to res_party_slot_max() at world-load time. Mirrors the state.npc[] pattern. PARTY_SLOT_HARD_MAX remains the upper bound fold_limits asserts against; the in-memory table is right-sized via res_load_world's calloc paired with a res_shutdown free. Idempotent free-then-alloc on reload; zeroed on calloc so per-party heap pointers (members[], journal) stay NULL until party_realize lifts them.
  - **`eb999d1` — §12.3 heap-allocate state.map[][] off MAP_ROWS_HARD_MAX/MAP_COLS_HARD_MAX**. Companion to state.p heap-alloc. Flips struct ryl_state.map from `char map[MAP_ROWS_HARD_MAX][MAP_COLS_HARD_MAX]` to `char (*map)[MAP_COLS_HARD_MAX]` heap-allocated to MAP_ROWS_HARD_MAX rows at world-load time, freed in res_shutdown. Outer dimension only — row width stays compile-time so wld.c's `sizeof state.map[y]` still resolves to a single row. Both heap-alloc commits are byte-identical at default cap; the structural value is dropping the BSS-static cap on the two biggest state-side blocks.

Earlier session #10 detail (kept for context): three commits (`729edd5..776b02c`):

  - **`729edd5` — speak.* coverage polish (background agent)** (#10). Two new replay fixtures gate the Q→M and C arms of `src/speak.c::speak`; previously only Q→P was covered (by `towne-thord-tavern-speak-people` from #9). `towne-thord-tavern-speak-magic` exercises `play_script("speak.magic", {job_sym})` → `speak.magic.jmerchant` under seed 42; `towne-thord-tavern-speak-chit-reply` exercises the conversation-mode `C` key → `reply_chitchat` → `play_script("speak.chit_reply", {subj, job_sym})` (seed 42 picks `subj=2 health` so the body resolves to `speak.chit.reply.health.jmerchant`). Mirrors the people fixture's setup verbatim (RYL_FAKE_EPOCH=1700000000, RYL_FAKE_SEED=42, RYL_NO_DELAY=1; same C22ETSA prefix; same BLLXY trailer); fixture count 41 → 43. Per-repo memory `feedback_background_agents.md` honored — agent worked in `/tmp/speak-coverage/`, emitted a patch, and the patch was applied via `git am`.
  - **`fe6ad45` — wld tile-class predicates + `class:` field on terrain records** (#10). Introduces `wld_tile_is_<class>(char)` helpers driven by an optional `class:` field on each `data/world.json5` `terrain.<c>` record. Classes mapped: `'0'` = town, `'1'` = hut, `'2'` = castle, `'m'` = mountain, `'w'` = water; tiles without a class stay unclassified and the predicates return 0. New `res_terrain_class(char)` accessor mirrors `res_glyph` / `res_color`'s shape — strdup'd into a `class_tab[256]` parallel to the existing two, freed in `reset_terrain_from_tree`. Wld helpers: `wld_tile_is_hut` / `_is_town` / `_is_castle` / `_is_settlement` (any of those three) + `_is_mountain` / `_is_water` / `_is_impassable` (mountain or water). `wld_is_passable` becomes `!_is_impassable` of the tile at (x,y). The four magic-char comparison sites in the codebase migrate: `towne.c::enter` (the `tile != '0' && tile != '1' && tile != '2'` triple → `!wld_tile_is_settlement`; the `tile == '1'` → `wld_tile_is_hut`; the `tile == '2'` → `wld_tile_is_castle`); `res.c::position_locations`'s NPC-obstruction probe (`tile != '0' && '1' && '2' && 'm' && 'w'` → `!wld_tile_is_settlement && !wld_tile_is_impassable`); `script.c::eng_pa_teleport_step`'s priest-teleport mountain block (`!= 'm'` → `!wld_tile_is_mountain`); and `wld.c::wld_is_passable`'s own internal check (now folds through the new helper for symmetry). Adding a fourth settlement type (or a new impassable terrain) is now: data edit + new `wld_tile_is_<name>` helper. No fixture rebless; 43 fixtures byte-identical across both gates; format clean.
  - **`776b02c` — Ydarg as a mission-table E-key trigger** (#10). The (14, 30, castle-tile) → `ydarg()` trigger leaves `towne.c::enter()` and lands in the `mission_triggers[]` table as the seventeenth entry. `mission_trigger` gains four fields: `where` (MW_STEP / MW_ENTER_KEY — splits the dispatcher into step-poll vs E-key phases; existing 16 entries default to MW_STEP), `action` (MA_SCRIPT / MA_C_FN — MA_C_FN dispatches to a `void (*)(void)` function pointer instead of `play_script`, used here to call `ydarg()` directly), `tile_class` (terrain-class string for the new MG_TILE_AT_POS guard), and `pos_path` (dotted path to a `{x, y}` subtree under the world tree, resolved via `res_int_path` at guard-evaluation time). New guard kind `MG_TILE_AT_POS` combines the position match with the tile-class predicate via `res_terrain_class`. The Ydarg trigger is `{.where = MW_ENTER_KEY, .kind = MG_TILE_AT_POS, .tile_class = "castle", .pos_path = "ydarg.entry", .action = MA_C_FN, .fn = ydarg}` — no flag gate (`flag_tag` is now optional, NULL = always-pass). `data/world.json5` gains a `ydarg.entry: { x: 14, y: 30 }` block under the existing `ydarg:` subtree, so the position is data-driven (the castle-tile identification is also data via the terrain `class:` from the previous commit). `mission_control()` filters to MW_STEP entries; `towne.c::enter()` calls a new `mission_try_at_enter()` (MW_ENTER_KEY filter) before falling through to hut/town dispatch. Inventory gating (jazzyfoot / mastercrown / silverkey / bracelets) still happens inside `ydarg()` itself — not yet expressible as a trigger predicate, see Deferred for the full DSL lift. 43 fixtures byte-identical across `make check` + `make testasan`; format clean (clang-format reformatted two struct-member comments).

Earlier session #9 detail (kept for context): three small follow-ups plus a related clangd cleanup, in four commits (`7671ea5..83814b7`):

  - **`7671ea5` — inventory cap `[17]` lifted to `INVENTORY_SLOT_MAX + 1` in `driv.c::write_current_world` / `read_current_world` save & load array-width args. Removes the last raw `17` literal in the inventory path; the `INVENTORY_SLOT_MAX` constant + `res_inventory_slot_max()` accessor were already plumbed everywhere else, so the `limits.inventory_slot_max` knob now genuinely covers all consumers. Same commit also drops the `ryl.h` umbrella include from `driv.c` (clangd `[unused-includes]` real, not stale — driv.c uses none of ryl.h's pic constants / `_version_string`); pulls in `compat.h` / `ctype.h` / `stdio.h` / `stdlib.h` directly. driv.c needed compat.h's `ryl_random` / `ryl_delay` / `ryl_state_path` / `stricmp` / `str_append` / `clock_day` and the C-stdlib for `exit` / `toupper` / `fopen` / `fprintf`. Pattern carried over to inventory.c, party.c, speak.c, res.c in `83814b7` (see below).
  - **`be52f0e` — `wilderness-arrow-keys` replay fixture (40th → 41st gate slot)**. Captures byte-identity for tty_getch's ESC[A/B/C/D translation path that c4715d9 added but didn't gate. Mirrors the wilderness-terrain-narration setup (same epoch + seed + party create + `C22ETSA\n\n` prefix) but pipes ANSI arrow-key sequences instead of literal numpad digits for the eight movement bytes. The `.expect` and `.expect_snapshot` files come out byte-identical to the digit fixture's — strongest possible proof that tty_getch's peek_byte_timed translation produces the same downstream digit at the `key()` boundary. Future drift in either path surfaces immediately.
  - **`aa3efd6` — emit_indexed slug-spliced rebless** (the §24bis phase 3 follow-up flagged in the previous snapshot's Active section). Three pieces: (a) `do_emit_indexed` in `src/script.c` extended to splice non-numeric string values via `%s` (was int-only). New helper `emit_indexed_string_lookup` checks whether `value` is a `{var:'name'}` reference resolving to either an rt_env RT_STR binding or a non-numeric ryl_bind value; on hit the splice runs through `%s` instead of the legacy `%d` path. Either path stays strict — `die_at` on missing text record. (b) `do_engine_bind`'s `'s'` sig position lifted to also accept `{var:'name'}` runtime references (in addition to the existing N_STR author-time literals). Lets engine_bind callers route slug bindings through the same arg slot the `'job'` constant uses. (c) New `res_enum_name_by_sym(id, sym)` in `src/res.c`/`res.h` (compose of `res_enum_id` + `res_enum`); registered in script.c's `str_engine_tbl` with sig `'ss'` so DSL callers can recover the display name from a slug binding without round-tripping through int. C wrappers swap the `npc_job(t)` int binding for `npc_job_sym(t)` slug at four migrated callsites: `speak.c::reply_chitchat` (signature flipped from `(int32_t subj, int32_t jb)` to `(int32_t subj, const char *job_sym)`), the (P)/(M)/(L) Q-prompt arms in speak.c::speak (drop the int snprintf scratch buffers; pass `job_sym` direct), the bard special-case `tx_fmt("speak.people.j8", ...)` updated to the renamed key `speak.people.jbard`, and `towne.c::hut` switched to pass `npc_job_sym` binding (DSL-side switched to `res_enum_name_by_sym` engine_bind). DSL scripts updated: `data/world/scripts/speak.json5` (people / magic / chit_reply.{well,weather,health}) all flip their `value:` from `{var:'job'}` to `{var:'job_sym'}`; `data/world/scripts/lodge.json5` (nonbargirl) same; lodge.bargirl's three text refs `speak.lodge.j13.{offer,broke,spent}` → `speak.lodge.jbargirl.{offer,broke,spent}`; `data/world/scripts/towne.json5`'s hut script updated. `data/world/texts/speak.json5` reblessed: 96 keys renamed across six sections (chit.reply.{well,weather,health} ×16 + people ×16 + magic ×16 + lodge ×16) from `j1`..`j16` → `j<slug>` (`jmerchant` / `jranger` / `jfighter` / `jwizard` / `jpriest` / `jthief` / `jpeasant` / `jbard` / `jdrifter` / `jhalf_orc` / `jhalf_elf` / `jsmith` / `jbargirl` / `jinnkeeper` / `jtavernkeeper` / `jhermit`) via a Python word-boundary rewrite. Coverage gap closed via a new fixture `towne-thord-tavern-speak-people` that exercises the Q→P arm against a merchant NPC (seed 42 picks merchant for the speak target), proving end-to-end that the new emit_indexed string path resolves to `speak.people.jmerchant` and emits its text body. All 41 fixtures byte-identical on `make check` + `make testasan` after the rebless commit; format clean throughout.
  - **`83814b7` — clangd `[unused-includes]` follow-up** for inventory.c, party.c, speak.c, res.c. Same pattern as 7671ea5 for driv.c: drop the `ryl.h` umbrella where it isn't actually pulling its weight (none of those four files reference `ryl.h`'s pic constants / `_version_string`) and pull what's needed directly. inventory.c and party.c lose the `ryl.h /* state */` line outright since `state.h` is reachable via `inventory.h` / `party.h`; speak.c gains explicit `<ctype.h>` / `<stdio.h>` / `<string.h>` / `compat.h`; res.c gains `compat.h` (ryl_random / ryl_delay) and drops the unused `towne.h` (none of `enter` / `rumour` / `object` / `obdamage` referenced). The five files that genuinely use ryl.h identifiers (battle.c / dungeon.c / subs.c / towne.c / ydarg.c — all reach for the pic constants; ryl.c itself defines `_version_string`) keep their `ryl.h` include unchanged.

Earlier session #8 detail (kept for context): finished the per-member shape rework end-to-end on the C side (four commits `dfe4aaf..37dbfff`): (a) `dfe4aaf` — data + DSL field tags spelled out (`ws`→`weapon_skill`, `hp`→`hit_points`, `lv`→`level`, `ma`→`magic`, `wi`→`wisdom`, `ch`→`charisma`, `ar`→`armour`, `wp`→`weapon`, `exp`→`experience`, `h`→`health`) across `data/world.json5` (levelup keys + min_stat name + reward formula bindings), `data/world/scripts/`, `data/world/spells/`, `data/world/items/` (all `field: 'X'` and `battle_flag: 'X'` references), `data/world/texts/driv.json5` (`ws_low`/`wi_low`/`ch_low` text ids), `data/world/texts/battle.json5` (`{exp}` → `{experience}` placeholder); C-side strcmp dispatch in `src/script.c` + `src/res.c` + `res_class_levelup` callers in `src/battle.c` + `src/replay_knobs.c` follow; battle.c reward-binding name flips (`"exp"` → `"experience"`, `"ws"` → `"weapon_skill"`). (b) `fd6b483` — `pm_*`/`pa_*` accessor helpers spelled out (`pm_lv` → `pm_level`, `pm_hp` → `pm_hit_points`, `pm_wi` → `pm_wisdom`, `pm_ws` → `pm_weapon_skill`, `pm_h` → `pm_health`, `pm_ma` → `pm_magic`, `pm_ch` → `pm_charisma`, `pm_ar` → `pm_armour`, `pm_wp` → `pm_weapon`, `pm_exp` → `pm_experience`, `pm_cl` → `pm_class_id`, `pm_ra` → `pm_race_id`, `pm_hp_floor` → `pm_hit_points_floor`, `pm_add_ch_clamp0` → `pm_add_charisma_clamp0`, `pa_lv_avg` → `pa_level_avg`, plus matching `pm_set_*`/`pm_add_*`); 192 sites across 13 files; engine-call dispatch table (`pm_add_hp` → `pm_add_hit_points`, `pm_hp_floor` → `pm_hit_points_floor`) with two data-script references updated to match. (c) `9bae96f` — **SoA → AoS via `struct party_member`**: collapses the 14 parallel heap arrays on `struct party` (name / ws / h / hp / wi / ma / ch / cl / ra / lv / ar / wp / exp / dead / gender) into a single `struct party_member *members` allocation sized to `res_party_member_max()`; field names full-word (`weapon_skill` / `health` / `hit_points` / `wisdom` / `magic` / `charisma` / `class_slug` / `race_slug` / `level` / `armour` / `weapon` / `experience` / `dead` / `gender` / `name`); pm_t accessor bodies shorten from `pm.party->lv[pm.m]` → `pm_rec(pm)->level`; `party_realize` / `party_release_members` / `party_clear_heap_pointers` go from 15 calls each to 1; `read_current_world` / `write_current_world` / `snapshot.c::emit_party` write through the new shape with full-word JSON5/snapshot keys (`class=` / `race=` / `level=` / `health=` / `hit_points=` / `weapon_skill=` / `wisdom=` / `magic=` / `charisma=` / `armour=` / `weapon=` / `experience=`); 39 expect_snapshot fixtures reblessed once; the lone direct write in `inventory.c` (`pm.party->ma[pm.m] = pm.party->wi[pm.m]`) lifted to `pm_set_magic(pm, pm_wisdom(pm))`. (d) `37dbfff` — table-driven member-field dispatch via `offsetof`: the three duplicated strcmp chains in `script.c::expr_member_field` + `read_lv` LV_MEMBER_FIELD + `write_lv` LV_MEMBER_FIELD collapse into one `pm_field` table (`{name, offset, kind}` rows; kind ∈ `INT32`/`UINT32`/`UCHAR`/`CLASS`/`RACE`); read returns int32_t with the type coercion in one switch; write takes int32_t with the negativity check on UINT32 lifted into `pm_field_write_at`; CLASS/RACE rows fold the slug-pointer round-trip via `res_enum_id` / `res_enum_sym`; adding a new member field is now one row instead of three strcmp arms; net 156 → 123 lines. The user's "shitload of these can be key-value tables" intuition cashed out cleanly. 39 fixtures byte-identical on `make check` + `make testasan` after every commit; format clean throughout.

Earlier session #7 detail (kept for context): **closed §24bis phase 3 end-to-end** (`385270a..224fd89`, eight commits) by taking the rebless-once path the user opted into mid-session: (a) flipped storage for class / race / job from `int32_t` → `const char *` slug-pointer (interned in res's enum keys[] table); (b) cleaned up — dropped `PC_FIGHTER..PC_RANGER` defines and `pm_class_id` mapper; res.c spell-load and pa_is_spellcaster lifted to direct slug compare; (c) dropped `id:` from `enums.race` / `enums.pc_class` / `enums.job` / `enums.world_locations` in `data/world.json5` — `fold_enum_records` in res.c gained an id-optional path (1-indexed positional ordering when no explicit id; npcs subtree keeps explicit ids since it's 0-indexed); (d) reblessed all 39 snapshot fixtures so `cl=2 ra=1` reads `cl=thief ra=human`; (e) reshaped the per-party save block — collapsed ~14 parallel per-member arrays into one `members: [{ ... }, ...]` array of self-contained objects with full-word field names (`weapon_skill` / `level` / `armour` / etc.) and `dead` as a JSON5 bool; (f) added `make sample-save` Makefile target that drops a 4-member fixture party (Aragorn=Fighter/Human, Gandalf=Mage/Elf, Mira=Priest/Halfling, Legolas=Ranger/Elf) into `tmp-sample-save/` (gitignored, never overwrites $HOME/.rylween) so play-testing post-changes doesn't require manual party rolling. 39 fixtures byte-identical on `make check` + `make testasan` at every commit. After this snapshot, the only remaining §24bis loose end is the DSL `emit_indexed jN` text-shard rebless (last vestige of int-position keying — see Active section below); the §12.3 cap-flip queue is unchanged.

### Guiding principle (re-affirmed 2026-04-27 #12)

**Adding a town / NPC / item / etc. should be a pure `data/world.json5` edit.** The cap-driven heap-allocation pattern that step 3a (`state.npc`) and step 3b (`towns`) follow is the working shape: name the static cap in `state.h`, heap-allocate off the world.json5 record count at `res_load_world` time, free in `res_shutdown`. §12.3 (dynamic sizing from `world.json5 limits:`) is the umbrella section; new work that touches a still-static array should ride along under that principle rather than baking in a new fixed cap. **§12.3 fully closed after 2026-04-28 #13a**: every party-slot-indexed buffer (state.p[], state.npc[], struct party.attack_today, struct person.visited) and the per-function on-stack tables in subs.c::scorelist are heap-allocated off `res_party_slot_max()` at world-load / party-realize time; map dims (state.map) are heap-sized to `res_map_rows_max()` × `res_map_cols_max()`; bounds checks in party.c / state.h / snapshot.c all resolve through the runtime accessors. The `fold_limits` clamp on `limits.party_slot_max` is `>= 2` only — `PARTY_SLOT_HARD_MAX` is now just the default value, no longer a ceiling. NPC_SLOT_HARD_MAX still functions as a defensive ceiling inside `npc_slot_max()` because the random-NPC placement RNG identity depends on the bin count.

### Active — work is in flight or fully unblocked

- **State-abstraction sweep** (§25.A.1 #6 footer; tracker: `feedback_state_abstraction.md`). **Closed total in 2026-04-28 #5.** Final code-side counts: every top-level `struct ryl_state` field (`p[]`, `npc[]`, `map[][]`, `cpl`, `num_parties`, `global_name`, `last_played_day`, `day_at_game_start`, `banished`, `hour_of_day`, `twn`, `dx[]`, `dy[]`, `last_terrain`) is now accessed through helpers from every consumer except the documented abstraction-home files. New helpers landed this snapshot: `npc_record_at` (state.h, save/load by-slot escape hatch), `wld_rows`/`wld_cols`/`wld_set_tile_at`/`wld_load_row`/`wld_last_terrain`/`wld_locations_dx`/`wld_locations_dy` (wld.h/c), `party_current_slot`/`party_set_current_slot`/`party_count`/`party_count_inc`/`party_count_dec`/`party_set_count` (state.h, alongside party_current), the `session_*` family for global scalars (state.h: `player_name`/`last_played_day`/`day_at_game_start`/`banished`/`hour`/`current_town` + setters where applicable). Future state-shape changes (hash-keyed parties, dynamic sizing post-§12.3, save-format slug rework) only touch the abstraction-home files.
<!-- 2026-04-28 #5: full sweep extended to npc/map/session scalars + clock_* + terminate fix + towne class + §24bis phase 1 slug accessors + speak.c phase-2 lifts (commits 0d075c3..f21f7af). See "Closed since the previous snapshot (2026-04-28 #4)" below. -->
<!-- 2026-04-28 #4: party_at helper + subs.c::scorelist + driv.c (50+ sites) + replay_knobs.c + primitives.c lifts landed (8d3abbd..0dd1580). -->
<!-- 2026-04-28 #3: towne.c / missions.c / speak.c / res.c state-abstraction lifts landed (75fd006..4ec26b1). -->
<!-- §17.A.4 slice D closed in 2026-04-28 #2; §17 closed end-to-end and body moved to `port-plan-complete.md`. -->
<!-- §23.A structure-extraction follow-up closed in 2026-04-27 #16 — see "Closed since the previous snapshot (2026-04-27 #15)" below. -->

### Mechanical residuals (all closed 2026-04-28 #5)

- **driv.c::terminate visited-grid shift bug** — fixed (`d8db513`). The inner `for (j=1; j<num_parties; j++)` body never referenced `j`, so only `visited[x]` was rewritten. Now `for (j=x; j<party_count(); j++) { npc_visited_set(n, j, npc_visited(n, j+1)); }` — proper compaction. Fixture-unreachable today; gates green.
- **towne.c `state.twn == 2` city/village fork** — closed (`5493dff`). Lifted settlement class onto each town record in `data/world.json5` under `class: 'city' | 'village'`; added `res_town_is_city(int twn)` helper. towne.c::enter now reads `res_town_is_city(state.twn)` instead of hardcoding the slot index. Adding a fifth town as a city is now a data edit.
- **getclk + kl[N] array** — replaced (`c411fdc`). Drops the legacy Amiga `getclk(char kl[8])` + opaque `kl[1]/kl[2]/kl[3]` indexing. Adds `struct wall_clock` + `clock_now(out)` for atomic snapshots and `clock_year`/`clock_month`/`clock_day`/`clock_hour`/`clock_minute` shortcuts. Migrates driv.c (4 day-check sites), log.c (audit timestamp), ydarg.c (heroes-file inscription, preserves the legacy "19%d"+(year-1900) Y2K cosmetic). compat.h's old kl[] index contract retired.

### Deferred — work is queued but explicitly post-something

- **§24bis — `id: <int>` → slug-keyed state**. **Fully closed.** Phase 1 (b08978d): slug accessors + `*_is_sym` predicates. Phase 2 (f21f7af): speak.c npc_job lifts. **Phase 3 closed in 2026-04-28 #7** (`385270a..224fd89`, eight commits): storage flipped from `int32_t` → `const char *` slug-pointer for `struct party.cl` / `struct party.ra` / `struct person.job`; PC_* defines + `pm_class_id` mapper retired; `id:` dropped from `enums.race` / `.pc_class` / `.job` / `.world_locations` (`fold_enum_records` accepts id-less keyed-record entries via 1-indexed positional ordering); snapshot reblessed (`cl=fighter ra=human`); save format reshaped to per-member objects with full-word field names. **Phase 3 follow-up closed in 2026-04-28 #9** (`aa3efd6`): `emit_indexed` extended to splice slug bindings via `%s` (was int-only), `do_engine_bind`'s `'s'` sig position lifted to accept `{var:'name'}` runtime references, new `res_enum_name_by_sym` str_engine helper, four C-side wrappers (`speak.c::reply_chitchat`, the speak.c (P)/(M)/(L) Q-prompt arms, `towne.c::hut`) flipped from `npc_job(t)` int binding to `npc_job_sym(t)` slug binding, 96 keys in `data/world/texts/speak.json5` renamed from positional `j1..j16` to slug-spliced `j<slug>` (`jmerchant` / `jranger` / ... / `jbargirl` / `jhermit`) across six sections (chit.reply.{well,weather,health} + people + magic + lodge), DSL scripts updated to match, new `towne-thord-tavern-speak-people` fixture gates the slug-spliced path. Body in `port-plan-complete.md` "§24bis phase 3 closure" section.
- **§12.3 — dynamic sizing from world.json5 (`limits` subtree)**. **Closed end-to-end in 2026-04-28 #11..#13a** (commits `467e4c6..bfd2265`, plus `376f6bf` for the §20.14 S10 map-dims piece). Body in `port-plan-complete.md` "§12.3 cap-flip queue end-to-end closure". Every party-slot-indexed buffer is heap-allocated off `res_party_slot_max()`; map grid is heap-sized to `res_map_rows_max()` × `res_map_cols_max()`; the `fold_limits` clamp is `>= 2` only. PARTY_SLOT_HARD_MAX is now the default value, no longer a ceiling. NPC_SLOT_HARD_MAX retained as a defensive ceiling because random-NPC placement RNG identity depends on the bin count.
- **§12.1 — Dockerfile + GitHub CI**. **Closed in 2026-04-28 #6** (`d35f663..405f398`, four commits). Multi-stage Dockerfile (`debian:stable-slim` builder → tester → slim runtime) installs LLVM 22 from `apt.llvm.org` so the tester stage's `make test` + `make testasan` + `make format` all run against the same major as macOS brew. `.github/workflows/ci.yml` mirrors with `linux` (gcc+clang matrix, runs all three gates), `format` (clang-format dry-run), `macos` (build + replay + ASan as scorecard — Apple-clang ships without LSan, so Linux is the authoritative leak gate). Two parallel landings were needed to ship: (a) `bb453c7` adds `-D_DEFAULT_SOURCE` to `CPPFLAGS` so glibc's strict-ISO C99 mode exposes the POSIX surface (`strdup`, `usleep`/`useconds_t`, `sigaction`/`SIGWINCH`, …) the codebase uses everywhere — Apple-libc exposes those unconditionally, which masked the gap until Linux compiled; (b) `d35f663` runs `clang-format -i` across 12 files (114+/103-) and flips `.clang-format`'s `ReflowComments: Never` to `ReflowComments: false` (a documented alias accepted by every clang-format major back to 18). Format gate had been silently broken pre-§12.1; recorded in `.git-blame-ignore-revs`.
- **Ydarg as a regular mission** (§12.4 residual). **Closed end-to-end in 2026-04-28 #13** (8-commit background-agent patch `c3ea341..db43423`, on top of #10/#11's earlier lifts `c758874..914b8c1`). Full DSL lift complete: cases 2 (pitfall), 3 (treasure), 50 (stairs), the per-floor `while(1)` driver, the character-command dispatch (T/P/L/W/R/Q/Z/V), and the nav-render block all moved to DSL scripts; module-level `struct ydarg_state g_ydarg` (xl/yl/lvl/map_size/room[8][8]/exit_requested) accessed by 11 new engine_call/engine_int backers. **`ydarg()` is now a one-line trampoline** `play_script("ydarg.main", NULL)`. src/ydarg.c 456 → 354 lines (-102 across all sessions). 44 fixtures byte-identical, no rebless. Body in `port-plan-complete.md` "Ydarg-as-mission moderate-path closure" + the §23.J lift commit messages.
- **ydarg inscription Y2K cosmetic** — closed in 2026-04-28 #6 (`ff6195b`). `"19%d" + (c.year - 1900)` → `"%d", c.year` (clock_year() returns the full 4-digit year per compat.h). No fixture rebless needed: the `ydarg-confrontation` fixture's env sidecar explicitly notes "the heroes-file write is left for a follow-up", and grep confirms no fixture exercises this code path.

### Documented & deferred — won't land unless requirements change

- **§18 — ANSI art EAW / narrow-cell rendering**. Path A shipped: README terminal-compat note documents the macOS Terminal.app drift and recommends iTerm2 / Ghostty / Alacritty / Kitty / WezTerm. Path B (rewrite art to symmetric blocks) is an art-rewrite job; Path D (Sixel/Kitty graphics) is a port-quality bump. Neither scheduled.
- **§21 — reentrancy / `ryl_ctx`**. Per §22.12 verdict: "worth it only if a real multi-tenant use case materialises." None has. Treat §21 body as documentation only.
- **§6 — C99 vs C++17 subset decision**. Decision deferred indefinitely; the C99 path is working.
- **§22.E items 11 + 12 — engine registry unification + op-shape micro-inconsistencies**. Cosmetic; consciously skipped per §22.12.
- **§20.12 — unwired DSL hooks (`spawn_encounter`, `call`)**. eval.c retired, ops gone from the DSL surface entirely. Superseded; if a script ever needs `spawn_encounter` again, it'd be a new design.
- **§20.20 #1 / #3 / #4 — DSL ergonomics**. Class-id symbol args, `{path: '…'}` expr form, default-`{0}` speak op town arg — deferred until a flow with a clear win surfaces.

### Closed but body still in this file (move-out queue)

(All section bodies are now in their correct file. §17 / §22 / §23 / §24 / §25 bodies all relocated to `port-plan-complete.md`; only stubs remain in this file so the master TOC's §-anchors still resolve. The session-journal entries from 2026-04-26 / 2026-04-27 lower in this file are evergreen migration notes that can stay — they're referenced from continuation prompts and contain shape decisions that don't have a natural §-anchor.)

### Closed since the previous snapshot (2026-04-28 #13)

Six commits on `lagergren/port1` (`855c138..e7cbd71`); summaries in the Top-of-queue snapshot above:

- **WLD_LOCATION_SLOT_MAX cap-flip — last static cap in struct ryl_state** (`855c138`).
- **subs.c::spell priest-list bound from data — `res_class_spell_max`** (`fdb3d42`).
- **rumour: 33-case switch → DSL** (`21c9821`, `c7454ef`, background agent).
- **temp_low_damage / temp_very_low_damage doc clarification** (`fdcae1d`).
- **§20 L2 closure: enum/script naming bug fix + stale-comment retire** (`e7cbd71`, background agent).

### Closed since the previous snapshot (2026-04-28 #12)

Fourteen commits on `lagergren/port1` (`376f6bf..72d8cc8`); summaries in the Top-of-queue snapshot above; canonical bodies live in commit messages until move-to-complete sweep (next session):

- **§20.14 S10 map dims to data** (`376f6bf`). `state.map` flat 1D buffer sized to `map.rows_max` × `map.cols_max`.
- **§12.3 final cap-flip — limits.party_slot_max unbounded above** (`bfd2265`). subs.c::scorelist heap-alloc; bounds checks flip to runtime accessors; fold_limits clamp `>= 2` only.
- **plan move-out** (`1ea80b2`). §12.3 + §20.14 S10/S11 closure section appended to port-plan-complete.md.
- **fold_map_dims defensive validation + gfx.c / interact.c clangd cleanup** (`d9b9c6b`).
- **driv.c daily time_left → driv.daily.time_left data knob** (`5778669`).
- **Ydarg DSL lift — 8 commits via background agent** (`c3ea341..db43423`). Cases 2/3/50 + nav-render + cmd-dispatch + while(1) driver all moved to DSL scripts. ydarg.c 423 → 354. `ydarg()` is a one-line trampoline.
- **ydarg.c clangd unused-includes sweep post-lift** (`72d8cc8`). Drops `ryl.h` + `towne.h`; pulls `compat.h` + `<ctype.h>` directly. ryl.h now retains only its real users (battle / dungeon / subs / towne / ryl).

### Closed since the previous snapshot (2026-04-28 #11)

Two commits on `lagergren/port1` (`16e0ee3..a70feab`); summaries in the Top-of-queue snapshot above; canonical bodies live in commit messages until move-to-complete sweep (next session):

- **§12.3 inner per-record byte arrays heap-alloc** (`16e0ee3`). struct party.attack_today + struct person.visited flip from `[PARTY_SLOT_HARD_MAX]` to heap pointers sized to res_party_slot_max(). attack_today via party_realize / party_release_members / party_clear_heap_pointers; visited via per-NPC alloc loop in res_load_world + symmetric free loop at the head of res_shutdown. driv.c's two save/load width args flip to res_party_slot_max(). 44 fixtures byte-identical at default cap.
- **clangd unused-includes follow-up — compat.c, primitives.c** (`a70feab`). Drop the `ryl.h` umbrella where it isn't pulling its weight; pull `<assert.h>` into compat.c directly + `compat.h` into primitives.c directly to replace the lost transit.

### Closed since the previous snapshot (2026-04-28 #10)

Nine commits on `lagergren/port1` (`467e4c6..eb999d1`); summaries in the Top-of-queue snapshot above; canonical bodies live in commit messages until move-to-complete sweep (next session):

- **§12.3 state.p[26] triple cap-flip — PARTY_SLOT_HARD_MAX + res_party_slot_max()** (`467e4c6`).
- **§12.3 map[42][82] cap-flip — MAP_ROWS_HARD_MAX/MAP_COLS_HARD_MAX** (`d374c8d`).
- **fixture: ydarg-arrow-keys covers ydarg.c ESC[A/B/C/D translation** (`93548c4`).
- **ydarg: lift no_key + ruin gate arms to MG_SPECIAL_EQ triggers** (`c758874`, background agent commit 1/4).
- **ydarg: lift intro narration into ydarg.intro DSL script** (`fa7681d`, agent 2/4).
- **ydarg: lift confrontation() into ydarg.confrontation DSL script** (`f061616`, agent 3/4).
- **ydarg: lift case 1 (monster room) into ydarg.room_monster script** (`914b8c1`, agent 4/4).
- **§12.3 heap-allocate state.p[] off res_party_slot_max()** (`3af207b`).
- **§12.3 heap-allocate state.map[][] off MAP_ROWS_HARD_MAX/MAP_COLS_HARD_MAX** (`eb999d1`).

### Closed since the previous snapshot (2026-04-28 #9)

Three commits on `lagergren/port1` (`729edd5..776b02c`); bodies in `port-plan-complete.md`:

- **speak.* coverage polish — Q→M and C arms gated** (`729edd5`) — body in "speak.* coverage polish closure (2026-04-28 #10)".
- **wld tile-class predicates + `class:` field on terrain records** (`fe6ad45`) — body in "Tile-class abstraction closure (2026-04-28 #10)".
- **Ydarg as a mission-table E-key trigger** (`776b02c`) — body in "Ydarg-as-mission moderate-path closure (2026-04-28 #10)".

### Closed since the previous snapshot (2026-04-28 #8)

Four commits on `lagergren/port1` (`7671ea5..83814b7`); bodies in `port-plan-complete.md`:

- **§12.3 inventory cap-flip + driv.c clangd cleanup** (`7671ea5`) — body in "§12.3 inventory cap-flip closure (2026-04-28 #9)".
- **wilderness-arrow-keys replay fixture** (`be52f0e`) — body in "Wilderness arrow-key fixture closure (2026-04-28 #9)".
- **§24bis phase 3 follow-up — emit_indexed slug-splice + 96-key rebless** (`aa3efd6`) — body in "§24bis phase 3 follow-up closure (2026-04-28 #9)".
- **clangd unused-includes follow-up sweep** (`83814b7`) — body in "clangd unused-includes follow-up sweep closure (2026-04-28 #9)".

### Closed since the previous snapshot (2026-04-28 #7)

- **Per-member shape spelled out + collapsed to AoS** (four commits, `dfe4aaf..37dbfff`). Mirrors the save-format rename from session #7 onto every other side of the codebase that touched per-member stats. **Data + DSL** (`dfe4aaf`): every `field: 'X'` and `battle_flag: 'X'` reference in `data/world/scripts/*.json5` / `data/world/spells/*.json5` / `data/world/items/magic_items.json5` flips from two-letter abbreviation to full word (`ws`/`hp`/`wi`/`ma`/`ch`/`lv`/`ar`/`wp`/`exp`/`h` → `weapon_skill`/`hit_points`/`wisdom`/`magic`/`charisma`/`level`/`armour`/`weapon`/`experience`/`health`); `data/world.json5`'s `levelup` keys + `min_stat` `name:` + `fail_text:` ids + reward-formula `var:` bindings follow; `data/world/texts/driv.json5`'s `ws_low`/`wi_low`/`ch_low` text-id keys flip; `data/world/texts/battle.json5`'s `{exp}` placeholder flips to `{experience}`. C-side strcmp dispatch in `script.c` + `res.c::res_class_min_stat_check` + `res_class_levelup` callers in `battle.c` + `replay_knobs.c` follow; battle.c reward-binding names (`{"exp", ...}` → `{"experience", ...}`, `{"ws", ...}` → `{"weapon_skill", ...}`). Monster-shard schema (`data/world/monsters/monsters.json5`) keeps its short `n1`/`n2`/`h1`/`h2`/`ws`/`sp`/`im` keys for now — that's a tabular record schema where the abbreviations buy column density across 50+ rows; revisit only if a new caller needs the full names. Status-bar / printf rendering (`H1: %3d/%3d`, `FD: %4d`, `MV: %3d`, `AU: %6d` in `driv.c::statusline`, `Wisdom : %2d` etc in `showparty`) keeps its short display strings — those are user-facing UI fitted to the 80-col game window, not C-side identifiers. **Accessors** (`fd6b483`): `pm_*`/`pa_*` helper names spell out their stat (`pm_lv` → `pm_level`, `pm_hp` → `pm_hit_points`, `pm_wi` → `pm_wisdom`, `pm_ws` → `pm_weapon_skill`, `pm_h` → `pm_health`, `pm_ma` → `pm_magic`, `pm_ch` → `pm_charisma`, `pm_ar` → `pm_armour`, `pm_wp` → `pm_weapon`, `pm_exp` → `pm_experience`, `pm_cl` → `pm_class_id`, `pm_ra` → `pm_race_id`, `pm_hp_floor` → `pm_hit_points_floor`, `pm_add_ch_clamp0` → `pm_add_charisma_clamp0`, `pa_lv_avg` → `pa_level_avg`, plus all matching `pm_set_*`/`pm_add_*`); 192 sites across 13 files (`battle.c` / `driv.c` / `inventory.c` / `party.c` / `party.h` / `replay_knobs.c` / `res.c` / `res.h` / `script.c` / `state.h` / `subs.c` / `towne.c` / `ydarg.c`) auto-renamed via `perl -i -pe 's/\bpm_lv\b/pm_level/g; ...'` (word-boundary regex; longest names first to avoid double-rename). The `script.c::engine_call` dispatch table follows (`{"pm_add_hp", ...}` → `{"pm_add_hit_points", ...}`, same for `pm_hp_floor`); two data scripts (`dungeon.gold_chest`, `rest.guard`) update their `engine_call: 'pm_add_hp'` / `engine_call: 'pm_hp_floor'` references to match. `pm_full_heal` / `pm_set_dead` / `pa_teleport_step` / `pa_add_pl` / `pa_set_inventory` keep their existing names (already full-word). **Storage shape** (`9bae96f`): `struct party`'s 14 parallel heap pointers (`name` / `ws` / `h` / `hp` / `wi` / `ma` / `ch` / `cl` / `ra` / `lv` / `ar` / `wp` / `exp` / `dead` / `gender`) collapse into a single `struct party_member *members` heap-allocated to `res_party_member_max()` slots. **`struct party_member`** carries the per-member fields with full-word names: `name[30]` / `weapon_skill` / `health` / `hit_points` / `wisdom` / `magic` / `charisma` / `class_slug` (slug pointer) / `race_slug` (slug pointer) / `level` / `armour` / `weapon` / `experience` (uint32) / `dead` / `gender` (uchar). `class_slug`/`race_slug` keep the `_slug` suffix to keep the storage shape obvious at the call site and to dodge clangd's C++-mode parser warning on a bare `class` field name (the codebase is pure C; `class` is a valid identifier but lights up tooling). `pm_t` accessor bodies shorten from `pm.party->lv[pm.m]` → `pm_rec(pm)->level` via the new `pm_rec` helper that returns `&pm.party->members[pm.m]`. Lifecycle: `party_realize` / `party_release_members` / `party_clear_heap_pointers` go from 15 calloc / free / NULL-set sites apiece down to one `calloc(slots, sizeof *p->members)` / one `free` / one NULL; the `alloc_zero` private helper retires. `read_current_world` / `write_current_world` in `driv.c` and `snapshot.c::emit_party` rewrite the per-member loop to `p->members[m].field`. **Snapshot fixture format**: the per-member emit line collapses from `cl=%s ra=%s lv=%d dead=%d h=%d hp=%d ws=%d wi=%d ma=%d ch=%d ar=%d wp=%d exp=%u` to `class=%s race=%s level=%d dead=%d health=%d hit_points=%d weapon_skill=%d wisdom=%d magic=%d charisma=%d armour=%d weapon=%d experience=%u`. All 39 expect_snapshot fixtures reblessed once via `tools/replay.sh --all --bless` (mechanical key rename, no value churn). The lone direct write in `inventory.c` (`pm.party->ma[pm.m] = pm.party->wi[pm.m];` post-AoS would have been `pm.party->members[pm.m].magic = pm.party->members[pm.m].wisdom;`) was lifted to `pm_set_magic(pm, pm_wisdom(pm))` to match the rest of the codebase's accessor discipline. **Table-driven dispatch** (`37dbfff`): the three duplicated strcmp chains in `script.c::expr_member_field` (DSL `{member: <expr>, field: 'X'}` read) + `read_lv` LV_MEMBER_FIELD case + `write_lv` LV_MEMBER_FIELD case (~50 lines each) collapse into one `pm_field` lookup table (`{const char *name, size_t offset, pm_field_kind kind}` rows, kind ∈ `PMF_INT32`/`PMF_UINT32`/`PMF_UCHAR`/`PMF_CLASS`/`PMF_RACE`) plus `pm_field_lookup` + `pm_field_read_at` + `pm_field_write_at` helpers. `pm_field_read_at` returns int32_t with the type coercion in one switch; `pm_field_write_at` takes int32_t and applies the negativity check on UINT32 (the experience field's only constraint, lifted into the helper) plus the slug round-trip on CLASS/RACE (via `res_enum_sym`). Adding a new member field is now one row instead of three strcmp arms — exactly the user's "shitload of these can just be key-value tables" intuition. Net 156 → 123 lines on `script.c`. **Data tags drift left to investigate later**: `data/world/monsters/monsters.json5`'s `n1`/`n2`/`h1`/`h2`/`ws`/`sp`/`im`/`n2_lv_div` schema; revisit only if a column reader appears outside `res.c::fold_monsters`. Same 39 fixtures byte-identical on `make check` + `make testasan` at every commit; format clean. **Local WIP preserved verbatim** through the rename — the user's uncommitted arrow-key support (`src/io_tty.c` peek_byte_timed + ESC[A/B/C/D dispatch, `src/wld.{c,h}` `dir_t` enum + `wld_dir_dx`/`wld_dir_dy`/`wld_dir_from_key` helpers, `src/script.c::eng_pa_teleport_step` `dir_t` integration, `src/ydarg.c` `dir_t` integration in the navigation block) sat across two of the four rename commits (script.c + ydarg.c) but was kept out of the staged changes via the save-restore-replay-restore workflow (save the WIP-bearing file to `/tmp/`, `git checkout HEAD --` the file, reapply the rename via Edit, commit, restore `/tmp/`). Commits left to the user when ready.

### Closed since the previous snapshot (2026-04-28 #6)

- **§24bis phase 3 closed end-to-end** (eight commits, `385270a..224fd89`). Storage flip + boundary cleanup + data shape refactor for the `pc_class` / `race` / `job` axes. **Storage**: `struct party.cl` and `struct party.ra` flip from `int32_t *` to `const char **`; `struct person.job` flips from `int32_t` to `const char *`. Each slot holds an interned slug pointer into res's enum `keys[]` table (NULL = unset sentinel). Legacy int form recovered at the boundary via `res_enum_id` for `pm_cl` / `pm_ra` / `npc_job`, snapshot translation, and save-format emit. **Cleanup**: PC_FIGHTER..PC_RANGER `#define`s retired from `state.h`; `pm_class_id` mapper retired from `compat.c` (was the only thing using PC_* outside `state.h`); res.c's three `pm_class_id(cname) == PC_PRIEST/MAGE` spell-load sites flip to direct `strcmp(cname, "priest"/"mage")`; `res.c::fold_wieldable_by` switches from `pm_class_id(...)` to `res_enum_id("pc_class", ...)`; `pa_is_spellcaster` becomes a slug compare; `pm_is_fighter` / `_thief` / `_mage` / `_priest` / `_bard` / `_ranger` flip to `pm_class_is_sym(pm, "<sym>")` direct slug compare. **Data shape**: `data/world.json5`'s `enums.race` / `.pc_class` / `.job` / `.world_locations` lose the `id:` field on each entry; `res.c::fold_enum_records` gains an id-optional path that uses 1-indexed positional ordering when no entry carries explicit `id:` (slot 0 reserved for unset sentinel). Mixing id-bearing and id-less entries within one enum is rejected. NPCs subtree keeps explicit `id:` (it's 0-indexed and uses sparse layout — garth=0). **Snapshot rebless**: `snapshot.c::emit_party` switches from `cl=%d ra=%d` (translated) to `cl=%s ra=%s` (slug passthrough); 39 fixtures reblessed once via `tools/replay.sh --all --bless` (153 line changes, exact substitution: `cl=2 ra=1` → `cl=thief ra=human`). **Save format reshape**: per-party block collapses ~14 parallel per-member arrays (`name` / `ws` / `h` / `hp` / `wi` / `ma` / `ch` / `cl` / `ra` / `lv` / `ar` / `wp` / `exp` / `dead` / `gender`) into a single `members: [{ ... }, ...]` array of self-contained per-member objects with full-word field names (`weapon_skill` / `level` / `armour` / `wisdom` / `magic` / `charisma` / `health` / `hit_points` / `experience`) and `dead` as JSON5 boolean (`true`/`false`); reader accepts the bool primarily and an int 0/1 fallback for hand-edited saves. Six writer/reader helpers (`wcw_u32_arr`, `wcw_enum_arr`, `wcw_enum_barr`, `rcw_u32_arr`, `rcw_enum_arr`, `rcw_enum_barr`) retired as dead code post-refactor. Save-file backwards compatibility was explicitly not a goal — old saves load empty per-member; per the user's go-ahead this is fine since snapshot fixtures don't go through the save-format path. **Tooling**: `make sample-save` Makefile target drops a fresh 4-member fixture party (Aragorn=Fighter/Human, Gandalf=Mage/Elf, Mira=Priest/Halfling, Legolas=Ranger/Elf) into `tmp-sample-save/` (gitignored, never overwrites $HOME/.rylween) so play-testing post-changes doesn't require manual party rolling. RYL_FAKE_SEED=1 lands all four members past their class-gate min-stat checks on the first roll. 39 fixtures byte-identical on `make check` + `make testasan` at every commit. Body in `port-plan-complete.md` "§24bis phase 3 closure" section. Only follow-up loose end is the DSL `emit_indexed jN` text-shard rebless (last vestige of int-position keying) — tracked under Active in the new top-of-queue snapshot.

- **Format gate restored on uncommitted arrow-key WIP** (in-tree, no commit). The arrow-key→numpad-digit translation in `src/io_tty.c` (peek_byte_timed + ESC[A/B/C/D → 8/2/6/4 dispatch in tty_getch) plus the `dir_t` enum + `wld_dir_dx`/`wld_dir_dy`/`wld_dir_from_key` helpers in `src/wld.{c,h}` and the `try_move(dir_t)` driv.c / ydarg.c / script.c integration carried clang-format violations that broke the format gate. Ran `clang-format -i` on `src/io_tty.c` / `src/script.c` / `src/wld.c` / `src/wld.h` / `src/ydarg.c` to re-flow per `.clang-format`; arrow-key logic preserved verbatim. Format gate green; the WIP itself remains uncommitted in the working tree for the user to commit when ready.

### Closed since the previous snapshot (2026-04-28 #4)

- **Full state-abstraction sweep extension** (commits `0d075c3..26941a2..a4bb984..c55888d`, four commits). Builds on 2026-04-28 #4's party_at lift by extending the same pattern to every other top-level `struct ryl_state` field. **state.npc[]**: introduces `npc_record_at(int slot)` in state.h (struct-person handle, mirrors `party_at`); driv.c's four save/load sites swap to `npc_record_at` + the existing `npc_visited`/`npc_visited_set`. **state.map[][]**: adds `wld_rows`/`wld_cols`/`wld_set_tile_at`/`wld_load_row`/`wld_last_terrain` to wld.c/h; lifts driv.c (NPC hut stamp + map.dat row loader), subs.c (check_terrain + 5x5 update_map), script.c (movement mountain-block test), snapshot.c (map emit loop), missions.c (MG_TERRAIN guard). Snapshot byte-identity preserved — `%02d` vs `%02zu` emit identical bytes for 0..41. **state.cpl + state.num_parties**: adds `party_current_slot`/`party_set_current_slot`/`party_count`/`party_count_inc`/`party_count_dec`/`party_set_count` inline in state.h alongside `party_current`. Lifts driv.c's 26 sites (asserts, login, createparty, terminate's free + memmove + clear-tail block), subs.c::scorelist's 4 sites, snapshot.c's 3 cpl emit sites, speak.c's 2 npc_visited slot args, battle.c's encounter assert, script.c's `{cpl}` DSL binding. **state.global_name + last_played_day + day_at_game_start + banished + hour_of_day + twn**: adds `session_*` inline helpers in state.h (player_name getter; last_played_day / day_at_game_start / banished / hour / current_town getter+setter pairs; advance_hour stays the only ++ path). Lifts driv.c (init / save / read_current_world / write_current_world), dungeon.c (banished read+reset), primitives.c (sysop name compare), script.c (DSL bindings + LV_* mutate paths), snapshot.c (emit), subs.c (sysop name compare). **state.dx[] + state.dy[]**: adds `wld_locations_dx` / `wld_locations_dy` array handles for the driv.c save/load wcw_i32_arr / rcw_i32_arr loops; per-slot reads/writes already used wld_location_x/y/set. **state.last_terrain**: subs.c read swapped to `wld_last_terrain()` (helper added). All four commits byte-identical on the 39 fixtures across both gates; no `.expect_snapshot` rebless. After this snapshot **only** abstraction-home files (party.c for state.p[], inventory.c for state.p[].inventory, ryl.c for state.global_name init, towne.c for state.twn writes, wld.c for state.dx/dy/map/last_terrain) retain any direct `state.X` access by design.

- **getclk + kl[N] array retired** (single commit, `c411fdc`). Drops the legacy Amiga `getclk(char kl[8])` + opaque indexing (`kl[1]` = year-1980, `kl[2]` = month, `kl[3]` = day, `kl[4]` = hour, `kl[5]` = minute). New compat.h API: `struct wall_clock { year, month, day, hour, minute, second, weekday }` + `clock_now(out)` for atomic snapshots + `clock_year`/`clock_month`/`clock_day`/`clock_hour`/`clock_minute` single-field shortcuts. clock_year() returns the full 4-digit year; the kl[1] year-1980 encoding is gone. Migrates driv.c (4 day-check sites — clean local kl[8] arrays drop from `init` and `save`), log.c (audit timestamp, uses clock_now() into struct for atomicity across a possible second boundary), ydarg.c (heroes-file inscription, preserves the legacy "19%d" + (year-1900) Y2K cosmetic format verbatim with a comment pointing at compat.h). 39 fixtures byte-identical on both gates.

- **driv.c::terminate visited-grid shift bug** — fixed in single commit (`d8db513`). The inner `for (j=1; j<num_parties; j++) { npc_visited_set(n, x, npc_visited(n, x+1)); }` body never referenced `j`, so only `visited[x]` was overwritten — `visited[x+1..count]` still pointed at their pre-shift owners. Fix: `for (j=x; j<party_count(); j++) { npc_visited_set(n, j, npc_visited(n, j+1)); }` — proper compaction down by one slot. After party_count_dec the count is the new (smaller) value; reading j+1 up to the old tail reads the about-to-be-discarded data correctly. Fixture-unreachable today (would need a multi-party realm + login + sysop kill of a different party); 39 fixtures byte-identical on both gates.

- **towne.c::enter `state.twn == 2` city/village fork** — closed (single commit, `5493dff`). Lifts settlement class onto each town record in `data/world.json5` under `class: 'city' | 'village'` (default 'village' if absent). Adds `res_town_is_city(int twn)` to res.c/h. towne.c::enter swaps `state.twn == 2 ? "city" : "village"` to `res_town_is_city(state.twn) ? "city" : "village"`. Adding a fifth town as a city is now a pure data edit.

- **§24bis phase 1 — slug-form sym accessors** (single commit, `b08978d`). Adds the lowercase-key "slug" accessors paired with the existing int-slot getters; storage is unchanged so call sites can compare against script-stable symbols rather than PC_FIGHTER / 1..6 magic numbers. New API: `res_enum_sym(id, index)` (base; returns `enums[i].keys[idx]` for keyed-record enums, falls back to display string for positional arrays); `pm_class_sym` / `pm_race_sym` / `pm_class_is_sym` / `pm_race_is_sym` (member-handle slug + symbol-equality predicate); `pa_class_sym` / `pa_race_sym` / `pa_class_is_sym` / `pa_race_is_sym` (current-party variants in party.c/h); `npc_job_sym` / `npc_job_is_sym` (state.h, slug for NPC's job). No callsite churn yet; phase 2 / 3 land separately.

- **§24bis phase 2 — speak.c npc_job slug compares** (single commit, `f21f7af`). Lifts the two `npc_job(talker) == res_enum_id("job", "bard"|"bargirl")` sites in speak.c::speak Q-prompt dispatch to `npc_job_is_sym(talker, "<sym>")` — direct enum-key compare, no res_enum_id call per dispatch. Phase 2 sweep verified ~complete: only 13 PC_* references remain in the codebase (compat.c name→id mapper, party.c::pa_is_spellcaster, res.c spell-book / loader lookup) and they're all in translator/loader code where the int form is correct.

### Closed since the previous snapshot (2026-04-28 #3)

- **State-abstraction sweep effectively closed — `party_at(int idx)` helper + every cross-party + remaining-current-party callsite outside the abstraction-home files lifted** (four commits, `8d3abbd..0dd1580`). Introduces `struct party *party_at(int idx)` in party.c / party.h: thin by-index handle returning `&state.p[idx]` with a bounds-checked exit on out-of-range. The current-party-only `pa_*` family is the right shape for the 95% of call sites that read / mutate the active party (state.cpl); party_at is the escape hatch for the cross-party walks. **By-file lifts**: **subs.c::scorelist** (11 cross-party reads — `state.p[i].{exp[j],lv[j],kills,party_name,name[0],fireshield,bracelets,mastercrown,silverkey,jazzyfoot,cl[j]}` collapsed via a single `const struct party *p = party_at(i)` binding at the top of the loop body); **driv.c** (50+ sites: createparty's per-field setup parameterized by `a = state.num_parties` lifted via `struct party *p = party_at(a)` bound once after `a = state.num_parties` is set, all 45 inline `state.p[a].FIELD` reads/writes become `p->FIELD`; the_game's initial-time-left loop, owner-name match, silverkey-sweep menu loop, write_current_world / read_current_world per-party loops, and terminate's free + memmove + clear-tail block all swap to party_at; the memmove block keeps its sizeof as `sizeof(struct party)` for clarity); **replay_knobs.c** (11 sites across `rk_apply_party_lv` / `rk_apply_pos` / `rk_apply_ydarg_grants` / `rk_apply_party_wp` / `rk_apply_party_hp_full` / `rk_apply_dead_slots` — all six helpers bind `struct party *p = party_at(a)` once at the top, with `rk_apply_dead_slots` binding as `party` instead of `p` to avoid shadowing the existing `const char *p = env` env-string walker); **primitives.c** (5 sysop backdoor writes — `state.p[state.cpl].time_left += 50` → `pa_add_time_left(50)`, `state.p[state.cpl].inventory[N] = id` → `pa_set_inventory(N, id)`, `state.p[state.cpl].gold += 1000` → `pa_add_gold(1000)`; party_at not needed since these are current-party). All four lifts are byte-identical on the 39 fixtures across both gates; no `.expect_snapshot` rebless required. **Pattern**: by-field by-index helpers (pa_kills_at, pa_party_name_at, etc.) would have required ~15 new helpers for one call site each — `struct party *party_at(int)` returning a handle was the right trade-off for the read-everything cases. Encapsulation is preserved because party_at hides where state lives (only party.c knows state.p[] is an array of struct party); a future migration (hash, vec, JSON5-keyed slug) only changes party_at's body. **Closed-empty observation**: the party_at design discussion considered also adding `party_count(void)` as the abstraction for `state.num_parties`, but kept tight scope — that's a separate refactor since num_parties is a different concept (count, not party-state).

### Closed since the previous snapshot (2026-04-28 #2)

- **State-abstraction ride-along — four files lifted off `state.p[state.cpl].FIELD`** (four commits, `75fd006..4ec26b1`). The cheapest tier of the State-abstraction sweep — every call site swaps a raw struct field read/write for an existing `pa_*` / `npc_visited` helper, no new abstraction added. By file: **towne.c** (2 hits in `enter_tax_preamble` — gold read for the percentage calc + decrement on payment → `pa_gold()` / `pa_add_gold(-amount)`); **missions.c** (3 hits — `guard_matches` reads x/y for MG_POS / MG_DUNGEON_IDX / MG_TERRAIN matching → `pa_x()` / `pa_y()`; `resolve_bindings` reads `name[b->arg]` for MB_PLAYER_NAME → `pa_name(b->arg)`. The single MB_PLAYER_NAME call site passes arg=2 — pa_name's NULL-on-out-of-range guard never trips); **speak.c** (2 hits in `speak()` — `talker.p->visited[state.cpl]` read for the recognition arm + write on first-meeting → `npc_visited(talker, state.cpl)` / `npc_visited_set(talker, state.cpl, 1)`. Both helpers were already in use in driv.c — extends them to the second consumer, the unused-includes warning on `ryl.h` post-lift was confirmed pre-existing-pattern across the codebase and intentionally left alone); **res.c** (1 hit in `res_dungeon_encounter_recurring`'s `until_flag` arm → `pa_flag(slot)`; the `(unsigned char)` cast on `want` is dropped since pa_flag returns int and want is already int32_t. Adds party.h include — no cycle since party.h pulls state.h but res.h does not pull party.h). 39 fixtures byte-identical on `make check` + `make testasan` at every commit; no `.expect_snapshot` rebless required. **Closed-empty observation**: the per-file unused-includes warnings flagged by clangd post-lift (`ryl.h` no longer needed in speak.c; `ryl.h` / `towne.h` already unused in res.c pre-lift) are out of scope — `ryl.h` is the kitchen-sink include used as a transitive carrier across the whole codebase; cleaning it up file-by-file is its own queue and would break compile in ~all migrated files without parallel ctype.h / stdio.h / compat.h / state.h additions.

### Closed since the previous snapshot (2026-04-28)

- **§17.A.4 slice D — subs.c text extraction**. The §17.1 plan table sized subs.c at 1310 LOC; the file at HEAD was 811 LOC because earlier §20 / §22 work had migrated the camp / rest body (`play_script` into `data/world/scripts/rest.json5`), the hunt body (same shape into `hunt.json5`), and the priest spell-cast handoff (`play_spell_body`) — leaving only the dispatcher narration in `foodhandler` (tired / exhausted / weak / starved arms), the equipment-swap menus (`weaponoff` / `armouroff` / `weaponr` / `armourr`), `drop`, `quaff`, and the priest-spell prompt scaffolding. Forty inline `tx()` calls collapse to seventeen text records under `subs.*` (three shared across multiple callsites — `who_prompt` ×6, `dead` ×4, `no_room` ×2). One small static helper `tx_who_prompt(void)` (7 lines) folds the six "Who? (1-N)" callsites. The column-aligned priest spell-list rows in `subs.c::spell` (lines 608 + 611) stay inline per the §20 L6 / slice B convention along with their static "8. Abort" trailer. The §17.4 `scorelist` caveat didn't apply: `scorelist()` writes `data/vscore.txt` / `data/score.txt` via `fopen` + `fputs` rather than terminal text, so there's nothing to lift in this slice and the §14.4 sort-of-indices rewrite stays in its own queue (the `Fighter`/`Thief`/etc. class names are queue-able under §24bis or §12.3 alongside the rest of the class-id work). 39 fixtures byte-identical on both gates, no `.expect_snapshot` rebless required (greps for the lifted literals across `tools/replay/*.expect_snapshot` returned zero hits across the board). Body in `port-plan-complete.md` "§17.A.4 slice closures" section. **§17.A.4 closes end-to-end** (slices A + B + C + D landed; slice E unnecessary — slices A-D didn't surface the ≥3 near-identical C blocks that §17.2's recommendation gated DSL widening on); **§17 closes end-to-end** with it, body relocated to `port-plan-complete.md` (full §17.1 / §17.2 / §17.3 / §17.4 text preserved verbatim alongside the slice closures).

### Closed since the previous snapshot (2026-04-27 #16)

- **§17.A.4 slice C — dungeon.c text extraction** (single commit, `6f63350`). Like slices A and B, the §17.1 plan table sized this slice much larger than reality: 917 LOC in the table, 175 LOC at HEAD. Earlier §17 / §22 work had migrated all per-event encounter narration into `data/world/scripts/dungeon.json5` + `data/world/texts/dungeon.json5`, leaving only the dispatcher-loop UI in `dungeon.c::dungeon` to lift. Twelve inline `tx()` calls collapse to six text records under a new `dungeon.loop.*` namespace (`level_menu` / `enter_level` / `who_prompt` are `tx_fmt`'d with `{level}` / `{max}` bindings; `no_torches` / `at_max_depth` / `leave_cavern` are static `tx0(res_text)`). 39 fixtures byte-identical on both gates including `wilderness-cave-descent` (45445 bytes, the fixture that exercises the dispatcher-loop UI); no `.expect_snapshot` rebless. Body in `port-plan-complete.md` "§17.A.4 slice closures" section.

### Closed since the previous snapshot (2026-04-27 #15)

- **§23.A follow-up — monster-melee fallback arm lifted to DSL** (two commits, `4468739..b99de4e`). The §23.A residual closure (2026-04-27 #8) lifted the four melee tuning constants into `combat.monster_attack` but left the surrounding C control flow inline; this closure lifts the whole arm into a script DSL `do:` body parallel to the four spell bodies under `monster_spells.spells[]`. The plan's "two helpers needed" claim was stale: `for_each_alive_player` / `pick_random_alive_player` / `monster_hp` / `battle_flag` / `member_field` / `chance` / `obdamage` all shipped during §23.A's spell-DSL work; only a `{ combat_int: '<path>' }` expression op was missing (added as the parent commit, 17 lines). The lift drops two pre-existing C-level quirks per project policy (the `damage_div > 0` div-by-zero protect and the `pa_pl()` death-decrement guard); both are unreachable in practice. RNG identity preserved exactly; 39 fixtures byte-identical on both gates. **§23.A closed end-to-end.** Body in `port-plan-complete.md` "§23.A follow-up closure" section.

### Closed since the previous snapshot (2026-04-27 #14)

- **§17.A.4 slice B — interact.c text extraction (closed empty)**. The §17.1 table sized interact.c at 428 LOC; by the time slice B was picked up, the file was 69 lines because earlier §17/§22 work had migrated the drop/swap/HP/rest flows, the §12.4 residuals pass had moved the journal helpers in from driv.c, and §20 T1 had reduced `check_terrain` to a `terrain.transition` script wrapper. The two remaining `tx(...)` calls inside `read_journal()` are dynamic-format (bullet prefix on a stored journal entry) and a literal blank-line separator — both match the column-aligned/pass-through inline convention noted in the §20 L6 closure for battle.c. No source changes; no fixtures touched. Body in `port-plan-complete.md` "§17.A.4 slice closures" section. Closure shape mirrors §17.A.5 (driv.c/ydarg.c residuals — closed empty per §17 footer).

### Closed since the previous snapshot (2026-04-27 #13)

- **§17.A.4 slice A — missions.c text extraction**. Block 5 (Thun gem-altar) was the only inline residual; 15 of the 16 historical trigger blocks had migrated during §16.5/§22 mission-state-machine work. Five `tx("...")` runs lifted into `missions.thun_temple.{temple_arrival, altar_inscription, gem_prompt, no_gems, altar_opens}`; bundled `gem_prompt` absorbed the previous `common.yes` call + the `(L)eave` line, matching the menu-bundling convention. C control flow (Y/L key, `inv_count_in_range(39, 41)` guard, `dungeon(18, "thun_temple")` handoff) unchanged. Block 5 is fixture-unreachable (only Thun/bracelet refs in fixtures are unrelated rumour text + special-item inventory line); no rebless required. 39 fixtures byte-identical on both gates. Body in `port-plan-complete.md` "§17.A.4 slice closures" section.

### Closed since the previous snapshot (2026-04-27 #12)

- **§25.B step 4 — NPC axis flip** (single commit, `404b1e8`). Closes §25.B end-to-end. Body moved to `port-plan-complete.md` "§25.B step closures" section. 39 fixtures byte-identical on both gates with no `.expect_snapshot` rebless required.

### Closed since the previous snapshot (2026-04-27 #11)

- **§25.B step 3b — town axis flip + heap-alloc + enums.towne wilderness lift** (four commits, `bedd842..f66247a`). Body moved to `port-plan-complete.md` "§25.B step closures" section.

### Closed since the previous snapshot (2026-04-27 #10)

- **§25.B step 3a — NPC abstraction + wilderness-sentinel lift** (four commits, `0056881..d445b99`). Body moved to `port-plan-complete.md` "§25.B step closures" section.

### Closed since the previous snapshot (2026-04-27 #9)

- **§25.B step 1 — monster-slot axis** (`684b7e5`). Body moved to `port-plan-complete.md` "§25.B step closures" section.
- **§25.B step 2 — party-member axis** (`2ff078f`). Body moved to `port-plan-complete.md` "§25.B step closures" section.
- **§25.B input → slot LOG_D traces** (`4e4b6fa`). Body moved to `port-plan-complete.md` "§25.B step closures" section.

### Closed since the previous snapshot (2026-04-27 #8)

- **§12.4 mechanical residuals — all four shipped** (2026-04-27 #9, four commits on top of `6a6ee89`):
  - `.git-blame-ignore-revs` records the `33ffc52` clang-format reformat so blame skips it (`88b33b5`).
  - `title-menu-exit.env` gained `RYL_NO_DELAY=1`; replay drops ~15s → ~50ms, byte-identical (`22ad817`).
  - `read_journal()` + `journalf()` lifted from `driv.c` to `interact.c` (43-line drop in driv.c); `speak.c` swapped its `driv.h` include for `interact.h` since `journalf` was the only driv.h symbol it used. Text id `driv.journal.header` left as-is (renaming would touch `data/world/texts/` + fixtures with no payoff). (`8442d03`).
  - README §18.5 terminal-compat paragraph (Path A copy) (`4bcc0d9`).

### Closed since the previous snapshot (2026-04-27 #7)

- **§20 L6 — battle.c narration sweep**. ~30 hardcoded `tx("English")` sites in battle.c lifted to data/world/texts/battle.json5 via tx0(res_text(...)) + tx_fmt with {placeholder} bindings. Five tx() remain inline as pure pass-through `%s\r\n` (monster/caster name) or column-aligned table rows — matches the speak.json5 / towne.json5 / driv.json5 convention. (97e341e)
- **§23.A residual — monster-melee formula constants**. The four numeric constants in the melee-fallback arm (hit_bonus / damage_div / damage_spread_max / damage_min) lifted to combat.monster_attack in world.json5; reads symmetrically with combat.attack which §23.C already lifted for the player side. Branch structure stays in C pending a `for_each_alive_player` DSL helper. (2f1911b)
- **§20 LARGE residuals — verified DONE**. L3 (battle.c item-effect switch ids 100..111) is now `res_magic_item_effect_node` + `play_spell_body`; L5 (monster magic table at battle.c:625-740) was lifted with §23.A — the `g==5` block is the data-driven dispatcher. Both confirmed by reading battle.c at HEAD. L6 closed in this session.
- **§20 TINY T3/T4 / §22.10 / Mechanical residuals — verified DONE**. T3/T4 path literals routed through `res_data_path` (§22.14 commit 3271b14). §22.10 dungeon_encounter slots 10..19 collapsed (commit b258cb0). Makefile already carries `-MMD -MP` (line 8). ANSI SGR macros live in `src/ansi.h`. The §22.12 / 2026-04-27 #7 snapshot wasn't refreshed after §22.14 / §24 closures — those bullets had been silently superseded. Cleared in this snapshot.

### Vision / reference (always stays in this file)

- **§1, §8, §9** — what the code is, what'll bite you, end-state vision.
- **`plans/future-directions.md`** — multi-party / MUD / shared-realm scoping. Long-term direction; no scheduled work.

### Recommended next session

**Session #13's full queue (#1..#5) all closed in #13b** (six commits `855c138..e7cbd71` plus the four #13 commits applied earlier). `struct ryl_state` is now zero static caps; `subs.c::spell` priest-list bound is data-driven; `subs.c::rumour()` is a 3-line DSL trampoline; §20 L2 confirmed already-closed (with two latent bug fixes); the `temp_low_damage` doc clarification settles the "possible bug" tracking item.

The queue below is now organized by what should still be extracted from C → JSON5 to make the engine more generic. After session #13b's broad survey of every src/ .c file, the actual residual "this is content masquerading as code" items are:

#### Tier A — UI layer still hardcoded (highest leverage)

  - **A1. Terminal layout / screen positions (`c(x, y)` calls)**. The most pervasive. driv.c hardcodes statusline anchors (`c(3, 21)`, `c(13, 22)`, `c(49, 6)` after every blit, `c(0, 0)` for cursor reset); gfx.c::blitgfx anchors at `(49, 6)` after every render and clr_map_window has a hardcoded 8×9 erase grid (`c(8, 6)..c(8, 14)`); primitives.c::c() itself takes runtime args, but the call sites are content. Lift target: `data/world.json5 screen:` subtree with named regions (`screen.text_window: { x: 49, y: 6, w: 32, h: 15 }`, `screen.statusline_top: { x: 3, y: 21 }`, `screen.statusline_bottom: { x: 13, y: 22 }`, `screen.map_window: { x: 8, y: 6, w: 16, h: 9 }`, …) + a small `screen_*` accessor family in res.h. Bundle a clr_map_window DSL refactor that takes the region as a binding. Multi-callsite sweep across driv.c (12+ sites) + gfx.c (~10 sites). Session-sized.

  - **A2. Statusline content (TIME / status-bucket labels + H1..H4 template)**. driv.c::statusline emits `"TIME: DAWN"` / `"MIDDAY"` / `"TWILIGHT"` / `"MIDNIGHT"` based on hour ranges hardcoded in the function; status-bucket labels (`HUNGRY` / `STARVING` / `OK`) sit on inline `pa_food()` thresholds; the H1..H4 grid is one big snprintf. The four TIME labels are pure content (already could be `data/world/texts/driv.statusline.time.<bucket>`); the hour-range buckets themselves could move to `world.json5 status.time_buckets: [{lo: 6, hi: 11, text_id: 'driv.time.dawn'}, ...]`. Status labels follow the same shape. The H1..H4 template benefits less from lifting (it's tightly coupled to the 80-col game window per §17 convention) but the TIME / status emits are clear wins.

  - **A3. View command body** (driv.c::the_game's `case 'V'`). "Who? (1-N) / All" prompt + per-member `show()`, plus the help-screen render at line 1582. Small bodies, currently inline. Same shape as the §17 lifts — text records are already sharded; just need to wrap the dispatch in `play_script("driv.view", ...)`.

#### Tier B — bare-int dispatch in combat / inventory

  - **B1. battle.c magic-item case labels** (per `project_hardcoded_indices_doc.md` P4). The combat dispatcher still has a `switch (item_id)` over magic-item ints. Should migrate to slug-keyed dispatch via `res_enum("magic_item", ...)` plus a `magic_items.<slug>:` script tree, mirroring how dungeon.c migrated. Touches battle.c only; new shard `data/world/scripts/magic_items.json5`.

  - **B2. Equipment-swap UI flows** (subs.c::weaponoff / armouroff / weaponr / armourr). Four near-identical bodies (~40 LOC each) doing: digit-prompt → check has-equipment → check inventory-room → swap → emit. Already use res_text + tx_fmt for the emit; the cascade structure is in C. Lift to a shared `subs.equipment.swap` DSL script with bindings (`{slot, side: 'on'|'off', kind: 'weapon'|'armour'}`); collapses ~160 LOC of subs.c body. Compresses the file substantially after the existing #5 priest-list lift.

#### Tier C — engine state still partly in C

  - **C1. `foodhandler` fatigue ladder** (subs.c). Mostly data-driven (`upkeep.starve_damage`, `balance.fatigue.exhausted/weak`, etc.), but the ladder dispatcher itself (the if-cascade on `pa_hours_rested() == 0` / `== -4` / `== -8` / `< -8` with bespoke text emits per arm) lives in C. Could lift to a `subs.foodhandler.fatigue` DSL script that walks an `fatigue_levels: [{threshold, text_id, drain?}, ...]` table. Less obvious win — the four arms are stable — but consolidates the ladder shape.

  - **C2. `random_wilderness` Karl-Skagerberg easter egg** (subs.c). `if (!(pa_time_left() % 101) && !stricmp(session_player_name(), "Karl Skagerberg")) { encounter(skagerberg_killer); }` — hardcoded player-name compare + period. Could move to `world.json5 secrets:` subtree (e.g. `[{trigger: 'time_left % 101 == 0', condition: 'player_name == "Karl Skagerberg"', action: 'encounter:skagerberg_killer'}]`) with a small `secrets_check` engine_call. Niche but cleanly captured.

  - **C3. Save-file emit shape** (driv.c::write_current_world / read_current_world). The world.json5 emit is hand-coded `fputs("    party_member_max: %d", ...)` style. The DSL has a snapshot.c that emits a structured dump for fixtures; the realm save could in principle share the same plumbing. ~500 LOC total in driv.c that could shrink. Bigger lift; not session-sized.

#### Tier D — structural cap-flips (storage shape)

  - **D1. `struct party.flag[40]` cap-flip**. Last hardcoded byte-array on struct party. Sized to 40 mission-flag slots; `world.json5 limits.party_flag_max` could drive it. Would need careful save-format coordination since flags are persisted by slot id. Same shape as §12.3 cap-flips.

  - **D2. NPC_SLOT_HARD_MAX cap-flip** (RNG-identity gated). Lifting the static 41 to data requires re-rolling random NPC placement RNG identity (different bin counts shift the stream); every fixture using random NPC arrangement would need a rebless. Defer until > 41 NPCs has real demand.

  - **D3. TOWN_SLOT_HARD_MAX cap-flip** (RNG-identity gated). `int counts[TOWN_SLOT_HARD_MAX]` in res_realize_npcs is a small heap-alloc lift, but bumping the cap shifts the random NPC town-bin draw. Defer.

#### Tier E — script-side audit (the DSL has now grown enough to reward refactoring)

  - **E1. data/world/scripts/*.json5 repeat-pattern collapse**. Recurring snippets (e.g. "ask_target → cost-check → mutate → emit-result" cascades, "if member-stat < N → emit-fail; else → consume + apply") might benefit from new shared DSL ops. Low-pressure cleanup; do when an author trips over the duplication.

  - **E2. dungeon.c residual scripting refinements**. The 28 scripts in dungeon.json5 (live since 2026-04-25) aren't audited for code-as-data opportunities (inline RNG ranges that could become formulas, duplicated emit patterns). Worth a survey.

**Pick order suggestion**: Tier A (highest leverage; UI layer is the most "this should be authored") → Tier B (bare-int dispatch is the next biggest engine-vs-content boundary) → Tier C/D as opportunity surfaces.

Per-session bar: `make check` + `make testasan` both green at every commit; format gate (`make format`) now also runs in CI on push + PR. Push only on explicit user approval (per AGENTS.md).

## 1. What the code actually is

- **Language:** K&R-ish C, single-threaded, globals-heavy, heavy `tx()`/`c()`
  printf-wrapper style.
- **Target:** Amiga SAS/C, linked against the Xenolink door library.
- **UI:** Already **ANSI escape codes** (`\x1b[...m`, cursor positioning via
  `c(x,y)`). It does not paint a character grid — it just streams ANSI to a
  modem. That is fundamentally compatible with every modern terminal.
- **Two builds, nearly identical:**
  - `ryl.c` — Amiga Xenolink door version.
  - `pcryl.c` — PC/CLI variant (same source, slightly different formatting —
    likely produced by a broken line-ending round-trip; see §7).
- **Two main entry points already coexist in every module:**
  `if (cli) { printf/gets/getch } else { XCB->DisplayText/GetLine/GetCharacter }`.
  Roughly 80% of the Xenolink dependency is already gated behind `!cli`.
  This is the key finding — a CLI port is partially done.

## 6. Language: modernize to C99 vs C++17 subset — decision deferred

Before starting Phase 0, pick a target language. Both paths are viable;
they diverge in cost and ergonomics, not capability. The decision can wait
until you've diffed the pcryl/ryl duplicates and scoped the work, but it
shouldn't wait past Phase 1 — mid-project migration is painful. The
existing code compiles as either.

### Shared baseline (required on either path)

These are bug fixes, not style choices. Do them during Phase 2 regardless
of which language you pick.

- `getstr()` at `src/ryl.c:123` returns a pointer to a stack-local
  `char svar[90]`. Undefined behaviour — callers happen to copy before
  the frame goes away. Fix via static buffer, out-param, or (C++)
  return `std::string`.
- `sprintf` / `strcpy` / `strcat` — ~30 call sites with no bounds check.
  Replace with `snprintf` (C) or `std::string` / `fmt::format` (C++17).
  Audit every fixed-size buffer while you're there — `char pos[16]`,
  `char svar[90]`, `char str[144]` are sized by guess.
- `gets()` — removed in C11, Phase 2 already swaps it.
- Compile with warnings-as-errors on format strings:
  - C: `-std=c99 -Wall -Wextra -Werror=format-security -Wno-parentheses`.
  - C++: `-std=c++17 -Wall -Wextra -Werror=format-security
    -Wno-parentheses -Wno-write-strings`.

Roughly one day of work regardless of path.

### Path A — C99 / C11

Keep everything as C. After the baseline pass, you have a safer BBS door
with the same idioms.

**Pros**
- Zero toolchain change. `cc` on any Unix.
- No risk of C-as-C++ subtleties. Smaller diff.
- POD save structs trivially portable.
- Smaller binary, shorter compile times.

**Cons**
- Resource system (§9) needs a hashmap. Either roll your own, pull in
  uthash / klib (single-header), or scan a flat JSONL array linearly
  (fine at this scale — ~500 records).
- Dialogue interpreter (§9) wants tagged unions. C unions work, just
  more verbose.
- String handling stays manual. Every format call is a size audit.
- JSON parser: cJSON (single file, drop in).

**Cost on top of baseline**: 0 extra. The baseline is the modernization.

### Path B — C++17 subset

Compile with `g++ -std=c++17`. Use a narrow subset — "better C", not
idiomatic C++.

**Allowed**
- `std::string`, `std::string_view`.
- `std::vector`, `std::array`.
- `std::unordered_map`, `std::map`.
- `std::variant`, `std::optional`.
- RAII for file handles, termios state, allocations.
- References and `const` correctness where they clearly help.
- `constexpr` for compile-time tables.
- `nlohmann/json` (single header) for the resource loader.

**Forbidden (this is still a port, not a rewrite)**
- Exceptions. Use return codes or `std::optional`.
- Templates beyond using STL.
- `<iostream>`. Keep `stdio.h` — matches the existing `tx()`/`printf`
  style, and streams are slow and verbose.
- Boost, ranges, concepts, modules, coroutines.
- Inheritance / virtual / polymorphism. The `io_backend` struct-of-
  function-pointers in §4 stays exactly as it is.
- Operator overloading beyond what STL expects.

**Pros**
- `std::string` removes ~90% of the buffer-audit work in the baseline.
- `std::unordered_map<std::string, Record>` is the resource index.
- `std::variant` + `std::optional` make the dialogue AST ergonomic
  without a hand-rolled tagged-union type system.
- RAII restores termios / closes files on every error path
  automatically — big win for TTY state hygiene.
- Existing code compiles as C++ almost unchanged.

**Cons**
- `g++` instead of `gcc`. Minor.
- Compile-time hunt for C-as-C++ issues:
  - Implicit `void*` → typed casts. `malloc` return needs `(char*)`.
  - `char *` vs `const char *` — string literals become `const char*`
    in C++; the code frequently passes them as `char *`. A
    `-Wno-write-strings` suppresses this, or you do the cleanup pass.
  - Enum vs int implicit conversions.
  - `new`, `class`, `template`, `this`, `friend` as identifiers. A
    quick scan showed none in `ryl.h` / `driv.c`, but check.
  - Functions missing a `return` on non-void paths (warning in C,
    error in C++). I spotted one: `object()` at `src/towne.c:307`
    falls off the end past `case 111`.
- POD save structs stay POD. Do **not** put `std::string` inside
  `struct party` — keep `char name[5][30]`. The save format
  (`fwrite(&p[i], sizeof p[i], …)`) is a hard compatibility
  constraint, and you probably want to keep existing `REALM.DAT`
  files loadable.
- Slightly larger binary, slightly longer compile.

**Cost on top of baseline**: 1 extra day for the initial C-as-C++
compile pass. Thereafter cheaper than C for every new file you write.

### Decision matrix

| Endpoint | Pick |
|---|---|
| "Modernized BBS door, Phases 0-5 only." | C99 |
| "Data-driven engine + resources (§8, §9)." | C++17 subset |
| Uncertain / want to decide later. | C99 now, migrate at Phase 6 if needed. The baseline work is identical either way. |

Migrating C→C++ after Phase 5 costs ~2 days (mostly the same C-as-C++
compile pass, plus adopting `std::string` in new code going forward).
It's not free, but it's not a trap either. The main risk is writing
"too-C++" code and regretting the subset boundaries later — hence the
explicit Forbidden list above.

## 8. Things that will bite you

- `getstr()` stack-pointer return (`ryl.c:123`), unchecked `sprintf`,
  unchecked `strcpy`/`strcat`, and guess-sized fixed buffers are covered
  by §6's shared baseline (Phase 2 step 13). Don't skip that pass — the
  bugs are real, they just haven't fired because callers happen to copy
  before the stack frame dies.
- Amiga-only helper sources (`reveal.c`, `npclist.c`, `optdata.c`, `scan.c`,
  `rylreg.c`) were deleted in Phase 5 step 25 — each had its own `main()` and
  was never linked into the game.
- `speak.c` (1166 lines) and `strid.c` (933 lines) have the most `Delay()` and
  `pause()` pacing — the places where wrong `Delay` granularity will be most
  visible.
- Character encoding: comments in `subs.c` use Swedish (`"slumpvis"` =
  "randomly", `"spelare"` = "players"). Source is ISO-8859-1 / Amiga. Add
  `-finput-charset=latin1` or convert files to UTF-8 in one pass.
- `char` signedness: a few spots use `char` values as array indices. On ARM
  macOS (`-fsigned-char` vs `-funsigned-char`), audit with `-Wchar-subscripts`.

## 9. End-state vision: a data-driven engine

The bigger architectural goal this port is pointing at: turn the source
tree into **a small generic engine plus a content archive**, not a
monolithic game in C. "Rylween" becomes one set of resource files that
runs on top of an engine; the engine could in principle drive another
text-RPG given different content.

That's an ambitious target. The good news is it's the natural end state of
the abstractions you're already stacking up:

1. §4 — Abstract the I/O boundary → engine is independent of transport.
2. §5 — Extract ANSI tile art → art is data.
3. §7 Phase 5b — Lift globals into a `ryl_state` struct → engine is
   re-entrant and testable.
4. §9 — Extract text, items, spells, NPCs, towns, missions → content is
   data.

What's left in code after all three is the **engine**: turn loop, map
navigation, combat math, pathfinding, save/load, resource loader, renderer
primitives. Maybe 2-3 kLOC, compared to the current ~12 kLOC.

### Inventory — what's currently code but should be data

Five categories, ordered roughly by extraction difficulty.

**1. Static string arrays** — trivial:

- `char *jobname[]` — 16 entries, `src/ryl.c:23`.
- `char *name[]` — 40 entries, `src/ryl.c:43`.

**2. Functions that are really tables** — mechanical:

- `object(int nr)` — 47 items across 3 ID ranges, `src/towne.c:307`.
- `obcost(int nr)` / `obdamage(int nr)` — item cost/damage lookups.
- `sextext(nr, xx)` — pronoun lookup, `src/driv.c:722`.
- `kar(char t)` — character-class display name, `src/subs.c:254`.
- `cf(int)` — another lookup.
- Spell name/cost/description tables scattered in `subs.c`.
- Monster encounter definitions in `strid.c` (68 case-statements).

**3. NPC, town, and world-location data** — structured, worth pulling out
as first-class records:

- **Fixed NPCs** — `place_npcs()` in `src/person.c:75-164` hardcodes NPC
  slots 25-35 (innkeepers × 4 towns, tavernkeepers × 4, bargirls, the
  hermit at `(48,7)`, etc.). Each is a `{job, town, building, x, y}`
  record. Should be 11 JSONL `{"kind":"npc_fixed", ...}` records.
- **Random-NPC generation rules** — `person.c:43-71` places NPCs 1-24 by
  randomizing job=(i mod 12) + town/building/coords. The rules (valid
  coord range, collision avoidance with hermit at col 48, forbidden
  terrain chars `m`/`w`/`0`/`1`/`2`) should live in a `{"kind":
  "npc_spawn_rules", ...}` record.
- **Towns** — 4 towns, each with a fixed set of buildings (inn, tavern,
  smith, church, merchant, guild, houses). Currently embedded in
  `towne.c`'s navigation code. Should be `{"kind":"town","id":1,
  "name":"...","buildings":[{"type":"inn","keeper_npc":25}, ...]}` records.
- **World landmarks** — `position_locations()` in `driv.c:245` seeds
  `dx[1..9]`, `dy[1..9]` at campaign start as dungeon/cave locations.
  The *count* and *placement rules* are data; the *specific coords* per
  campaign stay in the save file. So: `{"kind":"landmarks","count":9,
  "place":{"x":[5,75],"y":[5,35],"forbid_terrain":"wm012"}}`.
- **Map** — `data/Map.DAT` is already a file. Either keep it or fold it
  in as a `{"kind":"map","rows":[...]}` record for uniformity.
- **Terrain glyphs → meaning** — single-char map codes (`m`=mountain,
  `w`=water, `0`/`1`/`2`=town types, etc.) are hardcoded in checks
  scattered across `driv.c` and `subs.c`. Should be a
  `{"kind":"terrain","glyph":"m","name":"mountain","passable":false,
  "enter":null}` record set.
- **Dungeon/cave structure** — `dungeon(int,int)` in `dungeon.c` has the
  monster/treasure generation rules. Parameter: dungeon depth tier, loot
  tables. Extractable.

**4. Missions / quest flags** — `missions.c` (14 kB) and flag refs
(`p[cpl].flag[40]`) scattered everywhere. Each flag has implicit semantic
meaning (flag 1 = Garth's mission accepted, flag 2 = gold found, etc.).
Missions are state machines defined implicitly by who sets/reads which
flag. Moving to data means:

- Naming every flag: `{"kind":"flag","id":1,"name":"garth.quest.accepted"}`.
- Defining each mission as a sequence of states with triggers:
  `{"kind":"mission","id":"garth.refund","stages":[{"accept":{"npc":1,
  "set":1}},{"complete":{"encounter_drops":"gold","set":2}},{"reward":{
  "npc":1,"need":2,"give_gold":500,"set":3}}]}`.

This is the **hardest** category and the one that most resembles
"writing a small DSL". Leave for last.

**5. Dialogue trees** — `speak.c` (37 kB, 1166 lines). The biggest single
piece. Each NPC's dialogue branches on mission flags and gives
conditional responses. Naturally expressible as:

```jsonl
{"kind":"dialogue","npc":"garth","if":{"flag":"garth.quest.accepted","eq":0},"lines":["Hail traveler.","I was robbed of 500 gold..."],"offer":{"mission":"garth.refund"}}
{"kind":"dialogue","npc":"garth","if":{"flag":"garth.quest.accepted","eq":1,"and":{"flag":"garth.refund.complete","eq":0}},"lines":["Any luck finding my gold?"]}
{"kind":"dialogue","npc":"garth","if":{"flag":"garth.refund.complete","eq":1},"lines":["You have my thanks.","Here's your reward."],"give_gold":200,"set_flag":"garth.refund.paid"}
```

A simple condition language (`if`, `and`, `or`, `flag_eq`, `has_item`) plus
a simple effect language (`set_flag`, `give_gold`, `give_item`, `goto`,
`encounter`) covers essentially all of `speak.c`. The engine gets a ~200-
line interpreter; `speak.c` goes from 1166 lines to ~0.

**Spell effects share this DSL.** The 7 priest spells (`subs.c spell()`,
lines 799-1156) and 6 mage spells (`strid.c` combat block,
lines 406-599) are effect machines with the same shape as dialogue
effects: target selection + context gate + mutate game state + say
flavor text. Design the DSL once so both dialogue and spells run on the
same interpreter:

```jsonl
{"kind":"spell","id":"priest.heal","class":"priest","slot":1,"name":"Healing","base_cost":8,"effect":{
  "ask_target":"party_alive",
  "mutate":[{"set":"target.hp","val":{"+":["target.hp","10","(rand 0 9)","(* caster.lv 2)"],"clamp_to":"target.h"}}],
  "say":"spell.priest.heal.success"
}}
{"kind":"spell","id":"priest.banish","class":"priest","slot":4,"name":"Banish Evil","base_cost":85,"effect":{
  "require":[{"context":"dungeon"}],
  "mutate":[{"set":"state.banished","val":1}],
  "say":"spell.priest.banish.narration"
}}
{"kind":"spell","id":"mage.frost","class":"mage","slot":1,"name":"Frost Finger","base_cost":8,"effect":{
  "require":[{"not":{"target":"immune"}}],
  "ask_target":"monster_alive",
  "mutate":[{"set":"target.hp","val":{"-":["target.hp",{"*":["caster.lv",{"+":["(rand 0 6)","2"]}]}]}}],
  "say":"spell.mage.frost.success"
}}
```

The primitives needed for spells (`target.hp`, `caster.lv`, `state.banished`,
`state.tid`, `rand`, `clamp_to`, `context`) overlap ~90% with what dialogue
needs (`flag_eq`, `has_item`, `party.gold`, `set_flag`, `give_gold`).
Designing them in one coherent grammar avoids two half-compatible DSLs.

**Phase sequence for spells:**
- **Phase 6b (now):** extract spell metadata (name, slot, class, base_cost)
  as data. Use it only for the menu and cost formula. Keep effect bodies
  as static C functions (`do_heal`, `do_frost`, …) — preserves the exact
  call order and flavor text each spell depends on. Sets up the record
  shape the DSL will consume later. ~80-100 line reduction.
- **Phase 6d (when dialogue DSL lands):** replace each `do_*` body with
  `eval_effect(spell->effect_tree)` one at a time. Effect trees are
  authored in the resource file alongside the metadata. Effect text
  moves to `{"kind":"text",...}` records at that point.

This ordering keeps every phase byte-identity-verifiable with the smoke
harness: 6b changes data layout without touching behavior; 6d migrates
behavior one spell at a time with the DSL interpreter validating against
the preserved reference output.

**6. String literals in remaining `tx()` calls** — large volume, low risk
once patterns are established: menu prompts, status messages, title
credits, nag screens, combat flavor, help text, spell chants.

### JSONL schema

One JSON object per line, discriminated by `kind`. The C side has a tiny
loader that indexes records by `(kind, id)` into a hashmap on startup.

```jsonl
{"kind":"enum","id":"jobname","values":["","Merchant","Ranger","Fighter","Wizard","Priest","Thief","Peasant","Bard","Drifter","Half-Orc","Half-Elf","Smith","Bargirl","Innkeeper","Tavernkeeper","Hermit"]}
{"kind":"enum","id":"npcname","values":["","Garth","Nrim'to","Areth","Nevaak","Gorm","Bernak","Matias"]}
{"kind":"item","id":1,"cat":"weapon","name":"Sword","cost":50,"damage":4}
{"kind":"item","id":15,"cat":"weapon","name":"Magic Dagger","cost":500,"damage":6}
{"kind":"item","id":31,"cat":"misc","name":"Torch","cost":10}
{"kind":"item","id":100,"cat":"combat","name":"Frost Wand","cost":0,"damage":20,"uses":1}
{"kind":"text","id":"title.prog_by","text":"Programming & Design by:"}
{"kind":"text","id":"nag.gnolls.intro","text":"Suddenly some gnolls\nappear out of nowhere.\nThe leader speaks:\n- Tell ze sysop to\n  register, or we'll tear\n  yar guts out!!!\n"}
{"kind":"dialogue","id":"merchant.greeting","speaker":"merchant","lines":["Welcome, traveler.","What can I get you?"]}
{"kind":"spell","id":3,"name":"Banish","mp_cost_base":85,"mp_cost_per_lv":1,"target":"dungeon"}
{"kind":"encounter","id":1,"name":"Gnoll Pack","min_hp":8,"max_hp":14,"dmg":[1,4]}
```

Keep **format specifiers** in the text strings where they exist (`%s`,
`%d`) — the C side still calls `printf`/`tx` with the retrieved string and
the runtime args. The schema doesn't need to know about them; it just
preserves them as literal characters.

Keep **inline SGR color codes** (`\x1b[33m`) in the strings where they
exist — they're part of the content, not formatting to be re-introduced.

### Loader side

Two options:

- **cJSON** (single `.c` + `.h`, public domain, ~2kLOC). Drop in, call
  `cJSON_Parse` per line, done. Zero dependencies beyond libc.
- **Tiny custom parser** — JSONL's per-line structure plus the flat shape
  of these records means a ~150-line parser handles everything you need
  (no nested objects except arrays of strings/ints). Slightly more work,
  zero deps, compiles faster.

Default to cJSON. It's not worth reinventing for this.

**Format choice: JSONL vs JSON5.** Phase 6a shipped on JSONL with the
tiny custom parser (which also silently accepts `#` and `//` line
comments, so section annotations work without JSON5). Reconsidered at
Phase 6b entry: JSON5 (single document, trailing commas, unquoted keys,
inline comments, multi-line strings) is nicer for hand-editing nested
structures, but buying it costs either a dependency or a 500–1000-line
parser extension, and most of the ergonomic win (comments) is already
there. Append-only growth also fits JSONL — phases 6c–f will add
hundreds of records, and one-record-per-line keeps PR diffs clean.
**Decision: stay on JSONL through 6c; revisit at 6d** when nested
dialogue records would actually benefit from multi-line strings and
structural grouping. Switching then is cheap because the loader is ours.

Loader API:

```c
void  res_load(const char *path);          /* resources.jsonl, called at init */
const char *res_text(const char *id);      /* "title.prog_by" -> string        */
const char *res_enum(const char *id, int); /* "jobname", 3 -> "Fighter"        */
const item_t *res_item(int id);            /* items are hot, use a direct array */
```

At call sites:

```c
/* Before */
tx("    Marcus Lagergren\r\n\r\n");

/* After */
tx("%s\r\n\r\n", res_text("title.prog_name"));

/* Before */
switch(nr) { case 1: return "Sword"; case 2: return "Axe"; ... }

/* After */
char* object(int nr) { const item_t *it = res_item(nr); return it ? it->name : "-"; }
```

### What NOT to extract

- **Computed status strings** like `sprintf(stline, "TIME: ...%2d ...")` in
  `driv.c:219`. They're format templates, not content. You'd just end up
  with a string table that's 1:1 with the format call sites.
- **Save-file struct layouts** — those stay binary (`REALM.DAT`,
  `OptData`'s writeout format) because save compatibility matters and
  they're consumed by the game code, not humans.
- **Control-flow "text"** — any string that branches on content (e.g.
  parsed with `sscanf`). Keep inline; externalizing would decouple data
  from the parser silently.
- **The `.ans` scene files** — already data, different format, don't
  collapse into JSONL.

### Eternal vs extractable data files

Your observation is right: `REALM.DAT` and the full-screen `.ans` files are
the only "eternal" runtime state. After extraction:

| File | Status | Format |
|---|---|---|
| `data/resources.jsonl` | **new** | JSONL, all static content. |
| `data/ansi/*.ans` | keep | Raw CP437/UTF-8 ANSI, full-screen scenes. |
| `data/tiles/NN.ans` | **new** (from §5) | One-row-per-line ANSI tiles. |
| `data/Map.DAT` | keep (for now) | ASCII grid; could fold into resources.jsonl as a `{"kind":"map", "rows":[...]}` record if you want total uniformity, but it's working as-is. |
| `data/NPC.DAT` | generated at runtime | Binary blob from `place_npcs()`. Static *templates* for NPCs move to resources.jsonl; runtime placement stays binary. |
| `~/.rylween/REALM.DAT` | save data | Binary struct array, per-party. Stays binary. |
| `distro/OptData` | utility | The save-file cleanup tool; moves or dies in Phase 0. |

### Suggested sub-phases

Do this as a separate pass *after* Phase 5, not mixed into the port. Each
sub-phase is independently committable and testable against the baseline.

- **Phase 6a** — static arrays (`jobname`, `name`). ~2 hours.
- **Phase 6b** — table-functions: `object`, `obcost`, `obdamage`, `kar`,
  `sextext`, spell tables, encounter tables. ~1 day.
- **Phase 6c** — `driv.c` and `subs.c` text. ~1 day.
- **Phase 6d** — `speak.c` dialogue. ~2 days. Biggest single chunk.
- **Phase 6e** — `strid.c` combat text. ~1 day.
- **Phase 6f** — everything else. ~0.5 day.

Total: ~5-7 days of focused work. Much of it mechanical (copy string,
write record, replace with lookup call). An LLM-assisted pass can do the
bulk edit if you want, with humans reviewing diffs.

## 12. Follow-ups queued for after Phase 6d module splits

The `json5.[ch]` / `state.h` / per-module-header series (commits
`baf768f`, `c2bf863`, `168ff9c`) landed three structural cuts. Open
work after that:

### 12.1 Dockerfile + GitHub CI — **closed 2026-04-28 #6**

Body relocated to `port-plan-complete.md` "§12.1 closure" section
(commits `d35f663..405f398`). Briefly: multi-stage Dockerfile
(`debian:stable-slim` builder → tester → slim runtime), LLVM 22
pulled from `apt.llvm.org` so all three platforms (Linux Docker,
GitHub-CI Linux, macOS brew) run the same clang-format major,
`.github/workflows/ci.yml` with `linux` (gcc+clang matrix, full gate)
+ `format` (clang-format dry-run) + `macos` (scorecard — Apple-clang
ships without LSan) jobs. Two parallel landings shipped: `bb453c7`
adds `-D_DEFAULT_SOURCE` to `CPPFLAGS` so glibc's strict-ISO C99 mode
exposes the POSIX surface (`strdup`, `usleep`, `sigaction`, …) the
codebase uses everywhere; `d35f663` runs `clang-format -i` across 12
files (114+/103-) since the format gate had been silently broken
pre-§12.1.

### 12.2 Global-state teardown + allocation audit

Lookup tables (`glyph_tab`, `color_tab`, `color_tab_set`, `enums`,
`texts`, `flags_tab`, `pronoun_tab`, `priest_spells`,
`mage_spells`, `items`, `monsters`) and `world_tree` currently
live as file-scope globals in `res.c`. `struct ryl_state state`
is similarly a file-scope global in `ryl.c`. On process exit
nothing is freed — we rely on the OS to reap. That's workable
but the user wants:

1. **Instance-allocated state.** A `ryl_context` (or equivalent)
   owns the tables + world tree + `struct ryl_state`; callers
   acquire it at init and release it on shutdown. Tests and any
   future "reload world" path become tractable.
2. **Teardown hook.** An `atexit` handler (or explicit
   `ryl_shutdown()` called from `main`'s normal exit paths and
   from `cleanexit()`) frees every resource the context owns.
3. **Leak audit.** Every `malloc`/`realloc`/`strdup` site in
   `res.c`, `json5.c`, and `eval.c` gets paired with a free
   path. Verify under macOS `leaks -atExit -- ./rylween …` and
   `-fsanitize=address` on a CI leg.

Known suspect sites:
- `fold_texts` / `fold_flags` / `fold_enums` allocate names +
  value arrays, never freed.
- `node_free` only runs on `resolve_imports` replacement
  children; `world_tree` itself survives process lifetime.
- `compile_world_tree` replaces `N_STR` nodes in place with
  `N_PATH` — verify the replaced strings are freed.
- `pronoun_tab[sex][xx]` / `glyph_tab[g]` get `strdup`'d on
  every re-fold; currently only run once but any future reload
  leaks without freeing the prior value.

Gate this work the same way (replay byte-identical + check_world
ok), and add an ASan-enabled replay leg to CI once the hook is
in. **Commit C landed**: `tools/replay_asan.sh` + `make testasan`
run the fixture set under `$(BIN_ASAN)` and fail on
`AddressSanitizer` / `LeakSanitizer` markers. See §13.3.

### 12.3 Dynamic sizing from world.json5

**Closed end-to-end in 2026-04-28 #11..#13a.** The original spec
called for a `limits:` subtree carrying named caps,
heap-allocation off those caps, and an audit of every static
`[N]` size in `struct party` / `struct ryl_state`. After 2026-04-28
#13a every party-slot-indexed buffer is heap-allocated off
`res_party_slot_max()`; the map grid is heap-sized to
`res_map_rows_max()` × `res_map_cols_max()`; and the `fold_limits`
clamp on `limits.party_slot_max` is `>= 2` only — the data
layer is the single source of truth. Body (commit ladder
`467e4c6..bfd2265` + the §20.14 S10 map-dims piece `376f6bf`)
in `port-plan-complete.md` "§12.3 cap-flip queue end-to-end
closure".

### 12.4 Residual cleanup queued across sessions

Small items surfaced in passing during the Phase 6d series that
aren't worth their own §. Clear when the relevant tree is already
open:

- **`title-menu-exit.env` sidecar with `RYL_NO_DELAY=1`.** Should
  drop the replay suite from ~15s to ~3s. Needs a bless if any
  delay-dependent byte leaks into stdout; suspected clean but
  re-verify against the golden.
- **Ydarg-as-mission.** End-game boss gated on
  jazzyfoot/mastercrown/silverkey/bracelets should move from the
  bespoke `ydarg()` / `confrontation()` path into a regular
  mission with trigger + item-flag conditions. Blocked on the
  DSL evaluator learning mission triggers + item-flag paths.
- **`.git-blame-ignore-revs`.** Record `33ffc52` (the
  clang-format reformat) so `git blame -w --ignore-revs-file
  .git-blame-ignore-revs` skips it. Easy win, do next time
  `.git*` files are open.
- **Journal code lives in the wrong module.** §19.2 shrank
  this: the file-backed `journal()` / `clrjr()` helpers are
  gone, only `read_journal()` (viewer) and `journalf()`
  (single-line writer, thin wrapper over
  `party_journal_add`) still live in `driv.c`. Still fits
  better alongside guild / mail / trade in `interact.c`, but
  the move is now ~40 lines, not the BBS-era sprawl it used
  to be.
- **Makefile header dependency tracking (`-MMD -MP`).**
  Surfaced during §19.2.2: editing `state.h` and running
  `make build` only rebuilt the `.c` files whose source
  changed, so `snapshot.o` / `driv.o` / etc. linked against
  the old `struct party` layout and produced garbage
  snapshots. Current mitigation is `make clean` before any
  build that touches a widely-included header; proper fix is
  a one-liner switch to `-MMD -MP` plus `include $(OBJ:.o=.d)`
  so make tracks header deps automatically. Keep it tight: one
  Makefile edit, verify incremental rebuild on a `state.h`
  edit covers every dependent `.c`, re-bless not required.
- **Clangd cache drift.** Background `<new-diagnostics>`
  reminders sometimes reference symbols that no longer exist
  (e.g. `sextext`, `kar`, `ryl_seed_now`, `strid.c`). The
  source tree is clean; the ghost file lives in a stranded
  Claude-agent worktree at `.claude/worktrees/agent-a4fa40c7/`
  that clangd indexes. `make clean` doesn't touch it. Gate on
  `make` + `tools/replay.sh --all` + `tools/check_world.sh`;
  stale diagnostics are cosmetic. Optional: remove the
  stranded worktree once we're sure the Claude Code runtime
  isn't still pointing at it.
- **ANSI SGR escapes hardcoded in tx() strings.** `gfx.c`,
  `primitives.c`, and `subs.c` embed raw `"\x1b[3Nm"` /
  `"\x1b[4Nm"` sequences inline (e.g. `tx("\x1b[30m\x1b[ p\x1b[37m")`
  in `cleanexit`, `tx("\x1b[37m")` throughout). `res.c` now
  has a local `ANSI_FG_*` enum (Commit B of §12.2) — lift it
  to a shared header (or introduce escape-string defines like
  `ANSI_FG_CYAN_ESC "\x1b[36m"`) so every site reads in
  English. Keep the scope tight: one header, pure textual
  substitution, no behavior change. Gate byte-identical on
  replay.

## 18. ANSI art EAW / narrow-cell rendering — pending

**Status:** pending — raised 2026-04-23 after the §17-F smith
migration. The `data/ansi/*.ans` (full-screen art: title, screen,
death, etc.) and `data/tiles/*.ans` (in-game sprites blitted via
`blitgfx` → `render_at`) are authored with Unicode block-drawing
characters in the U+2580..U+259F range: `▀▄▌▐█░▒▓` plus the
geometric shapes `■▬`. Every one of those is Unicode EAW class "A"
(Ambiguous). Terminals differ on whether to render them as 1 cell
(narrow) or 2 cells (wide). The art is authored for 1-cell-wide
rendering (its Amiga lineage), so wide-cell terminals stretch each
glyph horizontally and the layout drifts diagonally (each row
ends up further right than the previous).

### 18.1 Who's affected

- **macOS Terminal.app** — renders EAW=A as 2 cells by default for
  CJK-locale parity. No UI toggle exposes narrow-cell mode.
- **iTerm2, Ghostty, Alacritty, Kitty, WezTerm** — default to
  1-cell rendering for EAW=A. Art looks clean. iTerm2 also
  exposes an explicit toggle (Profiles → Text → "Treat
  ambiguous-width characters as double-width" — off).
- **Linux consoles** (gnome-terminal, konsole, xterm) — typically
  1-cell by default; vary by locale.

### 18.2 Why the obvious fix doesn't work

A failed attempt on 2026-04-23: rewrite `render_at` and a new
`.ans` branch of `disptext` to anchor every visible glyph to its
authored column via `ESC[<col>G` (CHA — Cursor Horizontal
Absolute) emitted after each glyph write. The walk parses CSI
escapes (passes SGR through, syncs the tracker on
CUF/CUB/CHA/CUP) and UTF-8 glyphs.

The approach works for symmetric block chars — `█▓▒░`. A wide-
rendered `█` occupies two cells with visually-equivalent halves;
overwriting the right half with the next glyph crops the render
cleanly and alignment holds.

It fails for the asymmetric half-block family — `▐▌▀▄`. A
right-half block `▐` rendered at 2 cells has its visible mark in
the **right** cell of the pair; my fix overwrites that right cell
with the next glyph and leaves the (empty) left cell behind. The
reverse holds for `▌` (left-half, visible in the left cell of the
2-cell render — which survives; but its right-hand neighbour now
starts on what used to be the right half of ▌'s 2-cell render,
overlapping visually). The same asymmetry applies to `▀` (top-
half) and `▄` (bottom-half) — while their horizontal extent is
full, the 2-cell rendering puts the glyph in a specific half
that can't be cropped symmetrically.

Visible result: patches where a `▐`-heavy section looks shifted
leftward and overlaps neighbouring structure. The user
confirmed this with a screenshot showing the second purple row
of title.ans overlapping the axe shape. Reverted the whole
attempt and the 3x fixture rebless; tree is back to the pre-
attempt state.

The code can't recover per-glyph which-half-is-visible without
knowing how the terminal chose to scale each specific
codepoint. Unicode's EAW table doesn't expose that, and
terminals don't report it.

### 18.3 Viable paths

**A. Document and defer (current state).** Recommend iTerm2 /
Ghostty / Alacritty / Kitty / WezTerm in the README. Zero code
change. Clean result on any terminal that renders EAW=A as
narrow.

**B. Rewrite the art to symmetric-only blocks.** Drop the
asymmetric `▐▌▀▄` and redraw every `.ans` file with
`█░▒▓` + spaces. The symmetric-only art would render correctly
even under wide-cell terminals using approach 18.2. Big art job;
loses detail because asymmetric blocks provide the fine edges
that make the adventurer silhouettes readable. Estimate the
rewrite at a day of careful art work per `.ans` file, ~15 files.

**C. Use escape-sequence cell geometry.** There exists an
experimental `DECSWL` / `DECDWL` pair that sets per-line
single-vs-double-width — but that applies to the **terminal's**
cell width, not the rendered-char width, and isn't widely
supported. Not viable.

**D. Switch from Unicode blocks to Sixel / Kitty-graphics
protocol.** The art becomes bitmap images embedded in the
terminal stream; terminals that support the graphics protocol
render them pixel-accurately; those that don't fall back to
some approximation. Biggest scope of all — effectively
replacing the art engine, plus per-terminal compatibility
testing.

### 18.4 Recommendation

Keep path **A** (documented limitation) for now. The blast
radius of the art is a cosmetic issue only; the game plays
identically in all terminals. When someone wants to pick up
path B, budget it as an art project separate from porting
work — its gate isn't `make test` (byte-identical bytes don't
help; the render changes are authorial) but a side-by-side
visual diff against the pre-rewrite version in a narrow-cell
terminal.

### 18.5 Add to README (follow-up)

One paragraph in `README.md` under "Running": "The ANSI art
assumes 1-cell-wide rendering of Unicode block characters; use
iTerm2, Ghostty, Alacritty, Kitty, or WezTerm. macOS Terminal.app
renders block glyphs as 2 cells and the layout drifts. See
port-plan §18 for the details."

## 20. Code-as-data extraction queue (audit 2026-04-25)

Comprehensive audit of every src/*.c file outside the engine plumbing
(script.c, json5.c, snapshot.c, eval.c, primitives.c, ryl.c,
compat.c, gfx.c, io_tty.c, res.c are excluded — those are mechanism,
not content). Goal: list every place in the codebase where scenario
content, balance numbers, narrative text, or hardcoded layouts live
in C source instead of JSON5. Plus the related state-coupling debt:
direct `state.p[state.cpl].FIELD` access bypassing the `pa_*` /
`pm_*` / `inv_*` / `res_*` abstractions.

This section is a punch list, not a sequencing plan — the order
inside each bucket is the natural read order, not the work order.
Sequencing notes are at the bottom.

Related memory:
- `feedback_state_abstraction.md` — abstraction-routing rule
- `feedback_battle_data_extraction.md` — earlier sketch (folded
  into this section but still relevant for the "why")
- `project_full_game_gate.md` — the migration → snapshot → longplay
  → harness end goal that this work feeds into

### 20.1 Categories used

For each file we list offenders by category:

  A. **Scenario-as-switch / literal table** — switch arms where the
     body is "set field X = literal Y" (data, not control flow), or
     `static const T table[] = {...}` records baking scenario specs
     into C source.
  B. **Magic balance numbers** — numeric literals representing
     balance or scenario choices, not engine mechanics. Multipliers,
     thresholds, percentages, magic dimensions.
  C. **Direct `state.*` access** — sites that read or mutate
     `state.p[state.cpl].FIELD` (or `state.<global>`) directly
     instead of going through `pa_*` / `pm_*` / `inv_*` / `res_*`.
  D. **Hardcoded narration** — `tx("English text\r\n")` calls in C
     instead of `res_text("namespaced.id")` lookups.
  E. **Hardcoded paths** — `fopen("data/...")` / `disptext("data/...")`
     literals that should go through a `res_path()` helper.
  F. **Unwired DSL hooks** — `rdie(...)` / "not yet wired" markers
     for engine ops the DSL surface promises but the runtime
     refuses.

### 20.2 battle.c (890 lines, ~70 raw `state.*` sites)

A. Scenario tables masquerading as switches:
- battle.c:181-390 — `switch (oc)` item-use dispatch in combat.
  11 magic items (ids 100..111), each arm hardcodes inventory slot
  consumption, damage range, target effect, and narration text.
  Migration: items.json5 entries get an `effect: {...}` payload
  describing damage/target/text. Engine becomes generic `apply_item_effect`.
- battle.c:449-524 — `switch (s->effect)` spell effect dispatch
  (SE_FIRE, SE_CONFUSION, SE_DEATH, SE_HOLD, SE_ANTIMAGIC). DSL
  spell-effect path already exists (lines 436-447); finish it and
  retire the switch.
- battle.c:685-744 — `switch (ryl_random(0, 3))` monster-magic
  dispatch (frost/fire/hold/confuse). Belongs in monsters.json5
  per-monster spell pool with damage scaling expression.

B. Magic balance numbers:
- battle.c:65 — `((lv[1]+lv[2]+lv[3]+lv[4])/4) * 2` party-avg HP
  scaling
- battle.c:67 — `lv[1] * 2`, line 69 `lv[1] * 3` leader HP scaling
- battle.c:74-75 — `noc > 8` foe-count cap
- battle.c:80 — `ws += lv[1]` weapon-skill bonus
- battle.c:191, 209, 452 — spell damage multipliers (1-4x, 3-8x)
- battle.c:629-630 — victory rewards `(noc * h2/4 * ws/4)` exp,
  `(noc * h2/7 * ws/4)/4` gold
- battle.c:696 — `h2 / 5` monster magic damage divisor
- All extract to `data/world/balance/combat.json5` keyed by
  formula name (encounter_hp_party_factor, leader_hp_factor,
  foe_count_cap, weapon_skill_leader_bonus, etc.).

C. Worst raw-state offenders:
- battle.c:65-80 (encounter scaling, repeated `lv[1..4]` reads)
- battle.c:138-143 (combat loop, per-member name/dead/cls reads)
- battle.c:181-390 (item arms, per-slot inventory mutations)
- battle.c:542-600 (spell casting, target indexing)
- Already-pattern: lines 397-443 use `pm_cur(g)` + `pm_*` —
  generalise outward from there.

D. Hardcoded narration (sample, not exhaustive):
- battle.c:37 "ENCOUNTER!\r\n", line 97 "You face\r\n"
- battle.c:132 "Blood shall flow!\r\n"
- battle.c:141 "What now?\r\n", line 160 "Use what?\r\n"
- battle.c:383-386 Flute of Madness narration
- battle.c:687 "Frost Finger\r\n", line 710 "Cloud of Fire!\r\n",
  line 731 "Hold Creature!\r\n", line 738 "Confusion!\r\n"
- battle.c:763 "And hits for\r\n", line 777 "Blow misses!\r\n"
- All move to `data/world/scripts/battle.json5` text subtree
  (model: existing `battle.run.coward` / `battle.run.fail_away`
  res_text usage at lines 112-125).

E. None.

F. None.

### 20.3 dungeon.c (915 lines) — the worst offender

A. Scenario tables:
- dungeon.c:30-78 — **the canonical worst case.** `switch (flag)`
  mapping scenario flag (1..8) to which dungeon levels carry which
  encounter id. Pure scenario layout:
    flag 1 → Fire Shield Guardian (lvl[19]=20) + Fire Shield (lvl[20]=21)
    flag 2 → Thun riddles on lvl[4,8,12,18] + Bracelets (lvl[18]=25)
    flag 3 → Nolrim's Tomb chain (lvl[12..20])
    flag 4 → Master Crown (lvl[10]=29)
    flag 5 → Silver Sanctum (lvl[20]=30)
    flag 6 → Hobgoblin chain (9 level assignments)
    flag 7 → Orc Nest + Tammy's Prison (lvl[6,7])
    flag 8 → Erethor + Lute chain (lvl[3,7,12])
  Migration: `data/world/dungeons.json5` array of scenario
  descriptors, each with `levels: N` + `encounters: [{level, id}]`.
  Engine `dungeon(flag)` becomes a one-line lookup + dispatch.

- dungeon.c:117-803 — `switch (lvl[cl])` over encounter ids 0..38.
  Each case is a per-encounter scenario event:
    1: find an object (3 item-pool branches with id ranges
       1..14 / 36..42 / 39..46 / 100..111)
    2: find gold
    3..5: encounter
    6,7: nothing
    8: chest with `ryl_random(40, 339)` gold
    9: torches
    20: Fire Shield Guardian
    21: Fire Shield
    22..24: Thun riddles
    25: Bracelets of Thun
    26: Spectrum message
    27: Colour riddle
    28: Nolrim's Tomb + Reebar
    29: Master Crown
    30: Silver Sanctum
    31: Hobgoblin patrol
    32-34: Royal Guard chain
    35: Orc Nest
    36: Tammy's Prison
    37: Priests of Erethor
    38: Chamber of the Lute
  Migration: `data/world/scripts/dungeon.json5` with conditional
  branches (`if lvl == 20 then ...`). The encounter id space (0..38)
  itself becomes a `res_enum("dungeon_encounter", "fire_shield")`
  set, retiring the numeric soup.

B. Magic balance:
- dungeon.c:28 — `ryl_random(1, 9)` per-level encounter roll
- dungeon.c:163 — `ryl_random(40, 339)` chest gold range
- dungeon.c:210, 217 — trap-disarm thief chance (4-in-10, 2-in-10)
- dungeon.c:225-264 — trap damage `4 * lv[i]`, chest gold
  `ryl_random(0, 99) + 20 + exp[1]/500 + 300`
- dungeon.c:473-475 — colour-puzzle hardcoded answer binding

C. Raw-state hot spots:
- dungeon.c:166, 267, 277 — `state.p[cpl].gold` mutations
- dungeon.c:214, 253, 264 — `lv[i]` reads in trap scaling
- dungeon.c:675-795 — `name[i]` / `dead[i]` reads in narrative

D. Massive narration blocks (lines 97, 142, 192, 227-250, 286-793).
   Migrate alongside the level switch — separating text from logic
   is what the DSL exists for.

E. None.

F. None directly, but the level switch is a `play_script` migration
   target — once §20.7 lands, replace the dispatch wholesale.

### 20.4 driv.c (1704 lines)

A. None pure-scenario (the main-loop switch at 90-242 is dispatcher
   logic — keep). Menu text lines 93/112/126/140 should use res_text.

B. Magic numbers:
- driv.c:24 — `hours_rested = 14` initial
- driv.c:176 — `c(49, 6)` magic cursor coords (one of multiple)
- driv.c:294-303 — time-of-day boundaries (dawn 6-11, midday
  12-17, twilight 18-23, midnight 24-5)
- driv.c:305, 311 — hunger threshold `food < pl * 3`
- driv.c:341-342 — `ryl_random(5, 74)` x-bound, `ryl_random(5, 34)`
  y-bound for `position_locations()`
- driv.c:343-344 — tile blacklist literal chars `'w'/'m'/'0'/'1'/'2'`
- driv.c:334-339 — `15` location slots, `1..9` placed
- driv.c:365 — `for (i = 1; i < 41; i++)` map row count
- driv.c:401 — `time_left = 175` daily play-time budget
- All extract to `data/world/balance/upkeep.json5` and a new
  `data/world/map.json5` (or extend world.json5) with map
  dimensions / safe-zone bounds / passable-tile class.

C. Direct state access (177 sites — highest count of any file).
   Most are unavoidable in main-loop dispatch. Targets:
- driv.c:96-148 movement arms repeat the `!= 'm' && != 'w'` tile
  blacklist — extract `wld_is_passable(tile)` helper.
- driv.c:101, 115, 129, 143 `state.map[y][x]` reads — wrap with
  `wld_tile_at(x, y)`.

D. Mostly already res_text. Remaining:
- driv.c:1430-1654 — character stats display (showparty / show)
  has `tx("Stats for char: %d\r\n", g)`, `tx("Weapon Skill : %2d\r\n", ...)`,
  etc. Move to res_text fmt strings.
- driv.c:1444 — `tx("Give Character %d a name!\r\n", g)`.

E. Hardcoded paths (12+ sites):
- driv.c:51, 167, 178, 384, 412, 464, 470, 477, 483, 492, 498, 503,
  509 — `disptext("data/ansi/<name>.ans")`
- driv.c:358 — `fopen("data/map.dat", "rb")`
- Wrap with `res_ansi_path("name")` and `res_map_path()`.

F. None.

### 20.5 subs.c (1271 lines, 123 raw `state.*` sites)

A. Scenario-as-switch:
- subs.c:628-667 — `switch (oc)` potion-use dispatch (3 cases:
  ids 36 healing, 37 poison, 38 mana restore). Each arm has its
  own effect formula and narration. Migration: items.json5 gains
  a `category: 'potion'` typed class with declarative `effect`
  spec — `{ kind: 'heal', amount: { add: [15, { var: 'lv' }] } }`
  etc. Engine `apply_potion()` becomes one generic call. Same
  pattern naturally extends to scrolls, food, torches.

B. Balance numbers:
- subs.c:495-527 — temperature damage thresholds (temp 6-9 low,
  ≥10 very low; 5 hp damage per tick — note line 522 may have
  the very-low arm using same `5` as low arm, possible bug)
- subs.c:535-539 — rest restore `hours_rested = 14`, food cost
  `4 * pl`
- subs.c:547-568 — ambush `ryl_chance(19)`, random death pick
  `ryl_random(1, 4)`
- subs.c:634, 646 — potion heal/poison `15 + level`
- subs.c:705-706 — hunt success `ryl_chance(45)`, food gain
  `ryl_random(10, 19) + lv[1]`
- All extract to `data/world/balance/{rest,wilderness,hunt}.json5`.

C. Direct-state hotspots:
- subs.c:72-106 — sleep cycle: `hours_rested--`, per-member
  `hp[i]--` damage. Use `pm_add_hp(slot, delta)` once it exists.
- subs.c:244-297 — drop/swap weapon/armour: direct `wp[i] = 0`,
  `ar[i] = 0`, `inventory[x] = ...`. Use pm + inv abstractions.
- subs.c:396-412 — inventory item-use dispatch (food cap fix-up,
  torch consumption). Direct `inventory[d-64]`, `food = cap`.
- subs.c:432-440 — rest gate: `dead[1] && dead[2] && dead[3] && dead[4]`
  hardcoded 4-member loop (should be `pa_count_alive() == 0`).
- subs.c:506-524 — temperature damage loop: per-member hp
  decrement.

D. Narration (sample):
- subs.c:455 "Camp.", 490 "Post a guard?", 502 "Zzz...", 514
  "Horrible pain sears...", 556 "Ambush!!!", 572 "You wake up
  8 hours later..."
- Move to `data/world/scripts/{rest,hunt}.json5` text subtrees.

E. None.

F. None.

### 20.6 towne.c (389 lines)

A. towne.c:217 — `has_church = (state.twn == 1 || state.twn == 3)`
   hardcoded town ids. Migration: `data/world.json5` towns array
   gets a `has_church: true/false` per record. Same pattern for
   any other "only towns N and M have feature X" checks.

B. Magic numbers:
- towne.c:148 — `lv[1] < 5` minimum level for tax (parameterise)
- towne.c:151-152 — tax formula `(gold * tax) / 100` is fine
  (`tax` already res-driven), but the `100` divisor is implicit
  percent — could be res_path("balance.tax_divisor") for clarity.

C. Direct state: lines 174, 189, 194, 199-200 — `state.p[cpl].x/y`,
   `state.map[y][x]`, `state.twn` for town entry flow. The enter()
   migration depends on `pa_x()`, `pa_y()`, `wld_tile_at()`,
   `pa_first_level()` helpers landing first.

D. Mostly migrated.

E. None new.

F. None.

### 20.7 ydarg.c (554 lines)

A. ydarg.c:234-275 — `switch (d - 48)` four special-item arms
   (fireshield, mastercrown, jazzyfoot, bracelets). Each checks
   `state.p[state.cpl].FIELD == 0` and consumes the item.
   Migration: table of `[item_flag, gate_text, success_text]`
   tuples in `data/world/scripts/ydarg.json5`.

B. Quest-specific magic numbers throughout (item ids, room ids,
   gold rewards) — each will surface during the migration.

C. Heavy `state.p[cpl].FIELD` access (lines 105, 115, 142, 236-272).
   Same abstraction story as battle.c.

D. Mostly migrated to res_text.

E. None.

F. None.

### 20.8 speak.c (765 lines) — dialog DSL is mature, BUT

A. **Mission-stage tables baked as C arrays.** speak.c:355-399
   has `static const mission_stage stages_<role>[]` for merchant /
   ranger / fighter / wizard_robbed / priest / erethor_bard. Each
   record is a flag-condition + script-id + binding spec. Pure
   scenario data living in C source. Migration: `data/world/missions.json5`
   (or new `data/world/speak_missions.json5`) gains an array of
   stage records keyed by NPC role. The struct is well-formed
   enough for a near-mechanical lift.

A.2 **jazzy_ogre_trader inline flow.** speak.c:720-758 —
   hardcoded inventory key range `'A'..'P'` (line 736), magic
   item-id threshold `oc < 35` (line 738), magic location index
   `state.dx[7]` / `state.dy[7]` for "Beast Cave" (lines 746-747),
   direct `state.p[cpl].inventory[d-64]` mutation (line 757),
   hardcoded English in `journalf("The 'Beast Cave' is at %dx%d.", ...)`
   (line 755). Should be a `speak.jazzy_ogre.*` script with a
   reward op (already exists in DSL).

A.3 speak.c outer dispatch (lines 179-259) hardcodes keypress
   chars (`'B'`, `'R'`, `'C'`, `'Q'`, `'P'`) — the dispatch shape
   itself is fine but should match the menu_branch shape used
   elsewhere.

B. speak.c:248 — lodge price `50 * lv[1]` (50 gold per leader
   level). Per-NPC pricing constants embedded throughout. Move
   to `data/world.json5` NPC role records.

C. Heavy `state.p[cpl].lv[1]` and `gold` reads in pricing logic
   (lines 121-145, 248-265).

D. Mostly migrated; speak.c:187 chitchat uses res_text.

E. None.

F. None in speak.c itself.

### 20.9 interact.c (59 lines) — entirely an offender

The whole file is `check_terrain(char old, char new)`:
- interact.c:5-59 — `switch (new)` over terrain chars
  (`'s'/'1'/'0'/'f'/'d'/'t'/'g'/'2'`), each arm writes 1-6 lines
  of hardcoded English narration.
- interact.c:9 — `ryl_chance(9)` 9% trigger rate (magic).
- interact.c:6 — only matches on transitions where `old != new`,
  but doesn't actually use `old` for narration shape.

**Extension idea (user-flagged):** the data model should support
`old → new` transition arcs, not just per-new descriptions.
Entering a desert from grasslands should be able to read
differently than entering from a forest. Schema: a sparse
transition matrix in `data/world/scripts/terrain.json5` —
default per-terrain narration plus optional `transitions: { from:
{ to: ... } }` overrides.

Migration: `data/world/scripts/terrain.json5`, with the trigger
rate as a `chance` field. Engine becomes a thin
`check_terrain(old, new)` that dispatches into the script.

### 20.10 missions.c (292 lines)

A. **Already gold-standard.** missions.c lines 78-143 use
   `mission_triggers[]` table + DSL dispatch. Use this as the
   reference shape for everything else in this section.

   But: missions.c:148 still has `switch (t->kind)` and 167
   `switch (b->kind)` that dispatch to per-trigger handlers —
   those are engine logic (interpreter), so keep.

B. Per-mission balance bits already in data.

C. Lower coupling than peer files but still some `state.*` —
   sweep on contact.

D. Mostly migrated.

E. None.

F. None directly. (See §20.12 for the unwired hooks in eval.c.)

### 20.11 party.c, inventory.c — abstraction layers

These are the abstraction layer the rest of the code should be
calling. No scenario data baked in. Used as the reference
pattern for `pa_*` / `inv_*` callers.

One nit: inventory.c:67-84 `tag_to_which()` hardcodes a small
enum map (fireshield → PS_FIRESHIELD etc.). Acceptable as a
bridge today; should auto-generate from items.json5 special
tags eventually.

### 20.12 Unwired DSL hooks (eval.c)

- eval.c:1267-1268 — `spawn_encounter`: rdie "engine hook not
  wired up". The DSL surface promises this op (eval.c:436, 469
  parser-side) but the runtime refuses.
- eval.c:1270-1271 — `call`: rdie "lib subtree not yet
  consulted". Library-routine dispatch, also promised but unwired.

These are honest TODOs in the runtime. Wiring them likely
unblocks several mission scripts that today have to fall back
to inline C.

### 20.13 Cross-file patterns

Recurrent literals worth a single-source-of-truth helper:

- `state.p[state.cpl].lv[1]` (leader level): 70+ sites across
  battle / dungeon / subs / speak / ydarg. Helper:
  `pa_first_level()` (party.h).
- `state.p[state.cpl].pl` (alive count): 15+ sites. Helper:
  `pa_alive_count()`.
- `state.p[state.cpl].gold`: 20+ sites. Helpers: `pa_gold()`,
  `pa_gold_add(delta)`.
- Food consumption `* state.p[cpl].pl`: subs.c:39, 305, 311, 536,
  539. Parameterise as `upkeep.food_cost_per_member`.
- Map-passable test `tile != 'm' && tile != 'w'`: driv.c:101,
  115, 129, 143; subs.c (terrain check). Helper:
  `wld_is_passable(tile)`.
- `state.p[cpl].x/y`: many sites. Helpers: `pa_x()`, `pa_y()`.
- `state.map[y][x]`: many sites. Helper: `wld_tile_at(x, y)`.
- Per-member equipment access (`wp[i]` / `ar[i]`): many sites.
  Helpers: `pm_has_weapon(slot)`, `pm_has_armour(slot)`.
- `ryl_chance(N)` percentage gates: 50+ sites total. Already
  data-driven where convenient (wilderness / missions); the
  remaining hardcodes should move to balance.json5 per-context.

### 20.14 Sequencing — TINY / SMALL / LARGE buckets

**TINY (single PR, no prerequisites):**

- T1. interact.c → `data/world/scripts/terrain.json5`. Whole file
  is 59 lines of pure data. Adds the optional `transitions: { from:
  { to: ... } }` shape.
- T2. towne.c:217 `has_church` → world.json5 town record field.
- T3. driv.c hardcoded ANSI paths (12+ sites) → `res_ansi_path()`
  helper.
- T4. driv.c `fopen("data/map.dat", "rb")` → `res_map_path()`.
- T5. inventory.c special-tag bridge (`tag_to_which`) — auto-gen
  hook, low priority.

**SMALL (1-2 sessions, may need a small abstraction first):**

- S1. **Add the missing `pa_*` / `pm_*` / `wld_*` helpers**:
  `pa_x`, `pa_y`, `pa_gold`, `pa_first_level`, `pa_alive_count`,
  `pm_has_weapon`, `pm_has_armour`, `pm_add_hp`, `wld_tile_at`,
  `wld_is_passable`. Pure-add commit, no behaviour change. Gates
  green via existing fixtures.
- S2. **State-coupling sweep** — retrofit battle.c, subs.c,
  dungeon.c, ydarg.c to use the S1 helpers instead of raw
  state.*. One commit per file. Byte-identical.
- S3. **enter() migration** — once S1 lands, the towne.c::enter()
  migration the user originally asked for becomes clean.
- S4. **Potion category in items.json5** — declarative effect
  spec for ids 36/37/38; replace subs.c:628-667 switch with
  generic `apply_potion`. Pattern then extends to scrolls / food.
- S5. **Battle balance constants** → combat.json5 (encounter
  HP scaling, foe-count cap, ws bonus, victory rewards).
- S6. **Wilderness / rest balance constants** (subs.c
  thresholds, ambush %, hunt %) → balance subtree.
- S7. **Wire `spawn_encounter` and `call` DSL ops** (eval.c:1267-1271).
  Each is its own commit; both unblock currently-inline mission
  flows.
- S8. **speak.c stages_*[] tables** → speak_missions.json5. The
  struct shape is already well-formed; near-mechanical lift.
- S9. **speak.c jazzy_ogre_trader flow** → speak.jazzy_ogre.*
  script with reward op.
- S10. **Map dimensions / passable tile class** → world.json5
  map subtree. **DONE 2026-04-28 #13a** (commit `376f6bf`).
  state.map flips from `char (*map)[MAP_COLS_HARD_MAX]` to a
  flat `char *map` 1D buffer of `res_map_rows_max()` ×
  `res_map_cols_max()` bytes; new `map.rows_max` / `map.cols_max`
  knobs in world.json5; wld.c is the sole consumer that knows
  the stride. Body in `port-plan-complete.md` "§12.3 cap-flip
  queue end-to-end closure".
- S11. **Lift battle reward + encounter formulas into `formulas:`
  subtree** (follow-up to S5). **DONE earlier in §22 / §20.14
  sweep** — `res_formula_int_n(name, bindings, n)` (res.h:220) is
  the multi-binding evaluator; battle.c calls it for all four
  reward / encounter formulas (`encounter_h1_scaled`,
  `encounter_h2_scaled`, `reward_xp_per_survivor`,
  `reward_gold_party`); formula bodies use multi-`{var:N}`
  arg lists. Body in `port-plan-complete.md` "§20.14 S10 / S11
  closure".

**LARGE (multi-session epic):**

- L1. **dungeon.c:30-78 special-encounter switch** →
  dungeons.json5 scenario descriptor array. Engine `dungeon(flag)`
  becomes a lookup + dispatch. **DONE 2026-04-25** (commit
  1962bd9 + dungeons.scenarios subtree; flag-int signature replaced
  by symbolic scenario name).
- L2. **dungeon.c:117-803 level-event switch** → dungeon.json5
  scripts with conditional branches. ~700 LOC of C → ~50 lines
  of JSON5. The mother of all extractions. Prerequisite: L1
  done, plus encounter-id enum (`res_enum("dungeon_encounter", ...)`).
  **DONE 2026-04-25** (commits 28f4d40..fc599b3, 7 steps; the
  switch is gone, dungeon.c is 870 → 171 lines, all 25 cases live
  in data/world/scripts/dungeon.json5).
- L3. **battle.c item-effect switch** (181-390, 11 magic items)
  → items.json5 effect specs + generic `apply_item_effect`.
  Prerequisite: DSL effect-syntax (damage formula, target
  selector, text template) per effect kind.
- L4. **Spell DSL completion** — finish migrating battle.c:449-524
  spell switch into the DSL spell-effect path that already exists
  at lines 436-447. Retire the switch.
- L5. **Monster magic table** (battle.c:685-744) →
  monsters.json5 per-monster spell pool with damage scaling
  expression.
- L6. **Battle narration sweep** → `data/world/scripts/battle.json5`
  text subtree (~30 hardcoded `tx()` strings).
- L7. **Camp / hunt / lodge / tavern flow lifts** — convert
  inline subs.c / towne.c flows into `data/world/scripts/{rest,
  hunt,lodge,tavern}.json5`. Last of these is the longplay-
  fixture prerequisite (per project_full_game_gate.md).
  **DONE 2026-04-25** (commits e355ef8 + d283423 + ecfd183 +
  03d2eff, 4 steps): every inline-narration flow named in L7's
  scope is now data-driven. See §20.18 for the full closure
  write-up; key landings:
    - `subs.c hunt()` 70 → 18 lines → `data/world/scripts/hunt.json5`
      + `data/world/texts/hunt.json5`.
    - `subs.c camp()` 164 → 35 lines → `data/world/scripts/rest.json5`
      + `data/world/texts/rest.json5`.
    - `speak.c` bargirl Q→L arm 35 → 13 lines → `data/world/scripts/lodge.json5`
      (`lodge.bargirl`); non-bargirl per-job static lodge text
      stays on `txi('speak.lodge.j%d', npc_job)`.
    - Tavern was already migrated in §17-F (towne.json5).
  Step 1 extended the DSL with two read-side expr forms
  (`{hours_rested:0}` / `{time_left:0}`), one mutate target
  (`{time_left:0}` → `pa_set_time_left`), two `engine_int`
  helpers (`pa_alive_count`, `pa_first_level`), and one
  `engine_call` (`advance_hour`).

### 20.15 Prerequisites & ordering

The cleanest dependency order is:

  1. T1, T2, T3, T4 (TINY, parallel) — buy quick clarity wins.
  2. S1 (helpers) — unblocks every later state-coupling reduction.
  3. S2 (sweep) and S7 (unwired hooks) in parallel — both improve
     the platform before the heavy lifts.
  4. S3 (enter migration) and S4 (potion category) — first uses
     of the new helpers in real migrations.
  5. S5, S6, S8, S9, S10 — incremental data extractions.
  6. L1 → L2 (dungeon stack) — sequential.
  7. L3 → L4 → L5 (combat data stack) — sequential.
  8. L6, L7 — independent of the others; can land any time after
     S1-S2.

The full-game gate (project_full_game_gate.md) wants L7's flow
lifts done before the longplay fixture, so any session focused
on that goal should pick L7 items.

### 20.16 Coverage + validator additions (2026-04-25, post-L2)

Two follow-ups from the L2-closing continuation prompt landed in a
short coverage / hygiene session:

- **wilderness-cave-descent replay fixture** (commit 06b7ec3).
  Closes the L1.5 / L2 coverage gap noted in the previous session
  marker — until now no fixture under tools/replay/ entered a cave.
  Walks the seed-42 entropy stream until the random cave-found
  prompt fires, enters via E, takes the level-1 generic gold
  encounter (193gp), descends to level 2 (consumes one torch),
  ascends back, ascends out of the cavern, exits on EOF. Exercises
  subs.c::random_wilderness cave dispatch + dungeon() level driver
  + (D)/(A) menu + the random-fill DSL scripts (gold here, with
  the emit + mutate + wait_input shape). 17 → 18 fixtures, byte-
  identical, deterministic across re-runs.

- **Fold-time script-body symbol validator** (commit 55d0b1f).
  New `fold_scripts` walks `data/world/scripts/*` at world load
  and resolves every symbol-typed field through the matching
  res_* table. Covers encounter.foe / encounter.tier /
  dungeon.scenario / speak.building / reward.items[] / set_flag
  tag+value / engine_call / engine_int / engine_bind.fn / enum /
  item / pic / flag. Diagnostic style matches fold_dungeons (`res:
  <path>:<line>: scripts.<breadcrumb>: unknown ...`). Two new
  helpers (`res_monster_tier_exists`, `res_dungeon_scenario_exists`)
  and three script-introspection wrappers
  (`script_engine_{call,int,bind}_argc`) so the walker doesn't
  duplicate the engine_tbl lists. Required publishing
  `world_tree` to res.c's static slot before the walk because the
  existing res_*_id helpers read off it; the path-compile pass
  that runs after still mutates `root` in place safely.

### 20.17 Single-ownership status (snapshot 2026-04-25 — post-L7)

Per `feedback_state_abstraction.md`, the long-running goal is one
owner per `state` field — every read/write should route through
`pa_*` / `pm_*` / `inv_*` / `wld_*` / `res_*` rather than indexing
`state.p[state.cpl].*` directly. Counts as of HEAD (post-L7):

  | file        | state.* | helpers (pa/pm/inv/wld/res) | notes                                  |
  |-------------|---------|-----------------------------|----------------------------------------|
  | battle.c    | 1       | 168                         | effectively single-ownership clean     |
  | subs.c      | 24      | 203                         | residual is map / global_name / hi-score |
  | driv.c      | 167     | 134                         | main-loop dispatch — H2 target         |
  | speak.c     | 6       | 53                          | residual is npc[i] direct-walk in speak() |
  | dungeon.c   | 2       | 9                           | DONE (residual = 2 sentinel reads)     |
  | towne.c     | 14      | 34                          | helpers lead                           |
  | missions.c  | 13      | 9                           | residual is mission stage table reads  |
  | ydarg.c     | 13      | 77                          | helpers dominate                       |
  | interact.c  | 0       | (file is 1 trivial wrapper) | DONE                                   |
  | script.c    | 17      | 162                         | DSL bridge layer (acceptable)          |

In every file except driv.c, helpers now outnumber raw `state.*`
accesses. battle.c / dungeon.c / interact.c are essentially fully
migrated; subs.c, speak.c, ydarg.c are predominantly through-helper.
driv.c is the only single-ownership debt of meaningful size — its
167 hits are mostly main-loop dispatch (movement arms reading
`state.last_terrain` / `state.p[cpl].time_left`). The real
reduction wedge there is `wld_*` / `pa_*` helpers for the movement
bookkeeping, not a DSL migration. driv.c retrofit is the **H2**
task — queued in the in-flight session continuation prompt; it
can land independently of any data extraction.

The script.c count is the engine bridge — `ie_*` / `sb_*` / `eng_*`
wrappers that read state on the DSL's behalf. Per the rule,
**these should themselves call `pa_*`/`pm_*` rather than read
state directly**; auditing the existing wrappers for compliance
is the next hygiene pass after the LARGE bucket.

(Counts above are first-cut greps — `[^a-zA-Z_]state\.` for the
state column and `(pa_|pm_|inv_|wld_|res_)[a-z_]+\(` for helpers.
Comments and string literals can inflate either side; the trend,
not the absolute count, is what matters.)

### 20.18 L7 closure write-up (2026-04-25)

L7 closed in four commits over a single session
(`e355ef8` → `d283423` → `ecfd183` → `03d2eff`). The session
exposed enough generalisable detail to warrant capturing as a
playbook for future flow lifts.

**DSL surface added in step 1** (`e355ef8`, src/script.c +30 LOC):

  - read-side expr forms `{hours_rested:0}` / `{time_left:0}`
    (mirror the existing mutate-target shapes — same convention
    `gold` / `torches` / `food` / `pl` / `hour_of_day` already
    use). Bare-int form means the value-arm `:0` is a placeholder.
  - mutate target `{time_left:0}` (LV_TIME_LEFT) — paired with
    `pa_set_time_left`. Closes the read+write pair so hunt's
    `pa_add_time_left(-cost)` ports cleanly to a sub op.
  - `engine_int pa_alive_count` (0-arg) — for the camp wipe-out
    early-out (`pa_alive_count() == 0` / `pa_count_alive()` was
    a pre-L7 follow-up note).
  - `engine_int pa_first_level` (0-arg) — for the hunt food-gain
    formula and the bargirl per-leader-level fee (50 * lv[1]).
  - `engine_call advance_hour` (0-arg) — camp's 8-iteration
    clock-bump loop.

  Pattern: any time a script needs a piece of party state the
  DSL can't see, the cheapest extension is one entry in the
  matching registry. Read-only counters → `engine_int`;
  read+write counters → expr form + LV_* + read_lv/write_lv;
  void-return engine surfaces → `engine_call`.

**C-wrapper convention (carries forward)**. The four wrappers
follow the same shape — towne.c smith / tavern / inn / merchant
established it in §17-F, and L7 reaffirms it for the subs.c side.

  ```c
  void <flow>() {
      char buf1[16], buf2[16], ...;
      snprintf(buf1, sizeof buf1, "%d", res_int_path("balance.<flow>.<key1>"));
      snprintf(buf2, sizeof buf2, "%d", res_int_path("balance.<flow>.<key2>"));
      ...
      play_script("<script_id>", (ryl_bind[]){
          {"<key1>", buf1},
          {"<key2>", buf2},
          ...
          {0},
      });
  }
  ```

  The C wrapper owns: balance constant resolution, environment-
  coupled values (opening hours via `res_building_hours`),
  any pre-formatted strings the script's text placeholders need.
  The DSL owns: every `tx*()` / `clw()` / `ryl_pause()` /
  `ryl_delay()`, all keypress dispatch, all party-state mutation.
  Splitting at this seam keeps `play_script` calls trivially
  reviewable and survives DSL evolution without churn.

**Cosmetic deltas (acceptable; documented in commit messages)**.
Three patterns came up across the four steps:

  - `tx("  %s", res_text("common.yes"))` (two-space indent) → bare
    `emit common.yes` in the DSL (no indent). speak.c's bargirl
    flow had this; matches inn / smith migrations from §17-F.
  - `tx("Very low temperature.\r\n")` printed without a leading
    `clw()` so it stacks under prior text in camp's temperature
    check; preserved literally — the rest.json5 script omits the
    `engine_call clw` in the `temp_very_low` arm.
  - DSL `emit` of an array-of-strings text record adds a final
    CRLF after the last line (the array→stream convention); some
    C tx() sites historically did not. Indistinguishable on the
    rendered terminal but visible in byte-stream diffs.

  None of L7's flows have replay fixtures today, so byte-identity
  isn't tested. Authoring fixtures (§20.19) will surface any of
  these deltas as expected diffs.

### 20.19 Fixture queue — replay coverage for L7 flows

Three new replay fixtures lock down byte-identity for the L7
surface. All three landed in the post-L7 fixture-author session
and fold into the existing `make check` aggregate; total replay
fixture count moves 18 → 21.

  1. **`wilderness-hunt`** [DONE] — exercises subs.c hunt() →
     hunt.json5. Four-Ranger party, H from spawn (12,9), member 1
     picked. Seed-42 lands on success → "20 food rations" trap
     (random[10,19]+pa_first_level=1 = 20). Validates: who-loop
     digit_range, pa_is_class enum lookup, time_left mutate sub,
     food mutate add, pa_first_level engine_int. The `-fail`
     variant is a follow-up — would need a different seed or
     enough RNG burn to push `ryl_chance(45)` past success.

  2. **`wilderness-camp`** [DONE] — exercises subs.c camp() →
     rest.json5. Burns 14 X+N (cancelled-quit) cycles to drain
     hours_rested 14→0 without firing random_wilderness
     encounters (movement keys would). C N to enter camp without
     guard. Seed-42 lands on temperature [6,10) → "Low
     Temperature" + "Party loses 5 health"; ambush_pct(19%) roll
     misses → well_rested wake-up; heal+15 clamps every member
     back to full hp from the temp damage. Validates: every L7
     DSL extension at once — hours_rested expr, advance_hour
     engine_call, pa_alive_count engine_int, food clamp:min,
     hp clamp:max, mutate set on hours_rested + hour_of_day. The
     `-ambush` and `-guard` variants are follow-ups; this one
     covers the largest single-fixture DSL surface today.

  3. **`towne-thord-tavern-bargirl-lodge`** [DONE] — exercises
     the speak.c bargirl Q→L arm → lodge.bargirl. Important:
     reach is via inn() chitchat (`I N N N C` then NPC letter),
     NOT tavern S — the tavern's S key still calls `speak(building='inn')`
     under the legacy bld=1/inn vs bld=2/tavern semantics
     (script.c:1287 documents this preserved Amiga-era cross-
     listing). The bargirl Deanna only shows in the inn's
     chitchat list. Letter E (after 3 random NPCs + Niklas at
     building=tavern). Q L Y → 50 gold deducted (gold 1000→950),
     each member -1 hp via the `gt: hp > 1` guard. Validates:
     gold mutate sub, member-field hp mutate sub with the gt
     guard, for_member loop. The `-broke` variant is a follow-up;
     hardest part is dropping gold below 50 (RYL_STARTING_GOLD
     env caps at ≥1000, so the variant needs ≥20 lodge-pay
     iterations or a merchant-spend prelude).

  Pre-flight wiring fix landed alongside this session: L7 steps
  2/3/4 added the script files (data/world/scripts/{hunt,rest,
  lodge}.json5) and texts (data/world/texts/{hunt,rest}.json5)
  but never added the matching `$import` lines to world.json5's
  scripts: and texts: subtrees. play_script("hunt"|"rest"|
  "lodge.bargirl") would die at lookup. Fix: import all three
  scripts and the two text shards; unwrap the `{ hunt: [...] }`
  / `{ rest: [...] }` outer keys so the imports flatten to ARR
  (play_script("hunt") needs scripts.hunt to BE the array, not
  contain a `.hunt` key). lodge.json5 keeps the outer wrapper —
  scripts.lodge needs to be an OBJ for the lodge.bargirl path.

### 20.20 DSL extensions discovered as nice-to-haves during L7

Items that would have made L7 cheaper but weren't worth doing
inline. None block subsequent migrations — they're ergonomics
investments.

  1. **`engine_int` class-id symbol args** (carried over from
     pre-L7 follow-up list). Today `pa_is_class` takes two ints,
     so the script writes
     `args: [{var:'who'}, {enum:['pc_class','Ranger']}]` to
     resolve the class-id symbolically. A nicer surface would
     accept `args: [{var:'who'}, 'Ranger']` directly and look
     the second positional up via `res_enum_id('pc_class', ...)`
     when the registry sig says it's a class id. Saves the
     `enum:` boilerplate at every class-gate callsite.

  2. **`txi`-style format-id-by-int op** — DONE 2026-04-25.
     `{ emit_indexed: { fmt: 'speak.lodge.j{}', value: <expr> } }`
     lives in src/script.c as `do_emit_indexed`. Strict variant —
     die_at if the resolved id has no text record (catches the
     silent-empty-line failure mode). Proving callsite is
     `lodge.nonbargirl` in data/world/scripts/lodge.json5; speak.c
     L-arm now dispatches between `lodge.bargirl` and
     `lodge.nonbargirl` based on `npc_job(talker) == Bargirl`.
     Remaining txi callsites (`speak.people.j%d`, `speak.magic.j%d`,
     `speak.chit.reply.<subj>.j%d`) can lift via the same pattern
     when the speak.c residual sweep happens — chitchat has two
     int axes, so it'd need a two-token fmt or a string-template
     extension; defer until a clear win shows up.

  3. **`{path: 'dotted.path'}` expr — DSL-side `res_int_path`
     reader**. L7's camp wrapper marshals 11 balance constants
     into 11 int bindings; hunt does 4. Inlining the lookup
     into the DSL via a `path:` expr form would let the script
     read e.g. `{ path: 'balance.rest.health_gain' }` directly
     and the C wrapper drops to a single-line `play_script(...)`
     call. Footgun: `res_int_path` returns 0 on missing paths
     today, so a typo silently breaks the flow. A strict variant
     (`die_at` on missing) plus a fold-time symbol resolver
     entry (already mostly in `fold_scripts`) covers this. Worth
     doing before the next balance-heavy migration; defer until
     a flow with 5+ tunables shows up again.

  4. **Default-`{0}`-binding for speak op town arg**. Carried
     over from the pre-L7 follow-up list — `{ speak: { building:
     'tavern', town: { twn: 0 } } }` is verbose for "use the
     party's current town". Either default `town:` to `state.twn`
     when omitted, or accept a literal `town: 'current'` symbol.
     Cosmetic; one callsite in towne.json5 (inn → C chitchat)
     and zero in L7 itself.

## 21. Reentrancy / thread-safety plan (audit 2026-04-25)

The user asked: can the gate tests run in different threads or
parallel processes? And, more strategically, what would it take
to make the engine reentrant — pass a context around, drop the
global state — so a single binary could host multiple independent
game instances?

### 21.1 Parallel-process tests — already done

`tools/replay.sh --all` forks one `rylween` subprocess per
fixture, waits for all of them, and prints buffered output in
script order so the summary is deterministic. Each fixture has
its own tmp `HOME`, env sidecar, stdin, RYL_SNAPSHOT path, and
stdout buffer. There is nothing to change: 22 fixtures already
run on whatever cores the OS hands out.

`make check` invokes `tools/replay.sh --all` and `tools/check_world.sh`
back-to-back. The fan-out lives inside the replay script; the
build itself is single-threaded `make` because the source set
is tiny enough that parallel cc isn't a wallclock win.

### 21.2 In-process threading — what blocks it today

Several pieces of process-singleton state would have to be lifted
into a per-instance context. Audited 2026-04-25:

  - **Game state** — `struct ryl_state state;` defined in
    `src/ryl.c` and read/written by every gameplay file. The big
    target: every `state.*` and helper (`pa_*` / `pm_*` / `inv_*`)
    becomes ctx-aware.

  - **RNG** — `static uint64_t rng_state` in `compat.c` (XorShift64*).
    Seeded at startup from `RYL_FAKE_SEED` or `time^pid`. Every
    `ryl_random` / `ryl_chance` advances it. Must be per-context;
    otherwise two threads draining the same generator would
    interleave non-deterministically and break replay.

  - **World tree** — `static node *world_tree` in `res.c`. The
    JSON5 AST that backs `res_text`, `res_int_path`, `res_enum_id`,
    `fold_scripts`, etc. *Read-only after `res_load_world`*, so
    in principle it can be shared across threads — but every
    dependent table (items, monsters, spells, glyphs, towns,
    flags, mission tables, enums) is also a `static` in res.c
    and they're all read-only post-fold. **Shareable as a unit**
    if we encode "world is immutable after load" as a discipline.

  - **Lazy env caches** — `compat.c` has function-statics for
    `RYL_NO_DELAY`, `RYL_FAKE_EPOCH`, and the resolved
    `$HOME/.rylween` path. Each one-shot at first call. Either
    move into ctx or accept that env-var reads are
    process-scoped (i.e., all threads share the same
    RYL_NO_DELAY setting); the latter is fine for testing as
    long as fixtures never need different env values
    concurrently, which today they always do (FAKE_SEED
    differs per fixture).

  - **I/O backend** — `io_backend *io = NULL;` in `io_tty.c`,
    plus `static struct termios saved_termios` and
    `volatile sig_atomic_t io_winch_pending`. The terminal is a
    process-singleton resource — can't have two threads driving
    raw mode on the same tty. For threaded tests, `io` must be
    per-context and configured for in-memory or
    pipe-backed I/O; the `tty_backend` would be a single-thread
    interactive-only path.

  - **atexit handlers** — three of them: `res_shutdown`,
    `tty_restore`, `snapshot_write`. atexit is process-global
    and runs in LIFO order at process exit. None of these are
    per-context-friendly. For threaded mode, replace with
    explicit `ryl_ctx_free(ctx)` lifecycle and skip atexit.

  - **SIGWINCH** — single signal handler shared by the process,
    sets `io_winch_pending`. Each context would need its own
    pending flag; but a process can only deliver a signal to one
    "place," so the handler dispatches to per-thread state via
    something like `pthread_self()` or a TLS slot. Practically:
    threaded engine instances probably don't care about
    SIGWINCH — only the interactive single-threaded session
    does. Make the handler conditional on "this ctx is the
    interactive one."

  - **stdio** — `printf` / `fprintf(stderr)` calls live in
    `primitives.c` (heavy), `res.c`, `snapshot.c`, `json5.c`.
    For threaded mode, every direct stdio call has to route
    through `ctx->io` instead. A grep + audit would find them;
    the non-error path is mostly already through tx/tx_fmt/clw,
    which themselves call into the io_backend.

  - **getenv reads at startup** — `RYL_FAKE_SEED`,
    `RYL_FAKE_EPOCH`, `RYL_NO_DELAY`, `RYL_SNAPSHOT`,
    `RYL_STARTING_GOLD`, `RYL_PARTY_MEMBER_MAX`,
    `RYL_REPLAY_RUMOUR`, `HOME`. Process-global today. Per-ctx
    config would override these via a `ryl_ctx_config` struct
    populated by the harness.

### 21.3 Target shape — `ryl_ctx`

  ```c
  struct ryl_ctx {
      struct ryl_state state;       /* the current state struct */
      uint64_t rng_state;
      io_backend *io;               /* may be tty-shared, in-mem, etc. */
      const node *world;            /* shared, read-only post-fold */

      /* config replacing env-var reads */
      uint64_t fake_seed;
      time_t   fake_epoch;
      int      no_delay;
      const char *snapshot_path;
      const char *home_dir;

      /* per-instance siblings of res.c's tables, IF we want each
         ctx to reload world (probably not — share it). Otherwise
         skip; world is immutable after load. */
  };
  ```

  Every gameplay function gains a `ryl_ctx *ctx` first parameter.
  Helpers (`pa_*`, `pm_*`, `inv_*`, `wld_*`, `res_*`) read from
  `ctx->state` / `ctx->world` instead of file-scope globals.
  `ryl_random(ctx, lo, hi)` advances `ctx->rng_state`.
  Engine bindings registered in `script.c` thread the ctx through
  to the C surfaces they call.

### 21.4 Sequencing — what to do first

  1. **Lift `struct ryl_state state` into ctx.** This is the
     biggest mechanical change but also the most contained: every
     `state.*` becomes `ctx->state.*` (or, better, the existing
     `pa_*`/`pm_*` helpers grow a `ctx` arg and stay the only
     readers). `feedback_state_abstraction.md` is the in-flight
     hygiene push for exactly this; finishing that work is the
     precondition for ctx-ifying the engine.

  2. **Lift the RNG.** Small, mechanical, deterministic. Adds a
     ctx arg to `ryl_random` / `ryl_chance` / `ryl_seed`. Once
     done, two ctxs can run pure-data scripts independently
     without RNG cross-talk.

  3. **Lift the env-var lazy caches.** Replace function-statics
     in `compat.c` with `ctx->no_delay` / `ctx->fake_epoch` /
     `ctx->home_dir`. Cheap, isolating change.

  4. **Decide world sharing.** Either keep `static` tables in
     res.c and document "world is process-shared, read-only
     post-fold" (cheapest), or duplicate every res.c table into
     ctx and reload per ctx (expensive, only matters if we ever
     want two ctxs running different world data — unlikely).
     Recommend: keep shared. Lock `world_tree` to read-only
     after `res_load_world` and assert no late writers exist.

  5. **Lift the I/O backend.** `io_backend *` becomes `ctx->io`.
     Add an in-memory backend (writes to a buffer instead of
     stdout, returns canned input) for headless testing. The
     replay harness would then be a Linux executable that
     spawns N threads, each with its own ctx and in-memory
     I/O, instead of N processes.

  6. **Drop atexit handlers** in favor of explicit
     `ryl_ctx_free()`. The single-process binary still calls
     `ryl_ctx_free()` from `main` (or from a thin
     `atexit(free_main_ctx)` shim).

  7. **Audit direct stdio.** Replace `fprintf(stderr, ...)` with
     `ctx_log(ctx, ...)` or similar. Same for fatal `ryl_die`
     paths. This is mostly mechanical but spreads across many
     files.

### 21.5 What's hard / will resist

  - **`ryl_die`** is currently `noreturn`-on-process-exit. In a
    threaded engine, dying the whole process kills sibling
    instances. Need a setjmp/longjmp escape to a per-ctx error
    handler, or convert the fatal contract to "raise this thread
    out of `ryl_ctx_run` with an error code." Affects every
    `die_at` site in `script.c` / `res.c`.

  - **Terminal raw mode** — fundamentally a process-singleton.
    Two ctxs can't both be the foreground tty driver. Threaded
    engine is for headless / scripted contexts only; the
    interactive game stays single-threaded.

  - **Signals** — see SIGWINCH note above. The model is "only
    one ctx is allowed to be the signal handler"; auxiliary
    ctxs install no-op handlers.

  - **atexit replacement** — needs a coordinated lifecycle, not
    just removing the calls. snapshot_write specifically
    *expects* to fire on process exit; in threaded mode we'd
    call it from `ryl_ctx_free` with a per-ctx path.

### 21.6 Verdict — when is this worth doing?

Process-level parallelism (already shipping) covers the
testing use case. The 22 fixtures take ~2s wall-clock under
`replay.sh --all`. Switching to threads would shave maybe
500ms by avoiding fork/exec and cold-start fold; not nothing,
but not load-bearing either.

The deeper reason to do this is **codebase health**: every
`static` global is a hidden coupling, and migrating to
explicit ctx-passing surfaces those couplings as type-checked
function signatures. The single-ownership work
(`feedback_state_abstraction.md`) is already the first leg of
this — `pa_*`/`pm_*`/`inv_*` helpers are the natural insertion
points for a `ctx` arg.

**Recommended sequencing**:
  - Finish single-ownership migration first (driv.c is the
    debt-bearer; H2 already queued).
  - Once helpers have one owner, add `ctx` to their signatures
    in one mechanical pass.
  - At that point `state` has effectively migrated; the
    remaining ctx work (RNG, env caches, I/O backend) is small.
  - World sharing stays as-is; document the "read-only
    post-fold" contract.

The thread-safety endgame is post-port — a "phase 6" cleanup
once every gameplay file routes through helpers. It's not
gating anything in flight, but the inventory above should be
re-read before any helper grows a non-trivial new global, so
the eventual ctx migration doesn't get worse over time.

## 22. DSL unification plan (audit 2026-04-25) — CLOSED, body moved

Closed end-to-end 2026-04-26 (§22.14); full body lives in
`port-plan-complete.md`. Stub left here so the master TOC's §22
anchor still resolves.

## 24. Logging + remote console (audit 2026-04-26) — CLOSED, body moved

Closed end-to-end 2026-04-27 #5; full body lives in
`port-plan-complete.md`. Stub left here so the master TOC's §24
anchor still resolves.

## 24bis. `id: <number>` → slug-keyed state (deferred, post-§25)

**Survey (2026-04-26 #3).** 205 occurrences of `id: <int>`
across `data/` JSON5 shards: `world.json5` (39 NPCs in
`npcs.<key>` + ~15 per enum across `enums.{job,race,
pc_class,gender,...}`), `world/monsters/monsters.json5`,
and seven item shards (`weapons.json5`, `armour.json5`,
`gear.json5`, `potions.json5`, `specials.json5`,
`treasures.json5`, `magic_items.json5`). Each `id:` is the
bridge between data and an int-keyed C subsystem:

  - `pa_inventory[slot]` stores item ids as ints; lookups
    via `res_item(id)` / `object(id)` index off the int.
  - `state.npc[slot]` is indexed by NPC id; `res_npc_slot
    (sym)` reads the entry's `id:` to map slug → slot.
  - `pm_cl(pm)` / `pm_ra(pm)` return int slots compared
    against `PC_*` constants in `state.h`; `state.p[].cl/
    ra/job` are uint8_t slots.
  - Save format (`current-world.json5`) writes the int
    ids directly — slug-keying state would change the
    save format strictly.

**Why deferred.** §23.B-style migrations (this session)
keep the int storage and only move the *dispatcher* out
of C. Eliminating the int entirely is a different shape:
every state accessor (`pm_cl`, `pa_inventory`,
`state.npc`), every save/snapshot path, and every C-side
comparison (`if (cl == PC_FIGHTER)`) has to switch to
slug strings. That's a coordinated cross-cutting refactor
touching ~every C file, with no incremental win until the
last call site flips.

**Sketch of the work (one possible path).** Three phases:

  1. **State layer flip.** Replace the int slot in
     `state.p[].cl/ra/job`, `state.npc[]`, `pa_inventory[]`
     with a slug-string-id (or a stable interned-symbol
     handle). Provide `pa_class_sym(slot)` / `pa_race_sym
     (slot)` / etc. helpers so accessors aren't bare
     comparisons.
  2. **Comparison-site sweep.** Every `if (cl ==
     PC_FIGHTER)` becomes `if (pa_class_is(cl, "fighter"))`.
     Largest churn site — driv.c, battle.c, subs.c are
     full of these.
  3. **Data-side cleanup.** Drop `id:` from all the
     enum/NPC/item records once nothing reads them.
     `res_npc_slot` becomes `res_npc_handle`; the int slot
     mapping retires.

**Sequencing.** Should land after §25 (1-indexed → 0-indexed)
because that pass also touches every state accessor — bundling
the two sweeps avoids re-touching the same call sites twice.
Probably a 4-6 session block, with one "halt all other work"
period because every C file changes.

**Caveat.** Save format is the strict boundary. Either bump
the save schema and document the break, or write a one-shot
migration that translates legacy int-keyed saves to the
slug-keyed form on first load. The latter is roughly one
session of careful schema-walker code.

## 25. 1-indexed → 0-indexed cleanup — CLOSED, body moved

Closed end-to-end 2026-04-27 #13 (§25.B step 4 — NPC axis flip,
the last of the four axis flips). Full body lives in
`port-plan-complete.md` alongside the per-step closures
("§25.B step closures" section). Stub left here so the master
TOC's §25 anchor still resolves.

## Session 2026-04-26 closure + continuation prompt

This session shipped five commits on `lagergren/port1`:

  - `ce7af68` — §23.A fixture: combat-monster-magic
    byte-identity baseline + texts-path bug fix in
    `data/world/spells/monster.json5`. The `do:` bodies were
    referencing `monster.frost.name` etc., but the texts live
    at `battle.monster.*`; resolve_text_id short-circuits on
    names containing `.` (treats them as absolute), so the
    lookups silently failed and every monster cast was
    emitting blank bytes. Invisible because no prior fixture
    exercised the dispatcher.
  - `5dfa961` — §24.A0: `src/log.{c,h}` with `LOG_D/I/W/E/A`
    macros, default threshold WARN. `ryl_log()` retired, two
    callers migrated to `LOG_A`. `res_realize_npcs` grew
    descriptive `LOG_I` lines + cosmetic delay 600ms→40ms
    (campaign setup ~14s → ~1s with the dot pacing intact).
  - `d3047d2` — port-plan split: master `port-plan.md` (45
    lines, TOC only) + `port-plan-complete.md` +
    `port-plan-incomplete.md`. New §24 (logging plan) + §25
    (1-indexed cleanup) sections drafted.
    `plans/future-directions.md` (multi-party / MUD /
    shared-realm scoping) created.
  - This commit — session closure + continuation prompt.

**State at end of session.** §23.A complete with fixture
coverage (Hold + Cloud-of-Fire pinned at seed 42; Frost +
Confusion share the same machinery). §24.A0 (minimal logging
API) landed; the 75-site `fprintf(stderr, …)` migration is
queued as §24.A1. `plans/future-directions.md` captures the
multi-party scoping the unfinished "version 3" started
exploring. `make check` + `make testasan` green at HEAD
(`d3047d2`).

==== CONTINUATION PROMPT (paste verbatim next session) ====

Read `port-plan-incomplete.md`'s "Session 2026-04-26 closure
+ continuation prompt" section. Five commits since the last
continuation prompt; the §23 lookahead in §22.14 is still
relevant for everything beyond §23.A.

Recommended sequencing for next session(s):

  1. **§24.A1** (~1 session, mechanical). Bulk-migrate the
     remaining ~75 `fprintf(stderr, …)` callsites to
     `LOG_W` / `LOG_E` per severity. Distribution: res.c x44,
     driv.c x36, snapshot.c x28, party.c x10, json5.c x5,
     primitives.c x2, ryl.c x3, inventory.c x3. None of them
     fire under existing fixtures today (zero stderr bytes
     during fixture playback), so the migration is bless-free
     in expectation. Validates the logging API end-to-end
     before more §23 / §24 work stacks callers on top of the
     legacy pattern. Author rule: severity is `LOG_E` for
     "couldn't proceed, returned default" sites
     (`res_formula_int: world not loaded`, `res: oom`);
     `LOG_W` for "shape was wrong, falling back" sites
     (unknown enum tag, slot out of range, malformed record).

  2. **§23.B** (~2 sessions). battle.c item-spell switch
     (cases 100..111, lines 194-403, ~210 LOC). Biggest §23
     piece remaining. Pattern: `data/world/items/*.json5`
     grows per-id `effect:` specs; the case dispatch becomes
     `apply_item_effect(id, ctx)` (or the data-shape
     equivalent). Rides on §23.A's prep ops + combat-ctx
     bindings already landed. After §24.A1 lands, any new
     diagnostic logging in §23.B uses `LOG_*` directly.

  3. **§23.C / §23.D in parallel** (~1 session each,
     mechanical). Combat-constants sweep + driv.c
     char-creation thresholds. Small.

  4. **§24.B** (~1 session). File + null sinks behind
     `RYL_LOG=file:…` / `RYL_LOG=null`. The null sink lets
     fixtures opt out of stderr noise once §24.A1 has
     migrated everything; the file sink replaces the
     implicit `~/.rylween/rylween.log` write with an explicit
     env-var path. After §24.A1 + §24.B land, the replay
     harness can default to `RYL_LOG=null` per fixture and a
     re-bless drops any residual noise lines.

  5. **§23.F + §23.E** (closeouts). ydarg stairs (already
     partly done in `e61be95`; close out the remaining
     branches) + bestiary special-encounter survey (likely
     closes as documentation).

  6. **Later: §24.C** (TCP listener — telnet-friendly remote
     console; bind to 127.0.0.1 by default). **§25**
     (1-indexed → 0-indexed cleanup, low priority, post-§22-
     §24). **`plans/future-directions.md`** multi-party MUD
     work — only after §22 / §23 / §24 are fully closed.

Pick (1) for a mechanical warm-up; the prompt for (2) is
already in §22.14 of `port-plan-incomplete.md` ("§23.B —
battle.c item-spell switch"). (1) before (2) avoids
re-touching the same callsites; either order is otherwise
fine.

Pre-flight notes:

  - `make check` + `make testasan` both green at HEAD
    (`d3047d2`).
  - All four §23.A monster-spell bodies share machinery; the
    seed-42 fixture exercises Hold + Cloud-of-Fire. Frost
    and Confusion are not byte-pinned by any current
    fixture — the dispatcher coverage they get is
    structural, not byte-identity. If §23.B introduces any
    monster-side logic that depends on Frost / Confusion
    firing, add a second seed/encounter at that time.
  - §24.A0 default threshold is WARN. Set
    `RYL_LOG_LEVEL=info` to see the realm-realization trace.
    AUDIT bypasses the threshold (writes to
    `data/rylween.log`).
  - `port-plan.md` is now a 45-line master TOC. Active queue
    lives in `port-plan-incomplete.md`. When a section
    closes, move its body across to `port-plan-complete.md`
    and update the master TOC's File column.
  - `plans/future-directions.md` is deliberately *outside*
    the port plan — don't merge it back; it captures
    multi-party MUD scoping that's not on any critical path.

Bar per session: `make check` + `make testasan` both green
at every commit.

==== END CONTINUATION PROMPT ====

## Session 2026-04-26 #2 — Mac Terminal rendering + DSL polish

This session detoured from §23.B into a chain of UI / DSL fixes
prompted by visual bugs the user hit while playing the build in
Mac Terminal.app. §23.B itself remains queued (start of session
work was a survey + design pass, no code landed).

Nine commits on `lagergren/port1`, all green at HEAD (`051557d`):

  1. `24ea66e`  title-menu (B): split `driv.menu.header` /
     `driv.menu.common` into per-line records so each
     `tx0`/`tx_fmt` call gets its own ESC[<x>C cursor jump.
     Author rule for menu records that target the right text
     window: each visible row is its own 1-line record.

  2. `d632da6`  title-menu: dropped the redundant white banner
     `driv.menu.banner` ("ADVENTURERS of RYLWEEN" lowercase
     `of`). The all-caps green banner from
     `data/ansi/screen.ans` is the canonical title; menu data
     no longer re-emits it. Owner line ("{name}'s copy.") is
     now the first row of the menu's right-window content.

  3. `d69664e`  primitives: `tx0`/`tx` re-emit ESC[<x>C between
     lines. Multi-line text records (`join_lines_crlf`) put
     `\r\n` between lines; the original code only emitted the
     column prefix once at the head of the buffer, so lines
     2+ rendered at column 1 instead of inside the text
     window. First version of the fix.

  4. `7c5f033`  primitives: robust per-line cursor positioning.
     Replaced the bare `ESC[<x>C` (relative cursor-right) with
     `\r ESC[<x>C` so the CR forces cursor to column 1 before
     the relative jump. `c(x, y)` also switched from
     `ESC[<y>;0H` to `ESC[<y>;1H` (col=0 has terminal-
     dependent behavior; col=1 is unambiguous).

  5. `c73652a`  primitives: `cl()` emits `ESC[2J ESC[H` instead
     of bare form feed (0x0c). On Mac Terminal / iTerm2 form
     feed is treated as LF only (no clear, no home), so the
     screen.ans's own ESC[2J cleared but didn't home the
     cursor — picture rendered shifted to wherever the prior
     `tx` had left the cursor.

  6. `22856cb`  primitives: `cl()` / `disptext` use explicit
     `ESC[1;1H`. Defense-in-depth: `disptext` itself prepends
     `ESC[1;1H` before dumping the .ans, so a picture always
     starts rendering from a known (1,1) state regardless of
     upstream cursor history.

  7. `089277e`  towne.church: fix unbound 'who' in E/R/H
     branches. Real game bug surfaced by the user — the three
     church-service branches each `let: 'member_idx', value:
     { sub: [{ var: 'who_key' }, 48] }` but the body
     downstream reads `{ var: 'who' }` (25 references). The
     DSL aborts at runtime with "unbound 'who'" the moment the
     player picks E/R/H + a digit. Partial rename. Smaller fix:
     rename the 3 lets back to `'who'`. The 4th identical-
     looking line in tavern story is correctly named, left
     alone.

  8. `b5cda77`  scripts: `48` -> `'0'` in who_key digit
     arithmetic across 6 sites. The DSL's expr evaluator
     accepts a single-char string in int position and returns
     its ASCII int. `'0'` reads at a glance; bare `48` doesn't.

  9. `051557d`  script: `digit_range` `index_var` auto-bind,
     kill who_key indirection. Extended `digit_range` to
     accept an optional `index_var:` mirroring the long-
     standing `letter_range` pattern. On match the engine
     binds the digit's int value (`ch - '0'`) into `rt_env`
     directly. Six callsites lose the `key_var: 'who_key'` on
     menu_branch and the `let` arithmetic line. The `'0'`
     subtraction (which only worked because `lo` happens to be
     1) is gone from the script layer entirely — the engine
     knows the menu's bounds and computes the slot itself.

State at HEAD:
  - make check + make testasan green at `051557d`.
  - port-plan-complete.md unchanged (no plan section closed).
  - port-plan.md (45-line master TOC) unchanged.
  - port-plan-incomplete.md: this section appended.

==== CONTINUATION PROMPT (paste verbatim next session) ====

Read port-plan-incomplete.md's "Session 2026-04-26 #2" section
above this prompt. Nine commits since the last continuation
prompt — see the list there for context. The §23 lookahead in
§22.14 / §23.B is still relevant for the queued work.

Recommended sequencing for next session(s):

  1. **§23.B — battle.c item-spell switch** (~2 sessions,
     biggest §23 piece remaining). cases 100..111 at lines
     194-403, ~210 LOC. Survey was done early in the previous
     session and recorded informally in conversation; key
     groupings:
       - Group A (narration-only): 102 (gem of confusion),
         103 (gem of blindness), 111 (flute of madness).
         Easiest. Validates the dispatcher infrastructure
         with no RNG complications.
       - Group B (first-alive-monster): 100 (frost wand),
         104 (drums of death). Need a `pick_first_alive_
         monster` engine_int.
       - Group C (AOE foes): 101 (fire wand), 109 (kael's
         axe), 110 (holy handgrenade). Use the existing
         `for_each_alive_monster` op.
       - Group D (target-prompt): 105 (staff of healing),
         108 (gem of resurrection). Need a digit-key prompt
         that binds target_idx — the new `digit_range +
         index_var:` shape from `051557d` covers this; reuse
         it.
       - Group E (party-wide): 106 (blessing scroll),
         107 (staff of curing). 106 needs a new
         `for_each_member` op (alive+dead, distinct from
         `for_each_alive_player`); 107 can use the existing
         alive iterator.
     Data shape: each `data/world/items/magic_items.json5`
     entry grows an `effect:` body (the `flute_of_madness`
     entry already has a placeholder). Lookup helper:
     `res_magic_item_effect_node(int oc)` parallel to
     `res_monster_spell_node`. Dispatcher: keep inv-letter
     read in C, look up `oc`, call `play_spell_body(...)`.
     Inventory consumption pattern: most cases consume up-
     front; case 108 only on success — handle in C around
     the play_spell_body call. RNG identity preserved per
     case (only 100/101/109 draw RNG).

  2. **§24.A1 — bulk fprintf-to-LOG_* migration** (~1 session,
     mechanical). ~75 callsites: res.c x44, driv.c x36,
     snapshot.c x28, party.c x10, json5.c x5, primitives.c
     x2, ryl.c x3, inventory.c x3. None fire under existing
     fixtures (zero stderr bytes during fixture playback), so
     bless-free in expectation. Author rule: LOG_E for
     "couldn't proceed, returned default"; LOG_W for "shape
     was wrong, falling back". Either before or after §23.B
     — both orderings fine.

  3. **§23.C / §23.D in parallel** (~1 session each,
     mechanical). Combat-constants sweep + driv.c
     char-creation thresholds. Small.

  4. **§24.B** (~1 session). File + null sinks behind
     `RYL_LOG=file:…` / `RYL_LOG=null`. Replay harness
     defaults to `RYL_LOG=null` after this lands; re-bless
     drops residual stderr noise.

  5. **§23.F + §23.E** (closeouts). ydarg stairs (already
     partly done in `e61be95`) + bestiary special-encounter
     survey (likely closes as documentation).

  6. **Later: §24.C** (TCP listener). **§25** (1-indexed
     cleanup, low priority). `plans/future-directions.md`
     multi-party MUD work — only after §22 / §23 / §24 are
     fully closed.

Pre-flight:
  - `make check` + `make testasan` green at HEAD (`051557d`).
  - 9 commits this session on `lagergren/port1`. All pushed
    to origin.
  - The §22.5 spell fixtures + §23.A combat-monster-magic
    fixture pin the player-side combat path byte-for-byte.
    Frost and Confusion are not byte-pinned by any current
    fixture — the dispatcher coverage they get is structural.
  - §24.A0 default threshold is WARN. `RYL_LOG_LEVEL=info`
    shows the realm-realization trace. AUDIT bypasses
    threshold (writes to `data/rylween.log`).
  - `tx0`/`tx` now re-emit `\r ESC[<x>C` per line. New code
    that emits multi-line text into the right text window
    just works; no need to split records. The earlier B-side
    (`24ea66e`) per-line driv.menu records still work — they
    could be merged back to multi-line shape but no
    incentive to.
  - `digit_range` now supports `index_var:` (`051557d`).
    Future menu_branch sites that read a 1..N digit pick can
    use that shape directly — no `key_var` indirection, no
    `let` arithmetic. Pattern:
        { digit_range: { lo: 1, hi: <max> },
          index_var: '<name>', then: [...] }
  - Mac Terminal renders block-char-edge gaps weirdly
    (Apple's font glyph metrics for U+258x). Pure rendering
    cosmetic; ignore.
  - port-plan.md is the 45-line master TOC. New section work
    appends to port-plan-incomplete.md; close moves to
    port-plan-complete.md.
  - plans/future-directions.md is outside the port plan —
    multi-party MUD scoping, not on the critical path.

Bar per session: `make check` + `make testasan` both green
at every commit.

==== END CONTINUATION PROMPT ====

## Session 2026-04-26 #3 — §23.B closed

§23.B (battle.c item-spell switch, cases 100..111) is now
fully migrated to data. The legacy switch is gone; every
magic item carries an `effect:` DSL body in
`data/world/items/magic_items.json5` and battle.c's
Use-Item branch dispatches through `play_spell_body` only.
See port-plan-complete.md for the full closure detail.

Five group commits + one closeout commit on
`lagergren/port1`:

  1. `4fc6962` Group A — gem_of_confusion / gem_of_blindness
     / flute_of_madness (RNG-free narration). Validated the
     dispatcher infra (res_magic_item_effect_node + the
     per-item `effect:` body shape).
  2. `4e2eead` Group B — frost_wand / drums_of_death.
     pick_first_alive_monster engine_int.
  3. `f19b5f1` Group C — fire_wand / kaels_axe /
     holy_handgrenade. for_monster +
     for_each_alive_monster cover the AOE shapes.
  4. `2537f4a` Group D — staff_of_healing /
     gem_of_resurrection. Body-owned inventory consume
     (pa_set_inventory engine_call) so 108 can defer to
     its success branch; action_retry battle_flag bumps
     108-fail back to the action menu.
  5. `3768f2e` Group E + closeout — blessing_scroll /
     staff_of_curing. Switch removed; dispatcher now the
     only Use-Item path.

`make check` + `make testasan` green at each commit.

**Followups identified during the work:**

  - Survey for `id: <number>` patterns showed 205 hits
    across world.json5 / monsters / items / NPCs. All are
    load-bearing today (each id is the int the C state
    layer stores in pa_inventory / state.npc / state.cl).
    Eliminating them is a slug-keyed-state refactor —
    out of scope for §23, queue as §25+ if/when wanted.
  - Numeric `case <int>:` dispatches still appear in
    driv.c (race switch, lines 1540-1575), ydarg.c
    (scenario switches, multiple), speak.c (small).
    Same shape as §23.B was — keep the int storage,
    move the dispatch to data. Worth queueing as
    §23.G (driv race), §23.H (ydarg scenarios), etc.

**Remaining work in §23 / §24 / §25** is unchanged from
the §23.B-pre continuation prompt: §23.C (combat
constants), §23.D (driv char-creation thresholds),
§24.A1 (LOG_* migration), §24.B (file/null log sinks),
§23.F (ydarg stairs), §23.E (bestiary survey), §24.C
(TCP listener), §25 (1-indexed cleanup).

==== CONTINUATION PROMPT (paste verbatim next session) ====

Read port-plan-incomplete.md's "Session 2026-04-26 #3"
section above this prompt — the previous session closed
§23.B in five group commits + one closeout commit. Plan-
file move (the doc-only closure entry in
port-plan-complete.md) is already done.

Recommended sequencing for the next session(s):

  1. **§23.C / §23.D in parallel** (~1 session each,
     mechanical). Combat-constants sweep + driv.c
     char-creation thresholds. The shape is just lifting
     literals into res_combat_int / res_driv_int lookups;
     no new DSL surface needed.

  2. **§24.A1 — bulk fprintf-to-LOG_* migration**
     (~1 session, mechanical). ~75 callsites: res.c x44,
     driv.c x36, snapshot.c x28, party.c x10, json5.c x5,
     primitives.c x2, ryl.c x3, inventory.c x3. Author rule:
     LOG_E for "couldn't proceed, returned default";
     LOG_W for "shape was wrong, falling back".

  3. **§23.G / §23.H — race + ydarg-scenario dispatcher
     migrations** (~1 session each). Same shape as §23.B
     — `switch (n) { case 1: ...; case 2: ... }` becomes
     a data-driven dispatch with bodies in JSON5. driv.c
     race switch (1540-1575) + ydarg.c scenario switches
     are the targets; both keep the int-keyed state, just
     move the dispatch table out.

  4. **§24.B** (~1 session). File + null sinks behind
     `RYL_LOG=file:…` / `RYL_LOG=null`. Replay harness
     defaults to `RYL_LOG=null` after this lands;
     re-bless drops residual stderr noise.

  5. **§23.F + §23.E** (closeouts). ydarg stairs (already
     partly done) + bestiary special-encounter survey
     (likely closes as documentation).

  6. **Later: §24.C** (TCP listener). **§25** (1-indexed
     cleanup, low priority). `plans/future-directions.md`
     multi-party MUD work — only after §22-§24 are
     fully closed.

Pre-flight:
  - `make check` + `make testasan` green at HEAD
    (3768f2e or whatever the §23.B closeout commit was).
  - 6 commits this session on `lagergren/port1`, all
    pushed to origin.
  - The §22.5 spell fixtures + §23.A combat-monster-magic
    fixture pin the player-side combat path byte-for-byte.
    Group A/B/C/D/E magic-item bodies are not byte-pinned
    by any current fixture — the dispatcher coverage they
    get is structural. (A combat-magic-item fixture
    covering one or two of the items would be a low-cost
    addition.)
  - The per-item-effect dispatcher in battle.c is now
    the only Use-Item path. A bad oc with no effect body
    fires `ryl_die("magic_items: no effect body for id N")`
    — instructive for authoring errors but never reaches
    today (every item has a body).
  - Body-owned inventory consume: every magic-item body
    starts with `engine_call: 'pa_set_inventory', args: [
    { var: 'inv_slot' }, 0 ]`. gem_of_resurrection is the
    one exception that defers the consume to its success
    branch. New magic items must follow the same pattern.
  - script_combat_ctx.action_retry: a body sets it via
    `mutate { battle_flag: 'action_retry' } = 1` to bump
    battle.c back to the action menu without consuming
    a turn. Used by 108-fail today; available to any
    future body that needs the same shape.
  - port-plan.md is the 45-line master TOC. Section work
    appends to port-plan-incomplete.md; close moves to
    port-plan-complete.md.
  - plans/future-directions.md is outside the port plan —
    multi-party MUD scoping, not on the critical path.

Bar per session: `make check` + `make testasan` both green
at every commit.

==== END CONTINUATION PROMPT ====

## Session 2026-04-26 #3 (cont) — plan polish + temple-R fixture

Two follow-on commits after the §23.B closeout:

  1. `178ec7f` plan polish — port-plan-complete.md gets the
     §23.B closure write-up; port-plan-incomplete.md gets
     this session's log + new §23.G (driv race switch),
     §23.H (ydarg scenario switches), §24bis (`id:` →
     slug-keyed state, deferred); port-plan.md TOC adds
     the §24bis row.
  2. `b1cf2d9` fixture — `towne-thord-church-resurrect`.
     Covers the temple (R)aise dead path that was broken
     and fixed earlier in the session (089277e + b5cda77 +
     051557d); without this fixture those fixes could
     regress unnoticed. Uses RYL_FORCE_DEAD_SLOT=3 to
     pre-seed a dead member without a combat detour.
     Test count: 28 → 29 fixtures.

State at HEAD (`b1cf2d9`):
  - 8 commits on `lagergren/port1` since the prior push
    point, all green on `make check` + `make testasan`.
  - The full session list, in order:
      4fc6962 §23.B Group A
      4e2eead §23.B Group B
      f19b5f1 §23.B Group C
      2537f4a §23.B Group D
      3768f2e §23.B Group E + closeout
      178ec7f plan polish (§23.G/H queued, §24bis deferred)
      b1cf2d9 towne-thord-church-resurrect fixture
      (this commit) session #3 (cont) closure + continuation
  - Fixture bank: 29 total. magic-item dispatcher is
    structural-only; a combat-magic-item fixture is the
    obvious follow-on — see continuation prompt below.

==== CONTINUATION PROMPT (paste verbatim next session) ====

Read this section of port-plan-incomplete.md (Session
2026-04-26 #3 — both halves) for the full §23.B closure
context. Eight commits on `lagergren/port1` since the
prior session push. HEAD is `b1cf2d9` plus the doc-commit
that wrote this prompt. `make check` + `make testasan`
green at every step.

**Where things stand right now (state worth keeping in
mind before you start):**

  - **Magic-item dispatcher pattern is established and
    reusable.** Resolver returns (body, slug); body owns
    its side effects (inventory consume,
    state mutations); a battle_flag (`action_retry`) on
    script_combat_ctx lets a body bump the dispatcher
    back to the action menu without consuming a turn.
    Same shape applies to §23.G (driv race switch) and
    §23.H (ydarg scenarios); reuse it instead of growing
    new ad-hoc dispatchers. The slug-yielding
    `&slug_out` parameter on `res_magic_item_effect_node`
    is the convention to copy when authoring future
    int-keyed → data-keyed resolvers.

  - **Body authoring rule: prefer mutate over
    engine_call when the lvalue exists.** Magic-item
    bodies use `mutate { target: { member_field: { idx,
    field } }, op: 'add', value: 3 }` to express stat
    bumps; an engine_call helper would have been a
    needless duplicate of an existing path. The minimal
    new engine surface §23.B added (pa_set_inventory /
    pm_full_heal / pm_set_dead) is shape-driven —
    pa_set_inventory because the body needs to clear the
    triggering slot; pm_full_heal because it has a
    dead-check guard the DSL doesn't reach for cleanly;
    pm_set_dead because dead is a u8 with bit-flag
    semantics. New engine_calls should clear the same
    bar.

  - **Iterator semantics matter for RNG identity.** The
    three combat iterators are NOT interchangeable:
      - `for_monster { var: 'i' }` — loops 1..noc
        unconditionally; RNG draws inside fire on dead
        slots too. Used by fire_wand to preserve the
        legacy `for (i=1; i<=noc; i++) { spelldamage =
        ...; if (!deadc[i]) ... }` byte stream.
      - `for_each_alive_monster` — skips dead slots;
        RNG draws only on alive. Used by kaels_axe /
        holy_handgrenade. Body sees `monster_idx` as a
        fixed-name binding.
      - `for_each_alive_player` — symmetric for
        party-side. Body sees `target_idx`.
      - `for_member { var: 'm' }` — loops the full party
        range; alive AND dead. Used by blessing_scroll
        (for stat boosts that hit dead members too).
    Pick by RNG identity first, then by intent.

  - **Body-owned consume + action_retry are reusable.**
    Any new script body that wants a "consume on success
    only" or "retry the prompt" semantics has the
    primitives in place. action_retry is currently only
    set by gem_of_resurrection but the scaffolding is
    there for any future case (e.g. a misclick on a
    scroll target).

  - **Pre-existing bugs surface during migrations** —
    the rule is to fix them inline (matches the existing
    "feedback_preexisting_bug_fixes" memory). §23.B Group
    B fixed `tx("...KILLED!")` (no \r\n) by routing
    through the standard battle.killed shard. Watch for
    this pattern in §23.G/H.

  - **Outstanding cleanup (low priority):**
      - clangd flags `ryl.h` / `towne.h` as unused
        includes in res.c, primitives.c, driv.c. Not
        -Werror, so non-blocking, but a quiet sweep
        through #include-cleanup is worth ~30 minutes.
      - DSL `key_range: ['1','4']` literals in
        data/world/scripts/towne.json5 don't track
        res_party_member_max(); three or four sites.
        Lift if a prompt ever needs N != 4.
      - The §22 lane-A "inert pre-DSL placeholder"
        cleanup — flute_of_madness was the last known
        such; nothing else is currently flagged but
        worth a grep for `['seq',\s*\['say'` / `set_flag`
        legacy shapes if a future migration surfaces one.

**Recommended sequencing for the next session(s):**

  1. **Combat-magic-item fixture** (~0.5 session). Pick
     one of the §23.B-migrated items (frost_wand is the
     most distinctive — single-target damage with RNG)
     and author a fixture that triggers it in combat.
     Pattern: createparty → seed 42 → wilderness encounter
     forced via RYL_FORCE_ENCOUNTER → in-combat (U)se
     Item → letter for slot containing the wand. Pin the
     RNG-identity stream + the H1..H4 / monster-HP block
     in the snapshot.
     This is the only §23.B coverage gap — the
     dispatcher works (make check passes today) but no
     fixture proves the body produces the right byte
     stream.

  2. **§23.C / §23.D** (~1 session each, mechanical).
     Combat-constants sweep + driv.c char-creation
     thresholds. Lift literals into res_combat_int /
     res_driv_int. No new DSL.

  3. **§24.A1 — fprintf-to-LOG_***  (~1 session,
     mechanical). ~75 callsites; LOG_E for "couldn't
     proceed", LOG_W for "shape was wrong, fallback".

  4. **§23.G / §23.H** (~1 session each). Apply the
     §23.B dispatcher pattern to driv.c race switch
     (1540-1575) + ydarg.c scenario switches. Reuse
     `res_<X>_effect_node(int, &slug)` resolver shape
     and the body-owns-side-effects rule.

  5. **§24.B** file/null log sinks (~1 session); replay
     harness default to RYL_LOG=null after this.

  6. **§23.F + §23.E closeouts** — finish ydarg stairs
     (partly done in e61be95) and survey bestiary
     special-encounter switches.

  7. **Later:** §24.C (TCP listener), §25 (1-indexed),
     §24bis (id: → slug, post-§25), then
     plans/future-directions.md.

Pre-flight:
  - HEAD passes `make check` + `make testasan`.
  - Magic-item dispatcher in battle.c is the only Use-
    Item path; new items must author an `effect:` body
    in magic_items.json5 (no C-side case arms).
  - Inventory consume is body-owned: every magic-item
    body opens with `engine_call: 'pa_set_inventory',
    args: [{ var: 'inv_slot' }, 0]`. gem_of_resurrection
    defers the consume to its success branch — pattern
    for any future conditional consumer.
  - action_retry battle_flag: set via
    `mutate { battle_flag: 'action_retry' } = 1` to bump
    battle.c back to the action menu. Reusable shape.
  - 29 fixtures in the suite. New fixtures append to
    tools/replay/<name>.{env,in,expect,expect_snapshot};
    `tools/replay.sh <name> --bless` writes the baselines.
  - port-plan.md is the 45-line master TOC. New work
    appends to port-plan-incomplete.md; close moves to
    port-plan-complete.md.

Bar per session: `make check` + `make testasan` both
green at every commit. Push at session close.

==== END CONTINUATION PROMPT ====

## Session 2026-04-26 #5 — §23 swept end-to-end

Walked the entire §23 queue in one session. Closures live in
`port-plan-complete.md` under "§23 closures". Status:

  - §23.D, §23.C, §23.F, §23.I, §23.H — all migrated.
    Six commits on `lagergren/port1`:
      4960a34 §23.D driv char-creation thresholds
      e6282bf §23.C battle.c combat constants
      1dca812 §23.F ydarg stairs item-trade
      a6430bb §23.I tx_narrate + ydarg.confrontation
      b9d183a §23.H ydarg room dispatcher leftovers
      e480e05 §23.J knobs (RYL_FORCE_YDARG_GRANTS +
              RYL_FORCE_YDARG_EASY_ROOMS)
  - §23.G — closed as planning artifact (no race switch
    exists in the codebase; plan conflated it with the
    class switch closed by §23.D).
  - §23.E — closed as documentation (UI dispatchers, not
    data targets, per the original §23.E plan note).
  - §23.J — knobs landed, fixture authoring deferred.

State at HEAD:
  - 6 commits on `lagergren/port1` since the prior push
    (b3d52b4); each green on `make check` + `make testasan`.
  - 29 fixtures total. No new fixtures this session.
  - `combat.attack.*` / `combat.battle_cry.*` /
    `combat.defend.*` / `combat.run.*` subtrees added;
    `enums.pc_class.<key>.min_stat` records added; `driv.*`
    top-level subtree added; `ydarg.{stairs,narration,
    pitfall,room}` subtrees added.

**Remaining §23 work is one item only:** the §23.J fixture
itself (ydarg-confrontation). Knobs `RYL_FORCE_YDARG_GRANTS`
+ `RYL_FORCE_YDARG_EASY_ROOMS` are wired and tested. The
fixture needs:

  1. RYL_FORCE_YDARG_GRANTS=1, RYL_FORCE_YDARG_EASY_ROOMS=1,
     RYL_FORCE_PARTY_LV=28, RYL_FORCE_POS=14,30 in env.
  2. Title sequence (party creation, 4 chars, all class 2
     thief — no min-stat gate, fastest).
  3. Wilderness E to enter castle.
  4. 5 ryl_pauses (approach, jaara_form, jaara_speech,
     key_accepted, enter), then the door-slam delay (no
     input), then the post-room-init pause.
  5. Per floor (×4): 8 moves to (5,5) (4× '6' east + 4×
     '2' south); each move triggers a no-op room body
     (case 4) + nav prompt. Then on stairs: G + digit
     (1..4 in floor order) + ryl_pause for mage_grateful.
  6. Confrontation: 2 ryl_pauses (act1), then 4-monster
     encounter sequence (~10 attack rounds each at LV=28),
     7 ryl_pauses (act2), baron_ydarg fight (~10 rounds),
     5 ryl_pauses (act3), 2 ryl_pauses (act4),
     cleanexit().

Estimated keystroke count: ~500 (the four pre-confrontation
monsters + baron_ydarg dominate). Fragile to RNG shifts in
combat (per-round damage rolls). The easy-rooms knob covers
ydarg's per-tile RNG; combat RNG is still an open exposure.

**Recommended next session order:**

  1. **§23.J fixture authoring + bless** (~1 sess). The
     only remaining §23 piece. Author with `--bless` after
     each act block to localize the diff if the keystroke
     stream is wrong. If a combat fight goes too many
     rounds and breaches the keystroke budget, consider
     RYL_FORCE_PARTY_LV=28 stacked with RYL_FORCE_GOLD or
     a hypothetical RYL_FORCE_WEAPON_DAMAGE knob — but
     defer those if not strictly needed.
  2. **§24.A1 — fprintf-to-LOG_*** (~1 sess, mechanical).
     ~75 callsites; LOG_E for "couldn't proceed", LOG_W
     for "shape was wrong, fallback".
  3. **§24.B** file/null log sinks (~1 sess); replay
     harness default to RYL_LOG=null after this.
  4. **Later:** §24.C (TCP listener), §25 (1-indexed),
     §24bis (id: → slug, post-§25), then
     plans/future-directions.md.

Bar per session: `make check` + `make testasan` both
green at every commit.

## Session 2026-04-27 #2 — §24.A1 closed

Bulk-migrated the `fprintf(stderr, …)` panic+warning sites to
the `LOG_W` / `LOG_E` API landed in §24.A0. 72 callsites flipped
across 7 files in 6 commits on `lagergren/port1`:

  - 1c64859 §24.A1: res.c (43)
  - ceccffa §24.A1: party.c (10)
  - 02a75a9 §24.A1: driv.c (7)
  - 4d04c02 §24.A1: json5.c (5)
  - acae7e5 §24.A1: snapshot.c (3)
  - a0129e6 §24.A1: inventory.c (3) + primitives.c::ryl_die (1)

All but one site was a panic followed by `exit(1)` and went to
LOG_E; the snapshot.c fopen-failure recovers (returns) and went
to LOG_W. Module names are file stems (`res`, `party`, `driv`,
`json5`, `snapshot`, `inventory`) — except primitives.c::ryl_die
kept module `ryl` to match its public-API "ryl: <msg>" prefix
contract that callers document.

Three remaining stderr writes in src/ are intentionally not
migrated:

  - **ryl.c x3** — the `--check-world` "world: ok" success
    line and the two argv usage messages. CLI output, not
    diagnostics; the LOG_W/LOG_E severity rule does not apply.
    Migrating them would either (a) silence "world: ok" under
    the default WARN threshold, breaking the visible CI signal,
    or (b) add `[INF][ryl]` prefix noise to a usage message.
  - **log.c:97** — the `LOG_*` emitter implementation itself.

No fixture drift. The 30-fixture replay set never exercises any
of the migrated paths — they're "world failed to load" / "scripts
contain a typo" / "save flag tag unknown" panics that a clean
fixture run never hits. `make check` + `make testasan` green at
every commit.

The redundant `"res: "` / `"snapshot: "` / `"inventory: "` etc.
file-tag prefixes inside the message bodies were dropped because
the new `[ERR][module]` prefix carries that information. Function
names like `res_formula_int:` / `pa_flag_set:` / `party_journal_add:`
were kept inside the message body because they identify the
specific call site, which the module tag alone cannot.

State at HEAD (a0129e6):
  - 30 fixtures green, both `make check` and `make testasan`
    clean. §23 fully closed; §24.A0 + §24.A1 closed. No
    open §23 / §24.A items.

**Recommended next session order:**

  1. **§24.B — file + null log sinks** (~1 sess). Add
     `RYL_LOG=file:<path>` and `RYL_LOG=null` parsers in
     `src/log.c`; default sink stays stderr. Once this lands,
     the replay harness can default to `RYL_LOG=null` per
     fixture so any future LOG_* drift doesn't propagate
     into baselines unless explicitly captured. Also adds
     a hook for §24.C (TCP) and §24.E (per-fixture opt-in).
  2. **§25 — 1-indexed → 0-indexed cleanup** (multi-sess).
     The §24 logging is now in place to surface noisy
     bless waves; §25 can land in measured slices.
  3. **Later:** §24.C (TCP listener — telnet-friendly remote
     log), §24bis (id: → slug, post-§25), then
     plans/future-directions.md (multi-party MUD work).

**Pre-flight notes for §24.B:**

  - The env-var parser slot is `RYL_LOG=...` (not the existing
    `RYL_LOG_LEVEL=...` which is for threshold). `RYL_LOG=null`
    drops everything; `RYL_LOG=file:<path>` writes
    [lvl][mod] body lines to the named file (resolve relative
    to `ryl_state_path` so the audit/log directory convention
    holds).
  - AUDIT writes (`LOG_A`) still go to `data/rylween.log`
    regardless of `RYL_LOG=…`. The audit file is "history,
    not diagnostic" per the §24.A0 docstring; its sink is
    fixed.
  - `RYL_LOG_LEVEL=…` still gates DEBUG/INFO. The two env
    vars compose: `RYL_LOG_LEVEL=info RYL_LOG=null` yields a
    silent-to-stderr but-still-info-threshold run, useful
    when capturing AUDIT to file without stderr spam.

Bar per session: `make check` + `make testasan` both green
at every commit.

## Session 2026-04-27 — §23.J fixture authored (loss path)

Authored the §23.J ydarg-confrontation fixture and added
the two remaining replay-only knobs needed for it. §23.J
is now closed end-to-end (closure note moved to
`port-plan-complete.md`). Two commits on `lagergren/port1`:

  - 78e12ac §23.J prep #2: RYL_FORCE_PARTY_WP +
    RYL_FORCE_PARTY_HP_FULL
  - 757e3dd §23.J: ydarg-confrontation fixture (loss path
    through fight #2)

`RYL_FORCE_PARTY_WP=<slug>` resolves the slug through
`res_item_id` and sets every member's wp; combined with
`RYL_FORCE_PARTY_HP_FULL=1` (hp = h after the lv bump),
a default-roll thief party at lv 28 deals ~65 dmg per
hit instead of the obdamage(0)-only ~7 dmg the lv-1-hp
path would give. Both knobs no-op when unset, so the
combat-mage-death / combat-monster-magic /
wilderness-priest-banish / wilderness-priest-rebirth
fixtures (which use RYL_FORCE_PARTY_LV without these
two) remain byte-identical.

The bless captured a loss path during fight #2 — the Night
Gaunt's Cloud of Fire AOE deals h2/5 + random(0,4) damage
(~90 dmg per char with the scaled h2=434), which one-shots
three of the four chars after the party kills the first of
two gaunts. Per the §23.J authoring note ("pin whichever
path the bless captures") the loss path is the fixture; a
follow-up could either stratify (D)efend rounds in to keep
hp above the 90-dmg threshold or add an explicit
`RYL_FORCE_NO_MONSTER_MAGIC=1` knob to suppress the
spell-cast roll entirely. Neither is needed for §23.J
itself — the loss path covers act_1 narration + crypt_horror
victory + monster-magic dispatch + losegame() narration,
which is enough to verify every §23.I tx_narrate call site
plus the §23.A monster nasty-magic dispatcher's spell-pick
draw.

State at HEAD (757e3dd):
  - 30 fixtures total (`ydarg-confrontation` is the new
    one). Both `make check` and `make testasan` green.
  - All §23 sub-items closed except for the conf_act2/3/4
    + baron_ydarg coverage extension above (optional;
    no §23 code is unverified, only the data is).

**Recommended next session order:**

  1. **§24.A1 — fprintf-to-LOG_*** (~1 sess, mechanical).
     ~75 callsites; LOG_E for "couldn't proceed", LOG_W
     for "shape was wrong, fallback".
  2. **§24.B** file/null log sinks (~1 sess); replay
     harness default to RYL_LOG=null after this.
  3. **Later:** §24.C (TCP listener), §25 (1-indexed),
     §24bis (id: → slug, post-§25), then
     plans/future-directions.md.

Optional follow-up: extend ydarg-confrontation to a
victory path (defend-driven survival or no-magic knob)
to pin act_2/3/4 narration + the heroes-file write. Not
load-bearing for any open §23 work; queue it if the
plain-text-pin coverage matters for later refactors of
ydarg.c::confrontation.

Bar per session: `make check` + `make testasan` both
green at every commit.

## Session 2026-04-27 #3 — §24.B closed

Added the `RYL_LOG=` sink selector to `src/log.c` alongside the
existing `RYL_LOG_LEVEL=` threshold knob. Three values land in
this commit; a fourth (`tcp:`) is queued as §24.C.

Recognized values (case-insensitive):
  - unset / `RYL_LOG=stderr` — stderr (the historical default)
  - `RYL_LOG=null` — drop every WARN/ERR/INFO/DEBUG line
  - `RYL_LOG=file:<path>` — append `[lvl][mod] body\n` to
    `<path>` resolved through `ryl_state_path` (so a bare leaf
    lands under `$HOME/.rylween/` or `data/`, mirroring how
    AUDIT resolves `rylween.log`)

AUDIT (LOG_A) writes are unchanged — they still go to
`data/rylween.log` regardless of `RYL_LOG=…`. Per the §24.A0
docstring, AUDIT is "history, not diagnostic" and its sink is
fixed. Only the WARN/ERR/INFO/DEBUG branch (the `fprintf` at
the bottom of `log_msg`) routes through the new dispatch.

Implementation notes:
  - The selector is parsed once on first emit and cached in a
    static `cached`, mirroring the existing `threshold()`
    helper. No per-line getenv hits.
  - The file: sink opens lazily with `fopen(path, "a")` and
    registers `atexit(sink_file_close)` once. On open failure
    we emit a one-shot `[ERR][log]` to stderr and degrade to
    NULL for the rest of the run (silent rather than infinite
    error spam).
  - An unrecognized value (e.g. `RYL_LOG=garbage` or the empty
    `file:`) emits a one-shot `[ERR][log] unrecognized RYL_LOG=…`
    diagnostic and falls back to STDERR — strict-but-helpful.
  - The `RYL_LOG=` parser is intentionally tiny — `strncasecmp`
    on a 5-char prefix. §24.C `tcp:host:port` will follow the
    same shape.

Smoke-tested (one-off rig built against `log.c` + `compat.c`)
across all five branches: default, `null`, `stderr`,
`file:smoke.log`, and the two error-path values. Each behaved
as documented; AUDIT continued writing through every variant.

State at HEAD:
  - 30 fixtures green, both `make check` and `make testasan`
    clean. §23 fully closed; §24.A0 + §24.A1 + §24.B closed.
    No open §23 / §24.A / §24.B items.
  - The replay harness still uses the default stderr sink.
    §24.E (per-fixture `RYL_LOG=null` opt-in) is next; it can
    land in a small follow-up commit independent of any
    further sink work.

**Recommended next session order:**

  1. **§24.E — replay-harness default `RYL_LOG=null`** (~0.5
     sess). Wire `tools/replay.sh` (and the per-fixture
     wrappers) to set `RYL_LOG=null` for each fixture so
     future LOG_* drift can't propagate into baselines unless
     a fixture explicitly captures it. Trivial; the
     `RYL_LOG_LEVEL` setup in those scripts is the same
     pattern.
  2. **§24.C — TCP listener sink** (~1 sess). Add
     `RYL_LOG=tcp:<host>:<port>`; same dispatch shape as
     §24.B. Telnet-friendly remote log for the eventual
     multi-party MUD work.
  3. **§25 — 1-indexed → 0-indexed cleanup** (multi-sess).
  4. **Later:** §24bis (id: → slug, post-§25), then
     plans/future-directions.md.

Bar per session: `make check` + `make testasan` both green
at every commit. Push at session close.

## Session 2026-04-27 #4 — §24.E closed

Defaulted `RYL_LOG=null` in the per-fixture subshell of both
replay harnesses (`tools/replay.sh` + `tools/replay_asan.sh`)
so any future LOG_W/LOG_E drift cannot propagate into the
baselines without a fixture explicitly opting in.

Shape:
  - The subshell sets `RYL_LOG=null` first under `set -a`,
    then sources the optional `<name>.env`, then runs the
    binary. Bash semantics → a `RYL_LOG=…` line in `.env`
    overrides the default; no `.env` (or one that omits
    `RYL_LOG`) keeps `null`.
  - This composes with the existing replay knobs
    (`RYL_FAKE_EPOCH`, `RYL_FAKE_SEED`, `RYL_FORCE_*`,
    `RYL_NO_DELAY`, `RYL_PARTY_MEMBER_MAX`) which are sourced
    the same way.
  - AUDIT (LOG_A) is unaffected — it still writes to
    `data/rylween.log` regardless of `RYL_LOG`. Each fixture
    runs under a fresh `HOME=$(mktemp -d)` so the audit log
    lands under `$home/.rylween/rylween.log` and is cleaned
    up by the existing trap; nothing leaks across runs.

The Makefile gate is unchanged on purpose — `make replay`
(and `make check`) still default `RYL_LOG=null` only via the
harness, so an interactive `./rylween` invocation outside the
fixture path keeps the historical stderr behavior. A fixture
that wants to capture stderr again only has to add
`RYL_LOG=stderr` to its `.env`.

No fixture re-bless was needed: none of the migrated
LOG_W/LOG_E paths fire under the 39-fixture replay set today,
so the baselines were already noise-free. Verified by
re-running `make check` (39 fixtures + snapshot diffs green)
and `make testasan` (39 fixtures, no AddressSanitizer/
LeakSanitizer markers).

State at HEAD:
  - 39 fixtures green, both `make check` and `make testasan`
    clean. §23 fully closed; §24.A0 + §24.A1 + §24.B + §24.E
    closed. No open §23 / §24.A / §24.B / §24.E items.
  - Logging plan remaining: §24.C (TCP listener sink) and the
    optional §24.D (syslog).

**Recommended next session order:**

  1. **§24.C — TCP listener sink** (~1 sess). Add
     `RYL_LOG=tcp:<host>:<port>`; same dispatch shape as
     §24.B. Telnet-friendly remote log for the eventual
     multi-party MUD work.
  2. **§25 — 1-indexed → 0-indexed cleanup** (multi-sess).
  3. **Later:** §24bis (id: → slug, post-§25), then
     plans/future-directions.md.

Bar per session: `make check` + `make testasan` both green
at every commit. Push at session close.

## Session 2026-04-27 #5 — §24.C closed (+ multi-sink fan-out)

Added the TCP listener sink to `src/log.c` and, in the same
session, generalised `RYL_LOG` from a single sink into a
comma-separated list so every enabled sink receives every
WARN/ERR/INFO/DEBUG line. Both pieces shipped together because
the multi-sink case is the only one where the rcon listener is
genuinely useful (you almost always also want the lines on
disk).

Shape:
  - `RYL_LOG=tcp:[host:]port` accepts `tcp:host:port`,
    `tcp::port`, and `tcp:port`. The last two default the host
    to `127.0.0.1`. IPv6 literals (bracket form) intentionally
    not parsed — out of scope for the rcon use-case; pass an
    IPv4 address or `0.0.0.0`.
  - Default bind is loopback. `tcp:0.0.0.0:<port>` is the
    explicit opt-out for "expose on every interface". No
    authentication; treat as a development diagnostic, not an
    operations-grade log shipper.
  - Up to 8 simultaneous clients. Pending `accept`s drained
    lazily inside `log_msg`'s emit branch (no separate event
    loop). A 9th connection while the slot table is full is
    accepted and immediately closed, so a runaway connect loop
    can't exhaust fds.
  - Loss policy on slow readers: `EAGAIN` / `EWOULDBLOCK` →
    drop the line for that one client; `EPIPE` / other send
    error / short write → close the fd and free its slot. The
    engine never blocks waiting for a reader. SIGPIPE dodged
    via `MSG_NOSIGNAL` on Linux + `SO_NOSIGPIPE` per accepted
    socket on macOS/BSD; no process-wide signal override.
  - `atexit(sink_tcp_close)` closes the listener fd plus every
    client fd on normal exit. `SO_REUSEADDR` set on the
    listener so a quick restart after `^C` doesn't trip
    TIME_WAIT.
  - Multi-sink list parser: comma-separated, tolerates stray
    commas, per-token max 255 chars. `null` is a no-op token —
    if it's the only entry, the effective mask is empty and
    the engine drops every diag line (preserving pre-existing
    `RYL_LOG=null` semantics). `file:` and `tcp:` are
    single-instance per process; a duplicate emits a one-shot
    `[ERR][log] …; ignored` and leaves the first instance
    alone. Open errors degrade only the failing token, the
    rest of the list still applies. Unknown tokens add the
    stderr bit so the misconfig is visible.
  - The fan-out point is `log_msg`: the line is `snprintf`-ed
    once into a 1100-byte buffer and teed in stderr → file →
    tcp order. AUDIT (`LOG_A`) is unchanged — still routed
    unconditionally to `data/rylween.log`, untouched by
    `RYL_LOG`.
  - New file: `docs/log.md` documents severity / threshold,
    the four sink tokens, bind discipline, on-wire format,
    loss policy, and `telnet localhost <port>` usage.

Smoke-tested end-to-end with
`RYL_LOG=stderr,file:smoke.log,tcp:<port>` running the
`title-create-party-5-exit` fixture: stderr + file each saw
the same 25 LOG_I lines clean; an `nc 127.0.0.1 <port>`
client captured the 18 lines emitted *after* it connected
(lazy-accept by design — earlier lines went out before the
client attached). The client got byte-identical
`[INF][realm] …` lines to the file sink — confirming the
single-format-once / tee-many path works.

Fixture impact: zero. Every fixture pins `RYL_LOG=null` after
§24.E, so the new dispatch arm only fires on developer
opt-in. `make check` (39 fixtures + snapshot diffs) and
`make testasan` (39 fixtures, no AddressSanitizer /
LeakSanitizer markers) both green at the §24.C commit and
again after this plan-update commit.

State at HEAD:
  - 39 fixtures green, `make check` + `make testasan` clean.
    §23 fully closed; §24.A0 + §24.A1 + §24.B + §24.C +
    §24.E closed. No open §23 / §24.A-C / §24.E items.
  - Logging plan remaining: only the optional §24.D (syslog
    sink). Skip if no demand surfaces.

**Recommended next session order:**

  1. **§25 — 1-indexed → 0-indexed cleanup** (multi-sess).
  2. **Later:** §24bis (id: → slug, post-§25), then
     plans/future-directions.md.

Bar per session: `make check` + `make testasan` both green
at every commit. Push at session close.

## Session 2026-04-27 #5 — §24.C + §24.F + §24.D closed (§24 done)

Three logging-plan items landed in one session, fully closing §24
ahead of the "optional D" estimate. Order:
  1. **§24.C** — TCP listener sink + `RYL_LOG` generalised to a
     comma-separated multi-sink list. (See the §24.C session note
     above for the full shape.)
  2. **§24.F** — `runtime.json5` config layer driving the always-on
     `RYL_*` knobs through a new `rcfg_get(name)` helper with
     env-overrides-file priority.
  3. **§24.D** — `RYL_LOG=syslog[:<ident>]` sink with a
     level → syslog-priority mapping.

Why bundled: §24.C's multi-sink fan-out is the load-bearing change;
once tee-to-many works, §24.D (one extra arm) and §24.F (one
indirection layer in front of `getenv`) are each tiny additions.
Splitting them into separate sessions would have meant three rounds
of "rebuild rylween-asan + replay all 39 fixtures + push" for what
amounts to one logical change to the logging surface.

Shape of §24.F (the only piece that touched callsites outside
`log.c`):
  - New `src/runtime_cfg.{c,h}`. Single public function
    `rcfg_get(name)` with this priority: env var (non-empty wins)
    → `$HOME/.rylween/runtime.json5` → `data/runtime.json5`
    (optional, absent from repo) → caller's hard-coded fallback.
  - Cache is a tiny first-writer-wins string table (`RCFG_MAX = 64`
    entries). Personal config loaded first, project-default loaded
    second — keys already in the table are skipped, preserving the
    priority order. Keys are `RYL_*` names; values are JSON5
    scalars (string / int / bool — ints/bools stringified at load).
    Non-scalar values get a one-shot `[WRN][rcfg]` and are skipped.
  - `init_once()` is lazy on first call, registers
    `atexit(rcfg_close)` for LSan cleanliness.
  - `json5_parse_file` is fatal on syntax/IO errors via `res_die`
    — same policy as world.json5 — so a malformed config is loud,
    not silently ignored. `access(path, F_OK)` gates the call so a
    missing file is silently OK.
  - Routed through `rcfg_get`: `RYL_LOG` (`log.c`),
    `RYL_LOG_LEVEL` (`log.c`), `RYL_NO_DELAY` (`compat.c`),
    `RYL_FAKE_EPOCH` (`compat.c`), `RYL_FAKE_SEED` (`compat.c`),
    `RYL_PARTY_MEMBER_MAX` (`res.c`), `RYL_REPLAY_RUMOUR`
    (`towne.c`), `RYL_SNAPSHOT` (`snapshot.c`).
  - **Not** routed through `rcfg_get`: every knob in
    `replay_knobs.c` (`RYL_FORCE_*`, `RYL_STARTING_GOLD`,
    `RYL_FORCE_ENCOUNTER`, etc.). These `unsetenv()` themselves
    on first read and are by nature per-fixture; a personal-config
    default would have terrible-surprise semantics
    ("every run starts with RYL_FORCE_PARTY_LV=10"). If a knob
    graduates from fixture-only to general-purpose, migrate it
    out of `replay_knobs.c` first, then add it to the always-on
    bucket.

Smoke-tested:
  * §24.C end-to-end with `RYL_LOG=stderr,file:smoke.log,
    tcp:<port>` running the title-create-party-5-exit fixture:
    stderr + file each got the same 25 LOG_I lines clean; an
    `nc 127.0.0.1 <port>` client captured the 18 lines emitted
    after it connected (lazy-accept, by design).
  * §24.F with a personal `runtime.json5` setting
    `RYL_LOG: 'file:cfg.log'` + `RYL_LOG_LEVEL: 'info'` and no
    env vars → 25 LOG_I lines into cfg.log under a fresh
    `HOME=$(mktemp -d)`. Same config with env override
    `RYL_LOG=null` → 0 LOG lines anywhere (env beat the config,
    confirming the priority contract).
  * §24.D fan-out with `RYL_LOG=syslog:rylween-test,file:syslog_
    smoke.log`: file got 25 lines clean, stderr stayed silent,
    syslog calls fired (verified by the sink-mask: every line
    that reached the file also went through `syslog(3)`).

State at HEAD:
  - 39 fixtures green, `make check` + `make testasan` clean.
    §23 fully closed; **§24 fully closed** (A0 / A1 / B / C / D /
    E / F all landed).
  - Logging plan has nothing remaining.

**Recommended next session order:**

  1. **§25 — 1-indexed → 0-indexed cleanup** (multi-sess).
  2. **Later:** §24bis (`id: <int>` → slug, deferred until
     after §25), then plans/future-directions.md (multi-party
     MUD work).

Bar per session: `make check` + `make testasan` both green
at every commit. Push at session close.

## Session 2026-04-27 #6 — §25.A.1 (party-member iterator)

First slice of §25.A: introduce a `for_each_party_member(slot)`
macro in `src/party.h` and migrate every `for (i = 1; i <=
res_party_member_max(); i++)` callsite to use it. The body keeps
`slot` 1-based — no semantic change. The macro is the single
point of truth that §25.B's eventual flip to 0-indexed will edit
(plus a fixture re-bless) instead of revisiting two dozen call
sites across nine .c files.

Three commits:

  - `571e47e` — `§25.A.1: for_each_party_member iterator macro`.
    Macro definition (just `for (int slot = 1; slot <=
    res_party_member_max(); slot++)` — calling the cached static
    res_party_member_max getter once per iteration is trivially
    cheap, no need to hoist the bound). 25 callsites migrated:
    party.c x4 (pa_first_alive / pa_count_class /
    pa_count_alive_class / pa_alive_count), subs.c x5 (foodhandler
    mana regen + starve damage + three sleep arms — the outer
    `int32_t i` and `int mm` cache retired), battle.c x4 (post-
    combat exp grant + expcheck level-up tally x3), driv.c x6
    (the_game 'A' show-all + createparty zero-init x5 — the
    outer `int32_t i` retired), dungeon.c x1, ydarg.c x3,
    towne.c x2 (the `y = 0` initializer lifted out of the
    for-init expression), snapshot.c x1, replay_knobs.c x3.
    party.h was added to snapshot.c and replay_knobs.c.

  - `f813e2b` — `§25.A.1 follow-up: collapse same-range loops +
    state abstraction`. Two adjacent cleanups surfaced during code
    review of the iterator landing:
      (1) **Loop-merge.** createparty was running five sequential
          `for_each_party_member(i)` loops over the same 1..N range
          to zero/init `lv` / `ar` / `exp` / `dead` (with `lv = 1`
          written twice — a duplicate the original C had carried
          forward through every refactor). One merged loop now
          handles all four fields. battle.c expcheck dropped its
          zero-init loop in favor of `int32_t add[5] = {0};` —
          the second loop was the only writer. subs.c foodhandler
          factored the three identical sleep-deprivation hp-drain
          loops (-4 / -8 / < -8 hours_rested arms) into a single
          `sleep_deprivation_drain()` helper.
      (2) **State abstraction.** Routed every migrated loop body
          through `pm_*` / `pa_*` instead of reaching back into
          raw `state.p[a].<field>[i]`. createparty now uses
          pm_set_lv / pm_set_ar / pm_set_exp / pm_set_dead.
          replay_knobs.c rk_apply_party_lv / wp / hp_full /
          dead_slots all use pm_set_* / pm_h / pm_dead. towne.c
          enter_ready_prompt uses pm_dead / pm_wp / pm_ar.
          battle.c expcheck reads exp via pm_exp(pm) instead of
          pm.party->exp[pm.m]. The "state owned by party.c, accessed
          via pm_*/pa_* everywhere else" rule is now honored across
          every callsite §25.A.1 touched. Pre-existing raw-state
          callers in inventory.c / primitives.c / missions.c /
          subs.c::scorelist remain queued under the
          `feedback_state_abstraction` memory note — out of scope
          for this session's iterator-axis migration.

  - `8353f16` — `§25.A.1 follow-up: lift hardcoded 15 / 10 in
    driv.c::position_locations`. Same-axis cleanup at the
    constant-bound layer rather than the loop-variable layer. Two
    new public enums in `src/wld.h`: `WLD_LOCATION_SLOT_MAX = 15`
    (total slot count — replaces the zero-init bound and the four
    save/load array sizes) and `WLD_RANDOM_LOCATION_MAX = 9`
    (inclusive upper bound for randomized slots — replaces the
    `for (i = 1; i < 10; i++)` randomize loop). Wld.h's docstring
    now spells out which slots are randomized vs reserved so the
    gap between the two has an explicit semantic. wld.c's
    file-static `WLD_LOCATION_SLOTS = 15` retired in favor of
    the public enum.

State at HEAD (`8353f16`):

  - 39 fixtures green; `make check` + `make testasan` clean at
    every commit.
  - §25.A.1 closed: party-member axis is now centralised behind
    `for_each_party_member`. The four-axis sweep (party → monster
    → npc → town) now has 1 of 4 done.
  - State abstraction inside §25.A.1's blast radius is clean.
    The broader audit (inventory.c x5, primitives.c x7, missions.c
    a few, subs.c::scorelist with hardcoded `[1..4]` fanouts) is
    queued under the existing memory feedback note —
    not blocking §25.A's remaining axes.

**Recommended next session order:**

  1. **§25.A.2 — monster-slot iterator** (~1 session, mechanical).
     Same shape as §25.A.1 but for `for (i = 1; i < noc + 1; i++)`
     loops over `mh[i] / deadc[i]` in battle.c (5 sites) and
     script.c (1 site at line 2976) plus the matching `for_monster`
     DSL op family. Macro lives in battle.h or a new combat.h, takes
     a combat ctx (`for_each_monster(idx, ctx)`) so the body sees
     `idx` 1-based. battle.c::expcheck is unrelated — it iterates
     party members, already done. Smaller blast radius than §25.A.1
     (two .c files vs nine).
  2. **§25.A.3 — NPC-slot iterator** (~1 session). Loops over
     `state.npc[1..res_npc_slot_max()]` in driv.c (3 sites) and
     res.c. Slot 0 is unused. Macro `for_each_npc(slot)` in res.h
     or a new npc.h. Touch is concentrated.
  3. **§25.A.4 — town-slot iterator** (~1 session). The smallest
     axis: `for (i = 1; i <= n_towns; i++)` in res.c / interact.c.
     `towns[0]` is the JSON `null` sentinel left zeroed (the
     §24.A0 setup-logging off-by-one landed precisely because of
     this). Macro `for_each_town(slot)` plus the town-zero-sentinel
     comment.
  4. **§25.B — flip to 0-indexed** (~8-12 sessions). Only after all
     four §25.A axes have iterators in place. Lots of fixture re-
     blesses; each step is one fixture's worth at a time. The
     RYL_FAKE_SEED order has to stay identical, so any draw-loop
     that did `random(1, 4)` becomes `random(0, 3)` — value space
     same, semantic-of-the-number changes. Land as a single
     coherent wave, not a long-tail nibble.
  5. **Later:** §24bis (`id: <int>` → slug, post-§25), then
     `plans/future-directions.md` multi-party MUD work.

Bar per session: `make check` + `make testasan` both green at
every commit. Push at session close.

## Session 2026-04-27 #7 — §25.A.2 + §25.A.3 + §25.A.4 closed

The remaining three §25.A axes — monster slots, NPC slots, town
slots — landed in a single session. Each axis got its own iterator
macro mirroring §25.A.1's `for_each_party_member` shape, with the
slot-bound parameterized where it varies per call (monsters: encounter-
local `noc`) and parameterless where it's a global (npc/town: derived
from data). Per the §25.A.1 precedent, follow-up commits lifted same-
shape hardcoded literal bounds (speak.c `< 40`).

Four commits:

  - `7411209` — `§25.A.2: for_each_monster iterator macro`. Macro in
    src/battle.h takes the `noc` bound as a free arg (encounter-local,
    not a global). 7 callsites migrated: battle.c x5 (encounter mh/
    deadc init, 'A' show-list, alive-check accumulator, monster-nasty
    `while (x < noc + 1)` turn → `for_each_monster(x, noc)` with the
    macro-scoped int x shadowing the outer int32_t x per §25.A.1 — no
    -Wshadow; "still face" survivor count) and script.c x2 (ie_pick-
    _first_alive_monster's `<= noc` form, ask_target monster_alive
    drops a redundant int i). The DSL `for_monster` op family
    (do_for_monster / do_for_each_alive_monster / do_for_range_alive)
    already routes through generic helpers that take the bound — no
    migration needed there. Two `x = 0` accumulators lifted out of
    for-init position to standalone statements.

  - `28fd1d9` — `§25.A.3: for_each_npc iterator macro`. Macro in
    src/res.h next to the existing `res_npc_slot_max()` decl. 5
    callsites migrated: driv.c x3 (the_game's hut/cottage map seed,
    init's party-shift visited-grid migration, write_current_world's
    persistence writer); res.c x1 (realize_npcs's visited[] zero-out;
    the local `slot_max` cache stays — still needed by the random-
    placement bin draw and the `slot < 1 || slot > slot_max` guards);
    towne.c::hut x1 (NPC-at-tile probe; outer `int32_t i` and `int
    nmax = res_npc_slot_max()` retired together). The save/load
    formal-zero loops (writer 0..slot_max, reader capped at slot_max
    + 1) keep their explicit shape — those are file-format walks, not
    slot iters.

  - `e825dc2` — `§25.A.3 follow-up: lift speak.c hardcoded `< 40``.
    Same-shape literal-bound cleanup at the loop-variable layer
    (precedent: §25.A.1's commit 8353f16 lifting 15/10 in driv.c::-
    position_locations). speak.c::speak's two NPC-slot loops (the
    "list NPCs in a town building" picker and the wilderness "who's
    on this tile" probe) used `i < 40` — half of state.npc[]'s 41-
    slot capacity. Bound has been semantically equivalent to "every
    realized NPC" since §15 step 3 (slots beyond res_npc_slot_max()
    are zero-init by static and never realized; both loop predicates
    require non-zero towne/building or a non-zero party position to
    match). Replaced both with `for_each_npc(i)` — same semantics in
    today's data, single source of truth for the iteration bound.
    Three accumulator inits (`j = 1, x = 0` picker, `talk = 0`
    probe) lifted out of for-init; outer `int32_t i` retired.

  - `5dcbe74` — `§25.A.4: for_each_town iterator macro`. Macro in
    src/res.h plus a public `res_town_count()` getter (file-static
    `n_towns` now externally visible — required so the macro's
    expansion can access the bound from any caller). One callsite
    migrated: `res_town_at`'s linear scan, which was the last
    `for (int i = 1; i <= 4; i++)` over `towns[i]` in the codebase —
    the hardcoded `4` retired in favor of n_towns. The `twn > 4`
    guards on `res_town_name` / `_x` / `_y` / `_tax` / `_has_church`
    keep the hardcoded 4: those mirror the towns[5] static-array
    capacity, not the iteration bound, and lifting them requires
    expanding the realm past four towns (a structural change). Doc
    note clarifies the towns[0] = JSON-null = wilderness convention
    and the link back to the §24.A0 setup-logging off-by-one (which
    landed precisely because of the bin-0-is-wilderness invariant).

State at HEAD (`5dcbe74`):

  - 39 fixtures green; `make check` + `make testasan` clean at every
    commit.
  - §25.A closed end-to-end. All four iteration axes (party member /
    monster / npc / town) are now centralised behind their respective
    `for_each_*` macros — single point of truth that §25.B will edit
    when the codebase flips to 0-indexed.
  - State abstraction inside §25.A's blast radius is clean across
    every axis (party in §25.A.1, monsters touch only encounter-
    local arrays not state, NPCs route through `state.npc[i]` reads
    that already use struct-direct access, towns route through
    `towns[i]` which is the file-static state). The broader audit
    queued under `feedback_state_abstraction` (inventory.c x5,
    primitives.c x7, missions.c MB_PLAYER_NAME, subs.c::scorelist
    `[1..4]` fanouts, towne.c::enter_tax_preamble) remains the only
    pending state-abstraction work; not blocking §25.B.

**Recommended next session order:**

  1. **§25.B — flip to 0-indexed** (~8-12 sessions). Now unblocked.
     The macros at party.h::for_each_party_member, battle.h::for_each-
     _monster, res.h::for_each_npc, res.h::for_each_town are the four
     edit points; flipping each from `for (int slot = 1; slot <= N;
     slot++)` to `for (int slot = 0; slot < N; slot++)` plus a
     fixture re-bless wave is the bulk of the work. Constraints:
       - The RYL_FAKE_SEED order has to stay identical — any draw-loop
         that did `random(1, 4)` becomes `random(0, 3)`; value space
         same, semantic-of-the-number changes.
       - towns[0] / state.npc[0] / monster slot 0 / party slot 0
         interpretations all change — towns[0] becomes a real town
         (not the wilderness sentinel); the wilderness bin in
         res_realize_npcs has to use a separate sentinel value
         (-1 or a parallel flag) instead of overloading slot 0.
         res_town_at / res_npc_sym / npc_at_slot bounds shift.
       - Save/load fixtures: every fixture that emits npcs[].id /
         visited_npcs[i] / etc. needs a re-bless. Bench with the
         smallest fixture first (towne-thord-inn or similar) before
         the wave.
     Land as a single coherent wave (one PR or commit chain), not a
     long-tail nibble — the half-flipped state is hard to reason
     about and fixture re-blesses are wasted if a later step
     reverses something.

  2. **State-abstraction sweep** outside §25.A's blast radius. The
     `feedback_state_abstraction` memory note tracks this; the next
     time someone touches one of these files, route the new code
     through `pm_*` / `pa_*` / `inv_*` / `res_*` helpers and leave
     the untouched callsites alone (no speculative refactors).
     Survey: inventory.c x5, primitives.c x7, subs.c::scorelist
     (~10 raw `state.p[i].{exp,lv,kills,name}[1..4]` reads with
     hardcoded 1..4 fanouts — the §25.A.1 #6 footer's call-out),
     towne.c::enter_tax_preamble, missions.c MB_PLAYER_NAME binder.
     Add helpers as needed; don't grow a parallel `pa_*_at(int slot,
     ...)` family unless a test-infra caller forces it (replay-
     _knobs.c::rk_apply_pos / rk_apply_ydarg_grants are the only
     current candidates).

  3. **Later:** §24bis (`id: <int>` → slug, post-§25), then
     `plans/future-directions.md` multi-party MUD work.

Bar per session: `make check` + `make testasan` both green at every
commit. Push at session close.

## Session 2026-04-28 #5 closure + continuation prompt

Ten commits on `lagergren/port1` (`0d075c3..877ded4`), all pushed to
origin. 39 fixtures byte-identical on `make check` + `make testasan` at
every commit; no `.expect_snapshot` rebless required.

What landed (in commit order):

  - `0d075c3` state.npc[] — adds `npc_record_at(slot)` in state.h;
    driv.c's four save/load sites (write_current_world emit + visited
    writer; read_current_world populator + visited reader) lift to
    npc_record_at + the existing npc_visited / npc_visited_set
    helpers.

  - `26941a2` state.map[][] — adds wld_rows/wld_cols/wld_set_tile_at/
    wld_load_row/wld_last_terrain to wld.c/h. Lifts driv.c (NPC hut
    stamp + map.dat row loader), subs.c (check_terrain + 5x5
    update_map blit), script.c (movement mountain-block test, four
    directions), snapshot.c (map emit), missions.c (MG_TERRAIN guard).
    Snapshot byte-identity preserved — `%02d` vs `%02zu` emit
    identical bytes for 0..41.

  - `a4bb984` state.cpl + state.num_parties — adds
    party_current_slot / party_set_current_slot / party_count /
    party_count_inc / party_count_dec / party_set_count inline in
    state.h alongside party_current(). Lifts driv.c (26 sites:
    asserts, login matching, createparty, terminate's free + memmove +
    clear-tail block, write/read_current_world), subs.c::scorelist
    (4 sites), snapshot.c (3 cpl emit sites), speak.c (npc_visited
    party-slot args), battle.c (encounter assert), script.c (`{cpl}`
    DSL binding).

  - `c55888d` global session scalars — adds session_player_name /
    session_last_played_day(+set) / session_day_at_game_start(+set) /
    session_banished(+set) / session_hour(+set) / session_current_town
    (+set) inline helpers in state.h, plus wld_locations_dx/dy array
    handles for save/load. Lifts driv.c (login menu owner, entry.dat
    dedup + append, sysop/createparty player init, hour=5 init, all
    last_played_day + day_at_game_start sites, dx/dy save+load),
    dungeon.c (banished read+reset), primitives.c (sysop name compare),
    script.c ({twn / hour_of_day / state_banished} bindings,
    LV_HOUR_OF_DAY / LV_STATE_BANISHED mutate paths), snapshot.c
    (twn / hour / banished / last_played_day emit), subs.c (sysop
    name compare).

  - `c411fdc` retire getclk + kl[N] — drops the legacy Amiga
    `getclk(char kl[8])` + opaque kl[1]/kl[2]/kl[3] indexing. Adds
    `struct wall_clock { year, month, day, hour, minute, second,
    weekday }` + `clock_now(out)` for atomic snapshots and
    `clock_year` / `clock_month` / `clock_day` / `clock_hour` /
    `clock_minute` shortcuts. clock_year() returns the full 4-digit
    year; the kl[1] year-1980 encoding is gone. Migrates driv.c
    (4 day-check sites — local `char kl[8]` arrays drop from `init`
    and `save`), log.c (audit timestamp uses clock_now() into struct
    for atomicity), ydarg.c (heroes-file inscription, preserves the
    legacy "19%d" + (year-1900) Y2K cosmetic format verbatim with a
    comment pointing at compat.h).

  - `d8db513` driv.c::terminate visited-grid shift bug — the inner
    `for (j=1; j<num_parties; j++) { npc_visited_set(n, x,
    npc_visited(n, x+1)); }` body never referenced `j`, so only
    `visited[x]` was overwritten — `visited[x+1..count]` still
    pointed at their pre-shift owners. Fix: `for (j=x;
    j<party_count(); j++) { npc_visited_set(n, j, npc_visited(n,
    j+1)); }` — proper compaction. After party_count_dec the count
    is the new (smaller) value; reading j+1 up to the old tail
    reads the about-to-be-discarded data correctly. Fixture-
    unreachable today (no fixture exercises terminate-realm-then-
    keep-playing).

  - `5493dff` towne.c::enter `state.twn == 2` city/village fork —
    lifted to per-town data. Each towns[] record in
    `data/world.json5` now carries `class: 'city' | 'village'`
    (default 'village' if absent). New helper `res_town_is_city(int
    twn)` in res.c/h. towne.c::enter swaps the hardcoded `state.twn
    == 2` to `res_town_is_city(state.twn)`. Adding a fifth town as
    a city is now a pure data edit.

  - `b08978d` §24bis phase 1 — slug-form sym accessors. Adds
    `res_enum_sym(id, index)` (returns enums[i].keys[idx] — the
    lowercase key — for keyed-record enums; falls back to display
    string for positional arrays). Paired helpers in state.h:
    `pm_class_sym` / `pm_race_sym` / `pm_class_is_sym` /
    `pm_race_is_sym` (per-member); `npc_job_sym` / `npc_job_is_sym`
    (per-NPC). Paired helpers in party.c/h: `pa_class_sym(m)` /
    `pa_race_sym(m)` / `pa_class_is_sym(m, sym)` / `pa_race_is_sym(m,
    sym)` (current-party). Storage stays int — helpers translate at
    the boundary.

  - `f21f7af` §24bis phase 2 — speak.c npc_job slug compares. The two
    `npc_job(talker) == res_enum_id("job", "bard"|"bargirl")` sites
    in speak.c::speak Q-prompt dispatch swap to
    `npc_job_is_sym(talker, "<sym>")` — direct enum-key compare, no
    res_enum_id lookup per dispatch. Phase 2 sweep verified ~complete:
    only 13 PC_* references remain (compat.c name→id mapper,
    party.c::pa_is_spellcaster, res.c spell-book / loader) — all in
    translator/loader code where the int form is correct. The plan's
    "driv.c, battle.c, subs.c are full of these" estimate was stale;
    those moved to data-driven dispatch in the §22 / §23 sweeps.

  - `877ded4` plan refresh — top-of-queue snapshot to 2026-04-28 #5,
    mechanical residuals queue empty, recommended-next-session list
    rewritten to point at §12.1 / §24bis phase 3 / §12.3 / Ydarg.

State at end of session. **State-abstraction sweep total**: every
top-level `struct ryl_state` field (p[], npc[], map[][], cpl,
num_parties, global_name, last_played_day, day_at_game_start,
banished, hour_of_day, twn, dx[], dy[], last_terrain) is accessed
exclusively through helpers from outside its abstraction-home file.
Abstraction-home files retaining direct `state.X` access by design:
party.c (state.p[], state.cpl), inventory.c (state.p[].inventory),
ryl.c (state.global_name init from argv), towne.c (state.twn
writes on enter/leave), wld.c (state.dx/dy/map/last_terrain).

§24bis phase 1 closed; phase 2 ~complete; phase 3 (drop the int from
struct party / state.npc storage) remains a coordinated multi-session
refactor — but now genuinely unblocked. Memory note
`feedback_state_abstraction.md` was updated to reflect the total
closure + the §24bis phase-1 helpers landing.

Continuation prompt for next session.

  Ten new commits on `lagergren/port1` (`0d075c3..877ded4`), all
  pushed to origin. 39 fixtures byte-identical on `make check` +
  `make testasan` at every commit. State-abstraction sweep is now
  total across every top-level `struct ryl_state` field — every
  consumer outside the abstraction-home files routes through
  helpers. §24bis phase 1 (slug-form sym accessors) landed; phase 2
  partially closed; phase 3 (storage flip) remains unblocked but
  multi-session.

  Recommended next session:

    - **§12.1 — Dockerfile + GitHub CI**. The clearest concrete next
      item. ~1 session, no code changes blocking it. Plan §12.1
      describes the multi-stage Dockerfile (debian:stable-slim
      builder → tester running tools/replay.sh --all +
      tools/check_world.sh → slim runtime) and the
      `.github/workflows/ci.yml` shape (push + PR trigger,
      ubuntu-latest with gcc + clang, `make test` + `make testasan`
      + `make format` as gates). Verify both legs locally before
      committing the workflow file.

    - **§24bis phase 3 — drop the int from struct party / state.npc
      storage**. Genuinely unblocked but 2-3 sessions because
      save-format JSON5 emit + read paths flip together. Plan
      §24bis sketches three sub-phases.

    - **§12.3 — dynamic sizing from world.json5 (`limits` subtree)**.
      Remaining static caps `state.p[26]`, `map[42][82]`,
      party-inventory `[17]`, `attack_today[26]`. Save-format
      license already granted (2026-04-22).

    - **ydarg inscription Y2K cosmetic** (mechanical residual). "%d"
      + `clock_year()` instead of "19%d" + `(year-1900)`; needs a
      ydarg-confrontation fixture rebless.

    - **Ydarg as a regular mission** — still blocked on the DSL
      evaluator learning mission triggers + item-flag paths.

  Memory notes worth re-reading: `feedback_state_abstraction.md`
  (total closure + §24bis phase 1 helpers; the rule for new code is
  pa_* / pm_* / inv_* / res_* / npc_* / session_* / wld_*, and the
  *_sym variants for class/race/job comparisons),
  `feedback_preexisting_bug_fixes.md`,
  `feedback_replay_knobs_centralized.md`,
  `feedback_script_text_wiring.md`. The cap-driven heap-allocation
  principle from §25.B still applies for §12.3's remaining targets.

  Bar per session: `make check` + `make testasan` both green at
  every commit. Push only on explicit user approval (per AGENTS.md).

## Session 2026-04-28 #6 closure + continuation prompt

Six commits on `lagergren/port1` (`d35f663..ff6195b`). 39 fixtures
byte-identical on `make check` + `make testasan` at every commit; no
`.expect_snapshot` rebless required. **§12.1 (Dockerfile + GitHub
CI) closes end-to-end** — body relocated to `port-plan-complete.md`
"§12.1 closure" section.

What landed (in commit order):

  - `d35f663` **style: clang-format -i sweep + `.clang-format`
    compat for clang-format 19**. The format-gate at HEAD was
    silently broken pre-§12.1 — `make format` had been wired up
    but never run, and column-alignment violations had crept in
    across 11 source files. `.clang-format` flips `ReflowComments:
    Never` (the new enum form, accepted only by clang-format 22+)
    back to `ReflowComments: false` (a documented alias for `Never`
    in clang-format 22+; the bool form every clang-format major
    back to 18 accepts). Bulk `clang-format -i src/*.c src/*.h`:
    12 files, 114+/103- whitespace-only. Mirrors historical 33ffc52
    in shape; recorded in `.git-blame-ignore-revs` (`405f398`).

  - `bb453c7` **build: `-D_DEFAULT_SOURCE` so the Linux glibc build
    sees POSIX extensions**. `-std=c99` on glibc hides `strdup`,
    `usleep`/`useconds_t`, `sigaction`/`SIGWINCH`, etc. Apple-libc
    exposes them unconditionally, masking the gap. Added to
    `CPPFLAGS`; `$(BIN_ASAN)` recipe also gained `$(CPPFLAGS)`;
    `io_tty.c` got an explicit `#include <signal.h>` for what was
    previously a transitive include via `<termios.h>` that doesn't
    survive the strict-mode toggle.

  - `6d391c7` **ci: §12.1 Dockerfile + GitHub Actions workflow**.
    Multi-stage `builder` → `tester` → `runtime` Dockerfile;
    `tester` runs `make test` + `make testasan` + `make format` as
    separate `RUN` steps. `.dockerignore` covers build artefacts +
    runtime data. `.github/workflows/ci.yml` has `linux` (ubuntu-
    latest, gcc+clang matrix, full gate — authoritative leak gate
    via LSan), `format` (clang-format dry-run), `macos` (build +
    replay + ASan as scorecard).

  - `8397247` **ci: pin LLVM 22 from upstream apt repo (matches
    macOS brew)**. Initial commit defaulted to debian:stable's apt
    clang-format-19 — three minors behind brew's clang-format 22.
    Both Dockerfile + workflow now install LLVM 22 via `llvm.sh`
    from `apt.llvm.org`. `LLVM_VERSION` is a build-arg / workflow
    env var; bump in lock-step with brew's current stable.

  - `405f398` **chore: record d35f663 in `.git-blame-ignore-revs`**.

  - `ff6195b` **ydarg: fix Y2K cosmetic in heroes-file inscription**.
    `"19%d" + (c.year - 1900)` → `"%d", c.year` (clock_year()
    returns the full 4-digit year per compat.h). Originally
    documented as "needs ydarg-confrontation fixture rebless," but
    the rebless is unnecessary: the fixture's env sidecar
    explicitly notes the heroes-file write is left for a follow-up,
    and grep confirms no fixture exercises this code path.

State at end of session. **§12.1 closed end-to-end** (§12 status
flips from "in-progress" to "partial" in the master TOC since
§12.2/§12.3 remain). **Ydarg Y2K cosmetic closed**. **Ydarg-as-
mission investigated** and confirmed NOT cheaply unblockable —
three structural gaps documented in the queue list above. The
moderate-path scope (mission framework + C-fn callback + compound
guard kind) is sketched as a single-session item in "Recommended
next session"; the full DSL lift remains 3+ sessions.

Continuation prompt for next session.

  Six new commits on `lagergren/port1` (`d35f663..ff6195b`),
  pushed to origin. HEAD is the Y2K cosmetic fix (`ff6195b`); the
  prior five commits close §12.1 (Dockerfile + GitHub Actions)
  end-to-end with two parallel cleanup commits required to ship
  (clang-format -i sweep + `-D_DEFAULT_SOURCE` for Linux glibc).
  39 fixtures byte-identical on `make check` + `make testasan` at
  every commit; the new format gate (`make format`) is also green
  on Linux clang-format 22 inside the tester image.

  State at end of session. §12.1 (Dockerfile + GitHub CI) closes
  end-to-end; §12 status in the master TOC flips to "partial"
  (§12.1 + §12.4 closed; §12.2 + §12.3 still open). Ydarg Y2K
  cosmetic closed (`ff6195b`). Investigated Ydarg-as-mission and
  confirmed it's NOT cheaply unblockable — three structural gaps
  scoped (see "Deferred" / "Recommended next session" in
  `port-plan-incomplete.md`). State-abstraction sweep (total in
  2026-04-28 #5) + §24bis phases 1/2 stand. Mechanical residuals
  queue empty.

  Recommended next session.

    - **§24bis phase 3 — drop the int from struct party /
      state.npc storage**. The highest-leverage open item. 2-3
      sessions because save-format JSON5 emit + read paths flip
      together. Phase 1's `pm_class_sym` / `pa_class_sym` /
      `npc_job_sym` slug accessors already isolate the read paths,
      so the cutover is contained. Per-axis incremental landing
      (cl first, then ra, then job) is supported by phase 1.

    - **§12.3 — dynamic sizing from world.json5 (`limits`
      subtree)**. Reasonable to land before §24bis phase 3 since
      both flip the save format and doing them together avoids a
      double rebless of the format-version gate. Remaining static
      caps `state.p[26]`, `map[42][82]`, party-inventory `[17]`,
      `attack_today[26]`.

    - **Ydarg-as-mission — moderate-path single-session shape**.
      Add `mission_action_kind` enum (MA_SCRIPT / MA_C_FN) to
      `mission_trigger`; add a compound MG_TILE_AT_POS guard kind;
      move the (14,30, tile '2') trigger from `towne.c::enter()`
      into the mission table with `fn = ydarg`. Sidesteps the DSL
      lift + pa_special predicate work the original blocker called
      out. The full DSL lift of ydarg.c (200+ LOC of multi-floor
      navigation) remains 3+ sessions.

  Reading order in repo. `port-plan-incomplete.md` "Top of queue
  (status snapshot, 2026-04-28 #6)" + the trailing "Session
  2026-04-28 #6 closure" section have the full per-commit detail.
  `port-plan-complete.md` "§12.1 closure (2026-04-28 #6)" has the
  full Dockerfile / CI shape for reference.

  Memory notes worth re-reading: `feedback_state_abstraction.md`
  (total closure + §24bis phase 1 helpers; the rule for new code is
  pa_*/pm_*/inv_*/res_*/npc_*/session_*/wld_*, plus *_sym variants
  for class/race/job comparisons); `feedback_preexisting_bug_fixes.md`;
  `feedback_replay_knobs_centralized.md`;
  `feedback_script_text_wiring.md`. The cap-driven heap-allocation
  principle from §25.B still applies for §12.3's remaining targets.

  Bar per session. `make check` + `make testasan` + `make format`
  all green at every commit (the format gate is now wired into
  CI). Push only on explicit user approval (per AGENTS.md).
