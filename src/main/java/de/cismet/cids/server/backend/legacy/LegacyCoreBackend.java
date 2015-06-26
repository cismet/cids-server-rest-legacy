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
package de.cismet.cids.server.backend.legacy;

import Sirius.navigator.connection.Connection;
import Sirius.navigator.connection.ConnectionFactory;
import Sirius.navigator.connection.ConnectionInfo;
import Sirius.navigator.connection.ConnectionSession;
import Sirius.navigator.connection.RESTfulConnection;
import Sirius.navigator.connection.SessionManager;
import Sirius.navigator.connection.proxy.ConnectionProxy;
import Sirius.navigator.connection.proxy.DefaultConnectionProxyHandler;

import Sirius.server.middleware.types.MetaClass;
import Sirius.server.newuser.UserGroup;

import org.openide.util.Exceptions;
import org.openide.util.Lookup;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.rmi.RemoteException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import de.cismet.cids.navigator.utils.ClassCacheMultiple;

import de.cismet.cids.server.CallServerService;
import de.cismet.cids.server.actions.ServerAction;
import de.cismet.cids.server.api.types.User;
import de.cismet.cids.server.api.types.legacy.ClassNameCache;
import de.cismet.cids.server.cores.legacy.LegacyCidsServerCore;
import de.cismet.cids.server.data.RuntimeContainer;
import de.cismet.cids.server.search.CidsServerSearch;
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
    private static final org.apache.log4j.Logger LOG = org.apache.log4j.Logger.getLogger(LegacyCoreBackend.class);

    //~ Instance fields --------------------------------------------------------

    private final HashMap<User, Sirius.server.newuser.User> userMap = new HashMap<User, Sirius.server.newuser.User>();
    private final HashMap<String, ServerAction> serverActionMap = new HashMap<String, ServerAction>();
    private final transient ClassNameCache classNameCache = new ClassNameCache();

    private final CallServerService service = new RESTfulSerialInterfaceConnector(LegacyCidsServerCore.getCallserver());
    private boolean testModeEnabled = false;
    private Sirius.server.newuser.User testUser = null;

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
     *
     * @param   className  DOCUMENT ME!
     * @param   cidsUser   DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  RemoteException  DOCUMENT ME!
     */
    public MetaClass getMetaclassForClassname(final String className, final Sirius.server.newuser.User cidsUser)
            throws RemoteException {
        final String tableName = className.toLowerCase();
        final String domainName = RuntimeContainer.getServer().getDomainName();

        final MetaClass metaClass = LegacyCoreBackend.getInstance()
                    .getService()
                    .getClassByTableName(cidsUser, tableName, domainName);
        return metaClass;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  user  DOCUMENT ME!
     */
    private void initProxy(final User user) {
        try {
            final ConnectionInfo info = new ConnectionInfo();
            info.setCallserverURL(LegacyCidsServerCore.getCallserver());
            info.setUsername(user.getUser());
            info.setUsergroup(null);
            info.setPassword(user.getPass());
            info.setUserDomain(user.getDomain());
            info.setUsergroupDomain(null);

            final Connection connection = ConnectionFactory.getFactory()
                        .createConnection(RESTfulConnection.class.getCanonicalName(),
                            info.getCallserverURL());

            final ConnectionSession session = ConnectionFactory.getFactory().createSession(connection, info, true);
            final ConnectionProxy proxy = ConnectionFactory.getFactory()
                        .createProxy(DefaultConnectionProxyHandler.class.getCanonicalName(), session);
            SessionManager.init(proxy);

            ClassCacheMultiple.setInstance(user.getDomain());
        } catch (final Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

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
     * @param  enabled  DOCUMENT ME!
     */
    public void setEnableTestMode(final boolean enabled) {
        testModeEnabled = enabled;
        try {
            testUser = service.getUser(
                    null,
                    null,
                    LegacyCidsServerCore.getTestDomain(),
                    LegacyCidsServerCore.getTestUser(),
                    LegacyCidsServerCore.getTestPassword());
        } catch (final Exception ex) {
            LOG.error(ex, ex);
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
            initProxy(user);
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
        if (testModeEnabled) {
            return testUser;
        } else {
            return getCidsUser(user, null);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param   user  DOCUMENT ME!
     * @param   role  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public Sirius.server.newuser.User getCidsUser(final User user, final String role) {
        user.equals(user);
        final Sirius.server.newuser.User cidsUser = userMap.get(user);
        if (cidsUser == null) {
            return null;
        }
        if ((role == null) || role.equals("all")) {
            cidsUser.setUserGroup(null);
        } else {
            for (final UserGroup cidsUserGroup : cidsUser.getPotentialUserGroups()) {
                if (role.equals(cidsUserGroup.getName())) {
                    cidsUser.setUserGroup(cidsUserGroup);
                    break;
                }
            }
        }
        return cidsUser;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public ClassNameCache getClassNameCache() {
        return classNameCache;
    }

    /**
     * If the class name cache is not yet filled for the specified domain, getClasses is invoked on the remote legacy
     * rest server.
     *
     * @param   domain    domain of the meta class
     * @param   cidsUser  user performing the request
     *
     * @return  true if the domain is cached
     *
     * @throws  RuntimeException  DOCUMENT ME!
     */
    public boolean ensureDomainCached(final String domain, final User cidsUser) {
        if (!this.classNameCache.isDomainCached(domain)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("need to fill the class name cache for domain '" + cidsUser.getDomain()
                            + "' to loockup class with legacy ids.");
            }

            final Sirius.server.newuser.User legacyUser = this.getCidsUser(cidsUser, null);
            final MetaClass[] metaClasses;
            try {
                metaClasses = LegacyCoreBackend.getInstance().getService().getClasses(legacyUser, cidsUser.getDomain());
            } catch (RemoteException ex) {
                LOG.error(ex.getMessage(), ex);
                throw new RuntimeException(ex.getMessage(), ex);
            }

            if (metaClasses != null) {
                this.classNameCache.fillCache(cidsUser.getDomain(), metaClasses);
            } else {
                final String message = "cannot lookup class name for class with id '"
                            + "' and fill class name cache: no classes found at domain '" + domain
                            + "' for user '" + cidsUser.getUser() + "'";
                LOG.error(message);
                throw new RuntimeException(message);
            }
        }

        return this.classNameCache.isDomainCached(domain);
    }

    /**
     * Returns the id of a legacy meta class with the specified name for the specified domain. If the class name cache
     * is not yet filled, getClasses is invoked on the remote legacy rest server.
     *
     * @param   cidsUser   domain of the meta class
     * @param   className  legacy class id of the meta class
     *
     * @return  id of the legacy meta class or -1 if the id is not found
     *
     * @throws  Exception  java.rmi.RemoteException if any error occurs
     */
    public int getIdForClassName(final User cidsUser, final String className) throws Exception {
        if (!this.classNameCache.isDomainCached(cidsUser.getDomain())) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("need to fill the class name cache for domain '" + cidsUser.getDomain()
                            + "' to lookup class with legacy name '" + className + "'");
            }

            final Sirius.server.newuser.User legacyUser = this.getCidsUser(cidsUser, null);
            final MetaClass[] metaClasses = LegacyCoreBackend.getInstance()
                        .getService()
                        .getClasses(legacyUser, cidsUser.getDomain());

            if (metaClasses != null) {
                this.classNameCache.fillCache(cidsUser.getDomain(), metaClasses);
            } else {
                final String message = "cannot lookup class id for class with name '"
                            + "' and fill class name cache: no classes found at domain '" + cidsUser.getDomain()
                            + "' for user '" + cidsUser.getUser() + "'";
                LOG.error(message);
                throw new Exception(message);
            }
        }

        return this.classNameCache.getClassIdForClassName(
                cidsUser.getDomain(),
                className);
    }
}
