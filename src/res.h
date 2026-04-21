#ifndef RES_H__
#define RES_H__

/* Flat JSONL resource loader. Records are {"kind":"...","id":"...",...}
   one per line; unknown fields are tolerated. Indexed linearly — fine
   at the scale of a few hundred records. */

void res_load(const char *path);

/* Returns values[index] for the enum record with matching id, or "" if
   the record is missing or index is out of range. Never returns NULL. */
const char *res_enum(const char *id, int index);

#endif
