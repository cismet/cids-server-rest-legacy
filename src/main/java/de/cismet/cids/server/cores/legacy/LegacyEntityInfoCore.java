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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    public List<JsonNode> getAllClasses(final User user, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getAllClasses");
        }

        try {
            final List<JsonNode> all = new ArrayList<JsonNode>();
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final MetaClass[] metaClasses = LegacyCoreBackend.getInstance()
                        .getService()
                        .getClasses(legacyUser, legacyUser.getDomain());
            if (metaClasses != null) {
                for (final MetaClass metaClass : metaClasses) {
                    final CidsClass cidsClass = CidsClassFactory.getFactory()
                                .restCidsClassFromLegacyCidsClass(metaClass);
                    final JsonNode node = (JsonNode)MAPPER.convertValue(cidsClass, JsonNode.class);
                    all.add(node);
                }

                // fill the class key cache if required
                if (!LegacyCoreBackend.getInstance().getClassNameCache().isDomainCached(role)) {
                    LegacyCoreBackend.getInstance().getClassNameCache().fillCache(role, metaClasses);
                }
            }

            return all;
        } catch (final Exception ex) {
            final String message = "error while getting all classes: " + ex.getMessage();
            log.error(message, ex);
            throw new RuntimeException(message, ex);
        }
    }

    @Override
    public JsonNode getClass(final User user, final String classKey, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getClass with classKey '" + classKey + "'.");
        }

        try {
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClassname(classKey, legacyUser);
            if (metaClass == null) {
                final String message = "error while getting class with classKey '" + classKey
                            + "': class not found!";
                log.error(message);
                throw new RuntimeException(message);
            }

            final CidsClass cidsClass = CidsClassFactory.getFactory().restCidsClassFromLegacyCidsClass(metaClass);
            final JsonNode node = MAPPER.convertValue(cidsClass, JsonNode.class);
            return node;
        } catch (final Exception ex) {
            final String message = "error while getting class with classKey '" + classKey
                        + "': " + ex.getMessage();
            log.error(message, ex);
            throw new RuntimeException(message, ex);
        }
    }

    @Override
    public JsonNode getAttribute(final User user,
            final String classKey,
            final String attributeKey,
            final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getAttribute with classKey '" + classKey + "' and attributeKey '" + attributeKey + "'.");
        }

        try {
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClassname(classKey, legacyUser);
            if (metaClass == null) {
                final String message = "error while getting attribute with classKey '" + classKey
                            + "' and attributeKey '" + attributeKey + "': class not found!";
                log.error(message);
                throw new RuntimeException(message);
            }

            final CidsClass cidsClass = CidsClassFactory.getFactory().restCidsClassFromLegacyCidsClass(metaClass);
            final CidsAttribute cidsAttribute = cidsClass.getAttribute(attributeKey);
            final JsonNode node = MAPPER.convertValue(cidsAttribute, JsonNode.class);
            return node;
        } catch (final Exception ex) {
            final String message = "error while getting attribute with classKey '" + classKey
                        + "' and attributeKey '" + attributeKey + "': " + ex.getMessage();
            log.error(message, ex);
            throw new RuntimeException(message, ex);
        }
    }

    @Override
    public JsonNode emptyInstance(final User user, final String classKey, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("emptyInstance with classKey '" + classKey + "'.");
        }

        try {
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaclassForClassname(classKey, legacyUser);
            if (metaClass == null) {
                final String message = "error while getting empty instance with classKey '" + classKey
                            + "': class not found!";
                log.error(message);
                throw new RuntimeException(message);
            }

            final CidsBean beanNew = metaClass.getEmptyInstance().getBean();
            final JsonNode node = MAPPER.reader().readTree(beanNew.toJSONString(true));
            return node;
        } catch (final Exception ex) {
            final String message = "error while getting empty instance with classKey '" + classKey
                        + "': " + ex.getMessage();
            log.error(message, ex);
            throw new RuntimeException(message, ex);
        }
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.entityInfo"; // NOI18N
    }
}
