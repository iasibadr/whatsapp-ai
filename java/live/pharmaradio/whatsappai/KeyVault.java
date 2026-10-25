package live.pharmaradio.whatsappai;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class KeyVault {
    private static final String ALIAS = "whatsapp-ai-openai-key";

    private static SecretKey encryptionKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }

    static synchronized void save(Context context, String value) throws Exception {
        String key = value.trim();
        if (!key.startsWith("sk-") || key.length() < 20 || key.length() > 512 || key.matches(".*\\s.*")) {
            throw new IllegalArgumentException("Format de clé invalide");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey());
        byte[] encrypted = cipher.doFinal(key.getBytes(StandardCharsets.UTF_8));
        if (!context.getSharedPreferences("openai-vault", Context.MODE_PRIVATE).edit()
                .putString("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).commit()) {
            throw new IllegalStateException("Enregistrement de la clé impossible");
        }
        OpenAiCheck.invalidate();
    }

    static synchronized boolean configured(Context context) {
        return context.getSharedPreferences("openai-vault", Context.MODE_PRIVATE).contains("ciphertext");
    }

    static synchronized String load(Context context) throws Exception {
        android.content.SharedPreferences prefs = context.getSharedPreferences("openai-vault", Context.MODE_PRIVATE);
        if (!prefs.contains("ciphertext")) throw new IllegalStateException("Configurez votre clé API OpenAI");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(prefs.getString("ciphertext", ""), Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }

    static synchronized void clear(Context context) throws Exception {
        if (!context.getSharedPreferences("openai-vault", Context.MODE_PRIVATE).edit().clear().commit()) {
            throw new IllegalStateException("Effacement impossible");
        }
        OpenAiCheck.invalidate();
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS);
    }
}
