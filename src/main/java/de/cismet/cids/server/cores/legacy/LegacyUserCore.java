/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cids.server.cores.legacy;

import Sirius.server.newuser.UserGroup;

import org.openide.util.lookup.ServiceProvider;

import java.util.ArrayList;
import java.util.Collection;

import de.cismet.cids.server.api.types.User;
import de.cismet.cids.server.cores.CidsServerCore;
import de.cismet.cids.server.cores.UserCore;

/**
 * DOCUMENT ME!
 *
 * @author   thorsten
 * @version  1.0
 */
@ServiceProvider(service = CidsServerCore.class)
public class LegacyUserCore implements UserCore {

    //~ Instance fields --------------------------------------------------------

    private final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(this.getClass());

    //~ Methods ----------------------------------------------------------------

    @Override
    public boolean isNoneUserAllowed() {
        return false;
    }

    @Override
    public User validate(final User user) {
        System.setProperty("sun.rmi.transport.connectionTimeout", "15");
        final String username = user.getUser();
        final String password = user.getPass();
        final String domain = user.getDomain();
        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance()
                        .getService()
                        .getUser(null, null, domain, username, password);
            final Collection<String> userGroupNames = new ArrayList<String>();
            for (final UserGroup cidsUserGroup : cidsUser.getPotentialUserGroups()) {
                userGroupNames.add(cidsUserGroup.getName());
            }
            user.setUserGroups(userGroupNames);
            LegacyCoreBackend.getInstance().registerUser(cidsUser, user);
            user.setValidated(true);
            return user;
        } catch (final Exception ex) {
            log.error("Fehler beim Anmelden", ex);
            return null;
        }
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.user"; // NOI18N
    }
}
