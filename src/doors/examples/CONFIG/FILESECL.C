/****************************************************************************/
/* XENOLINK Developer's Toolkit Release 7 - Compatible with 1.90 and above  */
/*                                                                          */
/* FILESECLIST.C - Displays file section information                        */
/*                                                                          */
/****************************************************************************/

#include <exec/types.h>
#include <exec/memory.h>
#include <exec/semaphores.h>
#include <libraries/dos.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include "XDOOR:xenolink.h"


struct XenolinkBase   *XenolinkBase;
struct SharedData     *Shared;


/* Function prototypes */
void Init();
void BriefListFileSections();
void DetailedListFileSections();


main(int argc, char **argv)
{
   /* Open xenolink.library and get pointer to shared data segment */
   Init();

   BriefListFileSections();
   DetailedListFileSections();

   /* Close library */
   CloseLibrary((struct Library *) XenolinkBase);

   exit(0);
}


void Init()
{
   /* Open xenolink.library */
   if (!(XenolinkBase = (struct XenolinkBase *) OpenLibrary("xenolink.library", 0L)))
   {
      printf("\n[FILESECLIST] Program Failure:  Couldn't open xenolink.library\n\n");
      exit(1);
   }

   /* Get the shared data segment */
   if (!(Shared = ulibGetSharedData()))
   {
      printf("\n[FILESECLIST] Program Failure:  The Xenolink Manager is not running\n\n");
      CloseLibrary((struct Library *) XenolinkBase);
      exit(1);
   }
}


void BriefListFileSections()
{
   UBYTE *Name;
   long  *FileSecList;
   long   NumFileSections;
   long   i;
   long   Section;

   ulibGetGlobalSectionData(NULL, &NumFileSections, NULL, &FileSecList);

   printf("Sec # Name                     \n");
   printf("----- -------------------------\n");

   for (i = 0; i < NumFileSections; i++)
   {
      Section = FileSecList[i];

      Name = (UBYTE *) ulibGetSystemData(XSD_FILE_SECTION_NAME, Section);

      printf("%5d %-25s\n", Section, Name);
   }

   printf("\n");
}


void DetailedListFileSections()
{
   UBYTE *Name;
   long  *FileSecList;
   long   NumFileSections;
   long   i;
   long   j;
   void  *DirList;
   long   NumDirs;
   long   Section;
   long   MaxFileNameLength;
   UBYTE *DirName;
   UBYTE  Readable;
   UBYTE  Writable;
   UBYTE  CountAbortedDownloads;
   UBYTE  CountAbortedDownloadBytes;
   UBYTE  UploadsVisible;
   UBYTE  EditOwnUploads;
   UBYTE  KillOwnUploads;
   UBYTE  MSDOSFileNames;

   ulibGetGlobalSectionData(NULL, &NumFileSections, NULL, &FileSecList);

   for (i = 0; i < NumFileSections; i++)
   {
      Section = FileSecList[i];

      Name         = (UBYTE *) ulibGetSystemData(XSD_FILE_SECTION_NAME, Section);
      DirList      = (void *) ulibGetSystemData(XSD_FILE_DIR, Section);
      NumDirs      = (long) ulibGetSystemData(XSD_FILE_NUM_DIRS, Section);

      MaxFileNameLength   = (long) ulibGetSystemData(XSD_FILE_MAX_NAME_LEN, Section);

      CountAbortedDownloads = (UBYTE) ulibGetSystemData(XSD_FILE_COUNT_ABORTED_DL, Section);
      CountAbortedDownloadBytes = (UBYTE) ulibGetSystemData(XSD_FILE_COUNT_ABORTED_DL_BYTES, Section);
      UploadsVisible = (UBYTE) ulibGetSystemData(XSD_FILE_UPLOADS_VISIBLE, Section);
      EditOwnUploads = (UBYTE) ulibGetSystemData(XSD_FILE_EDIT_OWN_UPLOADS, Section);
      KillOwnUploads = (UBYTE) ulibGetSystemData(XSD_FILE_KILL_OWN_UPLOADS, Section);
      MSDOSFileNames = (UBYTE) ulibGetSystemData(XSD_FILE_MSDOS_FILENAMES, Section);

      printf("Sec#%5d %-30s Max FileName Length: %d\n", Section, Name, MaxFileNameLength);

      printf("          Flags   :%s%s%s%s%s%s\n",
         CountAbortedDownloads     ? " CountAbortedDL" : "",
         CountAbortedDownloadBytes ? " CountAbortedDLBytes" : "",
         UploadsVisible            ? " UploadsVisible" : "",
         EditOwnUploads            ? " EditOwnUpl" : "",
         KillOwnUploads            ? " KillOwnUpl" : "",
         MSDOSFileNames            ? " MSDOS" : "");

      for (j = 0; j < NumDirs; j++)
      {
         DirName  = (UBYTE *) ulibGetFileDirInfo(XDD_NAME, Section, j);
         Readable = (UBYTE) ulibGetFileDirInfo(XDD_READABLE, Section, j);
         Writable = (UBYTE) ulibGetFileDirInfo(XDD_WRITABLE, Section, j);

         printf("          ");

         if (Readable == 2 && Writable)
         {
            printf("Read/Copy/Write  ");
         }
         else if (Readable && Writable)
         {
            printf("Read/Write       ");
         }
         else if (Readable == 2)
         {
            printf("Read/Copy        ");
         }
         else if (Readable)
         {
            printf("Read only        ");
         }
         else if (Writable)
         {
            printf("Write only       ");
         }
         else
         {
            printf("Inaccessible     ");
         }

         printf(" %s\n", DirName);
      }

      printf("\
------------------------------------------------------------------------------\n");
   }
}
