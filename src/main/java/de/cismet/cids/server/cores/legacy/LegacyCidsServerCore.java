/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cids.server.cores.legacy;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.lookup.ServiceProvider;

import de.cismet.cids.server.cores.CidsServerCore;

/**
 * DOCUMENT ME!
 *
 * @author   thorsten
 * @version  1.0
 */
@Slf4j
@ServiceProvider(service = CidsServerCore.class)
public class LegacyCidsServerCore implements CidsServerCore {

    //~ Methods ----------------------------------------------------------------

    @Override
    public String getCoreKey() {
        return "core.legacy.cidsServer"; // NOI18N
    }
}
