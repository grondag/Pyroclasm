package grondag.pyroclasm.init;

import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.projectile.EntityLavaBlob;
import net.fabricmc.fabric.api.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityCategory;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class ModEntities {
	public static final EntityType<EntityLavaBlob> LAVA_BLOB = FabricEntityTypeBuilder.create(EntityCategory.AMBIENT, EntityLavaBlob::create)
		.setImmuneToFire().build();

	public static void init() {
		Registry.register(Registry.ENTITY_TYPE, new Identifier(Pyroclasm.MODID + ":lava_blob"), LAVA_BLOB);

	}
}
