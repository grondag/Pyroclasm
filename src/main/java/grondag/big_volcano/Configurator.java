package grondag.big_volcano;

import java.util.IdentityHashMap;

import grondag.exotic_matter.init.ConfigPathNodeType;
import grondag.exotic_matter.init.SubstanceConfig;
import grondag.exotic_matter.model.BlockHarvestTool;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.Config.Comment;
import net.minecraftforge.common.config.Config.LangKey;
import net.minecraftforge.common.config.Config.RangeDouble;
import net.minecraftforge.common.config.Config.RangeInt;
import net.minecraftforge.common.config.Config.RequiresMcRestart;
import net.minecraftforge.common.config.Config.Type;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.IForgeRegistry;

@LangKey("config.general")
@Config(modid = BigActiveVolcano.MODID, type = Type.INSTANCE)
public class Configurator
{

    public static void recalcDerived()
    {
        Volcano.recalcDerived();
    }

    ////////////////////////////////////////////////////        
    // SUBSTANCES
    ////////////////////////////////////////////////////
    @LangKey("config.substance")
    @Comment("Volcano material properties.")
    public static Substances SUBSTANCES = new Substances();

    public static class Substances
    {

        public SubstanceConfig basalt = new SubstanceConfig(2, BlockHarvestTool.PICK, 1, 10, 1.0);

        public SubstanceConfig volcanicLava = new SubstanceConfig(-1, BlockHarvestTool.SHOVEL, 3, 2000, 0.75).setBurning().withPathNodeType(ConfigPathNodeType.LAVA);
    }    


    ////////////////////////////////////////////////////        
    // VOLCANO
    ////////////////////////////////////////////////////
    @LangKey("config.volcano")
    @Comment("Settings for Volcano feature.")
    public static Volcano VOLCANO = new Volcano();

    public static class Volcano
    {
        @Comment("Basically the purpose of this mod.")
        public boolean enableVolcano = true;

        @Comment("Fraction of alloted CPU usage must be drop below this before volcano in cooldown mode starts to flow again.")
        @RangeDouble(min = 0.3, max = 0.7)
        public float cooldownTargetLoadFactor = 0.5F;

        @Comment("After volcano in cooldown reaches the target load factor, it waits this many ticks before flowing again.")
        @RangeInt(min = 0, max = 6000)
        public int cooldownWaitTicks = 200;

        @Comment("Y-orthogonalAxis build limit at which Volcano becomes permanently dormant.")
        @RangeInt(min = 128, max = 255)
        public int maxYLevel = 147; 

        @Comment("Number of blocks per tick that can be cleared by volcano mounding. Values above 1 are mostly useful for testing.")
        @RangeInt(min = 1, max = 64)
        public int moundBlocksPerTick = 64;

        @Comment("Radius of one standard deviation, in blocks, for underground volcano mounding.")
        @RangeInt(min = 14, max = 28)
        public int moundRadius = 20;

        @Comment("Minimum number of ticks between the time a volcano becomes dormant and the same or another erupts.")
        @RangeInt(min = 20, max = 24000000)
        public int minDormantTicks = 20;

        @Comment({"Maximum number of ticks between the time a volcano becomes dormant and the same or another erupts.",
        "Should be larger than minDormantTicks"})
        @RangeInt(min = 20, max = 24000000)
        public int maxDormantTicks = 200;

        @Comment({"Maximum number of flying/falling volcalnic lava entities that may be in the world simultaneously.",
        "Higher numbers may provide more responsive flowing and better realism but can create lag."})
        @RangeInt(min = 10, max = 200)
        public int maxLavaEntities = 20;

        @Comment({"Number of ticks needed for lava or basalt to cool from one stage to another.",
        "Should be larger than minDormantTicks"})
        @RangeInt(min = 200, max = 200000)
        public int basaltCoolingTicks = 200;

        @Comment({"Block updates are buffered for at least this many ticks before applied to world.",
        "Higher numbers can be better for performance but may cause block updates to appear strangely."})
        @RangeInt(min = 0, max = 20)
        public int minBlockUpdateBufferTicks = 3;

        @Comment({"Block updates are considered high priority after this many ticks.",
        "Higher numbers can be better for performance but may cause block updates to appear strangely."})
        @RangeInt(min = 10, max = 40)
        public int maxBlockUpdateBufferTicks = 20;

        @Comment({"Maximum number of chunk updates applied to world each tick.",
            "The actual number of chunk render updates can be higher due to effects on neighboring chunks.",
        "Higher numbers provide more realism but can negatively affect performance."})
        @RangeInt(min = 1, max = 10)
        public int maxChunkUpdatesPerTick = 2;

        @Comment({"Blocks that will be destroyed on contact by volcanic lava.",
            "Blocks should be listed in modname:blockname format.",
        "At this time, metadata and NBT values cannot be specified."})
        public String[] blocksDestroyedByVolcanicLava = {
                "minecraft:sponge", 
                "minecraft:stone_pressure_plate",
                "minecraft:ice",
                "minecraft:snow",
                "minecraft:cactus",
                "minecraft:pumpkin",
                "minecraft:lit_pumpkin",
                "minecraft:cake",
                "minecraft:stained_glass",
                "minecraft:glass_pane",
                "minecraft:melon_block",
                "minecraft:redstone_lamp",
                "minecraft:lit_redstone_lamp",
                "minecraft:light_weighted_pressure_plate",
                "minecraft:heavy_weighted_pressure_plate",
                "minecraft:stained_glass_pane",
                "minecraft:slime",
                "minecraft:hay_block",
                "minecraft:coal_block",
                "minecraft:packed_ice",
        "minecraft:frosted_ice"};

