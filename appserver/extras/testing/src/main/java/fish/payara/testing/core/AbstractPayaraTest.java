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

import fish.payara.testing.core.config.Config;
import fish.payara.testing.core.container.LazyStartingVideoWebDriverContainer;
import fish.payara.testing.core.exception.UnexpectedException;
import fish.payara.testing.core.jupiter.TestcontainersController;
import org.assertj.core.api.Assertions;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parent class for all the Payara Container tests. These tests also needs to be annotated with @PayaraContainerTest.
 */
public class AbstractPayaraTest {

    @Container
    public static PayaraContainer payara;

    @Container
    public static LazyStartingVideoWebDriverContainer chrome =
            new LazyStartingVideoWebDriverContainer().withCapabilities(new ChromeOptions())
                    .withRecordingMode(BrowserWebDriverContainer.VncRecordingMode.RECORD_FAILING, new File("./target/"));

    // An instance gets injected into this in the BeforeAll.
    private static TestcontainersController controller;  // FIXME Used?

    protected RemoteWebDriver driver;

    private boolean videoStarted;  // So that we do no start video more than once for a test.

    private Client client;

    @BeforeEach
    public void setup() {
        driver = chrome.getWebDriver();
        videoStarted = false;
    }

    // Start of Selenium related methods

    protected void openWebConsolePage() {
        startVideoIfNeeded();
        // getContainerIP and getWebConsolePort since this run within Docker
        driver.get("http://" + payara.getContainerIP() + ":" + payara.getWebConsolePort());
    }

    private void startVideoIfNeeded() {
        if (!videoStarted) {
            controller.startRecording();
            videoStarted = true;
        }
    }

    protected void openPage(AbstractContainer<?> container, String page) {
        startVideoIfNeeded();
        // getContainerIP and getWebConsolePort since this run within Docker
        driver.get("http://" + container.getContainerIP() + ":" + container.getApplicationPort() + page);
    }

    protected void openPage(GenericContainer<?> container, int port, String page) {
        startVideoIfNeeded();
        // We use the actual port since it is 'internal'
        driver.get("http://" + DockerUtils.getDockerContainerIP(container.getDockerClient(), container.getContainerId()) + ":" + port + page);
    }

    protected String getPageContent() {
        return driver.getPageSource();
    }

    protected void assertPageTitle(String title) {
        assertThat(driver.getTitle()).contains(title);
    }

    protected void assertPageContains(String content) {
        String pageSource = driver.getPageSource();
        assertThat(pageSource).contains(content);
    }

    /**
     * Waits until page is fully loaded (all javascript executed)
     */
    protected void waitForLoad() {
        new WebDriverWait(driver, 2).until((ExpectedCondition<Boolean>) wd ->
                ((JavascriptExecutor) wd).executeScript("return document.readyState").equals("complete"));
    }

    /**
     * Waits until the page contains a certain element.
     *
     * @param id
     */
    protected void waitUntilPresent(String id) {
        WebDriverWait wait = new WebDriverWait(driver, Config.getElementWaitTimeout());
        wait.until(ExpectedConditions.elementToBeClickable(By.id(id)));
    }

    /**
     * Waits until the AJAX queue is fully processed.
     *
     * @param timeoutInSeconds
     */
    protected void waitForAjax(Long timeoutInSeconds) {
        try {
            Thread.sleep((long) (timeoutInSeconds * 1000 / 10 * Config.getFactor()));
        } catch (InterruptedException e) {
            throw new UnexpectedException("InterruptedException during the wait until the AJAX logic is performed", e);
        }
        Function<WebDriver, Boolean> condition = wd -> (Boolean) ((JavascriptExecutor) driver).executeScript("return jQuery.active==0");
        waitFor(condition, timeoutInSeconds);
    }

    protected void waitFor(Function<WebDriver, Boolean> waitCondition, Long timeoutInSeconds) {
        WebDriverWait webDriverWait = new WebDriverWait(driver, timeoutInSeconds);
        webDriverWait.withTimeout(timeoutInSeconds, TimeUnit.SECONDS);
        try {
            webDriverWait.until(waitCondition);
        } catch (Exception e) {
            Assertions.fail(e.getMessage());
        }
    }

    protected WebElement getElementById(String id) {
        return driver.findElement(By.id(id));
    }

    protected String getHtmlSourceOf(String id) {
        WebElement element = driver.findElement(By.id(id));
        return (String) ((JavascriptExecutor) driver).executeScript("return arguments[0].outerHTML;", element);
    }

    protected void sendKeys(WebElement element, String text) {
        new Actions(driver).moveToElement(element).perform();  // Focus the field.
        element.sendKeys(text); // Simulate typing
    }

    protected void click(WebElement element) {
        element.click();
        waitForLoad();  // FIXME Or should this be #waitForAjax()
    }

    // Start of JAX--RS Rest client related methods.
    private void defineClient() {
        if (client == null) {
            client = ClientBuilder.newClient();
            client.register(JacksonFeature.class);
        }
    }

    protected WebTarget getClientWebTarget(AbstractContainer<?> container) {
        defineClient();
        // getContainerIpAddress and getMappedApplicationPort since we run this locally
        return client.target("http://" + container.getContainerIpAddress() + ":" + container.getMappedApplicationPort());
    }

    protected WebTarget getClientWebTargetApplication(AbstractContainer<?> container) {
        defineClient();
        // getContainerIpAddress and getMappedApplicationPort since we run this locally
        return client.target("http://" + container.getContainerIpAddress() + ":" + container.getMappedApplicationPort() + "/test");
    }
}
