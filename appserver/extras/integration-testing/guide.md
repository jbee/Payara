# Setup

The project requires a Docker client installation on the machine which is running the integration tests.  The client needs to point to an accessible Docker Host/cluster, if not the test will fail.

When the client can't access the Docker Host (like Docker for Desktop is not started) but it finds a docker-machine configuration, it will use Docker machine (running within VirtualBox) to start up a Docker Host instance.

The tests created with the `Integration Testing Framework` project work on both a docker machine environment as when the docker client points to a host (so it knows where the Docker containers are running and uses the correct IP, localhost or 192.x.x.x, automatically)

NOTE: When the Integration Testing Framework project starts up a Docker machine instance, it leaves it running. So if you don't need it anymore, don't forget to shut it down (with `docker-machine stop`).

# Configuration maven project

A reproducer is a maven project which requires the following artifact

    <dependency>
        <groupId>fish.payara.testing</groupId>
        <artifactId>core-integration-test</artifactId>
        <version>xxxx</version>  // Determines also if Enterprise or Community Docker images are used
        <scope>test</scope>
    </dependency>

With the option `-Dpayara.version` you can force that a certain version is used.

Additional dependencies can be required depending on the type of the test.

The project should be of packaging type WAR (unless no test application is required, see [No test application](#no_test_application)).

# Use the correct @Test

WARNING: Important

TestContainers has some hard dependencies on JUnit 4, so the JUnit 4 API is also available. This is an issue when the `@Test` from JUnit 4 is used (instead of the one of JUnit 5) as this will lead to the incorrect startup of the containers.

# Maven project Structure

If a test application is required for the test, not all of them need it, see [No test application](#no_test_application), it is created in the `src/main/java` and `src/main/webapp` directories as any other application.

The test(s) is/are created in the src/main/test directory, just as any other unit or integration test.

# Prerequisites

- Dependencies on JUnit 5, Testcontainers, Selenium (when using a browser to test the app), DBUnit, and MySql when using Database and Jersey Rest Client when calling Rest endpoints.
- Docker client installed on the test machine
- JDK 8 to build core project and reproducer projects
- JDK 8 or 11 to run reproducer projects.

# Functionality

## Type of runtime

The `Integration Testing Framework` project can run the tests against a default Payara Server or Payara Micro installation, or you can provide a custom Docker image that needs to be used.
It is foreseen that tests can be run by the developer or the CI environment on the current master of the repo. (Requires additional maven commands to build payara.zip and Docker Images from current master)

The official Payara Docker Images are used, unless a custom image is defined, to run the test.

The default Payara Micro image for the reproducer starts the runtime with the `--noCluster` option. For testing DataGrid features in Payara Micro, see also [Payara Micro specifics](#payara_micro_specifics).

When using Payara Micro without a test application, the successful startup of Payara Micro is not correctly determined at the moment (BUG : See fish.payara.testing.core.PayaraContainer#configure)

## Define runtime for reproducer

### Single runtime

When your reproducer only needs to run on 1 runtime (Server, Micro, or your custom image), use the following structure for test class and test method.

    @PayaraContainerTest("server")
    public class DeleteOptionTest extends AbstractPayaraTest {
        @Test
        public void testMain() {

The allowed values for the _value_ member of `@PayaraContainerTest` are `server`, `micro`, and `custom` (case insensitive).  When the value is not recognized, the value `custom` is used.

When the value `server` is specified, by default it will use the Full profile version (_server-full_). However, with the system property `-Dpayara.test.container.variant` one can define which variant is used.

- full -> server-full (Full Profile)
- web -> server-web (Web Profile)
- ml -> server-ml (ML version, does not work et as there are no Docker Images created)

### Multiple runtimes

A certain test method can also be run on multiple runtimes (sequentially). Use the following structure for that case

    @PayaraContainerTest
    public class BasicJAXRSTest extends AbstractPayaraTest {

        @ParameterizedTest
        @ValueSource(strings = {"server", "micro"})
        public void testMain(String runtime)  {

In this case, we do not specify a value with `@PayaraContainerTest` but define the runtimes using the `@ValueSource` and `@ParameterizedTest` of JUnit 5.

### Payara Micro specifics

By default, the `--noCluster` is added to the startup commands. In case you are testing multiple Payara Micro instances, you need to define this;

    @PayaraContainerTest(value = "micro", microCluster = true)

See also the `PayaraMicroContainer` code.

### Define version

You can define the version of the Docker image which will be used in several ways, in order of precedence

- Version defined in the `@PayaraContainerTest` annotation, for example `value="server-5.193"`. (Not recommended for CI tests as this will fix the Payara Version)
- The version is defined by the System property `payara.version`.
- Hardcoded default version in `fish.payara.testing.core.config.Config.Defaults` which is initialised from the Maven Project Version.

The recommended way is to not specify the Payara version in the `@PayaraContainerTest` annotation. That way the default (latest released version) is taken and one can use the System property to define another version.

For local testing, you can of course define the desired version at the annotation level.

The `-SNAPSHOT` and `-RC` in a version has specific meaning where the `-` is not used as separator but part of the value.

So `server-5.202-SNAPSHOT-jdk11` is parsed as Runtime type `server`, version `5.202-SNAPSHOT`, and JDK `jdk11`.

The version is ignored when using a `custom` image. See [Custom container](#custom_container)

NOTE: you always need to use the value `server` to indicate a Payara Server instance. With the system property `-Dpayara.test.container.variant` one can define which _variant_.

The allowed values, case insensitive, for the system property.
 
- full -> server-full (Full Profile). The default if no value is specified.
- web -> server-web (Web Profile)
- ml -> server-ml (ML version, does not work et as there are no Docker Images created)

### JDK Version of Image

Since Payara 5 supports JDK 8 and JDK 11 now, it is important that both those versions can be used for testing / creating reproducers.

The JDK version is determined in the following order of precedence.

- Version defined in the `@PayaraContainerTest` annotation, for example `value="server-5.193-jdk11"` or `value="server--jdk11"`. (Not recommended)
- The version is defined by the System property `payara.test.container.jdk`.
- Hardcoded default version (JDK 8) in `fish.payara.testing.core.config.Config.Defaults`.

The recognized values are `jdk8` and `jdk11`.  Anything else will be ignored and the value from the System property or the hardcoded value in `Config.Defaults` will be taken.

The version is ignored when using a `custom` image. See  [Custom container](custom_container)

## Grouping for CI

It is recommended to use test suites or `@Tag` to group the different tests which need to run on the same runtime, Payara Server or Payara Micro. This also allows us to run tests either on the Full profile or the Web profile.

1. A Test suite or `@Tag` with all tests having `@PayaraContainerTest("server")` and `@PayaraContainerTest("custom")`  
2. A Test suite or `@Tag` with all tests having `@PayaraContainerTest("micro")`

When you run the first group, it will run all tests for the Payara Server (assuming all custom based images are also Payara Server) and by default, they run with the full Profile. By setting the option `-Dpayara.test.container.variant=web` and you run it again, all the tests are performed with the Web profile version of the Payara Server.
  
## Types of Containers

The `Integration Testing Framework` project has 3 different types of Containers

- The container running the application under test with Payara Server or Payara Micro.
- A container with Java installed which can be used as a remote SSH node within a domain.
- 3rd party containers (with a database for example) or any other custom container.

The logic to determine which image is running the test application is described mainly in the section [Define runtime for reproducer](#define-runtime-for-reproducer) and also in the section on the custom image (TODO Link ???)

### Plain Java Container

There is a specific image available that can be used for the remote SSH node for a domain.

A container can be started for a Payara instance with SSH ready to be used as a remote SSH node. The following construct can be used

    @PayaraContainerTest("server")
    public class SSHNodesIT extends AbstractPayaraTest {

        @Container
        public static PlainJavaContainer node1Container;

        @Test
        public void testMain() {

            payara.addASAdminSSHPassword(PlainJavaContainer.SSH_PASSWORD);
            ...
        }
    }

When the `Payara Integration Test` JUnit 5 extension encounters a `PlainJavaContainer` field annotated with `@Container`, it starts a container based on file `plainJava8.docker` or `plainJava11.docker`. These images are rather small and startup the SSH server in combination with Java. The JDK version will be matched automatically with the one which is specified (or derived) from the version of the 'main' container (the container running the test application)

### Payara Micro Container

This is a specific container for using Payara Micro in cluster mode.

    @PayaraContainerTest(value = "micro", microCluster = true)
    public class BasicMicroClusterTest extends AbstractPayaraTest {

        @Container
        public static PayaraMicroContainer micro2;

The `PayaraMicroContainer` creates another container identical to the one assigned to the `payara` variable. Due to the `microCluster = true` option, they form a cluster.

The above example creates a container based on the same version (like 5.201 and JDK 8 version) as defined in `@PayaraContainerTest` which has the same application deployed (a test application is always expected).


### 3th party containers

You can use the predefined containers defined by the Testcontainers project itself, for example, the MySQL container used in the JPA template.  But you can also use some other software in containers if you need them. The steps to have your custom-defined are.

     @Container
     public static DockerImageContainer jaeger;

Define the additional container you need in your test class. The name of the variable is important here as it will search for `src/docker/<varname>/DockerFile`.  The entire contents of that directory and subdirectories will be passed on the Docker Daemon when the image is built.

You should never have a variable called `custom` as this might conflict with the custom containers described in the next section.

## Custom container

This is different from the custom 3rd party container. This is a custom version of the 'main' container running your test application (if any).

This type of container needs to be specified on the test annotation on the class.

      @PayaraContainerTest("custom")

In this scenario, it looks for a file called `src/docker/custom/payara.docker` and this file will be used as Docker build file. Also, the entire directory and subdirectories will be passed on to the Docker Daemon for building the image.

In order to now always have to update this custom docker build file when a new official Payara image is released, the _variant_ and version is updated automatically.

Suppose you have the following contents in `src/docker/custom/payara.docker`

      FROM payara/server-full:5.194
      ADD jaeger-tracer-lib-1.0-jar-with-dependencies.jar /opt/payara/glassfish/domains/production/lib

When the Docker image is built, the code will replace the `:5.194` value to the one defined in the JVM properties `payara.test.container.version` and `payara.test.container.jdk` or the defaults defined in `Config.Defaults`. That way, even the custom-defined Build files will always use the 'correct' versions.
It also will replace the `server-full` part with `server-web` for example, when the system property `-Dpayara.test.container.variant` has the value _web_.

So to avoid confusion, and prevent CI testing an old version, it is best to define the tag value as

      FROM payara/server-full:xxxx

If you do not want that this replacement happens, use a base image which is not a Payara official one (so it doesn't start with `payara/`).  However, for tests running on the CI, the version should always be the latest version of the master branch and thus you should not commit any custom image which is not based on the Payara image.

Also, there is no need to add a command to include the test application to the image. The code will add the following command automatically (and makes sure that your application is added to the correct directory with a name of `test.war`)

     ADD test.war /opt/payara/deployments

## Enterprise Images support

When the specified version is a released enterprise version, the correct repository is added to the from clause.  The following rules are applied.

- Version should not contain `-RC` or `-SNAPSHOT`.
- minor version should indicate an Enterprise number (bewteen 20 and 99)

When a version is determined to be an Enterprise version which has a Docker Image on the Payara Nexus server, the appropriate repo is added where needed (`nexus.payara.fish:5000/`). This is the case when the _standard_ image will be used or when the custom Docker file (`src/docker/custom/payara.docker`) is used (FROM is rewritten, just as what happens with the version.) 
 
## Submit asadmin commands

The main container (Java class PayaraContainer) has support for submitting _asadmin_ commands to the DAS and reading the outcome.

The field `payara`, defined in the `AbstractPayaraTest` superclass (which will be injected properly when the container starts), has a method to send asadmin commands.

Please note that this will only be working when based on the `server` image or a custom image that is running the Payara Server. It also assumes the paths of the official Docker image. It assumes for instance that the <payara-home> is in the directory `/opt/payara/appserver`.

If the command returns a return code different from 0, a JUnit fail is thrown with as message the _out_ and _err_ streams.

The result of the asadmin command can be retrieved as the return value of the method. A Java 8+ `Function` can convert the command output (String, lines are concatenated with new line markers ) to a list of a specific Java Type. There is a 'default' implemented which converts it just a List of String (you can use `StringListOutput.INSTANCE`). But any Function can be used to convert the output in something more Java alike.

The code adds automatically the options `--terse` and the `--user` and `--passwordFile` options since secure admin is assumed (and the path to password file is assumed to be as in the official Payara Docker image)

## No test application

Most reproducers will need a test application to demonstrate the problem. But sometimes the issue can just be reproduced by using some asadmin commands.  For that case, there is the possibility to launch a container without the need for a WAR file. The things you need to do are

- Indicate that no test application is required by setting `testApplication = false` within `@PayaraContainerTest`
- Maven packaging type can be `JAR` instead of `WAR`.
- the `src/main/java` can be empty

## Test endpoints

Application functionality can be tested in several always

- Testing the actual endpoints like servlets, REST endpoints, platform endpoints like /openapi
- Use application in a browser for the JSF frontend
- Wrap non-UI functionality within a servlet call (like reading from Database)

The `Integration Testing Framework` project has 2 ways to support the testing.

### Endpoints

Testing the endpoints can be done using a JAX-RS client which can be retrieved from the Abstract test class which is already configured.

`AbstractPayaraTest#getClientWebTarget(payara)`  returns a `WebTarget` already pointing to the correct host (http://<host:port>/).  This can be used to call the /openapi endpoint for example.
`AbstractPayaraTest#getClientWebTargetApplication(payara)` returns a `WebTarget` pointing already to the application (http://<host:port>/test).  This can be used to call a servlet or JAX-RS resource which is deployed within the application.

The parameter of the methods points to the Docker container which will be the target for the JAX-RS client. In most cases, this will the container running the application which can be specified using the `payara` variable.  The other option is to use a `PlainJavaContainer` instance as a parameter.

In case you call an endpoint of any 3rd party container, make sure you use the `ContainerState#getContainerIpAddress()` and `ContainerState#getMappedPort()` as this test runs locally.

### Selenium test

There is already some rudimentary support for Selenium tests in the project and this will be extended in a future version.  These tests run within a specific container started for this purpose.  This allows, for example, for the generation of a video with the application interactions to better understand why a test failed.

Remember that the browser runs in a separate container, next to the other containers, and thus you need to use the internal IP addresses and actual port (unmapped) to access the test application from the browser. 

Before any specific Selenium related method is used, a call to `openPage()` (or `openWebConsolePage()`) is required.

Parameters of the `openPage()` method

- container: define the container running the app (domain or instance)
- port: (optionally) defines an alternative port to access the app. This needs to be the actual port.
- page: The 'context path' part of the URL. Remember to include the root of the application. So for example `/test/index.html`

The `openWebConsolePage()` doesn't need any parameters and just opens the web administration console in the browser.

Once the first page is on the browser, you can start interacting with it.

- getPageContent(): Get the HTML source of the current page in the browser
- assertPageTitle(String title): Assert the title of the page
- assertPageContains(String content): Assert a certain character sequence is in the current page.
- waitForLoad(): Wait until page is completely loaded (and initial javascript is executed (technically waits until document.readyState = 'complete'))
- waitUntilPresent(String id): Wait until a certain id is present on the screen.
- waitForAjax(Long timeoutInSeconds): Wait until all AJAX calls are completed (with a maximum time to wait)
- waitFor(Function<WebDriver, Boolean> waitCondition, Long timeoutInSeconds): A more generic wait method
- getElementById(String id): Returns a reference to the HTML element defined by the id. Throws NoSuchElementException when not found.
- getHtmlSourceOf(String id): Return the HTML source for the element.
- sendKeys(WebElement element, String text): Send the characters to the element, simulating typing in the field for example. This also moves the focus.
- click(WebElement element): Click on the element to simulate a mouse click on it.

## Video recording of failed TestContainers run

As already mentioned, when using the Selenium test support within the `Integration Testing Framework` project, a video will be available when the test fails so that it is easier to follow and diagnose the problem.  The recording starts when you call the method `openPage()` (or `openWebConsolePage()`) for the first time.

Only when the test fails, a video will be created within the `target` directory of the project.

## Retrieving a file

The `AbstractContainer` (so available within `PayaraContainer`, `PlainJavaContainer`, and `PayaraMicroContainer`) has a method `getContainerFileContent(String)` which returns the contents of a file defined within the container indicated by the parameter (full path expected).
The method returns a byte array so that also binary data can be retrieved from the container.

## Logging

By default only a minimal amount of logging is available. During coding, it might be useful to see the output of the Payara Container. You can do this in 2 ways

- Define the member `verboseLogging` of the `@PayaraContainerTest`.
- Define the system property `payara.test.container.logging.verbose`.

Setting the system property has precedence over the annotation member. This way you can make sure that on your local machine the logging is always shown, but on the CI environment, you save space by having only a minimum amount of logging.

The logging when the Docker Image is building is the Docker image is not shown (= is a not implemented feature yet)

## Timeouts

In various parts of the code, there are timeouts defined to wait until something has finished. Examples address

- Waiting until the Docker container is started.
- Timeouts are used by Selenium until an element appears on the screen or AJAX is performed.

As the performance is impacted on the system on which the tests are run, those timeouts might be insufficient.  Therefore, an environment variable `payara_test_timeout_factor` is read which defines a factor for increasing the timeout.

When the variable contains 2.0, all timeouts are doubled. This is also the case for the value defined by the developer in the AbstractPayaraTest#waitForAjax method.

When the value is 0 or negative, a warning is shown and 1.0 is taken.

This factor is defined as an environment variable so that it can be defined in your CI config and taken automatically.

## Database example

It is possible to use a Database in your tests. A template Test method is available on request for this (or there will be some basic test available in the near future in the repo)

# Some known issues

- The error handling around invoking the asadmin command method against a container running Payara Micro is not handled correctly (also due to the bug that Container readiness is not correctly determined when using Micro without test application)

- TestContainers has some hard dependencies on JUnit 4, so the JUnit 4 API is also available. This is an issue when the @Test from JUnit 4 is used (instead of the one of JUnit 5) as this will lead to the incorrect startup of the containers.

- The logging when the Image is building the image is not yet shown. They should be done by LoggableImageFromDockerFile. See also the comments in that class.

# Future enhancements 

- Support for the ML version.
- Transfer of the `server.log` when the test fails.
- Support for EAR test applications.
- Improve support for Database tests
 