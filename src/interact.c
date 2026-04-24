#include "ryl.h"
#include "interact.h"
#include "primitives.h"

void check_terrain(char old, char new) {
    if (old == new) {
        return;
    }
    if (!ryl_chance(9)) {
        return;
    }

    clw();

    switch (new) {
        case 's':
            tx("The rich green plainland\r\n");
            tx("of Rylween stretches far\r\n");
            tx("in all the directions.\r\n");
            break;
        case '1':
            tx("You have found a hut.\r\n");
            break;
        case '0':
            tx("You are close to a towne.\r\n");
            break;
        case 'f':
            tx("Small foothills make your\r\n");
            tx("progress more arduous.\r\n");
            break;
        case 'd':
            tx("You are in a hot and dry\r\n");
            tx("desert. The sun fries\r\n");
            tx("you mercilessly.\r\n");
            break;
        case 't':
            tx("You are in the swampy\r\n");
            tx("marshlands of Rylween.\r\n");
            tx("Bubbles of gas rise to\r\n");
            tx("the surface where they\r\n");
            tx("burst and emit a foul\r\n");
            tx("smelling odour.\r\n");
            break;
        case 'g':
            tx("The ancient forest is\r\n");
            tx("very tranquil. A soft\r\n");
            tx("breeze rustles the\r\n");
            tx("leaves of mighty oaks.\r\n");
            break;
        case '2':
            tx("You stand outside the\r\n");
            tx("castle Zadraz. The\r\n");
            tx("black steel building\r\n");
            tx("seems to want to swallow\r\n");
            tx("you whole.\r\n");
            break;
    }
    tx("\r\n");
}
