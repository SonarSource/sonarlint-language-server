package org.sonarsource.sonarlint.ls;

import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

public class K {

    public static int TEST = 0;

    public static void main(String[] args) throws NoSuchAlgorithmException, NoSuchPaddingException {
        while (true){
           // baz();
        }

    }


    static void baz() throws NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher c0 = Cipher.getInstance("AES"); // Noncompliant: by default ECB mode is chosen
        Cipher c1 = Cipher.getInstance("AES/ECB/NoPadding"); // Noncompliant: ECB doesn't provide serious message confidentiality
        Cipher c3 = Cipher.getInstance("Blowfish/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality
        Cipher c4 = Cipher.getInstance("DES/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality

        Cipher c6 = Cipher.getInstance("AES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
        Cipher c7 = Cipher.getInstance("Blowfish/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
        Cipher c8 = Cipher.getInstance("DES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
        Cipher c9 = Cipher.getInstance("AES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
        Cipher c10 = Cipher.getInstance("Blowfish/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
        Cipher c11 = Cipher.getInstance("DES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks

        Cipher c14 = Cipher.getInstance("RSA/NONE/NoPadding"); // Noncompliant: RSA without OAEP padding scheme is not recommanded

    }

    /**
     *
     * @param i - some integer
     * @param c - same character
     * @return - constant double
     */
    static Double foo(Integer i, Character c) {
        return 2.0;
    }

    static void bar() throws NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher c0 = Cipher.getInstance("AES"); // Noncompliant: by default ECB mode is chosen
        Cipher c1 = Cipher.getInstance("AES/ECB/NoPadding"); // Noncompliant: ECB doesn't provide serious message confidentiality
        Cipher c3 = Cipher.getInstance("Blowfish/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality
        Cipher c4 = Cipher.getInstance("DES/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality

        Cipher c6 = Cipher.getInstance("AES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
        Cipher c7 = Cipher.getInstance("Blowfish/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
        Cipher c8 = Cipher.getInstance("DES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
        Cipher c9 = Cipher.getInstance("AES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
        Cipher c10 = Cipher.getInstance("Blowfish/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
        Cipher c11 = Cipher.getInstance("DES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks

        Cipher c14 = Cipher.getInstance("RSA/NONE/NoPadding"); // Noncompliant: RSA without OAEP padding scheme is not recommanded
    }

    static void baZ() throws NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher c0 = Cipher.getInstance("AES"); // Noncompliant: by default ECB mode is chosen
        Cipher c1 = Cipher.getInstance("AES/ECB/NoPadding"); // Noncompliant: ECB doesn't provide serious message confidentiality
        Cipher c3 = Cipher.getInstance("Blowfish/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality
        Cipher c4 = Cipher.getInstance("DES/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality

        Cipher c6 = Cipher.getInstance("AES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
        Cipher c7 = Cipher.getInstance("Blowfish/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
        Cipher c8 = Cipher.getInstance("DES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
        Cipher c9 = Cipher.getInstance("AES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
        Cipher c10 = Cipher.getInstance("Blowfish/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
        Cipher c11 = Cipher.getInstance("DES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks

        Cipher c14 = Cipher.getInstance("RSA/NONE/NoPadding"); // Noncompliant: RSA without OAEP padding scheme is not recommanded
    }

    static void bAz() throws NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher c0 = Cipher.getInstance("AES"); // Noncompliant: by default ECB mode is chosen
        Cipher c1 = Cipher.getInstance("AES/ECB/NoPadding"); // Noncompliant: ECB doesn't provide serious message confidentiality
        Cipher c3 = Cipher.getInstance("Blowfish/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality
        Cipher c4 = Cipher.getInstance("DES/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality

        Cipher c6 = Cipher.getInstance("AES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
        Cipher c7 = Cipher.getInstance("Blowfish/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
        Cipher c8 = Cipher.getInstance("DES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
        Cipher c9 = Cipher.getInstance("AES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
        Cipher c10 = Cipher.getInstance("Blowfish/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
        Cipher c11 = Cipher.getInstance("DES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks

        Cipher c14 = Cipher.getInstance("RSA/NONE/NoPadding"); // Noncompliant: RSA without OAEP padding scheme is not recommanded
    }

    static void Baz() throws NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher c0 = Cipher.getInstance("AES"); // Noncompliant: by default ECB mode is chosen
        Cipher c1 = Cipher.getInstance("AES/ECB/NoPadding"); // Noncompliant: ECB doesn't provide serious message confidentiality
        Cipher c3 = Cipher.getInstance("Blowfish/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality
        Cipher c4 = Cipher.getInstance("DES/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality

        Cipher c6 = Cipher.getInstance("AES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
        Cipher c7 = Cipher.getInstance("Blowfish/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
        Cipher c8 = Cipher.getInstance("DES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
        Cipher c9 = Cipher.getInstance("AES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
        Cipher c10 = Cipher.getInstance("Blowfish/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
        Cipher c11 = Cipher.getInstance("DES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks

        Cipher c14 = Cipher.getInstance("RSA/NONE/NoPadding"); // Noncompliant: RSA without OAEP padding scheme is not recommanded
    }

}
