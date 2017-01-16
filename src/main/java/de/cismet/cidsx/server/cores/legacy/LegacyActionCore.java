/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cidsx.server.cores.legacy;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.lookup.ServiceProvider;

import java.io.InputStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import javax.servlet.http.HttpServletResponse;

import javax.ws.rs.core.MediaType;

import de.cismet.cids.server.actions.ServerActionParameter;

import de.cismet.cidsx.base.types.MediaTypes;
import de.cismet.cidsx.base.types.Type;

import de.cismet.cidsx.server.api.types.ActionInfo;
import de.cismet.cidsx.server.api.types.ActionParameterInfo;
import de.cismet.cidsx.server.api.types.ActionResultInfo;
import de.cismet.cidsx.server.api.types.ActionTask;
import de.cismet.cidsx.server.api.types.GenericResourceWithContentType;
import de.cismet.cidsx.server.api.types.User;
import de.cismet.cidsx.server.api.types.legacy.ServerActionFactory;
import de.cismet.cidsx.server.api.types.legacy.ServerSearchFactory;
import de.cismet.cidsx.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cidsx.server.cores.ActionCore;
import de.cismet.cidsx.server.cores.CidsServerCore;
import de.cismet.cidsx.server.exceptions.ActionNotFoundException;
import de.cismet.cidsx.server.exceptions.ActionTaskNotFoundException;
import de.cismet.cidsx.server.exceptions.CidsServerException;
import de.cismet.cidsx.server.exceptions.InvalidParameterException;

import de.cismet.commons.concurrency.CismetExecutors;

import static Sirius.server.middleware.impls.domainserver.DomainServerImpl.SERVER_ACTION_PERMISSION_ATTRIBUTE_PREFIX;

/**
 * DOCUMENT ME!
 *
 * @author   thorsten
 * @version  1.0
 */
@Slf4j
@ServiceProvider(service = CidsServerCore.class)
public class LegacyActionCore implements ActionCore {

    //~ Static fields/initializers ---------------------------------------------

    private static final ObjectMapper MAPPER = new ObjectMapper(new JsonFactory());

    private static final ConcurrentHashMap<String, ExecutorService> actionExecutorServices =
        new ConcurrentHashMap<String, ExecutorService>();

    //~ Instance fields --------------------------------------------------------

    private final Map<String, ActionTask> taskMap = Collections.synchronizedMap(new HashMap<String, ActionTask>());
    private final Map<String, GenericResourceWithContentType> resultMap = Collections.synchronizedMap(
            new HashMap<String, GenericResourceWithContentType>());

    //~ Methods ----------------------------------------------------------------

