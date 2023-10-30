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

import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletResponse;

import de.cismet.cidsx.server.api.types.User;
import de.cismet.cidsx.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cidsx.server.cores.ConfigAttributesCore;
import de.cismet.cidsx.server.exceptions.CidsServerException;

/**
 * DOCUMENT ME!
 *
 * @author   therter
 * @version  $Revision$, $Date$
 */
@Slf4j
public class LegacyConfigAttributesCore implements ConfigAttributesCore {

    //~ Methods ----------------------------------------------------------------

    @Override
    public String getConfigattribute(final User user, final String configattribute) {
        LegacyCoreBackend.getInstance().ensureDomainCached(user.getDomain(), user);

        try {
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, null);

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
