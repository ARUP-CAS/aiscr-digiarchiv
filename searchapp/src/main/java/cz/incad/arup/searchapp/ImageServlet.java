/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.incad.arup.searchapp;

import cz.incad.arup.searchapp.imaging.ImageSupport;
import cz.incad.arup.searchapp.index.Indexer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author alberto
 */
@WebServlet(name = "ImageServlet", urlPatterns = {"/img"})
public class ImageServlet extends HttpServlet {

  public static final Logger LOGGER = Logger.getLogger(ImageServlet.class.getName());

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
    try (OutputStream out = response.getOutputStream()) {
      String id = request.getParameter("id");
      String size = request.getParameter("size");
      boolean full = Boolean.parseBoolean(request.getParameter("full"));
      Options opts = Options.getInstance();
      boolean dynamicThumbs = opts.getBoolean("dynamicThumbs", false);
      if (full) {
        String imagesDir = opts.getString("imagesDir");
        String path = request.getParameter("filepath");
        File f = new File(imagesDir + path);
        IOUtils.copy(new FileInputStream(f), out);
        return;
      }
      if (size == null) {
        size = "thumb";
      }
      if (id != null && !id.equals("")) {
        try {

          //String fname = Options.getInstance().getString("thumbsDir") + id + ".jpg";
          String dest = ImageSupport.getDestDir(id) + id + ".jpg";
          String fname = ImageSupport.getDestDir(id) + id + "_" + size + ".jpg";
          File f = new File(fname);
          if (f.exists()) {
            response.setContentType("image/jpeg");
            BufferedImage bi = ImageIO.read(f);
            ImageSupport.addWatermark(bi, logoImg(response, out), (float) opts.getDouble("watermark.alpha", 0.2f));
            ImageIO.write(bi, "jpg", out);
          } else if (dynamicThumbs) {
            Indexer indexer = new Indexer();
            String t = indexer.createThumb(id, "thumb".equals(size));
            //String t = ImageSupport.thumbnail(id);
            if (t != null) {
              response.setContentType("image/jpeg");
              BufferedImage bi = ImageIO.read(new File(fname));
              ImageSupport.addWatermark(bi, logoImg(response, out), (float)opts.getDouble("watermark.alpha", 0.2f));
              ImageIO.write(bi, "jpg", out);
            } else {
              LOGGER.info("no image");
              emptyImg(response, out);
            }
          } else {
            LOGGER.info("no image");
            emptyImg(response, out);
          }
        } catch (Exception ex) {
          LOGGER.log(Level.SEVERE, null, ex);
          emptyImg(response, out);
        }
      } else {
        LOGGER.info("no id");
        emptyImg(response, out);
      }
    }
  }

  private BufferedImage logoImg(HttpServletResponse response, OutputStream out) throws IOException {
    String empty = getServletContext().getRealPath(File.separator) + "/assets/img/logo-watermark-white.png";
    response.setContentType("image/gif");
    return ImageIO.read(new File(empty));

  }

  private void emptyImg(HttpServletResponse response, OutputStream out) throws IOException {
    String empty = getServletContext().getRealPath(File.separator) + "/assets/img/empty.gif";
    response.setContentType("image/gif");
    BufferedImage bi = ImageIO.read(new File(empty));
    ImageIO.write(bi, "gif", out);

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