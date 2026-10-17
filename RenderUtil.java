package pl.ksawu23.ana;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import java.util.function.Consumer;

/** Rysowanie linii/boxow PRZEZ SCIANY (bez depth testu). Wspolrzedne podawaj relatywnie do kamery. */
public final class RenderUtil {
    public static void draw(Matrix4f m, Consumer<BufferBuilder> body) {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        body.accept(b);
        BuiltBuffer built = b.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public static void line(BufferBuilder b, Matrix4f m, Vec3d a, Vec3d c, int argb) {
        b.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(argb);
        b.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(argb);
    }

    public static void box(BufferBuilder b, Matrix4f m, Box x, int argb) {
        Vec3d[] v = {
            new Vec3d(x.minX, x.minY, x.minZ), new Vec3d(x.maxX, x.minY, x.minZ),
            new Vec3d(x.maxX, x.minY, x.maxZ), new Vec3d(x.minX, x.minY, x.maxZ),
            new Vec3d(x.minX, x.maxY, x.minZ), new Vec3d(x.maxX, x.maxY, x.minZ),
            new Vec3d(x.maxX, x.maxY, x.maxZ), new Vec3d(x.minX, x.maxY, x.maxZ)};
        int[][] e = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
        for (int[] i : e) line(b, m, v[i[0]], v[i[1]], argb);
    }
}
