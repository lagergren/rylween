/****************************************************************************/
/* XENOLINK Developer's Toolkit Release 7 - Compatible with 1.90 and above  */
/*                                                                          */
/* MSGLIST.C - Traverses message base                                       */
/*                                                                          */
/****************************************************************************/


#include <exec/types.h>
#include <exec/ports.h>
#include <exec/memory.h>
#include <exec/semaphores.h>
#include <libraries/dos.h>
#include <clib/exec_protos.h>
#include <clib/dos_protos.h>
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


struct MsgTraverseCommand Command;


/* Function prototypes */
void Init();
void TraverseMsgBase(long Section);


main(int argc, char **argv)
{
   long SectionNumber;

   if (argc < 2)
   {
      printf("\nSyntax: MSGLIST <MsgBase>\n\n");
      exit(1);
   }

   SectionNumber = atoi(argv[1]);

   /* Open xenolink.library and get pointer to shared data segment */
   Init();

   /* Demo */
   TraverseMsgBase(SectionNumber);

   /* Close library */
   CloseLibrary((struct Library *) XenolinkBase);

   exit(0);
}


void Init()
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


void TraverseMsgBase(long Section)
{
   struct Msg *MsgHeader;
   UBYTE       SectionType;

   SectionType = ulibGetSystemData(Section, XSD_MSG_SECTION_TYPE);

   /* Initialise key command */
   bzero((void *) &Command, sizeof(struct MsgTraverseCommand));

   /* ------ Traverse the message base in order ----- */
   Command.Direction = 0;
   Command.Section   = Section;

   Command.TraverseHeader = 1;

   /* Load body text */
   Command.TraverseText   = 1;

   /* Strip all lines beginning with CTRL-A (e.g. MSGID:,REPLY:,INTL,etc.) */
   Command.StripFidoLines = 1;

   /* Convert soft returns (0x8D 0x8D) to a carriage return/line feed combination */
   Command.SoftReturnsToCRLF = 1;

   /* Number of data records (headers only, not text) you wish to buffer */
   Command.HeadBufferRecords = 8;

   if (ulibInitTraverseMessageBase(&Command))
   {
      CloseLibrary((struct Library *) XenolinkBase);
      printf("\n[MSGLIST] Program Failure:  Error initialising traversal for section #%d\n", Section);
      printf("                            That message section probably does not exist\n\n");
      exit(1);
   }

   MsgHeader = &Command.Header;

   printf("--- Section %d (%s) ---\n\n", Section,
      ulibGetSystemData(XSD_MSG_SECTION_NAME, Section));

   /* Read each entry */
   while (1)
   {
      if (ulibTraverseMessageBase(&Command))
      {
         break;
      }

      printf("From: %s\n", MsgHeader->From);
      printf("  To: %s\n", MsgHeader->To);
      printf("Subj: %s\n", MsgHeader->Subject);

      printf("Sent: %02d-%s-%02d %02d:%02d:%02d\n", MsgHeader->DateSent.Day,
         Month[ MsgHeader->DateSent.Month ],
         MsgHeader->DateSent.Year + (FIRST_YEAR-1900),
         MsgHeader->DateSent.Hour, MsgHeader->DateSent.Minute,
         MsgHeader->DateSent.Second);

      if (MsgHeader->Flags.ReadByReceiver)
      {
         printf("Rcvd: %02d-%s-%02d %02d:%02d:%02d\n", MsgHeader->DateReceived.Day,
            Month[ MsgHeader->DateReceived.Month ],
            MsgHeader->DateReceived.Year + (FIRST_YEAR-1900),
            MsgHeader->DateReceived.Hour, MsgHeader->DateReceived.Minute,
            MsgHeader->DateReceived.Second);
      }
      else
      {
         printf("Rcvd: No\n");
      }

      printf("\n");
      printf("%s\n", Command.MsgText);
   }

   /* Free file handles and memory */
   ulibEndTraverseMessageBase(&Command);
}
