package grondag.pyroclasm;

import java.util.IdentityHashMap;

import grondag.exotic_matter.block.BlockHarvestTool;
import grondag.exotic_matter.init.ConfigPathNodeType;
import grondag.exotic_matter.init.SubstanceConfig;
import grondag.pyroclasm.simulator.LavaCell;
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

@LangKey("pyroclasm.config.general")
@Config(modid = Pyroclasm.MODID, type = Type.INSTANCE)
public class Configurator
{
    
    public static void recalcDerived()
    {
        Volcano.recalcDerived();
    }

    ////////////////////////////////////////////////////        
    // SUBSTANCES
    ////////////////////////////////////////////////////
    @LangKey("pyroclasm.config.substance")
    @Comment("Volcano material properties.")
    public static Substances SUBSTANCES = new Substances();

    public static class Substances
    {
        @LangKey("pyroclasm.config.basalt_material")
        public SubstanceConfig basalt = new SubstanceConfig(2, BlockHarvestTool.PICK, 1, 10, 1.0);

        @LangKey("pyroclasm.config.lava_material")
        public SubstanceConfig volcanicLava = new SubstanceConfig(-1, BlockHarvestTool.SHOVEL, 3, 2000, 0.75).setBurning().withPathNodeType(ConfigPathNodeType.LAVA);

        @LangKey("pyroclasm.config.lava_light_level")
        @Comment({"Light level emitted by fully-molten volcanic lava.",
            "Larger numbers can harm performance by triggering many lighting updates.",
            "The default is a balance between performance and aestheics.",
            "For best performance, choose zero.",
            "Note: changes will not be visible until chunks containing lava are relit due to other events."})
        @RangeInt(min = 0, max = 15)
        public int lavaLightLevel = 4;
        
        @LangKey("pyroclasm.config.destroyed_blocks")
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

        @LangKey("pyroclasm.config.safe_blocks")
        @Comment({"Blocks that will stop the flow of volcanic lava.",
            "Blocks should be listed in modname:blockname format.",
            "At this time, metadata and NBT values cannot be specified."})
        public String[] blocksSafeFromVolcanicLava = {"minecraft:end_gateway", "minecraft:portal", "minecraft:end_portal"};
    }    

    ////////////////////////////////////////////////////        
    // PERFORMANCE
    ////////////////////////////////////////////////////
    @LangKey("pyroclasm.config.performance")
    @Comment("Performance Tuning")
    public static Performance PERFORMANCE = new Performance();

    public static class Performance
    {
        @LangKey("pyroclasm.config.max_chunk_updates_per_tick")
        @Comment({"Maximum number of chunk updates applied to world each tick.",
        "The actual number of chunk render updates can be higher due to effects on neighboring chunks.",
        "Higher numbers provide more realism but can negatively affect performance.",
        "Server-side only"})
        @RangeInt(min = 1, max = 10)
        public int maxChunkUpdatesPerTick = 1;

        @LangKey("pyroclasm.config.cooldown_target_load_factor")
        @Comment({"Fraction of alloted CPU usage must be drop below this before volcano in cooldown mode starts to flow again.",
            "Server-side only"})
        @RangeDouble(min = 0.3, max = 0.7)
        public float cooldownTargetLoadFactor = 0.5F;

        @LangKey("pyroclasm.config.cooldown_wait_ticks")
        @Comment({"After volcano in cooldown reaches the target load factor, it waits this many ticks before flowing again.",
            "Server-side only"})
        @RangeInt(min = 0, max = 6000)
        public int cooldownWaitTicks = 200;

        @LangKey("pyroclasm.config.total_tick_budget")
        @Comment({"Percentage of elapsed time that can be devoted to volcano fluid simulation overall.",
            "This includes both single-threaded on-tick time and multi-threaded processing that occurs between server ticks.",
            "Larger numbers will enable larger lava flows but will consume more CPU used for other tasks on the machine where it runs.",
            "If you are seeing lag or log spam that the server can't keep up, reduce this mumber or disable volcanos.",
            "Server-side only"})
        @RangeInt(min = 5, max = 60)
        public int totalProcessingBudget = 10;

