/*
 * Copyright (C) 2013 alberto
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package cz.incad.arup.searchapp;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author alberto
 */
public class ForwardServlet extends HttpServlet {

  public static final Logger LOGGER = Logger.getLogger(Options.class.getName());

  /**
   * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
   * methods.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  protected void processRequest(HttpServletRequest request, HttpServletResponse response)
          throws ServletException, IOException {
    try {

      response.setContentType("application/json;charset=UTF-8");
      response.addHeader("Access-Control-Allow-Origin", "*");
      response.addHeader("Access-Control-Allow-Methods", "GET, POST");

      int handlerIdx = request.getRequestURI().lastIndexOf("/") + 1;
      int solrIdx = request.getRequestURI().indexOf("/search/") + 8;
      String handler = request.getRequestURI().substring(handlerIdx);
      String core = request.getRequestURI().substring(solrIdx, handlerIdx);
      Options opts = Options.getInstance();
      JSONArray handlers = opts.getJSONArray("handlers");

      if (handlers.toJSONObject(handlers).has(handler)) {
        String solrhost = opts.getString("solrhost", "http://localhost:8983/solr/")
                + core + handler + "?" + request.getQueryString();
        String userFilter = "";
        if (core.equals("dokument")) {
          JSONObject ses = (JSONObject) request.getSession().getAttribute("user");
          if (ses == null) {
            userFilter = "&fq=pristupnost:A";
          } else if (ses.has("error")) {
            userFilter = "&fq=pristupnost:A";
          } else {
            String userid = (String) request.getSession().getAttribute("userid");
            String pristupnost = ses.getString("pristupnost");

            userFilter = "&fq=pristupnost:[A%20TO%20" + pristupnost + "]";
          }
        }
        

        LOGGER.log(Level.INFO, "requesting url {0}", solrhost + userFilter);
        InputStream inputStream = RESTHelper.inputStream(solrhost + userFilter);
        org.apache.commons.io.IOUtils.copy(inputStream, response.getOutputStream());
      } else {

        String msg = "Handler not allowed: " + handler;
        LOGGER.log(Level.SEVERE, msg);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg);
        PrintWriter out = response.getWriter();
        out.print(msg);
      }
    } catch (IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    } catch (JSONException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
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
