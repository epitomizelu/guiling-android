package top.george888.guiling;
import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;

final class Vault {
    private static final String ALIAS="guiling.api.v1";
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("ledger",Context.MODE_PRIVATE);}
    private static javax.crypto.SecretKey secret() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias(ALIAS)) {KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}
        return (javax.crypto.SecretKey)ks.getKey(ALIAS,null);
    }
    static void save(Context c,String value) throws Exception {Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,secret());String encrypted=Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);prefs(c).edit().putString("token",encrypted).putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).commit();}
    static String read(Context c) throws Exception {String token=prefs(c).getString("token","");if(token.isEmpty())return "";Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,secret(),new GCMParameterSpec(128,Base64.decode(prefs(c).getString("iv",""),Base64.NO_WRAP)));return new String(cipher.doFinal(Base64.decode(token,Base64.NO_WRAP)),StandardCharsets.UTF_8);}
}
