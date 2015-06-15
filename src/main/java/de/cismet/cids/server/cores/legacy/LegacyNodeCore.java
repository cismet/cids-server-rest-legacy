/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cids.server.cores.legacy;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.lookup.ServiceProvider;

import java.util.ArrayList;
import java.util.List;

import de.cismet.cids.server.api.types.CidsNode;
import de.cismet.cids.server.api.types.User;
import de.cismet.cids.server.api.types.legacy.CidsNodeFactory;
import de.cismet.cids.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cids.server.cores.CidsServerCore;
import de.cismet.cids.server.cores.NodeCore;

/**
 * DOCUMENT ME!
 *
 * @author   thorsten
 * @version  1.0
 */
@Slf4j
@ServiceProvider(service = CidsServerCore.class)
public class LegacyNodeCore implements NodeCore {

    //~ Static fields/initializers ---------------------------------------------

    private static final ObjectMapper MAPPER = new ObjectMapper(new JsonFactory());

    //~ Methods ----------------------------------------------------------------

    @Override
    public List<ObjectNode> getRootNodes(final User user, final String role) {
        LegacyCoreBackend.getInstance().ensureDomainCached(user.getDomain(), user);

        try {
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final Sirius.server.middleware.types.Node[] legacyNodes = LegacyCoreBackend.getInstance()
                        .getService()
                        .getRoots(legacyUser);

            final List<ObjectNode> nodes = new ArrayList<ObjectNode>();
            for (final Sirius.server.middleware.types.Node legacyNode : legacyNodes) {
                final String className = LegacyCoreBackend.getInstance()
                            .getClassNameCache()
                            .getClassNameForClassId(user.getDomain(), legacyNode.getClassId());
                final CidsNode node = CidsNodeFactory.getFactory()
                            .restCidsNodeFromLegacyCidsNode(legacyNode, className);
                nodes.add(MAPPER.convertValue(node, ObjectNode.class));
            }

            return nodes;
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            throw new RuntimeException("error while getting root nodes: " + ex.getMessage(), ex);
        }
    }

    @Override
    public ObjectNode getNode(final User user, final String nodeKey, final String role) {
        LegacyCoreBackend.getInstance().ensureDomainCached(user.getDomain(), user);

        try {
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final Sirius.server.middleware.types.Node legacyNode = LegacyCoreBackend.getInstance()
                        .getService()
                        .getMetaObjectNode(legacyUser, Integer.parseInt(nodeKey), user.getDomain());
            final String className = LegacyCoreBackend.getInstance()
                        .getClassNameCache()
                        .getClassNameForClassId(user.getDomain(), legacyNode.getClassId());
            final CidsNode restNode = CidsNodeFactory.getFactory()
                        .restCidsNodeFromLegacyCidsNode(legacyNode, className);
            return MAPPER.convertValue(restNode, ObjectNode.class);
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            throw new RuntimeException("error while getting node", ex);
        }
    }

    @Override
    public List<ObjectNode> getChildren(final User user, final String nodeKey, final String role) {
        LegacyCoreBackend.getInstance().ensureDomainCached(user.getDomain(), user);

        try {
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final Sirius.server.middleware.types.Node legacyNode = LegacyCoreBackend.getInstance()
                        .getService()
                        .getMetaObjectNode(legacyUser, Integer.parseInt(nodeKey), user.getDomain());
            final Sirius.server.middleware.types.Node[] legacyChildrenNodes = LegacyCoreBackend.getInstance()
                        .getService()
                        .getChildren(legacyNode, legacyUser);

            final List<ObjectNode> nodes = new ArrayList<ObjectNode>();
            for (final Sirius.server.middleware.types.Node legacyChildrenNode : legacyChildrenNodes) {
                final String className = LegacyCoreBackend.getInstance()
                            .getClassNameCache()
                            .getClassNameForClassId(user.getDomain(), legacyChildrenNode.getClassId());
                final CidsNode childrenNode = CidsNodeFactory.getFactory()
                            .restCidsNodeFromLegacyCidsNode(legacyChildrenNode, className);
                nodes.add(MAPPER.convertValue(childrenNode, ObjectNode.class));
            }

            return nodes;
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            throw new RuntimeException("error while getting children of node '"
                        + nodeKey + "': " + ex.getMessage(),
                ex);
        }
    }

    @Override
    public List<ObjectNode> getChildrenByQuery(final User user, final String nodeQuery, final String role) {
        LegacyCoreBackend.getInstance().ensureDomainCached(user.getDomain(), user);

        try {
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final Sirius.server.middleware.types.Node legacyNode = CidsNodeFactory.getFactory()
                        .createLegacyQueryNode(user.getDomain(), nodeQuery);

            final Sirius.server.middleware.types.Node[] legacyChildrenNodes = LegacyCoreBackend.getInstance()
                        .getService()
                        .getChildren(legacyNode, legacyUser);

            final List<ObjectNode> children = new ArrayList<ObjectNode>();
            for (final Sirius.server.middleware.types.Node legacyChildrenNode : legacyChildrenNodes) {
                final String className = LegacyCoreBackend.getInstance()
                            .getClassNameCache()
                            .getClassNameForClassId(user.getDomain(), legacyChildrenNode.getClassId());
                final CidsNode childrenNode = CidsNodeFactory.getFactory()
                            .restCidsNodeFromLegacyCidsNode(legacyChildrenNode, className);
                children.add(MAPPER.convertValue(childrenNode, ObjectNode.class));
            }

            return children;
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            throw new RuntimeException("error while getting children by query: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.node"; // NOI18N
    }
}
