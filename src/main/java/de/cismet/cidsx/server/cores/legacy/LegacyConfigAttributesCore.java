/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.cismet.cidsx.server.cores.legacy;

import Sirius.server.newuser.UserGroup;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.lookup.ServiceProvider;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import de.cismet.cidsx.server.api.types.User;
import de.cismet.cidsx.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cidsx.server.cores.CidsServerCore;
import de.cismet.cidsx.server.cores.ConfigAttributesCore;
import de.cismet.cidsx.server.exceptions.CidsServerException;

/**
 * DOCUMENT ME!
 *
 * @author   therter
 * @version  $Revision$, $Date$
 */
@Slf4j
@ServiceProvider(service = CidsServerCore.class)
public class LegacyConfigAttributesCore implements ConfigAttributesCore {

    //~ Methods ----------------------------------------------------------------

    @Override
    public String getConfigattribute(final User user, final String configattribute) {
        try {
            Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, null);

            if (legacyUser == null) {
                legacyUser = new Sirius.server.newuser.User(3000, user.getUser(), user.getDomain(), user.getJwt());
                final List<UserGroup> groups = new ArrayList<>();

                for (final String grString : user.getUserGroups()) {
                    final UserGroup gr = new UserGroup(-1, grString, user.getDomain());
                    groups.add(gr);
                }

                legacyUser.setPotentialUserGroups(groups);
            }

            final String configAttr = LegacyCoreBackend.getInstance()
                        .getService()
                        .getConfigAttr(
                            legacyUser,
                            configattribute,
                            LegacyCoreBackend.getInstance().getConnectionContext());

            return configAttr;
        } catch (final Exception ex) {
            final String message = "error while getting configuration attributes for user '"
                        + user.getUser() + "': " + ex.getMessage();
            log.error(message, ex);
            throw new CidsServerException(message, message,
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex);
        }
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.configAttr"; // NOI18N
    }
}
