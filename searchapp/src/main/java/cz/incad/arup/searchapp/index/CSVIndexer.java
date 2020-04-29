package cz.incad.arup.searchapp.index;

//import au.com.bytecode.opencsv.CSVReader;
import cz.incad.FormatUtils;
import cz.incad.arup.searchapp.I18n;
import cz.incad.arup.searchapp.Options;
import cz.incad.arup.searchapp.imaging.ImageSupport;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author alberto
 */
public class CSVIndexer {

  public static final Logger LOGGER = Logger.getLogger(CSVIndexer.class.getName());

  Options opts;
  String apiPoint;
  int success = 0;
  int errors = 0;

  SolrClient relationsClient;
  SolrClient exportClient;
  SolrClient dokumentClient;
  SolrClient translationsClient;

  ArrayList<String> csvExcludeRelations;
  ArrayList<String> csvStavyAkce;
  ArrayList<String> csvStavyLokalita;
  ArrayList<String> csvStavyPas;

  Map<String, String> obdobi_poradi;

  public CSVIndexer() throws IOException {

    opts = Options.getInstance();

    apiPoint = opts.getString("csvApiPoint");
    csvExcludeRelations = new ArrayList<>();
    for (int i = 0; i < opts.getJSONArray("csvExcludeRelations").length(); i++) {
      csvExcludeRelations.add(opts.getJSONArray("csvExcludeRelations").getString(i));
    }

    csvStavyAkce = new ArrayList<>();
    for (int i = 0; i < opts.getJSONArray("csvStavyAkce").length(); i++) {
      csvStavyAkce.add(opts.getJSONArray("csvStavyAkce").getString(i));
    }

    csvStavyLokalita = new ArrayList<>();
    for (int i = 0; i < opts.getJSONArray("csvStavyLokalita").length(); i++) {
      csvStavyLokalita.add(opts.getJSONArray("csvStavyLokalita").getString(i));
    }

    csvStavyPas = new ArrayList<>();
    for (int i = 0; i < opts.getJSONArray("csvStavyPas").length(); i++) {
      csvStavyPas.add(opts.getJSONArray("csvStavyPas").getString(i));
    }
  }

