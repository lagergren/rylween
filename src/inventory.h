#ifndef INVENTORY_H__
#define INVENTORY_H__

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
   carry any weapon?" / "any armour?" style gating. */
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

#endif
