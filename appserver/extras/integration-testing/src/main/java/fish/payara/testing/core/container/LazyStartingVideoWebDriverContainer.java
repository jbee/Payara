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
package fish.payara.testing.core.container;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.google.common.collect.ImmutableSet;
import fish.payara.testing.core.config.Config;
import fish.payara.testing.core.exception.UnexpectedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.rnorth.ducttape.timeouts.Timeouts;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.*;
import org.testcontainers.containers.traits.LinkableContainer;
import org.testcontainers.containers.traits.VncService;
import org.testcontainers.containers.wait.HostPortWaitStrategy;
import org.testcontainers.containers.wait.LogMessageWaitStrategy;
import org.testcontainers.containers.wait.WaitAllStrategy;
import org.testcontainers.containers.wait.WaitStrategy;
import org.testcontainers.lifecycle.TestDescription;
import org.testcontainers.lifecycle.TestLifecycleAware;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;

// Mainly a copy of BrowserWebDriverContainer but I wanted to start the video lazily (which avoid useless seconds (or minute) at the beginning when preparing DeploymentGroup for example
// But BrowserWebDriverContainer is not designed for extension and thus ended up duplicating the code.
// only changes in #containerIsStarted and #startRecording.
public class LazyStartingVideoWebDriverContainer<SELF extends LazyStartingVideoWebDriverContainer<SELF>> extends GenericContainer<SELF> implements VncService, LinkableContainer, TestLifecycleAware {

    private static final String CHROME_IMAGE = "selenium/standalone-chrome-debug:%s";
    private static final String FIREFOX_IMAGE = "selenium/standalone-firefox-debug:%s";

    private static final String DEFAULT_PASSWORD = "secret";
    private static final int SELENIUM_PORT = 4444;
    private static final int VNC_PORT = 5900;

    private static final String NO_PROXY_KEY = "no_proxy";

    @Nullable
    private Capabilities capabilities;
    private boolean customImageNameIsSet = false;

    @Nullable
    private RemoteWebDriver driver;
    private BrowserWebDriverContainer.VncRecordingMode recordingMode = BrowserWebDriverContainer.VncRecordingMode.RECORD_FAILING;
    private RecordingFileFactory recordingFileFactory;
    private File vncRecordingDirectory = new File("/tmp");

    private VncRecordingContainer vncRecordingContainer = null;

