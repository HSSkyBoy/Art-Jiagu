package com.ark.jiagu;

import android.content.Context;
import android.util.Base64;

import com.mcal.apksigner.CertConverter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

public class JksSha256Util {

    public static String getArkJksSha256(Context context) throws Exception {
        return getJksSha256FromAssets(
                context,
                "Ark.jks",
                "123456",
                "123456",
                "123456"
        );
    }

    public static String getJksSha256FromAssets(
            Context context,
            String assetsName,
            String storePassword,
            String alias,
            String keyPassword
    ) throws Exception {

        File tempDir = new File(context.getCacheDir(), "jks_sha256");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        File jksFile = new File(tempDir, assetsName);
        File pk8File = new File(tempDir, "temp_key.pk8");
        File certFile = new File(tempDir, "temp_cert.x509.pem");

        copyAssetsToFile(context, assetsName, jksFile);

        CertConverter.convert(
                jksFile,
                storePassword,
                alias,
                keyPassword,
                pk8File,
                certFile
        );

        Certificate certificate = readX509Certificate(certFile);
        byte[] certBytes = certificate.getEncoded();

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] sha256 = digest.digest(certBytes);

        return bytesToHex(sha256);
    }

    private static void copyAssetsToFile(Context context, String assetsName, File outFile) throws Exception {
        try (
                InputStream inputStream = context.getAssets().open(assetsName);
                FileOutputStream outputStream = new FileOutputStream(outFile)
        ) {
            byte[] buffer = new byte[8192];
            int len;

            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }

            outputStream.flush();
        }
    }

    private static Certificate readX509Certificate(File certFile) throws Exception {
        try (InputStream inputStream = new java.io.FileInputStream(certFile)) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return factory.generateCertificate(inputStream);
        } catch (Exception e) {
            String pem = readText(certFile);

            pem = pem.replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replace("\r", "")
                    .replace("\n", "")
                    .trim();

            byte[] derBytes = Base64.decode(pem, Base64.DEFAULT);

            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return factory.generateCertificate(new ByteArrayInputStream(derBytes));
        }
    }

    private static String readText(File file) throws Exception {
        try (InputStream inputStream = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int len = inputStream.read(buffer);
            return new String(buffer, 0, len);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();

        for (byte b : bytes) {
            builder.append(String.format("%02X", b));
        }

        return builder.toString();
    }

    public static String getJksSha256FromFile(
            File jksFile,
            String storePassword,
            String alias,
            String keyPassword,
            File cacheDir
    ) throws Exception {

        File tempDir = new File(cacheDir, "jks_sha256_custom");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        File pk8File = new File(tempDir, "custom_key.pk8");
        File certFile = new File(tempDir, "custom_cert.x509.pem");

        CertConverter.convert(
                jksFile,
                storePassword,
                alias,
                keyPassword,
                pk8File,
                certFile
        );

        Certificate certificate = readX509Certificate(certFile);
        byte[] certBytes = certificate.getEncoded();

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] sha256 = digest.digest(certBytes);

        return bytesToHex(sha256);
    }
}