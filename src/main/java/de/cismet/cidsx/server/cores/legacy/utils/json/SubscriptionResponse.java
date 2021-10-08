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
public class SubscriptionResponse {

    //~ Instance fields --------------------------------------------------------

    private String type;
    private String id;
    private Payload payload;

    //~ Inner Classes ----------------------------------------------------------

    /**
     * DOCUMENT ME!
     *
     * @version  $Revision$, $Date$
     */
    @Getter
    @Setter
    public static class Payload {

        //~ Instance fields ----------------------------------------------------

        Data data;
        // the fields extensions and message will be used, when an error occurs
        Extensions extensions;
        String message;

        //~ Inner Classes ------------------------------------------------------

        /**
         * DOCUMENT ME!
         *
         * @version  $Revision$, $Date$
         */
        @Getter
        @Setter
        public static class Data {

            //~ Instance fields ------------------------------------------------

            Action[] action;

            //~ Inner Classes --------------------------------------------------

            /**
             * DOCUMENT ME!
             *
             * @version  $Revision$, $Date$
             */
            @Getter
            @Setter
            public static class Action {

                //~ Instance fields --------------------------------------------

                String id;
                String updatedAt;
                String isCompleted;
                String result;
                String parameter;
                String action;
                String jwt;
                String applicationId;
                String createdAt;
                String body;
                Integer status;

                //~ Methods ----------------------------------------------------

                @Override
                public boolean equals(final Object obj) {
                    if (obj instanceof Action) {
                        final Action other = (Action)obj;

                        return isSame(this.getId(), other.getId()) && isSame(this.getStatus(), other.getStatus())
                                    && isSame(this.getResult(), other.getResult())
                                    && isSame(this.getJwt(), other.getJwt());
                    }

                    return false;
                }

                /**
                 * DOCUMENT ME!
                 *
                 * @param   o1  DOCUMENT ME!
                 * @param   o2  DOCUMENT ME!
                 *
                 * @return  DOCUMENT ME!
                 */
                private boolean isSame(final Object o1, final Object o2) {
                    if ((o1 == null) && (o2 == null)) {
                        return true;
                    } else if (o1 == null) {
                        return false;
                    } else if (o2 == null) {
                        return false;
                    } else {
                        return o1.equals(o2);
                    }
                }
            }
        }

        /**
         * DOCUMENT ME!
         *
         * @version  $Revision$, $Date$
         */
        @Getter
        @Setter
        public static class Extensions {

            //~ Instance fields ------------------------------------------------

            String path;
            String code;
        }
    }
}
