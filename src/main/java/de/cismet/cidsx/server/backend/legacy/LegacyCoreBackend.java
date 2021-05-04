/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cidsx.server.backend.legacy;

import Sirius.navigator.connection.Connection;
import Sirius.navigator.connection.ConnectionFactory;
import Sirius.navigator.connection.ConnectionInfo;
import Sirius.navigator.connection.ConnectionSession;
import Sirius.navigator.connection.RESTfulConnection;
import Sirius.navigator.connection.SessionManager;
import Sirius.navigator.connection.proxy.ConnectionProxy;
import Sirius.navigator.connection.proxy.DefaultConnectionProxyHandler;

import Sirius.server.localserver.attribute.ObjectAttribute;
import Sirius.server.middleware.types.MetaClass;
import Sirius.server.middleware.types.MetaObject;
import Sirius.server.newuser.UserGroup;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.Lookup;

import java.awt.image.BufferedImage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.net.URL;

import java.rmi.RemoteException;

import java.util.Collection;
import java.util.HashMap;

import javax.imageio.ImageIO;

import de.cismet.cids.dynamics.CidsBean;

import de.cismet.cids.navigator.utils.ClassCacheMultiple;

import de.cismet.cids.server.CallServerService;
import de.cismet.cids.server.actions.ServerAction;
import de.cismet.cids.server.ws.SSLConfig;
import de.cismet.cids.server.ws.SSLConfigProvider;
import de.cismet.cids.server.ws.rest.RESTfulSerialInterfaceConnector;

import de.cismet.cidsx.server.api.types.CidsNode;
import de.cismet.cidsx.server.api.types.User;
import de.cismet.cidsx.server.api.types.legacy.ClassNameCache;
import de.cismet.cidsx.server.cores.legacy.LegacyCidsServerCore;
import de.cismet.cidsx.server.data.RuntimeContainer;
import de.cismet.cidsx.server.data.StatusHolder;
import de.cismet.cidsx.server.exceptions.CidsServerException;

import de.cismet.connectioncontext.AbstractConnectionContext.Category;

import de.cismet.connectioncontext.ConnectionContext;
import de.cismet.connectioncontext.ConnectionContextProvider;

/**
 * DOCUMENT ME!
 *
 * @author   jruiz
 * @version  $Revision$, $Date$
 */
@Slf4j
public class LegacyCoreBackend implements ConnectionContextProvider {

    //~ Static fields/initializers ---------------------------------------------

    private static final LegacyCoreBackend INSTANCE = new LegacyCoreBackend();

    //~ Instance fields --------------------------------------------------------

    final SSLConfigProvider sslConfigProvider = Lookup.getDefault().lookup(SSLConfigProvider.class);
    final SSLConfig sslConfig = (sslConfigProvider == null) ? null : sslConfigProvider.getSSLConfig();
    private final HashMap<User, Sirius.server.newuser.User> userMap = new HashMap<>();
    private final HashMap<String, ServerAction> serverActionMap = new HashMap<>();
    private final transient ClassNameCache classNameCache = new ClassNameCache();
    private final CallServerService service = new RESTfulSerialInterfaceConnector(LegacyCidsServerCore.getCallserver(),
            sslConfig,
            LegacyCidsServerCore.isCompressionEnabled());
    private boolean testModeEnabled = false;
    private Sirius.server.newuser.User testUser = null;
    private final ConnectionContext connectionContext = ConnectionContext.create(
            Category.LEGACY,
            getClass().getSimpleName());

    /** Class Cache: Domain, classKey, Class (JsonNode) */
    @Getter private final HashMap<String, JsonNode> classCache = new HashMap<String, JsonNode>();

    /** Class Icon Cache: Domain, classKey, byte[] icon */
    @Getter private final HashMap<String, byte[]> classIconCache = new HashMap<String, byte[]>();

    /** Object Icon Cache: Domain, classKey, byte[] icon */
    @Getter private final HashMap<String, byte[]> objectIconCache = new HashMap<String, byte[]>();

    /** Object Icon Cache: Domain, classKey, byte[] icon */
    @Getter private final HashMap<String, byte[]> nodeIconCache = new HashMap<String, byte[]>();

    //~ Constructors -----------------------------------------------------------

