package cz.incad.arup.searchapp.index;

import cz.incad.arup.searchapp.Options;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author alberto
 */
public class SolrIndex {

  public static final Logger LOGGER = Logger.getLogger(SolrIndex.class.getName());
  private static final String DEFAULT_HOST = "http://localhost:8983/solr/";

  public static String host() {

    try {
      Options opts = Options.getInstance();
      return opts.getString("solrhost", DEFAULT_HOST);
    } catch (IOException | JSONException ex) {
      Logger.getLogger(SolrIndex.class.getName()).log(Level.SEVERE, null, ex);
    }
    return DEFAULT_HOST;
  }

  public static SolrClient getClient(String core) throws IOException {
    SolrClient server = new HttpSolrClient.Builder(String.format("%s%s",
            host(),
            core)).build();
    return server;
  }

  public static String getPristupnostBySoubor(String id, String field) {
    try {
      SolrClient relClient = getClient("relations");
      SolrQuery query = new SolrQuery("*").addFilterQuery("filepath:\"" + id + "\"").setRows(1).setFields("dokument", "samostatny_nalez");
      QueryResponse rsp = relClient.query(query);
      relClient.close();
      if (rsp.getResults().isEmpty()) {
        return null;
      } else {
        String dok = (String) rsp.getResults().get(0).getFirstValue("dokument");
        if (dok == null || "".equals(dok)) {
          // dok = (String) rsp.getResults().get(0).getFirstValue("samostatny_nalez");
          // Pro smostatne nalezy obrazky vzdy povolene #162
          return "A";
        }
        SolrClient dokClient = getClient("dokument");
        SolrQuery queryDok = new SolrQuery("*").addFilterQuery("ident_cely:\"" + dok + "\"").setRows(1).setFields("pristupnost");
        QueryResponse rsp2 = dokClient.query(queryDok);
        dokClient.close();
        if (rsp2.getResults().isEmpty()) {
          return null;
        } else {
          return (String) rsp2.getResults().get(0).getFirstValue("pristupnost");
        }
      }
    } catch (IOException | SolrServerException ex) {
      Logger.getLogger(SolrIndex.class.getName()).log(Level.SEVERE, null, ex);
      return null;
    }
  }

  public static SolrDocumentList query(SolrQuery query, String core) throws IOException, SolrServerException {
    SolrClient server = getClient(core);
    QueryResponse rsp = server.query(query);
    return rsp.getResults();
  }

  public static String xml(String q, String core) throws MalformedURLException, IOException {
    SolrQuery query = new SolrQuery(q);
    query.set("indent", true);

    return xml(query, core);
  }

  private static String doQuery(SolrQuery query, String core) throws MalformedURLException, IOException, ProtocolException {

    String urlQueryString = query.toQueryString();
    Options opts = Options.getInstance();
    String solrURL = String.format("%s/%s/select",
            host(),
            core);
    URL url = new URL(solrURL + urlQueryString);

    // use org.apache.commons.io.IOUtils to do the http handling for you
//            String xmlResponse = IOUtils.toString(url, "UTF-8");
//            return xmlResponse;
    HttpURLConnection urlc = null;
    String POST_ENCODING = "UTF-8";

    urlc = (HttpURLConnection) url.openConnection();
    urlc.setConnectTimeout(10000);

    urlc.setRequestMethod("GET");
    urlc.setDoOutput(false);
    urlc.setDoInput(true);

    String ret = null;
    String errorStream = "";
    InputStream in = null;
    try {
      in = urlc.getInputStream();
      int status = urlc.getResponseCode();
      if (status != HttpURLConnection.HTTP_OK) {
        LOGGER.log(Level.WARNING, " HTTP response code={0}", status);
      }
      ret = IOUtils.toString(in, "UTF-8");

    } catch (IOException e) {

      LOGGER.log(Level.WARNING, "IOException while reading response");
      LOGGER.log(Level.WARNING, null, e);
    } finally {
      IOUtils.closeQuietly(in);
    }

    InputStream es = urlc.getErrorStream();
    if (es != null) {
      try {
        errorStream = IOUtils.toString(es);
        LOGGER.log(Level.WARNING, "Mame ERROR {0}", errorStream);
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "IOException while reading response");
        throw new IOException(e);
      } finally {
        es.close();
      }
    }
    if (errorStream.length() > 0) {
      LOGGER.log(Level.FINE, "errorStream: {0}", errorStream);
      return errorStream;
    }

