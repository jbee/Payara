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
package fish.payara.testing.core.util;

import fish.payara.testing.core.config.Config;
import fish.payara.testing.core.exception.DockerFileNotFound;
import fish.payara.testing.core.exception.UnexpectedException;
import fish.payara.testing.core.images.LoggableImageFromDockerFile;
import fish.payara.testing.core.server.JDKRuntime;
import fish.payara.testing.core.server.PlainJavaAdapter;
import fish.payara.testing.core.server.RuntimeType;
import fish.payara.testing.core.server.ServerAdapterMetaData;
import org.assertj.core.api.Assertions;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class DockerImageProcessor {

    private static final Pattern IMAGE_PATTERN = Pattern.compile("payara/(.*):(.*)");

    private DockerImageProcessor() {
    }

    /**
     * Returns the Docker image which will used in the test running Payara Server or Payara Micro.
     *
     * @param appFile The application file to include in the resulting Docker image
     * @return The docker image including the supplied appFile
     */
    public static ImageFromDockerfile getImage(ServerAdapterMetaData metaData, File appFile, String containerName) {

        String dockerFileContext = defineContent(metaData, containerName);


        try {
            Path tempDirWithPrefix = Files.createTempDirectory("payara.test.");

            Path dockerPath = tempDirWithPrefix.resolve("Dockerfile");
            try (BufferedWriter writer = Files.newBufferedWriter(dockerPath)) {
                writer.write(dockerFileContext);
            }

            Path dockerCopy = Files.copy(dockerPath, tempDirWithPrefix.resolve("Dockerfile"));

            if (metaData.getRuntimeType() == RuntimeType.CUSTOM) {
                URI path = new File("./src/docker/custom").toURI();
                new FileCopyHelper(path.toURL(), tempDirWithPrefix).copyDependentFiles();;
            }
            String name = "das";
            if (metaData.isTestApplication()) {
                Path source = appFile.toPath();
                Files.copy(source, tempDirWithPrefix.resolve("test.war"));
                name = appFile.getName();
            }


            ImageFromDockerfile image = new LoggableImageFromDockerFile(containerName + "/" + name, metaData)
                    .withDockerfile(dockerCopy);

            return image;
        } catch (IOException e) {
            Assertions.fail(e.getMessage());
        }
        return null;  // Assertions.fail will throw an exception but Compiler needs a return value.

    }

    /*
     * Returns the Docker image which will used in the test for thr Plain Java Image used for Payara server instances.
     *
     * @param appFile The application file to include in the resulting Docker image
     * @return The docker image including the supplied appFile
     */
    public static ImageFromDockerfile getImage(ServerAdapterMetaData metaData, String name) {

        String dockerFile = "plainJava8.docker";
        if (metaData.getJdkRuntime() == JDKRuntime.JDK11) {
            dockerFile = "plainJava11.docker";

        }
        URL resource = PlainJavaAdapter.class.getClassLoader().getResource(dockerFile);

        try {
            if (resource == null) {
                throw new UnexpectedException("Unable to find the plainJava8.docker or the plainJava11.docker files ion the class-path");
            }
            Path tempDirWithPrefix = Files.createTempDirectory("payara.test.");
            FileCopyHelper fileCopyHelper = new FileCopyHelper(resource, tempDirWithPrefix);
            Path dockerCopy = fileCopyHelper.copyToTemp();

            return new LoggableImageFromDockerFile("plaincontainer/" + name.toLowerCase(), true)
                    .withDockerfile(dockerCopy);
        } catch (IOException e) {
            Assertions.fail(e.getMessage());
        }
        return null;  // Assertions.fail will throw an exception but method needs a return value.

    }

    private static String defineContent(ServerAdapterMetaData metaData, String name) {
        StringBuilder result = new StringBuilder();
        String version = "";
        if (metaData.getJdkRuntime() == JDKRuntime.JDK11) {
            version = "-jdk11";
        }
        switch (metaData.getRuntimeType()) {

            case SERVER:
                String dockerServerVariant = getDockerServerVariantName();
                result.append("FROM payara/").append(dockerServerVariant).append(":").append(metaData.getPayaraVersion()).append(version).append("\n");
                break;
            case MICRO:
                result.append("FROM payara/micro:").append(metaData.getPayaraVersion()).append(version).append("\n");

                String noCluster = "";
                if (!metaData.isMicroCluster()) {
                    noCluster = ", \"--noCluster\"";
                }
                result.append("CMD [\"--deploymentDir\", \"/opt/payara/deployments\", \"--name\", \"").append(name)
                        .append("\"").append(noCluster).append("]\n");
                break;
            case CUSTOM:
                try {
                    File dockerFile = new File("./src/docker/custom/payara.docker");
                    if (!dockerFile.exists() || !dockerFile.canRead()) {
                        throw new DockerFileNotFound("./src/docker/custom/payara.docker");
                    }
                    String content = new String(Files.readAllBytes(Paths.get("./src/docker/custom/payara.docker")));
                    result.append(updateTag(content, metaData));
                    result.append("\n");  // Make sure the ADD test.war ... is placed on a new line

                } catch (IOException e) {
                    throw new UnexpectedException("IOException during file read of 'src/docker/custom/payara.docker' ", e);
                }

                break;

            default:
                throw new IllegalStateException("Unexpected value: " + metaData.getRuntimeType());
        }

        if (metaData.isTestApplication()) {
            result.append("ADD test.war /opt/payara/deployments\n");
        }
        return result.toString();
    }

    public static String getDockerServerVariantName() {
        String result;
        switch (Config.getPayaraServerVariant()) {
            case FULL:
                result = "server-full";
                break;
            case WEB:
                result = "server-web";
                break;
            case ML:
                result = "server-ml";  // TODO We don't have this yet. Future extension
                break;
            default:
                throw new IllegalArgumentException("Value " + Config.getPayaraServerVariant() + " not supported");
        }
        return result;
    }

    // TODO Move this logic out of this class so that it can be tested.
    private static String updateTag(String content, ServerAdapterMetaData metaData) {
        return Arrays.stream(content.split("\n"))
                .map(l -> processLine(l, metaData))
                .collect(Collectors.joining("\n"));
    }

    private static String processLine(String line, ServerAdapterMetaData metaData) {
        String result = line.trim();
        if (result.toUpperCase(Locale.ENGLISH).startsWith("FROM")) {
            result = updateVersion(result, metaData);
        }
        return result;
    }

    private static String updateVersion(String fromLine, ServerAdapterMetaData metaData) {
        String[] parts = fromLine.split(" ");
        Matcher matcher = IMAGE_PATTERN.matcher(parts[1]);

        if (!matcher.matches()) {
            // Only perform the update of the Docker image tag when it is a payara official image.
            return fromLine;
        }

        StringBuilder newImage = new StringBuilder();
        newImage.append("payara/");
        newImage.append(getDockerServerVariantName());  // server-full, server-web etc
        newImage.append(':');
        newImage.append(metaData.getPayaraVersion());
        if (metaData.getJdkRuntime() == JDKRuntime.JDK11) {
            newImage.append("-jdk11");
        }
        parts[1] = newImage.toString();

        return String.join(" ", parts);
    }
}
