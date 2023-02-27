/****************************************************************************/
/* XENOLINK Developer's Toolkit Release 7 - Compatible with 1.90 and above  */
/*                                                                          */
/* NIM.C - Simple game demonstrating i/o                                    */
/*                                                                          */
/****************************************************************************/

#include <exec/types.h>
#include <exec/ports.h>
#include <exec/semaphores.h>
#include <libraries/dos.h>
#include <proto/intuition.h>
#include <proto/exec.h>
#include <time.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>

#include "XDOOR:xenolink.h"


/* Use function pointer #defines */
#define DOOR_FUNCTION_MACROS
#include "XDOOR:doorlib.h"

#include "XDOOR:doorcode.h"


long e, f, m, l, NumCookies, h, q, k;


void Title(void);
void Instructions(void);
void CookiesLeft(void);
void DisplayCookies(void);
void Game(void);


main(int argc, char **argv)
{
   if (argc < 2)
   {
      exit(1);
   }

   _ONEXIT = ExitRoutine;

   /* Get address of Xenolink callback functions, and use this to get */
   /* the address of the callback data */
   XCB  = (struct XenolinkCallBack *) atol(argv[1]);
   XCBD = XCB->XCBD;

   InitLibraryBases();

   Title();
   Instructions();
   Game();

   exit(0);
}


void Title(void)
{
   wprintf("The Game of NIM - Xenolink implementation by Jonathan Forbes, 1991.\r\n\r\n");
}


void Instructions(void)
{
   wprintf("Nim is a traditional game which has many versions.  The object of the game,\r\n");
   wprintf("however, is always the same -namely, not to be forced to take the last turn.\r\n");
   wprintf("In this version, the random number generator has baked an ovenful of cookies\r\n");
   wprintf("which you and the Amiga settle down to scoff...  A limit is set on the number\r\n");
   wprintf("which can be eaten at a time, and the loser is the one who goes last; that is,\r\n");
   wprintf("who eats the last cookie.\r\n\r\n");

   wprintf("Press any key to continue...\r\n");
   GetCharacter();
}


void CookiesLeft(void)
{
   wprintf("\r\nThere are %d cookies left\r\n", NumCookies);
   wprintf("The most you can take is: %d\r\n", h);
}


void DisplayCookies(void)
{
   UBYTE PrintedReturn;

   for (k = 1; k <= NumCookies; k++)
   {
      PrintedReturn = 0;

      wprintf("* ");

      if (rand() % 3 == 1)
      {
         wprintf("\r\n");
         PrintedReturn = 1;
      }
   }

   if (!PrintedReturn)
   {
      wprintf("\r\n\r\n");
   }
   else
   {
      wprintf("\r\n");
   }
}


void Game(void)
{
   long  Clock[2];
   UBYTE Line[3];

   e = f = m = l = 0;

   CurrentTime(&Clock[0], &Clock[1]);
   srand(Clock[1] + Clock[0]);

   NumCookies = (rand() % 20) + 10;
   h = (rand() % 3) + 3;

   while (1)
   {
      CookiesLeft();

      wprintf("\r\n");

      DisplayCookies();

      do
      {
         wprintf("How many will you take? ");

         GetLine(Line, 2, 0, GL_APPEND_NEW_LINE);

         if (!XCBD->CarrierPresent)
         {
            exit(1);
         }

         e = atoi(Line);

         if (e >= 48)
         {
            e -= 48;
         }

         wprintf("\r\n");

      } while (e < 1 || e > h || e > NumCookies);

      NumCookies -= e;

      if (!NumCookies)
      {
         wprintf("I win!\r\n\r\n");
         exit(0);
      }

      do
      {
         if (!(q = NumCookies - 1 - ((NumCookies-1)/(h+1))*(h+1)))
         {
            q = (rand() % h) + 1;
         }
      } while (q > NumCookies || q > h || q < 1);

      wprintf("You took %d, I took %d\r\n", e, q);

      if (!(NumCookies = NumCookies - q))
      {
         wprintf("You actually won!  You must know the algorithm!\r\n\r\n");
         exit(0);
      }
   }
}
