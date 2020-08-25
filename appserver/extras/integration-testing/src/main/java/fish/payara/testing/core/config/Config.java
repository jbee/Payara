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
package fish.payara.testing.core.config;

import fish.payara.testing.core.server.JDKRuntime;
import fish.payara.testing.core.server.PayaraServerVariant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Config {

    private static final Logger LOGGER = LoggerFactory.getLogger(Config.class);

    private static Double factor;

    private Config() {
    }

    public static String getVersion() {
        return System.getProperty("payara.version", Defaults.VERSION);

    }

    public static JDKRuntime getJDKRuntime() {
        return JDKRuntime.parse(System.getProperty("payara.test.container.jdk", Defaults.JDK_RUNTIME));
    }

    public static PayaraServerVariant getPayaraServerVariant() {
        return PayaraServerVariant.parse(System.getProperty("payara.test.container.variant", "full"));
    }

    /**
     * @return The amount of time (in seconds) to wait for a runtime to start before
     * assuming that application start has failed and aborting the start process.
     * <p>
     * With the env property 'payara.test.timeout.factor' you can increase this.
     */
    public static int getAppStartTimeout() {

        return (int) (30 * getFactor());
    }

    public static int getElementWaitTimeout() {
        return (int) (30 * getFactor());
    }

    public static double getFactor() {
        if (factor == null) {
            factor = readFactor();
        }
        return factor;
    }

    private static Double readFactor() {
        String value = System.getenv("payara_test_timeout_factor");
        double result = 1.0;
        if (value == null || value.isEmpty()) {
            return result;
        }
        try {
            result = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            LOGGER.warn(String.format("The env variable payara_test_timeout_factor is not a valid number(double) : '%s'. Using factor 1.0", value));
        }
        if (result <= 0.0) {
            result = 1.0;
            LOGGER.warn(String.format("The env variable payara_test_timeout_factor is zero or negative : '%s'. Using factor 1.0", value));
        }
        return result;
    }

    private static class Defaults {
        // read version from Maven.
        static final String VERSION = Defaults.class.getPackage().getImplementationVersion();
        static final String JDK_RUNTIME = JDKRuntime.JDK8.name();
    }
}
