import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/**
 * Usage: KEYSTORE_PASS=... Sign <keystore.p12> <alias> <in.apk> <out.apk> <expected cert SHA-256 hex>
 * Signs with APK Signature Scheme v2, then verifies the result and refuses to finish if the
 * signing certificate is not the expected release certificate (protects against releasing with a wrong key).
 */
public class Sign {
    static String sha256(byte[] b) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte x : MessageDigest.getInstance("SHA-256").digest(b)) sb.append(String.format("%02X", x));
        return sb.toString();
    }

    public static void main(String[] a) throws Exception {
        // the password comes from the environment, not the command line (not visible in process lists)
        char[] pw = System.getenv("KEYSTORE_PASS").toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(a[0])) { ks.load(in, pw); }
        PrivateKey key = (PrivateKey) ks.getKey(a[1], pw);
        X509Certificate cert = (X509Certificate) ks.getCertificate(a[1]);
        String expected = a[4].replace(":", "").toUpperCase();
        if (!sha256(cert.getEncoded()).equals(expected))
            throw new IllegalStateException("Keystore certificate does not match the release certificate – refusing to sign");
        ApkSigner.SignerConfig sc = new ApkSigner.SignerConfig.Builder("CERT", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(sc))
                .setInputApk(new File(a[2]))
                .setOutputApk(new File(a[3]))
                .setMinSdkVersion(24)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .build()
                .sign();
        ApkVerifier.Result r = new ApkVerifier.Builder(new File(a[3])).build().verify();
        if (!r.isVerified() || r.getSignerCertificates().size() != 1
                || !sha256(r.getSignerCertificates().get(0).getEncoded()).equals(expected))
            throw new IllegalStateException("Signed APK failed verification: " + r.getErrors());
        System.out.println("signed and verified " + a[3] + " (cert SHA-256 " + expected + ")");
    }
}