    private boolean recordingAvailable;

    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserWebDriverContainer.class);

    /**
     */
    public LazyStartingVideoWebDriverContainer() {
        final WaitStrategy logWaitStrategy = new LogMessageWaitStrategy()
                .withRegEx(".*(RemoteWebDriver instances should connect to|Selenium Server is up and running).*\n")
                .withStartupTimeout(Duration.of(15, SECONDS));

        this.waitStrategy = new WaitAllStrategy()
                .withStrategy(logWaitStrategy)
                .withStrategy(new HostPortWaitStrategy())
                .withStartupTimeout(Duration.of(15, SECONDS));

        this.withRecordingFileFactory(new DefaultRecordingFileFactory());
    }

    /**
     * Constructor taking a specific webdriver container name and tag
     * @param dockerImageName Name of the docker image to pull
     */
    public LazyStartingVideoWebDriverContainer(String dockerImageName) {
        this();
        super.setDockerImageName(dockerImageName);
        this.customImageNameIsSet = true;
        // We have to force SKIP mode for the recording by default because we don't know if the image has VNC or not
        recordingMode = BrowserWebDriverContainer.VncRecordingMode.SKIP;
    }


    public SELF withCapabilities(Capabilities capabilities) {
        this.capabilities = capabilities;
        return self();
    }

    /**
     * @deprecated Use withCapabilities(Capabilities capabilities) instead:
     * withCapabilities(new FirefoxOptions())
     *
     * @param capabilities DesiredCapabilities
     * @return SELF
     * */
    @Deprecated
    public SELF withDesiredCapabilities(DesiredCapabilities capabilities) {
        this.capabilities = capabilities;
        return self();
    }

    @NotNull
    @Override
    protected Set<Integer> getLivenessCheckPorts() {
        Integer seleniumPort = getMappedPort(SELENIUM_PORT);
        if (recordingMode == BrowserWebDriverContainer.VncRecordingMode.SKIP) {
            return ImmutableSet.of(seleniumPort);
        } else {
            return ImmutableSet.of(seleniumPort, getMappedPort(VNC_PORT));
        }
    }

    @Override
    protected void configure() {

        String seleniumVersion = SeleniumUtils.determineClasspathSeleniumVersion();

        if (capabilities == null) {
            if (seleniumVersion.startsWith("2.")) {
                logger().info("No capabilities provided, falling back to DesiredCapabilities.chrome()");
                capabilities = DesiredCapabilities.chrome();
            } else {
                logger().info("No capabilities provided, falling back to ChromeOptions");
                capabilities = new ChromeOptions();
            }
        }

        if (recordingMode != BrowserWebDriverContainer.VncRecordingMode.SKIP) {
            if (getNetwork() == null) {
                withNetwork(Network.SHARED);
            }

            vncRecordingContainer = new VncRecordingContainer(this)
                    .withVncPassword(DEFAULT_PASSWORD)
                    .withVncPort(VNC_PORT);
        }

        if (!customImageNameIsSet) {
            super.setDockerImageName(getImageForCapabilities(capabilities, seleniumVersion));
        }

        String timeZone = System.getProperty("user.timezone");

        if (timeZone == null || timeZone.isEmpty()) {
            timeZone = "Etc/UTC";
        }

        addExposedPorts(SELENIUM_PORT, VNC_PORT);
        addEnv("TZ", timeZone);

        if (!getEnvMap().containsKey(NO_PROXY_KEY)) {
            addEnv(NO_PROXY_KEY, "localhost");
        }

        setCommand("/opt/bin/entry_point.sh");

        /*
         * Some unreliability of the selenium browser containers has been observed, so allow multiple attempts to start.
         */
        setStartupAttempts(3);
    }

    public static String getImageForCapabilities(Capabilities capabilities, String seleniumVersion) {

        String browserName = capabilities.getBrowserName();
        switch (browserName) {
            case BrowserType.CHROME:
                return String.format(CHROME_IMAGE, seleniumVersion);
            case BrowserType.FIREFOX:
                return String.format(FIREFOX_IMAGE, seleniumVersion);
            default:
                throw new UnsupportedOperationException("Browser name must be 'chrome' or 'firefox'; provided '" + browserName + "' is not supported");
        }
    }

    public URL getSeleniumAddress() {
        try {
            return new URL("http", getContainerIpAddress(), getMappedPort(SELENIUM_PORT), "/wd/hub");
        } catch (MalformedURLException e) {
            throw new UnexpectedException("URL malformed exception for Selenium Port", e);
        }
    }

    @Override
    public String getVncAddress() {
        return "vnc://vnc:secret@" + getContainerIpAddress() + ":" + getMappedPort(VNC_PORT);
    }

    @Override
    public String getPassword() {
        return DEFAULT_PASSWORD;
    }

    @Override
    public int getPort() {
        return VNC_PORT;
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        driver = Unreliables.retryUntilSuccess( Config.getAppStartTimeout(), TimeUnit.SECONDS,
                Timeouts.getWithTimeout(10, TimeUnit.SECONDS,
                        () ->
                                () -> new RemoteWebDriver(getSeleniumAddress(), capabilities)));

        /*
        Removed in this version.
        if (vncRecordingContainer != null) {
            LOGGER.debug("Starting VNC recording");
            vncRecordingContainer.start();
        }

         */
    }

    public void startRecording() {
        // Added so that recording can be started at any point.
        if (vncRecordingContainer != null) {
            LOGGER.debug("Starting VNC recording");
            vncRecordingContainer.start();
            recordingAvailable = true;
        }
    }

    /**
     * Obtain a RemoteWebDriver instance that is bound to an instance of the browser running inside a new container.
     * <p>
     * All containers and drivers will be automatically shut down after the test method finishes (if used as a @Rule) or the test
     * class (if used as a @ClassRule)
     *
     * @return a new Remote Web Driver instance
     */
    public RemoteWebDriver getWebDriver() {
        return driver;
    }

    @Override
    public void afterTest(TestDescription description, Optional<Throwable> throwable) {
        retainRecordingIfNeeded(description.getFilesystemFriendlyName(), !throwable.isPresent());
    }

    @Override
    public void stop() {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                LOGGER.debug("Failed to quit the driver", e);
            }
        }

        if (vncRecordingContainer != null) {
            try {
                vncRecordingContainer.stop();
            } catch (Exception e) {
                LOGGER.debug("Failed to stop vncRecordingContainer", e);
            }
        }

        super.stop();
    }

    private void retainRecordingIfNeeded(String prefix, boolean succeeded) {
        final boolean shouldRecord;
        switch (recordingMode) {
            case RECORD_ALL:
                shouldRecord = true;
                break;
            case RECORD_FAILING:
                shouldRecord = !succeeded;
                break;
            default:
                shouldRecord = false;
                break;
        }

        if (shouldRecord && recordingAvailable) {
            File recordingFile = recordingFileFactory.recordingFileForTest(vncRecordingDirectory, prefix, succeeded);
            LOGGER.info("Screen recordings for test {} will be stored at: {}", prefix, recordingFile);

            vncRecordingContainer.saveRecordingToFile(recordingFile);
        }
    }

    /**
     * Remember any other containers this needs to link to. We have to pass these down to the container so that
     * the other containers will be initialized before linking occurs.
     *
     * @param otherContainer the container rule to link to
     * @param alias          the alias (hostname) that this other container should be referred to by
     * @return this
     *
     * @deprecated Links are deprecated (see <a href="https://github.com/testcontainers/testcontainers-java/issues/465">#465</a>). Please use {@link Network} features instead.
     */
    @Deprecated
    public SELF withLinkToContainer(LinkableContainer otherContainer, String alias) {
        addLink(otherContainer, alias);
        return self();
    }

    public SELF withRecordingMode(BrowserWebDriverContainer.VncRecordingMode recordingMode, File vncRecordingDirectory) {
        this.recordingMode = recordingMode;
        this.vncRecordingDirectory = vncRecordingDirectory;
        return self();
    }

    public SELF withRecordingFileFactory(RecordingFileFactory recordingFileFactory) {
        this.recordingFileFactory = recordingFileFactory;
        return self();
    }
}