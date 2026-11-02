package com.brendigo.fakturiva;

import org.junit.Test;
import static org.junit.Assert.*;



/** Regression checks for native client list/detail presentation. */
@SuppressWarnings("deprecation")
public final class ClientScreensTest extends FakturivaTestCase {
    private Store.Client client(long id,String name,String address,String oib){
        Store.Client c=new Store.Client();
        c.id=id;c.name=name;c.address=address;c.oib=oib;
        c.email="";c.phone="";c.vatId="";
        return c;
    }

    @Test
    public void testInitialsFromTwoWordsAndSafeFallback(){
        assertEquals("SS",MainActivity.clientInitials("Studio Sjever d.o.o."));
        assertEquals("MK",MainActivity.clientInitials("Marin Kovač"));
        assertEquals("ŽŠ",MainActivity.clientInitials("Živko Šimić"));
        assertEquals("K",MainActivity.clientInitials(" "));
        assertEquals("K",MainActivity.clientInitials(null));
    }

    @Test
    public void testCityIsNeverInventedFromStreetOnly(){
        assertEquals("Rijeka",MainActivity.clientCity("Trg Primjera 8, Rijeka"));
        assertEquals("Zagreb",MainActivity.clientCity("Zagreb, Hrvatska"));
        assertEquals("",MainActivity.clientCity("Trg Primjera 8"));
        assertEquals("",MainActivity.clientCity(""));
        assertEquals("Pula",MainActivity.clientCity("Pula"));
    }

    @Test
    public void testClientFiltersUseRealDocumentMembership(){
        Store.Client a=client(1,"Studio Sjever","Zagreb","12345678903");
        Store.Client b=client(2,"Studio Sjever","Zagreb","12345678903");
        assertTrue(MainActivity.clientMatches(a,"sjever","S dokumentima",true));
        assertFalse(MainActivity.clientMatches(b,"sjever","S dokumentima",false));
        assertTrue(MainActivity.clientMatches(b,"sjever","Bez dokumenata",false));
        assertFalse(MainActivity.clientMatches(a,"sjever","Bez dokumenata",true));
        assertTrue(MainActivity.clientMatches(a,"12345678903","Svi",true));
        assertFalse(MainActivity.clientMatches(a,"Split","Svi",true));
    }
}
