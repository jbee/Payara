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

import fish.payara.testing.core.server.ServerAdapter;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Some code on top of the GenericContainer to get the correct Adapter (responsible for defining the correct Docker image)
 * and having some general useful methods.
 *
 * @param <T>
 */
public abstract class AbstractContainer<T extends GenericContainer<T>> extends GenericContainer<T> {

    public AbstractContainer(final Future<String> image) {
        super(image);
    }

    public String getContainerIP() {
        return DockerUtils.getDockerContainerIP(getDockerClient(), getContainerId());
    }

    public abstract String getApplicationPort();

    public abstract int getMappedApplicationPort();

    public abstract String getWebConsolePort();

    /**
     * Value returned by `InetAddress.getLocalHost().getHostName()` within the container.
     * @return Hostname of the container.
     */
    public String getHostName() {
        return getContainerId().substring(0,12);
    }

    public byte[] getContainerFileContent(String path) {


        return copyFileFromContainer(path, inputStream -> {
            ByteArrayOutputStream content = new ByteArrayOutputStream();

            IOUtils.copy(inputStream, content);
            return content.toByteArray();

        });
    }

    /**
     * Returns reference to the project build artifact (.war or .ear) to include in the Docker Image.
     * @param testApplicationRequired is application expected? When no build artifact found but application expected an exception is thrown.
     * @param logger Logger
     * @return File Reference to found artifact or null when no artifact and no application expected.
     */
    protected static File findAppFile(boolean testApplicationRequired, Logger logger) {
        // Find a .war file in the target/ directories
        Set<File> matches = new HashSet<>(findAppFiles("target"));
        if (matches.size() == 0) {
            if (!testApplicationRequired) {
                return null;
            }
            throw new IllegalStateException("No .war or .ear files found in target / output folders.");
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Found multiple application files in target output folders: " + matches +
                    " Expecting exactly 1 application file to be found.");
        }
        File appFile = matches.iterator().next();
        logger.info("Found application file at: " + appFile.getAbsolutePath());
        return appFile;
    }

    /**
     * Configure the timeout check using the readiness path.
     * @param readinessUrl The relative URL for the check
     * @param timeoutSeconds The timeout
     */
    protected void withReadinessPath(String readinessUrl, int timeoutSeconds) {
        Objects.requireNonNull(readinessUrl);
        readinessUrl = buildPath(readinessUrl);
        waitingFor(Wait.forHttp(readinessUrl)
                .withStartupTimeout(Duration.ofSeconds(timeoutSeconds)));
    }

    /**
     * Configure container properties like exposed ports and logging.
     * @param adapter Info about ports
     * @param verboseLogging Activate verbose logging?
     * @param logger The Logger for verbose logging.
     */
    protected void containerConfiguration(ServerAdapter adapter, boolean verboseLogging, Logger logger) {
        // FIXME Review, after we are using restClient to access endpoints in some cases.
        addExposedPorts(adapter.getDefaultHttpPort());
        if (verboseLogging) {
            withLogConsumer(new Slf4jLogConsumer(logger));
        }
    }

    /**
     * Find all .war and .ear files in a directory and subdirectories.
     * @param path The top level directory to start the search
     * @return The set of files found matching the file type.
     */
    private static Set<File> findAppFiles(String path) {
        File dir = new File(path);
        if (dir.exists() && dir.isDirectory()) {
            try {
                return Files.walk(dir.toPath())
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().toLowerCase().endsWith(".war"))  // FIXME Support for ear !!
                        .map(Path::toFile)
                        .collect(Collectors.toSet());
            } catch (IOException ignore) {
            }
        }
        return Collections.emptySet();
    }

    private static String buildPath(String firstPart, String... moreParts) {
        StringBuilder result = new StringBuilder(firstPart.startsWith("/") ? firstPart : '/' + firstPart);
        if (moreParts != null && moreParts.length > 0) {
            for (String part : moreParts) {
                if (result.toString().endsWith("/") && part.startsWith("/")) {
                    result.append(part.substring(1));
                } else if (result.toString().endsWith("/") || part.startsWith("/")) {
                    result.append(part);
                } else {
                    result.append("/").append(part);
                }
            }
        }
        return result.toString();
    }
}
