#include <stdio.h>
#include <string.h>
#include <exec/types.h>

int main(int argc, char** argv)
{
    char	namn[50], vnamn[50];
    ULONG	checksum = 0, vcheck=0, verify = 0;
    FILE	*fh;
    UBYTE	i;
    
    size_t len;

    printf("\nWho is the lucky owner ? ");	
    gets(namn);

    for (i = 0 ; i<strlen(namn) ; i++) {
        checksum += namn[i];
    }
    checksum *= 5;
    checksum += strlen(namn) * strlen(namn);

    checksum += 'd';
    checksum += 'a';
    checksum += 'l';
    checksum += 'i';
    checksum += 'n';
    checksum += 'a';

    if (!(fh=fopen("ram:RYLWEEN.REG","wb"))) {
        printf("\nKunde inte |ppna regfilen f|r skrivning!\n\n");
        exit(10);
    }

    fwrite((char *)namn,sizeof(namn),1,fh);
    fwrite((char *)&checksum,sizeof(checksum),1,fh);
    fclose(fh);

    printf("\nOk. Regfil skapad f|r %s! (ChK: %d)\n\n",namn,checksum);

    printf("Testar regfilen:\n");
    if (!(fh=fopen("ram:RYLWEEN.REG","rb")))
    {
        printf("\nKunde inte |ppna regfilen f|r l{sning!\n\n");
        exit(10);
    }
    
    fread((char *)vnamn,sizeof(namn),1,fh);
    fread((char *)&vcheck,sizeof(checksum),1,fh);
    fclose(fh);

    for(i = 0 ; i < strlen(vnamn) ; i++) {
        verify += namn[i];
    }
    verify *= 5;
    verify += strlen(vnamn)*strlen(vnamn);

    verify += 'd';
    verify += 'a';
    verify += 'l';
    verify += 'i';
    verify += 'n';
    verify += 'a';

    printf("Namn: %s\nChecksum: %d (skall vara %d)\n\n",vnamn,vcheck,verify);
}
