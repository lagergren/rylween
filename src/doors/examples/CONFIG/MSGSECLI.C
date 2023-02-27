/****************************************************************************/
/* XENOLINK Developer's Toolkit Release 7 - Compatible with 1.90 and above  */
/*                                                                          */
/* MSGSECLIST.C - Displays message section information                      */
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


/* Function prototypes */
void Init();
void BriefListMsgSections();
void DetailedListMsgSections();


main(int argc, char **argv)
{
   /* Open xenolink.library and get pointer to shared data segment */
   Init();

   BriefListMsgSections();
   DetailedListMsgSections();

   /* Close library */
   CloseLibrary((struct Library *) XenolinkBase);

   exit(0);
}


void Init()
{
   /* Open xenolink.library */
   if (!(XenolinkBase = (struct XenolinkBase *) OpenLibrary("xenolink.library", 0L)))
   {
      printf("\n[MSGSECLIST] Program Failure:  Couldn't open xenolink.library\n\n");
      exit(1);
   }

   /* Get the shared data segment */
   if (!(Shared = ulibGetSharedData()))
   {
      printf("\n[MSGSECLIST] Program Failure:  The Xenolink Manager is not running\n\n");
      CloseLibrary((struct Library *) XenolinkBase);
      exit(1);
   }
}


void BriefListMsgSections()
{
   UBYTE *Name;
   long  *MsgSecList;
   long   NumMsgSections;
   long   SecType;
   long   i;
   long   Low;
   long   High;
   long   HighViaNet;
   long   Section;

   ulibGetGlobalSectionData(&NumMsgSections, NULL, &MsgSecList, NULL);

   printf("Sec # Name                      Low   High  ViaNet\n");
   printf("----- ------------------------- ----- ----- ------\n");

   for (i = 0; i < NumMsgSections; i++)
   {
      Section = MsgSecList[i];

      Name       = (UBYTE *) ulibGetSystemData(XSD_MSG_SECTION_NAME, Section);
      SecType    = (long) ulibGetSystemData(XSD_MSG_SECTION_TYPE, Section);
      Low        = (long) ulibGetSystemData(XSD_LOW_MESSAGE, Section);
      High       = (long) ulibGetSystemData(XSD_HIGH_MESSAGE, Section);
      HighViaNet = (long) ulibGetSystemData(XSD_HIGH_MESSAGE_VIA_NET, Section);

      printf("%5d %-25s %5d %5d", Section, Name, Low, High);

      if (SecType == MSTYPE_FIDONET_ECHOMAIL || SecType == MSTYPE_FIDONET_NETMAIL)
      {
         printf(" %6d\n", HighViaNet);
      }
      else
      {
         printf("   n/a\n");
      }
   }

   printf("\n");
}


void DetailedListMsgSections()
{
   UBYTE *Name;
   UBYTE *Dir;
   UBYTE *TagName;
   long  *MsgSecList;
   long   NumMsgSections;
   long   MaxMsgs;
   long   SecType;
   struct FidoNetAddress *AddressList;
   long   NumAddresses;
   long   i;
   long   j;
   long   Low;
   long   High;
   long   HighViaNet;
   long   Section;
   UBYTE  PermitAnon;
   UBYTE  PermitPrivate;
   UBYTE  PermitQuoting;
   UBYTE  PermitDelUponRecv;
   UBYTE  PermitDelToSelf;
   UBYTE  PermitDelFromSelf;
   UBYTE  UseHandles;

   ulibGetGlobalSectionData(&NumMsgSections, NULL, &MsgSecList, NULL);

   for (i = 0; i < NumMsgSections; i++)
   {
      Section = MsgSecList[i];

      Name         = (UBYTE *) ulibGetSystemData(XSD_MSG_SECTION_NAME, Section);
      Dir          = (UBYTE *) ulibGetSystemData(XSD_MSG_DIR, Section);
      TagName      = (UBYTE *) ulibGetSystemData(XSD_MSG_TAG_NAME, Section);
      MaxMsgs      = (long) ulibGetSystemData(XSD_MSG_MAX_MSGS, Section);
      SecType      = (long) ulibGetSystemData(XSD_MSG_SECTION_TYPE, Section);
      NumAddresses = (long) ulibGetSystemData(XSD_MSG_NUM_ADDRESSES, Section);
      AddressList  = (struct FidoNetAddress *) ulibGetSystemData(XSD_MSG_ADDRESS_LIST, Section);
      Low          = (long) ulibGetSystemData(XSD_LOW_MESSAGE, Section);
      High         = (long) ulibGetSystemData(XSD_HIGH_MESSAGE, Section);
      HighViaNet   = (long) ulibGetSystemData(XSD_HIGH_MESSAGE_VIA_NET, Section);

      /* Section flags */
      PermitAnon   = (UBYTE) ulibGetSystemData(XSD_MSG_PERMIT_ANONYMOUS, Section);
      PermitPrivate  = (UBYTE) ulibGetSystemData(XSD_MSG_PERMIT_PRIVATE, Section);
      PermitQuoting  = (UBYTE) ulibGetSystemData(XSD_MSG_PERMIT_QUOTING, Section);
      PermitDelUponRecv  = (UBYTE) ulibGetSystemData(XSD_MSG_PERMIT_DEL_UPON_RECV, Section);
      PermitDelToSelf = (UBYTE) ulibGetSystemData(XSD_MSG_PERMIT_DEL_MAIL_TO_SELF, Section);
      PermitDelFromSelf   = (UBYTE) ulibGetSystemData(XSD_MSG_PERMIT_DEL_MAIL_FROM_SELF, Section);
      UseHandles   = (UBYTE) ulibGetSystemData(XSD_MSG_USE_HANDLES, Section);

      printf("Sec#%5d %-25s   Low: %5d   High: %5d", Section, Name, Low, High);

      if (SecType == MSTYPE_FIDONET_ECHOMAIL || SecType == MSTYPE_FIDONET_NETMAIL)
      {
         printf("   Net: %5d\n", HighViaNet);
      }
      else
      {
         printf("\n");
      }

      printf("          Dir     : %-40s MaxMsgs: %5d\n", Dir, MaxMsgs);

      if (SecType == MSTYPE_FIDONET_ECHOMAIL || SecType == MSTYPE_FIDONET_NETMAIL)
      {
         printf("          TagName : %-25s\n",
            SecType == MSTYPE_FIDONET_NETMAIL ? "MATRIX" : TagName);

         printf("          AddrList:");

         for (j = 0; j < NumAddresses; j++)
         {
            printf(" %d:%d/%d.%d",
               AddressList[j].Zone, AddressList[j].Net,
               AddressList[j].Node, AddressList[j].Point);
         }

         printf("\n");
      }

      printf("          Flags   :%s%s%s%s%s%s%s\n",
         UseHandles        ? " Handles" : "",
         PermitAnon        ? " Anonymous" : "",
         PermitPrivate     ? " Private" : "",
         PermitQuoting     ? " Quoting" : "",
         PermitDelUponRecv ? " DelUponRecv" : "",
         PermitDelToSelf   ? " DelToSelf" : "",
         PermitDelFromSelf ? " DelFromSelf" : "");

      printf("\
------------------------------------------------------------------------------\n");
   }
}
