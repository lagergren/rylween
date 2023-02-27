#include "dh0:cprogs/z/ryl_cli/ryl.h"

void ydarg()
{

	int room[8][8];
	int xl,yl,lvl,n,s,e,w,i,j,x,oc;
	char c,d;

	clr_map_window();
	blitgfx(ydargpic);
	clw();
	if(p[cpl].silverkey == 2) {
		tx("The once so mighty\r\n");
		tx("castle Zadraz is now\r\n");
		tx("nothing but a ruin.\r\n");
		tx("You have nothing else\r\n");
		tx("to do here.\r\n\r\n");
		pause();
		clr_map_window();
		update_map();
		clw();
		return;
	}
	tx("A black and foreboding\r\n");
	tx("castle looms over the\r\n");
	tx("horizon. Its cold, black\r\n");
	tx("stone walls are decorated\r\n");
	tx("with complex symbols and\r\n");
	tx("archaic runes of long\r\n");
	tx("forgotten language.\r\n\r\n");
	pause(); clw();
	tx("A shape of white mist\r\n");
	tx("slowly thickens to human\r\n");
	tx("shape. A transparent image\r\n");
	tx("of a beautiful woman\r\n");
	tx("forms in front of you.\r\n\r\n");
	pause(); clw();
	tx("- Do not be afraid,\r\n");
	tx("adventurers. I am Jaara,\r\n");
	tx("guardian of the gate.\r\n");
	tx("Baron Ydarg has imprisoned\r\n");
	tx("me here forever. I can't\r\n");
	tx("hurt you, just stop you\r\n");
	tx("from entering if you don't\r\n");
	tx("have the Silver Key!\r\n\r\n");
	pause(); clw();
	if(!p[cpl].silverkey) {
		tx("- You don't have the\r\n");
		tx("Key. The gate will\r\n");
		tx("remain sealed.\r\n\r\n");
		pause();
		clr_map_window();
		update_map();
		return;
	}
	tx("You show the Silver Key to\r\n");
	tx("the ghost. Her eyes widen,\r\n");
	tx("as she sees it. She smiles\r\n");
	tx("and waves her hand towards\r\n");
	tx("the drawbridge. It falls\r\n");
	tx("to the ground with a\r\n");
	tx("resounding KRASH. Jaara\r\n");
	tx("slowly fades away until\r\n");
	tx("nothing remains.\r\n\r\n");
	pause();
	clw();
	tx("You enter.\r\n\r\n");
	pause();
	clw();
	
	/*
		Bracelets of Thun
		Fire Shield
		Jazzy Foot
		Crown

		Monster, F{lla, Hitta Vapen, Hitta f|rem}l, Trappa upp
		4 levels. level 1 - Bracelets, Level 2 = fire shield, level 3 = foot
		level 4 = crown.

    XX XX XX XX XX

    XX XX XX XX XX

    XX XX XX XX XX

    XX XX XX XX XX

    XX XX XX XX XX

	*/

	tx("The first door you enter\r\n");
	tx("slams shut behind you!\r\n");
	Delay(100);
	clw();
	lvl=1; xl=1; yl=1;
	for(i=1 ; i<6 ; i++) {
		for(j=1 ; j<6 ; j++) {
			room[i][j] = (rand() % 5)+1;
		}
	}
	room[5][5] = 50;
	pause();

	while(1)
	{
		clw();

		switch(room[xl][yl])
		{
			case 1: /* Monster */
				room[xl][yl] = 0;
				tx("You are spotted by\r\n");
				tx("monsters!\r\n");
				Delay(75);
				encounter((rand() % 20)+60,0);
				clr_map_window();
				blitgfx(ydargpic);
				clw();
			break;

			case 2: /* Pitfall */
				room[xl][yl] = 0;
				tx("%s springs a\r\n",p[cpl].name[(rand() % 4)+1]);
				tx("trap!!!\r\n");
				Delay(75);
				clw();
				j=0;
				for(i=1 ; i<5 ; i++) {
					if(p[cpl].cl[i] == 2) {
						tx("%s tries to\r\n",p[cpl].name[i]);
						tx("disarm it...\r\n\r\n");
						Delay(75);
						if(rand() % 100 < p[cpl].wi[i]) {
							tx("AND SUCCEEDS!\r\n");
							Delay(50);
							j=1;
						}
						else {
							tx("BUT FAILS!\r\n");	
							Delay(50);
							j=0;
						}
						break;
					}
				}
				if(!j) {
					switch(rand() % 3)
					{
						case 0:
						tx("Arrows speed through\r\n");
						tx("the air and hits the\r\n");
						tx("party!\r\n\r\n");
						break;
						case 1:	
						tx("Poison needles shoot\r\n");
						tx("out from the floor!\r\n\r\n");
						break;
						case 2:
						tx("A volley of shurikens\r\n");
						tx("is hurled from a hatch\r\n");
						tx("in the wall!\r\n\r\n"); 
						break;
					}
					pause();
					for(i=1 ; i<5 ; i++) {
						if(!p[cpl].dead[i]) {
							p[cpl].hp[i] -= p[cpl].hp[i]/6;
						}
					}			
				}
			break;

			case 3:
				room[xl][yl] = 0;
				x=(rand() % 5);
				if(x) {
					x=(rand() % 3);
					if(x == 0) { oc=rand() % 14; oc +=   1; } /* 0-13 + 1 = 1-14 */
					if(x == 1) { oc=rand() % 7 ; oc +=  36; } /* 0-6  + 36= 36-42 */
					if(x == 2) { oc=rand() % 8 ; oc +=  39; } /* 0-7  + 39= 39-46 */
				}
				else { /* 0-11 + 100, ger 100-111 */
					oc=rand() % 12; oc += 100;
				}
				tx("You find a\r\n");
				tx("%s.\r\n\r\n",object(oc));
				tx("(T)ake it\r\n");
				tx("(L)eave it\r\n");
				c=key();
				c=toupper(c);
				if(c == 'T') {
					for(i=1,x=0 ; i<17 ; i++) {
						if(!p[cpl].inventory[i]) { p[cpl].inventory[i] = oc; x=1; break; }
					}					
					if(!x) {
						clw();
						tx("You can't carry more.\r\n");
						Delay(40);
					}
					else {	
						tx("\r\n");
						tx("Ok.\r\n");
						Delay(40);
					}
				}
			break;

			case 50:
				clw();
				tx("You stand before the\r\n");
				tx("stairs to the next level.\r\n\r\n");
				Delay(75);
				clw();
				tx("A mage imprisoned in a\r\n");
				tx("fiery sphere offers to\r\n");
				tx("open trapdoor at the top\r\n");
				tx("of the stairs if you\r\n");
				tx("give him an item of power!\r\n");
				tx("He also offers to teleport\r\n");
				tx("you out of the castle,\r\n");
				tx("for free.\r\n\r\n");
				pause();
				while(1)
				{
					clw();
					tx("Choose:\r\n\r\n");
					tx("(T)eleport Out\r\n");
					tx("(G)ive him an item\r\n");
					tx("(L)eave\r\n");
					while(1) {
						d=key();
						d=toupper(d);
						if(d=='T' || d=='G' || d=='L') break;
					}
					if(d == 'L') {
						clw();
						tx("You take the nearest exit!\r\n");
						Delay(50);
						yl--;
						break;
					}
					if(d == 'T') {
						clw();	
						tx("The world fades into a\r\n");
						tx("vortex of agonizing\r\n");
						tx("vertigo...\r\n\r\n");
						Delay(150);
						tx("And you are outside...\r\n");
						Delay(150);
						clr_map_window();
						update_map();
						clw();
						return;
					}
					clw();
					tx("Give him what?\r\n\r\n");
					tx("1. The Fire Shield\r\n");
					tx("2. The Master Crown\r\n");
					tx("3. The Foot of Jazzy\r\n");
					tx("4. The Bracelets of Thun\r\n");
					tx("5. Nothing\r\n");
					while(1) { d=key(); if(d>='1' && d<='5') break; }
					j=0;
					switch(d-48) {
						case 1: 
							if(!p[cpl].fireshield) {
								clw();
								tx("You don't have it!\r\n");
								Delay(75);
								break;
							}
							p[cpl].fireshield = 0;
							j=1;
						break;
						case 2:
							if(!p[cpl].mastercrown) {
								clw();
								tx("You don't have it!\r\n");
								Delay(75);
								break;
							}
							p[cpl].mastercrown = 0;
							j=1;
						break;
						case 3:
							if(!p[cpl].jazzyfoot) {
								clw();
								tx("You don't have it!\r\n");
								Delay(75);
								break;
							}
							p[cpl].jazzyfoot = 0;
							j=1;
						break;
						case 4:
							if(!p[cpl].bracelets) {
								clw();
								tx("You don't have them!\r\n");
								Delay(75);
								break;
							}
							p[cpl].bracelets = 0;
							j=1;
						break;
					}
					if(j) {
						clw();
						tx("The sphere fades away\r\n");
						tx("and the mage steps out.\r\n");
						tx("He is grateful. Before\r\n");
						tx("he teleports away, he\r\n");
						tx("casts an OPEN spell on the\r\n");
						tx("trapdoor above and speaks:\r\n");
						tx("- I will aid thee in the\r\n");
						tx("final battle!\r\n\r\n");	
						pause();
						clw();
						xl=1; yl=1; lvl++;
						if(lvl == 5) confrontation();
						break;
					}
				}
			break;
		}

		clw();
		statusline();
		tx("You are at %dx%d.\r\n\r\n",xl,yl,lvl);
		tx("You are on level %d.\r\n",lvl);
		switch(lvl)
		{
			case 1: tx("(Ground Level)\r\n\r\n");  break;
			case 2: tx("(First Floor)\r\n\r\n");   break;
			case 3: tx("(Base of Tower)\r\n\r\n"); break;
			case 4: tx("(Top of Tower)\r\n\r\n");  break;
		}
		n=0;s=0;e=0;w=0;
		if(yl > 1) n=1;
		if(xl > 1) w=1;
		if(xl < 5) e=1;
		if(yl < 5) s=1;
		if(n) {
					tx("    8  North  \r\n");
					tx("    |\r\n");
		}
		else  tx("\r\n\r\n");

if(e && w)tx(" 4 -+- 6  East/West\r\n"); 
if(e &&!w)tx("    +- 6  East\r\n");
if(!e&& w)tx(" 4 -+     West\r\n");
		if(s) {		
					tx("    |\r\n");
					tx("    2  South  \r\n");
		}
					
		c=key();
		c=toupper(c);
		if(c == '8' && n) { clw(); tx("North.\r\n"); Delay(15); clw(); yl--; }
		if(c == '2' && s) { clw(); tx("South.\r\n"); Delay(15); clw(); yl++; }
		if(c == '6' && e) { clw(); tx("East.\r\n");  Delay(15); clw(); xl++; }
		if(c == '4' && w) { clw(); tx("West.\r\n");  Delay(15); clw(); xl--; }
		if(c == '?') help();
		if(c == 'T') armouroff();
		if(c == 'P') weaponoff();
		if(c == 'L') drop();
		if(c == 'W') weaponr();
		if(c == 'R') armourr();
		if(c == 'Q') quaff();
		if(c == 'Z') spell(0); /* 0 means over ground */		
		if(c == 'V') {
			showparty();
			clw();
			tx("Who? (1-4/Q/A)\r\n");
			while(1) { d=key(); if((d >= '1' && d <= '4') || toupper(d) == 'Q' || toupper(d) == 'A') break; }
			if(d >= '1' && d <= '4') show(d-48);
			if(toupper(d) == 'A') { for(i=1 ; i<5 ; i++) show(i); }
			clw();
		}				
	}
}

