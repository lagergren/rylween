#ifndef TOWNE_H__
#define TOWNE_H__

#include <stdint.h>

/* Town buildings + wilderness huts, plus the inventory lookup helpers
   (object / obcost / obdamage) shared across buy/sell/equip flows. */

void enter(void);
void rumour(void);
char *object(int32_t);
int32_t obdamage(int32_t);

#endif
