package net.weavemc.mods.endstone;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.EnumFacing;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GlassAppearanceTest {
    private static int[] texture(int visiblePixels) {
        int[] pixels = new int[256];
        for (int index = 0; index < visiblePixels; index++) {
            pixels[index] = 0xFF00FF00;
        }
        return pixels;
    }

    @Test
    public void fullyTransparentGlassCountsAsInvisible() {
        Assert.assertFalse(GlassAppearance.isVisiblePixels(texture(0)));
        int[] faint = new int[256];
        Arrays.fill(faint, 0x10FFFFFF);
        Assert.assertFalse("alpha below the cutout threshold never renders",
                GlassAppearance.isVisiblePixels(faint));
    }

    @Test
    public void vanillaStyleGlassBorderCountsAsVisible() {
        Assert.assertTrue(GlassAppearance.isVisiblePixels(texture(60)));
        Assert.assertTrue(GlassAppearance.isVisiblePixels(texture(8)));
    }

    @Test
    public void missingTextureDataDefaultsToVisible() {
        Assert.assertTrue(GlassAppearance.isVisiblePixels(null));
        Assert.assertTrue(GlassAppearance.isVisiblePixels(new int[0]));
    }

    @Test
    public void withAlphaRewritesOnlyTheColourAlphaOfEachVertex() {
        int[] data = new int[28];
        for (int index = 0; index < data.length; index++) {
            data[index] = 0x11223344 + index;
        }
        int[] faded = GlassAppearance.withAlpha(data, 110);
        Assert.assertNotSame(data, faded);
        for (int index = 0; index < data.length; index++) {
            if (index % 7 == 3) {
                if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                    Assert.assertEquals(110, faded[index] >>> 24);
                    Assert.assertEquals(data[index] & 0x00FFFFFF, faded[index] & 0x00FFFFFF);
                } else {
                    Assert.assertEquals(110, faded[index] & 0xFF);
                    Assert.assertEquals(data[index] & 0xFFFFFF00, faded[index] & 0xFFFFFF00);
                }
            } else {
                Assert.assertEquals(data[index], faded[index]);
            }
        }
    }

    @Test
    public void ghostModelFadesEveryQuadAndDelegatesTheRest() {
        int[] vertices = new int[28];
        Arrays.fill(vertices, 0xFFFFFFFF);
        BakedQuad quad = new BakedQuad(vertices, -1, EnumFacing.UP);
        IBakedModel source = mock(IBakedModel.class);
        when(source.getFaceQuads(any(EnumFacing.class))).thenReturn(Collections.<BakedQuad>emptyList());
        when(source.getFaceQuads(EnumFacing.UP)).thenReturn(Collections.singletonList(quad));
        when(source.getGeneralQuads()).thenReturn(Collections.singletonList(quad));
        when(source.isAmbientOcclusion()).thenReturn(true);

        GlassAppearance.GhostModel ghost = new GlassAppearance.GhostModel(source, 110);
        List<BakedQuad> up = ghost.getFaceQuads(EnumFacing.UP);
        Assert.assertEquals(1, up.size());
        Assert.assertEquals(EnumFacing.UP, up.get(0).getFace());
        Assert.assertEquals(-1, up.get(0).getTintIndex());
        Assert.assertNotSame(quad.getVertexData(), up.get(0).getVertexData());
        Assert.assertEquals(0xFFFFFFFF, quad.getVertexData()[3]);
        Assert.assertNotEquals(0xFFFFFFFF, up.get(0).getVertexData()[3]);
        Assert.assertTrue(ghost.getFaceQuads(EnumFacing.DOWN).isEmpty());
        Assert.assertEquals(1, ghost.getGeneralQuads().size());
        Assert.assertTrue(ghost.isAmbientOcclusion());
    }
}
