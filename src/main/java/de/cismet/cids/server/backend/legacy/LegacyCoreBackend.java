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

import Sirius.server.localserver.attribute.ClassAttribute;
import Sirius.server.localserver.attribute.MemberAttributeInfo;
import Sirius.server.middleware.types.MetaClass;
import Sirius.server.newuser.UserGroup;

import org.openide.util.Lookup;

import org.reflections.ReflectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.cismet.cids.server.CallServerService;
import de.cismet.cids.server.actions.ServerAction;
import de.cismet.cids.server.api.types.User;
import de.cismet.cids.server.cores.legacy.LegacyCidsServerCore;
import de.cismet.cids.server.data.configkeys.AttributeConfig;
import de.cismet.cids.server.data.configkeys.CidsAttributeConfigurationFlagKey;
import de.cismet.cids.server.data.configkeys.CidsAttributeConfigurationKey;
import de.cismet.cids.server.data.configkeys.CidsClassConfigurationFlagKey;
import de.cismet.cids.server.data.configkeys.CidsClassConfigurationKey;
import de.cismet.cids.server.data.configkeys.ClassConfig;
import de.cismet.cids.server.data.legacy.CidsAttribute;
import de.cismet.cids.server.data.legacy.CidsClass;
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
     * @param   metaClass  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public CidsClass createCidsClass(final MetaClass metaClass) {
        final CidsClass cidsClass = new CidsClass((String)metaClass.getKey(), metaClass.getDomain());

        for (final MemberAttributeInfo mai
                    : (Collection<MemberAttributeInfo>)metaClass.getMemberAttributeInfos().values()) {
            try {
                final CidsAttribute cidsAttribute = new CidsAttribute((String)mai.getKey(), (String)metaClass.getKey());

                // KEY
                setAttributeConfig(cidsAttribute, AttributeConfig.Key.NAME, mai.getName());
                setAttributeConfig(cidsAttribute, AttributeConfig.Key.DEFAULT_VALUE, mai.getDefaultValue());
                setAttributeConfig(cidsAttribute, AttributeConfig.Key.ARRAY_KEY_FIELD_NAME, mai.getArrayKeyFieldName());
                setAttributeConfig(cidsAttribute, AttributeConfig.Key.JAVACLASS_NAME, mai.getJavaclassname());
                setAttributeConfig(cidsAttribute, AttributeConfig.Key.POSITION, mai.getPosition());
                setAttributeConfig(cidsAttribute, AttributeConfig.Key.REFERENCE_TYPE, mai.getTypeId());

                // XPKey
                setAttributeConfig(cidsAttribute, AttributeConfig.XPKey.COMPLEX_EDITOR_XP, mai.getComplexEditor());
                setAttributeConfig(cidsAttribute, AttributeConfig.XPKey.EDITOR_XP, mai.getEditor());
                setAttributeConfig(cidsAttribute, AttributeConfig.XPKey.FROM_STRING_XP, mai.getFromString());
                setAttributeConfig(cidsAttribute, AttributeConfig.XPKey.RENDERER_XP, mai.getRenderer());
                setAttributeConfig(cidsAttribute, AttributeConfig.XPKey.TO_STRING_XP, mai.getToString());

                // FLAGKEY
                setAttributeFlag(cidsAttribute, AttributeConfig.FlagKey.ARRAY, mai.isArray());
                setAttributeFlag(
                    cidsAttribute,
                    AttributeConfig.FlagKey.EXTENSION_ATTRIBUTE,
                    mai.isExtensionAttribute());
                setAttributeFlag(cidsAttribute, AttributeConfig.FlagKey.FOREIGN_KEY, mai.isForeignKey());
                setAttributeFlag(cidsAttribute, AttributeConfig.FlagKey.INDEXED, mai.isIndexed());
                setAttributeFlag(cidsAttribute, AttributeConfig.FlagKey.OPTIONAL, mai.isOptional());
                setAttributeFlag(cidsAttribute, AttributeConfig.FlagKey.VIRTUAL, mai.isVirtual());
                setAttributeFlag(cidsAttribute, AttributeConfig.FlagKey.VISIBLE, mai.isVisible());

                cidsClass.putAttribute(cidsAttribute);
            } catch (final Exception ex) {
                log.error(ex, ex);
            }
        }

        final Collection<ClassAttribute> metaClassAttributes = (Collection<ClassAttribute>)metaClass.getAttributes();
        final Map<String, ClassAttribute> caMap = new HashMap<String, ClassAttribute>();
        for (final ClassAttribute metaClassAttribute : metaClassAttributes) {
            caMap.put(metaClassAttribute.getName(), metaClassAttribute);
        }

        // KEY
        setClassConfig(cidsClass, ClassConfig.Key.ATTRIBUTE_POLICY, metaClass.getAttributePolicy());
        setClassConfig(cidsClass, ClassConfig.Key.CLASS_ICON, metaClass.getIcon());
        setClassConfig(cidsClass, ClassConfig.Key.NAME, metaClass.getName());
        setClassConfig(cidsClass, ClassConfig.Key.PK_FIELD, metaClass.getPrimaryKey());
        setClassConfig(cidsClass, ClassConfig.Key.POLICY, metaClass.getPolicy());
        setClassConfig(cidsClass, ClassConfig.Key.OBJECT_ICON, metaClass.getObjectIcon());
        setClassConfig(cidsClass, ClassConfig.Key.FEATURE_BG, caMap.remove("FEATURE_BG"));
        setClassConfig(cidsClass, ClassConfig.Key.FEATURE_FG, caMap.remove("FEATURE_FG"));
        setClassConfig(
            cidsClass,
            ClassConfig.Key.FEATURE_POINT_SYMBOL,
            caMap.remove("FEATURE_POINT_SYMBOL"));
        setClassConfig(
            cidsClass,
            ClassConfig.Key.FEATURE_POINT_SYMBOL_SWEETSPOT_X,
            caMap.remove("FEATURE_POINT_SYMBOL_SWEETSPOT_X"));
        setClassConfig(
            cidsClass,
            ClassConfig.Key.FEATURE_POINT_SYMBOL_SWEETSPOT_Y,
            caMap.remove("FEATURE_POINT_SYMBOL_SWEETSPOT_Y"));

        // XPKEY
        setClassConfig(
            cidsClass,
            ClassConfig.XPKey.AGGREGATION_RENDERER_XP,
            caMap.remove("AGGREGATION_RENDERER"));
        setClassConfig(cidsClass, ClassConfig.XPKey.EDITOR_XP, metaClass.getEditor());
        setClassConfig(
            cidsClass,
            ClassConfig.XPKey.FEATURE_RENDERER_XP,
            caMap.remove("FEATURE_RENDERER"));
//        setClassConfig(cidsClass, ClassConfig.XPKey.FROM_STRING_XP, );
        setClassConfig(cidsClass, ClassConfig.XPKey.ICON_FACTORY_XP, caMap.remove("ICON_FACTORY"));
        setClassConfig(cidsClass, ClassConfig.XPKey.RENDERER_XP, metaClass.getRenderer());
        setClassConfig(cidsClass, ClassConfig.XPKey.TO_STRING_XP, metaClass.getToString());
        setClassConfig(
            cidsClass,
            ClassConfig.FeatureSupportingrasterServiceKey.FEATURE_SUPPORTING_RASTER_SERVICE_ID_ATTRIBUTE,
            caMap.remove("FEATURE_SUPPORTING_RASTER_SERVICE_ID_ATTRIBUTE"));
        setClassConfig(
            cidsClass,
            ClassConfig.FeatureSupportingrasterServiceKey.FEATURE_SUPPORTING_RASTER_SERVICE_LAYER,
            caMap.remove("FEATURE_SUPPORTING_RASTER_SERVICE_LAYER"));
        setClassConfig(
            cidsClass,
            ClassConfig.FeatureSupportingrasterServiceKey.FEATURE_SUPPORTING_RASTER_SERVICE_NAME,
            caMap.remove("FEATURE_SUPPORTING_RASTER_SERVICE_NAME"));
        setClassConfig(
            cidsClass,
            ClassConfig.FeatureSupportingrasterServiceKey.FEATURE_SUPPORTING_RASTER_SERVICE_SIMPLE_URL,
            caMap.remove("FEATURE_SUPPORTING_RASTER_SERVICE_SIMPLE_URL"));
        setClassConfig(
            cidsClass,
            ClassConfig.FeatureSupportingrasterServiceKey.FEATURE_SUPPORTING_RASTER_SERVICE_SUPPORT_XP,
            caMap.remove("FEATURE_SUPPORTING_RASTER_SERVICE_SUPPORT_XP"));

        // FLAGKEY
        setClassFlag(cidsClass, ClassConfig.FlagKey.ARRAY_LINK, metaClass.isArrayElementLink());
        setClassFlag(cidsClass, ClassConfig.FlagKey.HIDE_FEATURE, caMap.remove("HIDE_FEATURE") != null);
        setClassFlag(cidsClass, ClassConfig.FlagKey.INDEXED, metaClass.isIndexed());
        setClassFlag(
            cidsClass,
            ClassConfig.FlagKey.REASONABLE_FEW,
            caMap.remove("REASONABLE_FEW")
                    != null);

        for (final String caName : caMap.keySet()) {
            final ClassAttribute otherAttribute = caMap.get(caName);
            cidsClass.setOtherConfigAttribute(caName, otherAttribute);
        }

        return cidsClass;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  cidsClass  DOCUMENT ME!
     * @param  key        DOCUMENT ME!
     * @param  value      DOCUMENT ME!
     */
    private void setClassConfig(final CidsClass cidsClass, final CidsClassConfigurationKey key, final Object value) {
        if ((cidsClass != null) && (key != null) && (value != null)) {
            cidsClass.setConfigAttribute(key, value);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param  cidsClass  DOCUMENT ME!
     * @param  flagKey    DOCUMENT ME!
     * @param  isSet      DOCUMENT ME!
     */
    private void setClassFlag(final CidsClass cidsClass,
            final CidsClassConfigurationFlagKey flagKey,
            final boolean isSet) {
        if ((cidsClass != null) && (flagKey != null) && isSet) {
            cidsClass.setConfigFlag(flagKey);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param  cidsAttribute  DOCUMENT ME!
     * @param  key            DOCUMENT ME!
     * @param  value          DOCUMENT ME!
     */
    private void setAttributeConfig(final CidsAttribute cidsAttribute,
            final CidsAttributeConfigurationKey key,
            final Object value) {
        if ((cidsAttribute != null) && (key != null) && (value != null)) {
            cidsAttribute.setConfigAttribute(key, value);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param  cidsAttribute  DOCUMENT ME!
     * @param  flagKey        DOCUMENT ME!
     * @param  isSet          DOCUMENT ME!
     */
    private void setAttributeFlag(final CidsAttribute cidsAttribute,
            final CidsAttributeConfigurationFlagKey flagKey,
            final boolean isSet) {
        if ((cidsAttribute != null) && (flagKey != null) && isSet) {
            cidsAttribute.setConfigFlag(flagKey);
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
