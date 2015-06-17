/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cids.server.cores.legacy;

import Sirius.server.newuser.UserGroup;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.lookup.ServiceProvider;

import java.util.ArrayList;
import java.util.Collection;

import de.cismet.cids.server.api.types.User;
import de.cismet.cids.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cids.server.cores.CidsServerCore;
import de.cismet.cids.server.cores.UserCore;

/**
 * DOCUMENT ME!
 *
 * @author   thorsten
 * @version  1.0
 */
@Slf4j
@ServiceProvider(service = CidsServerCore.class)
public class LegacyUserCore implements UserCore {

    //~ Methods ----------------------------------------------------------------

    @Override
    public boolean isNoneUserAllowed() {
        return false;
    }

    @Override
    public User validate(final User user) {
        if (log.isDebugEnabled()) {
            log.debug("validate with user '" + user.getUser() + "'.");
        }

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
        } catch (final Exception ex) {
            log.error("Could not validate user '" + user.getUser() + "': "
                        + ex.getMessage(), ex);
            user.setValidated(false);
        }
        return user;
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.user"; // NOI18N
    }
}
