/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cids.server.cores.legacy;

import Sirius.server.middleware.types.MetaClass;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.openide.util.lookup.ServiceProvider;

import java.util.List;

import de.cismet.cids.dynamics.CidsBean;

import de.cismet.cids.server.api.types.User;
import de.cismet.cids.server.cores.CidsServerCore;
import de.cismet.cids.server.cores.EntityInfoCore;

import static de.cismet.cids.server.cores.legacy.LegacyEntityCore.MAPPER;

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

    //~ Methods ----------------------------------------------------------------

    @Override
    public List<ObjectNode> getAllClasses(final User user, final String role) {
//        try {
//            final MetaClass[] metaClasses = ConnectorHelper.getInstance().getService().getClasses(null, user.getDomain());
//
//            final ArrayList all = new ArrayList();
//            for (final MetaClass metaClass : metaClasses) {
//                if (metaClass != null) {
//                    try {
//                        final ObjectNode on = new ObjectNode(JsonNodeFactory.instance);
//                        all.add(on);
//                    } catch (IOException ex) {
//                        ex.printStackTrace();
//                    } finally {
//                        IOUtils.closeQuietly(bis);
//                    }
//                }
//            }
//            return all;
//        } catch (RemoteException ex) {
//            return null;
//        }
        // TODO
        return null;
    }

    @Override
    public ObjectNode getClass(final User user, final String classKey, final String role) {
        // TODO
        return null;
    }

    @Override
    public ObjectNode getAttribute(final User user,
            final String classKey,
            final String attributeKey,
            final String role) {
        // TODO
        return null;
            // Tools | Templates.
    }

    @Override
    public ObjectNode emptyInstance(final User user, final String classKey, final String role) {
        try {
            final String[] classKeySplitted = classKey.split("@");
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user);
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
