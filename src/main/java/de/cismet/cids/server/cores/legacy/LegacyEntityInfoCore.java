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

import lombok.extern.slf4j.Slf4j;

import org.openide.util.lookup.ServiceProvider;

import java.util.ArrayList;
import java.util.List;

import de.cismet.cids.dynamics.CidsBean;

import de.cismet.cids.server.api.types.CidsAttribute;
import de.cismet.cids.server.api.types.CidsClass;
import de.cismet.cids.server.api.types.User;
import de.cismet.cids.server.api.types.legacy.CidsClassFactory;
import de.cismet.cids.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cids.server.cores.CidsServerCore;
import de.cismet.cids.server.cores.EntityInfoCore;

/**
 * DOCUMENT ME!
 *
 * @author   thorsten
 * @version  1.0
 */
@Slf4j
@ServiceProvider(service = CidsServerCore.class)
public class LegacyEntityInfoCore implements EntityInfoCore {

    //~ Static fields/initializers ---------------------------------------------

    protected static final ObjectMapper MAPPER = new ObjectMapper(new JsonFactory());

    //~ Methods ----------------------------------------------------------------

    @Override
    public List<ObjectNode> getAllClasses(final User user, final String role) {
        try {
            final List<ObjectNode> all = new ArrayList<ObjectNode>();
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final MetaClass[] metaClasses = LegacyCoreBackend.getInstance()
                        .getService()
                        .getClasses(legacyUser, legacyUser.getDomain());
            if (metaClasses != null) {
                for (final MetaClass metaClass : metaClasses) {
                    final CidsClass cidsClass = CidsClassFactory.getFactory()
                                .restCidsClassFromLegacyCidsClass(metaClass);
                    final ObjectNode node = (ObjectNode)MAPPER.convertValue(cidsClass, ObjectNode.class);
                    all.add(node);
                }

                // fill the class key cache if required
                if (!LegacyCoreBackend.getInstance().getClassNameCache().isDomainCached(role)) {
                    LegacyCoreBackend.getInstance().getClassNameCache().fillCache(role, metaClasses);
                }
            }

            return all;
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            throw new RuntimeException("error while getting all classes", ex);
        }
    }

    @Override
    public ObjectNode getClass(final User user, final String classKey, final String role) {
        try {
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClassname(classKey, legacyUser);
            if (metaClass == null) {
                throw new RuntimeException("classKey " + classKey + " no found");
            }

            final CidsClass cidsClass = CidsClassFactory.getFactory().restCidsClassFromLegacyCidsClass(metaClass);
            final ObjectNode node = (ObjectNode)MAPPER.convertValue(cidsClass, ObjectNode.class);
            return node;
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            throw new RuntimeException("error while gett class", ex);
        }
    }

    @Override
    public ObjectNode getAttribute(final User user,
            final String classKey,
            final String attributeKey,
            final String role) {
        try {
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClassname(classKey, legacyUser);
            if (metaClass == null) {
                throw new RuntimeException("classKey " + classKey + " not found");
            }

            final CidsClass cidsClass = CidsClassFactory.getFactory().restCidsClassFromLegacyCidsClass(metaClass);
            final CidsAttribute cidsAttribute = cidsClass.getAttribute(attributeKey);
            final ObjectNode node = (ObjectNode)MAPPER.convertValue(cidsAttribute, ObjectNode.class);
            return node;
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            throw new RuntimeException("error while getting all classes", ex);
        }
    }

    @Override
    public ObjectNode emptyInstance(final User user, final String classKey, final String role) {
        try {
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClassname(classKey, legacyUser);
            if (metaClass == null) {
                throw new RuntimeException("classKey " + classKey + " no found");
            }

            final CidsBean beanNew = metaClass.getEmptyInstance().getBean();
            final ObjectNode node = (ObjectNode)MAPPER.reader().readTree(beanNew.toJSONString(true));
            return node;
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            throw new RuntimeException("error while creating empty instance", ex);
        }
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.entityInfo"; // NOI18N
    }
}
