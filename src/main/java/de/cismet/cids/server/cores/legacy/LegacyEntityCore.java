/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cids.server.cores.legacy;

import Sirius.server.middleware.types.LightweightMetaObject;
import Sirius.server.middleware.types.MetaClass;
import Sirius.server.middleware.types.MetaObject;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.vividsolutions.jts.geom.Geometry;

import lombok.NonNull;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.lookup.ServiceProvider;

import java.sql.Date;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.cismet.cids.dynamics.CidsBean;

import de.cismet.cids.server.api.types.SimpleObjectQuery;
import de.cismet.cids.server.api.types.User;
import de.cismet.cids.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cids.server.cores.CidsServerCore;
import de.cismet.cids.server.cores.EntityCore;
import de.cismet.cids.server.data.RuntimeContainer;
import de.cismet.cids.server.exceptions.InvalidClassKeyException;
import de.cismet.cids.server.exceptions.InvalidRoleException;
import de.cismet.cids.server.exceptions.InvalidUserException;

/**
 * DOCUMENT ME!
 *
 * @author   thorsten
 * @version  1.0
 */
@Slf4j
@ServiceProvider(service = CidsServerCore.class)
public class LegacyEntityCore implements EntityCore {

    //~ Static fields/initializers ---------------------------------------------

    protected static final ObjectMapper MAPPER = new ObjectMapper(new JsonFactory());
    private static final Pattern CLASSKEY_PATTERN = Pattern.compile("^/([^/]*)/");
    private static final Pattern OBJECTID_PATTERN = Pattern.compile("([^/?]+)(?=/?(?:$|\\?))");

    //~ Methods ----------------------------------------------------------------

