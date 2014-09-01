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
                }
            }
            return taskNameNodes;
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            return null;
        }
    }

    @Override
    public ObjectNode getAction(final User user, final String actionKey, final String role) {
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
                return null;
            }
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            return null;
        }
    }

    @Override
    public List<ObjectNode> getAllTasks(final User user, final String actionKey, final String role) {
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
    public ObjectNode createNewActionTask(final User user,
            final String actionKey,
            ActionTask actionTask,
            final String role,
            final boolean requestResultingInstance,
            final InputStream fileAttachement) {
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
                            final Object taskResult = LegacyCoreBackend.getInstance()
                                        .getService()
                                        .executeTask(
                                            cidsUser,
                                            actionKey,
                                            "WUNDA_BLAU",
                                            null,
                                            cidsSAPs.toArray(new ServerActionParameter[0]));
                            final GenericResourceWithContentType grwct = new GenericResourceWithContentType(
                                    STREAMTYPE_APPOCTETSTREAM,
                                    taskResult);
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
            throw new RuntimeException(ex);
        }

        if (requestResultingInstance) {
            try {
                return (ObjectNode)MAPPER.convertValue(actionTask, ObjectNode.class);
            } catch (final Exception ex) {
                log.error(ex.getMessage(), ex);
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public ObjectNode getTask(final User user, final String actionKey, final String taskKey, final String role) {
        final ActionTask actionTask = taskMap.get(taskKey);
        if (actionTask != null) {
            final ObjectNode on = (ObjectNode)MAPPER.convertValue(actionTask, ObjectNode.class);
            return on;
        } else {
            return null;
        }
    }

    @Override
    public List<ActionResultInfo> getResults(final User user,
            final String actionKey,
            final String taskKey,
            final String role) {
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
        }
        return ariList;
    }

    @Override
    public void deleteTask(final User user, final String actionKey, final String taskKey, final String role) {
        if (taskMap.containsKey(taskKey)) {
            taskMap.remove(taskKey);
            resultMap.remove(taskKey);
        }
    }

    @Override
    public GenericResourceWithContentType getResult(final User user,
            final String actionKey,
            final String taskKey,
            final String resultKey,
            final String role) {
        final GenericResourceWithContentType result = resultMap.get(taskKey);
        return result;
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.action"; // NOI18N
    }
}
