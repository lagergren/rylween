/****************************************************************************/
/* XENOLINK Developer's Toolkit Release 7 - Compatible with 1.90 and above  */
/*                                                                          */
/* HELLO.C - Hello World CLI door                                           */
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

   /* Flush C input handle */
   fflush(stdin);

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

   /* Get hotkeyed input from standard C input */
   KeyPress = getc(stdin);

   printf("\n\nYou pressed '%c'\n\n", KeyPress);
}


void ExitRoutine()
{
   /* Unset raw mode if we set it */
   if (HaveSetRawMode)
   {
      SetMode(Input(), 0);
   }
}