    @Override
    public List<JsonNode> getAllObjects(@NonNull final User user,
            @NonNull final String classKey,
            @NonNull final String role,
            final int limit,
            final int offset,
            final String expand,
            final String level,
            final String fields,
            final String profile,
            final String filter,
            final boolean omitNullValues,
            final boolean deduplicate) {
        if (log.isDebugEnabled()) {
            log.debug("getAllObjects with classKey '" + classKey + "'.");
        }

        if (!user.isValidated()) {
            final String message = "error while getting all objects with classKey '"
                        + classKey + "': user '" + user.getUser() + "' is not validated!";
            log.error(message);
            throw new InvalidUserException(message);     // NOI18N
        }
        if (classKey.isEmpty()) {
            final String message = "error while getting all objects with classKey '"
                        + classKey + "': classKey is empty!";
            log.error(message);
            throw new InvalidClassKeyException(message); // NOI18N
        }
        if (role.isEmpty()) {
            final String message = "error while getting all objects with classKey '"
                        + classKey + "': role is empty!";
            log.error(message);
            throw new InvalidRoleException(message);     // NOI18N
        }

        try {
            final List<JsonNode> all = new ArrayList<JsonNode>();

            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final String domain = RuntimeContainer.getServer().getDomainName();
            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClassname(classKey, cidsUser);
            if (metaClass == null) {
                throw new RuntimeException("classKey " + classKey + " not found");
            }

            final String query = "SELECT " + metaClass.getID() + ", " + metaClass.getTableName() + "."
                        + metaClass.getPrimaryKey() + " FROM " + metaClass.getTableName() + " ORDER BY "
                        + metaClass.getTableName() + "."
                        + metaClass.getPrimaryKey() + " ASC LIMIT " + limit + " OFFSET " + offset;

            if ("0".equals(level)) {
                if (log.isDebugEnabled()) {
                    log.debug(
                        "getAllObjects: level is 0, requesting creating LightweightMetaObjects without any attributes");
                }

                if ((fields != null) || (expand != null)) {
                    log.warn("getAllObjects: level is 0, ignoring fields '" + fields + "' and expand '"
                                + expand + "' parameters!");
                }

                final LightweightMetaObject[] lwmos = LegacyCoreBackend.getInstance()
                            .getService()
                            .getLightweightMetaObjectsByQuery(metaClass.getId(), cidsUser, query, new String[0]);
                for (final LightweightMetaObject lwmo : lwmos) {
                    final String selfString = "{\"$self\":\"/" + metaClass.getDomain() + "." + metaClass.getTableName()
                                + "/" + lwmo.getId() + "\"}";
                    final JsonNode node = MAPPER.reader().readTree(selfString);
                    all.add(node);
                }
            } else {
                final MetaObject[] metaObjects = LegacyCoreBackend.getInstance()
                            .getService()
                            .getMetaObject(cidsUser, query, domain);

                if (metaObjects != null) {
                    for (final MetaObject metaObject : metaObjects) {
                        metaObject.setAllClasses();
                        int intLevel = -1;
                        try {
                            intLevel = Integer.parseInt(level);
                        } catch (final Exception ex) {
                            // could not be cast, ignore (intLevel = -1)
                        }
                        final List<String> fieldsList = (fields != null) ? Arrays.asList(fields.split(",")) : null;
                        final List<String> expandList = (expand != null) ? Arrays.asList(expand.split(",")) : null;

                        final JsonNode node = MAPPER.reader()
                                    .readTree(metaObject.getBean().toJSONString(
                                            deduplicate,
                                            omitNullValues,
                                            intLevel,
                                            fieldsList,
                                            expandList));
                        all.add(node);
                    }
                }
            }
            return all;
        } catch (final Exception ex) {
            final String message = "error while getting all objects with classKey '"
                        + classKey + "': " + ex.getMessage();
            log.error(message, ex);
            throw new RuntimeException(message, ex);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param   sourceBean  DOCUMENT ME!
     * @param   targetBean  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  Exception  DOCUMENT ME!
     */
    public static CidsBean deepcopyAllProperties(final CidsBean sourceBean, final CidsBean targetBean)
            throws Exception {
        return deepcopyAllProperties(sourceBean, targetBean, true);
    }

    /**
     * DOCUMENT ME!
     *
     * @param   sourceBean  DOCUMENT ME!
     * @param   targetBean  DOCUMENT ME!
     * @param   cloneDeep   DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  Exception  DOCUMENT ME!
     */
    public static CidsBean deepcopyAllProperties(final CidsBean sourceBean,
            final CidsBean targetBean,
            final boolean cloneDeep) throws Exception {
        if ((sourceBean == null) || (targetBean == null)) {
            return null;
        }

        final String primaryKeyFieldName = sourceBean.getPrimaryKeyFieldname().toLowerCase();
        for (final String propName : sourceBean.getPropertyNames()) {
            final Object o = sourceBean.getProperty(propName);
            if (sourceBean.getMetaObject().getAttributeByFieldName(propName).isChanged()) {
                if (propName.toLowerCase().equals(primaryKeyFieldName)) {
                    final int id = (Integer)sourceBean.getProperty(primaryKeyFieldName);
                    targetBean.setProperty(primaryKeyFieldName, id);
                    targetBean.getMetaObject().setID(id);
                } else if (o instanceof CidsBean) {
                    final CidsBean sourceChild = (CidsBean)o;
                    final CidsBean targetChild;
                    if (cloneDeep) {
                        targetChild = deepcloneCidsBean(sourceChild);
                    } else if (targetBean.getProperty(propName) == null) {
                        targetChild = deepcopyAllProperties(
                                sourceChild,
                                sourceChild.getMetaObject().getMetaClass().getEmptyInstance().getBean(),
                                cloneDeep);
                    } else {
                        targetChild = deepcopyAllProperties(
                                sourceChild,
                                (CidsBean)targetBean.getProperty(propName),
                                cloneDeep);
                    }
                    targetBean.setProperty(propName, targetChild);
                } else if (o instanceof Collection) {
                    final List<CidsBean> list = (List<CidsBean>)o;
                    final List<CidsBean> newList = new ArrayList<CidsBean>();

                    for (final CidsBean tmpBean : list) {
                        newList.add(deepcloneCidsBean(tmpBean));
                    }
                    targetBean.setProperty(propName, newList);
                } else if (o instanceof Geometry) {
                    targetBean.setProperty(propName, ((Geometry)o).clone());
                } else if (o instanceof Float) {
                    targetBean.setProperty(propName, new Float(o.toString()));
                } else if (o instanceof Boolean) {
                    targetBean.setProperty(propName, new Boolean(o.toString()));
                } else if (o instanceof Long) {
                    targetBean.setProperty(propName, new Long(o.toString()));
                } else if (o instanceof Double) {
                    targetBean.setProperty(propName, new Double(o.toString()));
                } else if (o instanceof Integer) {
                    targetBean.setProperty(propName, new Integer(o.toString()));
                } else if (o instanceof Date) {
                    targetBean.setProperty(propName, ((Date)o).clone());
                } else if (o instanceof String) {
                    targetBean.setProperty(propName, o);
                } else {
                    if (o != null) {
                        log.error("unknown property type: " + o.getClass().getName());
                    }
                    targetBean.setProperty(propName, o);
                }
            }
        }

        return targetBean;
    }

    /**
     * DOCUMENT ME!
     *
     * @param   cidsBean  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  Exception  DOCUMENT ME!
     */
    public static CidsBean deepcloneCidsBean(final CidsBean cidsBean) throws Exception {
        if (cidsBean == null) {
            return null;
        }
        final CidsBean cloneBean = cidsBean.getMetaObject().getMetaClass().getEmptyInstance().getBean();
        return deepcopyAllProperties(cidsBean, cloneBean);
    }

    @Override
    public JsonNode updateObject(final User user,
            final String classKey,
            final String objectId,
            final JsonNode jsonObject,
            final String role,
            final boolean requestResultingInstance) {
        if (log.isDebugEnabled()) {
            log.debug("updateObject with classKey '" + classKey + "' and objectId '" + objectId + "'.");
        }

        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClassname(classKey, cidsUser);
            if (metaClass == null) {
                throw new RuntimeException("classKey " + classKey + " no found");
            }

            final CidsBean beanToUpdate = CidsBean.updateCidsBeanFromJSON(jsonObject.toString(), false);
//            final CidsBean updatedBean = beanToUpdate;
            final CidsBean updatedBean = beanToUpdate.persist();
//            log.error("beanToUpdate:\n" + beanToUpdate.getMOString());

            if (requestResultingInstance) {
                final JsonNode node = MAPPER.reader().readTree(updatedBean.toJSONString(true));
                return node;
            } else {
                return null;
            }
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            throw new RuntimeException("error while updating Object", ex);
        }
    }

    @Override
    public JsonNode patchObject(final User user,
            final String classKey,
            final String objectId,
            final JsonNode jsonObject,
            final String role) {
        if (log.isDebugEnabled()) {
            log.debug("patchObject with classKey '" + classKey + "' and objectId '" + objectId + "'.");
        }

        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClassname(classKey, cidsUser);
            if (metaClass == null) {
                final String message = "error while patching an object with classKey '"
                            + classKey + "' and objectId '" + objectId + "': class for class key not found!";
                log.error(message);
                throw new RuntimeException(message);
            }

            final CidsBean beanToUpdate = CidsBean.updateCidsBeanFromJSON(jsonObject.toString(), true);
//            final CidsBean updatedBean = beanToUpdate;
            final CidsBean updatedBean = beanToUpdate.persist();
//            log.error("beanToUpdate:\n" + updatedBean.getMOString());

            return null; // <-- why ???
        } catch (final Exception ex) {
            final String message = "error while updating an object with classKey '"
                        + classKey + "' and objectId '" + objectId + "': " + ex.getMessage();
            log.error(message, ex);
            throw new RuntimeException(message, ex);
        }
    }

