package hu.aestallon.image.manipulator.complex;

import java.awt.image.BufferedImage;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.function.ToIntFunction;

public class ImageManipulator {
    private static final int MIN_PIXEL_COUNT = 100;
    private final BufferedImage image;

    public ImageManipulator(BufferedImage image) {
        this.image = image;
    }

    public void makeNegative() {
        final ToIntFunction<int[]> negatePixel = argb ->
                0xFFFFFF - (argb[0] & 0xFFFFFF) + (argb[0] & 0xFF000000);
        manipulateWith(new PixelToPixelManipulationAction(negatePixel));
    }

    public void makeGreyScaled() {
        final ToIntFunction<int[]> greyPixel = argb -> {
            int argbValue = argb[0];
            int a = (argbValue >> 24) & 0xFF;
            int r = (argbValue >> 16) & 0xFF;
            int g = (argbValue >> 8) & 0xFF;
            int b =  argbValue & 0xFF;
            int avg = (r + g + b) / 3;
            return (a << 24) | (avg << 16) | (avg << 8) | avg;
        };

        manipulateWith(new PixelToPixelManipulationAction(greyPixel));
    }

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
            if (pixelCount < MIN_PIXEL_COUNT) transformTile();
            else invokeAll(
                    constructNew(pixelTransformer, xFrom, xFrom + (actWidth / 2), yFrom, yFrom + (actHeight / 2)),
                    constructNew(pixelTransformer, xFrom, xFrom + (actWidth / 2), yFrom + (actHeight / 2), yTo),
                    constructNew(pixelTransformer, xFrom + actWidth / 2, xTo, yFrom + actHeight / 2, yTo),
                    constructNew(pixelTransformer, xFrom + actWidth / 2, xTo, yFrom, yFrom + actHeight / 2)
            );
        }
    }

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
                    // ezt öröm volt debuggolni :)
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
