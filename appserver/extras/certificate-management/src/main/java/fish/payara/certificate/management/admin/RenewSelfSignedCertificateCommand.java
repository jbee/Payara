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
package fish.payara.certificate.management.admin;

import com.sun.enterprise.admin.cli.CLICommand;
import com.sun.enterprise.admin.servermgmt.KeystoreManager;
import com.sun.enterprise.admin.servermgmt.RepositoryException;
import fish.payara.certificate.management.CertificateManagementKeytoolCommands;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.api.admin.CommandException;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

/**
 *
 * @author jonathan coustick
 */
@Service(name = "renew-self-signed-certificate")
@PerLookup
public class RenewSelfSignedCertificateCommand extends AbstractCertManagementCommand {

    private static final Logger LOGGER = Logger.getLogger(CLICommand.class.getPackage().getName());

    private KeyStore store;

    @Override
    protected int executeCommand() throws CommandException {
        parseKeyAndTrustStores();

        try {
            store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(new FileInputStream(keystore), keystorePassword);
        } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            return ERROR;
        }

        List<X509Certificate> certificates = findSelfSignedCerts();
        for (X509Certificate cert : certificates) {
            try {
                String alias = store.getCertificateAlias(cert);
                LOGGER.log(Level.INFO, "Renewing certifacte with alias {0}", alias);
                exportPrivateKey(alias);
                LOGGER.log(Level.INFO, "Generating CSR...");
                generateCsr(alias);
                LOGGER.log(Level.INFO, "Signing CSR with private key...");
                signRequest(alias);
                LOGGER.log(Level.INFO, "Writing keystore back to file...");
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                LOGGER.log(Level.INFO, "Got cert factory...");
                File certFile = new File(getInstallRootPath() + File.separator + "tls" + File.separator + "result.crt");
                if (!certFile.exists() && !certFile.canRead()) {
                    LOGGER.log(Level.INFO, "Cannot read file {0}", certFile.getCanonicalPath());
                }
                FileInputStream inputStream = new FileInputStream(certFile);
                LOGGER.log(Level.INFO, "Generating certificate...");
                Certificate newCert = factory.generateCertificate(inputStream);
                LOGGER.log(Level.INFO, newCert.toString());
                store.setCertificateEntry(alias, newCert);
                LOGGER.log(Level.INFO, "File write...");
                FileOutputStream writeOut = new FileOutputStream(keystore);
                store.store(writeOut, keystorePassword);
                LOGGER.log(Level.INFO, "All done");
            } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException | CommandException ex) {
                LOGGER.log(Level.SEVERE, "Error renewing certificate", ex);
                return ERROR;
            } finally {
                //Clear up after ourselves
                File privateKeyFile = new File(keystore.getParent() + File.separator + "keystore.p12");
                if (privateKeyFile.exists()) {
                    privateKeyFile.delete();
                }
                File keyFile = new File("key.pem");
                if (keyFile.exists()) {
                    keyFile.delete();
                }
                
                File certFolder = new File(getInstallRootPath() + File.separator + "tls");
                if (certFolder.exists()) {
                    certFolder.delete();
                }
            }
        }

        return SUCCESS;
    }

    private List<X509Certificate> findSelfSignedCerts() {
        ArrayList<X509Certificate> selfSignedCerts = new ArrayList<>();
        try {
            Enumeration<String> aliases = store.aliases();
            while (aliases.hasMoreElements()) {
                X509Certificate cert = (X509Certificate) store.getCertificate(aliases.nextElement());
                if (cert.getIssuerX500Principal().equals(cert.getSubjectX500Principal())) {
                    selfSignedCerts.add(cert);
                }

            }
        } catch (KeyStoreException ex) {
            Logger.getLogger(RenewSelfSignedCertificateCommand.class.getName()).log(Level.SEVERE, null, ex);
        }
        return selfSignedCerts;
    }

    private void exportPrivateKey(String alias) throws CommandException {
        String[] privCommand = CertificateManagementKeytoolCommands.extractPrivateKeyFromKeystore(keystore, keystorePassword, alias);
        LOGGER.log(Level.SEVERE, Arrays.toString(privCommand));
        KeystoreManager.KeytoolExecutor keytoolExecutor = new KeystoreManager.KeytoolExecutor(
                CertificateManagementKeytoolCommands.extractPrivateKeyFromKeystore(keystore, keystorePassword, alias), 60);
        try {
            LOGGER.log(Level.SEVERE, "about to extract private key");
            keytoolExecutor.execute("unableExtractPrivateKey", keystore);
            LOGGER.log(Level.SEVERE, "extracted private key");
        } catch (RepositoryException re) {
            logger.severe(re.getCause().getMessage()
                    .replace("keytool error: java.lang.Exception: ", "")
                    .replace("keytool error: java.io.IOException: ", ""));
            throw new CommandException(re);
        }

        ProcessBuilder builder = new ProcessBuilder();
        builder = builder.command("openssl", "pkcs12", "-in", keystore.getParent() + File.separator + "keystore.p12", "-nodes", "-nocerts", "-out", "key.pem");
        try {
            LOGGER.log(Level.SEVERE, Arrays.toString(builder.command().toArray()));
            Process result = builder.start();
            result.waitFor(1, TimeUnit.MINUTES);
            if (result.exitValue() != 0) {
                throw new CommandException("Unable to extract certicate");
            }

        } catch (IOException | InterruptedException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            throw new CommandException(ex);
        }
    }

    private void addToKeystore(String dname, String alias) throws CommandException {
        // Run keytool command to generate self-signed cert
        KeystoreManager.KeytoolExecutor keytoolExecutor = new KeystoreManager.KeytoolExecutor(
                CertificateManagementKeytoolCommands.constructGenerateCertKeytoolNewStoreCommand(keystorePassword, alias, dname), 60);

        try {
            keytoolExecutor.execute("certNotCreated", keystore);
        } catch (RepositoryException re) {
            logger.severe(re.getCause().getMessage()
                    .replace("keytool error: java.lang.Exception: ", "")
                    .replace("keytool error: java.io.IOException: ", ""));
            throw new CommandException(re);
        }
    }

    private void generateCsr(String alias) throws CommandException {
        // Get CSR install dir and ensure it actually exists
        File csrLocation = new File(getInstallRootPath() + File.separator + "tls");
        if (!csrLocation.exists()) {
            csrLocation.mkdir();
        }

        // Run keytool command to generate self-signed cert
        KeystoreManager.KeytoolExecutor keytoolExecutor = new KeystoreManager.KeytoolExecutor(
                CertificateManagementKeytoolCommands.constructGenerateCertRequestKeytoolCommand(
                        keystore, keystorePassword,
                        new File(csrLocation.getAbsolutePath() + File.separator + alias + ".csr"),
                        alias),
                60);

        try {
            keytoolExecutor.execute("csrNotCreated", keystore);
        } catch (RepositoryException re) {
            logger.severe(re.getCause().getMessage()
                    .replace("keytool error: java.lang.Exception: ", "")
                    .replace("keytool error: java.io.IOException: ", ""));
            throw new CommandException(re);
        }
    }

    private void signRequest(String alias) throws CommandException {
        File csrLocation = new File(getInstallRootPath() + File.separator + "tls" + File.separator + alias + ".csr");
        
        ProcessBuilder builder = new ProcessBuilder();
        builder = builder.command("openssl", "x509", "-req", "-days", "365", "-in", csrLocation.getAbsolutePath(),
                "-signkey", "key.pem", "-sha256", "-out", csrLocation.getParent() + File.separator + "result.crt");
        try {
            Process result = builder.start();
            result.waitFor(1, TimeUnit.MINUTES);
            if (result.exitValue() != 0) {
                throw new CommandException("Unable to extract certicate");
            }

        } catch (IOException | InterruptedException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            throw new CommandException(ex);
        }

    }
    
}
