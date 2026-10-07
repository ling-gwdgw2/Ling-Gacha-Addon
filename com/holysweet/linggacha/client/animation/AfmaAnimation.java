package com.holysweet.linggacha.client.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class AfmaAnimation {

    public static AnimatedTexture parseAfma(String name, InputStream is, boolean loop) {
        try (InputStreamReader reader = new InputStreamReader(is)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            int fps = obj.has("fps") ? Math.max(1, obj.get("fps").getAsInt()) : 24;
            int delayMs = 1000 / fps;
            boolean isLoop = obj.has("loop") ? obj.get("loop").getAsBoolean() : loop;

            if (obj.has("frames")) {
                JsonArray framesArr = obj.getAsJsonArray("frames");
                List<AnimatedTexture.Frame> frames = new ArrayList<>();
                for (int i = 0; i < framesArr.size(); i++) {
                    String frameStr = framesArr.get(i).getAsString();
                    ResourceLocation rl = ResourceLocation.parse(frameStr);
                    frames.add(new AnimatedTexture.Frame(rl, delayMs));
                }
                return new AnimatedTexture(name, frames, frames.size() * delayMs, isLoop);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static AnimatedTexture parseAfmaFile(String name, File file, boolean loop) {
        try {
            String content = Files.readString(file.toPath());
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            int fps = obj.has("fps") ? Math.max(1, obj.get("fps").getAsInt()) : 24;
            int delayMs = 1000 / fps;
            boolean isLoop = obj.has("loop") ? obj.get("loop").getAsBoolean() : loop;

            if (obj.has("frames")) {
                JsonArray framesArr = obj.getAsJsonArray("frames");
                List<AnimatedTexture.Frame> frames = new ArrayList<>();
                for (int i = 0; i < framesArr.size(); i++) {
                    String frameStr = framesArr.get(i).getAsString();
                    File frameFile = new File(file.getParentFile(), frameStr);
                    if (frameFile.exists()) {
                        BufferedImage img = ImageIO.read(frameFile);
                        if (img != null) {
                            ResourceLocation loc = AnimatedTexture.uploadFrame(name + i, img);
                            frames.add(new AnimatedTexture.Frame(loc, delayMs));
                        }
                    } else {
                        ResourceLocation loc = ResourceLocation.parse(frameStr);
                        frames.add(new AnimatedTexture.Frame(loc, delayMs));
                    }
                }
                if (!frames.isEmpty()) {
                    return new AnimatedTexture(name, frames, frames.size() * delayMs, isLoop);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
