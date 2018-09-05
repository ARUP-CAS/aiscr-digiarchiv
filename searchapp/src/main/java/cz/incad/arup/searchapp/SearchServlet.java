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
public class SearchServlet extends HttpServlet {

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
        
        
        String pristupnost = LoginServlet.pristupnost(request.getSession());
        

        LOGGER.log(Level.FINE, "requesting url {0}", solrhost + userFilter);
        InputStream inputStream = RESTHelper.inputStream(solrhost + userFilter);
        String solrResp = org.apache.commons.io.IOUtils.toString(inputStream, "UTF-8");
        JSONObject jo =  new JSONObject();
        if(request.getParameter("json.wrf") != null){
          String wrf = request.getParameter("json.wrf");
          jo =  new JSONObject(solrResp.substring(wrf.length()+1, solrResp.length() -2));
        }else{
          jo =  new JSONObject(solrResp);
        }
        
        filter(jo, pristupnost);

        if(request.getParameter("json.wrf") != null){
          response.getWriter().println(request.getParameter("json.wrf") + "(" + jo.toString() + ")");
        }else{
          response.getWriter().println(jo.toString(2));
        }
        
        //org.apache.commons.io.IOUtils.copy(inputStream, response.getOutputStream());
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
  
  private void filter(JSONObject jo, String pristupnost){
    JSONArray ja = jo.getJSONObject("response").getJSONArray("docs");
    for(int i = 0; i<ja.length(); i++){
      JSONObject doc = ja.getJSONObject(i);
      if(doc.has("lokalita_pristupnost")){
        JSONArray lp = doc.getJSONArray("lokalita_pristupnost");
        for(int j=lp.length()-1; j > -1 ; j--){
          if(lp.getString(j).compareTo(pristupnost) > 0){
            removeVal(doc, "lokalita_ident_cely", j);
            
            removeVal(doc, "lokalita_poznamka", j);
            removeVal(doc, "lokalita_katastr", j);
            removeVal(doc, "lokalita_typ_lokality", j);
            removeVal(doc, "lokalita_nazev", j);
            removeVal(doc, "lokalita_dalsi_katastry", j);
            removeVal(doc, "lokalita_stav", j);
            removeVal(doc, "lokalita_okres", j);
            removeVal(doc, "lokalita_druh", j);
            removeVal(doc, "lokalita_popis", j);
            
          }
        }
      }
      
      
      if(doc.has("akce_pristupnost")){
        JSONArray lp = doc.getJSONArray("akce_pristupnost");
        for(int j=lp.length()-1; j > -1 ; j--){
          if(lp.getString(j).compareTo(pristupnost) > 0){
            removeVal(doc, "akce_ident_cely", j);
            removeVal(doc, "akce_okres", j);
            removeVal(doc, "akce_katastr", j);
            removeVal(doc, "akce_dalsi_katastry", j);
            removeVal(doc, "akce_vedouci_akce", j);
            removeVal(doc, "akce_organizace", j);
            removeVal(doc, "akce_hlavni_typ", j);
            removeVal(doc, "akce_typ", j);
            removeVal(doc, "akce_vedlejsi_typ", j);
            removeVal(doc, "akce_datum_zahajeni_v", j);
            removeVal(doc, "akce_datum_ukonceni_v", j);
            removeVal(doc, "akce_lokalizace", j);
            removeVal(doc, "akce_poznamka", j);
            removeVal(doc, "akce_ulozeni_nalezu", j);
            removeVal(doc, "akce_vedouci_akce_ostatni", j);
            removeVal(doc, "akce_organizace_ostatni", j);
            removeVal(doc, "akce_stav", j);
            
          }
        }
      }
      
      
    }
  } 
  
  private void removeVal(JSONObject doc, String key, int j){
    if(doc.optJSONArray(key) != null){
      doc.getJSONArray(key).remove(j);
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
