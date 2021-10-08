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
package de.cismet.cidsx.server.cores.legacy.utils.json;

import lombok.Getter;
import lombok.Setter;

/**
 * DOCUMENT ME!
 *
 * @author   therter
 * @version  $Revision$, $Date$
 */
@Getter
@Setter
public class UpdateResult {

    //~ Instance fields --------------------------------------------------------

    Data data;

    //~ Inner Classes ----------------------------------------------------------

    /**
     * DOCUMENT ME!
     *
     * @version  $Revision$, $Date$
     */
    @Getter
    @Setter
    public static class Data {

        //~ Instance fields ----------------------------------------------------

        UpdateAction update_action;

        //~ Inner Classes ------------------------------------------------------

        /**
         * DOCUMENT ME!
         *
         * @version  $Revision$, $Date$
         */
        @Getter
        @Setter
        public static class UpdateAction {

            //~ Instance fields ------------------------------------------------

            Integer affected_rows;
        }
    }
}
