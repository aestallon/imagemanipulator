package hu.aestallon.image.manipulator.complex;

import java.awt.image.BufferedImage;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.function.ToIntFunction;

public class ImageManipulator {
    /** The pixel count of a tile which is processed directly. */
    private static final int MAX_TILE_SIZE = 100;

    private final BufferedImage image;

    /**
     * Creates a new instance with the provided image.
     *
     * @param image a {@code BufferedImage}
     */
    public ImageManipulator(BufferedImage image) {
        this.image = image;
    }

    /**
     * Inverts the colours of the stored image.
     *
     * <p><b>Note:</b> The modification is performed on the image
     * stored in this instance, and no new 'result' image is created!
     */
    public void makeNegative() {
        final ToIntFunction<int[]> negatePixel = onePixelArray -> onePixelArray[0] ^ 0x00FFFFFF;
        manipulateWith(new PixelToPixelManipulationAction(negatePixel));
    }

    /**
     * Creates a simple greyscale version of the stored image.
     *
     * <p><b>Note:</b> The modification is performed on the image
     * stored in this instance, and no new 'result' image is created!
     */
    public void makeGreyScale() {
        final ToIntFunction<int[]> greyPixel = onePixelArray -> {
            int argbValue = onePixelArray[0];
            int a = (argbValue >> 24) & 0xFF;
            int r = (argbValue >> 16) & 0xFF;
            int g = (argbValue >> 8) & 0xFF;
            int b =  argbValue & 0xFF;
            int avg = (r + g + b) / 3;
            return (a << 24) | (avg << 16) | (avg << 8) | avg;
        };

        manipulateWith(new PixelToPixelManipulationAction(greyPixel));
    }

    /**
     * Blurs the stored image.
     *
     * <p>The blur is performed by averaging the colours of the
     * image's pixel in tiles not larger than {@link #MAX_TILE_SIZE}.
     *
     * <p><b>Note:</b> The modification is performed on the image
     * stored in this instance, and no new 'result' image is created!
     */
    public void blur() {
        final ToIntFunction<int[]> blur = tilePixels -> {
            int aSum = 0, rSum = 0, gSum = 0, bSum = 0;
            int pixelCount = tilePixels.length;

            for (int pixel : tilePixels) {
                int a = (pixel >> 24) & 0xFF;
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b =  pixel & 0xFF;
                aSum += a;
                rSum += r;
                gSum += g;
                bSum += b;
            }

            int aResult = aSum / pixelCount;
            int rResult = rSum / pixelCount;
            int gResult = gSum / pixelCount;
            int bResult = bSum / pixelCount;

            return (aResult << 24) | (rResult << 16) | (gResult << 8) | bResult;
        };
        manipulateWith(new TileToPixelManipulationAction(blur));
    }

    private void manipulateWith(ImageManipulationAction action) {
        ForkJoinPool pool = ForkJoinPool.commonPool();
        pool.invoke(action);
    }

    private static abstract class ImageManipulationAction extends RecursiveAction {

        protected final ToIntFunction<int[]> pixelTransformer;
        protected final int xFrom;
        protected final int xTo;
        protected final int yFrom;
        protected final int yTo;

        protected ImageManipulationAction(ToIntFunction<int[]> pixelTransformer, int xFrom, int xTo, int yFrom, int yTo) {
            this.pixelTransformer = pixelTransformer;
            this.xFrom = xFrom;
            this.xTo = xTo;
            this.yFrom = yFrom;
            this.yTo = yTo;
        }

        protected abstract void transformTile();
        protected abstract ImageManipulationAction constructNew(ToIntFunction<int[]> pixelTransformer, int xFrom, int xTo, int yFrom, int yTo);

        @Override
        protected void compute() {
            int actWidth = xTo - xFrom;
            int actHeight = yTo - yFrom;
            int pixelCount = actHeight * actWidth;
            if (pixelCount < MAX_TILE_SIZE) transformTile();
            else invokeAll(
                    constructNew(pixelTransformer, xFrom, xFrom + (actWidth / 2), yFrom, yFrom + (actHeight / 2)),
                    constructNew(pixelTransformer, xFrom, xFrom + (actWidth / 2), yFrom + (actHeight / 2), yTo),
                    constructNew(pixelTransformer, xFrom + actWidth / 2, xTo, yFrom + actHeight / 2, yTo),
                    constructNew(pixelTransformer, xFrom + actWidth / 2, xTo, yFrom, yFrom + actHeight / 2)
            );
        }
    }

    // The pixelTransformer's argument must be an int[1] containing the pixel's
    // old aRGB value as the only entry.
    // This and general implementation instruction would be included in the
    // Javadoc-comment _here_ under the tag "Implementation Note:".
    private final class PixelToPixelManipulationAction extends ImageManipulationAction {
        private PixelToPixelManipulationAction(ToIntFunction<int[]> pixelTransformer) {
            this(pixelTransformer, 0, image.getWidth(), 0, image.getHeight());
        }
        private PixelToPixelManipulationAction(ToIntFunction<int[]> pixelTransformer, int xFrom, int xTo, int yFrom, int yTo) {
            super(pixelTransformer, xFrom, xTo, yFrom, yTo);
        }

        @Override
        protected void transformTile() {
            for (int y = yFrom; y < yTo; y++) {
                for (int x = xFrom; x < xTo; x++) {
                    int argb = image.getRGB(x, y);
                    argb = pixelTransformer.applyAsInt(new int[]{argb});
                    image.setRGB(x, y, argb);
                }
            }
        }

        @Override
        protected ImageManipulationAction constructNew(ToIntFunction<int[]> pixelTransformer, int xFrom, int xTo, int yFrom, int yTo) {
            return new PixelToPixelManipulationAction(pixelTransformer, xFrom, xTo, yFrom, yTo);
        }


    }

    private final class TileToPixelManipulationAction extends ImageManipulationAction {
        private TileToPixelManipulationAction(ToIntFunction<int[]> pixelTransformer) {
            this(pixelTransformer, 0, image.getWidth(), 0, image.getHeight());
        }
        private TileToPixelManipulationAction(ToIntFunction<int[]> pixelTransformer, int xFrom, int xTo, int yFrom, int yTo) {
            super(pixelTransformer, xFrom, xTo, yFrom, yTo);
        }

        @Override
        protected void transformTile() {
            // make an array big enough for every pixel in the tile:
            int[] tile = new int[(yTo - yFrom) * (xTo - xFrom)];
            // fill up the array with the pixels' values:
            for (int y = yFrom; y < yTo; y++) {
                for (int x = xFrom; x < xTo; x++) {
                    // put the integer representing the current pixel's
                    // aRGB value into a unique position in the tile[]:
                    tile[(x - xFrom) + (xTo - xFrom) * (y - yFrom)] = image.getRGB(x, y);
                }
            }
            // get the new aRGB value of the pixels based on the 'strategy':
            int resultArgb = pixelTransformer.applyAsInt(tile);
            // colour all the pixels in the tile:
            for (int y = yFrom; y < yTo; y++) {
                for (int x = xFrom; x < xTo; x++) {
                    image.setRGB(x, y, resultArgb);
                }
            }
        }

        @Override
        protected ImageManipulationAction constructNew(ToIntFunction<int[]> pixelTransformer, int xFrom, int xTo, int yFrom, int yTo) {
            return new TileToPixelManipulationAction(pixelTransformer, xFrom, xTo, yFrom, yTo);
        }
    }
}