        @LangKey("pyroclasm.config.on_tick_budget")
        @Comment({"Percentage of each server tick (1/20 of a second) that can be devoted to volcano fluid simulation.",
            "This is the single-threaded part of the simulation that interacts with the game world.",
            "Larger numbers will enable larger lava flows but casue simulation to compete with other in-game objects that tick.",
            "If you are seeing log spam that the server can't keep up, reduce this mumber or disable volcanos.",
            "Server-side only"})
        @RangeInt(min = 2, max = 30)
        public int onTickProcessingBudget = 5;

        @LangKey("pyroclasm.config.max_lava_chunks")
        @Comment({"Maximum number of chunks containing lava to be targeted by lava simulator.",
            "Lava flow will be throttled when in excess of this number.",
            "While it can affect CPU consumption, CPU usage is controlled more directly via tick budgets.",
            "Small numbers will mean smaller lava flows, lower memory consumption, and shorter/smaller world saves.",
            "Server-side only"})
        @RangeInt(min = 50, max = 400)
        public int chunkBudget = 100;

        @LangKey("pyroclasm.config.max_cooling_blocks")
        @Comment({"Maximum number of cooling basalt blocks to be tracked by lava simulator.",
            "Lava flow will be throttled when in excess of this number.",
            "While it can affect CPU consumption, CPU usage is controlled more directly via tick budgets.",
            "Smaller numbers will cause more blocks to cool before lava starts flowing again and may also ",
            "cause smaller lava flows, lower memory consumption, and shorter/smaller world saves.",
            "Server-side only"})
        @RangeInt(min = 10000, max = 20000000)
        public int coolingBlockBudget = 20000;

        @LangKey("pyroclasm.config.concurrency_threshold")
        @Comment({"When cell counts are above this limit, lava simulator will use multiple threads for cell processing.",
            "Defaults should generally be OK, but allows ajustment to tune performance for your hardware.",
            "Server-side only"})
        @RangeInt(min = 1000, max = 1000000)
        public int concurrencyThreshold = 2000;

        @LangKey("pyroclasm.config.perf_sample_secs")
        @Comment({"Number of seconds in each volcano fluid simulation performance sample.",
            "Larger numbers reduce log spam when performance logging is enabled.",
            "Smaller numbers (recommended) make fluid simulation performance throttling more responsive.",
            "Server-side only"})
        @RangeInt(min = 10, max = 120)
        public int performanceSampleInterval = 20;
        
        @LangKey("pyroclasm.config.terrain_setup_off_thread")
        @Comment({"When true, chunk rebuilds will always happen outside the main client thread. ",
        "Normally, Minecraft will rebuild nearby chunks on the main thread, but because volcanos ",
        "generate so many block updates, so frequently, it can cause low frame rates. ",
        "This settting is identical to the Forge setting of the same name, and even uses the Forge",
        "implementation internally to avoid creating a redundant hook, except it is enabled by default.",
        "*** Strongly recommended you leave this on. ***",
        "Client-side only."})
        public boolean alwaysSetupTerrainOffThread = true;

    }

    ////////////////////////////////////////////////////        
    // DEBUG
    ////////////////////////////////////////////////////
    @LangKey("pyroclasm.config.debug")
    @Comment("Debug and Testing Features")
    public static Debug DEBUG = new Debug();
    public static class Debug
    {

        @LangKey("pyroclasm.config.cell_debug_render")
        @Comment({"Enable render of lava cell bounding boxes for debug purposes.",
        "Client-side only."})
        public boolean enableLavaCellDebugRender = false;
        
        @LangKey("pyroclasm.config.chunk_debug_render")
        @Comment({"Enable render of lava cell chunk bounding boxes for debug purposes.",
        "Client-side only."})
        public boolean enableLavaChunkDebugRender = false;

        @LangKey("pyroclasm.config.enable_perf_log")
        @RequiresMcRestart
        @Comment({"If true, volcano simulation will periodically output performance statistics to log.",
            "Does cause minor additional overhead and log spam so should generally only be enabled for testing.",
            "Turning this off does NOT disable the minimal performance counting needed to detect simulation overload."})
        public boolean enablePerformanceLogging = false;
        
