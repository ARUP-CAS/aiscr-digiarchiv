package cz.incad.arup.searchapp;

import cz.incad.arup.searchapp.index.SolrIndex;
import javax.servlet.http.HttpServletRequest;

/**
 * @author SSC
 */
public class ImageAccess {
  public static boolean isAllowed(HttpServletRequest request) {
    boolean full = Boolean.parseBoolean(request.getParameter("full"));
    boolean allow;
    if (full) {
      // same as FileViewerComponent (in frontend)
      String userPr = LoginServlet.pristupnost(request.getSession());
      allow = (userPr != null) && (userPr.compareToIgnoreCase("D") >= 0);
    } else {
      String size = request.getParameter("size");
      if ((size == null) || "thumb".equals(size)) {
        allow = true;
      } else {
        String id = request.getParameter("id");
        String field = request.getParameter("field");
        if(field == null) {
          field = "dokument";
        }
        String imgPr = SolrIndex.getPristupnostBySoubor(id, field);
        if ("A".equals(imgPr)) {
          allow = true;
        } else {
          String userPr = LoginServlet.pristupnost(request.getSession());
          allow = (userPr != null) && (userPr.compareToIgnoreCase(imgPr) >= 0);
        }
      }
    }
    
    return allow;
  }
}

