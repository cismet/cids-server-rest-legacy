/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cids.server.cores.legacy;

import Sirius.server.middleware.types.LightweightMetaObject;
import Sirius.server.middleware.types.MetaClass;
import Sirius.server.middleware.types.MetaObject;
import Sirius.server.middleware.types.Node;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.lookup.ServiceProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import de.cismet.cids.base.types.Type;

import de.cismet.cids.dynamics.CidsBean;

import de.cismet.cids.server.api.types.CidsClass;
import de.cismet.cids.server.api.types.CidsNode;
import de.cismet.cids.server.api.types.SearchInfo;
import de.cismet.cids.server.api.types.SearchParameter;
import de.cismet.cids.server.api.types.User;
import de.cismet.cids.server.api.types.legacy.CidsBeanFactory;
import de.cismet.cids.server.api.types.legacy.CidsClassFactory;
import de.cismet.cids.server.api.types.legacy.CidsNodeFactory;
import de.cismet.cids.server.api.types.legacy.ServerSearchFactory;
import de.cismet.cids.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cids.server.cores.CidsServerCore;
import de.cismet.cids.server.cores.SearchCore;
import de.cismet.cids.server.search.CidsServerSearch;

/**
 * DOCUMENT ME!
 *
 * @author   thorsten
 * @version  1.0
 */
@Slf4j
@ServiceProvider(service = CidsServerCore.class)
public class LegacySearchCore implements SearchCore {

    //~ Static fields/initializers ---------------------------------------------

    private static final ObjectMapper MAPPER = new ObjectMapper(new JsonFactory());

    //~ Methods ----------------------------------------------------------------

    @Override
    public List<SearchInfo> getAllSearches(final User user, final String role) {
        // TODO: user and role ignored!
        return ServerSearchFactory.getFactory().getServerSearchInfos();
    }

    @Override
    public SearchInfo getSearch(final User user, final String searchKey, final String role) {
        // TODO: user and role ignored!
        final SearchInfo searchInfo = ServerSearchFactory.getFactory().getServerSearchInfo(searchKey);

        if (searchInfo == null) {
            throw new RuntimeException("searchKey '" + searchKey + "' not found");
        }

        return searchInfo;
    }

