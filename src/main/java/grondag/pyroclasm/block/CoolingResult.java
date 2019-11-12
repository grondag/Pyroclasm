package grondag.pyroclasm.block;

public enum CoolingResult {
	/** means no more cooling can take place */
	COMPLETE,
	/** means one stage completed - more remain */
	PARTIAL,
	/** means block wan't ready to cool */
	UNREADY,
	/** means this isn't a cooling block */
	INVALID
}