/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.incad.arup.searchapp.index;

import cz.incad.arup.searchapp.Options;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.xmlrpc.XmlRpcException;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.client.XmlRpcTransport;
import org.apache.xmlrpc.client.XmlRpcTransportFactory;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author alberto
 */
public class AmcrAPI {

  public static final Logger LOGGER = Logger.getLogger(AmcrAPI.class.getName());

  JSONObject conf;

  private String URL = "http://kryton.smartgis.cz/~smetak/isamcr-p-5-8-21/xmlrpc/0/?t=600";
  private String USER = "incad@incad.cz";
  private String PWD = "2d2dbafe59a025b8def42614f824a42db24e8633";
  //private String PWD = "test";

  private String sid = null;
  private boolean logged;

  public boolean connect() throws Exception {
    if (sid == null) {
      Options opts = Options.getInstance();
      conf = opts.getJSONObject("amcrapi");
      URL = conf.getString("url");
      USER = conf.getString("user");
      //PWD = sha1(conf.getString("pwd"));
      PWD = conf.getString("pwd");

      sid = getSid();
      logged = login() == 1;
    }
    return logged;

  }

  private String sha1(String s) {
    try {
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
      byte[] array = md.digest(s.getBytes());
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < array.length; ++i) {
        sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1, 3));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException ex) {
      Logger.getLogger(AmcrAPI.class.getName()).log(Level.SEVERE, null, ex);
      return s;
    }
  }

  public void raw() throws MalformedURLException {
    final XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
    config.setServerURL(new URL(URL));

    final XmlRpcClient client = new XmlRpcClient();

    final XmlRpcTransportFactory transportFactory = new XmlRpcTransportFactory() {
      public XmlRpcTransport getTransport() {
        return new MessageLoggingTransport(client);
      }
    };
    client.setTransportFactory(transportFactory);
    client.setConfig(config);
  }

  private Object sendRequest(String method, Object[] params) throws Exception {
    try {

      XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
      config.setServerURL(new URL(URL));
      final XmlRpcClient client = new XmlRpcClient();

//      final XmlRpcTransportFactory transportFactory = new XmlRpcTransportFactory() {
//        public XmlRpcTransport getTransport() {
//          return new MessageLoggingTransport(client);
//        }
//      };
      //client.setTransportFactory(transportFactory);

      client.setConfig(config);

      return client.execute(method, params);

    } catch (MalformedURLException | XmlRpcException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      throw new Exception(ex);
    }
  }

  private String getSid() throws Exception {
    return (String) sendRequest("get_sid", new Object[]{});
  }

  private Integer login() throws Exception {
    int resp = (Integer) sendRequest("login", new Object[]{sid, USER, sha1(PWD), "deprecated", false, "{\"api\":1}"});
    if (resp == 1) {
      LOGGER.log(Level.INFO, "login success");
    } else {
      LOGGER.log(Level.SEVERE, "admin login fail for {0}", USER);
    }
    return resp;
  }

  public JSONObject login(String user, String pwd) throws Exception {
    LOGGER.log(Level.INFO, "logining " +user);
    if (connect()) {
      int resp = (Integer) sendRequest("login", new Object[]{sid, user, sha1(pwd), "deprecated", false, "{\"api\":1}"});
      if (resp == 1) {
        LOGGER.log(Level.INFO, "login success");
        return getUserInfo();
      } else {
        LOGGER.log(Level.WARNING, "login fail for {0}", user);
        return new JSONObject().put("error", "login fail");
      }
    } else {
      return new JSONObject().put("error", "login fail");
    }
  }

  public String userInfo() throws Exception {
    if (connect()) {
      Object o = sendRequest("get_current_user", new Object[]{sid});
      System.out.println(o);
      return o.toString();

    } else {
      return "Connect  failed";
    }
  }

  public JSONObject getDocById(int id, boolean deep) throws Exception {
    if (connect()) {
      return getDoc(id, deep);
    } else {
      return new JSONObject();
    }

  }

  public JSONObject nactiInfo(String typ, int id, boolean deep) throws Exception {
    if (connect()) {
      Object o = sendRequest("nacti_informace", new Object[]{sid, typ, deep, id + ""});
      Object[] os = (Object[]) o;

      for (Object ob : os) {
        if (ob instanceof HashMap) {
          return new JSONObject((Map) ob);
        }
      }
    }
    return new JSONObject();
  }

  public HashMap<String, Object> nactiInfoMap(String typ, int id, boolean deep) throws Exception {
    if (connect()) {
      Object o = sendRequest("nacti_informace", new Object[]{sid, typ, deep, id + ""});
      Object[] os = (Object[]) o;

      for (Object ob : os) {
        if (ob instanceof HashMap) {
          return (HashMap) ob;
        }
      }
    }
    return new HashMap<>();
  }

  private JSONObject getDoc(int id, boolean deep) {
    try {
      Object o = sendRequest("nacti_informace", new Object[]{sid, "dokument", deep, id + ""});
      Object[] os = (Object[]) o;

      for (Object ob : os) {
        if (ob instanceof HashMap) {
          return new JSONObject((Map) ob);
        }
      }
      return new JSONObject();
    } catch (Exception ex) {
      Logger.getLogger(AmcrAPI.class.getName()).log(Level.SEVERE, null, ex);
      return new JSONObject();
    }
  }

  private String jsonToUrlParams(JSONObject jo) {
    StringBuilder sb = new StringBuilder();
    Iterator<?> keys = jo.keys();

    while (keys.hasNext()) {
      String key = (String) keys.next();
      sb.append(key).append("=").append(jo.getString(key)).append("&");
    }
    return sb.toString();
  }

  public JSONObject findDoc(String id, boolean deep) throws Exception {
    JSONObject jo = new JSONObject();
    JSONArray ja = new JSONArray();

    if (connect()) {
      JSONObject defParams = conf.getJSONObject("params");
      defParams.put("id_cj", id);
      Object o = sendRequest("hledej_dokument", new Object[]{sid, jsonToUrlParams(defParams)});
      Object[] os = (Object[]) o;

      for (Object ob : os) {

        if (ob instanceof HashMap) {
          Map map = (Map) ob;
          if (!map.containsKey("onlyForDebugging") || !map.get("onlyForDebugging").equals(1)) {
            JSONObject jo1 = new JSONObject(map);
            jo1.put("fields", getDoc((int) map.get("id"), deep));
            ja.put(jo1);
          } else {
            LOGGER.log(Level.INFO, "{0}", ob);
          }

        } else {
          LOGGER.log(Level.INFO, "{0}", ob);
        }

      }

      LOGGER.log(Level.INFO, "Found {0} docs", ja.length());

      jo.put("docs", ja);

    } else {
      jo.put("error", "Connect  failed");
    }
    return jo;

  }
  public JSONObject findAkce(String id, boolean deep) throws Exception {
    JSONObject jo = new JSONObject();
    JSONArray ja = new JSONArray();

    if (connect()) {
      JSONObject defParams = conf.getJSONObject("params");
      defParams.put("id_cj", id);
      String url = "rozsah=&typ_akce_vedlejsi=-1&uzivatelske_oznaceni=&typ=N&okres=-1&datum_zmeny_do=&nz_odlozene=&stav_akce=-1&pristupnost=-1&zahrnute=&limit=10&jako_projekt=1&datum_do=&organizace=-1&zahajene=&typ_akce=-1&obdobi_od=-1&nz_vracene=&stranka=0&vedouci_akce_ostatni=&odpovedny_pracovnik_zapisu=-1&datum_zmeny_od=&rozepsane_sledovani=&popisne_udaje=&typ_akce_hlavni=-1&rozepsane=&archivovane_zaa=&dalsi_katastry=&samostatne_bez_nz_navrzene_k_archivaci=&nz_podane=&uzivatel=-1&zahajene_sledovani=&k_archivaci=-1&katastr=-1&order_ascending=asc&samostatne_bez_nz_navrzene_k_archivaci_sledovani=&stav=&datum_od=&kraj=-1&nadrazene=&vracene_zaa_sledovani=&organizace_ostatni=&obdobi_do=-1&napojene_na_dokument_id=-1&nz_archivovane=&nz_odlozene_sledovani=&archivovane_zaa_sledovani=&nz_podane_sledovani=&order_by=okres&nz_archivovane_sledovani=&nz_vracene_sledovani=&vedouci_projektu=-1&vracene_zaa=&";
      url += "&id_cj=" + id;
      Object o = sendRequest("hledej_akce", new Object[]{sid, url});
      Object[] os = (Object[]) o;

      for (Object ob : os) {

        if (ob instanceof HashMap) {
          Map map = (Map) ob;
          if (!map.containsKey("onlyForDebugging") || !map.get("onlyForDebugging").equals(1)) {
            JSONObject jo1 = new JSONObject(map);
            jo1.put("fields", getDoc((int) map.get("id"), deep));
            ja.put(jo1);
          } else {
            LOGGER.log(Level.INFO, "{0}", ob);
          }

        } else {
          LOGGER.log(Level.INFO, "{0}", ob);
        }

      }

      LOGGER.log(Level.INFO, "Found {0} docs", ja.length());

      jo.put("docs", ja);

    } else {
      jo.put("error", "Connect  failed");
    }
    return jo;

  }

  public JSONObject searchDocs(JSONObject params, boolean deep) throws Exception {
    JSONObject jo = new JSONObject();
    JSONArray ja = new JSONArray();

    if (connect()) {
      JSONObject defParams = conf.getJSONObject("params");
      Iterator keys = params.keys();
      while (keys.hasNext()) {
        String key = (String) keys.next();
        LOGGER.log(Level.INFO, "key {0} will be overrided", key);
        defParams.put(key, params.get(key));
      }
      Object o = sendRequest("hledej_dokument", new Object[]{sid, jsonToUrlParams(defParams)});
      Object[] os = (Object[]) o;

      for (Object ob : os) {

        if (ob instanceof HashMap) {
          Map map = (Map) ob;
          if (!map.containsKey("onlyForDebugging") || !map.get("onlyForDebugging").equals(1)) {
            JSONObject jo1 = new JSONObject(map);
            jo1.put("fields", getDoc((int) map.get("id"), deep));
            ja.put(jo1);
          } else {
            LOGGER.log(Level.INFO, "{0}", ob);
          }

        } else {
          LOGGER.log(Level.INFO, "{0}", ob);
        }

      }

      LOGGER.log(Level.INFO, "Found {0} docs", ja.length());

      jo.put("docs", ja);

    } else {
      jo.put("error", "Connect  failed");
    }
    return jo;

  }

  public JSONObject getDocs(String rok_od, String rok_do, boolean deep) throws Exception {
    JSONObject jo = new JSONObject();
    JSONArray ja = new JSONArray();

    if (connect()) {
      JSONObject defParams = conf.getJSONObject("params");
      defParams.put("rok_od", rok_od);
      defParams.put("rok_do", rok_do);
      Object o = sendRequest("hledej_dokument", new Object[]{sid, jsonToUrlParams(defParams)});
      Object[] os = (Object[]) o;

      for (Object ob : os) {

        if (ob instanceof HashMap) {
          Map map = (Map) ob;
          if (!map.containsKey("onlyForDebugging") || !map.get("onlyForDebugging").equals(1)) {
            JSONObject jo1 = new JSONObject(map);
            jo1.put("fields", getDoc((int) map.get("id"), deep));
            ja.put(jo1);
          } else {
            LOGGER.log(Level.INFO, "{0}", ob);
          }

        } else {
          LOGGER.log(Level.INFO, "{0}", ob);
        }

      }

      LOGGER.log(Level.INFO, "Found {0} docs", ja.length());

      jo.put("docs", ja);

    } else {
      jo.put("error", "Connect  failed");
    }
    return jo;

  }

  public JSONArray getIds(String rok_od, String rok_do) throws Exception {
    //JSONObject jo = new JSONObject();
    int limit = 100;
    int found = limit;
    int stranka = 0;
    JSONArray ja = new JSONArray();
    if (connect()) {

      while (found == limit) {
        found = getIds(rok_od, rok_do, stranka, limit, ja);
//        LOGGER.log(Level.INFO, "Found {0} docs", found);
//        LOGGER.log(Level.INFO, "Last id : {0}", ja.getJSONObject(ja.length()-1).getInt("id"));
        //stranka += limit;
        stranka++;
      }

      //jo.put("docs", ja);
    } else {
      //jo.put("error", "Connect  failed");
    }

    LOGGER.log(Level.INFO, "Found total {0} docs", ja.length());
    return ja;
  }

  public int getIds(String rok_od, String rok_do, int stranka, int limit, JSONArray ja) throws Exception {

    JSONObject defParams = conf.getJSONObject("params");
    defParams.put("rok_od", rok_od);
    defParams.put("rok_do", rok_do);
    defParams.put("stranka", stranka + "");
    defParams.put("limit", limit + "");
    Object o = sendRequest("hledej_dokument", new Object[]{sid, jsonToUrlParams(defParams)});
    Object[] os = (Object[]) o;
    int found = 0;

    for (Object ob : os) {

      if (ob instanceof HashMap) {
        Map map = (Map) ob;
        if (!map.containsKey("onlyForDebugging") || !map.get("onlyForDebugging").equals(1)) {
          JSONObject jo1 = new JSONObject(map);
          ja.put(jo1);
          found++;
        } else {
          LOGGER.log(Level.INFO, "{0}", ob);
        }

      } else {
        LOGGER.log(Level.FINE, "{0}", ob);
      }

    }

    return found;

  }

  public JSONObject getUserInfo() throws Exception {
    JSONObject ja = new JSONObject();

    if (connect()) {
      Object o = sendRequest("get_current_user", new Object[]{sid});
      if (o instanceof HashMap) {
        Map map = (Map) o;
        if (!map.containsKey("onlyForDebugging") || !map.get("onlyForDebugging").equals(1)) {
          JSONObject jo1 = new JSONObject(map);

          ja.put(map.get("id") + "", jo1);
        } else {
          LOGGER.log(Level.FINE, "{0}", o);
        }

        return ja;
      }
      Object[] os = (Object[]) o;

      for (Object ob : os) {

        if (ob instanceof HashMap) {
          Map map = (Map) ob;
          if (!map.containsKey("onlyForDebugging") || !map.get("onlyForDebugging").equals(1)) {
            JSONObject jo1 = new JSONObject(map);

            ja.put(map.get("id") + "", jo1);
          } else {
            LOGGER.log(Level.FINE, "{0}", ob);
          }

        } else {
          LOGGER.log(Level.FINE, "{0}", ob);
        }

      }

      LOGGER.log(Level.INFO, "Found {0} docs", ja.length());

    }
    return ja;

  }

  public JSONObject getHeslar(String id) throws Exception {
    JSONObject ja = new JSONObject();

    if (connect()) {
      Object o = sendRequest("get_list", new Object[]{sid, id});
      Object[] os = (Object[]) o;

      for (Object ob : os) {

        if (ob instanceof HashMap) {
          Map map = (Map) ob;
          if (!map.containsKey("onlyForDebugging") || !map.get("onlyForDebugging").equals(1)) {
            JSONObject jo1 = new JSONObject(map);

            ja.put(map.get("id") + "", jo1);
          } else {
            LOGGER.log(Level.FINE, "{0}", ob);
          }

        } else {
          LOGGER.log(Level.FINE, "{0}", ob);
        }

      }

      LOGGER.log(Level.INFO, "Found {0} docs", ja.length());

    }
    return ja;

  }

  public JSONObject nactiVazby(String typ, int id) throws Exception {
    JSONObject jo = new JSONObject();
    JSONArray ja = new JSONArray();
    if (connect()) {
      Object o = sendRequest("nacti_vazby", new Object[]{sid, typ, id});
      Object[] os = (Object[]) o;
      for (Object ob : os) {
        if (ob instanceof HashMap) {
          Map map = (Map) ob;
          if (!map.containsKey("onlyForDebugging") || !map.get("onlyForDebugging").equals(1)) {
            JSONObject jo1 = new JSONObject(map);
            ja.put(jo1);
          } else {
            LOGGER.log(Level.INFO, "{0}", ob);
          }
        }
      }
      jo.put(typ, ja);

    }
    return jo;

  }

  public JSONObject getSoubory(int id) throws Exception {
    return nactiVazby("dokument_soubor", id);
  }

  public JSONObject getVazby(int id) throws Exception {
    return nactiVazby("vazby_na_dokument", id);

  }

  public JSONObject getOdkaz(int id) throws Exception {
    return nactiVazby("odkaz", id);

  }

}
