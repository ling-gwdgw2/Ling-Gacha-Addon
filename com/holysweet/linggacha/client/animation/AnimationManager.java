package com.holysweet.linggacha.client.animation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AnimationManager {

    private static final Map<String, AnimatedTexture> CACHE = new ConcurrentHashMap<>();
    private static final File BG_DIR = new File("config/ling_gacha/backgrounds");
    private static final File ANIM_DIR = new File("config/ling_gacha/animations");

    static {
        if (!BG_DIR.exists()) BG_DIR.mkdirs();
        if (!ANIM_DIR.exists()) ANIM_DIR.mkdirs();
    }

    public static AnimatedTexture getOrLoad(String path, boolean loop) {
        if (path == null || path.trim().isEmpty()) return null;
        String cleanPath = path.trim();
        String key = cleanPath.toLowerCase() + "_" + loop;

        if (CACHE.containsKey(key)) {
            return CACHE.get(key);
        }

        AnimatedTexture anim = loadFromPath(cleanPath, loop);
        if (anim != null) {
            CACHE.put(key, anim);
        }
        return anim;
    }

    private static AnimatedTexture loadFromPath(String path, boolean loop) {
        // 1. Try Disk Files in Config Directories
        File[] candidateFiles = new File[]{
                new File(path),
                new File(BG_DIR, path),
                new File(ANIM_DIR, path)
        };

        for (File f : candidateFiles) {
            if (f.exists() && f.isFile()) {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".gif")) {
                    try (InputStream is = new FileInputStream(f)) {
                        List<GifDecoder.GifFrame> frames = GifDecoder.readGif(is);
                        if (!frames.isEmpty()) {
                            return new AnimatedTexture(f.getName(), frames, loop);
                        }
                    } catch (Exception ignored) {}
                } else if (name.endsWith(".afma")) {
                    AnimatedTexture afma = AfmaAnimation.parseAfmaFile(f.getName(), f, loop);
                    if (afma != null) return afma;
                } else if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".webp")) {
                    try {
                        BufferedImage img = ImageIO.read(f);
                        if (img != null) {
                            ResourceLocation loc = AnimatedTexture.uploadFrame(f.getName(), img);
                            return new AnimatedTexture(f.getName(), List.of(new AnimatedTexture.Frame(loc, 1000)), 1000, loop);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // 2. Try Minecraft Resource Manager (Assets)
        try {
            ResourceLocation rl = ResourceLocation.parse(path);
            Optional<Resource> resOpt = Minecraft.getInstance().getResourceManager().getResource(rl);
            if (resOpt.isPresent()) {
                String pLower = path.toLowerCase();
                if (pLower.endsWith(".gif")) {
                    try (InputStream is = resOpt.get().open()) {
                        List<GifDecoder.GifFrame> frames = GifDecoder.readGif(is);
                        if (!frames.isEmpty()) {
                            return new AnimatedTexture(rl.getPath(), frames, loop);
                        }
                    }
                } else if (pLower.endsWith(".afma")) {
                    try (InputStream is = resOpt.get().open()) {
                        return AfmaAnimation.parseAfma(rl.getPath(), is, loop);
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    public static boolean renderBannerBackground(GuiGraphics guiGraphics, String path, int x, int y, int w, int h) {
        if (path == null || path.trim().isEmpty()) return false;
        String clean = path.trim();

        // Check if it is an animated or disk texture
        AnimatedTexture anim = getOrLoad(clean, true);
        if (anim != null) {
            ResourceLocation frame = anim.getFrameForTime(System.currentTimeMillis());
            if (frame != null) {
                guiGraphics.blit(frame, x, y, 0.0F, 0.0F, w, h, w, h);
                return true;
            }
        }

        // Fallback to standard static ResourceLocation
        try {
            ResourceLocation rl = ResourceLocation.parse(clean);
            guiGraphics.blit(rl, x, y, 0.0F, 0.0F, w, h, w, h);
            return true;
        } catch (Exception ignored) {}

        return false;
    }

    public static AnimatedTexture getPullAnimation(int stars) {
        String[] filesToCheck = switch (stars) {
            case 5 -> new String[]{"pull_5star.gif", "pull_5star.afma", "pull_gold.gif", "pull.gif"};
            case 4 -> new String[]{"pull_4star.gif", "pull_4star.afma", "pull_purple.gif", "pull.gif"};
            default -> new String[]{"pull_3star.gif", "pull_3star.afma", "pull_blue.gif", "pull.gif"};
        };

        for (String file : filesToCheck) {
            AnimatedTexture anim = getOrLoad(file, false);
            if (anim != null) return anim;
        }
        return null;
    }

    public static boolean renderPullAnimation(GuiGraphics guiGraphics, int stars, int x, int y, int w, int h, long elapsedMs) {
        AnimatedTexture anim = getPullAnimation(stars);
        if (anim != null) {
            ResourceLocation frame = anim.getFrameForTime(elapsedMs);
            if (frame != null) {
                guiGraphics.blit(frame, x, y, 0.0F, 0.0F, w, h, w, h);
                return true;
            }
        }
        return false;
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
