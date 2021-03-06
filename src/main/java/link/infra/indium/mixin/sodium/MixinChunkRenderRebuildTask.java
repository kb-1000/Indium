package link.infra.indium.mixin.sodium;

import link.infra.indium.Indigo;
import link.infra.indium.renderer.render.IndiumTerrainRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderBuildTask;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderRebuildTask;
import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderContext;
import me.jellysquid.mods.sodium.client.util.task.CancellationSource;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The main injection point into Sodium - here we stop Sodium from rendering FRAPI block models, and do it ourselves
 */
@Mixin(ChunkRenderRebuildTask.class)
public abstract class MixinChunkRenderRebuildTask<T extends ChunkGraphicsState> extends ChunkRenderBuildTask<T> {
	@Shadow(remap = false) @Final private WorldSlice slice;

	// Store a rendering context per rebuild task
	private final IndiumTerrainRenderContext indiumContext = new IndiumTerrainRenderContext();

	@Inject(at = @At("HEAD"), method = "performBuild", remap = false)
	public void beforePerformBuild(ChunkRenderContext pipeline, ChunkBuildBuffers buffers, CancellationSource cancellationSource, CallbackInfoReturnable<ChunkBuildResult<T>> cir) {
		// Set up our rendering context
		indiumContext.prepare(slice, buffers);
	}

	@Inject(at = @At("RETURN"), method = "performBuild", remap = false)
	public void afterPerformBuild(ChunkRenderContext pipeline, ChunkBuildBuffers buffers, CancellationSource cancellationSource, CallbackInfoReturnable<ChunkBuildResult<T>> cir) {
		// Tear down our rendering context
		indiumContext.release();
	}

	// Can't specify the arguments here, as the arguments wouldn't get remapped
	// and remap = true fails as it tries to find a mapping for renderBlock
	// so I just let MinecraftDev yell at me here
	@Redirect(method = "performBuild", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/pipeline/context/ChunkRenderContext;renderBlock"), remap = false)
	public boolean onRenderBlock(ChunkRenderContext pipeline, BlockRenderView world, BlockState state, BlockPos pos, ChunkModelBuffers buffers, boolean cull) {
		// We need to get the model with a bit more context than ChunkRenderContext has, so we do it here

		// This should probably be refactored to only get the model once: Either pass the Indium rendering
		// context through to ChunkRenderContext, or move the getModel call from ChunkRenderContext and pass it
		BakedModel model = ((AccessorChunkRenderContext)pipeline).getModels().getModel(state);
		if (!Indigo.ALWAYS_TESSELATE_INDIGO && ((FabricBakedModel) model).isVanillaAdapter()) {
			return pipeline.renderBlock(world, state, pos, buffers, cull);
		} else {
			// TODO: replace MatrixStack with just a Vec3d
			MatrixStack stack = new MatrixStack();
			Vec3d offset = state.getModelOffset(world, pos);
			stack.translate(offset.x, offset.y, offset.z);
			indiumContext.tesselateBlock(state, pos, model, stack);
			// TODO: determine if a block was actually rendered
			return true;
		}
	}
}