        @LangKey("pyroclasm.config.enable_flow_tracking")
        @RequiresMcRestart
        @Comment({"If true, volcano simulation will track and output the amount of fluid that flows across cell connections.",
            "Can cause additional overhead and log spam so should generally only be enabled for testing.",
            "Turning this off does NOT disable the minimal performance counting needed to detect simulation overload."})
        public boolean enableFlowTracking = false;

        @LangKey("pyroclasm.config.enable_cell_stats")
        @Comment({"If true, volcano simulation will output cell debug information each performance interval.",
            "Will cause significant log spam so should only be enabled for testing."})
        public boolean outputLavaCellDebugSummaries = false;

        @LangKey("pyroclasm.config.enable_chunk_trace")
        @Comment({"If true, volcano simulation will output cell chunk creation, load and unload.",
            "Will cause significant log spam so should only be enabled for debug and testing."})
        public boolean enableLavaCellChunkTrace = false;
    }
    
    ////////////////////////////////////////////////////        
    // VOLCANO
    ////////////////////////////////////////////////////
    @LangKey("pyroclasm.config.lava")
    @Comment("Lava and Basalt Configuration")
    public static Lava LAVA = new Lava();

    public static class Lava
    {
        @LangKey("pyroclasm.config.lava_cooling_ticks")
        @Comment({"Number of ticks lava should go without a significant flow before it becomes basalt.",
            "Should be larger than minDormantTicks"})
        @RangeInt(min = 200, max = 200000)
        public int lavaCoolingTicks = 200;
        
        @LangKey("pyroclasm.config.basalt_cooling_ticks")
        @Comment({"Minimum number of ticks needed for basalt to cool from one stage to another."})
        @RangeInt(min = 1, max = 20000)
        public int basaltCoolingTicks = 20;
        
        @LangKey("pyroclasm.config.min_cooling_propagation_ticks")
        @Comment({"Once lava starts cooling, the minimum number of ticks (inclusive) an interior block has to wait after",
            "a neighbor successfully cools before it can also cool."})
        @RangeInt(min = 1, max = 2000)
        public int lavaCoolingPropagationMin = 20;
        
        @LangKey("pyroclasm.config.max_cooling_propagation_ticks")
        @Comment({"Once lava starts cooling, the maximum number of ticks (exclusive) an interior block has to wait after",
            "a neighbor successfully cools before it can also cool."})
        @RangeInt(min = 2, max = 2001)
        public int lavaCoolingPropagationMax = 40;
        
        @LangKey("pyroclasm.config.lava_keepalive_flow_threshold")
        @Comment({"The minimum lava fluid units that must flow through a cell (in or out) during a single tick to delay cooling.",
            "1000 units is a single level of lava, and 12000 units are an entire block of lava.",
            "Small numbers will allow lava to fully level before it cools but it may take a longer time.",
            "Larger number will result in faster cooling but the resulting terrain may be less even."})
        @RangeInt(min = 2, max = 1000)
        public int lavaKeepaliveFlowThreshold = 40;
        
        @LangKey("pyroclasm.config.lava_flow_reversal_threshold")
        @Comment({"The minimum difference in cell lava surface units required for the direction of flow between two cell to reverse direction.",
            "1000 units is a single level of lava, and 12000 units are an entire block of lava.",
            "Larger numbers will help prevent oscillations in pooled lava so that cooling can begin.",
            "Smaller numbers will allow lava to fully equalize levels before lava hardens but may take longer."})
        @RangeInt(min = 2, max = 1000)
        public int lavaFlowReversalThreshold = 250;
    }
    
    ////////////////////////////////////////////////////        
    // VOLCANO
    ////////////////////////////////////////////////////
    @LangKey("pyroclasm.config.volcano")
    @Comment("Settings for Volcano feature.")
    public static Volcano VOLCANO = new Volcano();

    public static class Volcano
    {
        @LangKey("pyroclasm.config.lava_blocks_per_second")
        @Comment("Volume of lava ejected from bore, in full blocks per second.  Does not include proectile lava volume.")
        @RangeInt(min = 1, max = 64)
        public int lavaBlocksPerSecond = 8;
        
