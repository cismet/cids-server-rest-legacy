/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cids.server.cores.legacy;

import Sirius.server.localserver.attribute.Attribute;
import Sirius.server.middleware.types.MetaClass;
import Sirius.server.middleware.types.MetaObject;

import org.openide.util.lookup.ServiceProvider;

import de.cismet.cids.server.api.types.User;
import de.cismet.cids.server.cores.CidsServerCore;
import de.cismet.cids.server.cores.PermissionCore;

/**
 * DOCUMENT ME!
 *
 * @author   thorsten
 * @version  1.0
 */
@ServiceProvider(service = CidsServerCore.class)
public class LegacyPermissionCore implements PermissionCore {

    //~ Static fields/initializers ---------------------------------------------

    private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(LegacyNodeCore.class);

    //~ Methods ----------------------------------------------------------------

    @Override
    public boolean hasClassReadPermission(final User user, final String role, final String classKey) {
        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user);
            final String[] classKeySplitted = classKey.split("@");
            final String domain = classKeySplitted[1];
            final int classId = Integer.parseInt(classKeySplitted[0]);

            final MetaClass metaClass = LegacyCoreBackend.getInstance()
                        .getService()
                        .getClass(cidsUser, classId, domain);

            return metaClass.getPermissions().hasReadPermission(cidsUser);
        } catch (final Exception ex) {
            log.error(null, ex);
        } // Tools | Templates.

        return false;
    }

    @Override
    public boolean hasClassWritePermission(final User user, final String role, final String classKey) {
        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user);
            final String[] classKeySplitted = classKey.split("@");
            final String domain = classKeySplitted[1];
            final int classId = Integer.parseInt(classKeySplitted[0]);

            final MetaClass metaClass = LegacyCoreBackend.getInstance()
                        .getService()
                        .getClass(cidsUser, classId, domain);

            return metaClass.getPermissions().hasWritePermission(cidsUser);
        } catch (final Exception ex) {
            log.error(null, ex);
        } // Tools | Templates.

        return false;
    }

    @Override
    public boolean isCustomObjectPermissionEnabled(final String classKey) {
        // TODO
        return true;
    }

    @Override
    public boolean hasObjectReadPermission(final User user,
            final String role,
            final String classKey,
            final String objectKey) {
        // Tools | Templates.
        // TODO
        return false;
    }

    @Override
    public boolean hasObjectWritePermission(final User user,
            final String role,
            final String classKey,
            final String objectKey) {
        try {
            final String[] classKeySplitted = classKey.split("@");
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user);
            final String domain = classKeySplitted[1];
            final int cid = Integer.parseInt(classKeySplitted[0]);
            final int oid = Integer.parseInt(objectKey);
            final MetaObject metaObject = LegacyCoreBackend.getInstance()
                        .getService()
                        .getMetaObject(cidsUser, oid, cid, domain);
            return metaObject.hasObjectWritePermission(cidsUser);
        } catch (final Exception ex) {
            log.error(null, ex);
        } // Tools | Templates.

        return false;
    }

    @Override
    public boolean hasAttributeReadPermission(final User user,
            final String role,
            final String classKey,
            final String attributeKey) {
        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user);
            final String[] classKeySplitted = classKey.split("@");
            final String domain = classKeySplitted[1];
            final int classId = Integer.parseInt(classKeySplitted[0]);

            final MetaClass metaClass = LegacyCoreBackend.getInstance()
                        .getService()
                        .getClass(cidsUser, classId, domain);

            return ((Attribute)metaClass.getAttributeByName(attributeKey)).getPermissions().hasReadPermission(cidsUser);
        } catch (final Exception ex) {
            log.error(null, ex);
        } // Tools | Templates.

        return false;
    }

    @Override
    public boolean hasAttributeWritePermission(final User user,
            final String role,
            final String classKey,
            final String attributeKey) {
        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user);
            final String[] classKeySplitted = classKey.split("@");
            final String domain = classKeySplitted[1];
            final int classId = Integer.parseInt(classKeySplitted[0]);

            final MetaClass metaClass = LegacyCoreBackend.getInstance()
                        .getService()
                        .getClass(cidsUser, classId, domain);

            return ((Attribute)metaClass.getAttributeByName(attributeKey)).getPermissions()
                        .hasWritePermission(cidsUser);
        } catch (final Exception ex) {
            log.error(null, ex);
        } // Tools | Templates.

        return false;
    }

    @Override
    public boolean hasNodeReadPermission(final User user, final String role, final String nodeKey) {
        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user);
            final Sirius.server.middleware.types.Node cidsNode = LegacyCoreBackend.getInstance()
                        .getService()
                        .getMetaObjectNode(cidsUser, Integer.parseInt(nodeKey), user.getDomain());

            return cidsNode.getPermissions().hasReadPermission(cidsUser);
        } catch (final Exception ex) {
            log.error(null, ex);
        } // Tools | Templates.

        return false;
    }

    @Override
    public boolean hasNodeWritePermission(final User user, final String role, final String nodeKey) {
        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user);
            final Sirius.server.middleware.types.Node cidsNode = LegacyCoreBackend.getInstance()
                        .getService()
                        .getMetaObjectNode(cidsUser, Integer.parseInt(nodeKey), user.getDomain());

            return cidsNode.getPermissions().hasWritePermission(cidsUser);
        } catch (final Exception ex) {
            log.error(null, ex);
        } // Tools | Templates.

        return false;
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.permission"; // NOI18N
    }
}
