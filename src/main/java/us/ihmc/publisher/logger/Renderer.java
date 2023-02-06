package us.ihmc.publisher.logger;

import javafx.scene.paint.Paint;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadProbeInfo;
import org.freedesktop.gstreamer.PadProbeReturn;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.IntBuffer;

public class Renderer implements Pad.PROBE
{
    private final BufferedImage image;
    private final int[] data;
    private final Point[] points;
    private final GradientPaint fill;

    private Renderer(int width, int height)
    {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        data = ((DataBufferInt) (image.getRaster().getDataBuffer())).getData();
        points = new Point[18];
        for (int i = 0; i < points.length; i++)
        {
            points[i] = new Point();
        }
        fill = new GradientPaint(0, 0, new Color(1.0f, 0.3f, 0.5f, 0.9f),
                60, 20, new Color(0.3f, 1.0f, 0.7f, 0.8f), true);
    }

    @Override
    public PadProbeReturn probeCallback(Pad pad, PadProbeInfo info)
    {
        Buffer buffer = info.getBuffer();
        if (buffer.isWritable())
        {
            IntBuffer ib = buffer.map(true).asIntBuffer();
            ib.get(data);
            render();
            ib.rewind();
            ib.put(data);
            buffer.unmap();
        }
        return PadProbeReturn.OK;
    }

    private void render()
    {
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        for (Point point : points) {
            point.tick();
        }
        GeneralPath path = new GeneralPath();
        path.moveTo(points[0].x, points[0].y);
        for (int i = 2; i < points.length; i += 2) {
            path.quadTo(points[i - 1].x, points[i - 1].y,
                    points[i].x, points[i].y);
        }
        path.closePath();
        path.transform(AffineTransform.getScaleInstance(image.getWidth(), image.getHeight()));
        g2d.setPaint(fill);
        g2d.fill(path);
        g2d.setColor(Color.BLACK);
        g2d.draw(path);
    }

    private class Point
    {

        private double x, y, dx, dy;

        public Point()
        {
            this.x = Math.random();
            this.y = Math.random();
            this.dx = 0.02 * Math.random();
            this.dy = 0.02 * Math.random();
        }

        private void tick()
        {
            x += dx;
            y += dy;
            if (x < 0 || x > 1)
            {
                dx = -dx;
            }
            if (y < 0 || y > 1)
            {
                dy = -dy;
            }
        }
    }
}