    @Override
    public JsonNode createObject(@NonNull final User user,
            @NonNull final String classKey,
            @NonNull final JsonNode jsonObject,
            @NonNull final String role,
            final boolean requestResultingInstance) {
        if (log.isDebugEnabled()) {
            log.debug("createObject with classKey '" + classKey + "'.");
        }

        if (!user.isValidated()) {
            final String message = "error while creating an object with classKey '"
                        + classKey + "': user '" + user.getUser() + "' is not validated!";
            log.error(message);
            throw new InvalidUserException(message);     // NOI18N
        }
        if (classKey.isEmpty()) {
            final String message = "error while creating an object: class key is empty!";
            log.error(message);
            throw new InvalidClassKeyException(message); // NOI18N
        }
        if (role.isEmpty()) {
            final String message = "error while creating an object with classKey '"
                        + classKey + "': role is empty!";
            log.error(message);
            throw new InvalidRoleException(message);     // NOI18N
        }
        try {
            final CidsBean beanNew = CidsBean.createNewCidsBeanFromJSON(false, jsonObject.toString());
            beanNew.getMetaObject().setStatus(MetaObject.NEW);

            final CidsBean updatedBean = beanNew.persist();
            if (requestResultingInstance) {
                final JsonNode node = MAPPER.reader().readTree(updatedBean.toJSONString(true));
                return node;
            } else {
                return null;
            }
        } catch (final Exception ex) {
            final String message = "error while creating an object with classKey '"
                        + classKey + "': " + ex.getMessage();
            log.error(message, ex);
            throw new RuntimeException(message, ex);
        }
    }

