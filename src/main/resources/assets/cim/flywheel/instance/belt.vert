// Никаких #include! Flywheel 1.0 сам собирает структуру FlwInstance из нашего LayoutBuilder.

void flw_instanceVertex(in FlwInstance instance) {
    // 1. Позиция (flw_vertexPos уже является vec4, просто умножаем на матрицу pose)
    flw_vertexPos = instance.pose * flw_vertexPos;

    // 2. Нормали (Явно конвертируем нормаль в mat3, а вектор в xyz, чтобы избежать любых конфликтов типов)
    flw_vertexNormal.xyz = mat3(instance.normal) * flw_vertexNormal.xyz;

    // 3. Цвет (Умножаем цвет вершины на цвет из инстанса)
    flw_vertexColor = instance.color * flw_vertexColor;

    // 4. Свет и Оверлей (Явно конвертируем дробный vec2 в целочисленный ivec2)
    flw_vertexLight = ivec2(instance.light);
    flw_vertexOverlay = ivec2(instance.overlay);

    // 5. МАГИЯ АНИМАЦИИ:
    // Масштабируем развертку по длине (uvScale) и сдвигаем (uvScroll)
    // Если текстура в Blockbench наложена по другой оси, просто замени .y на .x или .z
    flw_vertexTexCoord.y = (flw_vertexTexCoord.y * instance.uvScale) - instance.uvScroll;
}