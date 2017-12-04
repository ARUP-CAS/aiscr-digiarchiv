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
@WebServlet(name = "PdfServlet", urlPatterns = {"/pdf"})
public class PdfServlet extends HttpServlet {

    public static final Logger LOGGER = Logger.getLogger(PdfServlet.class.getName());

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
            String page = request.getParameter("page");
            String size = request.getParameter("size");
            if (size == null) {
                size = "thumb";
            }
            boolean full = Boolean.parseBoolean(request.getParameter("full"));
            if (id != null && !id.equals("")) {
                try {
                    if (full) {

                        Options opts = Options.getInstance();
                        String imagesDir = opts.getString("imagesDir");
                        String path = request.getParameter("filepath");
                        File f = new File(imagesDir + path);
                        IOUtils.copy(new FileInputStream(f), out);
                    } else {
                        //String fname = Options.getInstance().getString("thumbsDir") + id + ".jpg";
                        String fname = ImageSupport.getDestDir(id) + id + File.separator + page + ".jpg";
                        File f = new File(fname);
                        if (f.exists()) {
                            response.setContentType("image/jpeg");
                            BufferedImage bi = ImageIO.read(f);
                            ImageSupport.addWatermark(bi, logoImg(response, out), (float) Options.getInstance().getDouble("watermark.alpha", 0.2f));
                            ImageIO.write(bi, "jpg", out);
                        } else {
                            Indexer indexer = new Indexer();
                            String t = indexer.createThumb(id, "thumb".equals(size));
                            //String t = ImageSupport.thumbnail(id);
                            if (t != null) {
                                //String fname = ImageSupport.getDestDir(id) + id + File.separator + page + ".jpg";
                                //File f = new File(fname);
                                response.setContentType("image/jpeg");
                                BufferedImage bi = ImageIO.read(new File(fname));
                                ImageIO.write(bi, "jpg", out);
                            } else {
                                LOGGER.info("no image");
                                emptyImg(response, out);
                            }
                        }
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
