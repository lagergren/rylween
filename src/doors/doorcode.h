/* Use a #define to check for carrier, for convenience */
#define CARRIER_TEST  { if (!XCBD->CarrierPresent) exit(1); }

#define USER (XCBD->UserStructure)

extern void *_ONEXIT;


/* Xenolink callback functions */
struct XenolinkCallBack     *XCB;

/* Xenolink callback data */
struct XenolinkCallBackData *XCBD;


struct SharedData   *Shared;
struct XenolinkBase *XenolinkBase;


void ExitRoutine(void);
void InitLibraryBases(void);




UBYTE TempString[2048];

long wprintf(char *fmt, ...)
{
   va_list var_args;
   long    Length;

   va_start(var_args, fmt);
   Length = vsprintf(TempString, fmt, var_args);
   va_end(var_args);

   DisplayText(TempString, Length);

   return (Length);
}


void ExitRoutine(void)
{
   if (XenolinkBase)
   {
      CloseLibrary((struct Library *) XenolinkBase);
      XenolinkBase = NULL;
   }
}


void InitLibraryBases(void)
{
   Shared = XCBD->SharedDataSegment;

   if (!(XenolinkBase = (struct XenolinkBase *) OpenLibrary("xenolink.library", 0)))
   {
      exit(1);
   }
}
