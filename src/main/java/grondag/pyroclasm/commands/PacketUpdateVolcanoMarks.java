package grondag.pyroclasm.commands;

import javax.annotation.Nullable;

import grondag.exotic_matter.network.AbstractServerToPlayerPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketUpdateVolcanoMarks extends AbstractServerToPlayerPacket<PacketUpdateVolcanoMarks>
{
    private @Nullable long[] data;
    
    public PacketUpdateVolcanoMarks()
    {
        
    }
    
    public PacketUpdateVolcanoMarks(long[] marks)
    {
        this.data = marks;
    }
    
    @Override
    public void fromBytes(PacketBuffer pBuff)
    {
        data = pBuff.readLongArray(data);
    }

    @SuppressWarnings("null")
    @Override
    public void toBytes(PacketBuffer pBuff)
    {
        pBuff.writeLongArray(data);
    }

    @SuppressWarnings("null")
    @Override
    protected void handle(PacketUpdateVolcanoMarks message, MessageContext context)
    {
        VolcanoMarks.setMarks(message.data);
    }
}
