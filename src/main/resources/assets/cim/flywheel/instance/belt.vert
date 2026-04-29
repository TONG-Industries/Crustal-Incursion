#include "flywheel:util/matrix.glsl"

void flw_instanceVertex(in FlwInstance i) {
    // Save original local Z before pose transformation (0.0 to 1.0)
    float localZ = flw_vertexPos.z;

    flw_vertexPos = i.pose * flw_vertexPos;
    flw_vertexNormal = mat3(transpose(inverse(i.pose))) * flw_vertexNormal;
    flw_vertexColor *= i.color;
    flw_vertexOverlay = i.overlay;
    // Some drivers have a bug where uint over float division is invalid, so use an explicit cast.
    flw_vertexLight = max(vec2(i.light) / 256.0, flw_vertexLight);

    // UV scroll animation and segment 1:1 tiling for belt texture
    flw_vertexTexCoord.y += i.scrollOffset + localZ * i.scrollMult;
}
