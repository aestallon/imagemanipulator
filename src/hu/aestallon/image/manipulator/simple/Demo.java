package hu.aestallon.image.manipulator.simple;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Demo {

    public static void main(String[] args) throws IOException {
        BufferedImage catPic = ImageIO.read(new File("resources/inputImages/cat.png"));
        new ImageManipulator(catPic).makeNegative();
        ImageIO.write(catPic, "png", new File("resources/outputImages/cat_negative.png"));
    }
}
