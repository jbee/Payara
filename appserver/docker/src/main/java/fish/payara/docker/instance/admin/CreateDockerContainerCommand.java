package fish.payara.docker.instance.admin;

import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.Node;
import com.sun.enterprise.config.serverbeans.Nodes;
import com.sun.enterprise.config.serverbeans.Server;
import com.sun.enterprise.config.serverbeans.Servers;
import com.sun.enterprise.config.serverbeans.SystemProperty;
import fish.payara.docker.DockerConstants;
import org.glassfish.api.ActionReport;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandRunner;
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.ParameterMap;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.glassfish.api.ActionReport.ExitCode.SUCCESS;

@Service(name = "_create-docker-container")
@PerLookup
@ExecuteOn({RuntimeType.DAS})
@RestEndpoints({
        @RestEndpoint(configBean = Domain.class,
                opType = RestEndpoint.OpType.POST,
                path = "_create-docker-container",
                description = "Create a Docker Container for the defined Instance on the specified nodeName")
})
public class CreateDockerContainerCommand implements AdminCommand {

    private static final Logger logger = Logger.getLogger(CreateDockerContainerCommand.class.getName());

    @Param(name = "nodeName", alias = "node")
    String nodeName;

    @Param(name = "instanceName", alias = "instance", primary = true)
    String instanceName;

    @Inject
    private Servers servers;

    @Inject
    private Nodes nodes;

    @Inject
    private CommandRunner commandRunner;

    private Properties containerConfig;
    private List<String> processedProperties;

    @Override
    public void execute(AdminCommandContext adminCommandContext) {
        ActionReport actionReport = adminCommandContext.getActionReport();

        // Get the Node Object and validate
        Node node = nodes.getNode(nodeName);
        if (node == null) {
            actionReport.failure(logger, "No nodeName found with given name: " + nodeName);
            return;
        }

        if (!node.getType().equals("DOCKER")) {
            actionReport.failure(logger, "Node is not of type DOCKER, nodeName is of type: " + node.getType());
            return;
        }

        // Get the DAS hostname and port and validate
        String dasHost = "";
        String dasPort = "";
        for (Server server : servers.getServer()) {
            if (server.isDas()) {
                dasHost = server.getAdminHost();
                dasPort = Integer.toString(server.getAdminPort());
                break;
            }
        }

        if (dasHost == null || dasHost.equals("") || dasPort.equals("")) {
            actionReport.failure(logger, "Could not retrieve DAS host address or port");
            return;
        }

        // Get the instance that we've got registered in the domain.xml to grab its config
        Server server = servers.getServer(instanceName);
        if (server == null) {
            actionReport.failure(logger, "No instance registered in domain with name: " + instanceName);
            return;
        }

        containerConfig = new Properties();

        // Add all instance-level system properties, stripping the "Docker." prefix
        for (SystemProperty systemProperty : server.getSystemProperty()) {
            if (systemProperty.getName().startsWith("Docker.")) {
                containerConfig.put(systemProperty.getName().substring(systemProperty.getName().indexOf(".") + 1),
                        systemProperty.getValue());
            }
        }

        // Add Docker system properties from config, making sure not to override any instance-level properties
        for (SystemProperty systemProperty : server.getConfig().getSystemProperty()) {
            if (systemProperty.getName().startsWith("Docker.")) {
                containerConfig.putIfAbsent(
                        systemProperty.getName().substring(systemProperty.getName().indexOf(".") + 1),
                        systemProperty.getValue());
            }
        }

        // Create the JSON Object to send
        JsonObject jsonObject = constructJsonRequest(node, dasHost, dasPort);

        // Create web target with query
        Client client = ClientBuilder.newClient();
        WebTarget webTarget = null;
        if (Boolean.valueOf(node.getUseTls())) {
            webTarget = client.target("https://"
                    + node.getNodeHost()
                    + ":"
                    + node.getDockerPort()
                    + "/containers/create");
        } else {
            webTarget = client.target("http://"
                    + node.getNodeHost()
                    + ":"
                    + node.getDockerPort()
                    + "/containers/create");
        }
        webTarget = webTarget.queryParam(DockerConstants.DOCKER_NAME_KEY, instanceName);

        // Send the POST request
        Response response = null;
        try {
            response = webTarget.queryParam(DockerConstants.DOCKER_NAME_KEY, instanceName)
                    .request(MediaType.APPLICATION_JSON).post(Entity.entity(jsonObject, MediaType.APPLICATION_JSON));
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Encountered an exception sending request to Docker: \n", ex);
        }

        // Check status of response and act on result
        if (response != null) {
            Response.StatusType responseStatus = response.getStatusInfo();
            if (!responseStatus.getFamily().equals(Response.Status.Family.SUCCESSFUL)) {
                // Log the failure
                actionReport.failure(logger, "Failed to create Docker Container: \n"
                        + responseStatus.getReasonPhrase());

                // Attempt to unregister the instance so we don't have an instance entry that can't be used
                unregisterInstance(adminCommandContext, actionReport);
            }
        } else {
            // If the response is null, clearly something has gone wrong, so treat is as a failure
            actionReport.failure(logger, "Failed to create Docker Container");

            // Attempt to unregister the instance so we don't have an instance entry that can't be used
            unregisterInstance(adminCommandContext, actionReport);
        }
    }

