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
import fish.payara.testing.core.server.ServerAdapterMetaData;
import fish.payara.testing.core.util.DockerImageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;

public class PlainJavaContainer extends AbstractContainer<PlainJavaContainer> {

    public static final String SSH_PASSWORD = "payara";

    private static final Logger LOGGER = LoggerFactory.getLogger(PlainJavaContainer.class);

    private ServerAdapter adapter;
    private ServerAdapterMetaData adapterMetaData;
    private boolean verboseLogging;

    public PlainJavaContainer(ServerAdapterMetaData adapterMetaData, boolean verboseLogging, String name) {
        super(DockerImageProcessor.getImage(adapterMetaData, name));
        this.adapterMetaData = adapterMetaData;
        this.verboseLogging = verboseLogging;
        setNetwork(Network.SHARED);
        adapter = new ServerAdapter();
    }

    @Override
    protected void configure() {
        super.configure();
        // FIXME Wrong exposed port
        containerConfiguration(adapter, verboseLogging, LOGGER);

        waitingFor(new NoWait());
    }

    @Override
    public String getApplicationPort() {
        return "28080";
    }

    @Override
    public int getMappedApplicationPort() {
        return getMappedPort(28080);
    }

    @Override
    public String getWebConsolePort() {
        throw new UnsupportedOperationException("No WebConsole Port available");
    }

}
