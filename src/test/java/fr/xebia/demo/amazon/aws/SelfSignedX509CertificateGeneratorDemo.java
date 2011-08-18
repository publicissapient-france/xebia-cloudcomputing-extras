/*
 * Copyright 2008-2010 Xebia and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.xebia.demo.amazon.aws;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.x509.X509V1CertificateGenerator;

import com.google.common.base.Strings;

/**
 * Demo of a generation of a X509 Self Signed Certificate using <a
 * href="http://www.bouncycastle.org/">Bouncy Castle</a> library.
 * 
 * @author <a href="mailto:cyrille@cyrilleleclerc.com">Cyrille Le Clerc</a>
 */
public class SelfSignedX509CertificateGeneratorDemo {

    static {
        // adds the Bouncy castle provider to java security
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * <p>
     * Generate a self signed X509 certificate .
     * </p>
     * <p>
     * TODO : do the same with
     * {@link org.bouncycastle.cert.X509v1CertificateBuilder} instead of the
     * deprecated {@link org.bouncycastle.x509.X509V1CertificateGenerator}.
     * </p>
     */
    @SuppressWarnings("deprecation")
    static void generateSelfSignedX509Certificate() throws NoSuchAlgorithmException, NoSuchProviderException, CertificateEncodingException,
            SignatureException, InvalidKeyException, IOException {

        // yesterday
        Date validityBeginDate = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
        // in 2 years
        Date validityEndDate = new Date(System.currentTimeMillis() + 2 * 365 * 24 * 60 * 60 * 1000);

        // GENERATE THE PUBLIC/PRIVATE RSA KEY PAIR
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
        keyPairGenerator.initialize(1024, new SecureRandom());

        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // GENERATE THE X509 CERTIFICATE
        X509V1CertificateGenerator certGen = new X509V1CertificateGenerator();
        X500Principal dnName = new X500Principal("CN=John Doe");

        certGen.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
        certGen.setSubjectDN(dnName);
        certGen.setIssuerDN(dnName); // use the same
        certGen.setNotBefore(validityBeginDate);
        certGen.setNotAfter(validityEndDate);
        certGen.setPublicKey(keyPair.getPublic());
        certGen.setSignatureAlgorithm("SHA256WithRSAEncryption");

        X509Certificate cert = certGen.generate(keyPair.getPrivate(), "BC");

        // DUMP CERTIFICATE AND KEY PAIR
        System.out.println(Strings.repeat("=", 80));
        System.out.println("CERTIFICATE TO_STRING");
        System.out.println(Strings.repeat("=", 80));
        System.out.println();
        System.out.println(cert);
        System.out.println();

        System.out.println(Strings.repeat("=", 80));
        System.out.println("CERTIFICATE PEM (to store in a cert-johndoe.pem file)");
        System.out.println(Strings.repeat("=", 80));
        System.out.println();
        PEMWriter pemWriter = new PEMWriter(new PrintWriter(System.out));
        pemWriter.writeObject(cert);
        pemWriter.flush();
        System.out.println();

        System.out.println(Strings.repeat("=", 80));
        System.out.println("PRIVATE KEY PEM (to store in a priv-johndoe.pem file)");
        System.out.println(Strings.repeat("=", 80));
        System.out.println();
        pemWriter.writeObject(keyPair.getPrivate());
        pemWriter.flush();
        System.out.println();
    }

    public static void main(String[] args) {
        try {
            generateSelfSignedX509Certificate();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
