package com.yaoshiji.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores WebDAV credentials locally. The password is encrypted with Android Keystore. */
public final class SecurePrefs {
    private static final String PREF="webdav_config_v1";
    private static final String ALIAS="yaoshiji_webdav_key_v1";
    private static SharedPreferences sp(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    public static String url(Context c){return sp(c).getString("url","");}
    public static String user(Context c){return sp(c).getString("user","");}
    public static String dir(Context c){return sp(c).getString("dir","/YaoShiJi/");}
    public static boolean autoBackup(Context c){return sp(c).getBoolean("auto",false);}
    public static long lastBackup(Context c){return sp(c).getLong("last_backup",0);}
    public static void setLastBackup(Context c,long v){sp(c).edit().putLong("last_backup",v).apply();}
    public static void saveBasic(Context c,String url,String user,String dir,boolean auto){sp(c).edit().putString("url",url).putString("user",user).putString("dir",dir).putBoolean("auto",auto).apply();}
    public static boolean configured(Context c){return !url(c).trim().isEmpty();}

    public static void setPassword(Context c,String pass){
        try{
            SecretKey key=key();Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);
            byte[] enc=cipher.doFinal(pass.getBytes(StandardCharsets.UTF_8));
            String iv=Base64.getEncoder().encodeToString(cipher.getIV());String data=Base64.getEncoder().encodeToString(enc);
            sp(c).edit().putString("pass_iv",iv).putString("pass_data",data).apply();
        }catch(Exception e){sp(c).edit().remove("pass_iv").remove("pass_data").apply();}
    }
    public static String password(Context c){
        try{
            String iv=sp(c).getString("pass_iv","");String data=sp(c).getString("pass_data","");if(iv.isEmpty()||data.isEmpty())return "";
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.getDecoder().decode(iv)));
            return new String(cipher.doFinal(Base64.getDecoder().decode(data)),StandardCharsets.UTF_8);
        }catch(Exception e){return "";}
    }
    private static SecretKey key() throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias(ALIAS))return ((KeyStore.SecretKeyEntry)ks.getEntry(ALIAS,null)).getSecretKey();
        KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return kg.generateKey();
    }
}
