/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.cismet.cids.server.cores.legacy;

import org.openide.util.Lookup;

import java.util.Collection;
import java.util.HashMap;

import de.cismet.cids.server.CallServerService;
import de.cismet.cids.server.actions.ServerAction;
import de.cismet.cids.server.api.types.User;
import de.cismet.cids.server.ws.rest.RESTfulSerialInterfaceConnector;

/**
 * DOCUMENT ME!
 *
 * @author   jruiz
 * @version  $Revision$, $Date$
 */
public class LegacyCoreBackend {

    //~ Static fields/initializers ---------------------------------------------

    private static final LegacyCoreBackend INSTANCE = new LegacyCoreBackend();

    private static final String CALLSERVER_URL = "http://localhost:9917/callserver/binary";

    //~ Instance fields --------------------------------------------------------

    final HashMap<User, Sirius.server.newuser.User> userMap = new HashMap<User, Sirius.server.newuser.User>();
    private HashMap<String, ServerAction> serverActionMap = new HashMap<String, ServerAction>();

    private final CallServerService service = new RESTfulSerialInterfaceConnector(CALLSERVER_URL);

    //~ Constructors -----------------------------------------------------------

    /**
     * Creates a new ConnectorHelper object.
     */
    private LegacyCoreBackend() {
        loadServerActions();
    }

    //~ Methods ----------------------------------------------------------------

    /**
     * DOCUMENT ME!
     */
    public final void loadServerActions() {
        final Collection<? extends ServerAction> serverActions = Lookup.getDefault().lookupAll(ServerAction.class);
        for (final ServerAction serverAction : serverActions) {
            serverActionMap.put(serverAction.getTaskName(), serverAction);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public HashMap<String, ServerAction> getServerActionMap() {
        return serverActionMap;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public static LegacyCoreBackend getInstance() {
        return INSTANCE;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public CallServerService getService() {
        return service;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  cidsUser  DOCUMENT ME!
     * @param  user      DOCUMENT ME!
     */
    public void registerUser(final Sirius.server.newuser.User cidsUser, final User user) {
        if (!userMap.containsKey(user)) {
            userMap.put(user, cidsUser);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param   user  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public Sirius.server.newuser.User getCidsUser(final User user) {
        return userMap.get(user);
    }
}