    /**
     * Builds the Json Object from all supplied configuration to send to Docker.
     *
     * @param node The Payara Server node
     * @param dasHost The IP address of the DAS
     * @param dasPort The admin port of the DAS
     * @return Json Object representing all supplied and default Docker container configuration.
     */
    private JsonObject constructJsonRequest(Node node, String dasHost, String dasPort) {
        JsonObjectBuilder rootObjectBuilder = Json.createObjectBuilder();

        // Add the image straight away - this is never overridden
        rootObjectBuilder.add(DockerConstants.DOCKER_IMAGE_KEY, node.getDockerImage());

        // If no user properties specified, go with defaults, otherwise go over the system properties and add them to
        // the Json object
        if (containerConfig.isEmpty()) {
            rootObjectBuilder.add(DockerConstants.DOCKER_HOST_CONFIG_KEY, Json.createObjectBuilder()
                    .add(DockerConstants.DOCKER_MOUNTS_KEY, Json.createArrayBuilder()
                            .add(Json.createObjectBuilder()
                                    .add(DockerConstants.DOCKER_MOUNTS_TYPE_KEY, "bind")
                                    .add(DockerConstants.DOCKER_MOUNTS_SOURCE_KEY, node.getDockerPasswordFile())
                                    .add(DockerConstants.DOCKER_MOUNTS_TARGET_KEY, DockerConstants.PAYARA_PASSWORD_FILE)
                                    .add(DockerConstants.DOCKER_MOUNTS_READONLY_KEY, true)))
                    .add(DockerConstants.DOCKER_NETWORK_MODE_KEY, "host"));
            rootObjectBuilder.add(DockerConstants.DOCKER_CONTAINER_ENV, Json.createArrayBuilder()
                    .add(DockerConstants.PAYARA_DAS_HOST + "=" + dasHost)
                    .add(DockerConstants.PAYARA_DAS_PORT + "=" + dasPort)
                    .add(DockerConstants.PAYARA_NODE_NAME + "=" + nodeName)
                    .add(DockerConstants.INSTANCE_NAME + "=" + instanceName));
        } else {
            translatePropertyValuesToJson(rootObjectBuilder, dasHost, dasPort);
        }

        return rootObjectBuilder.build();
    }

