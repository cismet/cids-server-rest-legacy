/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cids.server.cores.legacy;

import Sirius.server.middleware.types.MetaClass;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.openide.util.lookup.ServiceProvider;

import java.util.ArrayList;
import java.util.List;

import de.cismet.cids.dynamics.CidsBean;

import de.cismet.cids.server.api.types.User;
import de.cismet.cids.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cids.server.cores.CidsServerCore;
import de.cismet.cids.server.cores.EntityInfoCore;
import de.cismet.cids.server.data.legacy.CidsAttribute;
import de.cismet.cids.server.data.legacy.CidsClass;

/**
 * DOCUMENT ME!
 *
 * @author   thorsten
 * @version  1.0
 */
@ServiceProvider(service = CidsServerCore.class)
public class LegacyEntityInfoCore implements EntityInfoCore {

    //~ Static fields/initializers ---------------------------------------------

    private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(LegacyEntityInfoCore.class);

    protected static final ObjectMapper MAPPER = new ObjectMapper(new JsonFactory());

    //~ Methods ----------------------------------------------------------------

    @Override
    public List<ObjectNode> getAllClasses(final User user, final String role) {
        try {
            final List<ObjectNode> all = new ArrayList<ObjectNode>();
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final MetaClass[] metaClasses = LegacyCoreBackend.getInstance()
                        .getService()
                        .getClasses(cidsUser, cidsUser.getDomain());
            if (metaClasses != null) {
                for (final MetaClass metaClass : metaClasses) {
                    final CidsClass cidsClass = LegacyCoreBackend.getInstance().createCidsClass(metaClass);
                    final ObjectNode node = (ObjectNode)MAPPER.convertValue(cidsClass, ObjectNode.class);
                    all.add(node);
                }
            }

            return all;
        } catch (final Exception ex) {
            log.error(null, ex);
        }
        return null;
    }

    @Override
    public ObjectNode getClass(final User user, final String classKey, final String role) {
        try {
            final String[] classKeySplitted = classKey.split("@");
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final String domain = classKeySplitted[1];
            final MetaClass metaClass = LegacyCoreBackend.getInstance()
                        .getService()
                        .getClass(cidsUser, Integer.parseInt(classKeySplitted[0]), domain);
            if (metaClass != null) {
                final CidsClass cidsClass = LegacyCoreBackend.getInstance().createCidsClass(metaClass);
                final ObjectNode node = (ObjectNode)MAPPER.convertValue(cidsClass, ObjectNode.class);
                return node;
            }
        } catch (final Exception ex) {
            log.error(null, ex);
        }
        return null;
    }

    @Override
    public ObjectNode getAttribute(final User user,
            final String classKey,
            final String attributeKey,
            final String role) {
        try {
            final String[] classKeySplitted = classKey.split("@");
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final String domain = classKeySplitted[1];
            final MetaClass metaClass = LegacyCoreBackend.getInstance()
                        .getService()
                        .getClass(cidsUser, Integer.parseInt(classKeySplitted[0]), domain);
            if (metaClass != null) {
                final CidsClass cidsClass = LegacyCoreBackend.getInstance().createCidsClass(metaClass);
                final CidsAttribute cidsAttribute = cidsClass.getAttribute(attributeKey);
                final ObjectNode node = (ObjectNode)MAPPER.convertValue(cidsAttribute, ObjectNode.class);
                return node;
            }
        } catch (final Exception ex) {
            log.error(null, ex);
        }
        return null;
    }

    @Override
    public ObjectNode emptyInstance(final User user, final String classKey, final String role) {
        try {
            final String[] classKeySplitted = classKey.split("@");
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final String domain = classKeySplitted[1];
            final int cid = Integer.parseInt(classKeySplitted[0]);
            final MetaClass metaClass = LegacyCoreBackend.getInstance().getService().getClass(cidsUser, cid, domain);
            final CidsBean beanNew = metaClass.getEmptyInstance().getBean();
            final ObjectNode node = (ObjectNode)MAPPER.reader().readTree(beanNew.toJSONString(true));
            return node;
        } catch (final Exception ex) {
            log.error(null, ex);
            return null;
        }
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.entityInfo"; // NOI18N
    }
}
