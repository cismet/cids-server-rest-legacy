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

import org.openide.util.lookup.ServiceProvider;

import java.io.InputStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import de.cismet.cids.server.actions.ServerAction;
import de.cismet.cids.server.api.types.ActionResultInfo;
import de.cismet.cids.server.api.types.ActionTask;
import de.cismet.cids.server.api.types.GenericResourceWithContentType;
import de.cismet.cids.server.api.types.User;
import de.cismet.cids.server.cores.ActionCore;
import de.cismet.cids.server.cores.CidsServerCore;

import static Sirius.server.middleware.impls.domainserver.DomainServerImpl.SERVER_ACTION_PERMISSION_ATTRIBUTE_PREFIX;

/**
 * DOCUMENT ME!
 *
 * @author   thorsten
 * @version  1.0
 */
@ServiceProvider(service = CidsServerCore.class)
public class LegacyActionCore implements ActionCore {

    //~ Static fields/initializers ---------------------------------------------

    private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(LegacyNodeCore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper(new JsonFactory());

    private static final HashMap<String, Thread> taskMap = new HashMap<String, Thread>();

    //~ Methods ----------------------------------------------------------------

    @Override
    public List<ObjectNode> getAllActions(final User user, final String role) {
        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user);
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
            log.error(null, ex);
            return null;
        }
    }

    @Override
    public ObjectNode getAction(final User user, final String actionKey, final String role) {
        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user);
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
            log.error(null, ex);
            return null;
        }
    }

    @Override
    public List<ObjectNode> getAllTasks(final User user, final String actionKey, final String role) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
    }

    @Override
    public ObjectNode createNewActionTask(final User user,
            final String actionKey,
            final ActionTask actionTask,
            final String role,
            final boolean requestResultingInstance,
            final InputStream fileAttachement) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
    }

    @Override
    public ObjectNode getTask(final User user, final String actionKey, final String taskKey, final String role) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
    }

    @Override
    public List<ActionResultInfo> getResults(final User user,
            final String actionKey,
            final String taskKey,
            final String role) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
    }

    @Override
    public void deleteTask(final User user, final String actionKey, final String taskKey, final String role) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
    }

    @Override
    public GenericResourceWithContentType getResult(final User user,
            final String actionKey,
            final String taskKey,
            final String resultKey,
            final String role) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.action"; // NOI18N
    }
}
