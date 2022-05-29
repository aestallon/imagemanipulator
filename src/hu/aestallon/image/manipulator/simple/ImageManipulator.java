package hu.aestallon.image.manipulator.simple;

import java.awt.image.BufferedImage;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.function.IntUnaryOperator;

public class ImageManipulator {
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
        final IntUnaryOperator invertRgb = argb ->
                0xFFFFFF - (argb & 0xFFFFFF) + (argb & 0xFF000000);
        manipulateWith(invertRgb);
    }

    /**
     * Creates a simple greyscale version of the stored image.
     *
     * <p><b>Note:</b> The modification is performed on the image
     * stored in this instance, and no new 'result' image is created!
     */
    public void makeGreyScaled() {
        final IntUnaryOperator greyPixel = argb -> {
            int a = (argb >> 24) & 0xFF;
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            int avg = (r + g + b) / 3;
            return (a << 24) | (avg << 16) | (avg << 8) | avg;
        };

        manipulateWith(greyPixel);
    }

    private void manipulateWith(IntUnaryOperator pixelTransformer) {
        ForkJoinPool pool = ForkJoinPool.commonPool();
        ImageManipulationAction action = new ImageManipulationAction(pixelTransformer);
        pool.invoke(action);
    }

    private class ImageManipulationAction extends RecursiveAction {
        private final IntUnaryOperator pixelTransformer;
        private final int xFrom;
        private final int xTo;
        private final int yFrom;
        private final int yTo;

        private ImageManipulationAction(IntUnaryOperator pixelTransformer) {
            this(pixelTransformer, 0, image.getWidth(), 0, image.getHeight());
        }

        private ImageManipulationAction(IntUnaryOperator pixelTransformer, int xFrom, int xTo, int yFrom, int yTo) {
            this.pixelTransformer = pixelTransformer;
            this.xFrom = xFrom;
            this.xTo = xTo;
            this.yFrom = yFrom;
            this.yTo = yTo;
        }

        private void transformTile() {
            for (int y = yFrom; y < yTo; y++) {
                for (int x = xFrom; x < xTo; x++) {
                    int argb = image.getRGB(x, y);
                    argb = pixelTransformer.applyAsInt(argb);
                    image.setRGB(x, y, argb);
                }
            }
        }

        @Override
        protected void compute() {
            int actWidth = xTo - xFrom;
            int actHeight = yTo - yFrom;
            int tileSize = actHeight * actWidth;
            if (tileSize < MAX_TILE_SIZE) transformTile();
            else invokeAll(
                    new ImageManipulationAction(pixelTransformer, xFrom, xFrom + (actWidth / 2), yFrom, yFrom + (actHeight / 2)),
                    new ImageManipulationAction(pixelTransformer, xFrom, xFrom + (actWidth / 2), yFrom + (actHeight / 2), yTo),
                    new ImageManipulationAction(pixelTransformer, xFrom + actWidth / 2, xTo, yFrom + actHeight / 2, yTo),
                    new ImageManipulationAction(pixelTransformer, xFrom + actWidth / 2, xTo, yFrom, yFrom + actHeight / 2)
            );
        }
    }
}
