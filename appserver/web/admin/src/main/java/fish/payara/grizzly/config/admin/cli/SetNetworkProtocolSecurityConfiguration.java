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
package fish.payara.grizzly.config.admin.cli;

import java.text.MessageFormat;
import java.util.concurrent.TimeUnit;
import java.beans.PropertyVetoException;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import com.sun.enterprise.config.serverbeans.Config;
import com.sun.enterprise.util.SystemPropertyConstants;
import com.sun.enterprise.v3.services.impl.GrizzlyService;
import org.glassfish.api.ActionReport;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.config.support.CommandTarget;
import org.glassfish.config.support.TargetType;
import org.glassfish.grizzly.config.dom.NetworkListener;
import org.glassfish.grizzly.config.dom.Protocol;
import org.glassfish.grizzly.config.dom.Ssl;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.internal.api.Target;
import org.glassfish.web.admin.LogFacade;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.SingleConfigCode;
import org.jvnet.hk2.config.TransactionFailure;

/**
 *
 * @author jonathan
 */
@ExecuteOn({RuntimeType.DAS, RuntimeType.INSTANCE})
@TargetType(value = {CommandTarget.DAS, CommandTarget.STANDALONE_INSTANCE, CommandTarget.CLUSTER, CommandTarget.CONFIG, CommandTarget.DEPLOYMENT_GROUP})
@Service(name = "set-network-protocol-security-configuration")
@PerLookup
@RestEndpoints({
    @RestEndpoint(configBean = Ssl.class,
            opType = RestEndpoint.OpType.POST,
            description = "Sets Network protocol security configuration")
})
public class SetNetworkProtocolSecurityConfiguration implements AdminCommand {
    
    private static final Logger LOGGER = LogFacade.getLogger();

    @Param(name = "enabled", optional = true)
    private Boolean enabled;

    @Param(name = "dynamic", optional = true)
    private Boolean dynamic;

    @Param(name = "tls1-enabled", optional = true)
    private Boolean tls1Enabled;

    @Param(name = "tls11-enabled", optional = true)
    private Boolean tls11Enabled;

    @Param(name = "tls12-enabled", optional = true)
    private Boolean tls12Enabled;

    @Param(name = "tls13-enabled", optional = true)
    private Boolean tls13Enabled;

    @Param(name = "keystore", optional = true)
    private String keyStore;

    @Param(name = "keystore-password", optional = true)
    private String keyStorePassword;

    @Param(name = "truststore", optional = true)
    private String trustStore;

    @Param(name = "truststore-password", optional = true)
    private String trustStorePassword;

    @Param(name = "trust-algorithm", optional = true)
    private String trustAlgorithm;
    
    @Param(name="session-cache-size", optional = true)
    private Integer sessionCacheSize;
    
    @Param(name="session-timeout", optional=true)
    private Integer sessionTimeout;
    
    @Param(name="certificate-nickname", optional=true)
    private String certName;

    @Param(name = "client-auth", acceptableValues = "need,want,", optional = true)
    private String clientAuth;

    @Param(name = "protocol-name", primary = true)
    private String protocolName;

    @Param(name = "target", optional = true, defaultValue = SystemPropertyConstants.DAS_SERVER_NAME)
    private String target;

    @Inject
    Target targetUtil;

    @Inject
    private GrizzlyService service;

    @Override
    public void execute(AdminCommandContext context) {
        ActionReport report = context.getActionReport();
        Config config = targetUtil.getConfig(target);
        Protocol protocol = config.getNetworkConfig().getProtocols().findProtocol(protocolName);
        
        try {
            ConfigSupport.apply((Protocol param) -> {
                if (enabled != null) {
                    param.setSecurityEnabled(enabled.toString());
                }
                return null;
            }, protocol);
        } catch (TransactionFailure ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            report.setMessage(MessageFormat.format(LOGGER.getResourceBundle().getString(LogFacade.FAILED_CHANGE_SECURITY_PROTOCOL), protocol.getName())
                    + (ex.getMessage() == null ? "No reason given" : ex.getMessage()));
            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
            report.setFailureCause(ex);
            return;
        }

        try {
            ConfigSupport.apply(new SingleConfigCode<Ssl>() {
                @Override
                public Object run(final Ssl sslConfig) throws PropertyVetoException, TransactionFailure {

                    if (tls1Enabled != null) {
                        sslConfig.setTlsEnabled(tls1Enabled.toString());
                    }
                    if (tls11Enabled != null) {
                        sslConfig.setTlsEnabled(tls11Enabled.toString());
                    }
                    if (tls12Enabled != null) {
                        sslConfig.setTlsEnabled(tls12Enabled.toString());
                    }
                    if (tls13Enabled != null) {
                        sslConfig.setTlsEnabled(tls13Enabled.toString());
                    }
                    if (keyStore != null) {
                        sslConfig.setKeyStore(keyStore);
                    }
                    if (keyStorePassword != null) {
                        sslConfig.setKeyStorePassword(keyStorePassword);
                    }
                    if (trustStore != null) {
                        sslConfig.setTrustStore(trustStore);
                    }
                    if (trustStorePassword != null) {
                        sslConfig.setTrustStorePassword(trustStorePassword);
                    }
                    if (trustAlgorithm != null) {
                        sslConfig.setTrustAlgorithm(trustAlgorithm);
                    }
                    if (clientAuth != null) {
                        sslConfig.setClientAuth(clientAuth);
                    }
                    if (sessionCacheSize != null) {
                        sslConfig.setTlsSessionCacheSize(sessionCacheSize.toString());
                    }
                    if (sessionTimeout != null) {
                        sslConfig.setTlsSessionTimeout(sessionTimeout.toString());
                    }
                    if (certName != null) {
                        sslConfig.setCertNickname(certName);
                    }
                    return null;
                }
            }, protocol.getSsl());
        } catch (TransactionFailure ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            report.setMessage(MessageFormat.format(LOGGER.getResourceBundle().getString(LogFacade.FAILED_CHANGE_SECURITY_PROTOCOL), protocol.getName())
                    + (ex.getMessage() == null ? "No reason given" : ex.getMessage()));
            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
            report.setFailureCause(ex);
            return;
        }

        if (dynamic != null && dynamic) {
            for (NetworkListener listener : protocol.findNetworkListeners()) {
                try {
                    if (!"admin-listener".equals(listener.getName())) {
                        service.restartNetworkListener(listener, 10, TimeUnit.SECONDS);
                    }
                } catch (IOException | TimeoutException ex) {
                    report.setMessage(MessageFormat.format("Failed to restart listener {0}, {1}", listener.getName(),
                            (ex.getMessage() == null ? "No reason given" : ex.getMessage())));
                    report.setActionExitCode(ActionReport.ExitCode.FAILURE);
                    report.setFailureCause(ex);
                    return;
                }
            }
        }

        report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
    }

}
