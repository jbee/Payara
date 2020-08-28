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
package fish.payara.testing.core.jupiter;

import fish.payara.testing.core.DockerImageContainer;
import fish.payara.testing.core.PayaraContainer;
import fish.payara.testing.core.PayaraMicroContainer;
import fish.payara.testing.core.PlainJavaContainer;
import fish.payara.testing.core.container.LazyStartingVideoWebDriverContainer;
import fish.payara.testing.core.exception.UnexpectedException;
import fish.payara.testing.core.server.ServerAdapterMetaData;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.platform.commons.support.AnnotationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.lifecycle.TestDescription;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Control and manipulate all testContainers.
 */
public class TestcontainersController {

    private static final Logger LOG = LoggerFactory.getLogger(TestcontainersController.class);

    private Set<GenericContainer<?>> containers;
    private Class<?> testClass;
    private Field payaraContainerField;
    private List<Field> plainJavaContainerFields;
    private List<Field> dockerImageContainerFields;
    private List<Field> microContainerFields;

    private PayaraContainer payaraContainer;
    private List<PlainJavaContainer> plainJavaContainers;
    private List<DockerImageContainer> dockerImageContainers;
    private List<PayaraMicroContainer> microImageContainers;

    private LazyStartingVideoWebDriverContainer webDriverContainer;

    public TestcontainersController(Class<?> testClass) {
        this.testClass = testClass;
        containers = discoverContainers(testClass); // This contains not the PayaraContainer and PlainContainer.
        // They are added by the config() method.
    }

    protected Set<GenericContainer<?>> discoverContainers(Class<?> clazz) {
        plainJavaContainerFields = new ArrayList<>();
        dockerImageContainerFields = new ArrayList<>();
        microContainerFields = new ArrayList<>();
        Set<GenericContainer<?>> discoveredContainers = new HashSet<>();
        for (Field containerField : AnnotationSupport.findAnnotatedFields(clazz, Container.class)) {
            if (!Modifier.isPublic(containerField.getModifiers())) {
                throw new ExtensionConfigurationException("@Container annotated fields must be public visibility");
            }
            if (!Modifier.isStatic(containerField.getModifiers())) {
                throw new ExtensionConfigurationException("@Container annotated fields must be static");
            }
            boolean isStartable = GenericContainer.class.isAssignableFrom(containerField.getType());
            if (!isStartable) {
                throw new ExtensionConfigurationException("@Container annotated fields must be a subclass of " + GenericContainer.class);
            }
            try {
                boolean generic = true;
                if (containerField.getType().equals(PayaraContainer.class)) {
                    payaraContainerField = containerField;
                    payaraContainerField.setAccessible(true);
                    generic = false;
                }
                if (containerField.getType().equals(PlainJavaContainer.class)) {
                    plainJavaContainerFields.add(containerField);
                    containerField.setAccessible(true);
                    generic = false;
                }
                if (containerField.getType().equals(DockerImageContainer.class)) {
                    dockerImageContainerFields.add(containerField);
                    containerField.setAccessible(true);
                    generic = false;
                }
                if (containerField.getType().equals(PayaraMicroContainer.class)) {
                    microContainerFields.add(containerField);
                    containerField.setAccessible(true);
                    generic = false;
                }
                if (generic) {
                    // Some other container the developer uses in the test.
                    GenericContainer<?> startableContainer = (GenericContainer<?>) containerField.get(null);
                    startableContainer.setNetwork(Network.SHARED);
                    discoveredContainers.add(startableContainer);

                    if (startableContainer instanceof LazyStartingVideoWebDriverContainer) {
                        webDriverContainer = (LazyStartingVideoWebDriverContainer) startableContainer;
                    }
                }
            } catch (IllegalArgumentException | IllegalAccessException e) {
                LOG.warn("Unable to access field " + containerField, e);
            }
        }
        return discoveredContainers;
    }

    public void config(ServerAdapterMetaData metaData, boolean verboseLogging, boolean startWithDebug, boolean databaseRequired) {
        // Configure the Payara and the Plain Java containers.
        // ServerAdapterInstance determine the Docker image which will be used.

        // This method is called in the BeforeAll when no ParameterizedTest is executed or by BeforeEach when ParameterizedTest
        plainJavaContainers = new ArrayList<>();
        dockerImageContainers = new ArrayList<>();
        microImageContainers = new ArrayList<>();
        payaraContainer = new PayaraContainer(metaData, verboseLogging, startWithDebug, databaseRequired);
        try {
            payaraContainerField.set(null, payaraContainer);
            containers.add(payaraContainer);

            for (Field containerField : plainJavaContainerFields) {
                PlainJavaContainer plainJavaContainer = new PlainJavaContainer(metaData, verboseLogging, containerField.getName());
                containerField.set(null, plainJavaContainer);
                containers.add(plainJavaContainer);
                plainJavaContainers.add(plainJavaContainer);
            }
            for (Field containerField : dockerImageContainerFields) {
                DockerImageContainer dockerImageContainer = new DockerImageContainer(containerField.getName());
                containerField.set(null, dockerImageContainer);
                containers.add(dockerImageContainer);
                dockerImageContainers.add(dockerImageContainer);
            }
            for (Field containerField : microContainerFields) {
                PayaraMicroContainer microImageContainer = new PayaraMicroContainer(metaData, verboseLogging, containerField.getName());
                containerField.set(null, microImageContainer);
                containers.add(microImageContainer);
                microImageContainers.add(microImageContainer);
            }
        } catch (IllegalAccessException e) {
            Assertions.fail(e.getMessage());
        }

    }

