package cz.incad.arup.searchapp.imaging;

import cz.incad.arup.searchapp.Options;
import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Date;
import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.json.JSONException;

/**
 *
 * @author alberto
 */
public class ImageSupport {

    public static final Logger LOGGER = Logger.getLogger(ImageSupport.class.getName());
    public static int generated;

    public static void initCount() {
        ImageSupport.generated = 0;
    }

    public static void thumbs(String dir) {
        File root = new File(dir);
        File[] list = root.listFiles();

        if (list == null) {
            return;
        }

        for (File f : list) {
            if (f.isDirectory()) {
                thumbs(f.getAbsolutePath());
            } else {
                thumbnail(f.getAbsolutePath());
            }
        }
    }

    public static void pdfs(String dir) {
        File root = new File(dir);
        File[] list = root.listFiles();

        if (list == null) {
            return;
        }

        for (File f : list) {
            if (f.isDirectory()) {
                pdfs(f.getAbsolutePath());
            } else {
                String filename = f.getName();
                String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
                if ("pdf".equals(ext)) {
                    mediumPdf(f, f.getName());
                }
            }
        }
    }

    public static String thumbnail(String filename) {
        return thumbnail(filename, filename);
    }

    public static String thumbnail(String filename, String id) {
        File f = new File(filename);
        if (!f.exists()) {
            LOGGER.log(Level.WARNING, "File {0} doesn't exists", f);
            return null;
        }
        String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        if ("pdf".equals(ext)) {
            return thumbnailPdfPage(f, 0, id);
        } else {
            return thumbnailImg(f, id);
        }
    }

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

    public static String thumbnailImg(File f, String id) {

        String outputFile = getDestDir(id) + id;
        try {

            BufferedImage sourceImage = ImageIO.read(f);

            if (sourceImage == null) {

                LOGGER.log(Level.WARNING, "Cannot read image");
                return "Cannot read image";
            }
            Options opts = Options.getInstance();
            int t_width = opts.getInt("thumbWidth", 100);
            int t_height = opts.getInt("thumbHeight", 100);

            int width = sourceImage.getWidth();
            int height = sourceImage.getHeight();
            int pixels = width * height;
            if (pixels > Options.getInstance().getInt("maxPixels", 2000 * 2000)) {
                writeSkipped(-1, outputFile, width + " x " + height);
                return "Image too big";
            }
            makeDestDir(id);
            resize(outputFile + "_thumb.jpg", sourceImage, t_width, t_height);

//      t_width = opts.getInt("mediumHeight", 800);
//      t_height = opts.getInt("mediumHeight", 800);
//      
//      return resize(outputFile + "_medium.jpg", sourceImage, t_width, t_height );
            int max = opts.getInt("mediumHeight", 1000);

//      int width = sourceImage.getWidth();
//      int height = sourceImage.getHeight();
//            
//      double ratio = h * 1.0 / height;
//      int w = (int) Math.max(1, Math.round(width * ratio));
            int w;
            int h;
            if (height > width) {
                double ratio = max * 1.0 / height;
                w = (int) Math.max(1, Math.round(width * ratio));
                h = max;
            } else {
                double ratio = max * 1.0 / width;
                h = (int) Math.max(1, Math.round(height * ratio));
                w = max;
            }

            BufferedImage img2 = scale(sourceImage, w, h);

            ImageIO.write(img2, "jpg", new File(outputFile + "_medium.jpg"));
            sourceImage.flush();
            sourceImage = null;
            img2.flush();
            img2 = null;

            ImageSupport.generated++;
            return outputFile;

        } catch (Exception ex) {

            LOGGER.log(Level.SEVERE, "Error creating thumb {0}, ", outputFile);
            LOGGER.log(Level.SEVERE, null, ex);
            return null;
        }

    }

    public static String testPath2(String filename) {
        return getDestDir(filename);
    }

    public static boolean thumbExists(String f) {
        String dest = getDestDir(f) + f + ".jpg";
        return (new File(dest)).exists();
    }

    public static boolean folderExists(String f) {
        String dest = getDestDir(f);
        return (new File(dest)).exists();
    }

