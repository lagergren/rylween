/****************************************************************************/
/* XENOLINK Developer's Toolkit Release 7 - Compatible with 1.90 and above  */
/*                                                                          */
/* FILELIST.C - Traverses file catalogue (all sections)                     */
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


/* Use ANSI sequences (when optimal) to print multiple spaces? */
#define USE_ANSI


struct XenolinkBase   *XenolinkBase;
struct SharedData     *Shared;


/* List of message sections and file sections */
long                  *MsgList;
long                  *FileList;

/* Number of message sections and file sections */
long                   NumMsgSections;
long                   NumFileSections;


/* Abbreviated month names for displaying file date */
UBYTE *Month[] =
{
   "Jan", "Feb", "Mar", "Apr", "May", "Jun",
   "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
};


struct KeyTraverseCommand Command;


/* Function prototypes */
void Init();
UBYTE GetDescWordLength(UBYTE *s, UBYTE *endchar, UBYTE *r);
void CopyWord(UBYTE *s, UBYTE *d, long Length);
void DisplayFileDescription(UBYTE *p, long Indent, long CharsPerLine, UBYTE *PreLine);
void PrintSpaces(UBYTE HowMany);
void TraverseFileCatalogue();


main()
{
   /* Open xenolink.library and get pointer to shared data segment */
   Init();

   /* Get section data */
   ulibGetGlobalSectionData(&NumMsgSections, &NumFileSections, &MsgList, &FileList);

   /* Demo */
   TraverseFileCatalogue();

   /* Close library */
   CloseLibrary((struct Library *) XenolinkBase);

   exit(0);
}


/* ------ For displaying the word-wrapped description ------ */
UBYTE GetDescWordLength(UBYTE *s, UBYTE *endchar, UBYTE *r)
{
   UBYTE  w = 0;
   UBYTE  f = 0;
   UBYTE *t;

   /* Don't count ANSI colour change sequences as characters */
   /* (this enables one to highlight parts of the description) */
   while (*s && *s != ' ' && *s != 13 && w < 120)
   {
      if (*s == 27)
      {
         t = s;

         do
         {
            f++;
            t++;
         } while (*t && *t != 'm' && *t != ' ');
      }

      s++;
      w++;
   }

   *endchar = *s;

   if (*s == ' ' || *s == 13)
   {
      w++;
   }

   *r = w - f;

   if (*r < 1)
   {
      *r = 1;
   }

   return (w);
}


/* ------ For displaying the word-wrapped description ------ */
void CopyWord(UBYTE *s, UBYTE *d, long Length)
{
   UBYTE Temp[128];
   long  i;

   for (i=0; i<Length; i++)
   {
      Temp[i] = s[i];
   }

   Temp[i] = 0;

   strcat(d, Temp);
}


void DisplayFileDescription(UBYTE *p, long Indent, long CharsPerLine, UBYTE *PreLine)
{
   UBYTE *s;
   UBYTE *q;
   UBYTE  Pos;
   UBYTE  w; /* Word length including ANSI characters */
   UBYTE  r; /* Word length excluding ANSI characters */
   UBYTE  EndChar;
   UBYTE  Added;
   UBYTE  Buffer[128];

   s = p;
   q = p;
   Pos = 0;

   Buffer[0] = 0;

   for (;;)
   {
      w = GetDescWordLength(s, &EndChar, &r);

      /* Fatal error */
      if (!w)
      {
         goto EndOfLine;
      }

      Added = 0;

      if (Pos + r > CharsPerLine)
      {
         printf("%s\n", Buffer);

         Buffer[0] = 0;
         Pos = 0;

         if (EndChar == 13)
         {
            CopyWord(s, Buffer, w);
            Pos = 0;
            s += w;

            if (Indent)
               {
                  printf("%s", PreLine);
                  PrintSpaces(Indent);
                  printf("%s\n", Buffer);
               }
            else
               {
                  printf("%s%s\n", PreLine, Buffer);
               }

            Buffer[0] = 0;
         }

         if (Indent)
         {
            printf("%s", PreLine);

            if (Indent)
               {
                  PrintSpaces(Indent);
               }
         }
         else
         {
            printf("%s", PreLine);
         }

         while (*s == ' ' && w)
         {
            s++;
            w--;
         }

         if (EndChar == 13)
         {
            continue;
         }
      }
      else
      {
         if (EndChar == 13)
         {
            CopyWord(s, Buffer, w);
            Pos = 0;
            s += w;

            printf("%s\n%s", Buffer, PreLine);

            if (Indent)
            {
               PrintSpaces(Indent);
            }

            Buffer[0] = 0;
            Pos = 0;

            continue;
         }
      }

      if (w)
      {
         CopyWord(s, Buffer, w);
         Pos += r;
         s += w;
      }

      if (EndChar == 0)
      {
EndOfLine:
         printf("%s\n", Buffer);

         break;
      }
   }
}


void PrintSpaces(UBYTE HowMany)
{
#ifdef USE_ANSI
   /* Optimised ANSI - ESC[xC is four characters */
   if (HowMany > 4)
   {
      printf("[%dC", HowMany);
   }
   else
   {
      printf("%-*s", HowMany, "");
   }
#else
   printf("%-*s", HowMany, "");
#endif
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
      printf("Program Failure:  The Xenolink Manager is not running\n");
      CloseLibrary((struct Library *) XenolinkBase);
      exit(1);
   }
}


void TraverseFileCatalogue()
{
   long           *Search;
   long            i;
   long            Section;
   long            MaxFileNameLength;
   long            Indent;
   struct FileCat  File;

   /* Go through all sections */
   for (Search = FileList, i = 0; i < NumFileSections; i++, Search++)
   {
      Section = *Search;

      printf("--- Section %ld (%s) ---\n", Section,
         ulibGetSystemData(XSD_FILE_SECTION_NAME, Section));

      /* Initialise key command */
      bzero((void *) &Command, sizeof(struct KeyTraverseCommand));

      /* ------ Traverse the file catalogue in no particular order ------ */
      Command.Direction = 0;
      Command.Section   = Section;

      /* Load file descriptions */
      Command.Flags.LoadFileDesc = 1;

      /* Fast buffering */
      Command.Flags.LinearBufferDataFile = 1;

      /* Number of data records you wish to buffer (one file is about 250  */
      /* bytes, so 64 data records works out to about a 16K buffer).       */
      Command.DataRecords = 64;

      ulibInitTraverseFileCat(&Command);

      /* Max allowable file length for this section */
      MaxFileNameLength = ulibGetSystemData(XSD_FILE_MAX_NAME_LEN, Section);

      /* How much to indent the description by (after the first line) */
      Indent = MaxFileNameLength + 18;

      /* Read each entry */
      while (1)
      {
         if (ulibTraverseFileCat(&Command, &File))
         {
            break;
         }

         printf("%-*s %6d %02d-%s-%02d ",
            MaxFileNameLength, File.Name,
            File.Length,
            File.DateUploaded.Day, Month[File.DateUploaded.Month],
            File.DateUploaded.Year + (FIRST_YEAR - 1900));

         /* Just in case Description is NULL for some reason */
         DisplayFileDescription(File.Description ?
            File.Description : "Invalid description",
            Indent, 80-Indent, "");

         /* ulibTraverseFileCat() and ulibEndTraverseFileCat()   */
         /* will free up the description memory as they go along */
      }

      /* Free file handles and memory */
      ulibEndTraverseFileCat(&Command);

      printf("\n");
   }
}
