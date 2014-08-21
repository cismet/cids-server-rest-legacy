/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cids.server.cores.legacy;

import org.openide.util.lookup.ServiceProvider;

import de.cismet.cids.server.cores.CidsServerCore;

/**
 * DOCUMENT ME!
 *
 * @author   thorsten
 * @version  1.0
 */
@ServiceProvider(service = CidsServerCore.class)
public class LegacyCidsServerCore implements CidsServerCore {

    //~ Methods ----------------------------------------------------------------

    @Override
    public String getCoreKey() {
        return "core.legacy.cidsServer"; // NOI18N
    }
}