    public void start() {

        LOG.info("Starting containers in parallel for " + testClass);
        for (GenericContainer<?> c : containers) {
            LOG.info("  " + c.getImage());
        }
        long start = System.currentTimeMillis();
        containers.parallelStream().forEach(GenericContainer::start);
        LOG.info("All containers started in " + (System.currentTimeMillis() - start) + "ms");
    }

    public void startPayaraContainer(Method requiredTestMethod) {
        // In case of the ParameterizedTest, we start Payara Container
        LOG.info("Starting container  " + testClass + "#" + requiredTestMethod.getName());

        LOG.info("  " + payaraContainer.getImage());

        long start = System.currentTimeMillis();
        payaraContainer.start();
        LOG.info("Container started in " + (System.currentTimeMillis() - start) + "ms");
    }

    public void startPlainContainers(Method requiredTestMethod) {
        // In case of the ParameterizedTest, we start Plain Containers
        // TODO Merge with startPayaraContainer
        LOG.info("Starting plain Java containers  " + testClass + "#" + requiredTestMethod.getName());
        long start = System.currentTimeMillis();

        for (PlainJavaContainer plainJavaContainer : plainJavaContainers) {

            LOG.info("  " + plainJavaContainer.getImage());
            plainJavaContainer.start();
        }

        LOG.info("Containers started in " + (System.currentTimeMillis() - start) + "ms");
    }

    public void startDockerImageContainers(Method requiredTestMethod) {
        // In case of the ParameterizedTest, we start Plain Containers
        // FIXME Merge with startPayaraContainer
        LOG.info("Starting Docker Image containers  " + testClass + "#" + requiredTestMethod.getName());
        long start = System.currentTimeMillis();

        for (DockerImageContainer dockerImageContainer : dockerImageContainers) {
            LOG.info("  " + dockerImageContainer.getImage());
            dockerImageContainer.start();

        }

        LOG.info("Containers started in " + (System.currentTimeMillis() - start) + "ms");
    }

    // TODO Support for Starting microImageContainers in ParameterizedTest.

    public void stop() throws IllegalAccessException {
        // Stop all Containers in the AfterAll. Some containers can already be stopped by the AfterEach.
        long start = System.currentTimeMillis();
        containers.parallelStream().forEach(GenericContainer::stop);
        LOG.info("All containers stopped in " + (System.currentTimeMillis() - start) + "ms");
        payaraContainerField.set(null, null);
        for (Field plainJavaContainerField : plainJavaContainerFields) {
            plainJavaContainerField.set(null, null);
        }
        for (Field dockerImageContainerField : dockerImageContainerFields) {
            dockerImageContainerField.set(null, null);
        }
    }

    public void stopPayaraContainer() throws IllegalAccessException {
        // Stop Payara Container when using ParameterizedTest in the AfterEach
        containers.remove(payaraContainer);
        long start = System.currentTimeMillis();
        payaraContainer.stop();
        LOG.info("Payara container stopped in " + (System.currentTimeMillis() - start) + "ms");
        payaraContainerField.set(null, null);
    }

    public void stopPlainContainers() throws IllegalAccessException {
        // Stop Plain Java Containers when using ParameterizedTest in the AfterEach
        long start = System.currentTimeMillis();
        for (PlainJavaContainer plainJavaContainer : plainJavaContainers) {
            plainJavaContainer.stop();
        }
        for (Field plainJavaContainerField : plainJavaContainerFields) {
            plainJavaContainerField.set(null, null);
        }
        LOG.info("Plain Java containers stopped in " + (System.currentTimeMillis() - start) + "ms");
    }

    public void stopDockerImageContainers() throws IllegalAccessException {
        // Stop Plain Java Containers when using ParameterizedTest in the AfterEach
        long start = System.currentTimeMillis();
        for (DockerImageContainer dockerImageContainer : dockerImageContainers) {
            dockerImageContainer.stop();
        }

        for (Field dockerImageContainerField : dockerImageContainerFields) {
            dockerImageContainerField.set(null, null);
        }
        LOG.info("Docker Image containers stopped in " + (System.currentTimeMillis() - start) + "ms");
    }

    // TODO Support for Stopping microImageContainers in ParameterizedTest.

    public void afterTest(TestDescription description, Optional<Throwable> throwable) {
        // So that we can stop and save the recoding if test failed
        webDriverContainer.afterTest(description, throwable);
    }

    public void startRecording() {
        // Make sure this in only called when recording is not already running.
        webDriverContainer.startRecording();
    }

    public void printDebugPort(long debugWait) {
        System.out.println("******* Debug Information ******");
        System.out.println(String.format("Connect remote debugger to port %s for Payara Server", payaraContainer.getMappedPort(9009)));
        System.out.println("******* Debug Information ******");
        try {
            Thread.sleep(debugWait * 1000);  // Wait some seconds.
        } catch (InterruptedException e) {
            throw new UnexpectedException("InterruptedException during the wait time for the support of debugger", e);
        }
    }
}