    return ret;

  }

  public static String csv(SolrQuery query, String core) throws MalformedURLException, IOException {

    query.set("wt", "csv");
    return doQuery(query, core);
  }

  public static String xml(SolrQuery query, String core) throws MalformedURLException, IOException {

    query.set("indent", true);
    query.set("wt", "xml");
    return doQuery(query, core);
  }

  public static JSONObject json(SolrQuery query, String core) throws MalformedURLException, IOException {
    query.set("wt", "json");
    String solrURL = String.format("%s%sselect",
            host(),
            core);
    URL url = new URL(solrURL + query.toQueryString());
    return new JSONObject(IOUtils.toString(url, "UTF-8"));

    //return doQuery(query, core);
  }

  public static String json(String urlQueryString, String core) throws MalformedURLException, IOException {

    String solrURL = String.format("%s/%s/select",
            host(),
            core);
    URL url = new URL(solrURL + "?" + urlQueryString);

    // use org.apache.commons.io.IOUtils to do the http handling for you
    String resp = IOUtils.toString(url, "UTF-8");

    return resp;
  }

  public static String postDataToCore(String dataStr, String core)
          throws Exception {
    return postData(String.format("%s%supdate",
            host(),
            core), dataStr);
  }

  public static String postCSV(String core, String body) {
    try {
      String url = host() + core + "update/csv?wt=json&commitWithin=10000";

      HttpClient httpClient = HttpClientBuilder.create().build();
      HttpPost post = new HttpPost(url);

      StringEntity entity = new StringEntity(body, "UTF-8");
      entity.setContentType("text/csv");
      post.setEntity(entity);
      HttpResponse response = httpClient.execute(post);
      HttpEntity httpEntity = response.getEntity();
      InputStream in = httpEntity.getContent();

      String encoding = httpEntity.getContentEncoding() == null ? "UTF-8" : httpEntity.getContentEncoding().getName();
      encoding = encoding == null ? "UTF-8" : encoding;
      return IOUtils.toString(in, encoding);
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, null, e);
      return "{\"msg\":\"error\"}";
    }
  }

  public static String postJSON(String core, String body) {
    try {
      String url = host() + core + "update/json?wt=json&commitWithin=10000";

      HttpClient httpClient = HttpClientBuilder.create().build();
      HttpPost post = new HttpPost(url);

      StringEntity entity = new StringEntity(body, "UTF-8");
      entity.setContentType("application/json");
      post.setEntity(entity);
      HttpResponse response = httpClient.execute(post);
      HttpEntity httpEntity = response.getEntity();
      InputStream in = httpEntity.getContent();

      String encoding = httpEntity.getContentEncoding() == null ? "UTF-8" : httpEntity.getContentEncoding().getName();
      encoding = encoding == null ? "UTF-8" : encoding;
      return IOUtils.toString(in, encoding);
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, null, e);
      return "{\"msg\":\"error\"}";
    }
  }

  public static void commit(String core) {
    try {
      String url = host() + core + "update?commit=true";

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

  public static String postData(String url, String dataStr)
          throws Exception {

    URL solrUrl = new URL(url);
    Reader data = new StringReader(dataStr);
    StringBuilder output = new StringBuilder();
    HttpURLConnection urlc = null;
    String POST_ENCODING = "UTF-8";

    urlc = (HttpURLConnection) solrUrl.openConnection();
    try {
      urlc.setRequestMethod("POST");
    } catch (ProtocolException e) {
      throw new Exception("Shouldn't happen: HttpURLConnection doesn't support POST??", e);
    }
    urlc.setDoOutput(true);
    urlc.setDoInput(true);
    urlc.setUseCaches(false);
    urlc.setAllowUserInteraction(false);
    urlc.setRequestProperty("Content-type", "text/xml; charset=" + POST_ENCODING);

    OutputStream out = urlc.getOutputStream();

    try {
      Writer writer = new OutputStreamWriter(out, POST_ENCODING);
      pipe(data, writer);
      writer.close();
    } catch (IOException e) {
      throw new Exception("IOException while posting data", e);
    } finally {
      if (out != null) {
        out.close();
      }
    }

    InputStream in = urlc.getInputStream();
    int status = urlc.getResponseCode();
    StringBuilder errorStream = new StringBuilder();
    try {
      if (status != HttpURLConnection.HTTP_OK) {
        errorStream.append("postData URL=").append(solrUrl.toString()).append(" HTTP response code=").append(status).append(" ");
        throw new Exception("URL=" + solrUrl.toString() + " HTTP response code=" + status);
      }
      Reader reader = new InputStreamReader(in);
      pipeString(reader, output);
      reader.close();
    } catch (IOException e) {
      throw new Exception("IOException while reading response", e);
    } finally {
      if (in != null) {
        in.close();
      }
    }

    InputStream es = urlc.getErrorStream();
    if (es != null) {
      try {
        Reader reader = new InputStreamReader(es);
        pipeString(reader, errorStream);
        reader.close();
      } catch (IOException e) {
        throw new Exception("IOException while reading response", e);
      } finally {
        if (es != null) {
          es.close();
        }
      }
    }
    if (errorStream.length() > 0) {
      throw new Exception("postData error: " + errorStream.toString());
    }

    return output.toString();

  }

  /**
   * Pipes everything from the reader to the writer via a buffer
   */
  private static void pipe(Reader reader, Writer writer) throws IOException {
    char[] buf = new char[1024];
    int read = 0;
    while ((read = reader.read(buf)) >= 0) {
      writer.write(buf, 0, read);
    }
    writer.flush();
  }

  /**
   * Pipes everything from the reader to the writer via a buffer except lines
   * starting with '<?'
   */
  private static void pipeString(Reader reader, StringBuilder writer) throws IOException {
    char[] buf = new char[1024];
    int read = 0;
    while ((read = reader.read(buf)) >= 0) {
      if (!(buf[0] == '<' && buf[1] == '?')) {
        writer.append(buf, 0, read);
      }
    }
  }

}
