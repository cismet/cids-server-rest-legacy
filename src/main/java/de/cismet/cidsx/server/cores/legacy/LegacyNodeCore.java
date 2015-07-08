/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cidsx.server.cores.legacy;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.lookup.ServiceProvider;

import java.util.ArrayList;
import java.util.List;

import de.cismet.cidsx.server.api.types.CidsNode;
import de.cismet.cidsx.server.api.types.User;
import de.cismet.cidsx.server.api.types.legacy.CidsNodeFactory;
import de.cismet.cidsx.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cidsx.server.cores.CidsServerCore;
import de.cismet.cidsx.server.cores.NodeCore;

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
    public List<JsonNode> getRootNodes(final User user, final String role) {
        LegacyCoreBackend.getInstance().ensureDomainCached(user.getDomain(), user);
        if (log.isDebugEnabled()) {
            log.debug("getRootNodes");
        }

        try {
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final Sirius.server.middleware.types.Node[] legacyNodes = LegacyCoreBackend.getInstance()
                        .getService()
                        .getRoots(legacyUser);

            final List<JsonNode> nodes = new ArrayList<JsonNode>();
            for (final Sirius.server.middleware.types.Node legacyNode : legacyNodes) {
                final String className = LegacyCoreBackend.getInstance()
                            .getClassNameCache()
                            .getClassNameForClassId(user.getDomain(), legacyNode.getClassId());
                final CidsNode node = CidsNodeFactory.getFactory()
                            .restCidsNodeFromLegacyCidsNode(legacyNode, className);
                nodes.add(MAPPER.convertValue(node, JsonNode.class));
            }

            return nodes;
        } catch (final Exception ex) {
            final String message = "error while getting root nodes: " + ex.getMessage();
            log.error(message, ex);
            throw new RuntimeException(message, ex);
        }
    }

    @Override
    public JsonNode getNode(final User user, final String nodeKey, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getNode with nodeKey '" + nodeKey + "'.");
        }

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
            return MAPPER.convertValue(restNode, JsonNode.class);
        } catch (final Exception ex) {
            final String message = "error while getting a node with nodeKey '"
                        + nodeKey + "': " + ex.getMessage();
            log.error(message, ex);
            throw new RuntimeException(message, ex);
        }
    }

    @Override
    public List<JsonNode> getChildren(final User user, final String nodeKey, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getChildren with nodeKey '" + nodeKey + "'.");
        }

        LegacyCoreBackend.getInstance().ensureDomainCached(user.getDomain(), user);

        try {
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final Sirius.server.middleware.types.Node legacyNode = LegacyCoreBackend.getInstance()
                        .getService()
                        .getMetaObjectNode(legacyUser, Integer.parseInt(nodeKey), user.getDomain());
            final Sirius.server.middleware.types.Node[] legacyChildrenNodes = LegacyCoreBackend.getInstance()
                        .getService()
                        .getChildren(legacyNode, legacyUser);

            final List<JsonNode> nodes = new ArrayList<JsonNode>();
            for (final Sirius.server.middleware.types.Node legacyChildrenNode : legacyChildrenNodes) {
                final String className = LegacyCoreBackend.getInstance()
                            .getClassNameCache()
                            .getClassNameForClassId(user.getDomain(), legacyChildrenNode.getClassId());
                final CidsNode childrenNode = CidsNodeFactory.getFactory()
                            .restCidsNodeFromLegacyCidsNode(legacyChildrenNode, className);
                nodes.add(MAPPER.convertValue(childrenNode, JsonNode.class));
            }

            return nodes;
        } catch (final Exception ex) {
            final String message = "error while getting children of node '"
                        + nodeKey + "': " + ex.getMessage();
            log.error(message, ex);
            throw new RuntimeException(message, ex);
        }
    }

    @Override
    public List<JsonNode> getChildrenByQuery(final User user, final String nodeQuery, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getChildrenByQuery with nodeQuery '" + nodeQuery + "'.");
        }

        LegacyCoreBackend.getInstance().ensureDomainCached(user.getDomain(), user);

        try {
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final Sirius.server.middleware.types.Node legacyNode = CidsNodeFactory.getFactory()
                        .createLegacyQueryNode(user.getDomain(), nodeQuery);

            final Sirius.server.middleware.types.Node[] legacyChildrenNodes = LegacyCoreBackend.getInstance()
                        .getService()
                        .getChildren(legacyNode, legacyUser);

            final List<JsonNode> children = new ArrayList<JsonNode>();
            for (final Sirius.server.middleware.types.Node legacyChildrenNode : legacyChildrenNodes) {
                final String className = LegacyCoreBackend.getInstance()
                            .getClassNameCache()
                            .getClassNameForClassId(user.getDomain(), legacyChildrenNode.getClassId());
                final CidsNode childrenNode = CidsNodeFactory.getFactory()
                            .restCidsNodeFromLegacyCidsNode(legacyChildrenNode, className);
                children.add(MAPPER.convertValue(childrenNode, JsonNode.class));
            }

            return children;
        } catch (final Exception ex) {
            final String message = "error while getting children by query '"
                        + nodeQuery + "': " + ex.getMessage();
            log.error(message, ex);
            throw new RuntimeException(message, ex);
        }
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.node"; // NOI18N
    }
}
