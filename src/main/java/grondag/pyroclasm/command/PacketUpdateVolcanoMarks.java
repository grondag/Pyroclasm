package grondag.pyroclasm.command;

import javax.annotation.Nullable;

import net.minecraft.util.PacketByteBuf;


//TODO: redo w/ Brigadier
public class PacketUpdateVolcanoMarks { //extends AbstractServerToPlayerPacket<PacketUpdateVolcanoMarks> {
    private @Nullable long[] data;

    public PacketUpdateVolcanoMarks() {

    }

    public PacketUpdateVolcanoMarks(long[] marks) {
        this.data = marks;
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