    /**
     * Go over all system properties and add them to the Json object.
     *
     * @param rootObjectBuilder The top-level object builder that will contain all Docker configuration
     * @param dasHost The IP address that the DAS is running on
     * @param dasPort The admin port of the DAS
     */
    private void translatePropertyValuesToJson(JsonObjectBuilder rootObjectBuilder, String dasHost,
            String dasPort) {
        processedProperties = new ArrayList<>();
        boolean hostConfigAdded = false;
        boolean envConfigAdded = false;


        for (String property : containerConfig.stringPropertyNames()) {
            // As we recurse over nested properties, we add the processed ones to this list, so check that we're
            // not going to process the same property twice
            if (processedProperties.contains(property)) {
                continue;
            }

            // If the property is in the same namespace as our defaults, handle them here
            if (property.startsWith(DockerConstants.DOCKER_HOST_CONFIG_KEY)) {
                hostConfigAdded = true;
                addHostConfigProperties(rootObjectBuilder);
                continue;
            } else if (property.startsWith(DockerConstants.DOCKER_CONTAINER_ENV)) {
                envConfigAdded = true;
                addEnvProperties(rootObjectBuilder, dasHost, dasPort);
                continue;
            }

            // Check if this is a nested property
            if (property.contains(".")) {
                // Recurse through the properties and add any other properties that fall under the same namespace
                addNestedProperties(rootObjectBuilder, property);
            } else {
                // Not a nested property, add it as a plain key:value
                String propertyValue = containerConfig.getProperty(property);
                addPropertyToJson(rootObjectBuilder, property, propertyValue);
                processedProperties.add(property);
            }
        }

        // If we haven't added any HostConfig or Env settings, add the defaults here
        if (!hostConfigAdded) {
            rootObjectBuilder.add(DockerConstants.DOCKER_HOST_CONFIG_KEY, Json.createObjectBuilder()
                    .add(DockerConstants.DOCKER_MOUNTS_KEY, Json.createArrayBuilder()
                            .add(Json.createObjectBuilder()
                                    .add(DockerConstants.DOCKER_MOUNTS_TYPE_KEY, "bind")
                                    .add(DockerConstants.DOCKER_MOUNTS_SOURCE_KEY, nodes.getNode(nodeName).getDockerPasswordFile())
                                    .add(DockerConstants.DOCKER_MOUNTS_TARGET_KEY, DockerConstants.PAYARA_PASSWORD_FILE)
                                    .add(DockerConstants.DOCKER_MOUNTS_READONLY_KEY, true)))
                    .add(DockerConstants.DOCKER_NETWORK_MODE_KEY, "host"));
        }
        if (!envConfigAdded) {
            rootObjectBuilder.add(DockerConstants.DOCKER_CONTAINER_ENV, Json.createArrayBuilder()
                    .add(DockerConstants.PAYARA_DAS_HOST + "=" + dasHost)
                    .add(DockerConstants.PAYARA_DAS_PORT + "=" + dasPort)
                    .add(DockerConstants.PAYARA_NODE_NAME + "=" + nodeName)
                    .add(DockerConstants.INSTANCE_NAME + "=" + instanceName));
        }
    }

    /**
     * Loops over nested properties in the 'HostConfig' namespace and adds them to the Json builder.
     * @param rootObjectBuilder The top-level Json builder
     */
    private void addHostConfigProperties(JsonObjectBuilder rootObjectBuilder) {
        JsonObjectBuilder hostConfigObjectBuilder = Json.createObjectBuilder();

        // Populate HostConfig defaults map so we can check if any get overridden
        Map<String, Boolean> defaultsOverridden = new HashMap<>();
        defaultsOverridden.put(DockerConstants.DOCKER_MOUNTS_KEY, false);
        defaultsOverridden.put(DockerConstants.DOCKER_NETWORK_MODE_KEY, false);

        // Loop over all properties and add to HostConfig Json builder
        loopOverNestedProperties(rootObjectBuilder, DockerConstants.DOCKER_HOST_CONFIG_KEY,
                hostConfigObjectBuilder, defaultsOverridden);

        // Add any remaining defaults
        if (!defaultsOverridden.get(DockerConstants.DOCKER_MOUNTS_KEY)) {
            hostConfigObjectBuilder.add(DockerConstants.DOCKER_MOUNTS_KEY, Json.createArrayBuilder()
                    .add(Json.createObjectBuilder()
                            .add(DockerConstants.DOCKER_MOUNTS_TYPE_KEY, "bind")
                            .add(DockerConstants.DOCKER_MOUNTS_SOURCE_KEY, nodes.getNode(nodeName).getDockerPasswordFile())
                            .add(DockerConstants.DOCKER_MOUNTS_TARGET_KEY, DockerConstants.PAYARA_PASSWORD_FILE)
                            .add(DockerConstants.DOCKER_MOUNTS_READONLY_KEY, true)));
        }
        if (!defaultsOverridden.get(DockerConstants.DOCKER_NETWORK_MODE_KEY)) {
            hostConfigObjectBuilder.add(DockerConstants.DOCKER_NETWORK_MODE_KEY, "host");
        }

        // Finally, add host config object to final Json request object
        rootObjectBuilder.add(DockerConstants.DOCKER_HOST_CONFIG_KEY, hostConfigObjectBuilder);
    }

