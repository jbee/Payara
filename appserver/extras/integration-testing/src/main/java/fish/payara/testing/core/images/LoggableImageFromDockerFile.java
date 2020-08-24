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
package fish.payara.testing.core.images;

import fish.payara.testing.core.exception.UnexpectedException;
import fish.payara.testing.core.server.ServerAdapterMetaData;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class LoggableImageFromDockerFile extends ImageFromDockerfile {

    private ServerAdapterMetaData metaData;
    private String baseImage = "???";
    private boolean plainImage;

    public LoggableImageFromDockerFile(String dockerImageName, ServerAdapterMetaData metaData) {
        super(dockerImageName);
        this.metaData = metaData;
    }

    public LoggableImageFromDockerFile(String dockerImageName) {
        this(dockerImageName, null);
    }

    public LoggableImageFromDockerFile(String dockerImageName, boolean plainImage) {
        this(dockerImageName, null);
        this.plainImage = plainImage;
    }

    @Override
    public ImageFromDockerfile withDockerfile(Path dockerfile) {

        defineBaseImage(dockerfile);

        return super.withDockerfile(dockerfile);
    }

    // TODO We do not have any logging when Docker starts to download a new image.
    //  This can take a while so best to have some logging.
    //  But not yet determined how it can be done.
    //  It is part of the 'docker build' which is performed by org.testcontainers.images.builder.ImageFromDockerfile.resolve

    private void defineBaseImage(Path dockerfile) {
        if (metaData != null) {
            // We can determine base image from metadata, no need to parse DockerFile
            baseImage = String.format("%s - %s - %s", metaData.getRuntimeType().name(), metaData.getPayaraVersion(), metaData.getJdkRuntime().name());
            return;
        }
        // Parsing DockerFile to
        try {
            List<String> lines = Files.readAllLines(dockerfile, StandardCharsets.UTF_8);
            // TODO This doesn't work for multi stage build files in Custom type.
            Optional<String> fromClause = lines.stream().filter(l -> l.trim().startsWith("FROM")).findAny();
            String name = plainImage ? "PlainContainer" : "Custom";
            fromClause.ifPresent(s -> baseImage = name + s.trim().split(" ")[1]);
        } catch (IOException e) {
            throw new UnexpectedException("IOException during reading of the dockerFile", e);
        }
    }


    @Override
    public String toString() {
        return String.format("%s running on [%s]", getDockerImageName(), baseImage);
    }
}
