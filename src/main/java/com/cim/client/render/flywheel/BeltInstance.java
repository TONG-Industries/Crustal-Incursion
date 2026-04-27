package com.cim.client.render.flywheel;

import dev.engine_room.flywheel.api.instance.InstanceHandle;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.instance.InstanceWriter;
import dev.engine_room.flywheel.api.layout.FloatRepr;
import dev.engine_room.flywheel.api.layout.Layout;
import dev.engine_room.flywheel.api.layout.LayoutBuilder; // ИСПРАВЛЕННЫЙ ИМПОРТ
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.SimpleInstanceType;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;

public class BeltInstance extends TransformedInstance {

    public float uvScale = 1.0f;
    public float uvScroll = 0.0f;

    public BeltInstance(InstanceType<? extends BeltInstance> type, InstanceHandle handle) {
        super(type, handle);
    }

    public BeltInstance setUv(float scale, float scroll) {
        this.uvScale = scale;
        this.uvScroll = scroll;
        return this;
    }

    // В Flywheel 1.0 разметка памяти видеокарты пишется вручную.
    // Это точная копия полей TransformedInstance + наши 2 параметра.
    public static final Layout LAYOUT = LayoutBuilder.create()
            .matrix("pose", FloatRepr.FLOAT, 4)                   // Матрица трансформации (4x4)
            .matrix("normal", FloatRepr.FLOAT, 3)                 // Матрица нормалей (3x3)
            .vector("color", FloatRepr.NORMALIZED_UNSIGNED_BYTE, 4) // RGBA цвет
            .vector("light", FloatRepr.UNSIGNED_SHORT, 2)         // Block / Sky light
            .vector("overlay", FloatRepr.SHORT, 2)                // Ванильный Overlay
            .scalar("uvScale", FloatRepr.FLOAT)                   // НАШ МАСШТАБ (Float)
            .scalar("uvScroll", FloatRepr.FLOAT)                  // НАШ СКРОЛЛ (Float)
            .build();

    // Писатель (отправляет данные из Java прямиком в видеопамять)
    public static final InstanceWriter<BeltInstance> WRITER = (ptr, instance) -> {
        // Вызываем стандартный райтер, он запишет первые 5 полей (матрицы, цвет, свет)
        InstanceTypes.TRANSFORMED.writer().write(ptr, instance);

        // Узнаем размер старых данных, чтобы дописать наши в самый конец буфера
        long offset = InstanceTypes.TRANSFORMED.layout().byteSize();

        // Дописываем масштаб и скролл
        MemoryUtil.memPutFloat(ptr + offset, instance.uvScale);
        MemoryUtil.memPutFloat(ptr + offset + 4, instance.uvScroll);
    };

    // Регистрируем наш тип и привязываем шейдер
    public static final InstanceType<BeltInstance> TYPE = SimpleInstanceType.builder(BeltInstance::new)
            .layout(LAYOUT)
            .writer(WRITER)
            // 1. Указываем путь БЕЗ префикса flywheel/, так как движок добавит его сам
            .vertexShader(new ResourceLocation("cim", "instance/belt.vert"))

            // 2. Исправляем путь к стандартному шейдеру отсечения
            // В Flywheel 1.0 он обычно лежит по этому адресу:
            .cullShader(new ResourceLocation("flywheel", "instance/cull/transformed.glsl"))
            .build();
}