    /**
     * Generic version of addHostConfigProperties, that loops over nested properties namespace and adds them to
     * the Json builder.
     * @param rootObjectBuilder The root-level Json builder
     * @param originalProperty The first property that we found in this namespace
     */
    private void addNestedProperties(JsonObjectBuilder rootObjectBuilder, String originalProperty) {
        JsonObjectBuilder topLevelObjectBuilder = Json.createObjectBuilder();
        String topLevelProperty = originalProperty.substring(0, originalProperty.indexOf("."));

        // Loop over nested properties and add to Json
        loopOverNestedProperties(rootObjectBuilder, topLevelProperty, topLevelObjectBuilder, null);

        // Finally, add top level object builder to root Json request object builder
        rootObjectBuilder.add(topLevelProperty, topLevelObjectBuilder);
    }

    /**
     * Adds the Env array properties to the root Json builder
     * @param rootObjectBuilder The top-level Json builder
     * @param dasHost The IP address that the DAS is situated on
     * @param dasPort The admin port of the DAS
     */
    private void addEnvProperties(JsonObjectBuilder rootObjectBuilder, String dasHost, String dasPort) {
        String envConfigString = containerConfig.getProperty(DockerConstants.DOCKER_CONTAINER_ENV)
                .replaceAll("\\[", "")
                .replaceAll("\\]", "");

        // Check if we need to add any of our defaults
        if (!envConfigString.contains(DockerConstants.PAYARA_DAS_HOST)) {
            envConfigString += "|" + DockerConstants.PAYARA_DAS_HOST + "=" + dasHost;
        }
        if (!envConfigString.contains(DockerConstants.PAYARA_DAS_PORT)) {
            envConfigString += "|" + DockerConstants.PAYARA_DAS_PORT + "=" + dasPort;
        }
        if (!envConfigString.contains(DockerConstants.PAYARA_NODE_NAME)) {
            envConfigString += "|" + DockerConstants.PAYARA_NODE_NAME + "=" + nodeName;
        }
        if (!envConfigString.contains(DockerConstants.INSTANCE_NAME)) {
            envConfigString += "|" + DockerConstants.INSTANCE_NAME + "=" + instanceName;
        }

        // We can't currently have '=' in a system property value, so for this special case substitute ':' as we
        // add to Json
        envConfigString = envConfigString.replaceAll(":", "=");

        // Finally, add to top-level Json builder
        addPropertyToJson(rootObjectBuilder, DockerConstants.DOCKER_CONTAINER_ENV, envConfigString);
    }

