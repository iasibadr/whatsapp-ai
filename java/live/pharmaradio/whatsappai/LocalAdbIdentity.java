package live.pharmaradio.whatsappai;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.json.JSONObject;

final class LocalAdbIdentity {
    private static final String ALIAS = "whatsapp-ai-local-identity";
    final PrivateKey privateKey;
    final Certificate certificate;

    private LocalAdbIdentity(PrivateKey key, Certificate certificate) { this.privateKey = key; this.certificate = certificate; }

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

    static synchronized LocalAdbIdentity load(Context context) throws Exception {
        android.content.SharedPreferences prefs = context.getSharedPreferences("local-activation-identity", Context.MODE_PRIVATE);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        if (prefs.contains("ciphertext")) {
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)));
            byte[] decoded = cipher.doFinal(Base64.decode(prefs.getString("ciphertext", ""), Base64.NO_WRAP));
            try {
                JSONObject identity = new JSONObject(new String(decoded, java.nio.charset.StandardCharsets.UTF_8));
                byte[] key = Base64.decode(identity.getString("key"), Base64.NO_WRAP);
                try {
                    return new LocalAdbIdentity(KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(key)),
                            CertificateFactory.getInstance("X.509").generateCertificate(new java.io.ByteArrayInputStream(Base64.decode(identity.getString("certificate"), Base64.NO_WRAP))));
                } finally { java.util.Arrays.fill(key, (byte) 0); }
            } finally { java.util.Arrays.fill(decoded, (byte) 0); }
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        X500Name subject = new X500Name("CN=WhatsApp Relay Lab");
        BouncyCastleProvider provider = new BouncyCastleProvider();
        Certificate certificate = new JcaX509CertificateConverter().setProvider(provider).getCertificate(
                new JcaX509v3CertificateBuilder(subject, new BigInteger(128, new SecureRandom()),
                        new Date(System.currentTimeMillis() - 60000), new Date(System.currentTimeMillis() + 157680000000L), subject, pair.getPublic())
                        .build(new JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(pair.getPrivate())));
        byte[] plain = new JSONObject().put("key", Base64.encodeToString(pair.getPrivate().getEncoded(), Base64.NO_WRAP))
                .put("certificate", Base64.encodeToString(certificate.getEncoded(), Base64.NO_WRAP)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey());
            byte[] encrypted = cipher.doFinal(plain);
            if (!prefs.edit().putString("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).commit()) throw new IllegalStateException("Identité non enregistrée");
        } finally { java.util.Arrays.fill(plain, (byte) 0); }
        return new LocalAdbIdentity(pair.getPrivate(), certificate);
    }
}
