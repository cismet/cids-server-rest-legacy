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

import de.cismet.cids.server.api.types.Node;
import de.cismet.cids.server.api.types.User;
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
        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final Sirius.server.middleware.types.Node[] cidsNodes = LegacyCoreBackend.getInstance()
                        .getService()
                        .getRoots(cidsUser);

            final List<ObjectNode> nodes = new ArrayList<ObjectNode>();
            for (final Sirius.server.middleware.types.Node cidsNode : cidsNodes) {
                final Node node = createCidsNode(cidsNode);
                nodes.add(MAPPER.convertValue(node, ObjectNode.class));
            }

            return nodes;
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param   cidsNode  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public static Node createCidsNode(final Sirius.server.middleware.types.Node cidsNode) {
        final String key = Integer.toString(cidsNode.getId());
        final String name = cidsNode.getName();
        final String objectKey = Integer.toString(cidsNode.getClassId());
        final String dynamicChildren = cidsNode.getDynamicChildrenStatement();
        final boolean clientSort = cidsNode.isSqlSort();
        final boolean derivePermissionsFromClass = cidsNode.isDerivePermissionsFromClass();
        final boolean isLeaf = cidsNode.isLeaf();
        final String icon = cidsNode.getIconString();
        final String iconFactory = null;
        final String policy = cidsNode.getPermissions().getPolicy().getName();
        final Node node = new Node(
                key,
                name,
                objectKey,
                dynamicChildren,
                clientSort,
                derivePermissionsFromClass,
                isLeaf,
                icon,
                iconFactory,
                policy);
        return node;
    }

    @Override
    public ObjectNode getNode(final User user, final String nodeKey, final String role) {
        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final Sirius.server.middleware.types.Node cidsNode = LegacyCoreBackend.getInstance()
                        .getService()
                        .getMetaObjectNode(cidsUser, Integer.parseInt(nodeKey), user.getDomain());

            final Node node = createCidsNode(cidsNode);
            return MAPPER.convertValue(node, ObjectNode.class);
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            return null;
        } // Tools | Templates.
    }

    @Override
    public List<ObjectNode> getChildren(final User user, final String nodeKey, final String role) {
        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final Sirius.server.middleware.types.Node cidsNode = LegacyCoreBackend.getInstance()
                        .getService()
                        .getMetaObjectNode(cidsUser, Integer.parseInt(nodeKey), user.getDomain());
            final Sirius.server.middleware.types.Node[] cidsChildrenNodes = LegacyCoreBackend.getInstance()
                        .getService()
                        .getChildren(cidsNode, cidsUser);

            final List<ObjectNode> nodes = new ArrayList<ObjectNode>();
            for (final Sirius.server.middleware.types.Node cidsChildrenNode : cidsChildrenNodes) {
                final Node childrenNode = createCidsNode(cidsChildrenNode);
                nodes.add(MAPPER.convertValue(childrenNode, ObjectNode.class));
            }

            return nodes;
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            return null;
        } // Tools | Templates.
        // Tools | Templates.
    }

    @Override
    public List<ObjectNode> getChildrenByQuery(final User user, final String nodeQuery, final String role) {
        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final Sirius.server.middleware.types.Node[] cidsChildren = LegacyCoreBackend.getInstance()
                        .getService()
                        .getMetaObjectNode(cidsUser, nodeQuery);

            final List<ObjectNode> children = new ArrayList<ObjectNode>();
            for (final Sirius.server.middleware.types.Node cidsChildrenNode : cidsChildren) {
                final Node childrenNode = createCidsNode(cidsChildrenNode);
                children.add(MAPPER.convertValue(childrenNode, ObjectNode.class));
            }

            return children;
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            return null;
        } // Tools | Templates.
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.node"; // NOI18N
    }
}
