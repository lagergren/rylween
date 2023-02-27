/****************************************************************************/
/* XENOLINK Developer's Toolkit Release 7 - Compatible with 1.90 and above  */
/*                                                                          */
/* LOOKUP.C - Displays information on a node in the nodelist                */
/*                                                                          */
/****************************************************************************/

#include <exec/types.h>
#include <exec/memory.h>
#include <exec/semaphores.h>
#include <clib/exec_protos.h>
#include <libraries/dos.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <time.h>
#include "XDOOR:xenolink.h"


struct XenolinkBase   *XenolinkBase;
struct SharedData     *Shared;


/* Function prototypes */
void Init(void);
void Lookup(UBYTE *Address);


main(int argc, char **argv)
{
   if (argc < 2)
   {
      printf("\nSyntax: LOOKUP <Node>\n\n");
      exit(1);
   }

   /* Open xenolink.library and get pointer to shared data segment */
   Init();

   Lookup(argv[1]);

   /* Close library */
   CloseLibrary((struct Library *) XenolinkBase);

   exit(0);
}


void Init(void)
{
   /* Open xenolink.library */
   if (!(XenolinkBase = (struct XenolinkBase *) OpenLibrary("xenolink.library", 0L)))
   {
      printf("\n[LOOKUP] Program Failure:  Couldn't open xenolink.library\n\n");
      exit(1);
   }

   /* Get the shared data segment */
   if (!(Shared = ulibGetSharedData()))
   {
      printf("\n[LOOKUP] Program Failure:  The Xenolink Manager is not running\n\n");
      CloseLibrary((struct Library *) XenolinkBase);
      exit(1);
   }
}


void Lookup(UBYTE *Address)
{
   struct FidoNetAddress     Addr;
   struct GetNodelistCommand Cmd;
   struct XenolinkNode       DataBlock;
   struct NodelistEntry      Data;

   bzero(&Addr, sizeof(struct FidoNetAddress));
   bzero(&Cmd, sizeof(struct GetNodelistCommand));

   /* If you want a default zone (if the user provides no zone) then set */
   /* Addr.Zone equal to your default zone */
   if (!ulibParseFidoNetAddress(Address, &Addr))
   {
      Cmd.Fido.Zone  = Addr.Zone;
      Cmd.Fido.Net   = Addr.Net;
      Cmd.Fido.Node  = Addr.Node;
      Cmd.Fido.Point = Addr.Point;

      Cmd.Data  = &Data;
      Cmd.Block = &DataBlock;

      if (!ulibGetNodelistInfo(&Cmd))
      {
         printf("Node information\n");
         printf("----------------\n\n");

         printf("System name    : %s\n", DataBlock.BoardName);
         printf("Telephone #    : %s\n", DataBlock.Telephone);
         printf("Location       : %s\n", DataBlock.Location);
         printf("Sysop          : %s\n", DataBlock.Sysop);
         printf("Baud rate      : %ld\n", DataBlock.BaudRate);
         printf("Cost           : %ld\n\n", DataBlock.Cost);
      }
      else
      {
         printf("[LOOKUP] Program Failure:  Couldn't find node %ld:%ld/%ld.%ld\n",
            Addr.Zone, Addr.Net, Addr.Node, Addr.Point);
      }
   }
   else
   {
      printf("[LOOKUP] Program Failure:  Invalid address entered\n");
   }
}
