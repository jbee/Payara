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

import fish.payara.testing.core.AbstractPayaraTest;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicate the Payara Integration Testing based extension. Test class must be extending from {@link AbstractPayaraTest}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(PayaraContainerTestExtension.class)
public @interface PayaraContainerTest {
    /**
     * Defines the Docker Image which will be used for running the integration test. The value can be unspecified when
     * you want to make use of a ParameterizedTest but this is not recommended in combination with the CI environment.
     * In addition to the values 'server', 'micro' and 'custom', also the Payara Version and JDK version can be specified.
     * For more information, have a look at the guide.md.
     * @return Docker Image type, Payara Version, and JDK. See guide.md.
     */
    String value() default "";  // Set to the 'version' of the container or use ParameterizedTest

    /**
     * Define a non-zero value to activate the wait so that remote debugging can be started. For more information, see guide.md.
     * @return Number of seconds to wait for the remote debugging to attach or 0 to not start in debug mode.
     */
    long debugWait() default 0; // If != 0, activates debugging. Value is time to wait (seconds) when container is started to allow connection of debugger.

    /**
     * Defines if a test application ios deployed in the Docker Container or not. Currently a test application is always required when using the container type 'custom'.
     * @return true (default) when test application (war or ear) is deployed into the Docker Container.
     */
    boolean testApplication() default true;

    /**
     * When verbose logging (default is false) is activated, the server.log is send to the test run output.
     * @return true when verbose logging needs to be activated.
     */
    boolean verboseLogging() default false;

    /**
     * By default (value returns false), the '--noCluster'  option is used to start Payara Micro in the test. If integration test requires
     * a Payara Micro cluster, set this value to 'true' and look in the guide.md how to define the other instances.
     * @return
     */
    boolean microCluster() default false;  // false -> --noCluster for Micro.
}
