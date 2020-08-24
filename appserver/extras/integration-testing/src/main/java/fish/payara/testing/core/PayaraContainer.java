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
package fish.payara.testing.core;

import fish.payara.testing.core.config.Config;
import fish.payara.testing.core.server.ServerAdapter;
import fish.payara.testing.core.server.ServerAdapterMetaData;
import fish.payara.testing.core.util.DockerImageProcessor;
import org.apache.commons.compress.utils.IOUtils;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.shaded.org.apache.commons.lang.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PayaraContainer extends AbstractContainer<PayaraContainer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayaraContainer.class);

    private ServerAdapter adapter;
    private ServerAdapterMetaData adapterMetaData;
    private boolean databaseRequired;
    private boolean verboseLogging;

    public PayaraContainer(ServerAdapterMetaData adapterMetaData, boolean verboseLogging, boolean startWithDebug, boolean databaseRequired) {
        super(DockerImageProcessor.getImage(adapterMetaData, findAppFile(adapterMetaData.isTestApplication(), LOGGER), "payaracontainer"));
        this.adapterMetaData = adapterMetaData;
        this.databaseRequired = databaseRequired;
        this.verboseLogging = verboseLogging;
        setNetwork(Network.SHARED);
        if (startWithDebug) {
            addEnv("PAYARA_ARGS", "--debug");
        }
    }

    @Override
    protected void configure() {
        super.configure();

        adapter = new ServerAdapter();
        containerConfiguration(adapter, verboseLogging, LOGGER);
        if (databaseRequired) {
            withLogEntry(Config.getAppStartTimeout());
        } else {
            if (adapterMetaData.isTestApplication()) {
                withReadinessPath("/health", Config.getAppStartTimeout());
            } else {
                withReadinessPath("/",  Config.getAppStartTimeout());  // TODO This fails with Payara Micro
            }
        }
    }

    public void withLogEntry(int timeoutSeconds) {
        LogMessageWaitStrategy strategy = new LogMessageWaitStrategy();
        strategy.withRegEx(".*Boot Command deploy returned with result SUCCESS : PlainTextActionReporterSUCCESSDescription: deploy AdminCommandApplication deployed with name test.*")
                .withStartupTimeout(Duration.ofSeconds(timeoutSeconds));
        waitingFor(strategy);
    }

    @Override
    public String getApplicationPort() {
        return "8080";
    }

    @Override
    public int getMappedApplicationPort() {
        return getMappedPort(8080);
    }

    @Override
    public String getWebConsolePort() {
        return "4848";
    }

    /**
     * Execute an asadmin command and we are not interested in the result (=output) of the command.
     * So this method always returns null.
     *
     * @param command
     * @param arguments
     * @param <T>
     * @return
     */
    public void executeASAdminCommand(String command, String... arguments) {

         executeASAdminCommand(command, null, arguments);
    }

    /**
     * Execute an asadmin command and when outputParser is not null, parses the output of the command and return it.
     *
     * @param command
     * @param outputParser
     * @param arguments
     * @param <T>
     * @return
     */
    public <T> List<T> executeASAdminCommand(String command, Function<String, List<T>> outputParser, String... arguments) {

        List<T> result = null;

        String[] execArgs = assembleCommand(command, arguments);

        try {
            ExecResult commandResult = execInContainer(execArgs);
            if (commandResult.getExitCode() != 0) {
                Assertions.fail(commandResult.getStdout() + "\n" + commandResult.getStderr());
            } else {
                if (outputParser != null) {
                    result = outputParser.apply(commandResult.getStdout());
                }
            }
        } catch (IOException | InterruptedException e) {
            Assertions.fail(e.getMessage());
        }

        return result;
    }

    private String[] assembleCommand(String command, String[] arguments) {
        String[] execArgs = new String[5 + arguments.length];

        String payaraDomainDirectory = adapter.getPayaraDomainDirectory();
        execArgs[0] = payaraDomainDirectory + "/bin/asadmin";
        execArgs[1] = "--terse";
        execArgs[2] = "--user=admin";
        execArgs[3] = "--passwordfile=" + adapter.getPasswordFile();
        execArgs[4] = command;

        System.arraycopy(arguments, 0, execArgs, 5, arguments.length);
        return execArgs;
    }

    /**
     * Adds the AS_ADMIN_SSHPASSWORD to the password file with the SSH password configured in the Plain Java Containers.
     *
     * @param password
     */
    public void addASAdminSSHPassword(String password) {


        String fileContent = copyFileFromContainer(adapter.getPasswordFile(), inputStream -> {
            ByteArrayOutputStream content = new ByteArrayOutputStream();

            IOUtils.copy(inputStream, content);
            return content.toString();

        });

        List<String> fileLines = Pattern.compile("\n").splitAsStream(fileContent).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        fileLines.add("AS_ADMIN_SSHPASSWORD=" + password);

        String newFileContent = String.join("\n", fileLines);

        copyFileToContainer(new StringTransferable(newFileContent), adapter.getPasswordFile());

    }

    /**
     * A Transferable which keeps data in memory. Use it only for small transfers to the container.
     */
    private static class StringTransferable implements Transferable {
        private String content;
        private byte[] bytes;

        StringTransferable(String content) {
            this.content = content;
            bytes = content.getBytes();
        }


        public long getSize() {
            return this.bytes.length;
        }

        public String getDescription() {
            return "String: " + StringUtils.abbreviate(content, 100);
        }

        public byte[] getBytes() {
            return this.bytes;
        }
    }
}
