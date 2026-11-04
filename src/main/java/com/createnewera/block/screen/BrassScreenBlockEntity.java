package com.createnewera.block.screen;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Brass Screen. The top-left screen of a pane (ScreenPane) holds the whole pane's pixels;
 * the others only point at it. Programs draw into the pixels on the server; once a tick, if
 * anything changed, they go to the players nearby, compressed, and are saved with the world.
 */
public class BrassScreenBlockEntity extends BlockEntity {
    /** The pane's pixels, 0xRRGGBB, or null before anything has been drawn */
    private int @Nullable [] pixels;
    private int width, height;
    private boolean dirty;
    /** The last touch: where, by whom, and a number that grows with each, for onTouch */
    private int touchX, touchY;
    private String touchedBy = "";
    private long touches;

    /** Client only: bumped when new pixels arrive, so the renderer knows to upload them */
    public int version;

    public BrassScreenBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The pixels to draw into, sized to the pane now, kept as they were where the pane still covers them. */
    public ScreenCanvas canvas(ScreenPane pane) {
        int w = pane.width(), h = pane.height();
        if (pixels == null || width != w || height != h) {
            int[] resized = new int[w * h];
            if (pixels != null)
                for (int y = 0; y < Math.min(h, height); y++)
                    System.arraycopy(pixels, y * width, resized, y * w, Math.min(w, width));
            pixels = resized;
            width = w;
            height = h;
            changed();
        }
        return new ScreenCanvas(pixels, width, height);
    }

    public @Nullable ScreenCanvas shown() {
        return pixels == null ? null : new ScreenCanvas(pixels, width, height);
    }

    /** Something was drawn: send it at the end of the tick. */
    public void changed() {
        dirty = true;
    }

    public void touched(int x, int y, String player) {
        touchX = x;
        touchY = y;
        touchedBy = player;
        touches++;
        setChanged();
    }

    public int touchX() {
        return touchX;
    }

    public int touchY() {
        return touchY;
    }

    public String touchedBy() {
        return touchedBy;
    }

    public long touches() {
        return touches;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BrassScreenBlockEntity screen) {
        if (!screen.dirty)
            return;
        screen.dirty = false;
        screen.setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (pixels != null) {
            tag.putInt("Width", width);
            tag.putInt("Height", height);
            tag.putByteArray("Pixels", pack(pixels));
        }
        if (touches > 0) {
            tag.putLong("Touches", touches);
            tag.putInt("TouchX", touchX);
            tag.putInt("TouchY", touchY);
            tag.putString("TouchedBy", touchedBy);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Pixels")) {
            int w = tag.getInt("Width"), h = tag.getInt("Height");
            int[] read = unpack(tag.getByteArray("Pixels"), w * h);
            if (read != null) {
                pixels = read;
                width = w;
                height = h;
            }
        } else {
            pixels = null;
        }
        touches = tag.getLong("Touches");
        touchX = tag.getInt("TouchX");
        touchY = tag.getInt("TouchY");
        touchedBy = tag.getString("TouchedBy");
        version++;
    }

    /** Client: lets go of the texture a pane drew with when its top-left screen goes (BrassScreenRenderer plugs it in). */
    public static java.util.function.Consumer<BlockPos> clientRemoved = pos -> {};

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide)
            clientRemoved.accept(worldPosition);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Three bytes a pixel, deflated: a drawing is mostly runs of the same colour, so it packs small. */
    private static byte[] pack(int[] pixels) {
        byte[] raw = new byte[pixels.length * 3];
        for (int i = 0; i < pixels.length; i++) {
            raw[i * 3] = (byte) (pixels[i] >> 16);
            raw[i * 3 + 1] = (byte) (pixels[i] >> 8);
            raw[i * 3 + 2] = (byte) pixels[i];
        }
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 8 + 64);
        byte[] chunk = new byte[8192];
        while (!deflater.finished())
            out.write(chunk, 0, deflater.deflate(chunk));
        deflater.end();
        return out.toByteArray();
    }

    private static int @Nullable [] unpack(byte[] packed, int count) {
        byte[] raw = new byte[count * 3];
        Inflater inflater = new Inflater();
        inflater.setInput(packed);
        try {
            int read = 0;
            while (read < raw.length && !inflater.finished()) {
                int n = inflater.inflate(raw, read, raw.length - read);
                if (n == 0 && inflater.needsInput())
                    break;
                read += n;
            }
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
        int[] pixels = new int[count];
        for (int i = 0; i < count; i++)
            pixels[i] = (raw[i * 3] & 255) << 16 | (raw[i * 3 + 1] & 255) << 8 | (raw[i * 3 + 2] & 255);
        return pixels;
    }
}