void confrontation()
{
	FILE *fh;
	char str[80];
	char kl[8];
	char mstr[15];

	clw();
	tx("The last trapdoor\r\n");
	tx("slams shut behind you.\r\n");
	tx("You are high above the\r\n");
	tx("forest, on the roof\r\n");
	tx("of this fortress.\r\n");
	tx("Suddenly, a voice speaks:\r\n\r\n");
	pause(); clw();
	tx("- Fools! You have gone\r\n");
	tx("long to come this far, but\r\n");
	tx("here ends your journey!\r\n\r\n");
	tx("The night sky above lights\r\n");
	tx("up with green flashes, and\r\n");
	tx("Ydargs minions rush\r\n");
	tx("to attack you!\r\n\r\n");
	pause(); clw();
	encounter(26,1);
	encounter(27,1);
	encounter(28,1);
	encounter(29,1);
	clw();
	tx("Corpses of the nightmare\r\n");
	tx("terrors are strewn all\r\n");
	tx("around. A dark cloaked\r\n");
	tx("figure appears before you.\r\n");
	tx("He takes a broad step over\r\n");
	tx("the dead and dying, and\r\n");
	tx("gazes at you with cold\r\n");
	tx("glowing green eyes.\r\n\r\n");
	pause();
	clw();
	tx("- So. You have completed\r\n");
	tx("the battle test. I must\r\n");
	tx("confess that you impress\r\n");
	tx("me. But now...\r\n\r\n");
	tx("(He waves a pointed stick\r\n");
	tx("and energy begins to form\r\n");
	tx("in the air before you.)\r\n\r\n");
	pause();
	clw();
	tx("A glowing energy sphere\r\n");
	tx("appears, and at a gesture\r\n");
	tx("from Ydarg's stick, it\r\n");
	tx("hurtles towards you!!!\r\n\r\n");
	pause();
	clw();
	tx("Suddenly! You feel someone\r\n");
	tx("else near. %s turns\r\n",p[cpl].name[1]);
	tx("around and discovers the\r\n");
	tx("four grey-clad mages!\r\n");
	tx("They simultaneously raise\r\n");
	tx("their hands, and the\r\n");
	tx("sphere bounces over you\r\n");
	tx("and is absorbed by their\r\n");
	tx("hands!\r\n\r\n");
	pause();
	clw();
	tx("- CURSES! Ydarg growls.\r\n");
	tx("- So this is what has come\r\n");
	tx("to! Well, you got here by\r\n");
	tx("aid of cold steel, and\r\n");
	tx("that is the way you shall\r\n");
	tx("perish!\r\n\r\n");
	pause();
	clw();
	tx("He pulls a huge trident\r\n");
	tx("from his robe and utters\r\n");
	tx("a single syllable. Without\r\n");
	tx("warning he appears a foot\r\n");
	tx("before you!!!\r\n\r\n");
	pause();
	clw();
	tx("Let the battle commence!\r\n\r\n");
	pause();
	encounter(30,1);
	clw();
	tx("Ydarg's trident clatters\r\n");
	tx("to the ground before you.\r\n");
	tx("He shudders and falls over.\r\n");
	tx("As you rush to kill him\r\n");
	tx("he screams:\r\n\r\n");
	pause();
	clw();
	tx("- It is not over! I shall\r\n");
	tx("see you in hell! In\r\n");
	tx("another world! A time\r\n");
	tx("to come! You have tainted\r\n");
	tx("Zadraz with your ancient\r\n");
	tx("items, but I will triumph\r\n");
	tx("again!!! Anew...\r\n\r\n");
	pause();
	clw();
	tx("His cloak collapses, and\r\n");
	tx("when you tear it away,\r\n");
	tx("you reveal nothing but...\r\n\r\n");
	pause();
	clw();
	tx("dust...\r\n\r\n");
	pause();
	clw();
	tx("[31mCONGRATULATIONS![37m\r\n\r\n");
	tx("You have banished Ydarg\r\n");
	tx("from this dimension.\r\n");
	tx("Please ask your SysOp to\r\n");
	tx("reset the game.\r\n\r\n");
	pause();
	clw();
	p[cpl].silverkey = 2;
	save();
	tx("Be ready for:\r\n");
	tx("[31mHEROES OF RYLWEEN[37m\r\n");
	tx("- The second coming.\r\n");
	tx("Soon online at a BBS\r\n");
	tx("near you!\r\n\r\n");
 	pause();
	clw();
	tx("See Ya!!!\r\n\r\n");
	tx("     /Marcus\r\n\r\n");
	pause();
	clw();

	if(!(fh=fopen("Doors:Rylween/Heroes.TXT","r")))
	{
		fh=fopen("Doors:Rylween/Heroes.TXT","w");
		sprintf(str,"%c\n",12);
		fputs(str,fh);
		fputs(" +============================================================================+\n",fh);
		fputs(" I              ADVENTURERS OF RYLWEEN - HALL OF NATIONAL HEROES              I\n",fh);
		fputs(" +============================================================================+\n\n",fh);
		fclose(fh);
	}

	if(!(fh=fopen("Doors:Rylween/Heroes.TXT","a+"))) return;

	getclk(kl);

	switch(kl[2])
	{
		case  1: strcpy(mstr,"January");		break; 
		case  2: strcpy(mstr,"February");		break; 
		case  3: strcpy(mstr,"March");			break; 
		case  4: strcpy(mstr,"April");			break; 
		case  5: strcpy(mstr,"May");				break; 
		case  6: strcpy(mstr,"June");				break; 
		case  7: strcpy(mstr,"July");				break; 
		case  8: strcpy(mstr,"August");			break; 
		case  9: strcpy(mstr,"September");	break; 
		case 10: strcpy(mstr,"October");		break; 
		case 11: strcpy(mstr,"November");		break; 
		case 12: strcpy(mstr,"December");		break; 
	}

	sprintf(str,"  %s defeated baron Ydarg %s %d, 19%d\n",p[cpl].partyname,mstr,kl[3],kl[1]+80);
	fputs(str,fh);
	fclose(fh);
	cleanexit();
}
