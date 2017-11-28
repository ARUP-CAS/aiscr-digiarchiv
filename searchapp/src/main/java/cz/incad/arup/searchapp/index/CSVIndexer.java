package cz.incad.arup.searchapp.index;

//import au.com.bytecode.opencsv.CSVReader;
import cz.incad.FormatUtils;
import cz.incad.arup.searchapp.Options;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.json.JSONArray;
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

  public CSVIndexer() throws IOException {

    opts = Options.getInstance();
    relationsClient = SolrIndex.getClient(opts.getString("csvRelationsCore", "relations/"));
    exportClient = SolrIndex.getClient(opts.getString("exportCore", "export/"));
    dokumentClient = SolrIndex.getClient(opts.getString("dokumentsCore", "dokument/"));
    translationsClient = SolrIndex.getClient("translations/");

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
  }

  public JSONObject run() {

    JSONObject ret = new JSONObject();
    try {

      success = 0;
      errors = 0;

      ret.put("translations", indexTranslations());
      ret.put("relations", indexSource(relationsClient, opts.getJSONArray("csvRelationTables"), null, false));
      ret.put("tables", indexSource(exportClient, opts.getJSONArray("csvTables"), "ident_cely", false));
      ret.put("dokuments", indexSource(dokumentClient, new JSONArray().put("dokument"), "ident_cely", true));
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  public JSONObject indexTranslations() {

    JSONObject ret = new JSONObject();
    try {
      translationsClient.deleteByQuery("*:*", 1);
                translationsClient.commit();
      Date start = new Date();
      JSONArray ja = new JSONArray();
      ret.put("errors msgs", ja);
      String thesauriDir = opts.getString("thesauriDir");
      File dir = new File(thesauriDir);
      for (File file : dir.listFiles()) {
        LOGGER.log(Level.INFO, "indexing from {0}", file.getName());
        Reader in = new FileReader(file);
        //readOne( , uniqueid, "", translationsClient, ret, hasRelations);

        Date tstart = new Date();
        int tsuccess = 0;
        int terrors = 0;
        JSONObject typeJson = new JSONObject();

        CSVFormat f = CSVFormat.newFormat('#').withEscape('\\').withQuote('\"').withFirstRecordAsHeader();
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

        getSoubory(uniqueid, doc);
        getOdkaz(uniqueid, doc);
        getJednotkaDokument(uniqueid, doc);
      } catch (SolrServerException ex) {
        LOGGER.log(Level.SEVERE,
                "Error adding relations,\n uniqueid: {0} \n doctype: {1} \n doc {2}",
                new Object[]{uniqueid, doctype, doc});
        LOGGER.log(Level.SEVERE, null, ex);
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
    } catch (SolrServerException ex) {
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
          String childid = doc.getFirstValue("vazba").toString();
          getChilds(idoc, childid);
          getKomponentaDokumentu(idoc, doc.getFirstValue("ident_cely").toString());
        }
      }
    } catch (SolrServerException ex) {
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
    } catch (SolrServerException ex) {
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
    } catch (SolrServerException ex) {
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
    } catch (SolrServerException ex) {
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
        LOGGER.log(Level.FINE, "Doc {0} has no dokumentacni_jednotka", dokumentacni_jednotka);
      } else {
        for (SolrDocument doc : docs) {
          addFields(idoc, doc, "komponenta");
          getNalez(idoc, doc.getFirstValue("ident_cely").toString());
        }
      }
    } catch (SolrServerException ex) {
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

    } catch (SolrServerException ex) {
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

    } catch (SolrServerException ex) {
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
          getNalezDokumentu(idoc, doc.getFirstValue("parent").toString());
        }
      }

    } catch (SolrServerException ex) {
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

    } catch (SolrServerException ex) {
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
        idoc.addField("pian", docs);
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
    } catch (SolrServerException ex) {
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
      if (resp.getJSONObject("response").getJSONArray("docs").length() > 0) {
        idoc.addField("soubor", resp.getJSONObject("response").getJSONArray("docs"));
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
            idoc.addField(doctype + "_" + name, obj);
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

    CSVFormat f = CSVFormat.newFormat('#').withEscape('\\').withQuote('\"').withFirstRecordAsHeader();
    CSVParser parser = new CSVParser(in, f);
    Map<String, Integer> header = parser.getHeaderMap();
    try {

      for (final CSVRecord record : parser) {
        try {
          SolrInputDocument doc = parseCsvLine(header, record, uniqueField, doctype, hasRelations);

          sclient.add(doc);
          tsuccess++;
          success++;
          if (success % 500 == 0) {
            sclient.commit();
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

      sclient.commit();

      typeJson.put("docs indexed", tsuccess).put("errors", terrors);
      Date tend = new Date();

      typeJson.put("ellapsed time", FormatUtils.formatInterval(tend.getTime() - tstart.getTime()));
      ret.put(doctype, typeJson).put("docs indexed", success);
    } finally {
      parser.close();
    }

  }

  private SolrInputDocument parseCsvLine(Map<String, Integer> header, CSVRecord record, String uniqueField, String doctype, boolean hasRelations) {

    SolrInputDocument doc = new SolrInputDocument();
//      JSONArray jafields = opts.getJSONObject("indexFieldsByType").optJSONArray(doctype);
//      ArrayList<String> fields;
//      if (jafields != null) {
//        fields = new ArrayList<String>();
//        for (int i = 0; i < jafields.length(); i++) {
//          if(header.containsKey(jafields.getString(i))){
//            fields.add(jafields.getString(i));
//          } else {
//            LOGGER.log(Level.WARNING, "{0} not contains field {1}. Ignoring", new String[]{doctype, jafields.getString(i)});
//          }
//        }
//      } else {
//        fields = new ArrayList<String>(Arrays.asList((String[]) header.keySet().toArray()));
//      }
//      
//      for (String field :fields){
//          doc.addField(field, record.get(field));
//      }

    for (Map.Entry<String, Integer> entry : header.entrySet()) {
      doc.addField(entry.getKey(), record.get(entry.getKey()));
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
    return doc;

  }

  public JSONObject indexTable(String doctype) {
    JSONObject ret = new JSONObject();
    try {

      success = 0;
      errors = 0;
      JSONArray sources = new JSONArray().put(doctype);
      ret.put("tables", indexSource(exportClient, sources, "ident_cely", false));
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  public JSONObject indexRelation(String doctype) {
    JSONObject ret = new JSONObject();
    try {

      success = 0;
      errors = 0;
      JSONArray sources = new JSONArray().put(doctype);
      ret.put("relations", indexSource(relationsClient, sources, null, false));
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  public JSONObject indexDokuments() {
    JSONObject ret = new JSONObject();
    try {

      success = 0;
      errors = 0;
      ret.put("dokuments", indexSource(dokumentClient, new JSONArray().put("dokument"), "ident_cely", true));
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }
//  
//  public JSONObject indexDokument(String id){
//    
//    JSONObject ret = new JSONObject();
//    try {
//
//          SolrInputDocument doc = new SolrInputDocument();
//          for (int j = 0; j < nextLine.length; j++) {
//            doc.addField(headerLine[j], nextLine[j]);
//          }
//
//          String uniqueid;
//          if (uniqueField != null) {
//            uniqueid = nextLine[fNames.get(uniqueField)];
//          } else {
//            uniqueid = ""+lineNumber++;
//          }
//          doc.addField("uniqueid", doctype + "_" + uniqueid);
//          doc.addField("doctype", doctype);
//          if (hasRelations) {
//            addRelations(uniqueid, doctype, doc);
//          }
//          if(doc.containsKey("pian_centroid_e")){
//            
//            String loc = doc.getFieldValue("pian_centroid_n") + "," + doc.getFieldValue("pian_centroid_e");
//            doc.addField("loc", loc);
//            doc.addField("loc_rpt", loc);
//          }
//          sclient.add(doc);
//          tsuccess++;
//          success++;
//          if (success % 500 == 0) {
//            sclient.commit();
//            LOGGER.log(Level.INFO, "Indexed {0} docs", success);
//          }
//        
//
//      
//        
//        
//    } catch (Exception ex) {
//      LOGGER.log(Level.SEVERE, null, ex);
//      ret.put("error", ex);
//    }
//    return ret;
//  }

  public JSONObject indexRelations() {
    JSONObject ret = new JSONObject();
    try {

      success = 0;
      errors = 0;
      ret.put("relations", indexSource(relationsClient, opts.getJSONArray("csvRelationTables"), null, false));
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
        LOGGER.log(Level.INFO, "indexing from {0}", url);
        readOne(new InputStreamReader(new URL(url).openStream(), "UTF-8"), uniqueid, doctype, sclient, ret, hasRelations);
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

  public JSONObject indexFiles(String[] filenames) throws Exception {

    Date start = new Date();
    String core = opts.getString("exportCore", "export/");
    SolrClient sclient = SolrIndex.getClient(core);
    JSONObject ret = new JSONObject();
    JSONArray ja = new JSONArray();
    ret.put("errors msgs", ja);
    JSONArray sources;
    if (filenames == null || filenames.length == 0) {
      sources = opts.getJSONArray("csvFiles");
    } else {
      sources = new JSONArray(filenames);
    }

    for (int i = 0; i < sources.length(); i++) {

      String filename = sources.getJSONObject(i).getString("file");
      String doctype = sources.getJSONObject(i).getString("doctype");

      JSONObject jmap;

      if (sources.getJSONObject(i).has("map")) {

        String maps = sources.getJSONObject(i).getString("map");

        jmap = new JSONObject(FileUtils.readFileToString(new File(maps), "UTF-8"));
        //Otocim
        Set keyset = jmap.keySet();
        Object[] keys = keyset.toArray();
        for (Object s : keys) {
          String key = (String) s;
          jmap.put(jmap.getString(key), key);
        }
      } else {
        jmap = new JSONObject();
      }
      String url = String.format(apiPoint, doctype);
      LOGGER.log(Level.INFO, "indexing from {0}", url);
      readOne(new InputStreamReader(new URL(url).openStream(), "UTF-8"), "ident_cely", doctype, sclient, ret, true);
    }
    LOGGER.log(Level.INFO, "Indexed Finished. {0} success, {1} errors", new Object[]{success, errors});

    ret.put("errors", errors);

    Date end = new Date();
    ret.put("ellapsed time", FormatUtils.formatInterval(end.getTime() - start.getTime()));
    return ret;

  }

  private JSONObject indexRelationsFromFiles() throws Exception {

    Date start = new Date();
    Options opts = Options.getInstance();
    String core = opts.getString("csvRelationsCore", "relations/");

    int success = 0;
    int errors = 0;
    SolrClient sclient = SolrIndex.getClient(core);
    JSONObject ret = new JSONObject();
    JSONArray ja = new JSONArray();
    JSONArray sources = opts.getJSONArray("csvRelations");

    for (int i = 0; i < sources.length(); i++) {

      Date tstart = new Date();
      int tsuccess = 0;
      int terrors = 0;
      JSONObject typeJson = new JSONObject();
      String filename = sources.getJSONObject(i).getString("file");
      String doctype = sources.getJSONObject(i).getString("doctype");

      JSONObject jmap;

      if (sources.getJSONObject(i).has("map")) {

        String maps = sources.getJSONObject(i).getString("map");

        jmap = new JSONObject(FileUtils.readFileToString(new File(maps), "UTF-8"));
        //Otocim
        Set keyset = jmap.keySet();
        Object[] keys = keyset.toArray();
        for (Object s : keys) {
          String key = (String) s;
          jmap.put(jmap.getString(key), key);
        }
      } else {
        jmap = new JSONObject();
      }
      readOne(new InputStreamReader(new FileInputStream(filename), "UTF-8"), "ident_cely", doctype, sclient, ret, true);

      typeJson.put("docs indexed", tsuccess);
      typeJson.put("errors", terrors);
      Date tend = new Date();
      typeJson.put("ellapsed time", FormatUtils.formatInterval(tend.getTime() - tstart.getTime()));
      ret.put(doctype, typeJson);

      ret.put("docs indexed", success);
    }
    LOGGER.log(Level.INFO, "Indexed Finished. {0} success, {1} errors", new Object[]{success, errors});

    ret.put("errors", errors);
    ret.put("errors msgs", ja);

    Date end = new Date();
    ret.put("ellapsed time", FormatUtils.formatInterval(end.getTime() - start.getTime()));
    return ret;

  }

}
