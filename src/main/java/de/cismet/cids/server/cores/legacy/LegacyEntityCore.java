/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cids.server.cores.legacy;

import Sirius.server.middleware.types.MetaClass;
import Sirius.server.middleware.types.MetaObject;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.vividsolutions.jts.geom.Geometry;

import lombok.NonNull;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.lookup.ServiceProvider;

import java.sql.Date;

import java.util.ArrayList;
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
    public List<ObjectNode> getAllObjects(@NonNull final User user,
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
        if (!user.isValidated()) {
            throw new InvalidUserException("user is not validated");   // NOI18N
        }
        if (classKey.isEmpty()) {
            throw new InvalidClassKeyException("class key is empty");  // NOI18N
        }
        if (role.isEmpty()) {
            throw new InvalidRoleException("role is empty");           // NOI18N
        }

        try {
            final List<ObjectNode> all = new ArrayList<ObjectNode>();

            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final String domain = LegacyCoreBackend.getInstance().getDomainForClasskey(classKey);
            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClasskey(classKey, cidsUser);

            final String query = "SELECT " + metaClass.getID() + ", " + metaClass.getTableName() + "."
                        + metaClass.getPrimaryKey() + " FROM " + metaClass.getTableName() + " ORDER BY " + metaClass.getTableName() + "."
                        + metaClass.getPrimaryKey() + " ASC LIMIT " + limit + " OFFSET " + offset;
            final MetaObject[] metaObjects = LegacyCoreBackend.getInstance()
                        .getService()
                        .getMetaObject(cidsUser, query, domain);

            if (metaObjects != null) {
                for (final MetaObject metaObject : metaObjects) {
                    metaObject.setAllClasses();
                    final ObjectNode node = (ObjectNode)MAPPER.reader()
                                .readTree(metaObject.getBean().toJSONString(deduplicate));
                    all.add(node);
    }
            }
            return all;
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param   sourceBean  DOCUMENT ME!
     * @param   targetBean  DOCUMENT ME!
     *
     * @throws  Exception  DOCUMENT ME!
     */
    public static void deepcopyAllProperties(final CidsBean sourceBean, final CidsBean targetBean) throws Exception {
        if ((sourceBean == null) || (targetBean == null)) {
            return;
        }

        for (final String propName : sourceBean.getPropertyNames()) {
            final Object o = sourceBean.getProperty(propName);

            if (propName.toLowerCase().equals("id")) {
                final int id = (Integer)sourceBean.getProperty("id");
                targetBean.setProperty("id", id);
                targetBean.getMetaObject().setID(id);
            } else if (o instanceof CidsBean) {
                targetBean.setProperty(propName, deepcloneCidsBean((CidsBean)o));
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
        deepcopyAllProperties(cidsBean, cloneBean);
        return cloneBean;
    }

    @Override
    public ObjectNode updateObject(final User user,
            final String classKey,
            final String objectId,
            final ObjectNode jsonObject,
            final String role,
            final boolean requestResultingInstance) {
        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final String domain = LegacyCoreBackend.getInstance().getDomainForClasskey(classKey);
            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClasskey(classKey, cidsUser);

            final int cid = metaClass.getId();
            final int oid = Integer.parseInt(objectId);
            final CidsBean beanToUpdate = LegacyCoreBackend.getInstance()
                        .getService()
                        .getMetaObject(cidsUser, oid, cid, domain)
                        .getBean();
            final CidsBean beanNew = CidsBean.createNewCidsBeanFromJSON(false, jsonObject.toString());
            deepcopyAllProperties(beanNew, beanToUpdate);
            final CidsBean updatedBean = beanToUpdate.persist(LegacyCoreBackend.getInstance().getService(),
                    cidsUser,
                    domain);
            final ObjectNode node = (ObjectNode)MAPPER.reader().readTree(updatedBean.toJSONString(true));
            return node;
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            return null;
        }
    }

    @Override
    public ObjectNode createObject(@NonNull final User user,
            @NonNull final String classKey,
            @NonNull final ObjectNode jsonObject,
            @NonNull final String role,
            final boolean requestResultingInstance) {
        if (!user.isValidated()) {
            throw new InvalidUserException("user is not validated");  // NOI18N
        }
        if (classKey.isEmpty()) {
            throw new InvalidClassKeyException("class key is empty"); // NOI18N
        }
        if (role.isEmpty()) {
            throw new InvalidRoleException("role is empty");          // NOI18N
        }
        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final String domain = LegacyCoreBackend.getInstance().getDomainForClasskey(classKey);

            final CidsBean beanNew = CidsBean.createNewCidsBeanFromJSON(false, jsonObject.toString());
            final CidsBean updatedBean = beanNew.persist(LegacyCoreBackend.getInstance().getService(),
                    cidsUser,
                    domain);
            final ObjectNode node = (ObjectNode)MAPPER.reader().readTree(updatedBean.toJSONString(true));
            return node;
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            return null;
        }
    }

    @Override
    public ObjectNode getObjectsByQuery(final User user,
            final SimpleObjectQuery query,
            final String role,
            final int limit,
            final int offset) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
    }

    @Override
    public ObjectNode getObject(@NonNull final User user,
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
        if (!user.isValidated()) {
            throw new InvalidUserException("user is not validated");  // NOI18N
        }
        if (classKey.isEmpty()) {
            throw new InvalidClassKeyException("class key is empty"); // NOI18N
        }
        if (objectId.isEmpty()) {
            throw new IllegalArgumentException("objectId is empty");  // NOI18N
        }
        if (role.isEmpty()) {
            throw new InvalidRoleException("role is empty");          // NOI18N
        }
        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final String domain = LegacyCoreBackend.getInstance().getDomainForClasskey(classKey);
            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClasskey(classKey, cidsUser);

            final int cid = metaClass.getId();
            final MetaObject metaObject = LegacyCoreBackend.getInstance()
                        .getService()
                        .getMetaObject(
                            cidsUser,
                            Integer.parseInt(objectId),
                            cid,
                            domain);
            if (metaObject != null) {
                final ObjectNode node = (ObjectNode)MAPPER.reader()
                            .readTree(metaObject.getBean().toJSONString(deduplicate));
                return node;
            }
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
        }
        return null;
    }

    @Override
    public boolean deleteObject(@NonNull final User user,
            @NonNull final String classKey,
            @NonNull final String objectId,
            @NonNull final String role) {
        if (!user.isValidated()) {
            throw new InvalidUserException("user is not validated");  // NOI18N
        }
        if (classKey.isEmpty()) {
            throw new InvalidClassKeyException("class key is empty"); // NOI18N
        }
        if (objectId.isEmpty()) {
            throw new IllegalArgumentException("objectId is empty");  // NOI18N
        }
        if (role.isEmpty()) {
            throw new InvalidRoleException("role is empty");          // NOI18N
        }

        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final String domain = LegacyCoreBackend.getInstance().getDomainForClasskey(classKey);
            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClasskey(classKey, cidsUser);

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
            log.error(ex.getMessage(), ex);
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
    public String getClassKey(final ObjectNode jsonObject) {
        if (jsonObject.hasNonNull("$self")) {
            final Matcher matcher = CLASSKEY_PATTERN.matcher(jsonObject.get("$self").asText());
            if (matcher.find()) {
                return matcher.group(1);
            } else {
                throw new Error("Object with malformed self reference: " + jsonObject.get("$self"));
            }
        } else if (jsonObject.hasNonNull("$ref")) {
            final Matcher matcher = CLASSKEY_PATTERN.matcher(jsonObject.get("$ref").asText());
            if (matcher.find()) {
                return matcher.group(1);
            } else {
                throw new Error("Object with malformed reference: " + jsonObject.get("$ref"));
            }
        } else {
            throw new Error("Object without (self) reference is invalid!");
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
    public String getObjectId(final ObjectNode jsonObject) {
        if (jsonObject.hasNonNull("id")) {
            return jsonObject.get("id").asText();
        } else if (jsonObject.hasNonNull("$self")) {
            final Matcher matcher = OBJECTID_PATTERN.matcher(jsonObject.get("$self").asText());
            if (matcher.find()) {
                return matcher.group(1);
            } else {
                throw new Error("Object with malformed self reference: " + jsonObject.get("$ref"));
            }
        } else if (jsonObject.hasNonNull("$ref")) {
            final Matcher matcher = OBJECTID_PATTERN.matcher(jsonObject.get("$ref").asText());
            if (matcher.find()) {
                return matcher.group(1);
            } else {
                throw new Error("Object with malformed reference: " + jsonObject.get("$ref"));
            }
        }
        {
            return "-1";
        }
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.entity"; // NOI18N
    }
}
