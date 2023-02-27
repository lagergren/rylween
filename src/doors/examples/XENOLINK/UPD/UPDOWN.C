/****************************************************************************/
/* XENOLINK Developer's Toolkit Release 7 - Compatible with 1.90 and above  */
/*                                                                          */
/* UPDOWNLOAD.C - Simple game demonstrating uploading/downloading           */
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
#include <string.h>
#include <stdarg.h>

#include "XDOOR:xenolink.h"

/* Use function pointer #defines */
#define DOOR_FUNCTION_MACROS
#include "XDOOR:doorlib.h"

#include "XDOOR:doorcode.h"


void DownloadDemo(void);
void UploadDemo(void);
void FreeAllTransferFiles(void);
void FreeAllReceivedFiles(void);


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

   DownloadDemo();
   UploadDemo();

   exit(0);
}


void DownloadDemo(void)
{
   struct TransferFile *TransferEntry;
   struct TransferFile *Search;

   if (!ConfirmYN("Ready to perform the download test?", 1))
   {
      exit(1);
   }

   CARRIER_TEST;

   /* Remove all files currently in the file transfer list */
   FreeAllTransferFiles();


   /* Simple demo of adding "C:COPY" to send list */
   TransferEntry = ulibAddFileToSendList(XCBD, NULL, "C:", "COPY");

   if (!TransferEntry)
   {
      wprintf("Error adding C:COPY to transfer list (already there?)\r\n");
      exit(1);
   }

   /* Given the TransferEntry returned by ulibAddFileToSendList(), you can */
   /* set the following fields: */
   /*    Flags.DeleteAfterGoodSend : Delete the file if it is sent successfully */
   /*    Flags.DeleteAfterBadSend  : Delete the file even if aborted part way */
   /*    Flags.UseFakeFileName     : Send the file as usual, but pretend the file */
   /*                                is called <FakeFileName> */
   /*    Flags.TruncateAfterGoodSend : Truncate file length to zero if sent successfully */


   /* Demo of adding "C:DIR" to send list */
   TransferEntry = ulibAddFileToSendList(XCBD, NULL, "C:", "DIR");

   if (!TransferEntry)
   {
      wprintf("Error adding C:DIR to transfer list (already there?)\r\n");
      exit(1);
   }

   /* For example, we're going to send "C:DIR" to the remote system, but we're going */
   /* to send it as "Bugaboo" */
   TransferEntry->Flags.UseFakeFileName = 1;
   strcpy(TransferEntry->FakeFileName, "BUGABOO");


   /* Use ProtocolSend(0, x) to send immediately */
   /* Use ProtocolSend(1, x) to allow the user to remove files / change protocol */
   ProtocolSend(0, USER->Protocol); /* Send file with user's default protocol */


   /* Now let's see what happened after our protocol send; we'll examine all */
   /* entries in the file list */
   for (Search = XCBD->HeadTransferFile; Search; Search = Search->Next)
   {
      wprintf("Sent: %s   CPS: %6ld   Name: %s%s\r\n",
         Search->Flags.Sent ? "Yes" : "No ",
         Search->CPS,
         Search->DirName,
         Search->FileCat.Name);

      /* You can also check other fields such as: */
      /*    Flags.Aborted        : Started to send file, but aborted part way */
      /*    Flags.Skipped        : Remote side told us to skip the file */
      /*    Flags.StartedSending : We did start sending this file */
      /*    TimeStarted          : When we started sending this file */
      /*    TimeEnded            : When we finished sending this file */
   }


   /* Remove all file transfer entries */
   FreeAllTransferFiles();
}


void UploadDemo(void)
{
   struct TransferFile *Search;

   wprintf("\r\n");

   if (!ConfirmYN("Ready to perform the upload test?", 1))
   {
      exit(1);
   }

   CARRIER_TEST;


   wprintf("\r\nGreat, please upload something now...\r\n");

   /* Clear the "files received" list */
   FreeAllReceivedFiles();

   /* First parameter doesn't do anything yet */
   /* All files go to the current node's upload dir (e.g. upload/1/<filename>) */
   ProtocolReceive(0, USER->Protocol);

   wprintf("\r\nReceived the following files:\r\n\r\n");

   /* Xenolink will auto-upload-resume for you; if a file is aborted part way, */
   /* it will be moved into the upload/resume/ directory, and a subsequent */
   /* attempt to upload the file will invoke upload resume automatically, if */
   /* supported by the protocol.  To disable upload resume, simply attempt to */
   /* delete the file from the upload/resume/ directory if it is listed as */
   /* aborted */
   for (Search = XCBD->HeadReceiveFile; Search; Search = Search->Next)
   {
      wprintf("OK: %s  Length: %7ld   CPS: %6ld   Name: %s\r\n",
         Search->Flags.Aborted ? "NO" : "Yes",
         Search->Length,
         Search->CPS,
         Search->FileCat.Name);
   }

   /* It is your responsibility to move files out of /upload/<nodenumber>/ as */
   /* soon as possible; Xenolink will delete everything in that directory when */
   /* the user logs out */

   /* Clear the "files received" list */
   FreeAllReceivedFiles();

   wprintf("\r\n");
}


void FreeAllTransferFiles(void)
{
   struct TransferFile *Search;
   struct TransferFile *Next;

   for (Search = XCBD->HeadTransferFile; Search; Search = Next)
   {
      Next = Search->Next;

      (void) ulibFreeFileInSendList(XCBD, Search);
   }
}


void FreeAllReceivedFiles(void)
{
   struct TransferFile *Search;
   struct TransferFile *Next;

   for (Search = XCBD->HeadReceiveFile; Search; Search = Next)
   {
      Next = Search->Next;

      (void) ulibFreeFileInReceiveList(XCBD, Search);
   }
}
