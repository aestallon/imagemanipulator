package hu.aestallon.image.manipulator.complex;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Demo {

    public static void main(String[] args) throws IOException {
        BufferedImage catPic = ImageIO.read(new File("resources/inputImages/cat.png"));
        new ImageManipulator(catPic).blur();
        ImageIO.write(catPic, "png", new File("resources/outputImages/cat_blurred.png"));

        BufferedImage dragon = ImageIO.read(new File("resources/inputImages/dragon.jpg"));
        new ImageManipulator(dragon).makeNegative();
        ImageIO.write(dragon, "jpg", new File("resources/outputImages/dragon_negative.jpg"));

        BufferedImage fox = ImageIO.read(new File("resources/inputImages/fox.jpg"));
        new ImageManipulator(fox).makeGreyScaled();
        ImageIO.write(fox, "jpg", new File("resources/outputImages/fox_grey.jpg"));
    }
}
