#ifndef SPEAK_H__
#define SPEAK_H__

#include <stdint.h>

/* Dialogue with NPCs + the rumour/chitchat flag machinery. */

void speak(int32_t, int32_t);
void reply_chitchat(int32_t, int32_t);
void flaggkoll(int32_t);

#endif
