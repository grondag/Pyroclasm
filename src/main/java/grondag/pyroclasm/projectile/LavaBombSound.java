package grondag.pyroclasm.projectile;

import grondag.pyroclasm.init.ModSounds;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.util.SoundCategory;

public class LavaBombSound extends MovingSound
{
    private final EntityLavaBlob bomb;
    
    protected LavaBombSound(EntityLavaBlob bomb)
    {
        super(ModSounds.bomb_whoosh, SoundCategory.AMBIENT);
        this.bomb = bomb;
        this.repeat = true;
        this.repeatDelay = 0;
    }

    @Override
    public void update()
    {
        if (this.bomb.isDead)
        {
            this.donePlaying = true;
        }
        else
        {
            this.xPosF = (float)this.bomb.posX;
            this.yPosF = (float)this.bomb.posY;
            this.zPosF = (float)this.bomb.posZ;
        }        
    }

}
