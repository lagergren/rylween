/****************************************************************************/
/* XENOLINK Developer's Toolkit Release 7 - Compatible with 1.90 and above  */
/*                                                                          */
/* FILEINFO.C - Obtains information on a single file                        */
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


/* Max filename length you want */
#define MAX_FILENAME_LENGTH 15

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


/* Might as well let this be a global variable for this demo */
struct FileCat File;


/* Abbreviated month names for displaying file date */
UBYTE *Month[] =
{
   "Jan", "Feb", "Mar", "Apr", "May", "Jun",
   "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
};


/* Function prototypes */
void  Init();
UBYTE GetDescWordLength(UBYTE *s, UBYTE *endchar, UBYTE *r);
void  CopyWord(UBYTE *s, UBYTE *d, long Length);
void  DisplayFileDescription(UBYTE *p, long Indent, long CharsPerLine, UBYTE *PreLine);
void  PrintSpaces(UBYTE HowMany);
void  TraverseFileCatalogue();
void  GetFileInfo(UBYTE *FileName);


main(int argc, char **argv)
{
   if (argc < 2)
   {
      printf("Syntax: FILEINFO <FileName>\n");
      exit(1);
   }

   /* Open xenolink.library and get pointer to shared data segment */
   Init();

   /* Get section data */
   ulibGetGlobalSectionData(&NumMsgSections, &NumFileSections, &MsgList, &FileList);

   /* Demo */
   GetFileInfo(argv[1]);

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
   long i;

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


void GetFileInfo(UBYTE *FileName)
{
   struct CatCommand LFC;
   struct FileCat    File;
   long              SubDir;

   bzero(&LFC, sizeof(struct CatCommand));

   LFC.Name                  = FileName;
   LFC.File1                 = &File;
   LFC.Flags.LoadDescription = 1;

   if (ulibLoadFileCat(&LFC))
   {
      printf("Name                 : %s\n", File.Name);

      SubDir = ulibFindFileSubDir(File.Section, File.Name);

      printf("Located in dir       : %s\n",
         SubDir < 0 ? "OFFLINE" : ulibFileSubDir(File.Section, SubDir));

      printf("Date uploaded        : %02d-%s-%02d\n",
         File.DateUploaded.Day, Month[ File.DateUploaded.Month ],
         File.DateUploaded.Year+(FIRST_YEAR-1900));

      if (File.LastDownloaded.Day)
      {
         printf("Last downloaded      : %02d-%s-%02d\n",
            File.LastDownloaded.Day, Month[ File.LastDownloaded.Month ],
            File.LastDownloaded.Year+(FIRST_YEAR-1900));
      }
      else
      {
         printf("Last downloaded      : Never\n");
      }

      printf("Section              : %d (%s)\n",
         File.Section, ulibGetSystemData(XSD_FILE_SECTION_NAME, File.Section));

      printf("Flags.RecordValid    : %d\n", File.Flags.RecordValid);
      printf("Flags.Validated      : %d\n", File.Flags.Valid);
      printf("Flags.FreeDownload   : %d\n", File.Flags.FreeDownload);
      printf("Flags.Offline        : %d\n", File.Flags.Offline);
      printf("Flags.Invisible      : %d\n", File.Flags.Invisible);

      printf("Privilege            : %d\n", File.Privilege);

           /* 12345678901234567890123 - 23 characters printed */
      printf("Description          : ");

      /* Check that description is not NULL for some reason */

      /* Indent by 23 characters, and allow max 79 (not 80) on one line */
      DisplayFileDescription(File.Description ?
         File.Description : "Invalid description",
         23, 80-23, "");

      /* Free file description */
      FreeMem(File.Description, File.DescLength);

      printf("\n");
   }
   else
   {
      printf("Couldn't find file '%s'\n", FileName);
   }
}
