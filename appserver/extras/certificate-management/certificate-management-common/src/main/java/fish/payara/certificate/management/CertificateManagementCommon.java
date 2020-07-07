/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2020 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.certificate.management;

import com.sun.enterprise.security.auth.realm.certificate.OID;
import com.sun.enterprise.util.StringUtils;
import org.glassfish.api.admin.CommandException;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAKey;
import java.security.interfaces.RSAKey;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class CertificateManagementCommon {

    public static Map<String, String> getEntries(File keyOrTrustStore, char[] password, String alias, boolean verbose)
            throws CommandException {
        Map<String, String> entries = new HashMap<>();

        try {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(new FileInputStream(keyOrTrustStore), password);

            if (StringUtils.ok(alias)) {
                entries.put(alias, formatEntry(getCertificateFromStore(keyOrTrustStore, password, alias), verbose));
            } else {
                Enumeration<String> aliases = store.aliases();
                while (aliases.hasMoreElements()) {
                    String entryAlias = aliases.nextElement();
                    entries.put(entryAlias, formatEntry(
                            getCertificateFromStore(keyOrTrustStore, password, entryAlias), verbose));
                }
            }
        } catch (IOException | NoSuchAlgorithmException | CertificateException | KeyStoreException e) {
            throw new CommandException(e);
        }

        return entries;
    }

    private static String formatEntry(final X509Certificate certificate, boolean verbose) {
        if (verbose) {
            StringBuilder output = new StringBuilder(1024);
            output.append("Subject:    ").append(formatPrincipal(certificate.getSubjectX500Principal()));
            output.append("\nValidity:   ").append(certificate.getNotBefore()).append(" - ")
                    .append(certificate.getNotAfter());
            output.append("\nS/N:        ").append(certificate.getSerialNumber());
            output.append("\nVersion:    ").append(certificate.getVersion());
            output.append("\nIssuer:     ").append(formatPrincipal(certificate.getIssuerX500Principal()));
            output.append("\nPublic Key: ").append(formatKey(certificate.getPublicKey()));
            output.append("\nSign. Alg.: ").append(certificate.getSigAlgName()).append(" (OID: ")
                    .append(certificate.getSigAlgOID()).append(')');
            return output.toString();
        } else {
            return "Subject:    " + formatPrincipal(certificate.getSubjectX500Principal());
        }
    }

    private static String formatPrincipal(final X500Principal principal) {
        return principal.getName(X500Principal.RFC2253, OID.getOIDMap());
    }

    private static String formatKey(final PublicKey key) {
        if (key instanceof RSAKey) {
            final RSAKey rsaKey = (RSAKey) key;
            return key.getAlgorithm() + ", " + rsaKey.getModulus().bitLength() + " bits";
        }
        if (key instanceof DSAKey) {
            final DSAKey dsaKey = (DSAKey) key;
            if (dsaKey.getParams() != null) {
                return key.getAlgorithm() + ", " + dsaKey.getParams().getP().bitLength() + " bits";
            }
        }
        return key.getAlgorithm() + ", unresolved bit length.";
    }

    private static X509Certificate getCertificateFromStore(File keyOrTrustStore, char[] password, String entryAlias)
            throws CommandException {
        try {

            Callable<Certificate> supplier = () -> {
                KeyStore keyStore = KeyStore.getInstance(getKeystoreType(keyOrTrustStore));
                try (InputStream keyStoreStream = new FileInputStream(keyOrTrustStore)) {
                    keyStore.load(keyStoreStream, password);
                } catch (Exception ex) {
                    throw new KeyStoreException(ex);
                }
                return keyStore.getCertificate(entryAlias);
            };
            return getX509Certificate(supplier);
        } catch (final Exception e) {
            throw new CommandException(e);
        }
    }


    private static X509Certificate getX509Certificate(final Callable<Certificate> supplier) throws Exception {
        Certificate certificate  = supplier.call();
        if (certificate instanceof X509Certificate) {
            return (X509Certificate) certificate;
        }
        throw new IllegalStateException("The certificate was found but it is not supported X509 certificate.");
    }

    public static String getKeystoreType(File keyOrTrustStore) {
        String ksFilename = keyOrTrustStore.getName().toLowerCase();
        if (ksFilename.endsWith("jks")) {
            return "JKS";
        }
        if (ksFilename.endsWith("jceks")) {
            return "JCEKS";
        }
        if (ksFilename.endsWith("p12") || ksFilename.endsWith("pfx") || ksFilename.endsWith("pkcs12")) {
            return "PKCS12";
        }
        return KeyStore.getDefaultType();
    }
}