    @Override
    public JsonNode getObjectsByQuery(final User user,
            final SimpleObjectQuery query,
            final String role,
            final int limit,
            final int offset) {
        if (log.isDebugEnabled()) {
            log.warn("getObjectsByQuery with query '" + query + "'.");
        }

        final String message = "The operation getObjectsByQuery is currently not supported";
        log.error(message);
        throw new UnsupportedOperationException(message);
    }

    @Override
    public JsonNode getObject(@NonNull final User user,
            @NonNull final String classKey,
            @NonNull final String objectId,
            final String version,
            final String expand,
            final String level,
            final String fields,
            final String profile,
            @NonNull final String role,
            final boolean omitNullValues,
            final boolean deduplicate) {
        if (log.isDebugEnabled()) {
            log.debug("getObject with classKey '" + classKey + "' and objectId '" + objectId + "'");
        }

        if (!user.isValidated()) {
            final String message = "error while getting an object with classKey '" + classKey
                        + "' and objectId '" + objectId + "': user '" + user.getUser() + "' is not validated!";
            log.error(message);
            throw new InvalidUserException(message);     // NOI18N
        }
        if (classKey.isEmpty()) {
            final String message = "error while getting an object with classKey '" + classKey
                        + "' and objectId '" + objectId + "': class key is empty!";
            log.error(message);
            throw new InvalidClassKeyException(message); // NOI18N
        }
        if (objectId.isEmpty()) {
            final String message = "error while getting an object with classKey '" + classKey
                        + "' and objectId '" + objectId + "': objectId is empty!";
            log.error(message);
            throw new IllegalArgumentException(message); // NOI18N
        }
        if (role.isEmpty()) {
            final String message = "error while getting an object with classKey '" + classKey
                        + "' and objectId '" + objectId + "': role is empty!";
            log.error(message);
            throw new InvalidRoleException(message);     // NOI18N
        }
        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final String domain = RuntimeContainer.getServer().getDomainName();
            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClassname(classKey, cidsUser);
            if (metaClass == null) {
                final String message = "error while getting an object with classKey '" + classKey
                            + "' and objectId '" + objectId + "': class for class key not found!";
                log.error(message);
                throw new RuntimeException(message);
            }

            final int cid = metaClass.getId();
            final MetaObject metaObject = LegacyCoreBackend.getInstance()
                        .getService()
                        .getMetaObject(
                            cidsUser,
                            Integer.parseInt(objectId),
                            cid,
                            domain);
            if (metaObject != null) {
                metaObject.setAllClasses();

                int intLevel = -1;
                try {
                    intLevel = Integer.parseInt(level);
                } catch (final Exception ex) {
                    // could not be cast, ignore (intLevel = -1)
                }
                final List<String> fieldsList = (fields != null) ? Arrays.asList(fields.split(",")) : null;
                final List<String> expandList = (expand != null) ? Arrays.asList(expand.split(",")) : null;

                final JsonNode node = MAPPER.reader()
                            .readTree(metaObject.getBean().toJSONString(
                                    deduplicate,
                                    omitNullValues,
                                    intLevel,
                                    fieldsList,
                                    expandList));
                return node;
            } else {
                return null;
            }
        } catch (final Exception ex) {
            final String message = "error while getting an object with classKey '" + classKey
                        + "' and objectId '" + objectId + "': " + ex.getMessage();
            log.error(message, ex);
            throw new RuntimeException(message, ex);
        }
    }

    @Override
    public boolean deleteObject(@NonNull final User user,
            @NonNull final String classKey,
            @NonNull final String objectId,
            @NonNull final String role) {
        if (log.isDebugEnabled()) {
            log.debug("deleteObject with classKey '" + classKey + "' and objectId '" + objectId + "'");
        }

        if (!user.isValidated()) {
            final String message = "error while deleting an object with classKey '"
                        + classKey + "' and objectId '" + objectId
                        + "': user '" + user.getUser() + "' is not validated!";
            log.error(message);
            throw new InvalidUserException(message);     // NOI18N
        }
        if (classKey.isEmpty()) {
            final String message = "error while deleting an object with classKey '"
                        + classKey + "' and objectId '" + objectId
                        + "': classKey is empty!";
            log.error(message);
            throw new InvalidClassKeyException(message); // NOI18N
        }
        if (objectId.isEmpty()) {
            final String message = "error while deleting an object with classKey '"
                        + classKey + "' and objectId '" + objectId
                        + "': objectId is empty!";
            log.error(message);
            throw new IllegalArgumentException(message); // NOI18N
        }
        if (role.isEmpty()) {
            final String message = "error while deleting an object with classKey '"
                        + classKey + "' and objectId '" + objectId
                        + "': role is empty!";
            log.error(message);
            throw new InvalidRoleException(message);     // NOI18N
        }

        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final String domain = RuntimeContainer.getServer().getDomainName();
            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClassname(classKey, cidsUser);
            if (metaClass == null) {
                final String message = "error while deleting an object with classKey '"
                            + classKey + "' and objectId '" + objectId
                            + "': class for classKey not found!";
                log.error(message);
                throw new RuntimeException(message);
            }

            final int cid = metaClass.getId();

            final MetaObject metaObject = LegacyCoreBackend.getInstance()
                        .getService()
                        .getMetaObject(
                            cidsUser,
                            Integer.parseInt(objectId),
                            cid,
                            domain);
            final int ret = LegacyCoreBackend.getInstance().getService().deleteMetaObject(cidsUser, metaObject, domain);
            return (ret > 100);
        } catch (final Exception ex) {
            final String message = "error while deleting an object with classKey '"
                        + classKey + "' and objectId '" + objectId + "': "
                        + ex.getMessage();
            log.error(message, ex);
            return false; // Tools | Templates.
        }
    }

    /**
     * Returns the parsed class name from the $self or $ref properties of the object or throws an error, if the
     * properties are not found or invalid.
     *
     * @param   jsonObject  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  Error  DOCUMENT ME!
     */
    @Override
    public String getClassKey(final JsonNode jsonObject) {
        if (jsonObject.hasNonNull("$self")) {
            final Matcher matcher = CLASSKEY_PATTERN.matcher(jsonObject.get("$self").asText());
            if (matcher.find()) {
                return matcher.group(1);
            } else {
                final String message = "Object with malformed $self reference: " + jsonObject.get("$self");
                log.error(message);
                throw new Error(message);
            }
        } else if (jsonObject.hasNonNull("$ref")) {
            final Matcher matcher = CLASSKEY_PATTERN.matcher(jsonObject.get("$ref").asText());
            if (matcher.find()) {
                return matcher.group(1);
            } else {
                final String message = "Object with malformed $ref reference: " + jsonObject.get("$ref");
                log.error(message);
                throw new Error(message);
            }
        } else {
            final String message = "Object without ($self or $ref) reference is invalid!";
            log.error(message);
            throw new Error(message);
        }
    }

    /**
     * Returns the value of the object property 'id' or tries to extract the id from the $self or $ref properties.
     * Returns -1 if no id is found.
     *
     * @param   jsonObject  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  Error  DOCUMENT ME!
     */
    @Override
    public String getObjectId(final JsonNode jsonObject) {
        if (jsonObject.hasNonNull("id")) {
            return jsonObject.get("id").asText();
        } else if (jsonObject.hasNonNull("$self")) {
            final Matcher matcher = OBJECTID_PATTERN.matcher(jsonObject.get("$self").asText());
            if (matcher.find()) {
                return matcher.group(1);
            } else {
                final String message = "Object with malformed $self reference: " + jsonObject.get("$self");
                log.error(message);
                throw new Error(message);
            }
        } else if (jsonObject.hasNonNull("$ref")) {
            final Matcher matcher = OBJECTID_PATTERN.matcher(jsonObject.get("$ref").asText());
            if (matcher.find()) {
                return matcher.group(1);
            } else {
                final String message = "Object with malformed $ref reference: " + jsonObject.get("$ref");
                log.error(message);
                throw new Error(message);
            }
        } else {
            log.warn("Object without id, $self or $ref! returning -1 as id");
            return "-1";
        }
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.entity"; // NOI18N
    }
}
