/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cidsx.server.cores.legacy;

import Sirius.server.newuser.UserGroup;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.lookup.ServiceProvider;

import java.security.Key;

import java.util.ArrayList;
import java.util.Collection;

import de.cismet.cidsx.server.api.types.User;
import de.cismet.cidsx.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cidsx.server.cores.CidsServerCore;
import de.cismet.cidsx.server.cores.UserCore;

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

        String username;
        String password;
        String domain;

        System.setProperty("sun.rmi.transport.connectionTimeout", "15");
        if (user.getJwt() != null) {
            username = "jwt";
            password = user.getJwt();
            domain = user.getDomain(); // extracted from jwt payload
        } else {
            username = user.getUser();
            password = user.getPass();
            domain = user.getDomain();
        }
        try {
            final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance()
                        .getService()
                        .getUser(
                            null,
                            null,
                            domain,
                            username,
                            password,
                            LegacyCoreBackend.getInstance().getConnectionContext());
            final Collection<String> userGroupNames = new ArrayList<>();

            if (cidsUser.getPotentialUserGroups() != null) {
                for (final UserGroup cidsUserGroup : cidsUser.getPotentialUserGroups()) {
                    userGroupNames.add(cidsUserGroup.getName());
                }
            }

            user.setUserGroups(userGroupNames);
            LegacyCoreBackend.getInstance().registerUser(cidsUser, user);
            user.setValidated(true);
            user.setJwt(cidsUser.getJwsToken());
        } catch (final Exception ex) {
            log.error("Could not validate user '" + user.getUser() + "': "
                        + ex.getMessage(), ex);
            user.setValidated(false);
        }
        return user;
    }

    @Override
    public Key getPublicJwtKey(final String domain) {
        try {
            return LegacyCoreBackend.getInstance().getService().getPublicJwtKey(domain);
        } catch (Exception e) {
            log.error("Cannot retrieve public jwt key", e);

            return null;
        }
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.user"; // NOI18N
    }
}
