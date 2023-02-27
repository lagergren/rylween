/****************************************************************************/
/* XENOLINK Developer's Toolkit Release 7 - Compatible with 1.90 and above  */
/*                                                                          */
/* DIRS.C - Displays configured Xenolink directories                        */
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
void ListDirs();


main(int argc, char **argv)
{
   /* Open xenolink.library and get pointer to shared data segment */
   Init();

   ListDirs();

   /* Close library */
   CloseLibrary((struct Library *) XenolinkBase);

   exit(0);
}


void Init()
{
   /* Open xenolink.library */
   if (!(XenolinkBase = (struct XenolinkBase *) OpenLibrary("xenolink.library", 0L)))
   {
      printf("\n[DIRS] Program Failure:  Couldn't open xenolink.library\n\n");
      exit(1);
   }

   /* Get the shared data segment */
   if (!(Shared = ulibGetSharedData()))
   {
      printf("\n[DIRS] Program Failure:  The Xenolink Manager is not running\n\n");
      CloseLibrary((struct Library *) XenolinkBase);
      exit(1);
   }
}


void ListDirs()
{
   printf("Utilities      : %s\n", (UBYTE *) ulibGetSystemData(XSD_XENOLINK_UTILS_DIR, 0));
   printf("Modules        : %s\n", (UBYTE *) ulibGetSystemData(XSD_MODULES_DIR, 0));
   printf("Base upload    : %s\n", (UBYTE *) ulibGetSystemData(XSD_BASE_UPLOAD_DIR, 0));
   printf("Users          : %s\n", (UBYTE *) ulibGetSystemData(XSD_USER_DIR, 0));
   printf("Log files      : %s\n", (UBYTE *) ulibGetSystemData(XSD_LOG_DIR, 0));
   printf("File catalogue : %s\n", (UBYTE *) ulibGetSystemData(XSD_FILE_CAT_DIR, 0));
   printf("Doors          : %s\n", (UBYTE *) ulibGetSystemData(XSD_DOOR_DIR, 0));
   printf("Menu source    : %s\n", (UBYTE *) ulibGetSystemData(XSD_MENU_SOURCE_DIR, 0));
   printf("Menu object    : %s\n", (UBYTE *) ulibGetSystemData(XSD_MENU_OBJECT_DIR, 0));
   printf("File attaches  : %s\n", (UBYTE *) ulibGetSystemData(XSD_FILE_ATTACH_DIR, 0));
   printf("Menu text      : %s\n", (UBYTE *) ulibGetSystemData(XSD_MENU_TEXT_DIR, 0));
   printf("Text files     : %s\n", (UBYTE *) ulibGetSystemData(XSD_TEXT_DIR, 0));
   printf("FidoNet        : %s\n", (UBYTE *) ulibGetSystemData(XSD_FIDONET_DIR, 0));
   printf("Inbound mail   : %s\n", (UBYTE *) ulibGetSystemData(XSD_INBOUND_MAIL_DIR, 0));
   printf("Outbound mail  : %s\n", (UBYTE *) ulibGetSystemData(XSD_OUTBOUND_MAIL_DIR, 0));
   printf("Scripts        : %s\n", (UBYTE *) ulibGetSystemData(XSD_SCRIPTS_DIR, 0));
   printf("System data    : %s\n", (UBYTE *) ulibGetSystemData(XSD_SYSTEM_DATA_DIR, 0));
}
