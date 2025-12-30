package cgytrus.delightmap.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.*;

//? if <=1.19.2 {
import com.mojang.math.Vector3f;
//? } else {
/*import org.joml.Vector3f;
 *///? }

//? if >1.18.2 {
/*import net.minecraft.world.entity.LivingEntity;
 *///? }

@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    @Shadow
    private boolean updateLightTexture;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private DynamicTexture lightTexture;

    @Shadow
    private float blockLightRedFlicker;

    //? if >1.18.2 {
    /*@Shadow
    protected abstract float getDarknessGamma(float partialTick);

    @Shadow
    protected abstract float calculateDarknessScale(LivingEntity entity, float gamma, float partialTick);
    *///? }

    @Shadow
    @Final
    private GameRenderer renderer;

    @Shadow
    @Final
    private NativeImage lightPixels;

    /**
     * @author ConfiG
     * @reason Completely replaces the vanilla lightmap by design
     */
    @Overwrite
    public void updateLightTexture(float partialTicks) {
        if (!this.updateLightTexture)
            return;

        ClientLevel level = this.minecraft.level;
        LocalPlayer player = this.minecraft.player;
        if (level == null || player == null)
            return;

        this.updateLightTexture = false;

        this.minecraft.getProfiler().push("lightTex");

        float skyDarken = level.getSkyDarken(1.0f);

        float ambientLightFactor = level.dimensionType().ambientLight/*? >1.18.2 >> ';'*//*()*/;
        float skyFactor = level.getSkyFlashTime() > 0 ? 1.0f : skyDarken * 0.95f + 0.05f;
        boolean useBrightLightmap = level.effects().forceBrightLightmap();
        float blockFactor = useBrightLightmap ? 1.4f : (this.blockLightRedFlicker + 1.5f);
        float nightVisionFactor = 0.0f;
        Vector3f skyLightColor = new Vector3f(skyDarken, skyDarken, 1.0f);
        skyLightColor.lerp(new Vector3f(1.0f, 1.0f, 1.0f), 0.35f);
        float darkenWorldFactor = Math.max(0.0f, this.renderer.getDarkenWorldAmount(partialTicks));

        float waterVision = player.getWaterVision();
        if (player.hasEffect(MobEffects.NIGHT_VISION)) {
            nightVisionFactor = GameRenderer.getNightVisionScale(player, partialTicks);
        }
        else if (waterVision > 0.0f && player.hasEffect(MobEffects.CONDUIT_POWER)) {
            nightVisionFactor = waterVision;
        }
        nightVisionFactor = Math.max(0.0f, nightVisionFactor);

        //? if <=1.18.2 {
        float brightnessFactor = Math.max(0.0f, (float)this.minecraft.options.gamma);
        //? } else {
        /*float darknessEffectScale = this.minecraft.options.darknessEffectScale().get().floatValue();
        float scaledDarknessGamma = this.getDarknessGamma(partialTicks) * darknessEffectScale;
        float darknessScale = this.calculateDarknessScale(player, scaledDarknessGamma, partialTicks) * darknessEffectScale;
        float brightnessFactor = Math.max(0.0f, this.minecraft.options.gamma().get().floatValue() - scaledDarknessGamma);
        *///? }

        Vector3f color = new Vector3f();
        Vector3f temp = new Vector3f();
        for(int y = 0; y < 16; ++y) {
            for (int x = 0; x < 16; ++x) {
                float blockLevel = (x + 0.5f) / 16.0f;
                float curvedBlockLevel = blockLevel / (3.0f - 2.0f * blockLevel);
                float blockBrightness = Mth.lerp(ambientLightFactor, Mth.clamp(curvedBlockLevel - 0.05f, 0.0f, 1.0f), 1.0f) * blockFactor;

                float skyLevel = (y + 0.5f) / 16.0f + 0.11f;
                skyLevel -= 0.3f;
                float curvedSkyLevel = (skyLevel * 3.1f) / (10.0f - 9.0f * 1.3f * skyLevel);
                float skyBrightness = Mth.lerp(ambientLightFactor, Mth.clamp(curvedSkyLevel, 0.0f, 1.0f), 1.0f) * skyFactor;

                if (useBrightLightmap) {
                    float adjustedBlockBrightness = Mth.clamp(blockBrightness - 0.2f, 0.05f, 0.7f);
                    color.set(
                        adjustedBlockBrightness * (adjustedBlockBrightness * adjustedBlockBrightness * 0.2f + 0.8f) - 0.03f,
                        adjustedBlockBrightness * (adjustedBlockBrightness * 0.2f + 0.8f),
                        adjustedBlockBrightness
                    );
                }
                else {
                    color.set(
                        blockBrightness,
                        blockBrightness * (blockBrightness * blockBrightness * 0.39f + 0.61f),
                        blockBrightness * (blockBrightness * blockBrightness * 0.88f + 0.12f)
                    );
                }

                if (useBrightLightmap) {
                    color.add(0.4f, 0.4f, 0.4f);
                }
                else {
                    color.add(
                        skyLightColor.x() * skyBrightness * (skyBrightness * skyBrightness * 0.25f + 0.75f),
                        skyLightColor.y() * skyBrightness * (skyBrightness * skyBrightness * 0.2f + 0.8f),
                        skyLightColor.z() * skyBrightness
                    );
                }

                temp.set(color.x() * 0.7f, color.y() * 0.6f, color.z() * 0.6f);
                color.lerp(temp, darkenWorldFactor);

                // adjust for night vision effect
                if (nightVisionFactor > 0.0f) {
                    temp.set(1.0f, 1.0f, 1.0f);
                    color.lerp(temp, nightVisionFactor);
                }
                //? if >1.18.2 {
                /*// adjust for darkness effect
                if (darknessScale > 0.0f) {
                    float limitedDarknessScale = -Math.min(0.9f, darknessScale);
                    color.add(limitedDarknessScale, limitedDarknessScale, limitedDarknessScale);
                    color.set(
                        Mth.clamp(color.x(), 0.0f, 1.0f),
                        Mth.clamp(color.y(), 0.0f, 1.0f),
                        Mth.clamp(color.z(), 0.0f, 1.0f)
                    );
                }
                *///? }

                // adjust for brightness setting
                float brightnessAdjustment;
                if (useBrightLightmap) {
                    brightnessAdjustment = (brightnessFactor * (brightnessFactor / 1.7f) - 0.3f) / 4.0f;
                }
                else {
                    brightnessAdjustment = (brightnessFactor - 0.2f) / 4.0f;
                }
                color.add(brightnessAdjustment, brightnessAdjustment, brightnessAdjustment);

                color.set(
                    Mth.clamp(color.x(), 0.0f, 1.0f),
                    Mth.clamp(color.y(), 0.0f, 1.0f),
                    Mth.clamp(color.z(), 0.0f, 1.0f)
                );
                color.mul(255.0f);
                int r = (int)color.x();
                int g = (int)color.y();
                int b = (int)color.z();
                this.lightPixels.setPixelRGBA(x, y, 255 << 24 | b << 16 | g << 8 | r);
            }
        }

        this.lightPixels.setPixelRGBA(15, 15, 255 << 24 | 255 << 16 | 255 << 8 | 255);

        this.lightTexture.upload();
        this.minecraft.getProfiler().pop();
    }
}
