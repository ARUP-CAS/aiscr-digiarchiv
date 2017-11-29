/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.incad.arup.searchapp.index;

import cz.incad.FormatUtils;
import cz.incad.arup.searchapp.Options;
import cz.incad.arup.searchapp.imaging.ImageSupport;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CursorMarkParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author alberto
 */
public class Indexer {

  public static final Logger LOGGER = Logger.getLogger(Indexer.class.getName());

  Options opts;
  AmcrAPI amcr;

  Map<String, JSONObject> heslare;

  SolrClient dokumentClient;
  SolrClient exportClient;
  SolrClient relationsClient;

  public Indexer() {

    try {
      opts = Options.getInstance();
      heslare = new HashMap<>();

    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }

  }

  public JSONObject clean() {
    try {
      dokumentClient = SolrIndex.getClient(opts.getString("dokumentsCore", "dokument/"));
      exportClient = SolrIndex.getClient(opts.getString("exportCore", "export/"));
      relationsClient = SolrIndex.getClient(opts.getString("csvRelationsCore", "relations/"));
      
      dokumentClient.deleteByQuery("*:*", 100);
      exportClient.deleteByQuery("*:*", 100);
      relationsClient.deleteByQuery("*:*", 100);
      return new JSONObject().put("message", "Index cleaned");
    } catch (IOException | JSONException | SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      return new JSONObject().put("error", ex);
    }
  }

