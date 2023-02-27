/* Function prototypes */
void                InitLibraryBases();
long                wprintf(char *fmt, ...);
long                cprintf(char *fmt, ...);
void                NewLine();
UBYTE               LostCarrier();

#ifdef DOOR_FUNCTION_MACROS
#define GetLine (*XCB->GetLine)
#define DoorTerminating (*XCB->DoorTerminating)
#define GetCharacter (*XCB->GetCharacter)
#define MayGetCharacter (*XCB->MayGetCharacter)
#define FlushInputBufer (*XCB->FlushInputBufer)
#define PushToInputBuffer (*XCB->PushToInputBuffer)
#define ConfirmYN (*XCB->ConfirmYN)
#define MoreYNC (*XCB->MoreYNC)
#define DisplayText (*XCB->DisplayText)
#define ScreenDisplay (*XCB->ScreenDisplay)
#define ModemDisplay (*XCB->ModemDisplay)
#define Cls (*XCB->Cls)
#define DisplayTextFile (*XCB->DisplayTextFile)
#define SerialRead (*XCB->SerialRead)
#define SerialWrite (*XCB->SerialWrite)
#define AsyncSerialWrite (*XCB->AsyncSerialWrite)
#define AsyncSerialWait (*XCB->AsyncSerialWait)
#define PurgeSerialLine (*XCB->PurgeSerialLine)
#define ChangeBaudRate (*XCB->ChangeBaudRate)
#define OpenSerialDevice (*XCB->OpenSerialDevice)
#define CloseSerialDevice (*XCB->CloseSerialDevice)
#define GetNodeStatusVariable (*XCB->GetNodeStatusVariable)
#define SetNodeStatusVariable (*XCB->SetNodeStatusVariable)
#define GetUserVariable (*XCB->GetUserVariable)
#define SetUserVariable (*XCB->SetUserVariable)
#define GetMsgVariable (*XCB->GetMsgVariable)
#define SetMsgVariable (*XCB->SetMsgVariable)
#define GetFileVariable (*XCB->GetFileVariable)
#define SetFileVariable (*XCB->SetFileVariable)
#define sprintf (*XCB->sprintf)
#define vsprintf (*XCB->vsprintf)
#define TimeLeft (*XCB->TimeLeft)
#define CheckCarrier (*XCB->CheckCarrier)
#define DisplayCharacter (*XCB->DisplayCharacter)
#define GetKeyboardMsg (*XCB->GetKeyboardMsg)
#define DisplayCursor (*XCB->DisplayCursor)
#define ProtocolSend (*XCB->ProtocolSend)
#define ProtocolReceive (*XCB->ProtocolReceive)
#endif
