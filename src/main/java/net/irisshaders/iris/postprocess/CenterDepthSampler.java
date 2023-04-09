package net.irisshaders.iris.postprocess;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.texture.DepthCopyStrategy;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.gl.texture.PixelType;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.apache.commons.io.IOUtils;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL45C;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.IntSupplier;

public class CenterDepthSampler {
	private static final double LN2 = Math.log(2);
	private final Program program;
	private final GlFramebuffer framebuffer;
	private final int texture;
	private final int altTexture;
	private final int lastFrameTime;
	private boolean hasFirstSample;
	private boolean everRetrieved;
	private boolean destroyed;

	public CenterDepthSampler(IntSupplier depthSupplier, float halfLife) {
		this.texture = GlStateManager._genTexture();
		this.altTexture = GlStateManager._genTexture();
		this.framebuffer = new GlFramebuffer();

		InternalTextureFormat format = InternalTextureFormat.R32F;
		setupColorTexture(texture, format);
		setupColorTexture(altTexture, format);
		RenderSystem.bindTexture(0);

		this.framebuffer.addColorAttachment(0, texture);
		ProgramBuilder builder;

		try {
			String fsh = new String(IOUtils.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream("/centerDepth.fsh"))), StandardCharsets.UTF_8);
			String vsh = new String(IOUtils.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream("/centerDepth.vsh"))), StandardCharsets.UTF_8);

			builder = ProgramBuilder.begin("centerDepthSmooth", vsh, null, fsh, ImmutableSet.of(0, 1, 2));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		builder.addDynamicSampler(depthSupplier, "depth");
		builder.addDynamicSampler(() -> altTexture, "altDepth");
		this.program = builder.build();

		GL45C.glUniform1f(GL45C.glGetUniformLocation(program.getProgramId(), "decay"), (float) (1.0f / ((halfLife * 0.1) / LN2)));
		this.lastFrameTime = GL45C.glGetUniformLocation(program.getProgramId(), "lastFrameTime");
	}

	public void sampleCenterDepth() {
		if ((hasFirstSample && (!everRetrieved)) || destroyed) {
			// If the shaderpack isn't reading center depth values, don't bother sampling it
			// This improves performance with most shaderpacks
			return;
		}

		hasFirstSample = true;

		this.framebuffer.bind();
		this.program.use();

		IrisRenderSystem.uniform1f(lastFrameTime, SystemTimeUniforms.TIMER.getLastFrameTime());

		RenderSystem.viewport(0, 0, 1, 1);

		FullScreenQuadRenderer.INSTANCE.render();

		ProgramSamplers.clearActiveSamplers();

		// The API contract of DepthCopyStrategy claims it can only copy depth, however the 2 non-stencil methods used are entirely capable of copying color as of now.
		DepthCopyStrategy.fastest(false).copy(this.framebuffer, texture, null, altTexture, 1, 1);

		//Reset viewport
		Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
	}

	public void setupColorTexture(int texture, InternalTextureFormat format) {
		IrisRenderSystem.texImage2D(texture, GL21C.GL_TEXTURE_2D, 0, format.getGlFormat(), 1, 1, 0, format.getPixelFormat().getGlFormat(), PixelType.FLOAT.getGlFormat(), null);

		IrisRenderSystem.texParameteri(texture, GL21C.GL_TEXTURE_2D, GL21C.GL_TEXTURE_MIN_FILTER, GL21C.GL_LINEAR);
		IrisRenderSystem.texParameteri(texture, GL21C.GL_TEXTURE_2D, GL21C.GL_TEXTURE_MAG_FILTER, GL21C.GL_LINEAR);
		IrisRenderSystem.texParameteri(texture, GL21C.GL_TEXTURE_2D, GL21C.GL_TEXTURE_WRAP_S, GL21C.GL_CLAMP_TO_EDGE);
		IrisRenderSystem.texParameteri(texture, GL21C.GL_TEXTURE_2D, GL21C.GL_TEXTURE_WRAP_T, GL21C.GL_CLAMP_TO_EDGE);
	}

	public int getCenterDepthTexture() {
		return altTexture;
	}

	public void setUsage(boolean usage) {
		everRetrieved |= usage;
	}

	public void destroy() {
		GlStateManager._deleteTexture(texture);
		GlStateManager._deleteTexture(altTexture);
		framebuffer.destroy();
		program.destroy();
		destroyed = true;
	}
}
