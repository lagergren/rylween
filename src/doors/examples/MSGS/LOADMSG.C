/****************************************************************************/
/* XENOLINK Developer's Toolkit Release 7 - Compatible with 1.90 and above  */
/*                                                                          */
/* LOADMSG.C - Load a message                                               */
/*                                                                          */
/****************************************************************************/


#include <exec/types.h>
#include <exec/ports.h>
#include <exec/memory.h>
#include <exec/semaphores.h>
#include <libraries/dos.h>
#include <time.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "XDOOR:xenolink.h"
#include "XDOOR:xenolib_proto.h"
#include "XDOOR:xenolib_pragmas.h"


struct XenolinkBase   *XenolinkBase;
struct SharedData     *Shared;


/* Abbreviated month names for displaying file date */
UBYTE *Month[] =
{
   "Jan", "Feb", "Mar", "Apr", "May", "Jun",
   "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
};


/* Function prototypes */
void Init(void);
void LoadTest(long Section, long Number);


main(int argc, char **argv)
{
   long Section;
   long Number;

   if (argc < 3)
   {
      printf("\nSyntax: LOADMSG <Section> <Number>\n\n");
      exit(1);
   }

   Section = atoi(argv[1]);
   Number  = atoi(argv[2]);

   /* Open xenolink.library and get pointer to shared data segment */
   Init();

   /* Demo */
   LoadTest(Section, Number);

   /* Close library */
   CloseLibrary((struct Library *) XenolinkBase);

   exit(0);
}


void Init(void)
{
   /* Open xenolink.library */
   if (!(XenolinkBase = (struct XenolinkBase *) OpenLibrary("xenolink.library", 0L)))
   {
      printf("Program Failure:  Couldn't open xenolink.library\n");
      exit(1);
   }

   /* Get the shared data segment */
   if (!(Shared = ulibGetSharedData()))
   {
      printf("\n[MSGLIST] Program Failure:  The Xenolink Manager is not running\n\n");
      CloseLibrary((struct Library *) XenolinkBase);
      exit(1);
   }
}


void LoadTest(long Section, long Number)
{
   struct LoadMessageCommand LMC;
   struct Msg                Header;

   /* Initialise key command */
   bzero((void *) &LMC, sizeof(struct LoadMessageCommand));

   LMC.Section = Section;
   LMC.Number  = Number;
   LMC.Where   = &Header;

   /* Strip FidoNet ^A lines */
   LMC.Flags.NoFido   = 1;

   /* Convert soft returns (0x8D 0x8D) to CR/LF (0x0D 0x0A) */

   /* You may have 0x8D 0x8D converted to a single space and */
   /* have 0x0D 0x0A converted to simply 0x0D, by setting    */
   /* Flags.SoftReturnsToSpace = 1 instead */

   LMC.Flags.SoftReturnsToCRLF = 1;

   /* Load message text */
   LMC.Flags.LoadText = 1;

   if (!ulibLoadMessage(&LMC))
   {
      CloseLibrary((struct Library *) XenolinkBase);
      printf("\n[LOADMSG] Program Failure:  Message not found\n\n");
      exit(1);
   }

   /* Print message header */
   if (ulibGetSystemData(XSD_MSG_SECTION_TYPE, Header.Section) == MSTYPE_FIDONET_NETMAIL)
   {
      printf("From: %s @ %ld:%ld/%ld.%ld\n", Header.From,
         Header.OrigZone, Header.OrigNet, Header.OrigNode, Header.OrigPoint);
      printf("  To: %s @ %ld:%ld/%ld.%ld (routed through %ld:%ld/%ld.%ld)\n",
         Header.To,
         Header.DestZone, Header.DestNet, Header.DestNode, Header.DestPoint,
         Header.RouteZone, Header.RouteNet, Header.RouteNode, Header.RoutePoint);
      printf("Subj: %s\n", Header.Subject);
   }
   else
   {
      printf("From: %s\n", Header.From);
      printf("  To: %s\n", Header.To);
      printf("Subj: %s\n", Header.Subject);
   }

   printf("Sent: %02d-%s-%02d %02d:%02d:%02d\n", Header.DateSent.Day,
      Month[ Header.DateSent.Month ],
      Header.DateSent.Year + (FIRST_YEAR-1900),
      Header.DateSent.Hour, Header.DateSent.Minute,
      Header.DateSent.Second);

   if (Header.Flags.ReadByReceiver)
   {
      printf("Rcvd: %02d-%s-%02d %02d:%02d:%02d\n", Header.DateReceived.Day,
         Month[ Header.DateReceived.Month ],
         Header.DateReceived.Year + (FIRST_YEAR-1900),
         Header.DateReceived.Hour, Header.DateReceived.Minute,
         Header.DateReceived.Second);
   }
   else
   {
      printf("Rcvd: No\n");
   }

   /* Display null-terminated message text */
   printf("\n%s\n", LMC.MessageText);

   /* Free message text */
   FreeMem(LMC.MessageText, Header.Length+1);
}