        @Comment({"Blocks that will stop the flow of volcanic lava.",
            "Blocks should be listed in modname:blockname format.",
        "At this time, metadata and NBT values cannot be specified."})
        public String[] blocksSafeFromVolcanicLava = {"minecraft:end_gateway", "minecraft:portal", "minecraft:end_portal"};

        @RequiresMcRestart
        @Comment({"If true, volcano simulation will periodically output performance statistics to log.",
            "Does cause minor additional overhead and log spam so should generally only be enabled for testing.",
        "Turning this off does NOT disable the minimal performance counting needed to detect simulation overload."})
        public boolean enablePerformanceLogging = false;

        @RequiresMcRestart
        @Comment({"Number of seconds in each volcano fluid simulation performance sample.",
            "Larger numbers reduce log spam when performance logging is enabled.",
        "Smaller numbers (recommended) make fluid simulation performance throttling more responsive."})
        @RangeInt(min = 10, max = 120)
        public int performanceSampleInterval = 20;

        @RequiresMcRestart
        @Comment({"Percentage of each server tick (1/20 of a second) that can be devoted to volcano fluid simulation.",
            "This is the single-threaded part of the simulation that interacts with the game world.",
            "Larger numbers will enable larger lava flows but casue simulation to compete with other in-game objects that tick.",
        "If you are seeing log spam that the server can't keep up, reduce this mumber or disable volcanos."})
        @RangeInt(min = 2, max = 30)
        public int onTickProcessingBudget = 5;

        @RequiresMcRestart
        @Comment({"Percentage of elapsed time that can be devoted to volcano fluid simulation overall.",
            "This includes both single-threaded on-tick time and multi-threaded processing that occurs between server ticks.",
            "Larger numbers will enable larger lava flows but will consume more CPU used for other tasks on the machine where it runs.",
        "If you are seeing lag or log spam that the server can't keep up, reduce this mumber or disable volcanos."})
        @RangeInt(min = 5, max = 60)
        public int totalProcessingBudget = 10;

        @RequiresMcRestart
        @Comment({"If true, volcano simulation will track and output the amount of fluid that flows across cell connections.",
            "Can cause additional overhead and log spam so should generally only be enabled for testing.",
        "Turning this off does NOT disable the minimal performance counting needed to detect simulation overload."})
        public boolean enableFlowTracking = false;

        @Comment({"If true, volcano simulation will output cell debug information each performance interval.",
        "Will cause significant log spam so should only be enabled for testing."})
        public boolean outputLavaCellDebugSummaries = false;


        /** Contains block objects configured to be destroyed by lava */
        public static final IdentityHashMap<Block, Block> blocksDestroyedByLava = new IdentityHashMap<Block, Block>();

        /** Contains block objects configured to be unharmed by lava */
        public static final IdentityHashMap<Block, Block> blocksSafeFromLava = new IdentityHashMap<Block, Block>();

        /** Number of milliseconds in a volcano fluid simulation performance sample */
        public static int performanceSampleIntervalMillis;

        /** Number of nanoseconds in a volcano fluid simulation performance sample */
        public static long peformanceSampleIntervalNanos;

        /** Number of nanoseconds budgeted each interval for on-tick processing */
        public static long performanceBudgetOnTickNanos;

        /** Number of nanoseconds budgeted each interval for off-tick processing */
        public static long performanceBudgetTotalNanos;

        private static void recalcDerived()
        {
            performanceSampleIntervalMillis = VOLCANO.performanceSampleInterval * 1000;
            peformanceSampleIntervalNanos = (long)performanceSampleIntervalMillis * 1000000;
            performanceBudgetOnTickNanos = peformanceSampleIntervalNanos * VOLCANO.onTickProcessingBudget / 100;
            performanceBudgetTotalNanos = peformanceSampleIntervalNanos * VOLCANO.totalProcessingBudget / 100;

            if(VOLCANO.maxDormantTicks <= VOLCANO.minDormantTicks)
            {
                VOLCANO.maxDormantTicks = VOLCANO.minDormantTicks + 20;
            }

            IForgeRegistry<Block> reg = GameRegistry.findRegistry(Block.class);

            blocksDestroyedByLava.clear();
            for(String s : VOLCANO.blocksDestroyedByVolcanicLava)
            {
                ResourceLocation rl = new ResourceLocation(s);
                if(reg.containsKey(rl))
                {
                    Block b = reg.getValue(rl);
                    blocksDestroyedByLava.put(b, b);
                }
                else
                {
                    BigActiveVolcano.INSTANCE.warn("Did not find block " + s + " configured to be destroyed by volcanic lava. Confirm block name is correct." );
                }
            }

            blocksSafeFromLava.clear();
            for(String s : VOLCANO.blocksSafeFromVolcanicLava)
            {
                ResourceLocation rl = new ResourceLocation(s);
                if(reg.containsKey(rl))
                {
                    Block b = reg.getValue(rl);
                    blocksSafeFromLava.put(b, b);
                }
                else
                {
                    BigActiveVolcano.INSTANCE.warn("Did not find block " + s + " configured to be safe from volcanic lava. Confirm block name is correct." );
                }
            }
        }
    }
}
