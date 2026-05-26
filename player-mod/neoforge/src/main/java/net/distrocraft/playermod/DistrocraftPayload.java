package net.distrocraft.playermod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

public record DistrocraftPayload(String json) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.tryParse("distrocraft:payload");
    public static final CustomPacketPayload.Type<DistrocraftPayload> TYPE =
            new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<ByteBuf, DistrocraftPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                byte[] bytes = payload.json().getBytes(StandardCharsets.UTF_8);
                buf.writeInt(bytes.length);
                buf.writeBytes(bytes);
            },
            buf -> {
                int len = buf.readInt();
                byte[] bytes = new byte[len];
                buf.readBytes(bytes);
                return new DistrocraftPayload(new String(bytes, StandardCharsets.UTF_8));
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
