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

import org.openide.util.Lookup;

import org.reflections.ReflectionUtils;

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
    private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(LegacyCoreBackend.class);

    //~ Instance fields --------------------------------------------------------

    private final HashMap<User, Sirius.server.newuser.User> userMap = new HashMap<User, Sirius.server.newuser.User>();
    private final HashMap<String, ServerAction> serverActionMap = new HashMap<String, ServerAction>();
    private final HashMap<String, Class<? extends CidsServerSearch>> serverSearchMap =
        new HashMap<String, Class<? extends CidsServerSearch>>();
    private final HashMap<String, HashMap<String, String>> serverSearchParamsMap =
        new HashMap<String, HashMap<String, String>>();
    private final HashMap<String, List<String>> serverSearchParamsListMap = new HashMap<String, List<String>>();

    private final CallServerService service = new RESTfulSerialInterfaceConnector(LegacyCidsServerCore.getCallserver());
    private boolean testModeEnabled = false;
    private Sirius.server.newuser.User testUser = null;

    //~ Constructors -----------------------------------------------------------

    /**
     * Creates a new ConnectorHelper object.
     */
    private LegacyCoreBackend() {
        loadServerActions();
        loadServerSearches();
    }

    //~ Methods ----------------------------------------------------------------

    /**
     * DOCUMENT ME!
     *
     * @param   classKey  DOCUMENT ME!
     * @param   cidsUser  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  RemoteException  DOCUMENT ME!
     */
    public MetaClass getMetaclassForClasskey(final String classKey, final Sirius.server.newuser.User cidsUser)
            throws RemoteException {
        final String tableName = classKey.toLowerCase();
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
            log.error(ex.getMessage(), ex);
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
     */
    public final void loadServerSearches() {
        final Collection<? extends CidsServerSearch> subTypes = Lookup.getDefault().lookupAll(CidsServerSearch.class);

        for (final CidsServerSearch subType : subTypes) {
            final Class<? extends CidsServerSearch> serverSearchClass = subType.getClass();
            final Set<Method> setters = ReflectionUtils.getAllMethods(
                    serverSearchClass,
                    ReflectionUtils.withModifier(Modifier.PUBLIC),
                    ReflectionUtils.withPrefix("set"));
            final Set<Method> settersParent = ReflectionUtils.getAllMethods(
                    CidsServerSearch.class,
                    ReflectionUtils.withModifier(Modifier.PUBLIC),
                    ReflectionUtils.withPrefix("set"));
            final Collection<String> setterParentNames = new ArrayList<String>();
            for (final Method setterParent : settersParent) {
                setterParentNames.add(setterParent.getName());
            }

            final String searchKey = serverSearchClass.getName();
            serverSearchMap.put(searchKey, serverSearchClass);
            final HashMap<String, String> paramsMap = new HashMap<String, String>();
            final List<String> paramsList = new ArrayList<String>();
            for (final Method setter : setters) {
                if (!setterParentNames.contains(setter.getName())) {
                    final Class[] paramTypes = setter.getParameterTypes();
                    for (int index = 0; index < paramTypes.length; index++) {
                        final Class paramTyp = paramTypes[index];
                        final String paramName = setter.getName().split("set")[1];
                        paramsList.add(paramName);
                        if (paramTypes.length > 1) {
                            paramsMap.put(paramName + "[" + index + "]", paramTyp.getName());
                        } else {
                            paramsMap.put(paramName, paramTyp.getName());
                        }
                    }
                }
            }
            serverSearchParamsMap.put(searchKey, paramsMap);
            serverSearchParamsListMap.put(searchKey, paramsList);
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
            log.error(ex, ex);
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
    public HashMap<String, Class<? extends CidsServerSearch>> getServerSearchMap() {
        return serverSearchMap;
    }

    /**
     * DOCUMENT ME!
     *
     * @param   searchKey  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public HashMap<String, String> getServerSearchParamMap(final String searchKey) {
        return serverSearchParamsMap.get(searchKey);
    }

    /**
     * DOCUMENT ME!
     *
     * @param   searchKey  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public List<String> getServerSearchParamList(final String searchKey) {
        return serverSearchParamsListMap.get(searchKey);
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
}
