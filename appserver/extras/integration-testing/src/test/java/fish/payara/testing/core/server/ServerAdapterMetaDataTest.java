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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServerAdapterMetaDataTest {

    @Test
    void parse_basic() {
        ServerAdapterMetaData metaData = ServerAdapterMetaData.parse("server");
        assertThat(metaData).isNotNull();
        assertThat(metaData.getRuntimeType()).isEqualTo(RuntimeType.SERVER);
        assertThat(metaData.getPayaraVersion()).isEqualTo(Config.getVersion());
        assertThat(metaData.getJdkRuntime()).isEqualTo(Config.getJDKRuntime());
    }

    @Test
    void parse_full() {
        ServerAdapterMetaData metaData = ServerAdapterMetaData.parse("micro-5.193-jdk11");
        assertThat(metaData).isNotNull();
        assertThat(metaData.getRuntimeType()).isEqualTo(RuntimeType.MICRO);
        assertThat(metaData.getPayaraVersion()).isEqualTo("5.193");
        assertThat(metaData.getJdkRuntime()).isEqualTo(JDKRuntime.JDK11);
    }

    @Test
    void parse_jdkonly() {
        ServerAdapterMetaData metaData = ServerAdapterMetaData.parse("server--jdk11");
        assertThat(metaData).isNotNull();
        assertThat(metaData.getRuntimeType()).isEqualTo(RuntimeType.SERVER);
        assertThat(metaData.getPayaraVersion()).isEqualTo(Config.getVersion());
        assertThat(metaData.getJdkRuntime()).isEqualTo(JDKRuntime.JDK11);
    }

    @Test
    void parse_empty() {
        // test for the scenario when we have a parameterized test
        ServerAdapterMetaData metaData = ServerAdapterMetaData.parse("");
        assertThat(metaData).isNotNull();
        assertThat(metaData.getRuntimeType()).isNull();
        assertThat(metaData.getPayaraVersion()).isNull();
        assertThat(metaData.getJdkRuntime()).isNull();
    }

    @Test
    void parse_custom() {
        ServerAdapterMetaData metaData = ServerAdapterMetaData.parse("custom-5.193-jdk11");
        assertThat(metaData).isNotNull();
        assertThat(metaData.getRuntimeType()).isEqualTo(RuntimeType.CUSTOM);
        // Not that the following
        assertThat(metaData.getPayaraVersion()).isEqualTo(Config.getVersion());
        assertThat(metaData.getJdkRuntime()).isEqualTo(Config.getJDKRuntime());
    }

    @Test
    void parse_snapshot() {
        ServerAdapterMetaData metaData = ServerAdapterMetaData.parse("server-5.202-SNAPSHOT-jdk11");
        assertThat(metaData).isNotNull();
        assertThat(metaData.getRuntimeType()).isEqualTo(RuntimeType.SERVER);
        assertThat(metaData.getPayaraVersion()).isEqualTo("5.202-SNAPSHOT");
        assertThat(metaData.getJdkRuntime()).isEqualTo(JDKRuntime.JDK11);
    }

    @Test
    void parse_rc() {
        ServerAdapterMetaData metaData = ServerAdapterMetaData.parse("server-5.202-RC-jdk11");
        assertThat(metaData).isNotNull();
        assertThat(metaData.getRuntimeType()).isEqualTo(RuntimeType.SERVER);
        assertThat(metaData.getPayaraVersion()).isEqualTo("5.202-RC");
        assertThat(metaData.getJdkRuntime()).isEqualTo(JDKRuntime.JDK11);
    }

    @Test
    void parse_useDefaultJDKIfUnknow() {
        ServerAdapterMetaData metaData = ServerAdapterMetaData.parse("micro-5.193-something");
        assertThat(metaData).isNotNull();
        assertThat(metaData.getRuntimeType()).isEqualTo(RuntimeType.MICRO);
        assertThat(metaData.getPayaraVersion()).isEqualTo("5.193");
        assertThat(metaData.getJdkRuntime()).isEqualTo(Config.getJDKRuntime());
    }

}