/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cidsx.server.cores.legacy;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.lookup.ServiceProvider;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import de.cismet.cids.server.actions.ServerActionParameter;
import de.cismet.cids.server.actions.graphql.GraphqlAction;

import de.cismet.cidsx.server.api.types.User;
import de.cismet.cidsx.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cidsx.server.cores.CidsServerCore;
import de.cismet.cidsx.server.cores.GraphQlCore;
import de.cismet.cidsx.server.exceptions.CidsServerException;

/**
 * DOCUMENT ME!
 *
 * @author   thorsten
 * @version  1.0
 */
@Slf4j
@ServiceProvider(service = CidsServerCore.class)
public class LegacyGraphQlCore implements GraphQlCore {

    //~ Methods ----------------------------------------------------------------

    @Override
    public String getCoreKey() {
        return "core.legacy.graphQl"; // NOI18N
    }

    @Override
    public Object executeQuery(final User user,
            final String role,
            final String request,
            final String contentType) {
        final List<ServerActionParameter> cidsSAPs = new ArrayList<>();
        final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
        final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        final Query query = new Query();
        boolean chunked = false;

        try {
            final JsonNode node = mapper.readTree(request);
            final Iterator<Map.Entry<String, JsonNode>> it = node.fields();

            while (it.hasNext()) {
                final Map.Entry<String, JsonNode> n = it.next();

                if (n.getKey().equalsIgnoreCase("query")) {
                    query.setQuery(n.getValue().asText());
                } else if (n.getKey().equalsIgnoreCase("OperationName")) {
                    query.setOperationName(n.getValue().asText());
                } else if (n.getKey().equalsIgnoreCase("variables")) {
                    final JsonNode subNode = n.getValue();
                    final Iterator<Map.Entry<String, JsonNode>> subs = subNode.fields();
                    final List<VariableEntry> variableList = new ArrayList<VariableEntry>();

                    while (subs.hasNext()) {
                        final Map.Entry<String, JsonNode> subEntry = subs.next();

                        variableList.add(new VariableEntry(subEntry.getKey(), subEntry.getValue().toString()));
                    }
                    query.setVariables(variableList.toArray(new VariableEntry[variableList.size()]));
                } else if (n.getKey().equalsIgnoreCase("chunked") && n.getValue().asText().equalsIgnoreCase("true")) {
                    chunked = true;
                }
            }
        } catch (Exception e) {
            log.error("Error while parsing parameter: " + request, e);
        }

        ServerActionParameter cidsSAP = new ServerActionParameter("QUERY", query.getQuery());
        cidsSAPs.add(cidsSAP);
        cidsSAP = new ServerActionParameter("VARIABLES", query.getVariablesAsText());
        cidsSAPs.add(cidsSAP);

        if (chunked) {
            cidsSAP = new ServerActionParameter(GraphqlAction.PARAMETER_TYPE.CHUNKED.toString(), "true");
            cidsSAPs.add(cidsSAP);
        }

        boolean shouldBeZipped = false;

        if ((contentType != null)
                    && (contentType.toLowerCase().contains("gzip")
                        || contentType.toLowerCase().contains("octet-stream"))) {
            cidsSAP = new ServerActionParameter("ZIPPED", "true");
            cidsSAPs.add(cidsSAP);
            shouldBeZipped = true;
        }

        if (log.isDebugEnabled()) {
            log.debug(String.format("send graphql request (expect gzip: %s): %s", shouldBeZipped, query.getQuery()));
        }

        try {
            final Object taskResult = LegacyCoreBackend.getInstance()
                        .getService()
                        .executeTask(
                            cidsUser,
                            "graphQl",
                            cidsUser.getDomain(),
                            null,
                            LegacyCoreBackend.getInstance().getConnectionContext(),
                            cidsSAPs.toArray(new ServerActionParameter[0]));

            return taskResult;
        } catch (final Exception ex) {
            final String message = "error while executing queryQl task with request '"
                        + request + "': " + ex.getMessage();
            log.error(message, ex);
            throw new CidsServerException(message, message,
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex);
        }
    }

    //~ Inner Classes ----------------------------------------------------------

    /**
     * DOCUMENT ME!
     *
     * @version  $Revision$, $Date$
     */
    private static class Query {

        //~ Instance fields ----------------------------------------------------

        private String query;
        private VariableEntry[] variables;
        private String operationName;

        //~ Constructors -------------------------------------------------------

        /**
         * Creates a new Query object.
         */
        public Query() {
        }

        /**
         * Creates a new Query object.
         *
         * @param  query      DOCUMENT ME!
         * @param  variables  DOCUMENT ME!
         */
        public Query(final String query, final VariableEntry[] variables) {
            this.query = query;
            this.variables = variables;
        }

        /**
         * Creates a new Query object.
         *
         * @param  query          DOCUMENT ME!
         * @param  variables      DOCUMENT ME!
         * @param  operationName  DOCUMENT ME!
         */
        public Query(final String query, final VariableEntry[] variables, final String operationName) {
            this.query = query;
            this.variables = variables;
            this.operationName = operationName;
        }

        //~ Methods ------------------------------------------------------------

        /**
         * DOCUMENT ME!
         *
         * @return  the query
         */
        public String getQuery() {
            return query;
        }

        /**
         * DOCUMENT ME!
         *
         * @param  query  the query to set
         */
        public void setQuery(final String query) {
            this.query = query;
        }

        /**
         * DOCUMENT ME!
         *
         * @return  the variables
         */
        public VariableEntry[] getVariables() {
            return variables;
        }

        /**
         * DOCUMENT ME!
         *
         * @param  variables  the variables to set
         */
        public void setVariables(final VariableEntry[] variables) {
            this.variables = variables;
        }

        /**
         * DOCUMENT ME!
         *
         * @return  DOCUMENT ME!
         */
        public String getVariablesAsText() {
            final StringBuffer sb = new StringBuffer("{");
            boolean first = true;

            for (final VariableEntry variable : variables) {
                if (first) {
                    first = false;
                } else {
                    sb.append(",");
                }
                sb.append("\"").append(variable.name).append("\":");
                sb.append(variable.value);
            }

            sb.append("}");
            return sb.toString();
        }

        /**
         * DOCUMENT ME!
         *
         * @return  the variables
         */
        public String getOperationName() {
            return operationName;
        }

        /**
         * DOCUMENT ME!
         *
         * @param  operationName  variables the variables to set
         */
        public void setOperationName(final String operationName) {
            this.operationName = operationName;
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @version  $Revision$, $Date$
     */
    private static class VariableEntry {

        //~ Instance fields ----------------------------------------------------

        private String name;
        private String value;

        //~ Constructors -------------------------------------------------------

        /**
         * Creates a new VariableEntry object.
         */
        public VariableEntry() {
        }

        /**
         * Creates a new VariableEntry object.
         *
         * @param  name   DOCUMENT ME!
         * @param  value  DOCUMENT ME!
         */
        public VariableEntry(final String name, final String value) {
            this.name = name;
            this.value = value;
        }

        //~ Methods ------------------------------------------------------------

        /**
         * DOCUMENT ME!
         *
         * @return  the name
         */
        public String getName() {
            return name;
        }

        /**
         * DOCUMENT ME!
         *
         * @param  name  the name to set
         */
        public void setName(final String name) {
            this.name = name;
        }

        /**
         * DOCUMENT ME!
         *
         * @return  the value
         */
        public String getValue() {
            return value;
        }

        /**
         * DOCUMENT ME!
         *
         * @param  value  the value to set
         */
        public void setValue(final String value) {
            this.value = value;
        }
    }
}
