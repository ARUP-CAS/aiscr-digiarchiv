/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.incad.arup.thumbnailsgenerator;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CursorMarkParams;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author alberto
 */
public class Indexer {

    public static final Logger LOGGER = Logger.getLogger(Indexer.class.getName());

    private final String DEFAULT_HOST = "http://localhost:8983/solr/";

    Options opts;
    PDFThumbsGenerator pdfGen;
    int imgGenerated;
    int totalDocs = 0;

    SolrClient dokumentClient;
    SolrClient exportClient;
    SolrClient relationsClient;

    public Indexer() {

        try {
            opts = Options.getInstance();

            pdfGen = new PDFThumbsGenerator();
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

    }

    public String host() {
        try {
            Options opts = Options.getInstance();
            return opts.getString("solrhost", DEFAULT_HOST);
        } catch (JSONException | IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        return DEFAULT_HOST;
    }

    public SolrClient getClient(String core) throws IOException {
        SolrClient server = new HttpSolrClient.Builder(String.format("%s%s",
                host(),
                core)).build();
        return server;
    }

    public JSONObject createThumbs(boolean overwrite) throws IOException {
        Date start = new Date();
        totalDocs = 0;

        try {
            File file = new File(Options.getInstance().getString("thumbsDir") + File.separator + "skipped.txt");
            FileUtils.writeStringToFile(file, "Create thums started at " + start.toString() + System.getProperty("line.separator"), "UTF-8", true);
            relationsClient = getClient(opts.getString("csvRelationsCore", "relations/"));
            String sort = opts.getString("uniqueid", "uniqueid");
            int rows = 200;
            SolrQuery query = new SolrQuery();
            //query.setRequestHandler(core);
            query.setQuery("doctype:soubor");
            query.setFilterQueries("dokument:[* TO *]");
            query.setFilterQueries("-dokument:\"\"");
            query.setRows(rows);
            query.setSort(SolrQuery.SortClause.asc(sort));
            query.setTimeAllowed(0);

            String cursorMark = CursorMarkParams.CURSOR_MARK_START;

            boolean done = false;
            QueryResponse rsp = null;

            while (!done) {
                query.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
                try {
                    rsp = relationsClient.query(query);
                } catch (SolrServerException e) {
                    LOGGER.log(Level.SEVERE, null, e);

                    Date end = new Date();

                    String msg = String.format("Generate thumbs finished with error. Thumbs :%1$d", totalDocs);
                    LOGGER.log(Level.INFO, msg);

                    JSONObject jo = new JSONObject();
                    jo.put("result", "error");
                    jo.put("error", e.toString());
                    jo.put("total docs", totalDocs);
                    jo.put("ellapsed time", FormatUtils.formatInterval(end.getTime() - start.getTime()));
                    return jo;

                }

                createThumbs(rsp.getResults(), overwrite);
                //totalDocs += rsp.getResults().size();
                LOGGER.log(Level.INFO, "Currently {0} files processed", totalDocs);

                String nextCursorMark = rsp.getNextCursorMark();
                if (cursorMark.equals(nextCursorMark) || rsp.getResults().size() < rows) {
                    done = true;
                } else {
                    cursorMark = nextCursorMark;
                }
            }

            Date end = new Date();
            String msg = String.format("Generate thumbs finished. Files processed: %1$d. Pdf thumbs: %2$d. Image thumbs: %3$d. Time: %4$tF",
                    totalDocs, pdfGen.generated, imgGenerated, end);
            FileUtils.writeStringToFile(file, msg + System.getProperty("line.separator"), "UTF-8", true);
            LOGGER.log(Level.INFO, msg);
            JSONObject jo = new JSONObject();
            jo.put("result", "Update success");
            jo.put("total thumbs", totalDocs);
            jo.put("ellapsed time", FormatUtils.formatInterval(end.getTime() - start.getTime()));
            relationsClient.close();
            return jo;

        } catch (IOException | JSONException ex) {
            LOGGER.log(Level.SEVERE, null, ex);

            Date end = new Date();

            String msg = String.format("Generate thumbs finished with errors. Thumbs: %1$d", totalDocs);
            File file = new File(Options.getInstance().getString("thumbsDir") + File.separator + "skipped.txt");
            FileUtils.writeStringToFile(file, msg + System.getProperty("line.separator"), "UTF-8", true);
            LOGGER.log(Level.INFO, msg);

            JSONObject jo = new JSONObject();
            jo.put("result", "error");
            jo.put("error", ex.toString());
            jo.put("total docs", totalDocs);
            jo.put("ellapsed time", FormatUtils.formatInterval(end.getTime() - start.getTime()));
            return jo;
        }
    }

    public void createThumb(String nazev, boolean onlySmall) {
        try {

            relationsClient = getClient(opts.getString("csvRelationsCore", "relations/"));
            SolrQuery query = new SolrQuery();
            //query.setRequestHandler(core);
            query.setQuery("nazev:\"" + nazev + "\"");
            query.addFilterQuery("doctype:soubor");

            Options opts = Options.getInstance();
            String imagesDir = opts.getString("imagesDir");

            SolrDocumentList docs = relationsClient.query(query).getResults();
            if (docs.getNumFound() == 0) {
                LOGGER.log(Level.WARNING, "{0} not found", nazev);
                return;
            }
            SolrDocument doc = docs.get(0);

            relationsClient.close();
            //String nazev = doc.getFirstValue("nazev").toString();
            String path = doc.getFirstValue("filepath").toString();
            String mimetype = doc.getFirstValue("mimetype").toString();

            File f = new File(imagesDir + path);
            if (!f.exists()) {
                LOGGER.log(Level.WARNING, "File {0} doesn't exists", f);
                return;
            } else {
                LOGGER.log(Level.INFO, "processing file {0}", f);
                if ("application/pdf".equals(mimetype)) {
                    pdfGen.processFile(f);

                } else {
                    ImageSupport.thumbnailImg(f, nazev);
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    private void createThumbs(SolrDocumentList docs, boolean overwrite) {
        try {
            Options opts = Options.getInstance();
            String imagesDir = opts.getString("imagesDir");
            //ImageSupport.initCount();

            for (SolrDocument doc : docs) {
                String nazev = doc.getFirstValue("nazev").toString();
                String path = doc.getFirstValue("filepath").toString();
                String mimetype = doc.getFirstValue("mimetype").toString();
                //if (overwrite || !ImageSupport.thumbExists(nazev)) {
                if (overwrite || !ImageSupport.folderExists(nazev)) {

                    File f = new File(imagesDir + path);
                    if (!f.exists()) {
                        LOGGER.log(Level.FINE, "File {0} doesn't exists", f);
                    } else {
                        String msg = String.format("Currently Files processed: %1$d. Pdf thumbs: %2$d. Image thumbs: %3$d.",
                                totalDocs, pdfGen.generated, imgGenerated);
                        LOGGER.log(Level.INFO, "processing file {0}. {1}", new Object[]{f, msg});
                        if ("application/pdf".equals(mimetype)) {
                            pdfGen.processFile(f);
//                            ImageSupport.thumbnailPdfPage(f, 0, nazev);
//                            ImageSupport.mediumPdf(f, nazev);
                        } else {
                            ImageSupport.thumbnailzeImg(f, path);
                            imgGenerated++;
                        }
                    }
                }
                totalDocs++;
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

}
