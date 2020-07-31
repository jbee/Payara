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

import fish.payara.testing.core.DatabaseRequiredTest;
import fish.payara.testing.core.server.ServerAdapterMetaData;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.platform.commons.util.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.lifecycle.TestDescription;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

public class PayaraContainerTestExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, TestWatcher {

    static final Logger LOGGER = LoggerFactory.getLogger(PayaraContainerTestExtension.class);

    private TestcontainersController controller;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        // Once fore the test class. Start up the non Payara specific containers or all in case no parameterizedTest is used.
        Class<?> testClass = context.getRequiredTestClass();
        boolean databaseRequired = checkMarker(testClass, DatabaseRequiredTest.class);
        PayaraContainerTest payaraContainerTest = testClass.getAnnotation(PayaraContainerTest.class);
        boolean startWithDebug = payaraContainerTest.debugWait() != 0;
        boolean verboseLogging = payaraContainerTest.verboseLogging() || Boolean.parseBoolean(System.getProperty("payara.test.container.logging.verbose"));

        controller = new TestcontainersController(testClass);

        ServerAdapterMetaData adapterMetaData = ServerAdapterMetaData.parse(payaraContainerTest.value());
        adapterMetaData.setTestApplication(payaraContainerTest.testApplication());
        adapterMetaData.setMicroCluster(payaraContainerTest.microCluster());

        if (adapterMetaData.hasData()) {
            // if hasData return false, we assume a parameterized Test, see beforeEach
            controller.config(adapterMetaData, verboseLogging, startWithDebug, databaseRequired);
        }
        controller.start();
        if (startWithDebug) {
            controller.printDebugPort(payaraContainerTest.debugWait());
        }
        injectController(context);
    }

    private boolean checkMarker(Class<?> testClass, Class<DatabaseRequiredTest> markerClass) {
        boolean result = false;
        for (Class<?> anInterface : testClass.getInterfaces()) {
            if (anInterface.equals(markerClass)) {
                result = true;
            }
        }
        return result;
    }

    private void injectController(ExtensionContext context) {
        // Inject TestContainersController into the Test Class.
        Class<?> testClass = context.getRequiredTestClass();
        List<Field> fields = ReflectionUtils.findFields(testClass, f -> f.getType().equals(TestcontainersController.class), ReflectionUtils.HierarchyTraversalMode.TOP_DOWN);
        fields.forEach(this::setFieldValue);
    }

    private void setFieldValue(Field f) {
        f.setAccessible(true);
        try {
            f.set(null, controller);
        } catch (IllegalAccessException e) {
            Assertions.fail(e.getMessage());
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        // Make sure all containers stop after the test.
        controller.stop();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        // In case we use a ParameterizedTest, startup the required containers.
        Method requiredTestMethod = context.getRequiredTestMethod();
        if (requiredTestMethod.getAnnotation(ParameterizedTest.class) != null) {
            // Hacky way around the fact that you can't access the value for this Parameterized test properly.
            Object testDescriptor = getValueOf(context, "testDescriptor");
            Object invocationContext = getValueOf(testDescriptor, "invocationContext");
            Object[] arguments = getValueOf(invocationContext, "arguments");

            ServerAdapterMetaData adapterMetaData = ServerAdapterMetaData.parse(arguments[0].toString());// FIXME can arguments be null??

            Class<?> testClass = context.getRequiredTestClass();
            boolean databaseRequired = checkMarker(testClass, DatabaseRequiredTest.class);
            PayaraContainerTest payaraContainerTest = testClass.getAnnotation(PayaraContainerTest.class);
            boolean startWithDebug = payaraContainerTest.debugWait() != 0;
            boolean verboseLogging = payaraContainerTest.verboseLogging() || Boolean.parseBoolean(System.getProperty("payara.test.container.logging.verbose"));

            adapterMetaData.setTestApplication(payaraContainerTest.testApplication());
            adapterMetaData.setMicroCluster(payaraContainerTest.microCluster());

            controller.config(adapterMetaData, verboseLogging, startWithDebug, databaseRequired);
            controller.startPayaraContainer(requiredTestMethod);
            controller.startPlainContainers(requiredTestMethod); // Start nodes
            controller.stopDockerImageContainers();
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        // In case we do a ParameterizedTest, stop Payara and Plain Java Containers.
        Method requiredTestMethod = context.getRequiredTestMethod();
        if (requiredTestMethod.getAnnotation(ParameterizedTest.class) != null) {
            controller.stopPayaraContainer();
            controller.stopPlainContainers();
            controller.stopDockerImageContainers();
        }
    }

    public static <T> T getValueOf(Object target, String fieldName) throws NoSuchFieldException {
        Field field = findFieldInHierarchy(target, fieldName);

        if (field == null) {
            throw new NoSuchFieldException(String.format("Field %s not found", fieldName));
        }
        field.setAccessible(true);
        try {
            return (T) field.get(target);
        } catch (IllegalAccessException e) {
            Assertions.fail(e.getMessage());
        }

        return null;
    }

    private static Field findFieldInHierarchy(Object target, String fieldName) throws NoSuchFieldException {
        if (target instanceof Class<?>) {
            // static field
            return ((Class<?>) target).getDeclaredField(fieldName);
        }
        Class<?> targetClass = target.getClass();
        Field field = findField(targetClass, fieldName);
        while (field == null && !Object.class.equals(targetClass)) {
            targetClass = targetClass.getSuperclass();
            field = findField(targetClass, fieldName);
        }
        return field;
    }

    private static Field findField(Class<?> targetClass, String fieldName) {
        Field result = null;
        for (Field field : targetClass.getDeclaredFields()) {
            if (fieldName.equals(field.getName())) {
                result = field;
            }
        }
        return result;
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        // When test fails, make sure video is written
        controller.afterTest(toDescription(context), Optional.of(cause));
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        // When test success, make sure video cleanup is done properly
        controller.afterTest(toDescription(context), Optional.empty());
    }

    private TestDescription toDescription(final ExtensionContext context) {
        return new TestDescription() {
            public String getTestId() {
                return context.getDisplayName();
            }

            public String getFilesystemFriendlyName() {
                return context.getRequiredTestClass().getName() + "-" + context.getRequiredTestMethod().getName();

            }
        };
    }

}