  private JSONObject getHeslar(String id) {
    try {
      LOGGER.log(Level.INFO, "Vyplnim heslar {0}...", id);
      if (id.equals("katastr")) {
        //to je zvlastni pripad
        JSONObject k1 = amcr.getHeslar("katastry1");
        JSONObject k2 = amcr.getHeslar("katastry2");

        Iterator<?> keys = k2.keys();

        while (keys.hasNext()) {
          String key = (String) keys.next();
          k1.put(key, k2.getJSONObject(key));
        }

        return k1;
      } else {
        return amcr.getHeslar(id);

      }

    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);

      return new JSONObject();
    }

  }

  private void fillHeslar(String id) {
    try {
      LOGGER.log(Level.INFO, "Vyplnim heslar {0}...", id);
      if (id.equals("katastr")) {
        //to je zvlastni pripad
        JSONObject k1 = amcr.getHeslar("katastry1");
        JSONObject k2 = amcr.getHeslar("katastry2");

        Iterator<?> keys = k2.keys();

        while (keys.hasNext()) {
          String key = (String) keys.next();
          k1.put(key, k2.getJSONObject(key));
        }

        heslare.put(id, k1);
      } else {
        heslare.put(id, amcr.getHeslar(id));

      }

    } catch (Exception ex) {
      Logger.getLogger(Indexer.class
              .getName()).log(Level.SEVERE, null, ex);
    }

  }

  private void translateDoc(JSONObject doc) {
    JSONObject trans = opts.getJSONObject("translations");
    Iterator<?> keys = trans.keys();

    while (keys.hasNext()) {
      String field = (String) keys.next();
      if (doc.has(field)) {
        doc.put(field, translate(field, doc.getInt(field) + ""));
      }
    }
  }

  private String translate(String field, String id) {
    JSONObject translation = opts.getJSONObject("translations").getJSONObject(field);
    String heslar = translation.getString("heslar");
    if (!heslare.containsKey(heslar)) {
      fillHeslar(heslar);
    }
    if (heslare.get(heslar).has(id)) {
      return heslare.get(heslar).getJSONObject(id).getString(translation.getString("label"));
    } else {
      return id;
    }

  }

  public JSONObject indexHeslare() {
    try {
      amcr = new AmcrAPI();
      boolean logged = amcr.connect();
      if (!logged) {
        return new JSONObject().put("error", "Cant connect");
      }
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      return new JSONObject().put("error", ex.toString());
    }

    Date start = new Date();

    int total = 0;
    String[] hs = opts.getStrings("heslare");
    for (String h : hs) {
      JSONObject jh = getHeslar(h);

      JSONArray docs = new JSONArray();
      Iterator keys = jh.keys();
      while (keys.hasNext()) {
        String key = (String) keys.next();
        int id = jh.getJSONObject(key).getInt("id");
        docs.put(jh.getJSONObject(key).put("uniqueid", h + "_" + id).put("heslar_name", h));
        total++;
      }

      postSolr(docs.toString(), "heslar/");

    }

    Date end = new Date();

    String msg = String.format("Index heslar finished. Docs found :%1$d", total);
    LOGGER.log(Level.INFO, msg);

    JSONObject jo = new JSONObject();
    jo.put("result", "Heslar indexed successfully");
    jo.put("total docs", total);

    jo.put("ellapsed time", FormatUtils.formatInterval(end.getTime() - start.getTime()));

    return jo;

  }

  public JSONObject indexHeslar(String heslar) {

    try {
      amcr = new AmcrAPI();
      amcr.connect();
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      return new JSONObject().put("error", ex.toString());
    }

    Date start = new Date();

    int total = 0;
    if (heslar == null) {
      String[] hs = opts.getStrings("heslare");
      for (String h : hs) {
        JSONObject jh = getHeslar(h);

        JSONArray docs = new JSONArray();
        Iterator keys = jh.keys();
        while (keys.hasNext()) {
          String key = (String) keys.next();
          int id = jh.getJSONObject(key).getInt("id");
          docs.put(jh.getJSONObject(key).put("uniqueid", h + "_" + id).put("heslar_name", h));
          total++;
        }

        postSolr(docs.toString(), "heslar/");

      }
    } else {
      if (!heslare.containsKey(heslar)) {
        fillHeslar(heslar);
      }

      JSONArray docs = new JSONArray();
      Iterator keys = heslare.get(heslar).keys();
      while (keys.hasNext()) {
        String key = (String) keys.next();
        int id = heslare.get(heslar).getJSONObject(key).getInt("id");
        docs.put(heslare.get(heslar).getJSONObject(key).put("uniqueid", heslar + "_" + id).put("heslar_name", heslar));
        total++;
      }

      postSolr(docs.toString(), "heslar/");
    }

    Date end = new Date();

    String msg = String.format("Index heslar finished. Docs found :%1$d", total);
    LOGGER.log(Level.INFO, msg);

    JSONObject jo = new JSONObject();
    jo.put("result", "Heslar indexed successfully");
    jo.put("total docs", total);

    jo.put("ellapsed time", FormatUtils.formatInterval(end.getTime() - start.getTime()));

    return jo;
  }

  public JSONObject full() throws Exception {
    return update("-1", "-1");
  }

  public JSONObject update(String rok_od, String rok_do) throws Exception {

    amcr = new AmcrAPI();
    amcr.connect();
    Date start = new Date();
    int limit = 100;
    int found = limit;
    int total = 0;
    int totalchilds = 0;
    int stranka = 0;

    while (found == limit) {
      JSONArray ids = new JSONArray();
      found = amcr.getIds(rok_od, rok_do, stranka, limit, ids);
      total += found;

      JSONArray docs = new JSONArray();

      for (int i = 0; i < ids.length(); i++) {

        int docid = ids.getJSONObject(i).getInt("id");

        //Ziskame zakladni info
        JSONObject doc = new JSONObject();
        //JSONObject doc = amcr.getDocById(docid, false);
        HashMap infoDoc = amcr.nactiInfoMap("dokument", docid, true);

        Iterator it1 = infoDoc.entrySet().iterator();
        while (it1.hasNext()) {
          Map.Entry pair = (Map.Entry) it1.next();
          Object val = pair.getValue();
          if (val instanceof HashMap) {

          } else {
            doc.put((String) pair.getKey(), val);
          }

          //it.remove(); // avoids a ConcurrentModificationException
        }

        String typ_dokumentu = doc.getString("typ_dokumentu");
        String kategorie = opts.getJSONObject("kategorie").getString(typ_dokumentu);
        doc.put("uniqueid", "dokument_" + docid);
        doc.put("kategorie", kategorie);
        doc.put("doctype", "dokument");

        //Hledame vazby
        JSONArray vazby = amcr.getVazby(docid).getJSONArray("vazby_na_dokument");
        if (vazby.length() > 0) {

          //totalchilds += vazby.length();
          JSONArray childs = new JSONArray();
          for (int v = 0; v < vazby.length(); v++) {

            /* typ
              1 = akce
              5 = lokalita
              23 = neidentifikovaná akce
             */
            int typint = vazby.getJSONObject(v).getInt("type");
            if (typint != 23) {
              String typ = opts.getJSONObject("typ_vazby").getString(typint + "");
              int vazba = vazby.getJSONObject(v).getInt("vazba");
              //Vynechame 23
              HashMap info = amcr.nactiInfoMap(typ, vazba, true);

              if (info.size() > 0) {
                JSONObject vj = new JSONObject();
                Iterator it = info.entrySet().iterator();
                while (it.hasNext()) {
                  Map.Entry pair = (Map.Entry) it.next();
                  Object val = pair.getValue();
                  if (val instanceof HashMap) {
                    // Hledame "napojene_dokumenty"
                    if ("napojene_dokumenty".equals((String) pair.getKey())) {
                      HashMap ndMap = (HashMap) val;
                      if (!ndMap.isEmpty()) {
                        JSONArray nd = new JSONArray();
                        Iterator itNd = ndMap.entrySet().iterator();
                        while (itNd.hasNext()) {
                          Map.Entry ndPair = (Map.Entry) itNd.next();
                          HashMap ndVal = (HashMap) ndPair.getValue();
                          nd.put(ndVal.get("id_cj"));
                        }
                        vj.put((String) pair.getKey(), nd);
                      }
                    }

                  } else {
                    vj.put((String) pair.getKey(), val);
                  }

                  //it.remove(); // avoids a ConcurrentModificationException
                }
                vj.put("uniqueid", typ + "_" + info.get("id"));
                vj.put("doctype", typ);
                translateDoc(vj);
                childs.put(vj);
              } else {
                LOGGER.log(Level.INFO, "Vazba prazdna id: {0}, typ {1}", new Object[]{vazba, typ});
              }
            }
          }
          doc.put("_childDocuments_", childs);
          totalchilds += childs.length();
        }
        translateDoc(doc);

        docs.put(doc);
      }

      postSolr(docs.toString());
      LOGGER.log(Level.INFO, "indexing... Already sent {0} docs", total);

      stranka++;
    }
    commit();
    Date end = new Date();

    String msg = String.format("Update finished. Docs found :%1$d", total);
    LOGGER.log(Level.INFO, msg);

    JSONObject jo = new JSONObject();
    jo.put("result", "Update success");
    jo.put("total docs", total);
    jo.put("total child docs", totalchilds);
    jo.put("ellapsed time", FormatUtils.formatInterval(end.getTime() - start.getTime()));

    return jo;
  }

  public JSONObject createThumbs(boolean overwrite) throws IOException {
    Date start = new Date();
    int total = 0;

    try {
      File file = new File(Options.getInstance().getString("thumbsDir") + File.separator + "skipped.txt");
      FileUtils.writeStringToFile(file, "Create thums started at " + start.toString() + System.getProperty("line.separator"), true);
      relationsClient = SolrIndex.getClient(opts.getString("csvRelationsCore", "relations/"));
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

          String msg = String.format("Generate thumbs finished with error. Thumbs :%1$d", total);
          LOGGER.log(Level.INFO, msg);

          JSONObject jo = new JSONObject();
          jo.put("result", "error");
          jo.put("error", e.toString());
          jo.put("total docs", total);
          jo.put("ellapsed time", FormatUtils.formatInterval(end.getTime() - start.getTime()));
          return jo;

        }

        createThumbs(rsp.getResults(), overwrite);
        total += rsp.getResults().size();
        LOGGER.log(Level.INFO, "Currently {0} thumbs generated", total);

        String nextCursorMark = rsp.getNextCursorMark();
        if (cursorMark.equals(nextCursorMark) || rsp.getResults().size() < rows) {
          done = true;
        } else {
          cursorMark = nextCursorMark;
        }
      }

      Date end = new Date();
      String msg = String.format("Generate thumbs finished. Thumbs: %1$d. %2$tF", total, end);
      FileUtils.writeStringToFile(file, msg + System.getProperty("line.separator"), true);
      LOGGER.log(Level.INFO, msg);
      JSONObject jo = new JSONObject();
      jo.put("result", "Update success");
      jo.put("total thumbs", total);
      jo.put("ellapsed time", FormatUtils.formatInterval(end.getTime() - start.getTime()));
      return jo;

    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);

      Date end = new Date();

      String msg = String.format("Generate thumbs finished with errors. Thumbs: %1$d", total);
      File file = new File(Options.getInstance().getString("thumbsDir") + File.separator + "skipped.txt");
      FileUtils.writeStringToFile(file, msg + System.getProperty("line.separator"), true);
      LOGGER.log(Level.INFO, msg);

      JSONObject jo = new JSONObject();
      jo.put("result", "error");
      jo.put("error", ex.toString());
      jo.put("total docs", total);
      jo.put("ellapsed time", FormatUtils.formatInterval(end.getTime() - start.getTime()));
      return jo;
    }
  }

  public String createThumb(String nazev) {
    try {

      relationsClient = SolrIndex.getClient(opts.getString("csvRelationsCore", "relations/"));
      SolrQuery query = new SolrQuery();
      //query.setRequestHandler(core);
      query.setQuery("nazev:\"" + nazev + "\"");
      query.addFilterQuery("doctype:soubor");

      Options opts = Options.getInstance();
      String imagesDir = opts.getString("imagesDir");

      SolrDocumentList docs = relationsClient.query(query).getResults();
      if (docs.getNumFound() == 0) {
        return null;
      }
      SolrDocument doc = docs.get(0);

      //String nazev = doc.getFirstValue("nazev").toString();
      String path = doc.getFirstValue("filepath").toString();
      String mimetype = doc.getFirstValue("mimetype").toString();

      File f = new File(imagesDir + path);
      if (!f.exists()) {
        LOGGER.log(Level.WARNING, "File {0} doesn't exists", f);
        return null;
      } else {
        LOGGER.log(Level.INFO, "processing file {0}", f);
        if ("application/pdf".equals(mimetype)) {
          ImageSupport.mediumPdf(f, nazev);
          return ImageSupport.thumbnailPdfPage(f, 0, nazev);
        } else {
          return ImageSupport.thumbnailImg(f, nazev);
        }
      }

    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      return null;
    }
  }

  private void createThumbs(SolrDocumentList docs, boolean overwrite) {
    try {
      Options opts = Options.getInstance();
      String imagesDir = opts.getString("imagesDir");
      ImageSupport.initCount();

      for (SolrDocument doc : docs) {
        String nazev = doc.getFirstValue("nazev").toString();
        String path = doc.getFirstValue("filepath").toString();
        String mimetype = doc.getFirstValue("mimetype").toString();
        //if (overwrite || !ImageSupport.thumbExists(nazev)) {
        if (overwrite || !ImageSupport.folderExists(nazev)) {

          File f = new File(imagesDir + path);
          if (!f.exists()) {
            LOGGER.log(Level.WARNING, "File {0} doesn't exists", f);
          } else {
            LOGGER.log(Level.INFO, "processing file {0}. Currently generated {1}", new Object[]{f, ImageSupport.generated});
            if ("application/pdf".equals(mimetype)) {
              ImageSupport.thumbnailPdfPage(f, 0, nazev);
              ImageSupport.mediumPdf(f, nazev);
            } else {
              ImageSupport.thumbnailImg(f, nazev);
            }
          }
        }
      }
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  public JSONObject indexExport() {
    Date start = new Date();
    int total = 0;

    try {
      dokumentClient = SolrIndex.getClient(opts.getString("dokumentsCore", "dokument/"));
      exportClient = SolrIndex.getClient(opts.getString("exportCore", "export/"));
      relationsClient = SolrIndex.getClient(opts.getString("csvRelationsCore", "relations/"));
      String sort = opts.getString("uniqueid", "uniqueid");
      int rows = 200;
      SolrQuery query = new SolrQuery();
      //query.setRequestHandler(core);
      query.setQuery("doctype:dokument");
      query.setRows(rows);
      query.setSort(SolrQuery.SortClause.asc(sort));
      query.setTimeAllowed(0);

      String cursorMark = CursorMarkParams.CURSOR_MARK_START;

      boolean done = false;
      QueryResponse rsp = null;

      while (!done) {
        query.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
        try {
          rsp = exportClient.query(query);
        } catch (SolrServerException e) {
          LOGGER.log(Level.SEVERE, null, e);

          Date end = new Date();

          String msg = String.format("Update finished. Docs found :%1$d", total);
          LOGGER.log(Level.INFO, msg);

          JSONObject jo = new JSONObject();
          jo.put("result", "error");
          jo.put("error", e.toString());
          jo.put("total docs", total);
          jo.put("ellapsed time", FormatUtils.formatInterval(end.getTime() - start.getTime()));
          return jo;

        }

        createDocuments(rsp.getResults());
        total += rsp.getResults().size();
        LOGGER.log(Level.INFO, "Currently {0} docs sent to index", total);

        String nextCursorMark = rsp.getNextCursorMark();
        if (cursorMark.equals(nextCursorMark) || rsp.getResults().size() < rows) {
          done = true;
        } else {
          cursorMark = nextCursorMark;
        }
      }

      Date end = new Date();

      String msg = String.format("Update finished. Docs found :%1$d", total);
      LOGGER.log(Level.INFO, msg);
      JSONObject jo = new JSONObject();
      jo.put("result", "Update success");
      jo.put("total docs", total);
      jo.put("ellapsed time", FormatUtils.formatInterval(end.getTime() - start.getTime()));
      return jo;

    } catch (IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);

      Date end = new Date();

      String msg = String.format("Update finished. Docs found :%1$d", total);
      LOGGER.log(Level.INFO, msg);

      JSONObject jo = new JSONObject();
      jo.put("result", "error");
      jo.put("error", ex.toString());
      jo.put("total docs", total);
      jo.put("ellapsed time", FormatUtils.formatInterval(end.getTime() - start.getTime()));
      return jo;
    }
  }

  private void createDocuments(SolrDocumentList docs) {
    try {
      ArrayList<SolrInputDocument> idocs = new ArrayList<>();
      for (SolrDocument doc : docs) {
        SolrInputDocument idoc = new SolrInputDocument();
        String uniqueid = doc.get("uniqueid").toString();
        idoc.addField("uniqueid", uniqueid);
        idoc.addField("doctype", "dokument");
        addFields(idoc, doc, "");
        getExtraData(uniqueid, idoc);
        getSoubory(uniqueid, idoc);
        getOdkaz(uniqueid, idoc);
        getJednotkaDokumentu(uniqueid, idoc);
        if (doc.containsKey("centroid_e")) {

          getPian(doc.getFirstValue("centroid_e").toString(), idoc);
        }
        idocs.add(idoc);

      }
      dokumentClient.add(idocs);
      dokumentClient.commit();
    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private void getPian(String id, SolrInputDocument idoc) {

  }

  private void getExtraData(String uniqueid, SolrInputDocument idoc) {
    try {
      SolrQuery query = new SolrQuery();
      //query.setRequestHandler(core);
      query.setQuery("dokument:\"" + uniqueid + "\"");
      query.setFilterQueries("doctype:\"extra_data\"");
      JSONObject resp = SolrIndex.json(query, "relations/");
      idoc.addField("extra_data", resp.getJSONObject("response").getJSONArray("docs"));
//      SolrDocumentList docs = relationsClient.query(query).getResults();
//      if (docs.isEmpty()) {
//        LOGGER.log(Level.FINE, "Doc {0} has no extra data", uniqueid);
//      } else {
//        for (SolrDocument doc : docs) {
//          addFields(idoc, doc, "");
//        }
//      }
    } catch (IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private void getSoubory(String uniqueid, SolrInputDocument idoc) {
    try {
      SolrQuery query = new SolrQuery();
      //query.setRequestHandler(core);
      query.setQuery("dokument:\"" + uniqueid + "\"");
      query.setFilterQueries("doctype:\"soubor\"");
      JSONObject resp = SolrIndex.json(query, "relations/");
      idoc.addField("soubor", resp.getJSONObject("response").getJSONArray("docs"));
//      SolrDocumentList docs = relationsClient.query(query).getResults();
//      if (docs.isEmpty()) {
//        LOGGER.log(Level.FINE, "Doc {0} has no files", uniqueid);
//      } else {
//        for (SolrDocument doc : docs) {
//          addFields(idoc, doc, "soubor_");
//        }
//      }
    } catch (IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private void getOdkaz(String uniqueid, SolrInputDocument idoc) {
    try {
      SolrQuery query = new SolrQuery();
      //query.setRequestHandler(core);
      query.setQuery("dokument:\"" + uniqueid + "\"");
      query.setFilterQueries("doctype:\"odkaz\"");
      SolrDocumentList docs = relationsClient.query(query).getResults();
      if (docs.isEmpty()) {
        LOGGER.log(Level.FINE, "Doc {0} has no odkaz", uniqueid);
      } else {
        for (SolrDocument doc : docs) {
          String childid = doc.getFirstValue("vazba").toString();
          getChilds(idoc, childid, "odkaz");
        }
      }
    } catch (SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private void getJednotkaDokumentu(String uniqueid, SolrInputDocument idoc) {
    try {
      SolrQuery query = new SolrQuery();
      //query.setRequestHandler(core);
      query.setQuery("dokument:\"" + uniqueid + "\"");
      query.setFilterQueries("doctype:\"jednotka_dokument\"");
      SolrDocumentList docs = exportClient.query(query).getResults();
      if (docs.isEmpty()) {
        LOGGER.log(Level.FINE, "Doc {0} has no jednotka_dokument", uniqueid);
      } else {
        for (SolrDocument doc : docs) {
          String childid = doc.getFirstValue("vazba").toString();
          getChilds(idoc, childid, "jednotka_dokument");
        }
      }
    } catch (SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  /**
   * Hleda akce a lokality u jednotky dokumentu
   *
   * @param idoc
   * @param uniqueid obsah pole vazba u jednotky dokumentu
   * @param child prefix pridan pro nazev pole v solru
   */
  private void getChilds(SolrInputDocument idoc, String uniqueid, String child) {
    try {
      SolrQuery query = new SolrQuery();
      //query.setRequestHandler(core);
      query.setQuery("uniqueid:\"" + uniqueid + "\"");

      JSONObject resp = SolrIndex.json(query, "export/");
      idoc.addField(child, resp.getJSONObject("response").getJSONArray("docs").toString());

//      SolrDocumentList docs = exportClient.query(query).getResults();
//      if (docs.isEmpty()) {
//        LOGGER.log(Level.INFO, "Doc {0} has no child", uniqueid);
//      } else {
//        for (SolrDocument doc : docs) {
//          
//          String doctype = doc.getFieldValue("doctype").toString();
//          addFields(idoc, doc, doctype + "_");
//        }
//      }
    } catch (IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private void addFields(SolrInputDocument idoc, SolrDocument rdoc, String prefix) {
    Iterator<String> names = rdoc.getFieldNames().iterator();
    Map<String, Collection<Object>> values = rdoc.getFieldValuesMap();

    while (names.hasNext()) {
      String name = names.next();
      if (!"uniqueid".equals(name) && !"_version_".equals(name) && !"doctype".equals(name)) {

        Collection<Object> vals = values.get(name);
        Iterator<Object> valsIter = vals.iterator();
        while (valsIter.hasNext()) {
          Object obj = valsIter.next();
          idoc.addField(prefix + name, obj);
        }
      }
    }
  }

  private void postSolr(String jo) {
    postSolr(jo, opts.getString("core", "amcr/"));
  }

  private void postSolr(String jo, String core) {
    try {
      String url = opts.getString("solrhost", "http://localhost:8983/solr/")
              + core + "update/json?wt=json&commitWithin=10000";

      HttpClient httpClient = HttpClientBuilder.create().build();
      HttpPost post = new HttpPost(url);

      StringEntity entity = new StringEntity(jo, "UTF-8");
      entity.setContentType("application/json");
      post.setEntity(entity);
      HttpResponse response = httpClient.execute(post);
      HttpEntity httpEntity = response.getEntity();
      InputStream in = httpEntity.getContent();

      String encoding = httpEntity.getContentEncoding() == null ? "UTF-8" : httpEntity.getContentEncoding().getName();
      encoding = encoding == null ? "UTF-8" : encoding;
      String responseText = IOUtils.toString(in, encoding);
      LOGGER.log(Level.FINE, responseText);
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, null, e);
    }
  }

  private void commit() {
    try {
      String url = opts.getString("solrhost", "http://localhost:8983/solr/")
              + opts.getString("core", "amcr/") + "update?commit=true";

      HttpClient httpClient = HttpClientBuilder.create().build();
      HttpGet get = new HttpGet(url);

      HttpResponse response = httpClient.execute(get);
      HttpEntity httpEntity = response.getEntity();
      InputStream in = httpEntity.getContent();

      String encoding = httpEntity.getContentEncoding() == null ? "UTF-8" : httpEntity.getContentEncoding().getName();
      encoding = encoding == null ? "UTF-8" : encoding;
      String responseText = IOUtils.toString(in, encoding);
      LOGGER.log(Level.FINE, responseText);
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, null, e);
    }

  }

  public JSONObject addFav(String username, String docid) {
    int total = 0;

    try {
      SolrClient favClient = SolrIndex.getClient(opts.getString("favoritesCore", "favorites/"));

      SolrInputDocument idoc = new SolrInputDocument();
      idoc.addField("uniqueid", username + "_" + docid);
      idoc.addField("docid", docid);
      idoc.addField("username", username);
      favClient.add(idoc);
      favClient.commit();

      String msg = String.format("Update finished. Docs found :%1$d", total);
      LOGGER.log(Level.INFO, msg);
      JSONObject jo = new JSONObject();
      jo.put("msg", msg);
      return jo;

    } catch (IOException | SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);

      JSONObject jo = new JSONObject();
      jo.put("result", "error");
      jo.put("error", ex.toString());
      return jo;
    }
  }

  public JSONObject removeFav(String uniqueid) {
    int total = 0;

    try {
      SolrClient favClient = SolrIndex.getClient(opts.getString("favoritesCore", "favorites/"));

      favClient.deleteById(uniqueid);
      favClient.commit();

      String msg = String.format("Update finished. Docs found :%1$d", total);
      LOGGER.log(Level.INFO, msg);
      JSONObject jo = new JSONObject();
      jo.put("msg", msg);
      return jo;

    } catch (IOException | SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);

      JSONObject jo = new JSONObject();
      jo.put("result", "error");
      jo.put("error", ex.toString());
      return jo;
    }
  }
}
