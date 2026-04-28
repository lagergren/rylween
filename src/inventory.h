#ifndef INVENTORY_H__
#define INVENTORY_H__

#include "state.h" /* pm_t */

/* Party-inventory helpers. state.p[state.cpl].inventory[] is
   1-indexed with size INVENTORY_SLOT_MAX+1 (state.h); every helper
   here iterates slot 1..res_inventory_slot_max() — slot 0 is
   reserved. Single point of truth for "what is 17" iteration so
   lowering the cap via limits.inventory_slot_max is a one-line
   change.

   All helpers operate on the current party (state.cpl). Non-cpl
   callers (only driv.c createparty's init) use memset on
   state.p[a].inventory directly; the helpers aren't generic by
   design — state.cpl is the overwhelmingly common case and
   threading a party index through every read site dilutes the
   point of the abstraction.

   Special items (silverkey / fireshield / bracelets / mastercrown /
   jazzyfoot) are NOT inventory slots; they live as flags on struct
   party and never interact with this module. */

/* First empty slot (inventory[s] == 0), or 0 if full. */
int inv_find_empty(void);

/* First slot holding the given item id, or 0 if not carried. */
int inv_find_item(int id);

/* First slot whose item id falls in [lo, hi] inclusive, or 0 if
   none. Early-exit variant of inv_count_in_range for "does the party
   carry any weapon?" / "any armor?" style gating. */
int inv_find_in_range(int lo, int hi);

/* Count items whose id is in [lo, hi] inclusive. Used by mission
   completion checks that tally a tagged category (e.g. gems
   39..41). */
int inv_count_in_range(int lo, int hi);

/* Find the first empty slot and place `id` there; return that
   slot, or 0 if the inventory is full. Callers that need the
   drop-on-full fallback use the DSL `reward` op; this helper is
   for C paths that silently skip on full or handle the refusal
   themselves. */
int inv_add(int id);

/* Flag-backed party specials. The five quest items live as
   unsigned-char flags on struct party rather than as inventory
   slots (state.h). PS_NONE (0) is the "not a special" sentinel
   returned by item_is_special; PS_LAST is a one-past-the-last
   sentinel for init-time coverage checks. The id->PS_* map is
   built lazily from data/world/items/ on first use. */
enum party_special {
    PS_NONE = 0,
    PS_FIRESHIELD,
    PS_BRACELETS,
    PS_MASTERCROWN,
    PS_JAZZYFOOT,
    PS_SILVERKEY,
    PS_LAST,
};

/* Build the id->PS_* map from the world tree. Idempotent. Must run
   after res_load_world; call eagerly from boot (ryl.c main,
   --check-world) so data errors surface before gameplay. Dies if
   any PS_* value is not covered by a `special:` tag, or if a tag
   string is unknown. */
void inv_init(void);

/* If `id` is one of the flag-backed specials, stores the matching
   PS_* in *which and returns 1; otherwise returns 0 and does not
   touch *which. `which` may be NULL. Runs inv_init on first call
   if it hasn't already. */
int item_is_special(int id, int *which);

/* Map a special-tag string ("fireshield", "bracelets", "mastercrown",
   "jazzyfoot", "silverkey") to its PS_* value. Returns PS_NONE for
   unknown tags. Used by data-driven dispatchers (ydarg.stairs item
   trade) that author the tag in JSON5 instead of the int. */
int inv_special_id_for_tag(const char *tag);

/* Item-kind predicates. Each asks res_item_kind(id) and returns
   non-zero when the id was loaded from the matching shard under
   data/world/items/. `id == 0` is the empty-slot sentinel and
   returns 0 for every predicate. Use these everywhere you would
   otherwise hard-code an id range — `id < 20` for "weapon?",
   `id >= 20 && id <= 29` for "armor?", etc. New shards pick up
   a predicate just by adding the helper and pointing it at the
   shard name; the data table owns the membership. */
int item_is_weapon(int id);
int item_is_armor(int id);
int item_is_gear(int id);
int item_is_potion(int id);
int item_is_treasure(int id);
int item_is_magic_item(int id);

/* Set the flag named by `which` (a PS_* value) to 1 on the
   current party. PS_NONE is a no-op. Used by the DSL reward op to
   short-circuit inv_add for specials. */
void party_award_special(int which);

/* §20 S4: dispatch a potion's data-driven effect onto member `pm`.
   Looks up the item record by `item_id`, branches on
   res_item(id)->effect_kind, emits the per-kind narration, and
   applies the per-kind state mutation (heal / poison adjust hp,
   restore_mana sets ma to wi for spellcasters). Items without an
   effect: spec are a no-op. The function owns the surrounding
   ryl_pause / clw choreography so the caller's switch goes away.

   Pattern is intentionally pre-DSL: the kind strings are matched
   in C, narration is hardcoded per kind. L3 (battle item-effect
   switch) extends this scaffold; eventually the per-kind switch
   here drains into a DSL effect-spec resolver. */
void apply_potion(int item_id, pm_t pm);

#endif