    /**
     * Loops over all nested properties in a given namespace and adds them to the top-level builder of said namespace
     *
     * @param rootObjectBuilder The top level object builder
     * @param topLevelObjectBuilder The object builder of the top level component of the property
     * @param defaultsOverridden The map of booleans for if a default has been overridden
     */
    private void loopOverNestedProperties(JsonObjectBuilder rootObjectBuilder, String topLevelProperty,
            JsonObjectBuilder topLevelObjectBuilder, Map<String, Boolean> defaultsOverridden) {
        // Gather all properties in the same namespace as the top level property
        List<String> nestedProperties = new ArrayList<>();
        for (String property : containerConfig.stringPropertyNames()) {
            if (property.startsWith(topLevelProperty)) {
                nestedProperties.add(property);
            }
        }

        // Sort them into alphabetical order to group them all related properties together
        nestedProperties.sort(Comparator.comparing(String::toString));

        for (String property : nestedProperties) {
            // Only process if we haven't already
            if (processedProperties.contains(property)) {
                continue;
            }

            // Check if property overrides any of our defaults
            if (defaultsOverridden != null) {
                switch (property) {
                    case DockerConstants.DOCKER_HOST_CONFIG_KEY + "." + DockerConstants.DOCKER_MOUNTS_KEY:
                        defaultsOverridden.put(DockerConstants.DOCKER_MOUNTS_KEY, true);
                        break;
                    case DockerConstants.DOCKER_HOST_CONFIG_KEY + "." + DockerConstants.DOCKER_NETWORK_MODE_KEY:
                        defaultsOverridden.put(DockerConstants.DOCKER_NETWORK_MODE_KEY, true);
                        break;
                }
            }

            // Create a Map of Json builders for each level of the property
            Map<String, JsonObjectBuilder> propertyComponentObjectBuilders = new HashMap<>();
            propertyComponentObjectBuilders.put(topLevelProperty, topLevelObjectBuilder);

            // Recurse over the namespace and add all of them to the Json builders
            recurseOverNested(rootObjectBuilder, nestedProperties, property, propertyComponentObjectBuilders,
                    null);
        }
    }

    /**
     * Recurses over all properties in a given namespace, and adds them all to their Json builders
     * @param parentObjectBuilder The Json object builder of the parent property
     * @param sortedProperties The list of sorted properties to recurse over
     * @param property The property to add to Json
     * @param propertyComponentObjectBuilders The map of Json builders still under construction
     * @param parent The parent component property
     */
    private void recurseOverNested(JsonObjectBuilder parentObjectBuilder, List<String> sortedProperties,
            String property, Map<String, JsonObjectBuilder> propertyComponentObjectBuilders, String parent) {
        List<String> propertyComponents = Arrays.asList(property.split("\\."));

        // Check if we need to create any more Object Builders
        for (String propertyComponent : propertyComponents) {
            // We don't need to make a builder for the last component, as it isn't an object, it's a value
            if (propertyComponents.indexOf(propertyComponent) != propertyComponents.size() - 1) {
                propertyComponentObjectBuilders.putIfAbsent(propertyComponent, Json.createObjectBuilder());
            }
        }

        // Add lowest level property component to immediate parent builder (second last in list)
        String immediateParent = propertyComponents.get(propertyComponents.size() - 2);

        // Use the passed in object builder if the immediate parent is the same as the previous property
        JsonObjectBuilder immediateParentObjectBuilder;
        if (immediateParent.equals(parent)) {
            immediateParentObjectBuilder = parentObjectBuilder;
        } else {
            immediateParentObjectBuilder = propertyComponentObjectBuilders.get(immediateParent);
        }

        // Add the property to the Json builder
        String propertyComponentKey = propertyComponents.get(propertyComponents.size() - 1);
        String propertyValue = containerConfig.getProperty(property);
        addPropertyToJson(immediateParentObjectBuilder, propertyComponentKey, propertyValue);
        processedProperties.add(property);

        // If there are more properties, check if each parent has any extra children
        if (sortedProperties.indexOf(property) + 1 != sortedProperties.size()) {
            // Get the next property in the list
            String nextProperty = sortedProperties.get(sortedProperties.indexOf(property) + 1);

            // For each parent component in the property (e.g. fee & fih & foh for the property fee.fih.foh.fum)
            for (int i = propertyComponents.size() - 2; i > 0; i--) {
                // Build a string of all remaining parents (so 1st run would be fee.fih.foh, 2nd would be fee.fih etc.)
                StringBuffer parents = new StringBuffer();
                for (int j = 0; j < i + 1; j++) {
                    parents.append(propertyComponents.get(j));

                    if (j != i) {
                        parents.append(".");
                    }
                }

                // Check if the next property is at the same level in the namespace,
                // or if we need to go further up the namespace
                if (nextProperty.startsWith(parents.toString())) {
                    // We've found a property at the same level in the namespace,
                    // recurse into this method to add this next property to the same object builder
                    recurseOverNested(
                            propertyComponentObjectBuilders.get(propertyComponents.get(i)),
                            sortedProperties,
                            nextProperty, propertyComponentObjectBuilders, immediateParent);
                    // We don't want to keep looping as we'll end up adding stuff added in the recursive method call
                    // above
                    break;
                } else {
                    if (i != 0) {
                        // If we haven't found another property in the same namespace, add the current object builder
                        // to its parent
                        JsonObjectBuilder parentPropertyComponentObjectBuilder = propertyComponentObjectBuilders.get(
                                propertyComponents.get(i - 1));
                        parentPropertyComponentObjectBuilder.add(propertyComponents.get(i),
                                propertyComponentObjectBuilders.get(propertyComponents.get(i)));
                        propertyComponentObjectBuilders.remove(propertyComponents.get(i));
                    }
                }
            }
        } else {
            // If there are no more properties, make sure to add the remaining object builders to their parents
            // Only do so if it's more than two levels deep though, as otherwise we've already added it
            if (propertyComponents.size() > 2) {
                for (int i = propertyComponents.size() - 2; i > 0; i--) {
                    propertyComponentObjectBuilders.get(propertyComponents.get(i - 1))
                            .add(propertyComponents.get(i),
                                    propertyComponentObjectBuilders.get(propertyComponents.get(i)));
                    propertyComponentObjectBuilders.remove(
                            propertyComponents.get(propertyComponents.indexOf(immediateParent)));
                }
            }
        }
    }

