package com.cim.client.loader;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import com.cim.client.model.ProceduralWireGeometry;

public class ProceduralWireLoader implements IGeometryLoader<ProceduralWireGeometry> {

    @Override
    public ProceduralWireGeometry read(JsonObject jsonObject, JsonDeserializationContext context) {
        return new ProceduralWireGeometry();
    }
}
