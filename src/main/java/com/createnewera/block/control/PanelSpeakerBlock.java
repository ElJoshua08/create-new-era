package com.createnewera.block.control;

import java.util.Arrays;
import java.util.Locale;

import org.squiddev.cobalt.LuaError;

import com.createnewera.block.DirectionalShape;
import com.createnewera.bus.DeviceDrivers;
import com.createnewera.network.SpeakPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A speaker grille on a plate: plays any sound in the game, plays notes like a note block, and
 * speaks text aloud to the players around it with their system's text-to-speech voice (shown
 * as a subtitle too, for anyone without one).
 */
public class PanelSpeakerBlock extends PanelControlBlock {
    /** How far a spoken line carries, in blocks */
    public static final double SPEECH_RANGE = 48;
    /** In choices: every sound the game knows, filled in by the editor */
    public static final String SOUNDS = "@sounds";

    public PanelSpeakerBlock(Properties properties, DirectionalShape shape) {
        super(properties, shape);
    }

    @Override
    public void methods(DeviceDrivers.Api api, ServerLevel level, BlockPos pos) {
        api.order("playSound", "sound: id, volume?: 0-4, pitch?: 0.5-2", "", args -> {
            ResourceLocation id = ResourceLocation.tryParse(args.arg(1).checkString());
            if (id == null)
                throw new LuaError("bad argument #1 to 'playSound' (a sound id expected)");
            float volume = args.arg(2).isNil() ? 1 : (float) Mth.clamp(args.arg(2).checkDouble(), 0, 4);
            float pitch = args.arg(3).isNil() ? 1 : (float) Mth.clamp(args.arg(3).checkDouble(), 0.5, 2);
            // a sound no one registered still plays, if a resource pack has it
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(id).orElseGet(() -> SoundEvent.createVariableRangeEvent(id));
            level.playSound(null, pos, sound, SoundSource.RECORDS, volume, pitch);
            return null;
        });
        api.choices("playSound", java.util.List.of(SOUNDS));
        api.order("playNote", "instrument: name, note: 0-24, volume?: 0-4", "", args -> {
            NoteBlockInstrument instrument = instrument(args.arg(1).checkString());
            int note = Mth.clamp(args.arg(2).checkInteger(), 0, 24);
            float volume = args.arg(3).isNil() ? 3 : (float) Mth.clamp(args.arg(3).checkDouble(), 0, 4);
            level.playSound(null, pos, instrument.getSoundEvent().value(), SoundSource.RECORDS, volume, NoteBlock.getPitchFromNote(note));
            return null;
        });
        api.choices("playNote", Arrays.stream(NoteBlockInstrument.values()).filter(NoteBlockInstrument::isTunable)
                .map(NoteBlockInstrument::getSerializedName).toList());
        api.order("speak", "text: string", "", args -> {
            String text = args.arg(1).checkString();
            if (text.length() > 256)
                text = text.substring(0, 256);
            PacketDistributor.sendToPlayersNear(level, null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SPEECH_RANGE,
                    new SpeakPayload(pos, text));
            return null;
        });
    }

    private static NoteBlockInstrument instrument(String name) throws LuaError {
        for (NoteBlockInstrument instrument : NoteBlockInstrument.values())
            if (instrument.isTunable() && instrument.getSerializedName().equals(name.toLowerCase(Locale.ROOT)))
                return instrument;
        throw new LuaError("unknown instrument '" + name + "'");
    }

    @Override
    public Object value(BlockState state) {
        return null;
    }

    @Override
    public String valueType() {
        return "nil";
    }

    @Override
    public Component goggleValue(BlockState state) {
        return Component.translatable("create_new_era.goggles.speaker");
    }
}
