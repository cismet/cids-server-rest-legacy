/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cidsx.server.cores.legacy;

import Sirius.server.localserver.attribute.Attribute;
import Sirius.server.middleware.types.MetaClass;
import Sirius.server.middleware.types.MetaObject;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.lookup.ServiceProvider;

import de.cismet.cidsx.server.api.types.User;
import de.cismet.cidsx.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cidsx.server.cores.CidsServerCore;
import de.cismet.cidsx.server.cores.PermissionCore;
import de.cismet.cidsx.server.data.RuntimeContainer;

/**
 * DOCUMENT ME!
 *
 * @author   thorsten
 * @version  1.0
 */
@Slf4j
@ServiceProvider(service = CidsServerCore.class)
public class LegacyPermissionCore implements PermissionCore {

    //~ Methods ----------------------------------------------------------------

    @Override
    public boolean hasClassReadPermission(final User user, final String role, final String classKey) {
        if (log.isDebugEnabled()) {
            log.debug("hasClassReadPermission with classKey '" + classKey + "'.");
        }

        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClassname(classKey, cidsUser);
            if (metaClass == null) {
                throw new RuntimeException("classKey " + classKey + " no found");
            }

            return metaClass.getPermissions().hasReadPermission(cidsUser);
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
        } // Tools | Templates.

        return false;
    }

    @Override
    public boolean hasClassWritePermission(final User user, final String role, final String classKey) {
        if (log.isDebugEnabled()) {
            log.debug("hasClassWritePermission with classKey '" + classKey + "'.");
        }

        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClassname(classKey, cidsUser);
            if (metaClass == null) {
                throw new RuntimeException("classKey " + classKey + " no found");
            }

            return metaClass.getPermissions().hasWritePermission(cidsUser);
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
        } // Tools | Templates.

        return false;
    }

    @Override
    public boolean isCustomObjectPermissionEnabled(final String classKey) {
        if (log.isDebugEnabled()) {
            log.debug("isCustomObjectPermissionEnabled with classKey '" + classKey + "'.");
        }

        // TODO
        return true;
    }

    @Override
    public boolean hasObjectReadPermission(final User user,
            final String role,
            final String classKey,
            final String objectKey) {
        if (log.isDebugEnabled()) {
            log.debug("hasObjectReadPermission with classKey '" + classKey
                        + "' and objectKey '" + objectKey + "'.");
        }

        // Tools | Templates.
        // TODO
        return false;
    }

    @Override
    public boolean hasObjectWritePermission(final User user,
            final String role,
            final String classKey,
            final String objectKey) {
        if (log.isDebugEnabled()) {
            log.debug("hasObjectWritePermission with classKey '" + classKey
                        + "' and objectKey '" + objectKey + "'.");
        }

        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final String domain = RuntimeContainer.getServer().getDomainName();
            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClassname(classKey, cidsUser);
            if (metaClass == null) {
                throw new RuntimeException("classKey " + classKey + " no found");
            }

            final int cid = metaClass.getId();
            final int oid = Integer.parseInt(objectKey);
            final MetaObject metaObject = LegacyCoreBackend.getInstance()
                        .getService()
                        .getMetaObject(cidsUser, oid, cid, domain);
            return metaObject.hasObjectWritePermission(cidsUser);
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
        } // Tools | Templates.

        return false;
    }

    @Override
    public boolean hasAttributeReadPermission(final User user,
            final String role,
            final String classKey,
            final String attributeKey) {
        if (log.isDebugEnabled()) {
            log.debug("hasAttributeReadPermission with classKey '" + classKey
                        + "' and attributte key '" + attributeKey + "'.");
        }

        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClassname(classKey, cidsUser);
            if (metaClass == null) {
                throw new RuntimeException("classKey " + classKey + " no found");
            }

            return ((Attribute)metaClass.getAttributeByName(attributeKey)).getPermissions().hasReadPermission(cidsUser);
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
        } // Tools | Templates.

        return false;
    }

    @Override
    public boolean hasAttributeWritePermission(final User user,
            final String role,
            final String classKey,
            final String attributeKey) {
        if (log.isDebugEnabled()) {
            log.debug("hasAttributeWritePermission with classKey '" + classKey
                        + "' and attributte key '" + attributeKey + "'.");
        }

        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClassname(classKey, cidsUser);
            if (metaClass == null) {
                throw new RuntimeException("classKey " + classKey + " no found");
            }

            return ((Attribute)metaClass.getAttributeByName(attributeKey)).getPermissions()
                        .hasWritePermission(cidsUser);
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
        } // Tools | Templates.

        return false;
    }

    @Override
    public boolean hasNodeReadPermission(final User user, final String role, final String nodeKey) {
        if (log.isDebugEnabled()) {
            log.debug("hasNodeReadPermission with nodeKey '" + nodeKey + "'.");
        }

        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final Sirius.server.middleware.types.Node cidsNode = LegacyCoreBackend.getInstance()
                        .getService()
                        .getMetaObjectNode(cidsUser, Integer.parseInt(nodeKey), user.getDomain());

            return cidsNode.getPermissions().hasReadPermission(cidsUser);
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
        } // Tools | Templates.

        return false;
    }

    @Override
    public boolean hasNodeWritePermission(final User user, final String role, final String nodeKey) {
        if (log.isDebugEnabled()) {
            log.debug("hasNodeWritePermission with nodeKey '" + nodeKey + "'.");
        }

        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final Sirius.server.middleware.types.Node cidsNode = LegacyCoreBackend.getInstance()
                        .getService()
                        .getMetaObjectNode(cidsUser, Integer.parseInt(nodeKey), user.getDomain());

            return cidsNode.getPermissions().hasWritePermission(cidsUser);
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
        } // Tools | Templates.

        return false;
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.permission"; // NOI18N
    }
}