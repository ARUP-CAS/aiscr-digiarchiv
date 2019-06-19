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

  public static BufferedImage scaleAndCrop(BufferedImage img, int x, int y, int w, int h) {
      BufferedImage scaled = scale(img, w, h);
      return img.getSubimage(x, y, w, h);
  }

  public static BufferedImage scale(BufferedImage img, int targetWidth, int targetHeight) {
    ScalingMethod method = ScalingMethod.valueOf("BILINEAR");
    boolean higherQuality = true;
    return scale(img, targetWidth, targetHeight, method, higherQuality);
  }

  public static BufferedImage scale(BufferedImage img, int targetWidth, int targetHeight, ScalingMethod method, boolean higherQuality) {
    switch (method) {
      case REPLICATE:
        Image rawReplicate = img.getScaledInstance(targetWidth, targetHeight, Image.SCALE_REPLICATE);
        if (rawReplicate instanceof BufferedImage) {
          return (BufferedImage) rawReplicate;
        } else {
          return toBufferedImage(rawReplicate);
        }
      case AREA_AVERAGING:
        Image rawAveraging = img.getScaledInstance(targetWidth, targetHeight, Image.SCALE_AREA_AVERAGING);
        if (rawAveraging instanceof BufferedImage) {
          return (BufferedImage) rawAveraging;
        } else {
          return toBufferedImage(rawAveraging);
        }
      case BILINEAR:
        return getScaledInstanceJava2D(img, targetWidth, targetHeight, RenderingHints.VALUE_INTERPOLATION_BILINEAR, higherQuality);
      case BICUBIC:
        return getScaledInstanceJava2D(img, targetWidth, targetHeight, RenderingHints.VALUE_INTERPOLATION_BICUBIC, higherQuality);
      case NEAREST_NEIGHBOR:
        return getScaledInstanceJava2D(img, targetWidth, targetHeight, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR, higherQuality);
      case BILINEAR_STEPPED:
        return getScaledInstanceJava2D(img, targetWidth, targetHeight, RenderingHints.VALUE_INTERPOLATION_BILINEAR, higherQuality);
      case BICUBIC_STEPPED:
        return getScaledInstanceJava2D(img, targetWidth, targetHeight, RenderingHints.VALUE_INTERPOLATION_BICUBIC, higherQuality);
      case NEAREST_NEIGHBOR_STEPPED:
        return getScaledInstanceJava2D(img, targetWidth, targetHeight, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR, higherQuality);
    }
    return null;
  }

  /**
   * Convenience method that returns a scaled instance of the provided
   * {@code BufferedImage}.
   *
   * @param img the original image to be scaled
   * @param targetWidth the desired width of the scaled instance, in pixels
   * @param targetHeight the desired height of the scaled instance, in pixels
   * @param hint one of the rendering hints that corresponds to
   * {@code RenderingHints.KEY_INTERPOLATION} (e.g.
   * {@code RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR},
   * {@code RenderingHints.VALUE_INTERPOLATION_BILINEAR},
   * {@code RenderingHints.VALUE_INTERPOLATION_BICUBIC})
   * @param higherQuality if true, this method will use a multi-step scaling
   * technique that provides higher quality than the usual one-step technique
   * (only useful in downscaling cases, where {@code targetWidth} or
   * {@code targetHeight} is smaller than the original dimensions, and generally
   * only when the {@code BILINEAR} hint is specified)
   * @return a scaled version of the original {@code BufferedImage}
   */
  private static BufferedImage getScaledInstanceJava2D(BufferedImage img, int targetWidth, int targetHeight, Object hint, boolean higherQuality) {

    int type = (img.getTransparency() == Transparency.OPAQUE) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
    BufferedImage ret = (BufferedImage) img;
    int w, h;
    if (higherQuality) {
      // Use multi-step technique: start with original size, then
      // scale down in multiple passes with drawImage()
      // until the target size is reached
      w = img.getWidth();
      h = img.getHeight();
    } else {
      // Use one-step technique: scale directly from original
      // size to target size with a single drawImage() call
      w = targetWidth;
      h = targetHeight;
    }

    do {
      if (higherQuality && w > targetWidth) {
        w /= 2;
        if (w < targetWidth) {
          w = targetWidth;
        }
      }

      if (higherQuality && h > targetHeight) {
        h /= 2;
        if (h < targetHeight) {
          h = targetHeight;
        }
      }

      BufferedImage tmp = new BufferedImage(w, h, type);
      Graphics2D g2 = tmp.createGraphics();
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
      g2.drawImage(ret, 0, 0, w, h, null);
      g2.dispose();

      ret = tmp;
    } while (w > targetWidth || h > targetHeight);

    return ret;
  }

  public static BufferedImage getScaledInstanceJava2D(BufferedImage image, int targetWidth, int targetHeight, Object hint, GraphicsConfiguration gc) {

    // if (gc == null)
    // gc = getDefaultConfiguration();
    int w = image.getWidth();
    int h = image.getHeight();

    int transparency = image.getColorModel().getTransparency();
    // BufferedImage result = gc.createCompatibleImage(w, h, transparency);
    BufferedImage result = new BufferedImage(w, h, transparency);
    Graphics2D g2 = result.createGraphics();
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
    double scalex = (double) targetWidth / image.getWidth();
    double scaley = (double) targetHeight / image.getHeight();
    AffineTransform xform = AffineTransform.getScaleInstance(scalex, scaley);
    g2.drawRenderedImage(image, xform);

    g2.dispose();
    return result;
  }

  public static BufferedImage toBufferedImage(Image img) {
    BufferedImage bufferedImage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_RGB);
    Graphics g = bufferedImage.createGraphics();
    g.drawImage(img, 0, 0, null);
    g.dispose();
    return bufferedImage;
  }

  public static enum ScalingMethod {
    REPLICATE, AREA_AVERAGING, BILINEAR, BICUBIC, NEAREST_NEIGHBOR, BILINEAR_STEPPED, BICUBIC_STEPPED, NEAREST_NEIGHBOR_STEPPED
  }

  public static BufferedImage partOfImage(BufferedImage bufferedImage,
          double xPerctDouble, double yPerctDouble, double widthPerctDouble,
          double heightPerctDouble) {
    int width = bufferedImage.getWidth();
    int height = bufferedImage.getHeight();

    int xoffset = (int) (width * xPerctDouble);
    int yoffset = (int) (height * yPerctDouble);

    int cwidth = (int) (width * widthPerctDouble);
    int cheight = (int) (height * heightPerctDouble);

    BufferedImage subImage = bufferedImage.getSubimage(Math.max(xoffset, 0), Math.max(yoffset, 0), Math.min(cwidth, width - xoffset), Math.min(cheight, height - yoffset));
    return subImage;
  }
}
