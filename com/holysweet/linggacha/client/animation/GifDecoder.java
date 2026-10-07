package com.holysweet.linggacha.client.animation;

import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GifDecoder {

    public record GifFrame(BufferedImage image, int delayMs) {}

    public static List<GifFrame> readGif(InputStream is) {
        List<GifFrame> frames = new ArrayList<>();
        try {
            ImageInputStream stream = ImageIO.createImageInputStream(is);
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                return frames;
            }

            ImageReader reader = readers.next();
            reader.setInput(stream, false);

            int numImages = reader.getNumImages(true);
            for (int i = 0; i < numImages; i++) {
                BufferedImage image = reader.read(i);
                int delayMs = 100;
                try {
                    IIOMetadata metadata = reader.getImageMetadata(i);
                    String formatName = metadata.getNativeMetadataFormatName();
                    if (formatName != null) {
                        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(formatName);
                        IIOMetadataNode gce = getNode(root, "GraphicControlExtension");
                        if (gce != null) {
                            String delayStr = gce.getAttribute("delayTime");
                            if (delayStr != null && !delayStr.isEmpty()) {
                                int delay = Integer.parseInt(delayStr);
                                if (delay > 0) {
                                    delayMs = delay * 10;
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}

                frames.add(new GifFrame(image, delayMs));
            }

            reader.dispose();
            stream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return frames;
    }

    private static IIOMetadataNode getNode(IIOMetadataNode root, String nodeName) {
        int length = root.getLength();
        for (int i = 0; i < length; i++) {
            Node item = root.item(i);
            if (item.getNodeName().compareToIgnoreCase(nodeName) == 0) {
                return (IIOMetadataNode) item;
            }
        }
        return null;
    }
}
