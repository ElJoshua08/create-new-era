package com.createnewera.bus;

import java.lang.reflect.Field;
import java.util.List;

import org.squiddev.cobalt.LuaError;

import com.createnewera.bus.DeviceDrivers.Api;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.piston.LinearActuatorBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.Instruction;
import com.simibubi.create.content.kinetics.transmission.sequencer.InstructionSpeedModifiers;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Create's machines that build and move contraptions, and the sequenced gearshift: what a player
 * does by clicking them or setting them up in a screen, a program does by calling them.
 */
final class CreateMachines {
    static void add(Api api, BlockEntity be) {
        if (be instanceof MechanicalBearingBlockEntity bearing) {
            // bearings, windmill bearings, Aeronautics' propeller bearings
            api.family("bearing");
            api.read("isAssembled", "", "boolean", args -> bearing.isRunning());
            api.read("getAngle", "", "degrees", args -> round(bearing.getInterpolatedAngle(1)));
            api.read("getRotationSpeed", "", "degrees/tick", args -> round(bearing.getAngularSpeed()));
            api.order("assemble", "", "", args -> {
                if (!bearing.isRunning())
                    assembleNextTick(bearing, MechanicalBearingBlockEntity.class);
                return null;
            });
            api.order("disassemble", "", "", args -> {
                if (bearing.isRunning())
                    bearing.disassemble();
                return null;
            });
        }
        if (be instanceof LinearActuatorBlockEntity actuator) {
            // pistons, pulleys, gantry carriages
            api.family("linear_actuator");
            api.read("isAssembled", "", "boolean", args -> actuator.running);
            api.read("getExtension", "", "blocks", args -> round(actuator.getInterpolatedOffset(1)));
            api.order("assemble", "", "", args -> {
                if (!actuator.running)
                    actuator.assembleNextTick = true;
                return null;
            });
            api.order("disassemble", "", "", args -> {
                if (actuator.running)
                    actuator.disassemble();
                return null;
            });
        }
        if (be instanceof SequencedGearshiftBlockEntity sequencer) {
            api.family("sequenced_gearshift");
            api.read("isRunning", "", "boolean", args -> !sequencer.isIdle());
            api.order("rotate", "degrees: number, speed?: \"normal\" | \"fast\"", "", args -> {
                run(sequencer, SequencerInstructions.TURN_ANGLE, args.arg(1).checkInteger(), args.arg(2).optString("normal"));
                return null;
            });
            api.order("move", "blocks: number, speed?: \"normal\" | \"fast\"", "", args -> {
                run(sequencer, SequencerInstructions.TURN_DISTANCE, args.arg(1).checkInteger(), args.arg(2).optString("normal"));
                return null;
            });
            api.choices("rotate", 1, List.of("normal", "fast"));
            api.choices("move", 1, List.of("normal", "fast"));
        }
    }

    /** One step for the sequencer, as its screen would set it, then run: negative goes backwards. */
    private static void run(SequencedGearshiftBlockEntity sequencer, SequencerInstructions type, int amount, String speed) throws LuaError {
        if (!speed.equals("normal") && !speed.equals("fast"))
            throw new LuaError("bad argument #2 (\"normal\" or \"fast\" expected, got \"" + speed + "\")");
        int modifier = (amount < 0 ? -1 : 1) * (speed.equals("fast") ? 2 : 1);
        sequencer.getInstructions().clear();
        sequencer.getInstructions().add(new Instruction(type, InstructionSpeedModifiers.getByModifier(modifier), Math.abs(amount)));
        sequencer.getInstructions().add(new Instruction(SequencerInstructions.END));
        sequencer.run(0);
    }

    /** Assembles on its next tick, as a player's click does: the machine checks it can first. */
    private static void assembleNextTick(BlockEntity be, Class<?> owner) throws LuaError {
        try {
            Field field = owner.getDeclaredField("assembleNextTick");
            field.setAccessible(true);
            field.setBoolean(be, true);
        } catch (ReflectiveOperationException e) {
            throw new LuaError("can't assemble this from a program");
        }
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private CreateMachines() {}
}
