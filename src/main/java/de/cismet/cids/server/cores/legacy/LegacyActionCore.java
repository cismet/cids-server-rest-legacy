/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cids.server.cores.legacy;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.IOUtils;

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

import de.cismet.cids.server.actions.ServerAction;
import de.cismet.cids.server.actions.ServerActionParameter;
import de.cismet.cids.server.api.types.ActionResultInfo;
import de.cismet.cids.server.api.types.ActionTask;
import de.cismet.cids.server.api.types.GenericResourceWithContentType;
import de.cismet.cids.server.api.types.User;
import de.cismet.cids.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cids.server.cores.ActionCore;
import de.cismet.cids.server.cores.CidsServerCore;

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
    private static final String STREAMTYPE_APPOCTETSTREAM = "application/octet-stream";

    private static ConcurrentHashMap<String, ExecutorService> actionExecutorServices =
        new ConcurrentHashMap<String, ExecutorService>();

    //~ Instance fields --------------------------------------------------------

    private final Map<String, ActionTask> taskMap = Collections.synchronizedMap(new HashMap<String, ActionTask>());
    private final Map<String, GenericResourceWithContentType> resultMap = Collections.synchronizedMap(
            new HashMap<String, GenericResourceWithContentType>());

    //~ Methods ----------------------------------------------------------------

    @Override
    public List<ObjectNode> getAllActions(final User user, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getAllActions");
        }

        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            cidsUser.setUserGroup(null);
            final List<ObjectNode> taskNameNodes = new ArrayList<ObjectNode>();
            final HashMap<String, ServerAction> serverActionMap = LegacyCoreBackend.getInstance().getServerActionMap();
            for (final String actionKey : serverActionMap.keySet()) {
                if (LegacyCoreBackend.getInstance().getService().hasConfigAttr(
                                cidsUser,
                                SERVER_ACTION_PERMISSION_ATTRIBUTE_PREFIX
                                + actionKey)) {
                    final ServerAction cidsServerAction = serverActionMap.get(actionKey);
                    final ActionTask actionTask = new ActionTask(cidsServerAction.getTaskName(),
                            cidsServerAction.getTaskName(),
                            "legacy ServerAction",
                            null,
                            null);
                    taskNameNodes.add(MAPPER.convertValue(actionTask, ObjectNode.class));
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("user '" + user.getUser() + "' with role '"
                                    + role + "' does not have the permission to get the Action with actionKey '"
                                    + actionKey + "': Config Attribute '" + SERVER_ACTION_PERMISSION_ATTRIBUTE_PREFIX
                                    + actionKey + "' not available for user. ");
                    }
                }
            }
            return taskNameNodes;
        } catch (final Exception ex) {
            final String message = "error while getting all actions: " + ex.getMessage();
            log.error(message, ex);
            throw new RuntimeException(message, ex);
        }
    }

    @Override
    public ObjectNode getAction(final User user, final String actionKey, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getAction with actionKey '" + actionKey + "'");
        }

        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final HashMap<String, ServerAction> serverActionMap = LegacyCoreBackend.getInstance().getServerActionMap();
            if (LegacyCoreBackend.getInstance().getService().hasConfigAttr(
                            cidsUser,
                            SERVER_ACTION_PERMISSION_ATTRIBUTE_PREFIX
                            + actionKey)) {
                final ServerAction cidsServerAction = serverActionMap.get(actionKey);
                final ActionTask actionTask = new ActionTask(cidsServerAction.getTaskName(),
                        cidsServerAction.getTaskName(),
                        "legacy ServerAction",
                        null,
                        null);
                return MAPPER.convertValue(actionTask, ObjectNode.class);
            } else {
                log.warn("user '" + user.getUser() + "' with role '"
                            + role + "' does not have the permission to get the Action with actionKey '"
                            + actionKey + "': Config Attribute '" + SERVER_ACTION_PERMISSION_ATTRIBUTE_PREFIX
                            + actionKey + "' not found! ");
                return null;
            }
        } catch (final Exception ex) {
            final String message = "error while getting action with actionKey '"
                        + actionKey + "': " + ex.getMessage();
            log.error(message, ex);
            throw new RuntimeException(message, ex);
        }
    }

    @Override
    public List<ObjectNode> getAllTasks(final User user, final String actionKey, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getAllTasks with actionKey '" + actionKey + "'");
        }

        final List<ObjectNode> nodes = new ArrayList<ObjectNode>();
        for (final ActionTask actionTask : taskMap.values()) {
            if ((actionTask != null) && actionTask.getActionKey().equals(actionKey)) {
                final ObjectNode on = (ObjectNode)MAPPER.convertValue(actionTask, ObjectNode.class);
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
            final InputStream fileAttachement) {
        if (log.isDebugEnabled()) {
            log.info("executeNewAction with actionKey '" + actionKey + "'");
        }

        final List<ServerActionParameter> cidsSAPs = new ArrayList<ServerActionParameter>();
        final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
        if ((actionTask != null) && (actionTask.getParameters() != null)
                    && !actionTask.getParameters().isEmpty()) {
            final Map<String, Object> actionParameters = actionTask.getParameters();
            for (final String parameterKey : actionParameters.keySet()) {
                final Object parameterValue = actionParameters.get(parameterKey);
                final ServerActionParameter cidsSAP = new ServerActionParameter(parameterKey, parameterValue);
                cidsSAPs.add(cidsSAP);
                if (log.isDebugEnabled()) {
                    log.debug("processing server action parameter '" + cidsSAP.toString() + "'");
                }
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("no server action parameters provided!");
            }
        }

        try {
            final byte[] body = (fileAttachement != null) ? IOUtils.toByteArray(fileAttachement) : null;
            if (body != null) {
                if (log.isDebugEnabled()) {
                    log.debug("sending binary body (e.g. file) of length " + body.length);
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("no binary body (e.g. file) parameter provided!");
                }
            }

            final Object taskResult = LegacyCoreBackend.getInstance()
                        .getService()
                        .executeTask(
                            cidsUser,
                            actionKey,
                            cidsUser.getDomain(),
                            body,
                            cidsSAPs.toArray(new ServerActionParameter[0]));

            return new GenericResourceWithContentType(STREAMTYPE_APPOCTETSTREAM, taskResult);
        } catch (final Exception ex) {
            final String message = "error while executing action task with actionKey '"
                        + actionKey + "': " + ex.getMessage();
            log.error(message, ex);
            throw new RuntimeException(message, ex);
        }
    }

    @Override
    public ObjectNode createNewActionTask(final User user,
            final String actionKey,
            ActionTask actionTask,
            final String role,
            final boolean requestResultingInstance,
            final InputStream fileAttachement) {
        if (log.isDebugEnabled()) {
            log.debug("createNewActionTask with actionKey '" + actionKey + "'");
        }

        if (actionTask == null) {
            actionTask = new ActionTask();
        }

        try {
            actionTask.setStatus(ActionTask.Status.STARTING);
            actionTask.setActionKey(actionKey);

            ExecutorService es = actionExecutorServices.get(actionKey);
            if (es == null) {
                actionExecutorServices.putIfAbsent(
                    actionKey,
                    CismetExecutors.newFixedThreadPool(5));
                es = actionExecutorServices.get(actionKey);
            }
            final List<ServerActionParameter> cidsSAPs = new ArrayList<ServerActionParameter>();
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final Map<String, Object> actionParameters = actionTask.getParameters();
            if (actionParameters != null) {
                for (final String parameterKey : actionParameters.keySet()) {
                    final Object parameterValue = actionParameters.get(parameterKey);
                    final ServerActionParameter cidsSAP = new ServerActionParameter(parameterKey, parameterValue);
                    cidsSAPs.add(cidsSAP);
                }
            }

            final ActionTask finalTask = actionTask;
            final Runnable actionRunner = new Runnable() {

                    @Override
                    public void run() {
                        try {
                            finalTask.setStatus(ActionTask.Status.RUNNING);
                            final GenericResourceWithContentType grwct = executeNewAction(
                                    user,
                                    actionKey,
                                    finalTask,
                                    role,
                                    fileAttachement);
                            resultMap.put(finalTask.getKey(), grwct);
                            finalTask.setStatus(ActionTask.Status.FINISHED);
                        } catch (final Exception ex) {
                            log.error(ex.getMessage(), ex);
                            finalTask.setStatus(ActionTask.Status.ERROR);
                        } finally {
                            taskMap.remove(finalTask.getKey());
                        }
                    }
                };
            if (actionTask.getKey() == null) {
                actionTask.setKey(actionKey + ":" + String.valueOf(System.currentTimeMillis()));
            }

            es.execute(actionRunner);

            taskMap.put(actionTask.getKey(), finalTask);
        } catch (Exception ex) {
            final String message = "error while creating new action task with actionKey '"
                        + actionKey + "': " + ex.getMessage();
            log.error(message, ex);
            throw new RuntimeException(message, ex);
        }

        try {
            return (ObjectNode)MAPPER.convertValue(actionTask, ObjectNode.class);
        } catch (final Exception ex) {
            final String message = "error while creating new action task with actionKey '"
                        + actionKey + "': " + ex.getMessage();
            log.error(message, ex);
            throw new RuntimeException(message, ex);
        }
    }

    @Override
    public ObjectNode getTask(final User user, final String actionKey, final String taskKey, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getTask with actionKey '" + actionKey + "' and taskKey '" + taskKey + "'");
        }

        final ActionTask actionTask = taskMap.get(taskKey);
        if (actionTask != null) {
            final ObjectNode on = (ObjectNode)MAPPER.convertValue(actionTask, ObjectNode.class);
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

        final GenericResourceWithContentType result = resultMap.get(taskKey);
        final ActionTask actionTask = taskMap.get(taskKey);
        final List<ActionResultInfo> ariList = new LinkedList<ActionResultInfo>();
        if (result != null) {
            final ActionResultInfo ari = new ActionResultInfo(
                    actionTask.getKey(),
                    actionTask.getActionKey(),
                    actionTask.getDescription(),
                    STREAMTYPE_APPOCTETSTREAM,
                    actionTask.getParameters());
            ariList.add(ari);
        } else {
            log.warn("no results for actionKey '" + actionKey + "' and taskKey '" + taskKey
                        + "' found, returning null!");
        }
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

        if (!resultMap.containsKey(taskKey)) {
            log.warn("could not get result for task with '" + actionKey + ", taskKey '"
                        + taskKey + "' and resultKey '" + resultKey + "': task not found");
            return null;
        }

        final GenericResourceWithContentType result = resultMap.get(taskKey);
        return result;
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.action"; // NOI18N
    }
}
