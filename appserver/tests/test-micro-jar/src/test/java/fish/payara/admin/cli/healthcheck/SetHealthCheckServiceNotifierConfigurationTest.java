/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2019 Payara Foundation and/or its affiliates. All rights reserved.
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
package fish.payara.admin.cli.healthcheck;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import fish.payara.admin.cli.AsAdminIntegrationTest;
import fish.payara.micro.ClusterCommandResult;
import fish.payara.test.domain.healthcheck.HealthCheckService;
import fish.payara.test.domain.healthcheck.HealthCheckServiceConfiguration;
import fish.payara.test.domain.notification.Notifier;
import fish.payara.test.domain.notification.NotifierExecutionOptions;

/**
 * Verifies the correctness of the {@code SetHealthCheckServiceNotifierConfiguration} command.
 */
public class SetHealthCheckServiceNotifierConfigurationTest extends AsAdminIntegrationTest {

    private HealthCheckServiceConfiguration config;
    private HealthCheckService service;
    private Class<?> logNotifierType;

    @Before
    public void setUp() {
        config = HealthCheckServiceConfiguration.from(server);
        service = HealthCheckService.from(server);
        logNotifierType = server.getClass(Notifier.LOG_NOTIFIER_CLASS_NAME);
    }

    @Test
    public void setHealthCheckServiceConfiguration_EnabledIsMandatory() {
        assertMissingParameter("enabled", asadmin("set-healthcheck-service-notifier-configuration", 
                "--notifier", "LOG"));
    }

    @Test
    public void setHealthCheckServiceConfiguration_NotifierIsMandatory() {
        assertMissingParameter("notifierName", asadmin("set-healthcheck-service-notifier-configuration", 
                "--enabled", "true"));
    }

    @Test
    public void setHealthCheckServiceConfiguration_NotifierNames() {
        String[] names = { "LOG", "HIPCHAT", "SLACK", "JMS", "EMAIL", "XMPP", "SNMP", "EVENTBUS", "NEWRELIC",
                "DATADOG", "CDIEVENTBUS" };
        for (String notiferName : names) {
            ClusterCommandResult result = asadmin("set-healthcheck-service-notifier-configuration", 
                    "--notifier", notiferName, 
                    "--enabled", "true");
            assertSuccess(result); // just check the name got accepted
        }
    }

    @Test
    public void setHealthCheckServiceConfiguration_Enabled() {
        boolean logEnabled = getLogNotifierOptions().isEnabled();
        ClusterCommandResult result = asadmin("set-healthcheck-service-notifier-configuration", 
                "--notifier", "LOG",
                "--enabled", "true");
        assertSuccess(result);
        Notifier logNotifier = config.getNotifierByType(logNotifierType);
        assertTrue(logNotifier.getEnabled());
        assertUnchanged(logEnabled, getLogNotifierOptions().isEnabled());
        result = asadmin("set-healthcheck-service-notifier-configuration", 
                "--notifier", "LOG",
                "--enabled", "false");
        assertFalse(logNotifier.getEnabled());
        assertUnchanged(logEnabled, getLogNotifierOptions().isEnabled());
    }

    @Test
    public void setHealthCheckServiceConfiguration_EnabledDynamic() {
        ensureHealthChecksAreEnabled();
        ClusterCommandResult result = asadmin("set-healthcheck-service-notifier-configuration", 
                "--notifier", "LOG",
                "--enabled", "true",
                "--dynamic", "true");
        assertSuccess(result);
        Notifier logNotifier = config.getNotifierByType(logNotifierType);
        assertTrue(logNotifier.getEnabled());
        assertTrue(getLogNotifierOptions().isEnabled());
        result = asadmin("set-healthcheck-service-notifier-configuration", 
                "--notifier", "LOG",
                "--enabled", "false",
                "--dynamic", "true");
        assertFalse(logNotifier.getEnabled());
        assertFalse(getLogNotifierOptions().isEnabled());
    }

    @Test
    public void setHealthCheckServiceConfiguration_Noisy() {
        boolean logNoisy = getLogNotifierOptions().isNoisy();
        ClusterCommandResult result = asadmin("set-healthcheck-service-notifier-configuration", 
                "--notifier", "LOG",
                "--enabled", "true",
                "--noisy", "true");
        assertSuccess(result);
        Notifier logNotifier = config.getNotifierByType(logNotifierType);
        assertTrue(logNotifier.getNoisy());
        assertUnchanged(logNoisy, getLogNotifierOptions().isNoisy());
        result = asadmin("set-healthcheck-service-notifier-configuration", 
                "--notifier", "LOG",
                "--enabled", "false",
                "--noisy", "false");
        assertFalse(logNotifier.getNoisy());
        assertUnchanged(logNoisy, getLogNotifierOptions().isNoisy());
    }

    @Test
    public void setHealthCheckServiceConfiguration_NoisyDynamic() {
        ensureHealthChecksAreEnabled();
        ClusterCommandResult result = asadmin("set-healthcheck-service-notifier-configuration", 
                "--notifier", "LOG",
                "--enabled", "true",
                "--noisy", "true",
                "--dynamic", "true");
        assertSuccess(result);
        Notifier logNotifier = config.getNotifierByType(logNotifierType);
        assertTrue(logNotifier.getNoisy());
        assertTrue(getLogNotifierOptions().isNoisy());
        result = asadmin("set-healthcheck-service-notifier-configuration", 
                "--notifier", "LOG",
                "--enabled", "true",
                "--noisy", "false",
                "--dynamic", "true");
        assertFalse(logNotifier.getNoisy());
        assertFalse(getLogNotifierOptions().isNoisy());
    }

    private NotifierExecutionOptions getLogNotifierOptions() {
        return service.getNotifierExecutionOptions("LOG");
    }

    /**
     * Dynamic changes only take effect when the health check service is enabled so we make sure it is.
     */
    private void ensureHealthChecksAreEnabled() {
        if (service.isEnabled()) {
            return; // already enabled, fine
        }
        assertSuccess(asadmin("set-healthcheck-configuration", 
                "--enabled", "true", 
                "--dynamic", "true"));
    }
}