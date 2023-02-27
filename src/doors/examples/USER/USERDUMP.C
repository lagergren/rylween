/****************************************************************************/
/* XENOLINK Developer's Toolkit Release 7 - Compatible with 1.90 and above  */
/*                                                                          */
/* USERDUMP.C - Traverses user file                                         */
/*                                                                          */
/****************************************************************************/

#include <exec/types.h>
#include <exec/memory.h>
#include <exec/semaphores.h>
#include <libraries/dos.h>
#include <proto/dos.h>
#include <clib/exec_protos.h>
#include <clib/dos_protos.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include "XDOOR:xenolink.h"


struct XenolinkBase   *XenolinkBase;
struct SharedData     *Shared;


long                   Level;
long                   NumMsgSections;
long                   NumFileSections;
long                  *MsgSecList;
long                  *FileSecList;


/* Function prototypes */
void KeyedTraverse(long Direction);
void NonkeyedTraverse(void);
void Init(void);
void DisplaySectionData(struct User *Who);


main(int argc, char **argv)
{
   if (argc < 2)
   {
      printf("\nSyntax: USERDUMP <Level>\n\n");
      exit(1);
   }

   Level = atoi(argv[1]);

   /* Open xenolink.library and get pointer to shared data segment */
   Init();

   /* Traverse user file in no particular order (i.e. the order the */
   /* user records are in on disk) */
   NonkeyedTraverse();

   /* Close library */
   CloseLibrary((struct Library *) XenolinkBase);

   exit(0);
}


void NonkeyedTraverse(void)
{
   struct KeyTraverseCommand Command;
   struct User               User;

   /* Initialise key command */
   bzero((void *) &Command, sizeof(struct KeyTraverseCommand));

   /* ------ Traverse the user file in no particular order ------ */
   Command.Direction = 0;

   /* Allow ourselves the possibility of reading section data if */
   /* we want to, but read it ONLY if we explicitly request it */
   /* via the ulibFetchSectionData() function */
   Command.Flags.CanReadSectionData = 1;

   /* Faster traversal */
   /* Note, LinearBufferDataFile only works when Direction = 0 */
   Command.Flags.LinearBufferDataFile = 1;

   /* Number of data records you wish to buffer (one user is about 250  */
   /* bytes, so 64 data records works out to about a 16K buffer).       */
   /* Note, only works when Direction = 0 ("no particular order") */
   Command.DataRecords = 64;

   /* Initialise traversal */
   ulibInitTraverseUserFile(&Command);

   /* Read each user and display his name */
   while (1)
   {
      if (ulibTraverseUserFile(&Command, &User))
      {
         break;
      }

      /* This program is going to print out users of a certain access */
      /* level */
      if (User.Privilege == Level)
      {
         printf("\x0C"); /* Clear screen */

         printf(" NAME: %-25s\n", User.Name);
         printf("VOICE: %-15s           DATA: %-15s\n",
            User.VoiceTelephone, User.DataTelephone);
         printf("---------------------------------------------------------------------------\n");

         if (!ulibFetchUserTraversalSecData(&Command, &User))
         {
            DisplaySectionData(&User);
         }
         else
         {
            printf("Error reading user section data\n");
         }

         printf("\nPress [<return>] to continue\n");
         getc(stdin);
      }
   }

   /* Free file handles and memory */
   ulibEndTraverseUserFile(&Command);
}


void Init(void)
{
   /* Open xenolink.library */
   if (!(XenolinkBase = (struct XenolinkBase *) OpenLibrary("xenolink.library", 0L)))
   {
      printf("\n[USERDUMP] Program Failure:  Couldn't open xenolink.library\n\n");
      exit(1);
   }

   /* Get the shared data segment */
   if (!(Shared = ulibGetSharedData()))
   {
      printf("\n[USERDUMP] Program Failure:  The Xenolink Manager is not running\n\n");
      CloseLibrary((struct Library *) XenolinkBase);
      exit(1);
   }

   ulibGetGlobalSectionData(&NumMsgSections, &NumFileSections, &MsgSecList, &FileSecList);
}


void DisplaySectionData(struct User *Who)
{
   long  i;
   long  Section;
   UBYTE TempString[16];

   printf("\nMessage sections\n\n");

   for (i = 0; i < NumMsgSections; i++)
   {
      Section = MsgSecList[i];

      TempString[0] = ulibGetUserData(Who, XUD_MVIEW_ACCESS, Section, NULL) ? 'V' : '-';
      TempString[1] = ulibGetUserData(Who, XUD_READ_ACCESS, Section, NULL)  ? 'R' : '-';
      TempString[2] = ulibGetUserData(Who, XUD_WRITE_ACCESS, Section, NULL) ? 'W' : '-';
      TempString[3] = ulibGetUserData(Who, XUD_DELETE_TO_SELF_ACCESS, Section, NULL) ? 'T' : '-';
      TempString[4] = ulibGetUserData(Who, XUD_DELETE_FROM_SELF_ACCESS, Section, NULL) ? 'B' : '-';
      TempString[5] = ulibGetUserData(Who, XUD_DELETE_ANY_ACCESS, Section, NULL) ? 'D' : '-';
      TempString[6] = ulibGetUserData(Who, XUD_FORWARD_ACCESS, Section, NULL) ? 'F' : '-';
      TempString[7] = ulibGetUserData(Who, XUD_MSYSOP_ACCESS, Section, NULL) ? 'S' : '-';
      TempString[8] = 0;

      printf("  #%5ld %s  %s\n",
         Section,
         TempString,
         ulibGetSystemData(XSD_MSG_SECTION_NAME, Section));
   }

   printf("\nFile sections\n\n");

   for (i = 0; i < NumFileSections; i++)
   {
      Section = FileSecList[i];

      TempString[0] = ulibGetUserData(Who, XUD_FVIEW_ACCESS, Section, NULL)    ? 'V' : '-';
      TempString[1] = ulibGetUserData(Who, XUD_FLIST_ACCESS, Section, NULL)    ? 'L' : '-';
      TempString[2] = ulibGetUserData(Who, XUD_UPLOAD_ACCESS, Section, NULL)   ? 'U' : '-';
      TempString[3] = ulibGetUserData(Who, XUD_DOWNLOAD_ACCESS, Section, NULL) ? 'D' : '-';
      TempString[4] = ulibGetUserData(Who, XUD_FREE_DOWNLOAD_ACCESS, Section, NULL) ? 'F' : '-';
      TempString[5] = ulibGetUserData(Who, XUD_FILE_EDIT_ACCESS, Section, NULL)     ? 'E' : '-';
      TempString[6] = ulibGetUserData(Who, XUD_MOVE_ACCESS, Section, NULL)     ? 'M' : '-';
      TempString[7] = ulibGetUserData(Who, XUD_KILL_ACCESS, Section, NULL)     ? 'K' : '-';
      TempString[8] = ulibGetUserData(Who, XUD_FSYSOP_ACCESS, Section, NULL)   ? 'S' : '-';
      TempString[9] = 0;

      printf("  #%5ld %s %s\n",
         Section,
         TempString,
         ulibGetSystemData(XSD_FILE_SECTION_NAME, Section));
   }
}
