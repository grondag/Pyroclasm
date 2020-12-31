package grondag.pyroclasm.command;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.PacketByteBuf;


//TODO: redo w/ Brigadier
public class PacketUpdateVolcanoMarks { //extends AbstractServerToPlayerPacket<PacketUpdateVolcanoMarks> {
    private @Nullable long[] data;

    public PacketUpdateVolcanoMarks() {

    }

    public PacketUpdateVolcanoMarks(long[] marks) {
        data = marks;
    }

    public void fromBytes(PacketByteBuf pBuff) {
        data = pBuff.readLongArray(data);
    }

    public void toBytes(PacketByteBuf pBuff) {
        pBuff.writeLongArray(data);
    }

    protected void handle(PacketUpdateVolcanoMarks message) { //, MessageContext context) {
        VolcanoMarks.setMarks(message.data);
    }
}
