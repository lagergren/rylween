#include <stdio.h>

extern struct person {

	int  job;				// 1 Merchant

	int  nr;				// Reference number 1-40
	int  towne;			// What town? 0=wilderness
	int  bl;				// What building? 1=inn, 2=tavern. Not valid for wilderness
	int  x;					// What X coord.
	int  y;					// What Y coord. Not valid for townes.
									// The wilderness X&Y coord automagically become
									// HH (hut), if they are set for a character
	unsigned char visited[26];		// Start at 0. Visited before???

};

main()
{
	FILE *fh;
	int   i;
	struct person p;

	if(!(fh=fopen("Doors:Rylween/npc.dat","rb"))) exit(1);

	for(i=1 ; i<40 ; i++) {
		fread((char *)&p,sizeof(p),1,fh);
		printf("Player: %d - Job %d - Nr %d - Towne %d - X %d - Y %d - BL %d\n",i,p.job,p.nr,p.towne,p.x,p.y,p.bl);
	}

	fclose(fh);
}
