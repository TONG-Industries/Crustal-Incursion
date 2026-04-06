package com.cim.client.renderer;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;

public class GrenadierZombieRenderer extends ZombieRenderer {

    public GrenadierZombieRenderer(EntityRendererProvider.Context context) {
        super(context, ModelLayers.ZOMBIE, ModelLayers.ZOMBIE_INNER_ARMOR,
                ModelLayers.ZOMBIE_OUTER_ARMOR);
    }

}