    public static String getDestDir(String f) {
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

    public static String thumbnailPdfPage(File f, int page, String id) {

        try {
            Options opts = Options.getInstance();
            int t_width = opts.getInt("thumbWidth", 100);
            int t_height = opts.getInt("thumbHeight", 100);

            String filename = f.getName();
            String outputFile = getDestDir(id) + id + "_thumb.jpg";

            BufferedImage sourceImage = readImage(f, ImageMimeType.PDF, page);

            if (sourceImage == null) {

                LOGGER.log(Level.WARNING, "Cannot read image for page {0} in file {1}", new Object[]{(page + 1), id});
                return "Cannot read image";
            }

            int width = sourceImage.getWidth();
            int height = sourceImage.getHeight();

            int pixels = width * height;
            if (pixels > Options.getInstance().getInt("maxPixels", 2000 * 2000)) {
                writeSkipped(page, id, width + " x " + height);
                return "Image too big";
            }

            if (width > height) {
                float extraSize = height - t_height;
                float percentHight = (extraSize / height) * 100;
                float percentWidth = width - ((width / t_width) * percentHight);
                BufferedImage img = new BufferedImage((int) percentWidth, t_height, BufferedImage.TYPE_INT_RGB);
                Image scaledImage = sourceImage.getScaledInstance((int) percentWidth, t_height, Image.SCALE_SMOOTH);
                img.createGraphics().drawImage(scaledImage, 0, 0, null);
                BufferedImage img2 = img.getSubimage((int) ((percentWidth - 100) / 2), 0, t_width, t_height);

                makeDestDir(id);
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
                BufferedImage img2 = img.getSubimage(0, (int) ((percentHight - 100) / 2), t_width, t_height);

                makeDestDir(id);
                ImageIO.write(img2, "jpg", new File(outputFile));
                img.flush();
                img = null;
                img2.flush();
                img2 = null;
            }

            sourceImage.flush();
            sourceImage = null;
            ImageSupport.generated++;
            return outputFile;
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            return null;
        }

    }

    public static ArrayList<String> mediumPdf(File f, String id) {
        LOGGER.log(Level.INFO, "Generating medium thumbs for pdf {0}", f.getName());
        ArrayList<String> ret = new ArrayList<>();

        try {
            Options opts = Options.getInstance();
            int max = opts.getInt("mediumHeight", 1000);

            int maxPixels = Options.getInstance().getInt("maxPixels", 2000 * 2000);

            int pageCounter = 0;
            try (PDDocument document = PDDocument.load(f)) {
                PDFRenderer pdfRenderer = new PDFRenderer(document);
                String destDir = makeDestDir(id) + id + File.separator;
                new File(destDir).mkdir();

                for (PDPage page : document.getPages()) {
                    // note that the page number parameter is zero based

                    LOGGER.log(Level.FINE, "generating page {0}", pageCounter);
                    BufferedImage bim = pdfRenderer.renderImageWithDPI(pageCounter, 160, ImageType.RGB);

                    int width = bim.getWidth();
                    int height = bim.getHeight();

                    if (width * height < maxPixels) {
//          if (PDFAcceptor.acceptPage(page)) {
                        int w;
                        int h;
                        if (height > width) {
                            double ratio = max * 1.0 / height;
                            w = (int) Math.max(1, Math.round(width * ratio));
                            h = max;
                        } else {
                            double ratio = max * 1.0 / width;
                            h = (int) Math.max(1, Math.round(height * ratio));
                            w = max;
                        }

                        BufferedImage img2 = scale(bim, w, h);

                        String outputFile = destDir + (pageCounter++) + ".jpg";
                        ImageIO.write(img2, "jpg", new File(outputFile));
                        bim.flush();
                        bim = null;
                        img2.flush();
                        img2 = null;
                        ret.add(outputFile);
                        ImageSupport.generated++;

                    } else {
                        pageCounter++;
                        writeSkipped(pageCounter, id, width + " x " + height);
                    }
                }

                LOGGER.log(Level.INFO, "Processed {0} pages", pageCounter);
                return ret;
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
                writeSkipped(pageCounter, id, ex.toString());
                ret.add(f.getName() + " has error: " + ex.toString());
                return ret;
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
                writeSkipped(pageCounter, id, ex.toString());
                ret.add(f.getName() + " has error: " + ex.toString());
                return ret;
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.add(f.getName() + " has error: " + ex.toString());
            return ret;
        }
    }

    private static void writeSkipped(int page, String id, String size) {
        String d = new Date().toString();
        try {
            if (page == -1) {
                //Image
                LOGGER.log(Level.WARNING, "skipping image {0} with size {1}", new Object[]{id, size});
                File file = new File(Options.getInstance().getString("thumbsDir") + File.separator + "skipped.txt");
                FileUtils.writeStringToFile(file, d + ".- Image in " + id + " Size: " + size + System.getProperty("line.separator"), true);
            } else {
                //pdf page
                LOGGER.log(Level.WARNING, "skipping page {0} in file {1}. Image size {2}", new Object[]{page, id, size});
                File file = new File(Options.getInstance().getString("thumbsDir") + File.separator + "skipped.txt");
                FileUtils.writeStringToFile(file, d + ".- page " + page + " in " + id + " Image size: " + size + System.getProperty("line.separator"), true);
            }
        } catch (IOException | JSONException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    public static boolean useCache() {
        return true;
    }

    public static BufferedImage readImage(File f, ImageMimeType type, int page) throws IOException {
        LOGGER.fine("type is " + type);
        if (type == null || type.javaNativeSupport()) {
            ImageIO.setUseCache(useCache());
            return ImageIO.read(f);
        } else if (type.equals(ImageMimeType.PDF)) {
            PDDocument document = null;
            try {

                document = PDDocument.load(f);
                if (PDFAcceptor.acceptPage(document.getPage(page))) {
                    PDFRenderer pdfRenderer = new PDFRenderer(document);
                    int resolution = 96;
                    //int resolution = 160;
                    BufferedImage image = pdfRenderer.renderImageWithDPI(page, resolution, ImageType.RGB);

                    return image;
                } else {
                    return null;
                }
            } finally {
                if (document != null) {
                    document.close();
                }
            }
        } else {
            throw new IllegalArgumentException("unsupported mimetype '" + type.getValue() + "'");
        }
    }

    public static BufferedImage scale(BufferedImage img, int targetWidth, int targetHeight) {
        ScalingMethod method = ScalingMethod.valueOf("BICUBIC_STEPPED");
        boolean higherQuality = true;
        return scale(img, targetWidth, targetHeight, method, higherQuality);
    }

    public static BufferedImage scale(BufferedImage img, int targetWidth, int targetHeight, ScalingMethod method, boolean higherQuality) {
        // System.out.println("SCALE:"+method+" width:"+targetWidth+" height:"+targetHeight);
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
     * {@code targetHeight} is smaller than the original dimensions, and
     * generally only when the {@code BILINEAR} hint is specified)
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

    public static void addWatermark(BufferedImage sourceImage, BufferedImage mark, float alpha) {
        //try {
        Graphics2D g2d = (Graphics2D) sourceImage.getGraphics();

        // initializes necessary graphic properties
        AlphaComposite alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha);
        g2d.setComposite(alphaChannel);
//        g2d.setColor(Color.BLUE);
// 
        // calculates the coordinates where the String is painted

//        int cols = sourceImage.getWidth() / (mark.getWidth() + 20);
//        int rows = (sourceImage.getHeight() / (mark.getHeight() + 20 ));
//        for(int i =0; i<cols; i++){
//          for(int j = 0; j<rows; j++){
//            g2d.drawImage(mark, (mark.getWidth() + 20)*i, (mark.getHeight()+20)*j, null);
//          }
//        }
        int centerX = (sourceImage.getWidth() - (int) mark.getWidth()) / 2;
        int centerY = (sourceImage.getHeight() - (int) mark.getHeight()) / 2;
        g2d.drawImage(mark, centerX, centerY, null);

        //ImageIO.write(sourceImage, "png", destImageFile);
        g2d.dispose();

//    } catch (IOException ex) {
//        System.err.println(ex);
//    }
    }

    public static void addTextWatermark(BufferedImage sourceImage) {
        //try {
        Graphics2D g2d = (Graphics2D) sourceImage.getGraphics();

        // initializes necessary graphic properties
        AlphaComposite alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.1f);
        g2d.setComposite(alphaChannel);
        g2d.setColor(Color.BLUE);
        g2d.setFont(new Font("Arial", Font.BOLD, 64));
        FontMetrics fontMetrics = g2d.getFontMetrics();
        Rectangle2D rect = fontMetrics.getStringBounds("AMCR TEXTEXTEXT", g2d);

        // calculates the coordinate where the String is painted
        int centerX = (sourceImage.getWidth() - (int) rect.getWidth()) / 2;
        int centerY = sourceImage.getHeight() / 2;

        // paints the textual watermark
        g2d.drawString("AMCR TEXTEXTEXT", centerX, centerY);

        //ImageIO.write(sourceImage, "png", destImageFile);
        g2d.dispose();

        System.out.println("The tex watermark is added to the image.");

//    } catch (IOException ex) {
//        System.err.println(ex);
//    }
    }

}