    @Override
    public List<JsonNode> getAllActions(final User user, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getAllActions for user '" + user.getUser() + "' with role '" + role + "'");
        }

        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final List<JsonNode> taskNameNodes = new ArrayList<JsonNode>();
            final List<ActionInfo> serverActionInfos = ServerActionFactory.getFactory().getServerActionInfos();
            for (final ActionInfo actionInfo : serverActionInfos) {
                if (LegacyCoreBackend.getInstance().getService().hasConfigAttr(
                                cidsUser,
                                SERVER_ACTION_PERMISSION_ATTRIBUTE_PREFIX
                                + actionInfo.getActionKey())) {
                    taskNameNodes.add(MAPPER.convertValue(actionInfo, JsonNode.class));
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("user '" + user.getUser() + "' with role '"
                                    + role + "' does not have the permission to get the Action with actionKey '"
                                    + actionInfo.getActionKey() + "': Config Attribute '"
                                    + SERVER_ACTION_PERMISSION_ATTRIBUTE_PREFIX
                                    + actionInfo.getActionKey() + "' not available for user. ");
                    }
                }
            }
            return taskNameNodes;
        } catch (final Exception ex) {
            final String message = "error while getting all actions: " + ex.getMessage();
            log.error(message, ex);
            throw new CidsServerException(message, message,
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex);
        }
    }

    @Override
    public JsonNode getAction(final User user, final String actionKey, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getAction with actionKey '" + actionKey + "'");
        }

        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            if (LegacyCoreBackend.getInstance().getService().hasConfigAttr(
                            cidsUser,
                            SERVER_ACTION_PERMISSION_ATTRIBUTE_PREFIX
                            + actionKey)) {
                final ActionInfo actionInfo = ServerActionFactory.getFactory().getServerActionInfo(actionKey);
                if (actionInfo != null) {
                    return MAPPER.convertValue(actionInfo, JsonNode.class);
                }
            } else {
                log.warn("user '" + user.getUser() + "' with role '"
                            + role + "' does not have the permission to get the Action with actionKey '"
                            + actionKey + "': Config Attribute '" + SERVER_ACTION_PERMISSION_ATTRIBUTE_PREFIX
                            + actionKey + "' not found! ");
            }
        } catch (final Exception ex) {
            final String message = "error while getting action with actionKey '"
                        + actionKey + "': " + ex.getMessage();
            log.error(message, ex);
            throw new CidsServerException(message, message,
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex);
        }

        return null;
    }

    @Override
    public List<JsonNode> getAllTasks(final User user, final String actionKey, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getAllTasks with actionKey '" + actionKey + "'");
        }

        if (ServerActionFactory.getFactory().getServerAction(actionKey) == null) {
            final String message = "Action '" + actionKey + " could not be found!";
            log.warn(message);
            throw new ActionNotFoundException(message, actionKey);
        }

        final List<JsonNode> nodes = new ArrayList<JsonNode>();
        for (final ActionTask actionTask : taskMap.values()) {
            if ((actionTask != null) && actionTask.getActionKey().equals(actionKey)) {
                final JsonNode on = MAPPER.convertValue(actionTask, JsonNode.class);
                nodes.add(on);
            }
        }
        return nodes;
    }

    @Override
    public GenericResourceWithContentType executeNewAction(final User user,
            final String actionKey,
            final ActionTask actionTask,
            final String role,
            final GenericResourceWithContentType<InputStream> bodyResource) {
        if (log.isDebugEnabled()) {
            log.info("executeNewAction with actionKey '" + actionKey + "'");
        }

        // fetch the action info (meta-data about action parameters and return types) from
        // the introspected or annotated lookup-able ServerAction instance.
        final ActionInfo actionInfo = ServerActionFactory.getFactory().getServerActionInfo(actionKey);
        if (actionInfo == null) {
            final String message = "The Action '" + actionKey + "' is not supported by this CIDS Server Instance!";
            log.error(message);
            throw new InvalidParameterException(message, "actionKey", actionKey);
        }

        final ServerActionParameter[] serverActionParameters;
        final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

        // FIXME: pass client-provided accept-content header field and/or actionTask.getResultDescription() to
        // RestApiCidsServerAction in order to let action create requested content type
        // See https://github.com/cismet/cids-server-rest/issues/83

        try {
            // procress the parameters, the client may provide the action meta-info!
            if ((actionTask != null) && (actionTask.getParameters() != null)
                        && !actionTask.getParameters().isEmpty()) {
                if ((actionTask.getParameterDescription() == null) || actionTask.getParameterDescription().isEmpty()) {
                    log.warn(
                        "client did not send action parameter infos for '"
                                + actionKey
                                + "', trying to load them from local action info cache");
                    if ((actionInfo.getParameterDescription() != null)
                                && !actionInfo.getParameterDescription().isEmpty()) {
                        actionTask.setParameterDescription(actionInfo.getParameterDescription());
                    } else {
                        log.warn("action parameter descriptions for '" + actionKey
                                    + "' not found in local action info cache!");
                    }
                }

                serverActionParameters = ServerActionFactory.getFactory()
                            .ServerActionParametersFromActionTask(actionTask);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("no server action parameters provided for '" + actionKey
                                + "'!");
                }
                serverActionParameters = new ServerActionParameter[0];
            }

            // procress the (binary) attachment
            final Object bodyObject;
            if (bodyResource != null) {
                final ActionParameterInfo bodyDescription;
                if ((actionTask != null) && (actionTask.getBodyDescription() != null)
                            && (actionTask.getBodyDescription().getMediaType() != null)) {
                    bodyDescription = actionTask.getBodyDescription();
                } else if ((actionInfo.getBodyDescription() != null)
                            && (actionInfo.getBodyDescription().getMediaType() != null)) {
                    log.warn(
                        "client did not send body parameter info for '"
                                + actionKey
                                + "', trying to load them from local action info cache");
                    bodyDescription = actionInfo.getBodyDescription();
                } else {
                    log.warn(
                        "body parameter description for '"
                                + actionKey
                                + "' not found in local action info cache, assuming body content is JAVA_SERIALIZED_OBJECT!");
                    bodyDescription = ServerActionFactory.getFactory().getDefaultBodyDescription();
                }

                if ((bodyResource.getContentType() != null)
                            && !bodyResource.getContentType().equalsIgnoreCase(bodyDescription.getMediaType())) {
                    final String message = "The client provided an action body parameter of type '"
                                + bodyResource.getContentType() + "', but the Server Action '"
                                + actionKey + "' accepts only '" + bodyDescription.getMediaType() + "'";
                    log.error(message);

                    throw new CidsServerException(message, message,
                        HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
                }

                bodyObject = ServerActionFactory.getFactory()
                            .bodyObjectFromFileAttachment(bodyResource.getRes(), bodyDescription);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("no body parameter provided for '" + actionKey + "'!");
                }
                bodyObject = null;
            }

            // execute the action on the remote server
            final Object taskResult = LegacyCoreBackend.getInstance()
                        .getService()
                        .executeTask(
                            cidsUser,
                            actionKey,
                            cidsUser.getDomain(),
                            bodyObject,
                            serverActionParameters);

            if (taskResult != null) {
                // The Action implementation took care to create an appropriate repesentation of the resource,
                // just hand it over to the dispatcher (cids-server-rest)
                if (GenericResourceWithContentType.class.isAssignableFrom(taskResult.getClass())) {
                    log.info("Rest API Action  '" + actionKey + "' completed, result of type '"
                                + ((GenericResourceWithContentType)taskResult).getContentType() + "' generated");

                    final MediaType actualMediaType = MediaTypes.mediaTypeForJavaClass(taskResult.getClass());
                    if (!actualMediaType.getType().equals(
                                    ((GenericResourceWithContentType)taskResult).getContentType())) {
                        log.info("Action result of type '" + actualMediaType.getType() + "' of Action  '" + actionKey
                                    + "' does not match reported result type '"
                                    + ((GenericResourceWithContentType)taskResult).getContentType() + "'!");
                    }

                    return (GenericResourceWithContentType)taskResult;
                } else {
                    final String contentType;
                    final Type resultType;
                    if ((actionTask != null) && (actionTask.getResultDescription() != null)) {
                        log.info("Action  '" + actionKey + "' completed, result of type '"
                                    + actionTask.getResultDescription().getMediaType() + "' ("
                                    + actionTask.getResultDescription().getType() + ") generated");
                        contentType = actionTask.getResultDescription().getMediaType();
                        resultType = actionTask.getResultDescription().getType();
                    } else if (actionInfo.getResultDescription() != null) {
                        log.warn(
                            "client did not provide information about default content type of result, trying to use '"
                                    + actionInfo.getResultDescription().getMediaType()
                                    + "' ("
                                    + actionInfo.getResultDescription().getType()
                                    + ") from local action info cache");
                        contentType = actionInfo.getResultDescription().getMediaType();
                        resultType = actionInfo.getResultDescription().getType();
                    } else {
                        log.warn(
                            "default content type of result not found in local action info cache, assuming '"
                                    + MediaTypes.APPLICATION_X_JAVA_SERIALIZED_OBJECT
                                    + "' ("
                                    + Type.JAVA_SERIALIZABLE
                                    + ")!");

                        contentType = MediaTypes.APPLICATION_X_JAVA_SERIALIZED_OBJECT;
                        resultType = Type.JAVA_SERIALIZABLE;
                    }

                    final Object transformedResult = ServerActionFactory.getFactory()
                                .transformLegacyActionResult(
                                    taskResult,
                                    resultType,
                                    LegacyCoreBackend.getInstance().getClassNameCache());

                    final MediaType actualMediaType = MediaTypes.mediaTypeForJavaClass(taskResult.getClass());
                    if (!actualMediaType.getType().equals(contentType)) {
                        log.info("Action result of type '" + actualMediaType.getType() + "' of Action  '" + actionKey
                                    + "' does not match reported result of type '"
                                    + contentType + "'!");
                    }

                    return new GenericResourceWithContentType(contentType, transformedResult);
                }
            }

            log.warn("action '" + actionKey + "' did not produce any result!");
            return null;
        } catch (final Exception ex) {
            final String message = "error while executing action task with actionKey '"
                        + actionKey + "': " + ex.getMessage();
            log.error(message, ex);
            throw new CidsServerException(message, message,
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex);
        }
    }

    @Override
    public JsonNode createNewActionTask(final User user,
            final String actionKey,
            ActionTask actionTask,
            final String role,
            final boolean requestResultingInstance,
            final GenericResourceWithContentType<InputStream> bodyResource) {
        if (log.isDebugEnabled()) {
            log.debug("createNewActionTask with actionKey '" + actionKey + "'");
        }

        final ActionInfo actionInfo = ServerActionFactory.getFactory().getServerActionInfo(actionKey);
        if (actionInfo == null) {
            final String message = "The Action '" + actionKey + "' is not supported by this CIDS Server Instance!";
            log.error(message);
            throw new InvalidParameterException(message, "actionKey", actionKey);
        }

        if (actionTask == null) {
            log.warn("client did not provide an action task object, creating a default one from action info template");
            actionTask = new ActionTask(actionInfo);
        }

        try {
            actionTask.setStatus(ActionTask.Status.STARTING);
            ExecutorService es = actionExecutorServices.get(actionKey);
            if (es == null) {
                actionExecutorServices.putIfAbsent(
                    actionKey,
                    CismetExecutors.newFixedThreadPool(5));
                es = actionExecutorServices.get(actionKey);
            }

            final ActionTask finalTask = actionTask;
            final Runnable actionRunner = new Runnable() {

                    @Override
                    public void run() {
                        try {
                            final long start = System.currentTimeMillis();
                            finalTask.setStatus(ActionTask.Status.RUNNING);
                            final GenericResourceWithContentType grwct = executeNewAction(
                                    user,
                                    actionKey,
                                    finalTask,
                                    role,
                                    bodyResource);
                            resultMap.put(finalTask.getKey(), grwct);
                            finalTask.setStatus(ActionTask.Status.FINISHED);
                            if (log.isDebugEnabled()) {
                                log.debug("Action Task '" + finalTask.getKey() + "' of Action '"
                                            + finalTask.getActionKey() + "' successfully completed in "
                                            + (start - System.currentTimeMillis()) + "ms.");
                            }
                        } catch (final Exception ex) {
                            log.error(ex.getMessage(), ex);
                            finalTask.setStatus(ActionTask.Status.ERROR);
                        }
                    }
                };

            if (actionTask.getKey() == null) {
                actionTask.setKey(actionKey + ":" + String.valueOf(System.currentTimeMillis()));
            }

            es.execute(actionRunner);
            if (log.isDebugEnabled()) {
                log.debug("Action Task '" + finalTask.getKey() + "' of Action '"
                            + finalTask.getActionKey() + "' executed");
            }
            taskMap.put(actionTask.getKey(), finalTask);
        } catch (Exception ex) {
            final String message = "error while creating new action task with actionKey '"
                        + actionKey + "': " + ex.getMessage();
            log.error(message, ex);
            throw new CidsServerException(message, message,
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex);
        }

        try {
            return (JsonNode)MAPPER.convertValue(actionTask, JsonNode.class);
        } catch (final Exception ex) {
            final String message = "error while creating new action task with actionKey '"
                        + actionKey + "': " + ex.getMessage();
            log.error(message, ex);
            throw new CidsServerException(message, message,
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex);
        }
    }

    @Override
    public JsonNode getTask(final User user, final String actionKey, final String taskKey, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getTask with actionKey '" + actionKey + "' and taskKey '" + taskKey + "'");
        }

        if (ServerActionFactory.getFactory().getServerAction(actionKey) == null) {
            final String message = "Action '" + actionKey + " could not be found!";
            log.warn(message);
            throw new ActionNotFoundException(message, actionKey);
        }

        final ActionTask actionTask = taskMap.get(taskKey);
        if (actionTask != null) {
            final JsonNode on = MAPPER.convertValue(actionTask, JsonNode.class);
            return on;
        } else {
            log.warn("no taks for actionKey '" + actionKey + "' and taskKey '" + taskKey + "' found, returning null!");
            return null;
        }
    }

    @Override
    public List<ActionResultInfo> getResults(final User user,
            final String actionKey,
            final String taskKey,
            final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getResults with actionKey '" + actionKey + "' and taskKey '" + taskKey + "'");
        }

        if (ServerActionFactory.getFactory().getServerAction(actionKey) == null) {
            final String message = "Action '" + actionKey + " could not be found!";
            log.warn(message);
            throw new ActionNotFoundException(message, actionKey);
        }

        final ActionTask actionTask = taskMap.get(taskKey);
        if (actionTask == null) {
            final String message = "The Task '" + taskKey + "' of Action '"
                        + actionKey + " could not be found!";
            log.warn(message);
            throw new ActionTaskNotFoundException(message, taskKey);
        }

        final GenericResourceWithContentType result = resultMap.get(taskKey);
        if ((result == null) || (result.getRes() == null)) {
            log.warn("No results for  Task '" + taskKey + "' of Action '"
                        + actionKey + " found!");
            return null;
        }

        final List<ActionResultInfo> ariList = new LinkedList<ActionResultInfo>();
        final ActionResultInfo ari = new ActionResultInfo(
                actionTask.getKey(),
                actionTask.getActionKey(),
                actionTask.getDescription(),
                result.getContentType(),
                actionTask.getParameters());
        ariList.add(ari);
        return ariList;
    }

    @Override
    public void deleteTask(final User user, final String actionKey, final String taskKey, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("deleteTask with actionKey '" + actionKey + "' and taskKey '" + taskKey + "'");
        }

        if (taskMap.containsKey(taskKey)) {
            taskMap.remove(taskKey);
            resultMap.remove(taskKey);
        } else {
            log.warn("could not delete task with '" + actionKey + "' and taskKey '" + taskKey + "': task not found");
        }
    }

    @Override
    public GenericResourceWithContentType getResult(final User user,
            final String actionKey,
            final String taskKey,
            final String resultKey,
            final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getResult with actionKey '" + actionKey + "', taskKey '"
                        + taskKey + "' and resultKey '" + resultKey + "'");
        }

        if (ServerActionFactory.getFactory().getServerAction(actionKey) == null) {
            final String message = "Action '" + actionKey + " could not be found!";
            log.warn(message);
            throw new ActionNotFoundException(message, actionKey);
        }

        final ActionTask actionTask = taskMap.get(taskKey);
        if (actionTask == null) {
            final String message = "The Task '" + taskKey + "' of Action '"
                        + actionKey + " could not be found!";
            log.warn(message);
            throw new ActionTaskNotFoundException(message, taskKey);
        }

        final GenericResourceWithContentType result = resultMap.get(taskKey);
        if ((result == null) || (result.getRes() == null)) {
            log.warn("No results for  Task '" + taskKey + "' of Action '"
                        + actionKey + " found!");
            return null;
        }

        return result;
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.action"; // NOI18N
    }
}
