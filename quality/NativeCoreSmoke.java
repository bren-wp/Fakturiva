package com.brendigo.fakturiva;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Dependency-free smoke tests for the most sensitive pure-Java routines. */
public final class NativeCoreSmoke {
    private static void eq(long wanted,long actual,String name){
        if(wanted!=actual)throw new AssertionError(name+": expected "+wanted+" but got "+actual);
    }
    private static void require(boolean condition,String name){
        if(!condition)throw new AssertionError(name);
    }
    public static void main(String[] args)throws Exception{
        eq(123456,Money.cents("1.234,56"),"Croatian amount");
        eq(123456,Money.cents("1234.56"),"Dot decimal");
        eq(1,Money.cents("0,005"),"Half-up cents");
        eq(1250,Money.quantity("1,25"),"Quantity in millesimals");
        eq(1250,Money.subtotal(1250,1000),"Line net");
        eq(249,Money.tax(995,25),"Line VAT");
        require(Money.euros(123456).contains("1.234"),"Localized grouping");
        boolean invalid=false;
        try{Money.quantity("0");}catch(IllegalArgumentException expected){invalid=true;}
        require(invalid,"Zero quantity rejected");
        invalid=false;try{Money.tax(100,99);}catch(IllegalArgumentException expected){invalid=true;}
        require(invalid,"Unknown VAT rate rejected");
        byte[] original="Fakturiva šifrirana kopija".getBytes(StandardCharsets.UTF_8);
        char[] password="test-backup-password-2026".toCharArray();
        byte[] encrypted=SecureBackup.encrypt(original,password);
        require(!Arrays.equals(original,encrypted),"Ciphertext differs");
        require(Arrays.equals(original,SecureBackup.decrypt(encrypted,password)),"Correct password decrypts");
        byte[] tampered=encrypted.clone();tampered[tampered.length-1]^=1;
        boolean rejected=false;try{SecureBackup.decrypt(tampered,password);}catch(Exception expected){rejected=true;}
        require(rejected,"Tampered backup rejected");
        rejected=false;try{SecureBackup.decrypt(encrypted,"wrong-password".toCharArray());}catch(Exception expected){rejected=true;}
        require(rejected,"Wrong password rejected");
        Arrays.fill(password,'\0');System.out.println("Fakturiva native core checks OK");
    }
}