        @LangKey("pyroclasm.config.max_y_level")
        @Comment("Y-orthogonalAxis build limit at which Volcano becomes permanently dormant.")
        @RangeInt(min = 128, max = 255)
        public int maxYLevel = 147; 

        //TODO: use this
        @LangKey("pyroclasm.config.mound_blocks_per_tick")
        @Comment("Number of blocks per tick that can be cleared by volcano mounding. Values above 1 are mostly useful for testing.")
        @RangeInt(min = 1, max = 64)
        public int moundBlocksPerTick = 64;

        @LangKey("pyroclasm.config.mound_radius")
        @Comment("Radius of one standard deviation, in blocks, for underground volcano mounding.")
        @RangeInt(min = 14, max = 28)
        public int moundRadius = 20;

        @LangKey("pyroclasm.config.min_dormant_ticks")
        @Comment("Minimum number of ticks between the time a volcano becomes dormant and the same or another erupts.")
        @RangeInt(min = 20, max = 24000000)
        public int minDormantTicks = 20;

        @LangKey("pyroclasm.config.max_dormant_ticks")
        @Comment({"Maximum number of ticks between the time a volcano becomes dormant and the same or another erupts.",
            "Should be larger than minDormantTicks"})
        @RangeInt(min = 20, max = 24000000)
        public int maxDormantTicks = 200;

        @LangKey("pyroclasm.config.max_lava_projectiles")
        @Comment({"Maximum number of flying/falling volcalnic lava entities that may be in the world simultaneously.",
            "Higher numbers may provide more responsive flowing and better realism but can create lag."})
        @RangeInt(min = 10, max = 200)
        public int maxLavaEntities = 20;

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
        
        /**
         * Like {@link #lavaKeepaliveFlowThreshold} but for cells under pressure. 
         * Flows in cells under pressure count for more than cells in free flow.
         * Will always be >= 1.
         */
        public static int lavaCoolingPressuredKeepaliveThreshold;

        private static void recalcDerived()
        {
            lavaCoolingPressuredKeepaliveThreshold = Math.max(1, LAVA.lavaKeepaliveFlowThreshold / LavaCell.PRESSURE_FACTOR);
            
            performanceSampleIntervalMillis = PERFORMANCE.performanceSampleInterval * 1000;
            peformanceSampleIntervalNanos = (long)performanceSampleIntervalMillis * 1000000;
            performanceBudgetOnTickNanos = peformanceSampleIntervalNanos * PERFORMANCE.onTickProcessingBudget / 100;
            performanceBudgetTotalNanos = peformanceSampleIntervalNanos * PERFORMANCE.totalProcessingBudget / 100;

            if(VOLCANO.maxDormantTicks <= VOLCANO.minDormantTicks)
            {
                VOLCANO.maxDormantTicks = VOLCANO.minDormantTicks + 20;
            }

            IForgeRegistry<Block> reg = GameRegistry.findRegistry(Block.class);

            blocksDestroyedByLava.clear();
            for(String s : SUBSTANCES.blocksDestroyedByVolcanicLava)
            {
                ResourceLocation rl = new ResourceLocation(s);
                if(reg.containsKey(rl))
                {
                    Block b = reg.getValue(rl);
                    blocksDestroyedByLava.put(b, b);
                }
                else
                {
                    Pyroclasm.INSTANCE.warn("Did not find block " + s + " configured to be destroyed by volcanic lava. Confirm block name is correct." );
                }
            }

            blocksSafeFromLava.clear();
            for(String s : SUBSTANCES.blocksSafeFromVolcanicLava)
            {
                ResourceLocation rl = new ResourceLocation(s);
                if(reg.containsKey(rl))
                {
                    Block b = reg.getValue(rl);
                    blocksSafeFromLava.put(b, b);
                }
                else
                {
                    Pyroclasm.INSTANCE.warn("Did not find block " + s + " configured to be safe from volcanic lava. Confirm block name is correct." );
                }
            }
        }
    }
}
