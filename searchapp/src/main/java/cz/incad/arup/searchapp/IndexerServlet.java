/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.incad.arup.searchapp;

import cz.incad.arup.searchapp.index.AmcrAPI;
import cz.incad.arup.searchapp.index.CSVIndexer;
import cz.incad.arup.searchapp.index.Indexer;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;

/**
 *
 * @author alberto
 */
public class IndexerServlet extends HttpServlet {

  public static final Logger LOGGER = Logger.getLogger(IndexerServlet.class.getName());
  public static final String ACTION_NAME = "action";

  /**
   * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
   * methods.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  protected void processRequest(HttpServletRequest req, HttpServletResponse resp)
          throws ServletException, IOException {
    try {

      String actionNameParam = req.getParameter(ACTION_NAME);
      if (actionNameParam != null) {

        Set<String> localAddresses = new HashSet<String>();
        localAddresses.add(InetAddress.getLocalHost().getHostAddress());
        for (InetAddress inetAddress : InetAddress.getAllByName("localhost")) {
          localAddresses.add(inetAddress.getHostAddress());
        }
        if (localAddresses.contains(req.getRemoteAddr())) {
          LOGGER.log(Level.INFO, "running from local address");
          Actions actionToDo = Actions.valueOf(actionNameParam.toUpperCase());
          actionToDo.doPerform(req, resp);
        } else {

          String pristupnost = LoginServlet.pristupnost(req.getSession());
          String confLevel = Options.getInstance().getString("indexSecLevel", "E");
          LOGGER.log(Level.FINE, "pristupnost ->" + pristupnost + ". confLevel ->" + confLevel);
          if (pristupnost.compareTo(confLevel) >= 0) {
            Actions actionToDo = Actions.valueOf(actionNameParam.toUpperCase());
            actionToDo.doPerform(req, resp);
          } else {
            PrintWriter out = resp.getWriter();
            out.print("Insuficient rights");
          }
        }
      } else {
        PrintWriter out = resp.getWriter();
        out.print("Action missing");
      }
    } catch (IOException e1) {
      LOGGER.log(Level.SEVERE, e1.getMessage(), e1);
      resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e1.toString());
      PrintWriter out = resp.getWriter();
      out.print(e1.toString());
    } catch (SecurityException e1) {
      LOGGER.log(Level.SEVERE, e1.getMessage(), e1);
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
    } catch (Exception e1) {
      LOGGER.log(Level.SEVERE, e1.getMessage(), e1);
      resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      PrintWriter out = resp.getWriter();
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e1.toString());
      out.print(e1.toString());
    }

  }

  enum Actions {
    USER_INFO2 {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Options.resetInstance();

        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {

          AmcrAPI amcr = new AmcrAPI();

          json = amcr.login(req.getParameter("user"), req.getParameter("pwd"));

        } catch (Exception ex) {
          json.put("error", ex.toString());
        }
        out.println(json.toString(2));
      }
    },
    USER_INFO {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Options.resetInstance();

        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {

          AmcrAPI amcr = new AmcrAPI();

          json = amcr.getUserInfo();

        } catch (Exception ex) {
          json.put("error", ex.toString());
        }
        out.println(json.toString(2));
      }
    },
    HESLAR {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {

          AmcrAPI amcr = new AmcrAPI();

          json = amcr.getHeslar(req.getParameter("id"));

        } catch (Exception ex) {
          json.put("error", ex.toString());
        }
        out.println(json.toString(2));
      }
    },
    INDEX_HESLAR {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {

          Indexer indexer = new Indexer();

          JSONObject r = indexer.indexHeslar(req.getParameter("id"));

          json.put("message", r);
        } catch (Exception ex) {
          json.put("error", ex.toString());
        }
        out.println(json.toString(2));
      }
    },
    INDEX_HESLARE {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {
          if (Boolean.parseBoolean(req.getParameter("clean"))) {
            Indexer indexer = new Indexer();
            json.put("clean heslare", indexer.cleanHeslare());
          }
          Indexer indexer = new Indexer();

          JSONObject r = indexer.indexHeslare();

          json.put("message", r);
        } catch (Exception ex) {
          json.put("error", ex.toString());
        }
        out.println(json.toString(2));
      }
    },
    CLEAN {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {

          Indexer indexer = new Indexer();
          JSONObject r = indexer.clean();

          json.put("message", r);
        } catch (Exception ex) {
          json.put("error", ex.toString());
        }
        out.println(json.toString(2));
      }
    },
    FULL {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        boolean oldIndexing = Options.setIndexingFlag(true);
        try {
          if (oldIndexing) {
            throw new Exception("Indexing already in progress.");
          }

          Options.resetInstance();
          JSONObject r = new JSONObject();

          if (req.getParameter("clean") == null || Boolean.parseBoolean(req.getParameter("clean"))) {
            Indexer indexer = new Indexer();
            r.put("clean", indexer.clean());
            r.put("clean heslare", indexer.cleanHeslare());
          }

          Indexer indexer = new Indexer();
          r.put("heslare", indexer.indexHeslare());

          CSVIndexer csvindexer = new CSVIndexer();
          r.put("full", csvindexer.run());

//          Indexer indexer = new Indexer();
//
//          JSONObject r = indexer.indexExport();
          json.put("message", r);
        } catch (Exception ex) {
          json.put("error", ex.toString());
        }

        if (!Options.setIndexingFlag(oldIndexing)) {
          // shouldn't happen
          LOGGER.log(Level.WARNING, "indexing flag updates crossed");
        }

        out.println(json.toString(2));
      }
    },
    UPDATE {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {

          Options.resetInstance();
          Indexer indexer = new Indexer();
          JSONObject r = indexer.update(req.getParameter("rok_od"), req.getParameter("rok_do"));

          json.put("message", r);
        } catch (Exception ex) {
          json.put("error", ex.toString());
        }
        out.println(json.toString(2));
      }
    },
    INDEX_CSV {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {

          Options.resetInstance();
          CSVIndexer indexer = new CSVIndexer();
          JSONObject r = indexer.run();
          //JSONObject r = indexer.indexFiles(req.getParameterValues("filename"));

          out.println(r.toString(2));
        } catch (Exception ex) {
          json.put("error", ex.toString());

          out.println(json.toString(2));
        }
      }
    },
    INDEX_CSV_RELATIONS {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {

          Options.resetInstance();
          CSVIndexer indexer = new CSVIndexer();
          JSONObject r = indexer.indexRelations();

          out.println(r.toString(2));
        } catch (Exception ex) {
          json.put("error", ex.toString());

          out.println(json.toString(2));
        }
      }
    },
    INDEX_CSV_DOKUMENTS {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {

          Options.resetInstance();
          CSVIndexer indexer = new CSVIndexer();
          JSONObject r = indexer.indexDokuments();

          out.println(r.toString(2));
        } catch (Exception ex) {
          json.put("error", ex.toString());

          out.println(json.toString(2));
        }
      }
    },
    INDEX_CSV_PAS {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {

          Options.resetInstance();
          CSVIndexer indexer = new CSVIndexer();
          indexer.cleanPas();
          JSONObject r = indexer.indexPas();

          out.println(r.toString(2));
        } catch (Exception ex) {
          json.put("error", ex.toString());

          out.println(json.toString(2));
        }
      }
    },
    INDEX_DOKUMENT {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {

          Options.resetInstance();
          CSVIndexer indexer = new CSVIndexer();
          JSONObject r = indexer.indexDokument(req.getParameter("id"));

          out.println(r.toString(2));
        } catch (Exception ex) {
          json.put("error", ex.toString());

          out.println(json.toString(2));
        }
      }
    },
    INDEX_PAS_DOKUMENT {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {

          Options.resetInstance();
          CSVIndexer indexer = new CSVIndexer();
          JSONObject r = indexer.indexPasDokument(req.getParameter("id"));

          out.println(r.toString(2));
        } catch (Exception ex) {
          json.put("error", ex.toString());

          out.println(json.toString(2));
        }
      }
    },
    INDEX_CSV_TRANSLATIONS {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {

          Options.resetInstance();
          CSVIndexer indexer = new CSVIndexer();
          JSONObject r = indexer.indexTranslations();

          out.println(r.toString(2));
        } catch (Exception ex) {
          json.put("error", ex.toString());

          out.println(json.toString(2));
        }
      }
    },
    INDEX_CSV_TABLES {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {

          Options.resetInstance();
          CSVIndexer indexer = new CSVIndexer();
          JSONObject r = indexer.indexTables();

          out.println(r.toString(2));
        } catch (Exception ex) {
          json.put("error", ex.toString());

          out.println(json.toString(2));
        }
      }
    },
    INDEX_CSV_TABLE {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {

          Options.resetInstance();
          CSVIndexer indexer = new CSVIndexer();
          JSONObject r = indexer.indexTable(req.getParameter("doctype"));

          out.println(r.toString(2));
        } catch (Exception ex) {
          json.put("error", ex.toString());

          out.println(json.toString(2));
        }
      }
    },
    INDEX_CSV_RELATION {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {

          Options.resetInstance();
          CSVIndexer indexer = new CSVIndexer();
          JSONObject r = indexer.indexRelation(req.getParameter("doctype"));

          out.println(r.toString(2));
        } catch (Exception ex) {
          json.put("error", ex.toString());

          out.println(json.toString(2));
        }
      }
    },
    GET_DOC {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {
          AmcrAPI amcr = new AmcrAPI();

          json = amcr.getDocById(Integer.parseInt(req.getParameter("id")), true);

        } catch (Exception ex) {
          json.put("error", ex.toString());
        }
        out.println(json.toString(2));
      }
    },
    FIND_DOCS {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {
          AmcrAPI amcr = new AmcrAPI();

          JSONObject reqParams = new JSONObject(req.getParameter("params"));

          json = amcr.searchDocs(reqParams, true);

        } catch (Exception ex) {
          json.put("error", ex.toString());
        }
        out.println(json.toString(2));
      }
    },
    GET_DOC_BY_ID {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {
          AmcrAPI amcr = new AmcrAPI();

          JSONObject r = amcr.findDoc(req.getParameter("id"), true);

          json.put("message", r);
        } catch (Exception ex) {
          json.put("error", ex.toString());
        }
        out.println(json.toString(2));
      }
    },
    GET_AKCE_BY_ID {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {
          AmcrAPI amcr = new AmcrAPI();

          JSONObject r = amcr.findAkce(req.getParameter("id"), true);

          json.put("message", r);
        } catch (Exception ex) {
          json.put("error", ex.toString());
        }
        out.println(json.toString(2));
      }
    },
    GET_SOUBORY {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {
          AmcrAPI amcr = new AmcrAPI();

          JSONObject r = amcr.getSoubory(Integer.parseInt(req.getParameter("id")));

          json.put("message", r);
        } catch (Exception ex) {
          json.put("error", ex.toString());
        }
        out.println(json.toString(2));
      }
    },
    GET_VAZBY {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {
          AmcrAPI amcr = new AmcrAPI();

          JSONObject r = amcr.getVazby(Integer.parseInt(req.getParameter("id")));

          json.put("message", r);
        } catch (Exception ex) {
          json.put("error", ex.toString());
        }
        out.println(json.toString(2));
      }
    },
    NACTI_VAZBY {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {
          AmcrAPI amcr = new AmcrAPI();

          JSONObject r = amcr.nactiVazby(req.getParameter("typ"), Integer.parseInt(req.getParameter("id")));

          json.put("message", r);
        } catch (Exception ex) {
          json.put("error", ex.toString());
        }
        out.println(json.toString(2));
      }
    },
    NACTI_INFO {
      @Override
      void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        JSONObject json = new JSONObject();
        try {
          AmcrAPI amcr = new AmcrAPI();
          boolean deep = Boolean.parseBoolean(req.getParameter("deep"));
          JSONObject r = amcr.nactiInfo(req.getParameter("typ"), Integer.parseInt(req.getParameter("id")), deep);

          out.println(r.toString(2));
        } catch (Exception ex) {
          json.put("error", ex.toString());
          out.println(json.toString(2));
        }

      }
    };

    abstract void doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception;
  }

// <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
  /**
   * Handles the HTTP <code>GET</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
          throws ServletException, IOException {
    processRequest(request, response);
  }

  /**
   * Handles the HTTP <code>POST</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
          throws ServletException, IOException {
    processRequest(request, response);
  }

  /**
   * Returns a short description of the servlet.
   *
   * @return a String containing servlet description
   */
  @Override
  public String getServletInfo() {
    return "Short description";
  }// </editor-fold>

}
