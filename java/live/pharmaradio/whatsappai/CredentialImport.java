package live.pharmaradio.whatsappai;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/** One-use encrypted import. Only the shell-authenticated provider exposes this. */
final class CredentialImport {
    private static final String ALIAS="whatsapp-ai-import-once";
    private static KeyStore store() throws Exception {
        KeyStore s=KeyStore.getInstance("AndroidKeyStore"); s.load(null); return s;
    }
    static synchronized String publicKey(Context c) throws Exception {
        if(KeyVault.configured(c)) throw new IllegalStateException("Cle deja configuree");
        KeyStore s=store();
        if(!s.containsAlias(ALIAS)) {
            KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA","AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(4096).setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP).build());
            generator.generateKeyPair();
        }
        return Base64.encodeToString(s.getCertificate(ALIAS).getPublicKey().getEncoded(),Base64.NO_WRAP);
    }
    static synchronized void receive(Context c,String encoded) throws Exception {
        if(KeyVault.configured(c)) throw new IllegalStateException("Cle deja configuree");
        if(encoded==null||encoded.length()>1024) throw new IllegalArgumentException("Import invalide");
        byte[] encrypted=Base64.decode(encoded,Base64.NO_WRAP), plain=null;
        if(encrypted.length!=512) throw new IllegalArgumentException("Import invalide");
        KeyStore s=store();
        if(!s.containsAlias(ALIAS)) throw new IllegalStateException("Import non prepare");
        try {
            Cipher cipher=Cipher.getInstance("RSA/ECB/OAEPPadding");
            cipher.init(Cipher.DECRYPT_MODE,(PrivateKey)s.getKey(ALIAS,null),new OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA1,PSource.PSpecified.DEFAULT));
            plain=cipher.doFinal(encrypted);
            KeyVault.save(c,new String(plain,java.nio.charset.StandardCharsets.UTF_8));
            s.deleteEntry(ALIAS);
        } finally { Arrays.fill(encrypted,(byte)0); if(plain!=null)Arrays.fill(plain,(byte)0); }
    }
}