    // curl -F
    // "params={\"params\":[{\"key\":\"Resulttyp\",\"value\":\"rO0ABX5yAFFkZS5jaXNtZXQuY2lkcy5jdXN0b20ud3VuZGFfYmxhdS5zZWFyY2guc2VydmVyLkNpZHNBbGtpc1NlYXJjaFN0YXRlbWVudCRSZXN1bHR0eXAAAAAAAAAAABIAAHhyAA5qYXZhLmxhbmcuRW51bQAAAAAAAAAAEgAAeHB0AApGTFVSU1RVRUNL\"},{\"key\":\"Name\",\"value\":\"rO0ABXQABERpZXM=\"},{\"key\":\"Vorname\",\"value\":\"rO0ABXQAB2lzdCBlaW4=\"},{\"key\":\"Geburtsname\",\"value\":\"rO0ABXQABFRlc3Q=\"},{\"key\":\"Geburtstag\",\"value\":\"rO0ABXQACTE3LjUuMTk4Mg==\"},{\"key\":\"Ptyp\",\"value\":\"rO0ABXA=\"},{\"key\":\"Geometry\",\"value\":\"rO0ABXNyACNjb20udml2aWRzb2x1dGlvbnMuanRzLmdlb20uUG9seWdvbs+AAQJyNo5LAgACWwAFaG9sZXN0AClbTGNvbS92aXZpZHNvbHV0aW9ucy9qdHMvZ2VvbS9MaW5lYXJSaW5nO0wABXNoZWxsdAAoTGNvbS92aXZpZHNvbHV0aW9ucy9qdHMvZ2VvbS9MaW5lYXJSaW5nO3hyACRjb20udml2aWRzb2x1dGlvbnMuanRzLmdlb20uR2VvbWV0cnl5nqRlIoVKPgIABEkABFNSSURMAAhlbnZlbG9wZXQAJkxjb20vdml2aWRzb2x1dGlvbnMvanRzL2dlb20vRW52ZWxvcGU7TAAHZmFjdG9yeXQALUxjb20vdml2aWRzb2x1dGlvbnMvanRzL2dlb20vR2VvbWV0cnlGYWN0b3J5O0wACHVzZXJEYXRhdAASTGphdmEvbGFuZy9PYmplY3Q7eHAAAGTocHNyACtjb20udml2aWRzb2x1dGlvbnMuanRzLmdlb20uR2VvbWV0cnlGYWN0b3J5oVig364RTO0CAANJAARTUklETAAZY29vcmRpbmF0ZVNlcXVlbmNlRmFjdG9yeXQAN0xjb20vdml2aWRzb2x1dGlvbnMvanRzL2dlb20vQ29vcmRpbmF0ZVNlcXVlbmNlRmFjdG9yeTtMAA5wcmVjaXNpb25Nb2RlbHQALExjb20vdml2aWRzb2x1dGlvbnMvanRzL2dlb20vUHJlY2lzaW9uTW9kZWw7eHAAAGToc3IAP2NvbS52aXZpZHNvbHV0aW9ucy5qdHMuZ2VvbS5pbXBsLkNvb3JkaW5hdGVBcnJheVNlcXVlbmNlRmFjdG9yeccbYFkwkNFXAgAAeHBzcgAqY29tLnZpdmlkc29sdXRpb25zLmp0cy5nZW9tLlByZWNpc2lvbk1vZGVsa+5kBOmiXDsCAAJEAAVzY2FsZUwACW1vZGVsVHlwZXQAMUxjb20vdml2aWRzb2x1dGlvbnMvanRzL2dlb20vUHJlY2lzaW9uTW9kZWwkVHlwZTt4cAAAAAAAAAAAc3IAL2NvbS52aXZpZHNvbHV0aW9ucy5qdHMuZ2VvbS5QcmVjaXNpb25Nb2RlbCRUeXBls0Z1Mr9ZTUICAAFMAARuYW1ldAASTGphdmEvbGFuZy9TdHJpbmc7eHB0AAhGTE9BVElOR3B1cgApW0xjb20udml2aWRzb2x1dGlvbnMuanRzLmdlb20uTGluZWFyUmluZzv1LANJVSmsswIAAHhwAAAAAHNyACZjb20udml2aWRzb2x1dGlvbnMuanRzLmdlb20uTGluZWFyUmluZ8TdYdncmFlLAgAAeHIAJmNvbS52aXZpZHNvbHV0aW9ucy5qdHMuZ2VvbS5MaW5lU3RyaW5nKytRukNcjjgCAAFMAAZwb2ludHN0ADBMY29tL3Zpdmlkc29sdXRpb25zL2p0cy9nZW9tL0Nvb3JkaW5hdGVTZXF1ZW5jZTt4cQB+AAMAAGTocHEAfgALcHNyADhjb20udml2aWRzb2x1dGlvbnMuanRzLmdlb20uaW1wbC5Db29yZGluYXRlQXJyYXlTZXF1ZW5jZfNLtYhyTnH2AgABWwALY29vcmRpbmF0ZXN0AClbTGNvbS92aXZpZHNvbHV0aW9ucy9qdHMvZ2VvbS9Db29yZGluYXRlO3hwdXIAKVtMY29tLnZpdmlkc29sdXRpb25zLmp0cy5nZW9tLkNvb3JkaW5hdGU77cTUSKr6Q7cCAAB4cAAAAAZzcgAmY29tLnZpdmlkc29sdXRpb25zLmp0cy5nZW9tLkNvb3JkaW5hdGVcvywjXH5YPgIAA0QAAXhEAAF5RAABenhwQRbaBSYVN9NBVayD8KFgcn/4AAAAAAAAc3EAfgAgQRbZ2AvqBKJBVax/IDBisX/4AAAAAAAAc3EAfgAgQRbaQGPe/OxBVax6dDyeln/4AAAAAAAAc3EAfgAgQRbbBAdGBFZBVax78mh/rX/4AAAAAAAAc3EAfgAgQRbbKndkJBFBVayDZnSf/X/4AAAAAAAAc3EAfgAgQRbaBSYVN9NBVayD8KFgcn/4AAAAAAAA\"}]};type=application/json"
    // http://localhost:8890/searches/WUNDA_BLAU.de.cismet.cids.custom.wunda_blau.search.server.CidsAlkisSearchStatement/results?limit=100&offset=0
    // curl -F
    // "params={\"params\":[{\"key\":\"Resulttyp\",\"value\":\"rO0ABX5yAFFkZS5jaXNtZXQuY2lkcy5jdXN0b20ud3VuZGFfYmxhdS5zZWFyY2guc2VydmVyLkNp
    // ZHNBbGtpc1NlYXJjaFN0YXRlbWVudCRSZXN1bHR0eXAAAAAAAAAAABIAAHhyAA5qYXZhLmxhbmcu
    // RW51bQAAAAAAAAAAEgAAeHB0AApGTFVSU1RVRUNL\"},{\"key\":\"Name\",\"value\":\"rO0ABXQABERpZXM=\"},{\"key\":\"Vorname\",\"value\":\"rO0ABXQAA2lzdA==\"},{\"key\":\"Geburtsname\",\"value\":\"rO0ABXQABFRlc3Q=\"},{\"key\":\"Geburtstag\",\"value\":\"rO0ABXQAA2Vpbg==\"},{\"key\":\"Ptyp\",\"value\":\"rO0ABXA=\"},{\"key\":\"Geometry\",\"value\":\"rO0ABXNyACNjb20udml2aWRzb2x1dGlvbnMuanRzLmdlb20uUG9seWdvbs+AAQJyNo5LAgACWwAF
    // aG9sZXN0AClbTGNvbS92aXZpZHNvbHV0aW9ucy9qdHMvZ2VvbS9MaW5lYXJSaW5nO0wABXNoZWxs
    // dAAoTGNvbS92aXZpZHNvbHV0aW9ucy9qdHMvZ2VvbS9MaW5lYXJSaW5nO3hyACRjb20udml2aWRz
    // b2x1dGlvbnMuanRzLmdlb20uR2VvbWV0cnl5nqRlIoVKPgIABEkABFNSSURMAAhlbnZlbG9wZXQA
    // Jkxjb20vdml2aWRzb2x1dGlvbnMvanRzL2dlb20vRW52ZWxvcGU7TAAHZmFjdG9yeXQALUxjb20v
    // dml2aWRzb2x1dGlvbnMvanRzL2dlb20vR2VvbWV0cnlGYWN0b3J5O0wACHVzZXJEYXRhdAASTGph
    // dmEvbGFuZy9PYmplY3Q7eHAAAGTocHNyACtjb20udml2aWRzb2x1dGlvbnMuanRzLmdlb20uR2Vv
    // bWV0cnlGYWN0b3J5oVig364RTO0CAANJAARTUklETAAZY29vcmRpbmF0ZVNlcXVlbmNlRmFjdG9y
    // eXQAN0xjb20vdml2aWRzb2x1dGlvbnMvanRzL2dlb20vQ29vcmRpbmF0ZVNlcXVlbmNlRmFjdG9y
    // eTtMAA5wcmVjaXNpb25Nb2RlbHQALExjb20vdml2aWRzb2x1dGlvbnMvanRzL2dlb20vUHJlY2lz
    // aW9uTW9kZWw7eHAAAGToc3IAP2NvbS52aXZpZHNvbHV0aW9ucy5qdHMuZ2VvbS5pbXBsLkNvb3Jk
    // aW5hdGVBcnJheVNlcXVlbmNlRmFjdG9yeccbYFkwkNFXAgAAeHBzcgAqY29tLnZpdmlkc29sdXRp
    // b25zLmp0cy5nZW9tLlByZWNpc2lvbk1vZGVsa+5kBOmiXDsCAAJEAAVzY2FsZUwACW1vZGVsVHlw
    // ZXQAMUxjb20vdml2aWRzb2x1dGlvbnMvanRzL2dlb20vUHJlY2lzaW9uTW9kZWwkVHlwZTt4cAAA
    // AAAAAAAAc3IAL2NvbS52aXZpZHNvbHV0aW9ucy5qdHMuZ2VvbS5QcmVjaXNpb25Nb2RlbCRUeXBl
    // s0Z1Mr9ZTUICAAFMAARuYW1ldAASTGphdmEvbGFuZy9TdHJpbmc7eHB0AAhGTE9BVElOR3B1cgAp
    // W0xjb20udml2aWRzb2x1dGlvbnMuanRzLmdlb20uTGluZWFyUmluZzv1LANJVSmsswIAAHhwAAAA
    // AHNyACZjb20udml2aWRzb2x1dGlvbnMuanRzLmdlb20uTGluZWFyUmluZ8TdYdncmFlLAgAAeHIA
    // JmNvbS52aXZpZHNvbHV0aW9ucy5qdHMuZ2VvbS5MaW5lU3RyaW5nKytRukNcjjgCAAFMAAZwb2lu
    // dHN0ADBMY29tL3Zpdmlkc29sdXRpb25zL2p0cy9nZW9tL0Nvb3JkaW5hdGVTZXF1ZW5jZTt4cQB+
    // AAMAAGTocHEAfgALcHNyADhjb20udml2aWRzb2x1dGlvbnMuanRzLmdlb20uaW1wbC5Db29yZGlu
    // YXRlQXJyYXlTZXF1ZW5jZfNLtYhyTnH2AgABWwALY29vcmRpbmF0ZXN0AClbTGNvbS92aXZpZHNv
    // bHV0aW9ucy9qdHMvZ2VvbS9Db29yZGluYXRlO3hwdXIAKVtMY29tLnZpdmlkc29sdXRpb25zLmp0
    // cy5nZW9tLkNvb3JkaW5hdGU77cTUSKr6Q7cCAAB4cAAAAAVzcgAmY29tLnZpdmlkc29sdXRpb25z
    // Lmp0cy5nZW9tLkNvb3JkaW5hdGVcvywjXH5YPgIAA0QAAXhEAAF5RAABenhwQRbaBoSzBkxBVayJ
    // uRNB9H/4AAAAAAAAc3EAfgAgQRbZ+wWH0rBBVax4Jd0JrH/4AAAAAAAAc3EAfgAgQRbbRQ2zj89B
    // Vax3Tfktfn/4AAAAAAAAc3EAfgAgQRbbUIzeQfNBVayI4S7ov3/4AAAAAAAAc3EAfgAgQRbaBoSz
    // BkxBVayJuRNB9H/4AAAAAAAA\"}]};type=application/json"
    // http://localhost:8890/searches/WUNDA_BLAU.de.cismet.cids.custom.wunda_blau.search.server.CidsAlkisSearchStatement/results?limit=100&offset=0
    @Override
    public List<ObjectNode> executeSearch(final User user,
            final String searchKey,
            final List<SearchParameter> searchParameters,
            final int limit,
            final int offset,
            final String role) {
        log.info("executing cids server search '" + searchKey + "' with "
                    + searchParameters.size() + " search parameters");
        final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
        final SearchInfo searchInfo = ServerSearchFactory.getFactory().getServerSearchInfo(searchKey);
        final Class<? extends CidsServerSearch> serverSearchClass = ServerSearchFactory.getFactory()
                    .getServerSearchClass(searchKey);

        if ((searchInfo == null) || (serverSearchClass == null)) {
            final String message = "could not find cids server search  '" + searchKey + "'";
            log.error(message);
            throw new RuntimeException(message);
        }

        try {
            final CidsServerSearch cidsServerSearch = ServerSearchFactory.getFactory()
                        .serverSearchInstanceFromSearchParameters(
                            searchInfo,
                            searchParameters);

            final Collection searchResults = LegacyCoreBackend.getInstance()
                        .getService()
                        .customServerSearch(cidsUser, cidsServerSearch);

            final List<ObjectNode> objectNodes;

            if (searchInfo.getResultDescription().getType() == Type.ENTITY_REFERENCE) {
                if (log.isDebugEnabled()) {
                    log.debug("search result of cids server search '"
                                + searchKey + "' is a LightweightMetaObject, need to perform cutom conversion");
                }

                objectNodes = new ArrayList<ObjectNode>();
                final int i = 0;
                MetaClass metaClass = null;
                for (final Object searchResult : searchResults) {
                    if (LightweightMetaObject.class.isAssignableFrom(searchResult.getClass())) {
                        final LightweightMetaObject lightweightMetaObject = (LightweightMetaObject)searchResult;

                        // need to fetch the class only once.
                        // we assume that the collection contains only objects same class ....
                        if (metaClass == null) {
                            final String className = LegacyCoreBackend.getInstance()
                                        .getClassNameForClassId(user, lightweightMetaObject.getClassID());
                            if (log.isDebugEnabled()) {
                                log.debug(
                                    "assuming that the seult collection contains only LightweightMetaObjects of type '"
                                            + className
                                            + "'");
                            }
                            metaClass = LegacyCoreBackend.getInstance().getMetaclassForClassname(className, cidsUser);
                        }

                        final CidsBean cidsBean = CidsBeanFactory.getFactory()
                                    .cidsBeanFromLightweightMetaObject(lightweightMetaObject, metaClass);
                        final ObjectNode objectNode = (ObjectNode)MAPPER.reader()
                                    .readTree(cidsBean.toJSONString(false));
                        objectNodes.add(objectNode);
                    } else {
                        final String message = "cannot convert search result item #"
                                    + i + " to LightweightMetaObject, wrong result type:'"
                                    + searchResult.getClass().getSimpleName() + "' ";
                        log.error(message);
                        throw new RuntimeException(message);
                    }
                }
                if (log.isDebugEnabled()) {
                    log.debug(i + "LightWightMetaObject (entities references) returned by cids server search '"
                                + searchKey + "' and converted to entity references!");
                }
            } else {
                objectNodes = ServerSearchFactory.getFactory()
                            .objectNodesFromResultCollection(
                                    searchResults,
                                    searchInfo,
                                    LegacyCoreBackend.getInstance().getClassNameCache());
            }

            return objectNodes;
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            throw new RuntimeException("error while executing search '" + searchKey + "': " + ex.getMessage(), ex);
        }
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.search"; // NOI18N
    }
}
