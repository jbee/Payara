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
package fish.payara.testing.core.server;

import fish.payara.testing.core.config.Config;

public class ServerAdapterMetaData {

    private RuntimeType runtimeType;
    private String payaraVersion;
    private JDKRuntime jdkRuntime;
    private boolean testApplication;
    private boolean microCluster;

    private ServerAdapterMetaData() {
    }

    public boolean hasData() {
        return runtimeType != null;
    }

    public RuntimeType getRuntimeType() {
        return runtimeType;
    }

    public String getPayaraVersion() {
        return payaraVersion;
    }

    public JDKRuntime getJdkRuntime() {
        return jdkRuntime;
    }

    public boolean isTestApplication() {
        return testApplication;
    }

    public void setTestApplication(boolean testApplication) {
        this.testApplication = testApplication;
    }

    public void setMicroCluster(boolean microCluster) {
        this.microCluster = microCluster;
    }

    public boolean isMicroCluster() {
        return microCluster;
    }

    public static ServerAdapterMetaData parse(String data) {
        ServerAdapterMetaData result = new ServerAdapterMetaData();

        String[] parts = handlePredefinedValues(data).split("-");

        if (!parts[0].trim().isEmpty()) {
            result.runtimeType = RuntimeType.parse(parts[0].trim());
        } else {
            return result;  // When first part is empty, we are using @ParameterizedTest
        }
        if (parts.length > 1) {
            result.payaraVersion = restorePredefiniedValues(parts[1].trim());
        }
        if (parts.length > 2) {
            result.jdkRuntime = JDKRuntime.parse(parts[2].trim());
            if (result.jdkRuntime == JDKRuntime.UNKNOWN) {
                System.err.println("Unknown JDKRuntime definition :" + parts[2].trim());
                result.jdkRuntime = null;
            }

        }
        if (result.getRuntimeType() == RuntimeType.CUSTOM) {
            // When 'custom' defined, the Payara version and JDK version are always taken from JVM properties or default.
            result.payaraVersion = null;
            result.jdkRuntime = null;
        }

        if (result.payaraVersion == null || result.payaraVersion.isEmpty()) {
            result.payaraVersion = Config.getVersion();
        }

        if (result.jdkRuntime == null) {
            result.jdkRuntime = Config.getJDKRuntime();
        }

        return result;
    }

    private static String restorePredefiniedValues(String data) {
        return data.replaceAll("_SNAPSHOT", "-SNAPSHOT").replaceAll("_RC", "-RC");
    }

    private static String handlePredefinedValues(String data) {
        // The version can have -SNAPSHOT or -RC.
        return data.replaceAll("-SNAPSHOT", "_SNAPSHOT").replaceAll("-RC", "_RC");
    }
}
