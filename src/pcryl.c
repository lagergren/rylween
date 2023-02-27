/* MAIN LOOP - This module differs between Amiga and PC */

#include "dh0:cprogs/z/ryl_cli/ryl.h"

struct XenolinkCallBack *XCB;
struct XenolinkCallBackData *XCBD;
struct XenolinkBase *XenolinkBase;

int  tid,cpl,antal_spelare,global_x,senaste_dagen,twn;
int  hours_pigg;
int  banished;
int  dx[15],dy[15];
int  cli;
int  reg;
int  day_at_game_start;
char map[42][82];
char global_name[30];
char last_terrain;

struct party p[26];
struct person npc[41];

char *jobname[] = {
	"",
	"Merchant",
	"Ranger",
	"Fighter",
	"Wizard",
	"Priest",
	"Thief",
	"Peasant",
	"Bard",
	"Drifter",
	"Half-Orc",
	"Half-Elf",
	"Smith",
	"Bargirl",
	"Innkeeper",
	"Tavernkeeper",
	"Hermit"
};

char *name[] = {
	"",
	"Garth",
	"Nrim'to",
	"Areth",
	"Nevaak",
	"Gorm",
	"Bernak",
	"Matias",
	"Tybalt",
	"Romeo",
	"Werra",
	"Arenjun",
	"Oskar",	
	"Harry",
	"Marraga",
	"Broth",
	"Ebond",
	"Bolph",	
	"Gotharp",
	"Ron",
	"Kaal",
	"Daniel",
	"Leander",
	"Ithienn",
	"Azaaza",
	"Hassan",
	"Luqas",
	"Darth",
	"Yog",
	"Niklas",
	"Tokawa",
	"Miyaki",
	"Dargoth",
	"Marcus",
	"Deanna",
	"Jeanne",
	"Alex",
	"Galdon",
	"Kosch",
	"Ufarsin"
};

char *_version_string = "\0 $VER: Adventurers of Rylween v3 - By Marcus Lagergren "__DATE__;

void main(int argc, char **argv)
{
	int i;

	cli = 0;

	if(argc < 2)	exit(1);

	if(!stricmp(argv[1],"CLI")) {
		cli=1;
		global_name[0] = 0;
		strcpy(global_name,argv[2]);
		if(argc > 3) {
			for(i=3 ; i<argc ; i++) {
				strcat(global_name," ");
				strcat(global_name,argv[i]);
			}	
		}
	}

	if(!cli) {
		XCB 	=	(struct XenolinkCallBack *) atol(argv[1]);
		XCBD	= XCB->XCBD;
		strcpy(global_name,XCBD->UserStructure->Name);
	}

	init();
	exit(0);
}

void cl()
{
	tx("%c",12);
}

char* getstr(UBYTE lg)
{
	char svar[90];

	if(!cli) {
		if(!carrier()) cleanexit();
		(*XCB->GetLine)(svar,lg,0,GL_APPEND_NEW_LINE);
		if(svar[0] == '#') cleanexit();
		if(!carrier()) cleanexit();
	}
	else gets(svar);

	return svar;
}
void clw()
{
	int i;

	c(49,6);
	for(i=1 ; i<12 ; i++) tx("                          \r\n");
	c(49,6);
}

void c(UBYTE x, UBYTE y)
{
	UBYTE pos[16];

	global_x = x;

	sprintf(pos,"[%d;0H",y);
	if(!cli) (*XCB->DisplayText)(pos,strlen(pos));
	else printf("%s",pos);
}

ULONG tx(UBYTE *fmtstr, ...)
{
	UBYTE buffer[256];
	UBYTE jump[8];
	va_list var_args;
	ULONG length = 0L;

	sprintf(jump,"[%dC",global_x);
	if(!cli) (*XCB->DisplayText)(jump,strlen(jump));
	else printf("%s",jump);

	va_start(var_args, fmtstr);
	length = vsprintf(buffer, fmtstr, var_args);
	va_end(var_args);

	if(length) {
		if(!cli) (*XCB->DisplayText)(buffer, length);
		else printf("%s",buffer);
	}

	return(length);
}

UBYTE carrier() {
	if(!cli) {
		if(!(XCBD->CarrierPresent)) return 0;
		else return 1;
	}
}

void cleanexit()
{
	tx("[30m%c%c%c%c[37m",27,91,32,112);	
	exit(0);
}

UBYTE hotkey(void)
{
	char c;

	if(!cli) {
		c=(*XCB->GetCharacter)();
		if(toupper(c)=='Y') return 1;
		else return 0;
	}
	else {
		c=getch();
		if(toupper(c) == 'Y') return 1;
		else return 0;
	}
}
void log(char *st) 
{
	FILE *fh;
	char  kl[8];
	char  mon[5];
	char  str[144],str2[80];
	
	getclk(kl);

	switch(kl[2])
	{
		case 1:  strcpy(mon,"Jan"); break;
		case 2:  strcpy(mon,"Feb"); break;
		case 3:  strcpy(mon,"Mar"); break;
		case 4:  strcpy(mon,"Apr"); break;
		case 5:  strcpy(mon,"May"); break;
		case 6:  strcpy(mon,"Jun"); break;
		case 7:  strcpy(mon,"Jul"); break;
		case 8:  strcpy(mon,"Aug"); break;
		case 9:  strcpy(mon,"Sep"); break;
		case 10: strcpy(mon,"Oct"); break;
		case 11: strcpy(mon,"Nov"); break;
		case 12: strcpy(mon,"Dec"); break;
 	}
	
	sprintf(str,"%s %2d %2d:",mon,kl[3],kl[4]);
	if(kl[5]<10) strcat(str,"0");
	sprintf(str2,"%d %s\n",kl[5],st);
	strcat(str,str2);

	if(!(fh=fopen("Doors:Rylween/Rylween.LOG","a+"))) return;
	fputs(str,fh);
	fclose(fh);
}
void disptext(char *namn)
{
	FILE *fh;
	char  line[90];

	if(!(fh=fopen(namn,"r"))) return;

	if(!cli)
	{
		fclose(fh);
		(*XCB->DisplayTextFile)(namn,CONTINUOUS,TF_PROCESS_TILDE,0,0);
		return;
	}

	while(1) {
		line[0] = 0;
		fgets((char *)line,sizeof(line),fh);
		if(line[0] == 0) break;		
		//line[77] = 0;
		printf("%s",line);
	}

	fclose(fh);
}
