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

import org.assertj.core.api.Assertions;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class FileCopyHelper {

    private URL resource;
    private Path tempDir;

    private FileSystem fileSystem;

    /**
     * Construct an instance which copy the file or entire directory indicated by resource to the path.
     *
     * @param resource URL pointing to file or entire directory to copy. The jar locator is supported
     *                 through a custom FileSystem.
     * @param tempDir  Location to which file or directory contents needs to be copied.
     */
    public FileCopyHelper(URL resource, Path tempDir) {

        this.resource = resource;
        this.tempDir = tempDir;
    }

    /**
     * Copy the File to the directory
     *
     * @return Points to the copied Path of the file.
     */
    public Path copyToTemp() {
        Path result = null;
        Path path = definePath(resource);
        try {
            result = Files.copy(path, tempDir.resolve("Dockerfile"));
        } catch (IOException e) {
            Assertions.fail(e.getMessage());
        } finally {
            if (fileSystem != null) {
                try {
                    fileSystem.close();
                } catch (IOException e) {
                    Assertions.fail(e.getMessage());
                }
            }
        }
        return result;
    }

    private Path definePath(URL resource) {
        Path result = null;
        try {
            String uriString = resource.toURI().toString();
            if (uriString.contains("!")) {
                int idx = uriString.indexOf('!');
                // 4 -> jar:file:
                URI uri = new URI("jar", uriString.substring(4, idx), null);
                Map<String, String> env = new HashMap<>();
                env.put("create", "true");

                fileSystem = FileSystems.newFileSystem(uri, env);
                result = fileSystem.getPath("/").resolve(uriString.substring(idx + 2));
            } else {
                result = Paths.get(resource.toURI());
            }
        } catch (URISyntaxException | IOException e) {
            Assertions.fail(e.getMessage());
        }
        return result;
    }

    /**
     * Copy all files within the directory to the temp dir.
     */
    public void copyDependentFiles() {

        try {
            Files.find(Paths.get(resource.toURI()),
                    Integer.MAX_VALUE,
                    (filePath, fileAttr) -> fileAttr.isRegularFile())
                    .forEach(p -> copyToTemp(p, tempDir));
        } catch (IOException | URISyntaxException e) {
            Assertions.fail(e.getMessage());
        }

    }

    private void copyToTemp(Path path, Path tempDirWithPrefix) {

        Path targetPath = determineTargetPath(path, tempDirWithPrefix);
        File parentDirectory = targetPath.getParent().toFile();
        if (!parentDirectory.exists()) {
            boolean success = parentDirectory.mkdirs();
            if (!success) {
                Assertions.fail(String.format("Unable to create directory %s", parentDirectory));
            }
        }
        try {
            Files.copy(path, targetPath, REPLACE_EXISTING);
        } catch (IOException e) {
            Assertions.fail(e.getMessage());
        }
    }

    private Path determineTargetPath(Path path, Path tempDirWithPrefix) {
        int length = resource.getPath().length();
        String endPath = path.toString().substring(length);

        return tempDirWithPrefix.resolve(endPath);
    }
}