    /**
     * Adds the given property and property value to the provided JsonObjectBuilder
     *
     * @param jsonObjectBuilder The object builder to add the property to
     * @param property The name of the property to add
     * @param propertyValue The value of the property to add
     */
    private void addPropertyToJson(JsonObjectBuilder jsonObjectBuilder, String property, String propertyValue) {
        // Check for array
        if (propertyValue.startsWith("[") && propertyValue.endsWith("]")) {
            propertyValue = propertyValue.replaceAll("\\[", "").replaceAll("\\]", "");
            // If it is an array, check if there are objects in this array that we need to deal with
            if (propertyValue.contains(",")) {
                // We have the split operator for an array and an object, so assume it is an array of objects as
                // objects with arrays are added differently
                JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();
                for (String arrayElement : propertyValue.split("\\|")) {
                    JsonObjectBuilder arrayObjectBuilder = Json.createObjectBuilder();
                    for (String object : arrayElement.split(",")) {
                        String[] keyValue = object.split(":");
                        arrayObjectBuilder.add(keyValue[0], keyValue[1]);
                    }
                    jsonArrayBuilder.add(arrayObjectBuilder);
                }

                jsonObjectBuilder.add(property, jsonArrayBuilder);
            } else {
                // Just an array
                JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();
                for (String arrayElement : propertyValue.split("\\|")) {
                    jsonArrayBuilder.add(arrayElement);
                }
                jsonObjectBuilder.add(property, jsonArrayBuilder);
            }
        } else {
            // Just a value
            jsonObjectBuilder.add(property, propertyValue);
        }
    }

    /**
     * Lifecycle helper method that attempts to remove an instance registry if we failed to create the corresponding
     * Docker container
     *
     * @param adminCommandContext
     * @param actionReport
     */
    private void unregisterInstance(AdminCommandContext adminCommandContext, ActionReport actionReport) {
        if (commandRunner != null) {
            actionReport.appendMessage("\n\nWill attempt to unregister instance...");

            ActionReport subActionReport = actionReport.addSubActionsReport();
            CommandRunner.CommandInvocation commandInvocation = commandRunner.getCommandInvocation("_unregister-instance",
                    subActionReport, adminCommandContext.getSubject());
            ParameterMap commandParameters = new ParameterMap();
            commandParameters.add("DEFAULT", instanceName);
            commandInvocation.parameters(commandParameters);
            commandInvocation.execute();

            // The unregister instance command doesn't actually log any messages to the asadmin prompt, so let's
            // give a more useful message
            if (subActionReport.getActionExitCode() == SUCCESS) {
                actionReport.appendMessage("\nSuccessfully unregistered instance");
            } else {
                actionReport.appendMessage("\nFailed to unregister instance, user intervention will be required");
            }
        }
    }
}
