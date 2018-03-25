package grondag.volcano.lava;


import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.google.common.collect.ComparisonChain;

import grondag.exotic_matter.serialization.NBTDictionary;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.varia.PackedBlockPos;
import grondag.volcano.Configurator;
import grondag.volcano.Log;
import grondag.volcano.simulator.LavaSimulator;
import net.minecraft.nbt.NBTTagCompound;

public class LavaBlobManager
{
    private final static String NBT_LAVA_PARTICLE_MANAGER = NBTDictionary.claim("lavaBlobs");
    private final static int NBT_SAVE_DATA_WIDTH = 4;
    
    private final static int MIN_WAIT_TICKS = 4;
    /** if drop cell doesn't have anough to form a particle by this time the lava is deleted */
    private final static int MAX_WAIT_TICKS = 200;
    
    private final ConcurrentHashMap<Long, ParticleInfo> map = new ConcurrentHashMap<Long, ParticleInfo>(512);
            
    public void clear()
    {
        map.clear();
    }
    
    public int size()
    {
        return map.size();
    }
   
    public void addLavaForParticle(long packedBlockPos, int fluidAmount)
    {
        ParticleInfo particle = map.get(packedBlockPos);
        
        if(particle == null)
        {
            particle = new ParticleInfo(Simulator.instance().getTick(), packedBlockPos, fluidAmount);
            map.put(packedBlockPos, particle);
//            HardScience.log.info("ParticleManager added new particle @" + PackedBlockPos.unpack(particle.packedBlockPos).toString() + " with amount=" + particle.getFluidUnits());
        }
        else
        {
            particle.addFluid(fluidAmount);
//            HardScience.log.info("ParticleManager updated particle @" + PackedBlockPos.unpack(particle.packedBlockPos).toString() + " with amount=" + particle.getFluidUnits() + " added " + fluidAmount);
        }
    
    }
    
    /** returns a collection of eligible particles up the max count given */
    public Collection<ParticleInfo> pollEligible(LavaSimulator sim, int maxCount)
    {
        if(map.isEmpty()) return null;
        
        int firstEligibleTick = Simulator.instance().getTick() - MIN_WAIT_TICKS;
        int forceEligibleTick = Simulator.instance().getTick() - MAX_WAIT_TICKS;
        
        // wait until minimum size * minimum age, full size,  or max age
        List<ParticleInfo> candidates = map.values().parallelStream()
                .filter(p -> p.tickCreated <= forceEligibleTick 
                    || p.fluidUnits >= LavaSimulator.FLUID_UNITS_PER_BLOCK
                    || (p.tickCreated <= firstEligibleTick && p.fluidUnits >= LavaSimulator.FLUID_UNITS_PER_LEVEL))
                .sorted(new Comparator<ParticleInfo>() {

                    @Override
                    public int compare(ParticleInfo o1, ParticleInfo o2)
                    {
                        return ComparisonChain.start()
                                .compare(o2.fluidUnits, o1.fluidUnits)
                                .compare(o1.tickCreated, o2.tickCreated)
                                .result();
                    }})
                .sequential()
                .limit(maxCount)
                .collect(Collectors.toList());
        
        candidates.stream().forEach(p -> map.remove(p.packedBlockPos));
        
        return candidates;
    }
    
    public static class ParticleInfo
    {
        public final int tickCreated;
        private int fluidUnits = 0;
        public final long packedBlockPos;
        
        private ParticleInfo(int tickCreated, long packedBlockPos, int fluidUnits)
        {
            this.tickCreated = tickCreated;
            this.packedBlockPos = packedBlockPos;
            this.fluidUnits = fluidUnits;
        }
        
        private void addFluid(int fluidUnitsIn)
        {
            this.fluidUnits += fluidUnitsIn;
        }
        
        public int getFluidUnits()
        {
            return this.fluidUnits;
        }
        
        public int x() { return PackedBlockPos.getX(this.packedBlockPos); }
        public int y() { return PackedBlockPos.getY(this.packedBlockPos); }
        public int z() { return PackedBlockPos.getZ(this.packedBlockPos); }
        
    }

    public void readFromNBT(NBTTagCompound nbt)
    {
        this.map.clear();
    
        int[] saveData = nbt.getIntArray(NBT_LAVA_PARTICLE_MANAGER);

        //confirm correct size
        if(saveData == null || saveData.length % NBT_SAVE_DATA_WIDTH != 0)
        {
            Log.warn("Invalid save data loading lava entity state buffer. Lava entities may have been lost.");
            return;
        }

        int i = 0;

        while(i < saveData.length)
        {
            ParticleInfo p = new ParticleInfo(saveData[i++], (long)saveData[i++] << 32 | (long)saveData[i++], saveData[i++]);
            
            //protect against duplicate position weirdness in save data
            if(!map.containsKey(p.packedBlockPos))
            {
                map.put(p.packedBlockPos, p);
            }
        }

        Log.info("Loaded " + map.size() + " lava entities.");
    }

    public void writeToNBT(NBTTagCompound nbt)
    {
        if(Configurator.VOLCANO.enablePerformanceLogging)
            Log.info("Saving " + map.size() + " lava entities.");
        
        int[] saveData = new int[map.size() * NBT_SAVE_DATA_WIDTH];
        int i = 0;

        for(ParticleInfo p: map.values())
        {
            saveData[i++] = p.tickCreated;
            saveData[i++] = (int) (p.packedBlockPos & 0xFFFFFFFF);
            saveData[i++] = (int) ((p.packedBlockPos >> 32) & 0xFFFFFFFF);
            saveData[i++] = p.fluidUnits;
        }       

        nbt.setIntArray(NBT_LAVA_PARTICLE_MANAGER, saveData);

    }
}
