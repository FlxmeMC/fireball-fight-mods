package net.weavemc.mods.endstone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides how "glass" endstone should look with the active resource pack.
 *
 * Normally the pack's own glass block model is drawn on the cutout layer. Many
 * PvP packs ship a fully transparent glass texture, and cutout rendering
 * discards those pixels, so endstone would vanish entirely. When the glass
 * texture has too few visible pixels, endstone is instead drawn with its own
 * texture at reduced alpha on the translucent layer ("ghost" mode), which
 * works with any pack.
 */
public final class GlassAppearance {
    /** Alpha applied to ghost endstone vertices (0-255). */
    public static final int GHOST_ALPHA = 110;
    /** Cutout rendering discards alpha below 0.1, which is 25.5 of 255. */
    static final int CUTOUT_ALPHA_THRESHOLD = 26;
    /** Fraction of texture pixels that must survive cutout for glass to count as visible. */
    static final double MIN_VISIBLE_FRACTION = 0.03;

    private static int[] checkedGlassPixels;
    private static boolean checkedGlassVisible = true;
    private static IBakedModel ghostSource;
    private static IBakedModel ghostModel;

    private GlassAppearance() {
    }

    /** True when the active pack's glass texture would still be visible on the cutout layer. */
    public static synchronized boolean isGlassTextureVisible() {
        int[] pixels;
        try {
            IBakedModel glass = EndstoneFeature.getGlassModel();
            TextureAtlasSprite sprite = glass == null ? null : glass.getParticleTexture();
            if (sprite == null || sprite.getFrameCount() <= 0) {
                return true;
            }
            int[][] mipmaps = sprite.getFrameTextureData(0);
            pixels = mipmaps == null || mipmaps.length == 0 ? null : mipmaps[0];
        } catch (RuntimeException | LinkageError ignored) {
            // No client renderer here (unit tests, headless): keep the glass path.
            return true;
        }
        if (pixels == null) {
            return true;
        }
        // Sprites are reloaded in place when packs change, but each reload
        // allocates fresh pixel arrays, so array identity tracks pack changes.
        if (pixels != checkedGlassPixels) {
            checkedGlassPixels = pixels;
            checkedGlassVisible = isVisiblePixels(pixels);
            System.out.println("[Endstone] Glass texture " + (checkedGlassVisible
                    ? "is visible; using the pack's glass model."
                    : "is invisible on the cutout layer; drawing translucent endstone instead."));
        }
        return checkedGlassVisible;
    }

    static boolean isVisiblePixels(int[] argb) {
        if (argb == null || argb.length == 0) {
            return true;
        }
        int visible = 0;
        for (int pixel : argb) {
            if (((pixel >>> 24) & 0xFF) >= CUTOUT_ALPHA_THRESHOLD) {
                visible++;
            }
        }
        return visible >= argb.length * MIN_VISIBLE_FRACTION;
    }

    /** The endstone model with every vertex faded to {@link #GHOST_ALPHA}, or null outside a client. */
    public static synchronized IBakedModel getGhostEndstoneModel() {
        IBakedModel source;
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null || minecraft.getBlockRendererDispatcher() == null) {
                return null;
            }
            source = minecraft.getBlockRendererDispatcher().getBlockModelShapes()
                    .getModelForState(Blocks.end_stone.getDefaultState());
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
        if (source == null) {
            return null;
        }
        if (source != ghostSource) {
            ghostSource = source;
            ghostModel = new GhostModel(source, GHOST_ALPHA);
        }
        return ghostModel;
    }

    static List<BakedQuad> fade(List<BakedQuad> quads, int alpha) {
        List<BakedQuad> faded = new ArrayList<BakedQuad>(quads.size());
        for (BakedQuad quad : quads) {
            faded.add(new BakedQuad(withAlpha(quad.getVertexData(), alpha),
                    quad.getTintIndex(), quad.getFace()));
        }
        return faded;
    }

    /**
     * Rewrites the alpha byte of every vertex colour in block vertex data. The
     * BLOCK vertex format stores colour as four unsigned bytes in native order,
     * so alpha is the high byte on little-endian machines.
     */
    static int[] withAlpha(int[] vertexData, int alpha) {
        int[] copy = vertexData.clone();
        boolean littleEndian = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
        int stride = 7;
        for (int index = 3; index < copy.length; index += stride) {
            copy[index] = littleEndian
                    ? (copy[index] & 0x00FFFFFF) | (alpha << 24)
                    : (copy[index] & 0xFFFFFF00) | (alpha & 0xFF);
        }
        return copy;
    }

    static final class GhostModel implements IBakedModel {
        private final IBakedModel source;
        private final List<BakedQuad> general;
        private final List<List<BakedQuad>> faces;

        GhostModel(IBakedModel source, int alpha) {
            this.source = source;
            this.general = fade(source.getGeneralQuads(), alpha);
            this.faces = new ArrayList<List<BakedQuad>>(EnumFacing.values().length);
            for (EnumFacing facing : EnumFacing.values()) {
                faces.add(fade(source.getFaceQuads(facing), alpha));
            }
        }

        @Override
        public List<BakedQuad> getFaceQuads(EnumFacing facing) {
            return faces.get(facing.ordinal());
        }

        @Override
        public List<BakedQuad> getGeneralQuads() {
            return general;
        }

        @Override
        public boolean isAmbientOcclusion() {
            return source.isAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return source.isGui3d();
        }

        @Override
        public boolean isBuiltInRenderer() {
            return source.isBuiltInRenderer();
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return source.getParticleTexture();
        }

        @Override
        public ItemCameraTransforms getItemCameraTransforms() {
            return source.getItemCameraTransforms();
        }
    }
}