  private void getClients() {
    try {
      relationsClient = SolrIndex.getClient(opts.getString("csvRelationsCore", "relations/"));
      exportClient = SolrIndex.getClient(opts.getString("exportCore", "export/"));
      dokumentClient = SolrIndex.getClient(opts.getString("dokumentsCore", "dokument/"));
      translationsClient = SolrIndex.getClient("translations/");
    } catch (IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private void closeClients() {

    try {
      relationsClient.close();
      exportClient.close();
      dokumentClient.close();
      translationsClient.close();
    } catch (IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  public JSONObject run() {

    JSONObject ret = new JSONObject();
    try {

      getClients();

      success = 0;
      errors = 0;

      ret.put("translations", indexTranslations());
      ret.put("relations", indexSource(relationsClient, opts.getJSONArray("csvRelationTables"), null, false));
      ret.put("tables", indexSource(exportClient, opts.getJSONArray("csvTables"), "ident_cely", false));
      ret.put("dokuments", indexSource(dokumentClient, new JSONArray().put("dokument"), "ident_cely", true));
      ret.put("pas", indexSource(dokumentClient, new JSONArray().put("pas"), "ident_cely", true));

      closeClients();

    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  public JSONObject indexTranslations() {

    JSONObject ret = new JSONObject();
    try {
      getClients();
      translationsClient.deleteByQuery("*:*", 1);
      translationsClient.commit();
      Date start = new Date();
      JSONArray ja = new JSONArray();
      ret.put("errors msgs", ja);
      String thesauriDir = opts.getString("thesauriDir");
      File dir = new File(thesauriDir);
      LOGGER.log(Level.INFO, "indexing from {0}", thesauriDir);
      for (File file : dir.listFiles()) {
        LOGGER.log(Level.INFO, "indexing from {0}", file.getName());
        // Reader in = new FileReader(file);
        InputStreamReader in = new InputStreamReader(new FileInputStream(file), Charset.forName("UTF-8"));
        // Reader in = new FileReader(file, Charset.forName("UTF-8")); 
        //readOne( , uniqueid, "", translationsClient, ret, hasRelations);

        Date tstart = new Date();
        int tsuccess = 0;
        int terrors = 0;
        JSONObject typeJson = new JSONObject();

        //CSVFormat f = CSVFormat.newFormat('#').withEscape('\\').withQuote('\"').withFirstRecordAsHeader();
        CSVFormat f = CSVFormat.newFormat('#').withEscape('\\').withFirstRecordAsHeader();
        CSVParser parser = new CSVParser(in, f);
        Map<String, Integer> header = parser.getHeaderMap();
        try {

          for (final CSVRecord record : parser) {
            try {
              SolrInputDocument doc = new SolrInputDocument();

              doc.addField("id", record.get(0) + "_" + record.get(2));
              for (Map.Entry<String, Integer> entry : header.entrySet()) {
                doc.addField(entry.getKey().toLowerCase().trim(), record.get(entry.getKey()));
              }

              translationsClient.add(doc);
              tsuccess++;
              success++;
              if (success % 500 == 0) {
                translationsClient.commit();
                LOGGER.log(Level.INFO, "Indexed {0} docs", success);
              }
            } catch (Exception ex) {
              terrors++;
              errors++;
              ret.getJSONArray("errors msgs").put(record);
              LOGGER.log(Level.SEVERE, "Error indexing doc {0}", record);
              LOGGER.log(Level.SEVERE, null, ex);
            }
          }

          translationsClient.commit();

          typeJson.put("docs indexed", tsuccess).put("errors", terrors);
          Date tend = new Date();

          typeJson.put("ellapsed time", FormatUtils.formatInterval(tend.getTime() - tstart.getTime()));
          ret.put(file.getName(), typeJson).put("docs indexed", success);
        } finally {
          parser.close();
        }

      }
      I18n.resetInstance();
      LOGGER.log(Level.INFO, "Indexed Finished. {0} success, {1} errors", new Object[]{success, errors});

      ret.put("errors", errors);

      Date end = new Date();
      ret.put("ellapsed time", FormatUtils.formatInterval(end.getTime() - start.getTime()));
    } catch (IOException | SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
    return ret;

  }

  private void addRelations(String uniqueid, String doctype, SolrInputDocument doc) {
    if (doctype.equals("dokument")) {
      String typ_dokumentu = (String) doc.getField("typ_dokumentu").getValue();
      String kategorie = opts.getJSONObject("kategoriet").optString(typ_dokumentu);
      doc.addField("kategorie", kategorie);
      //doc.addField("dokument", uniqueid);
      try {
        //add extra_data to dokument
        SolrQuery query = new SolrQuery();
        query.setQuery("dokument:\"" + uniqueid + "\"");
        query.setFilterQueries("doctype:\"extra_data\"");
        query.setRows(100);
        //JSONObject resp = SolrIndex.json(query, "relations/");
        //doc.addField("extra_data", resp.getJSONObject("response").getJSONArray("docs"));
        SolrDocumentList docs = relationsClient.query(query).getResults();
        if (docs.isEmpty()) {
          LOGGER.log(Level.FINE, "Doc {0} has no extra data", uniqueid);
        } else {
          for (SolrDocument qdoc : docs) {
            addFields(doc, qdoc, "extra_data");
          }
        }

        if (doc.containsKey("let")) {
          String let = (String) doc.getFieldValue("let");
          getLet(let, doc);
        }
        getSoubory(uniqueid, doc);
        getOdkaz(uniqueid, doc);
        getTvar(uniqueid, doc);
        getJednotkaDokument(uniqueid, doc);

      } catch (IOException | SolrServerException ex) {
        LOGGER.log(Level.SEVERE,
                "Error adding relations,\n uniqueid: {0} \n doctype: {1} \n doc {2}",
                new Object[]{uniqueid, doctype, doc});
        LOGGER.log(Level.SEVERE, null, ex);
      }
    } else if (doctype.equals("pas")) {

      try {
        //add soubory to samostatny_nalez
        SolrQuery query = new SolrQuery();

        query.setQuery("samostatny_nalez:\"" + uniqueid + "\"");
        query.setFilterQueries("doctype:\"soubor\"");
        query.setRows(100);
        JSONObject resp = SolrIndex.json(query, "relations/");
        //LOGGER.log(Level.INFO, resp.getJSONObject("response").getJSONArray("docs").toString());
        if (resp.getJSONObject("response").getJSONArray("docs").length() > 0) {
          doc.addField("soubor", resp.getJSONObject("response").getJSONArray("docs").toString());

          String filepath = resp.getJSONObject("response").getJSONArray("docs").getJSONObject(0).optJSONArray("filepath").getString(0);
          String dest = ImageSupport.getDestDir(filepath);
          String fname = dest + filepath + "_thumb.jpg";

          //LOGGER.log(Level.INFO, fname);
          doc.addField("hasThumb", new File(fname).exists() || new File(dest + "_thumb.jpg").exists());

        } else {
          doc.addField("hasThumb", true);
        }
      } catch (IOException ex) {
        LOGGER.log(Level.SEVERE,
                "Error adding files,\n uniqueid: {0} \n doctype: {1} \n doc {2}",
                new Object[]{uniqueid, doctype, doc}); 
        LOGGER.log(Level.SEVERE, null, ex);
      }
    }
  }

  private void addSearchFields(SolrInputDocument idoc, String doctype) {
    if (doctype.equals("dokument") || doctype.equals("pas")) {
      List<String> excludePas = Arrays.asList(opts.getStrings("pasSecuredFields"));
      Object[] fields = idoc.getFieldNames().toArray();
      String pristupnost = (String) idoc.getFieldValue("pristupnost");
      for (Object f : fields) {
        String s = (String) f;
        idoc.addField("full_text_logged", idoc.getFieldValues(s));
        if (!"pas".equals(doctype) || !excludePas.contains(s) || "A".equals(pristupnost)) {
          idoc.addField("full_text_notlogged", idoc.getFieldValues(s));
        }
      }
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
          getChilds(idoc, childid);
        }
      }
    } catch (IOException | SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private void getTvar(String uniqueid, SolrInputDocument idoc) {
    try {
      SolrQuery query = new SolrQuery();
      //query.setRequestHandler(core);
      query.setQuery("dokument:\"" + uniqueid + "\"");
      query.setFilterQueries("doctype:\"tvar\"");
      SolrDocumentList docs = relationsClient.query(query).getResults();
      if (docs.isEmpty()) {
        LOGGER.log(Level.FINE, "Doc {0} has no odkaz", uniqueid);
      } else {
        for (SolrDocument doc : docs) {
          addFields(idoc, doc, "tvar");
        }
      }
    } catch (IOException | SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private void getLet(String uniqueid, SolrInputDocument idoc) {
    try {
      SolrQuery query = new SolrQuery();
      //query.setRequestHandler(core);
      query.setQuery("ident_cely:\"" + uniqueid + "\"");
      query.setFilterQueries("doctype:\"let\"");
      SolrDocumentList docs = exportClient.query(query).getResults();
      if (docs.isEmpty()) {
        LOGGER.log(Level.FINE, "Doc {0} has no let", uniqueid);
      } else {
        for (SolrDocument doc : docs) {
          addFields(idoc, doc, "let");
        }
      }
    } catch (IOException | SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private void getJednotkaDokument(String uniqueid, SolrInputDocument idoc) {
    try {
      SolrQuery query = new SolrQuery();
      //query.setRequestHandler(core);
      query.setQuery("dokument:\"" + uniqueid + "\"");
      query.setFilterQueries("doctype:\"jednotka_dokumentu\"");
      SolrDocumentList docs = exportClient.query(query).getResults();
      if (docs.isEmpty()) {
        LOGGER.log(Level.FINE, "Doc {0} has no jednotka_dokumentu", uniqueid);
      } else {
        for (SolrDocument doc : docs) {
          //getNeidentAkce(idoc, doc.getFirstValue("ident_cely").toString());
          if (doc.getFirstValue("vazba") != null) {
            String childid = doc.getFirstValue("vazba").toString();
            getChilds(idoc, childid);
          }
          getKomponentaDokumentu(idoc, doc.getFirstValue("ident_cely").toString());
        }
      }
    } catch (IOException | SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private void getExterniOdkaz(SolrInputDocument idoc, String vazba) {
    try {
      SolrQuery query = new SolrQuery();
      query.setQuery("vazba:\"" + vazba + "\"");
      query.setFilterQueries("doctype:\"externi_odkaz\"");
      SolrDocumentList docs = relationsClient.query(query).getResults();
      if (docs.isEmpty()) {
        LOGGER.log(Level.FINE, "Doc {0} has no externi_odkaz", vazba);
      } else {
        for (SolrDocument doc : docs) {
          addFields(idoc, doc, "externi_odkaz");
          getExterniZdroj(idoc, doc.getFirstValue("externi_zdroj").toString());
        }
      }
    } catch (IOException | SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private void getExterniZdroj(SolrInputDocument idoc, String uniqueid) {
    try {
      SolrQuery query = new SolrQuery();
      query.setQuery("ident_cely:\"" + uniqueid + "\"");
      query.setFilterQueries("doctype:\"externi_zdroj\"");
      SolrDocumentList docs = exportClient.query(query).getResults();
      if (docs.isEmpty()) {
        LOGGER.log(Level.FINE, "Doc {0} has no externi_zdroj", uniqueid);
      } else {
        for (SolrDocument doc : docs) {
          addFields(idoc, doc, "externi_zdroj");
        }
      }
    } catch (IOException | SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private void getDokJednotka(SolrInputDocument idoc, String uniqueid, String doctype, String pristupnost) {
    try {
      SolrQuery query = new SolrQuery();
      //query.setRequestHandler(core);
      query.setQuery("parent:\"" + uniqueid + "\"");
      query.setFilterQueries("doctype:\"dokumentacni_jednotka\"");
      SolrDocumentList docs = exportClient.query(query).getResults();
      if (docs.isEmpty()) {
        LOGGER.log(Level.FINE, "Doc {0} has no dokumentacni_jednotka", uniqueid);
      } else {
        for (SolrDocument doc : docs) {
          if (doc.containsKey("pian")) {
            getPian(idoc, doc.getFirstValue("pian").toString(), doctype, uniqueid, pristupnost);
          }

          if (!csvExcludeRelations.contains(idoc.getField("typ_dokumentu").getFirstValue().toString())) {
            getKomponenta(idoc, doc.getFirstValue("ident_cely").toString());
          }

        }
      }
    } catch (IOException | SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private void getKomponenta(SolrInputDocument idoc, String dokumentacni_jednotka) {
    try {
      SolrQuery query = new SolrQuery();
      query.setQuery("parent:\"" + dokumentacni_jednotka + "\"");
      query.setFilterQueries("doctype:\"komponenta\"");
      SolrDocumentList docs = exportClient.query(query).getResults();
      if (docs.isEmpty()) {
        LOGGER.log(Level.FINE, "Doc {0} has no komponenta", dokumentacni_jednotka);
      } else {
        for (SolrDocument doc : docs) {
          addFields(idoc, doc, "komponenta");
          getNalez(idoc, doc.getFirstValue("ident_cely").toString());
        }
      }
    } catch (IOException | SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private void getNalez(SolrInputDocument idoc, String komponenta) {
    try {
      SolrQuery query = new SolrQuery();
      //query.setRequestHandler(core);
      query.setQuery("komponenta:\"" + komponenta + "\"");
      query.setFilterQueries("doctype:\"nalez\"");
      query.setRows(100);

      SolrDocumentList docs = relationsClient.query(query).getResults();
      if (docs.isEmpty()) {
        LOGGER.log(Level.FINE, "Doc {0} has no nalez", komponenta);
      } else {
        for (SolrDocument doc : docs) {
          addFields(idoc, doc, "nalez");
        }
      }

    } catch (IOException | SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private void getNeidentAkce(SolrInputDocument idoc, String jednotka) {
    try {
      SolrQuery query = new SolrQuery();
      //query.setRequestHandler(core);
      query.setQuery("ident_cely:\"" + jednotka + "\"");
      query.setFilterQueries("doctype:\"neident_akce\"");
      query.setRows(100);

      SolrDocumentList docs = exportClient.query(query).getResults();
      if (docs.isEmpty()) {
        LOGGER.log(Level.FINE, "Doc {0} has no neident_akce", jednotka);
      } else {
        for (SolrDocument doc : docs) {
          addFields(idoc, doc, "neident_akce");
        }
      }

    } catch (IOException | SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private void getKomponentaDokumentu(SolrInputDocument idoc, String jednotka) {
    try {
      SolrQuery query = new SolrQuery();
      //query.setRequestHandler(core);
      query.setQuery("parent:\"" + jednotka + "\"");
      query.setFilterQueries("doctype:\"komponenta_dokumentu\"");
      query.setRows(100);

      SolrDocumentList docs = exportClient.query(query).getResults();
      if (docs.isEmpty()) {
        LOGGER.log(Level.FINE, "Doc {0} has no komponenta_dokumentu", jednotka);
      } else {
        for (SolrDocument doc : docs) {
          addFields(idoc, doc, "komponenta_dokumentu");
          getNalezDokumentu(idoc, doc.getFirstValue("ident_cely").toString());
        }
      }

    } catch (IOException | SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private void getNalezDokumentu(SolrInputDocument idoc, String komponenta_dokumentu) {
    try {
      SolrQuery query = new SolrQuery();
      //query.setRequestHandler(core);
      query.setQuery("komponenta_dokument:\"" + komponenta_dokumentu + "\"");
      query.setFilterQueries("doctype:\"nalez_dokumentu\"");
      query.setRows(100);

      SolrDocumentList docs = relationsClient.query(query).getResults();
      if (docs.isEmpty()) {
        LOGGER.log(Level.FINE, "Doc {0} has no nalez_dokumentu", komponenta_dokumentu);
      } else {
        for (SolrDocument doc : docs) {
          addFields(idoc, doc, "nalez_dokumentu");
        }
      }

    } catch (IOException | SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  /**
   * Hleda pian pro akce a lokality
   *
   * @param idoc
   * @param uniqueid obsah pole vazba u jednotky dokumentu
   */
  private void getPian(SolrInputDocument idoc, String uniqueid, String pdoctype, String parent, String pristupnost) {
    try {
      SolrQuery query = new SolrQuery();
      query.setQuery("ident_cely:\"" + uniqueid + "\"");
      query.setFields("ident_cely", "presnost", "typ", "centroid_e", "centroid_n");

      JSONObject resp = SolrIndex.json(query, "export/");
      JSONArray docs = resp.getJSONObject("response").getJSONArray("docs");
      for (int i = 0; i < docs.length(); i++) {
        JSONObject doc = docs.getJSONObject(i);

        idoc.addField("pian_centroid_e", doc.getJSONArray("centroid_e").get(0));
        idoc.addField("pian_centroid_n", doc.getJSONArray("centroid_n").get(0));
        idoc.addField("pian_ident_cely", doc.getJSONArray("ident_cely").get(0));
        doc.put("parent", parent);
        doc.put("parent_doctype", pdoctype);
        doc.put("parent_pristupnost", pristupnost);
      }

      if (docs.length() > 0) {
        idoc.addField("pian", docs.toString());
      }

    } catch (IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  /**
   * Hleda akce a lokality u jednotky dokumentu
   *
   * @param idoc
   * @param uniqueid obsah pole vazba u jednotky dokumentu
   */
  private void getChilds(SolrInputDocument idoc, String uniqueid) {
    try {
      SolrQuery query = new SolrQuery();
      query.setQuery("ident_cely:\"" + uniqueid + "\"");

      SolrDocumentList docs = exportClient.query(query).getResults();
      if (docs.isEmpty()) {
        LOGGER.log(Level.FINE, "Doc {0} has no child", uniqueid);
      } else {
        for (SolrDocument doc : docs) {
          String doctype = doc.getFieldValue("doctype").toString();
          // filtrujeme jen povolene akce a lokality
          if ("akce".equals(doc.getFieldValue("doctype").toString())
                  && !csvStavyAkce.contains(doc.getFirstValue("stav").toString())) {
            LOGGER.log(Level.FINE, "skipping akce {0} ve stavu {1}",
                    new String[]{doc.getFirstValue("ident_cely").toString(),
                      doc.getFirstValue("stav").toString()
                    });
            continue;
          }
          if ("lokalita".equals(doc.getFieldValue("doctype").toString())
                  && !csvStavyLokalita.contains(doc.getFirstValue("stav").toString())) {
            LOGGER.log(Level.FINE, "skipping lokalita {0} ve stavu {1}",
                    new String[]{doc.getFirstValue("ident_cely").toString(),
                      doc.getFirstValue("stav").toString()
                    });
            continue;
          }
          addFields(idoc, doc, doctype);
          getDokJednotka(idoc, doc.getFirstValue("ident_cely").toString(), doctype, (String) doc.getFirstValue("pristupnost"));
          getExterniOdkaz(idoc, doc.getFirstValue("ident_cely").toString());
        }
      }
    } catch (IOException | SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private void getSoubory(String uniqueid, SolrInputDocument idoc) {
    try {
      SolrQuery query = new SolrQuery();
      //query.setRequestHandler(core);
      query.setQuery("dokument:\"" + uniqueid + "\"");
      query.setFilterQueries("doctype:\"soubor\"");
      query.setRows(100);
      JSONObject resp = SolrIndex.json(query, "relations/");
      //LOGGER.log(Level.INFO, resp.getJSONObject("response").getJSONArray("docs").toString());
      if (resp.getJSONObject("response").getJSONArray("docs").length() > 0) {
        idoc.addField("soubor", resp.getJSONObject("response").getJSONArray("docs").toString());

        String filepath = resp.getJSONObject("response").getJSONArray("docs").getJSONObject(0).optJSONArray("filepath").getString(0);
        String dest = ImageSupport.getDestDir(filepath);
        String fname = dest + filepath + "_thumb.jpg";

        //LOGGER.log(Level.INFO, fname);
        idoc.addField("hasThumb", new File(fname).exists() || new File(dest + "_thumb.jpg").exists());

      } else {
        idoc.addField("hasThumb", true);
      }
    } catch (IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private void addFieldsAsJson(SolrInputDocument idoc, SolrDocument rdoc, String doctype, String fieldname) {
    Iterator<String> names = rdoc.getFieldNames().iterator();
    Map<String, Collection<Object>> values = rdoc.getFieldValuesMap();
    JSONArray jafields = opts.getJSONObject("indexFieldsByType").optJSONArray(doctype);
    ArrayList<String> fields = new ArrayList<>();
    if (jafields != null) {
      for (int i = 0; i < jafields.length(); i++) {
        fields.add(jafields.getString(i));
      }
    }
    while (names.hasNext()) {
      String name = names.next();

      if (jafields == null || fields.contains(name)) {
        if (!"uniqueid".equals(name) && !"_version_".equals(name) && !"doctype".equals(name)) {
          Collection<Object> vals = values.get(name);
          Iterator<Object> valsIter = vals.iterator();
          while (valsIter.hasNext()) {
            Object obj = valsIter.next();
          }
        }
      }
    }
    idoc.addField(fieldname, rdoc.toString());
  }

  private void addFields(SolrInputDocument idoc, SolrDocument rdoc, String doctype) {
    addFields(idoc, rdoc, doctype, false);
  }

  private void addFields(SolrInputDocument idoc, SolrDocument rdoc, String doctype, boolean plain) {
    Iterator<String> names = rdoc.getFieldNames().iterator();
    Map<String, Collection<Object>> values = rdoc.getFieldValuesMap();
    JSONArray jafields = opts.getJSONObject("indexFieldsByType").optJSONArray(doctype);
    ArrayList<String> fields = new ArrayList<>();
    if (jafields != null) {
      for (int i = 0; i < jafields.length(); i++) {
        fields.add(jafields.getString(i));
      }
    }
    while (names.hasNext()) {
      String name = names.next();
      if (jafields == null || fields.contains(name)) {
        if (!"uniqueid".equals(name) && !"_version_".equals(name) && !"doctype".equals(name)) {
          Collection<Object> vals = values.get(name);
          Iterator<Object> valsIter = vals.iterator();
          while (valsIter.hasNext()) {
            Object obj = valsIter.next();
            if (plain) {
              idoc.addField(name, obj);
            } else {
              idoc.addField(doctype + "_" + name, obj);
            }
          }
        }
      }
      if (name.contains("aktivita") && rdoc.getFirstValue(name).equals("1")) {
        idoc.addField("f_aktivita", name.substring("aktivita_".length()));
      }
    }
  }

  private void readOne(Reader in, String uniqueField, String doctype, SolrClient sclient, JSONObject ret, boolean hasRelations) throws MalformedURLException, IOException, SolrServerException {

    Date tstart = new Date();
    int tsuccess = 0;
    int terrors = 0;
    JSONObject typeJson = new JSONObject();
//    CSVReader reader = new CSVReader(new InputStreamReader(new URL(url).openStream(), "UTF-8"), '#', '\"', false);

    CSVFormat f = CSVFormat.newFormat('#').withEscape('\\').withQuote(null).withFirstRecordAsHeader();
    CSVParser parser = new CSVParser(in, f);
    Map<String, Integer> header = parser.getHeaderMap();
    ArrayList<SolrInputDocument> docs = new ArrayList<>();
    try {

      for (final CSVRecord record : parser) {
        try {
          SolrInputDocument doc = parseCsvLine(header, record, uniqueField, doctype, hasRelations);
          if (doc != null) {
            docs.add(doc);
            tsuccess++;
            success++;
            if (success % 500 == 0) {
              sclient.add(docs);
              sclient.commit();
              docs.clear();
              LOGGER.log(Level.INFO, "Indexed {0} docs", success);
            }
          }
        } catch (Exception ex) {
          terrors++;
          errors++;
          ret.getJSONArray("errors msgs").put(record);
          LOGGER.log(Level.SEVERE, "Error indexing doc in line {0}", parser.getCurrentLineNumber());
          LOGGER.log(Level.SEVERE, "Error indexing doc {0} in line {1}", record);
          LOGGER.log(Level.SEVERE, null, ex);
        }
      }
      if (!docs.isEmpty()) {
        sclient.add(docs);
        sclient.commit();
        docs.clear();
      }

      typeJson.put("docs indexed", tsuccess).put("errors", terrors);
      Date tend = new Date();

      typeJson.put("ellapsed time", FormatUtils.formatInterval(tend.getTime() - tstart.getTime()));
      ret.put(doctype, typeJson).put("docs indexed", success);
    } catch (Exception ex) {
      LOGGER.log(Level.WARNING, "Parser failed on line " + Long.toString(parser.getCurrentLineNumber()), ex);
      throw ex;
    } finally {
      parser.close();
    }

  }

  private SolrInputDocument parseCsvLine(Map<String, Integer> header, CSVRecord record, String uniqueField, String doctype, boolean hasRelations) {

    SolrInputDocument doc = new SolrInputDocument();
    for (Map.Entry<String, Integer> entry : header.entrySet()) {
      String field = entry.getKey();
      //Vyjimka pro pian, nechceme geom_gml
      if (field.equals("geom_gml")) {
        continue;
      }
      //Vyjimka pro autoru. Muze mit oddelovac ;
      switch (field) {
        case "autor":
          {
            ArrayList<String> values = new ArrayList<>(Arrays.asList(record.get(field).split(";")));
            doc.addField("autor", values);
            doc.addField("autor_sort", values.get(0));
            break;
          }
        case "vedouci_akce":
        case "vedouci_akce_ostatni":
          {
            ArrayList<String> values = new ArrayList<>(Arrays.asList(record.get(field).split(";")));
            doc.addField(field, values);
            break;
          }
        default:
          doc.addField(field, record.get(entry.getKey()));
          break;
      }
    }
    
    if ("1".equals(doc.getFieldValue("stav")) && "dokument".equals(doctype)) {
      // Přestat indexovat dokumenty ve stavu 1 (nerevidováno) #160
      return null;
    }

    String uniqueid;
    if (uniqueField != null) {
      uniqueid = record.get(uniqueField);
    } else {
      uniqueid = "" + record.getRecordNumber();
    }
    doc.addField("uniqueid", doctype + "_" + uniqueid);
    doc.addField("doctype", doctype);
    if (hasRelations) {
      addRelations(uniqueid, doctype, doc);
    }
    if (doc.containsKey("pian_centroid_e")) {

      String loc = doc.getFieldValue("pian_centroid_n") + "," + doc.getFieldValue("pian_centroid_e");
      doc.addField("loc", loc);
      doc.addField("loc_rpt", loc);
    }
    addSearchFields(doc, doctype);
    if (doctype.equals("pas")) {
//      if (!csvStavyPas.contains(doc.getFieldValue("stav").toString())) {
//        LOGGER.log(Level.FINE, "Skip doc as stav is {0}", doc.getFieldValue("stav"));
//        return null;
//      }
      
      doc.addField("katastr_pas", doc.getFieldValues("katastr"));
      doc.removeField("katastr");
      doc.addField("f_typ_dokumentu", "Samostatné nálezy");
      doc.addField("komponenta_dokumentu_obdobi", doc.getFieldValue("obdobi"));
      doc.addField("kategorie", "pas");
      doc.addField("dokument_popis", doc.getFieldValue("lokalizace") + " " + doc.getFieldValue("poznamka"));
      if (doc.getFieldValue("centroid_n") != null && !doc.getFieldValue("centroid_n").equals("")) {
        String loc = doc.getFieldValue("centroid_n") + "," + doc.getFieldValue("centroid_e");
        doc.addField("pian_centroid_n", doc.getFieldValue("centroid_n"));
        doc.addField("pian_centroid_e", doc.getFieldValue("centroid_e"));
        doc.addField("pian_ident_cely", uniqueid);
        JSONObject pian =  new JSONObject()
                .put("ident_cely", (new JSONArray()).put(uniqueid))
                .put("parent_pristupnost", doc.getFieldValue("pristupnost"))
                .put("centroid_n", (new JSONArray()).put(doc.getFieldValue("centroid_n")))
                .put("centroid_e", (new JSONArray()).put(doc.getFieldValue("centroid_e")));
        
        doc.addField("pian", "["+pian.toString()+"]");
        doc.addField("loc", loc);
        doc.addField("loc_rpt", loc);
        
      }
    }

    // Pridame pole pro adv search
    if (doc.getFieldValue("obdobi") != null && !doc.getFieldValue("obdobi").equals("")) {
      doc.addField("obdobi_poradi", getObdobiPoradi((String) doc.getFieldValue("obdobi")));
    }
    if (doc.getFieldValue("specifikace") != null && !doc.getFieldValue("specifikace").equals("")) {
      doc.addField("nalez_specifikace", doc.getFieldValue("specifikace"));
    }
    if (doc.getFieldValue("druh") != null && !doc.getFieldValue("druh").equals("")) {
      doc.addField("druh_nalezu", doc.getFieldValue("druh"));
    }
    if (doc.getFieldValue("predano_organizace") != null && !doc.getFieldValue("predano_organizace").equals("")) {
      doc.addField("organizace", doc.getFieldValue("predano_organizace"));
    }
    if (doc.getFieldValue("nalezce") != null && !doc.getFieldValue("nalezce").equals("")) {
      doc.addField("adv_jmeno", doc.getFieldValue("nalezce"));
    }

    if (doc.getFieldValue("datum_nalezu") != null && !doc.getFieldValue("datum_nalezu").equals("")) {
      doc.addField("datum", doc.getFieldValue("datum_nalezu"));
    }

    return doc;
  }

  private String getObdobiPoradi(String obdobi) {
    if (obdobi_poradi == null) {
      initObdobiPoradi();
    }
    return obdobi_poradi.get(obdobi.toLowerCase());
  }

  private void initObdobiPoradi() {
    try (SolrClient solr = new HttpSolrClient.Builder(String.format("%s%s",
            SolrIndex.host(),
            "heslar/")).build()) {
      obdobi_poradi = new HashMap<>();

      SolrQuery query = new SolrQuery()
              .setQuery("heslar_name:obdobi_druha")
              .setRows(1000)
              .setFields("poradi,nazev");
      QueryResponse resp = solr.query(query);
      for (SolrDocument doc : resp.getResults()) {
        obdobi_poradi.put(((String) doc.getFieldValue("nazev")).toLowerCase(), "" + doc.getFieldValue("poradi"));
      }
    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  public JSONObject indexTable(String doctype) {
    JSONObject ret = new JSONObject();
    try {
      getClients();
      success = 0;
      errors = 0;
      JSONArray sources = new JSONArray().put(doctype);
      ret.put("tables", indexSource(exportClient, sources, "ident_cely", false));
      closeClients();
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  public JSONObject indexRelation(String doctype) {
    JSONObject ret = new JSONObject();
    try {
      getClients();

      success = 0;
      errors = 0;
      JSONArray sources = new JSONArray().put(doctype);
      ret.put("relations", indexSource(relationsClient, sources, null, false));

      closeClients();

    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  public JSONObject indexDokuments() {
    JSONObject ret = new JSONObject();
    try {

      getClients();

      success = 0;
      errors = 0;
      ret.put("dokuments", indexSource(dokumentClient, new JSONArray().put("dokument"), "ident_cely", true));

      closeClients();

    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  public JSONObject cleanPas() {
    try {
      dokumentClient = SolrIndex.getClient(opts.getString("dokumentsCore", "dokument/"));
      dokumentClient.deleteByQuery("doctype:pas", 1);
      dokumentClient.close();
      return new JSONObject().put("message", "Pas cleaned");
    } catch (IOException | JSONException | SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      return new JSONObject().put("error", ex);
    }
  }

  public JSONObject indexPas() {
    JSONObject ret = new JSONObject();
    try {

      getClients();

      success = 0;
      errors = 0;
      ret.put("pas", indexSource(dokumentClient, new JSONArray().put("pas"), "ident_cely", true));

      closeClients();

    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  public JSONObject indexDokument(String id) {
    getClients();
    JSONObject ret = new JSONObject();
    try {
      SolrQuery query = new SolrQuery();
      query.setQuery("ident_cely:\"" + id + "\"");
      SolrDocumentList docs = exportClient.query(query).getResults();
      if (docs.isEmpty()) {
        LOGGER.log(Level.INFO, "Doc {0} not found", id);
        ret.put(id, "not exists");
      } else {
        SolrInputDocument idoc = new SolrInputDocument();
        addFields(idoc, docs.get(0), "dokument", true);
        idoc.removeField("indextime");
        idoc.removeField("_version_");
        idoc.removeField("_root_");
        idoc.setField("uniqueid", "dokument_" + id);
        idoc.setField("doctype", "dokument");
        addRelations(id, "dokument", idoc);
        if (idoc.containsKey("pian_centroid_e")) {
          String loc = idoc.getFieldValue("pian_centroid_n") + "," + idoc.getFieldValue("pian_centroid_e");
          idoc.addField("loc", loc);
          idoc.addField("loc_rpt", loc);
        }
        dokumentClient.add(idoc);
        dokumentClient.commit();
        LOGGER.log(Level.INFO, "Doc {0} indexed", id);
        ret.put(id, "success");
      }
      closeClients();
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  public JSONObject indexPasDokument(String id) {
    getClients();
    JSONObject ret = new JSONObject();
    try {
      SolrQuery query = new SolrQuery();
      query.setQuery("ident_cely:\"" + id + "\"");
      SolrDocumentList docs = exportClient.query(query).getResults();
      if (docs.isEmpty()) {
        LOGGER.log(Level.INFO, "Doc {0} not found", id);
        ret.put(id, "not exists");
      } else {
        SolrInputDocument idoc = new SolrInputDocument();
        addFields(idoc, docs.get(0), "pas", true);
        idoc.removeField("indextime");
        idoc.removeField("_version_");
        idoc.removeField("_root_");
        String uniqueid = "pas_" + id;
        idoc.setField("uniqueid", uniqueid);
        idoc.setField("doctype", "pas");

        //idoc.addField("f_typ_dokumentu", "Samostatné nálezy");
//        idoc.addField("kategorie", "pas");
//        if (idoc.getFieldValue("geom_x") != null && !idoc.getFieldValue("geom_x").equals("")) {
//          String loc = idoc.getFieldValue("geom_x") + "," + idoc.getFieldValue("geom_y");
//          idoc.addField("pian_centroid_n", idoc.getFieldValue("geom_x"));
//          idoc.addField("pian_centroid_e", idoc.getFieldValue("geom_y"));
//          idoc.addField("pian_ident_cely", uniqueid);
//          idoc.addField("pian", uniqueid);
//          idoc.addField("loc", loc);
//          idoc.addField("loc_rpt", loc);
//        }
        dokumentClient.add(idoc);
        dokumentClient.commit();
        LOGGER.log(Level.INFO, "Doc {0} indexed", id);
        ret.put(id, "success");
      }
      closeClients();
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  public JSONObject indexTables() {
    JSONObject ret = new JSONObject();
    try {
      getClients();
      success = 0;
      errors = 0;
      ret.put("tables", indexSource(exportClient, opts.getJSONArray("csvTables"), "ident_cely", false));
      closeClients();
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  public JSONObject indexRelations() {
    JSONObject ret = new JSONObject();
    try {
      getClients();
      success = 0;
      errors = 0;
      ret.put("relations", indexSource(relationsClient, opts.getJSONArray("csvRelationTables"), null, false));
      closeClients();
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  private JSONObject indexSource(SolrClient sclient, JSONArray sources, String uniqueid, boolean hasRelations) {

    JSONObject ret = new JSONObject();
    try {
      Date start = new Date();
      JSONArray ja = new JSONArray();
      ret.put("errors msgs", ja);

      for (int i = 0; i < sources.length(); i++) {

        String doctype = sources.getString(i);

        String url = String.format(apiPoint, doctype);
        LOGGER.log(Level.INFO, "downloading from {0}", url);
        File localSource = File.createTempFile(doctype + "-", ".csv");
        FileUtils.copyURLToFile(new URL(url), localSource);
        LOGGER.log(Level.INFO, "sourcing from {0}", localSource.getPath());
        FileInputStream fis = new FileInputStream(localSource);
        readOne(new InputStreamReader(fis, "UTF-8"), uniqueid, doctype, sclient, ret, hasRelations);
        if (!localSource.delete()) {
          LOGGER.log(Level.WARNING, "Cannot delete {0}", localSource.getPath());
        }
      }
      LOGGER.log(Level.INFO, "Indexed Finished. {0} success, {1} errors", new Object[]{success, errors});

      ret.put("errors", errors);

      Date end = new Date();
      ret.put("ellapsed time", FormatUtils.formatInterval(end.getTime() - start.getTime()));
    } catch (IOException | SolrServerException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
    return ret;
  }

}
