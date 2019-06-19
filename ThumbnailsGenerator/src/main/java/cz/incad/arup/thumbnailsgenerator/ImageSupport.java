package cz.incad.arup.thumbnailsgenerator;

import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;


import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.Date;
import net.coobird.thumbnailator.ThumbnailParameter;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.apache.commons.io.FileUtils;
import org.json.JSONException;

/**
 *
 * @author alberto
 */
public class ImageSupport {

  public static final Logger LOGGER = Logger.getLogger(ImageSupport.class.getName());

  private static String resize(String outputFile, BufferedImage sourceImage, int t_width, int t_height) {

    try {
      if (sourceImage == null) {

        LOGGER.log(Level.WARNING, "Cannot read image");
        return "Cannot read image";
      }

      int width = sourceImage.getWidth();
      int height = sourceImage.getHeight();

      if (width > height) {
        float extraSize = height - t_height;
        float percentHight = (extraSize / height) * 100;
        float percentWidth = width - ((width / t_width) * percentHight);
        BufferedImage img = new BufferedImage((int) percentWidth, t_height, BufferedImage.TYPE_INT_RGB);
        Image scaledImage = sourceImage.getScaledInstance((int) percentWidth, t_height, Image.SCALE_SMOOTH);
        img.createGraphics().drawImage(scaledImage, 0, 0, null);
        BufferedImage img2;// = new BufferedImage(100, 100 ,BufferedImage.TYPE_INT_RGB);
        img2 = img.getSubimage((int) ((percentWidth - 100) / 2), 0, t_width, t_height);

        ImageIO.write(img2, "jpg", new File(outputFile));

        img.flush();
        img = null;
        img2.flush();
        img2 = null;
      } else {
        float extraSize = width - t_width;
        float percentWidth = (extraSize / width) * 100;
        float percentHight = height - ((height / t_height) * percentWidth);
        BufferedImage img = new BufferedImage(t_width, (int) percentHight, BufferedImage.TYPE_INT_RGB);
        Image scaledImage = sourceImage.getScaledInstance(t_width, (int) percentHight, Image.SCALE_SMOOTH);
        img.createGraphics().drawImage(scaledImage, 0, 0, null);
        BufferedImage img2;// = new BufferedImage(100, 100 ,BufferedImage.TYPE_INT_RGB);
        img2 = img.getSubimage(0, (int) ((percentHight - 100) / 2), t_width, t_height);

        ImageIO.write(img2, "jpg", new File(outputFile));

        img.flush();
        img = null;
        img2.flush();
        img2 = null;
      }
      sourceImage.flush();
      sourceImage = null;
      return outputFile;
    } catch (Exception ex) {

      LOGGER.log(Level.SEVERE, "Error creating thumb {0}, ", outputFile);
      LOGGER.log(Level.SEVERE, null, ex);
      return null;
    }
  }

  public static boolean thumbExists(String f) {

    String dest = getDestDir(f) + f + "_thumb.jpg";
    return (new File(dest)).exists();
  }

  public static boolean folderExists(String f) {
    String dest = getDestDir(f);
    return (new File(dest)).exists();
  }
  
  public static String getDestDir(String f){
    try {
      Options opts = Options.getInstance();
      String destDir = opts.getString("thumbsDir");
      String filename = f.substring(Math.max(0, f.lastIndexOf("/")), f.lastIndexOf("."));
      int period = 2;
      int levels = 3;
      int l = filename.length();

      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < levels; i++) {
        sb.append(filename.substring(l - (i * period) - period, l - (i * period))).append(File.separator);
      }

      //new File(destDir + sb.toString()).mkdirs();

      return destDir + sb.toString();
    } catch (IOException | JSONException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      return null;
    }
  }

  public static String makeDestDir(String f) {
   
      String destDir = getDestDir(f);

      new File(destDir).mkdirs();

      return destDir;
  }
  
    public static String thumbnailzeImg(File f, String id, boolean onlyThumbs) {

        String outputFile = getDestDir(id) + id;
        try {
            BufferedImage srcImage = ImageIO.read(f);
            Options opts = Options.getInstance();
            
            makeDestDir(id);
            int t_width = opts.getInt("thumbWidth", 100);
            int t_height = opts.getInt("thumbHeight", 100);

            resizeAndCropWithThumbnailator(srcImage, t_width, t_height, new File(outputFile + "_thumb.jpg"));
            
            if(!onlyThumbs){
                int max = opts.getInt("mediumHeight", 1000);
                resizeWithThumbnailator(srcImage, max, max, new File(outputFile + "_medium.jpg"));
            }

            return outputFile;
        } catch (Exception ex) {

            LOGGER.log(Level.SEVERE, "Error creating thumb {0}, ", outputFile);
            LOGGER.log(Level.SEVERE, null, ex);
            return null;
        }
    }

  public static void writeSkipped(int page, String id, String size) {
    String d = new Date().toString();
    try {
      if (page == -1) {
        //Image
        LOGGER.log(Level.WARNING, "skipping image {0} with size {1}", new Object[]{id, size});
        File file = new File(Options.getInstance().getString("thumbsDir") + File.separator + "skipped.txt");
        FileUtils.writeStringToFile(file, d + ".- Image in " + id + " Size: " + size + System.getProperty("line.separator"), "UTF-8", true);
      } else {
        //pdf page
        LOGGER.log(Level.WARNING, "skipping page {0} in file {1}. Image size {2}", new Object[]{page, id, size});
        File file = new File(Options.getInstance().getString("thumbsDir") + File.separator + "skipped.txt");
        FileUtils.writeStringToFile(file, d + ".- page " + page + " in " + id + " Image size: " + size + System.getProperty("line.separator"), "UTF-8", true);
      }
    } catch (IOException | JSONException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  public static void resizeAndCropWithThumbnailator(BufferedImage srcImage, int w, int h, File dest) {
        try {
                Thumbnails.of(srcImage)
                        .size(w, h)
                        .crop(Positions.CENTER)
                        .imageType(getImageType(srcImage))
                        .outputFormat("jpg")
                        .toFile(dest);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error in image resizer:", ex);
        }
    }
  
  public static void resizeWithThumbnailator(BufferedImage srcImage, int w, int h, File f) {
        byte[] retval = null;
        try {
//                ByteArrayOutputStream os = new ByteArrayOutputStream();

                Thumbnails.of(srcImage)
                        .size(w, h)
                        .imageType(getImageType(srcImage))
                        .outputFormat("jpg")
                        .toFile(f);
//                retval = os.toByteArray();
//                os.close();
            
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error in image resizer:", ex);
        }
//        return retval;
    }

  private static int getImageType(BufferedImage img) {
    WritableRaster raster = img.getRaster();
    int elemCount = raster.getNumDataElements();
    return (elemCount == 1) ? BufferedImage.TYPE_BYTE_GRAY : ThumbnailParameter.ORIGINAL_IMAGE_TYPE;
  }
}
