/****************************************************************************/
/* XENOLINK Developer's Toolkit Release 7 - Compatible with 1.90 and above  */
/*                                                                          */
/* TILDE.C - Demonstrates use of Xenolink tilde codes from C                */
/*                                                                          */
/****************************************************************************/

#include <exec/types.h>
#include <exec/semaphores.h>
#include <libraries/dos.h>
#include <libraries/dosextens.h>
#include <clib/exec_protos.h>
#include <time.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include "XDOOR:xenolink.h"


struct XenolinkBase *XenolinkBase;


/* Input/output buffers; always make sure your output buffer is large */
/* enough for the output! */
UBYTE InBuf[200];
UBYTE OutBuf[200];


main()
{
   struct MenuCodeCmd Cmd;   /* Menu code command    */
   struct User        User;  /* Dummy user structure */

   /* Open xenolink.library */
   if (!(XenolinkBase = (struct XenolinkBase *) OpenLibrary("xenolink.library", 0L)))
      {
         exit(1);
      }

   /* ~100 returns the user name */
   strcpy(User.Name, "Jonathan Forbes");

   /* ~900 tests the user privilege level; changing the number 200 to */
   /* a number within the range 210-240 will cause the word SECRET    */
   /* to be displayed */
   User.Privilege = 200;

   /* Clear menu code command */
   bzero(&Cmd, sizeof(struct MenuCodeCmd));

   /* Point to start of input and output buffers */
   Cmd.OutBuf    = &OutBuf[0];
   Cmd.InBuf     = &InBuf[0];

   /* Point to user structure */
   /* (necessary only if using user tilde codes) */
   Cmd.User      = &User;

   strcpy(InBuf, "\
This is a test, ~100!  Today is the ~40~42 of ~44, 19~50\r\n\
You won't be able to see the ~900:210-240SECRET ~~word!\r\n");

   /* Process the string */
   /* (returns strlen(OutBuf)) */
   (void) ulibTildeProcess(&Cmd);

   /* Display resulting output buffer */
   printf("%s\n", OutBuf);

   CloseLibrary((struct Library *) XenolinkBase);

   exit(0);
}
