package com.holysweet.linggacha.client.animation;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class AnimatedTexture {

    public static class Frame {
        private final ResourceLocation location;
        private final int delayMs;

        public Frame(ResourceLocation location, int delayMs) {
            this.location = location;
            this.delayMs = Math.max(20, delayMs);
        }

        public ResourceLocation getLocation() {
            return location;
        }

        public int getDelayMs() {
            return delayMs;
        }
    }

    private final String id;
    private final List<Frame> frames = new ArrayList<>();
    private final int totalDurationMs;
    private final boolean loop;

    public AnimatedTexture(String id, List<GifDecoder.GifFrame> gifFrames, boolean loop) {
        this.id = id;
        this.loop = loop;
        int total = 0;

        for (int i = 0; i < gifFrames.size(); i++) {
            GifDecoder.GifFrame gf = gifFrames.get(i);
            ResourceLocation loc = uploadFrame(id + "_f" + i, gf.image());
            frames.add(new Frame(loc, gf.delayMs()));
            total += gf.delayMs();
        }

        this.totalDurationMs = Math.max(50, total);
    }

    public AnimatedTexture(String id, List<Frame> customFrames, int totalDurationMs, boolean loop) {
        this.id = id;
        this.frames.addAll(customFrames);
        this.totalDurationMs = totalDurationMs;
        this.loop = loop;
    }

    public ResourceLocation getFrameForTime(long elapsedMs) {
        if (frames.isEmpty()) return null;
        if (frames.size() == 1) return frames.get(0).getLocation();

        long time = elapsedMs;
        if (loop) {
            time = elapsedMs % totalDurationMs;
        } else if (time >= totalDurationMs) {
            return frames.get(frames.size() - 1).getLocation();
        }

        long accum = 0;
        for (Frame f : frames) {
            accum += f.getDelayMs();
            if (time < accum) {
                return f.getLocation();
            }
        }
        return frames.get(frames.size() - 1).getLocation();
    }

    public boolean isFinished(long elapsedMs) {
        return !loop && elapsedMs >= totalDurationMs;
    }

    public int getTotalDurationMs() {
        return totalDurationMs;
    }

    public List<Frame> getFrames() {
        return frames;
    }

    public static ResourceLocation uploadFrame(String name, BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        NativeImage nativeImage = new NativeImage(w, h, false);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                nativeImage.setPixelRGBA(x, y, abgr);
            }
        }

        DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("ling_gacha", "anim_" + name.toLowerCase().replaceAll("[^a-z0-9_]", "_"));
        Minecraft.getInstance().getTextureManager().register(loc, dynamicTexture);
        return loc;
    }
}
