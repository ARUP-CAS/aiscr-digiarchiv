/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.incad.arup.thumbnailsgenerator;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.json.JSONException;

/**
 *
 * @author alberto
 */
public class PDFThumbsGenerator {

  public static final Logger LOGGER = Logger.getLogger(PDFThumbsGenerator.class.getName());

  public int generated;
  Options opts;
  int maxPixels;
  int maxMedium;

  int t_width;
  int t_height;

  List<String> unprocessables;

  public PDFThumbsGenerator(boolean forced) {

    try {
      opts = Options.getInstance();
      maxPixels = opts.getInt("maxPixels", 2000 * 2000);
      maxMedium = opts.getInt("mediumHeight", 1000);

      t_width = opts.getInt("thumbWidth", 100);
      t_height = opts.getInt("thumbHeight", 100);

      unprocessables = readUnprocessable();

      //Test if the file was last processed before crash;
      String lastProcessed = readProcessing();
      if (!forced) {
        if (!"".equals(lastProcessed) && !unprocessables.contains(lastProcessed)) {
          LOGGER.log(Level.INFO, "Last attemp to generate file {0} failed. Writing to unpracessables.txt", lastProcessed);
          writeUnprocessable(lastProcessed);
          writeProcessing("");
          return;
        }
      }

      generated = 0;
    } catch (IOException | JSONException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  public void processFile(File f, boolean force, boolean onlyThumbs) {

    if (!force) {
      //Test if the file was last processed before crash;
      String lastProcessed = readProcessing();
      if (f.getName().equals(lastProcessed)) {
        LOGGER.log(Level.INFO, "Last attemp to generate file {0} failed. Writing to unpracessables.txt. Skipping it", f.getName());
        writeUnprocessable(f.getName());
        writeProcessing("");
        return;
      }

      if (unprocessables.contains(f.getName())) {
        LOGGER.log(Level.INFO, "File {0} is in unprocessables.txt. Skipping it", f.getName());
        return;
      }
      writeProcessing(f.getName());
    }
    LOGGER.log(Level.INFO, "Generating medium thumbs for pdf {0}", f);

    try {
      int pageCounter = 0;
      try (PDDocument document = PDDocument.load(f)) {
        PDFRenderer pdfRenderer = new PDFRenderer(document);
        for (PDPage page : document.getPages()) {
          LOGGER.log(Level.FINE, "page {0}", pageCounter + 1);

//                        getImagesFromResources(page.getResources());
          BufferedImage bim = getImageFromPage(pdfRenderer, pageCounter);
          if (pageCounter == 0) {
            thumbnailPdfPage(bim, f.getName());
          }

          if (onlyThumbs) {
            break;
          }
          processPage(bim, pageCounter, f.getName());
          pageCounter++;
        }
        writeProcessing("");
      } catch (IOException ex) {
        LOGGER.log(Level.SEVERE, f.getName() + " has error: {0}", ex);
        LOGGER.log(Level.SEVERE, null, ex);
        ImageSupport.writeSkipped(pageCounter, f.getName(), ex.toString());
      } catch (Exception ex) {
        LOGGER.log(Level.SEVERE, f.getName() + " has error: {0}", ex);
        LOGGER.log(Level.SEVERE, null, ex);
        ImageSupport.writeSkipped(pageCounter, f.getName(), ex.toString());
      }
    } catch (JSONException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private BufferedImage getImageFromPage(PDFRenderer pdfRenderer, int page) throws Exception {
    return pdfRenderer.renderImageWithDPI(page, 72, ImageType.RGB);
  }

  public String thumbnailPdfPage(BufferedImage sourceImage, String id) {

    if (sourceImage == null) {
      LOGGER.log(Level.WARNING, "Cannot read image for page 0 in file {0}", id);
      return "Cannot read image";
    }
    int width = sourceImage.getWidth();
    int height = sourceImage.getHeight();

    if (width * height > maxPixels) {
      writeSkipped(0, id, width + " x " + height);
      return null;
    }

    try {
      int w = opts.getInt("thumbWidth", 100);
      String destDir = ImageSupport.makeDestDir(id);
      new File(destDir).mkdir();
      String outputFile = destDir + id + "_thumb.jpg";
      Thumbnails.of(sourceImage)
              .size(w, w)
              .crop(Positions.CENTER)
              .outputFormat("jpg")
              .toFile(outputFile);

      generated++;
      return outputFile;
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, "Error in image resizer:", ex);
      return null;
    }
  }

  public String thumbnailPdfPage2(BufferedImage sourceImage, String id) {
    try {

      if (sourceImage == null) {

        LOGGER.log(Level.WARNING, "Cannot read image for page 0 in file {0}", id);
        return "Cannot read image";
      }

      int width = sourceImage.getWidth();
      int height = sourceImage.getHeight();

      if (width * height < maxPixels) {
        BufferedImage img2;
        if (width > height) {
          float extraSize = height - t_height;
          float percentHight = (extraSize / height) * 100;
          float percentWidth = width - ((width / t_width) * percentHight);

//                    BufferedImage img = new BufferedImage((int) percentWidth, t_height, BufferedImage.TYPE_INT_RGB);
//                    Image scaledImage = sourceImage.getScaledInstance((int) percentWidth, t_height, Image.SCALE_SMOOTH);
//                    img.createGraphics().drawImage(scaledImage, 0, 0, null);
//                     img2 = img.getSubimage((int) ((percentWidth - 100) / 2), 0, t_width, t_height);
          img2 = ImageSupport.scaleAndCrop(sourceImage, (int) ((percentWidth - 100) / 2), 0, t_width, t_height);

        } else {
          float extraSize = width - t_width;
          float percentWidth = (extraSize / width) * 100;
          float percentHight = height - ((height / t_height) * percentWidth);
//                    BufferedImage  img = new BufferedImage(t_width, (int) percentHight, BufferedImage.TYPE_INT_RGB);
//                    Image scaledImage = sourceImage.getScaledInstance(t_width, (int) percentHight, Image.SCALE_SMOOTH);
//                    img.createGraphics().drawImage(scaledImage, 0, 0, null);

//                img2 = img.getSubimage(0, (int) ((percentHight - 100) / 2), t_width, t_height);
          img2 = ImageSupport.scaleAndCrop(sourceImage, 0, (int) ((percentHight - 100) / 2), t_width, t_height);

        }

        String destDir = ImageSupport.makeDestDir(id);
        new File(destDir).mkdir();
        String outputFile = destDir + id + "_thumb.jpg";
        ImageIO.write(img2, "jpg", new File(outputFile));
        img2.flush();

        generated++;
        return outputFile;

      } else {
        writeSkipped(0, id, width + " x " + height);
        return null;
      }

    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      return null;
    }

  }

  public void processPage(BufferedImage bim, int pageCounter, String id) throws IOException {

    String outputFile = null;

    int width = bim.getWidth();
    int height = bim.getHeight();

    if (width * height < maxPixels) {
      int w;
      int h;
      if (height > width) {
        double ratio = maxMedium * 1.0 / height;
        w = (int) Math.max(1, Math.round(width * ratio));
        h = maxMedium;
      } else {
        double ratio = maxMedium * 1.0 / width;
        h = (int) Math.max(1, Math.round(height * ratio));
        w = maxMedium;
      }

      String destDir = ImageSupport.makeDestDir(id) + id + File.separator;
      new File(destDir).mkdir();
      outputFile = destDir + (pageCounter) + ".jpg";
      File f = new File(outputFile);
      ImageSupport.resizeWithThumbnailator(bim, w, h, f);
      //BufferedImage img2 = ImageSupport.scale(bim, w, h);
//            ImageIO.write(img2, "jpg", new File(outputFile));
//            LOGGER.info(outputFile);
//            img2.flush();
      generated++;

    } else {
      writeSkipped(pageCounter, id, width + " x " + height);
    }

  }

  int res = 0;

  private void logRes(COSDictionary d) {
    for (Map.Entry<COSName, COSBase> entry : d.entrySet()) {
      res++;
      COSBase b = entry.getValue();

      System.out.println("");
      System.out.print(entry.getKey().getName());
      System.out.print("\t");
      if (b instanceof COSDictionary) {
        COSDictionary d1 = (COSDictionary) b;
        System.out.print(d1.size());
        logRes(d1);
      } else if (b instanceof COSObject) {

        COSObject d1 = (COSObject) b;
        if (d1.getObject() instanceof COSDictionary) {
          COSDictionary d2 = (COSDictionary) d1.getObject();
          System.out.print(d2.size());
          logRes(d2);
        }
      }
    }
  }

  private List<RenderedImage> getImagesFromResources(PDResources resources) throws IOException {
    List<RenderedImage> images = new ArrayList<>();
    int i = 0;
    for (Map.Entry<COSName, COSBase> entry : resources.getCOSObject().entrySet()) {
      res++;

      System.out.println(entry.getKey().getName());
      COSBase b = entry.getValue();
      if (b instanceof COSDictionary) {
        COSDictionary d = (COSDictionary) b;
        System.out.println(d.size());
        logRes(d);
      }

    }
    System.out.println("res -----------> " + res);
    i = 0;

    for (COSName xObjectName : resources.getXObjectNames()) {
      PDXObject xObject = resources.getXObject(xObjectName);

      if (xObject instanceof PDFormXObject) {
        System.out.println("je to form");
        images.addAll(getImagesFromResources(((PDFormXObject) xObject).getResources()));
      } else if (xObject instanceof PDImageXObject) {
        i++;
        System.out.println(((PDImageXObject) xObject).getSuffix());
      }
    }

    LOGGER.log(Level.INFO, "resources: {0}", i);
    return images;
  }

  private String readProcessing() {

    try {
      File file = new File(opts.getString("thumbsDir") + File.separator + "processing.txt");
      if (file.exists()) {
        return FileUtils.readFileToString(file, "UTF-8");
      } else {
        return "";
      }
    } catch (IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      return "";
    }
  }

  private void writeProcessing(String name) {
    try {
      File file = new File(opts.getString("thumbsDir") + File.separator + "processing.txt");
      FileUtils.writeStringToFile(file, name, "UTF-8", false);
    } catch (IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private List<String> readUnprocessable() {

    try {
      File file = new File(opts.getString("thumbsDir") + File.separator + "unprocessables.txt");
      if (file.exists()) {
        return FileUtils.readLines(file, "UTF-8");
      } else {
        return new ArrayList<>();
      }
    } catch (IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      return null;
    }
  }

  private void writeUnprocessable(String name) {
    try {
      File file = new File(opts.getString("thumbsDir") + File.separator + "unprocessables.txt");
      FileUtils.writeStringToFile(file, name + System.getProperty("line.separator"), "UTF-8", true);
    } catch (IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  private static void writeSkipped(int page, String id, String size) {
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
}
