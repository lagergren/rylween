/****************************************************************************/
/* XENOLINK Developer's Toolkit Release 7 - Compatible with 1.90 and above  */
/*                                                                          */
/*                                                                          */
/*                    #### ADVANCED LEVEL EXAMPLE ####                      */
/*                  Produces smaller code than HelloWorld.c                 */
/*                                                                          */
/*                                                                          */
/* HELLO.C - Hello World CLI door (AmigaDOS version)                        */
/*           This version does not use C language i/o functions             */
/*                                                                          */
/*           Ensure that you link with SPRINTF.O, otherwise this program    */
/*           will crash; the myvsprintf() function in SPRINTF.O is NOT      */
/*           compatible with compiler versions.                             */
/*                                                                          */
/****************************************************************************/

#include <exec/types.h>
#include <exec/memory.h>
#include <exec/semaphores.h>
#include <libraries/dos.h>
#include <proto/dos.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include "XDOOR:xenolink.h"


/* Function prototypes */
void ExitRoutine();
void Hello(UBYTE *UserName);

/* Exit function (SAS/C) */
extern void *_ONEXIT;

/* Global variables */
UBYTE HaveSetRawMode = 0;


/* Print output buffer; don't put large print buffers (e.g. 1K+) on the stack */
/* inside the qprintf() function, otherwise you'll overflow the stack */
/* Keeping the print buffer as a global variable is much safer */
UBYTE PrintBuffer[512];

void qprintf(UBYTE *fmt, ...)
{
   /* Our own myvsprintf() in SPRINTF.O */
   myvsprintf(PrintBuffer, &fmt);

   /* AmigaDOS output function */
   Write(Output(), PrintBuffer, strlen(PrintBuffer));
}


/* Reroute all functions calling printf() to call our own qprintf() */
#define printf qprintf


main(int argc, char **argv)
{
   if (argc < 2)
   {
      printf("\nSyntax: HELLO <UserName>\n\n");
      exit(1);
   }

   /* (SAS/C) */
   /* Ensure that ExitRoutine() is called when the program exits. */
   /* We must make sure that this program does NOT leave the CLI */
   /* in RAW mode when it exits! */
   _ONEXIT = ExitRoutine;

   /* Do NOT flush C input handle, since we're not using C functions */

   /* Set AmigaDOS raw mode */
   HaveSetRawMode = 1;
   SetMode(Input(), 1);

   /* Run program */
   Hello(argv[1]);

   /* The exit function ExitRoutine() will unset raw mode */
   exit(0);
}


void Hello(UBYTE *UserName)
{
   UBYTE KeyPress;

   printf("Hello, %s!\n\n", UserName);

   printf("Here is a demonstration of raw console i/o.\n");
   printf("Press a key: ");

   /* Get hotkeyed input from AmigaDOS input handle */
   Read(Input(), &KeyPress, 1);

   /* In this case, the character is NOT echoed to the screen, so we */
   /* must echo it ourselves */
   Write(Output(), &KeyPress, 1);

   /* We could have used printf("%lc", KeyPress) instead */

   /* Notice that we use %lc instead of %c, since our formatting routine */
   /* calls Exec's RawDoFmt() function */
   printf("\n\nYou pressed '%lc'\n\n", KeyPress);
}


void ExitRoutine()
{
   /* Unset raw mode if we set it */
   if (HaveSetRawMode)
   {
      SetMode(Input(), 0);
   }
}
