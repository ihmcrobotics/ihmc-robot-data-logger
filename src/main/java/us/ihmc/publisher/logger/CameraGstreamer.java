package us.ihmc.publisher.logger;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.awt.image.SampleModel;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JFrame;

import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.elements.BaseSink;
import org.freedesktop.gstreamer.lowlevel.GstVideoOverlayAPI;

/**
 *
 * @author Neil C Smith (http://neilcsmith.net)
 */
public class CameraGstreamer {

    /**
     * @param args the command line arguments
     */

    private static Sample testing;
    private static Pipeline pipe;
    private static BufferedImage recording = null;
    static File test = new File("C:/Users/nkitchel/test3.mp4");

    public static void main(String[] args) {

        Gst.init("CameraTest", args);
        EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
                SimpleVideoComponent vc = new SimpleVideoComponent();
                Bin bin = Gst.parseBinFromDescription(
                        "autovideosrc ! videoconvert ! capsfilter caps=video/x-raw,width=640,height=480",
                        true);
                pipe = new Pipeline();
//                GstVideoFrame
//                bin.getElements();
                pipe.addMany(bin, vc.getElement());
                Pipeline.linkMany(bin, vc.getElement());
                testing = vc.getAppsinkFrame();

                BufferedImage whatthe;

                Buffer whatreally = testing.getBuffer();
//                whatreally.

                JFrame f = new JFrame("Camera Test");
                f.add(vc);
                vc.setPreferredSize(new Dimension(640, 480));
                f.pack();
                f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                pipe.play();
                f.setVisible(true);
            }
        });
    }

//    public static void main(String[] args) {
//
//        Gst.init("CameraTest", args);
//        EventQueue.invokeLater(new Runnable() {
//
//            @Override
//            public void run() {
//                SimpleVideoComponent vc = new SimpleVideoComponent();
//                Bin bin = Gst.parseBinFromDescription(
//                        "ksvideosrc " +
//                                "device-path=/////?//usb/#vid_12d1/&pid_4321/&mi_00/#6/&34bbb27/&0/&0000/#/{6994ad05-93ef-11d0-a3cc-00a0c9223196/}//global " +
//                                "! video/x-raw,width=640,height=480 " +
//                                "! autovideosink",
//                        true);
//                pipe = new Pipeline();
//                pipe.addMany(bin, vc.getElement());
//                Pipeline.linkMany(bin, vc.getElement());
//                BaseSink filesink = (BaseSink) pipe.getElementByName("filesink");
//                filesink.set("location", "C:/whatThe.mp4");// "capture" + System.currentTimeMillis() + ".mp4");
//
//                JFrame f = new JFrame("Camera Test");
//                f.add(vc);
//                vc.setPreferredSize(new Dimension(640, 480));
//                f.pack();
//                f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//
//                pipe.play();
//                recording = vc;
//
//
//                f.setVisible(true);
//            }
//        });
//    }
}