    /**
     * Creates a new ConnectorHelper object.
     */
    private LegacyCoreBackend() {
        loadServerActions();
        log.info("LegacyCoreBackend initialized with " + this.serverActionMap.size() + " server actions");
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
    public MetaClass getMetaClassForClassname(final String className, final Sirius.server.newuser.User cidsUser)
            throws RemoteException {
        final String tableName = className.toLowerCase();
        final String domainName = RuntimeContainer.getServer().getDomainName();

        final MetaClass metaClass = LegacyCoreBackend.getInstance()
                    .getService()
                    .getClassByTableName(cidsUser, tableName, domainName, getConnectionContext());
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
                            info.getCallserverURL(),
                            LegacyCidsServerCore.isCompressionEnabled(),
                            getConnectionContext());

            final ConnectionSession session = ConnectionFactory.getFactory()
                        .createSession(connection, info, true, getConnectionContext());
            final ConnectionProxy proxy = ConnectionFactory.getFactory()
                        .createProxy(DefaultConnectionProxyHandler.class.getCanonicalName(),
                            session,
                            getConnectionContext());
            SessionManager.init(proxy);

            ClassCacheMultiple.setInstance(user.getDomain(), getConnectionContext());
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
            log.error(ex.getMessage(), ex);
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
            StatusHolder.getInstance().putStatus("cachedUsers", String.valueOf(userMap.size()));
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
    public synchronized Sirius.server.newuser.User getCidsUser(final User user, final String role) {
        user.equals(user); // <- why ???

        final Sirius.server.newuser.User immutableCidsUser = userMap.get(user);

        if (immutableCidsUser == null) {
            log.warn("user '" + user.getUser() + "@" + user.getDomain() + "' not found");
            return null;
        }

        // setUserGroup is called on one user instance that might be used by different
        // requests (threads) using different roles! synchronized is not enough! Make copy of cidsUser!
        final Sirius.server.newuser.User cidsUser = new Sirius.server.newuser.User(
                immutableCidsUser.getId(),
                immutableCidsUser.getName(),
                immutableCidsUser.getDomain(),
                immutableCidsUser.isAdmin(),
                user.getJwt()); //always use the current jws, not the cached one (server has potentialy been restarted)
        if (immutableCidsUser.isValid()) {
            cidsUser.setValid();
        } else {
            log.warn("user '" + cidsUser.getName() + "@" + cidsUser.getDomain() + "' is not valid!?");
        }
        cidsUser.setPotentialUserGroups(immutableCidsUser.getPotentialUserGroups()); // no deep copy required here

        if ((role == null) || role.equalsIgnoreCase("all")) {
            // null == all potential user groups! See https://github.com/cismet/cids-server/issues/16
            cidsUser.setUserGroup(null);
        } else {
            for (final UserGroup cidsUserGroup : cidsUser.getPotentialUserGroups()) {
                if (role.equals(cidsUserGroup.getName())) {
                    cidsUser.setUserGroup(cidsUserGroup);
                    break;
                }
            }

            if (cidsUser.getUserGroup() == null) {
                log.warn("role '" + role + "' of user '" + cidsUser.getName() + "@" + cidsUser.getDomain()
                            + "' found in "
                            + cidsUser.getPotentialUserGroups().size()
                            + " potential usergroups, usergroups set to null -> "
                            + "user is acting with all " + cidsUser.getPotentialUserGroups().size()
                            + " potential usergroups!");
                cidsUser.setUserGroup(null);
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
            if (log.isDebugEnabled()) {
                log.debug("need to fill the class name cache for domain '" + cidsUser.getDomain()
                            + "' to loockup class with legacy ids.");
            }

            final Sirius.server.newuser.User legacyUser = this.getCidsUser(cidsUser, null);
            final MetaClass[] metaClasses;
            try {
                metaClasses = LegacyCoreBackend.getInstance().getService()
                            .getClasses(
                                    legacyUser,
                                    cidsUser.getDomain(),
                                    LegacyCoreBackend.getInstance().getConnectionContext());
            } catch (RemoteException ex) {
                log.error(ex.getMessage(), ex);
                throw new RuntimeException(ex.getMessage(), ex);
            }

            if (metaClasses != null) {
                this.classNameCache.fillCache(cidsUser.getDomain(), metaClasses);
                // TODO: report cached classes per domain
                StatusHolder.getInstance().putStatus("cachedClasses", String.valueOf(metaClasses.length));
            } else {
                final String message = "cannot lookup class name for class with id '"
                            + "' and fill class name cache: no classes found at domain '" + domain
                            + "' for user '" + cidsUser.getUser() + "'";
                log.error(message);
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
            if (log.isDebugEnabled()) {
                log.debug("need to fill the class name cache for domain '" + cidsUser.getDomain()
                            + "' to lookup class with legacy name '" + className + "'");
            }

            final Sirius.server.newuser.User legacyUser = this.getCidsUser(cidsUser, null);
            final MetaClass[] metaClasses = LegacyCoreBackend.getInstance()
                        .getService()
                        .getClasses(legacyUser, cidsUser.getDomain(), getConnectionContext());

            if (metaClasses != null) {
                this.classNameCache.fillCache(cidsUser.getDomain(), metaClasses);
            } else {
                final String message = "cannot lookup class id for class with name '"
                            + "' and fill class name cache: no classes found at domain '" + cidsUser.getDomain()
                            + "' for user '" + cidsUser.getUser() + "'";
                log.error(message);
                throw new Exception(message);
            }
        }

        return this.classNameCache.getClassIdForClassName(
                cidsUser.getDomain(),
                className);
    }

    /**
     * DOCUMENT ME!
     *
     * @param   baseIconString  DOCUMENT ME!
     * @param   iconType        DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  CidsServerException  DOCUMENT ME!
     */
    public byte[] getNodeIcon(final String baseIconString, final CidsNode.IconType iconType) {
        if ((baseIconString != null) && !baseIconString.isEmpty() && (baseIconString.lastIndexOf(".") != -1)) {
            final String iconString;

            if (iconType != null) {
                iconString = baseIconString.substring(0, baseIconString.lastIndexOf(".")) + iconType.getKey()
                            + baseIconString.substring(baseIconString.lastIndexOf("."));
            } else {
                iconString = baseIconString;
            }
            if (log.isDebugEnabled()) {
                log.debug("searching for icon '" + iconString + "'");
            }
            if (this.nodeIconCache.containsKey(iconString)) {
                return this.nodeIconCache.get(iconString);
            }

            final URL iconUrl = this.getClass().getResource(iconString);
            if (iconUrl != null) {
                try {
                    final BufferedImage iconImage = ImageIO.read(iconUrl);

                    if (iconImage != null) {
                        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        ImageIO.write(iconImage, "png", bos);
                        bos.flush();
                        bos.close();
                        final byte[] icon = bos.toByteArray();

                        this.nodeIconCache.put(iconString, icon);
                        return icon;
                    }
                } catch (IOException ex) {
                    final String message = "Could not load icon '" + iconString + "': "
                                + ex.getMessage();
                    log.error(message, ex);
                    throw new CidsServerException(message, ex);
                }
            } else if (iconType != null) {
                // use the base icon instead!
                final byte[] icon = this.getNodeIcon(baseIconString, null);

                // cache the (base) icon also if it is null!
                this.nodeIconCache.put(iconString, icon);
                return icon;
            } else {
                // cache the base icon also if it is null to avoid unecessary lookups
                this.nodeIconCache.put(iconString, null);
            }
        } else {
            log.warn("invalid base icon string: '" + baseIconString + "'");
        }
        return null;
    }

    /**
     * Recursively applies the update status of a CidsBeans MetaObject and all descendant MetaObject Attributes
     * according to the following rules:
     *
     * <p>If the id of the CidsBean / MetObject is -1, the status of the MetObject ist set to MetaObject.NEW. Otherwise,
     * the status is the to MetaObject.MODIFIED if the setChanged parameter is true.</p>
     *
     * @param  cidsBean    DOCUMENT ME!
     * @param  setChanged  Apply MetaObject.MODIFIED to all metaObject Attributes
     */
    public void applyCidsBeanUpdateStatus(final CidsBean cidsBean, final boolean setChanged) {
        if (cidsBean.getPrimaryKeyValue() == -1) {
            if (log.isDebugEnabled()) {
                log.debug("applying update status to NEW CidsBean '"
                            + cidsBean.getCidsBeanInfo().getJsonObjectKey() + "' (setChanged: " + setChanged + ")");
            }
        } else {
            if (cidsBean.getPrimaryKeyValue() == -1) {
                if (log.isDebugEnabled()) {
                    log.debug("applying update status to UPDATED CidsBean '"
                                + cidsBean.getCidsBeanInfo().getJsonObjectKey() + "' (setChanged: " + setChanged + ")");
                }
            }
        }

        this.applyMetaObjectUpdateStatus(cidsBean.getMetaObject(), setChanged);
    }

    /**
     * DOCUMENT ME!
     *
     * @param  metaObject  DOCUMENT ME!
     * @param  setChanged  DOCUMENT ME!
     */
    private void applyMetaObjectUpdateStatus(final MetaObject metaObject, final boolean setChanged) {
        if (metaObject.getID() == -1) {
            metaObject.setStatus(MetaObject.NEW);
        } else if (setChanged) {
            metaObject.setStatus(MetaObject.MODIFIED);
        }

        for (final ObjectAttribute objectAttribute : metaObject.getAttribs()) {
            if (objectAttribute.referencesObject() && (objectAttribute.getValue() != null)) {
                final MetaObject attributeMetaObject = (MetaObject)objectAttribute.getValue();
                attributeMetaObject.setChanged(true);
                this.applyMetaObjectUpdateStatus(attributeMetaObject, setChanged);
            }
        }
    }

    @Override
    public ConnectionContext getConnectionContext() {
        return connectionContext;
    }
}
