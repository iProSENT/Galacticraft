package micdoodle8.mods.galacticraft.core.tile;

import java.util.HashSet;
import java.util.Set;
import micdoodle8.mods.galacticraft.api.recipe.CompressorRecipes;
import micdoodle8.mods.galacticraft.core.GalacticraftCore;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.StatCollector;
import universalelectricity.core.item.IItemElectric;
import universalelectricity.prefab.network.IPacketReceiver;
import universalelectricity.prefab.network.PacketManager;
import com.google.common.io.ByteArrayDataInput;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;

/**
 * GCCoreTileEntityIngotCompressor.java
 *
 * This file is part of the Galacticraft project
 *
 * @author micdoodle8
 * @license Lesser GNU Public License v3 (http://www.gnu.org/licenses/lgpl.html)
 * 
 */
public class GCCoreTileEntityIngotCompressor extends TileEntity implements IInventory, ISidedInventory, IPacketReceiver
{
    public static final int PROCESS_TIME_REQUIRED = 200;
    public int processTicks = 0;
    public int furnaceBurnTime = 0;
    public int currentItemBurnTime = 0;
    private long ticks;

    private ItemStack producingStack = null;
    private ItemStack[] containingItems = new ItemStack[2];
    public InventoryCrafting compressingCraftMatrix = new InventoryCrafting(null, 3, 3);
    public final Set<EntityPlayer> playersUsing = new HashSet<EntityPlayer>();

    @Override
    public void updateEntity()
    {
        super.updateEntity();

        if (!this.worldObj.isRemote)
        {
            boolean updateInv = false;
            boolean flag = this.furnaceBurnTime > 0;

            if (this.furnaceBurnTime > 0)
            {
                --this.furnaceBurnTime;
            }

            if (this.furnaceBurnTime == 0 && this.canSmelt())
            {
                this.currentItemBurnTime = this.furnaceBurnTime = TileEntityFurnace.getItemBurnTime(this.containingItems[0]);

                if (this.furnaceBurnTime > 0)
                {
                    updateInv = true;

                    if (this.containingItems[0] != null)
                    {
                        --this.containingItems[0].stackSize;

                        if (this.containingItems[0].stackSize == 0)
                        {
                            this.containingItems[0] = this.containingItems[0].getItem().getContainerItemStack(this.containingItems[0]);
                        }
                    }
                }
            }

            if (this.furnaceBurnTime > 0 && this.canSmelt())
            {
                ++this.processTicks;

                if (this.processTicks % 40 == 0 && this.processTicks > GCCoreTileEntityIngotCompressor.PROCESS_TIME_REQUIRED / 2)
                {
                    this.worldObj.playSoundEffect(this.xCoord, this.yCoord, this.zCoord, "random.anvil_land", 0.2F, 0.5F);
                }

                if (this.processTicks == GCCoreTileEntityIngotCompressor.PROCESS_TIME_REQUIRED)
                {
                    this.processTicks = 0;
                    this.smeltItem();
                    updateInv = true;
                }
            }
            else
            {
                this.processTicks = 0;
            }

            if (flag != this.furnaceBurnTime > 0)
            {
                updateInv = true;
            }

            if (this.ticks % 3 == 0)
            {
                for (EntityPlayer player : this.playersUsing)
                {
                    PacketDispatcher.sendPacketToPlayer(this.getDescriptionPacket(), (Player) player);
                }
            }

            if (updateInv)
            {
                this.onInventoryChanged();
            }
        }

        if (this.ticks >= Long.MAX_VALUE)
        {
            this.ticks = 0;
        }

        this.ticks++;
    }

    @Override
    public Packet getDescriptionPacket()
    {
        return PacketManager.getPacket(GalacticraftCore.CHANNELENTITIES, this, this.processTicks, this.currentItemBurnTime, this.furnaceBurnTime);
    }

    @Override
    public void handlePacketData(INetworkManager network, int type, Packet250CustomPayload packet, EntityPlayer player, ByteArrayDataInput dataStream)
    {
        try
        {
            this.processTicks = dataStream.readInt();
            this.currentItemBurnTime = dataStream.readInt();
            this.furnaceBurnTime = dataStream.readInt();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void openChest()
    {
    }

    @Override
    public void closeChest()
    {
    }

    public void updateInput()
    {
        this.producingStack = CompressorRecipes.findMatchingRecipe(this.compressingCraftMatrix, this.worldObj);
    }

    private boolean canSmelt()
    {
        ItemStack itemstack = this.producingStack;
        if (itemstack == null)
        {
            return false;
        }
        if (this.containingItems[1] == null)
        {
            return true;
        }
        if (!this.containingItems[1].isItemEqual(itemstack))
        {
            return false;
        }
        int result = this.containingItems[1].stackSize + itemstack.stackSize;
        return result <= this.getInventoryStackLimit() && result <= itemstack.getMaxStackSize();
    }

    public void smeltItem()
    {
        if (this.canSmelt())
        {
            ItemStack resultItemStack = this.producingStack;

            if (this.containingItems[1] == null)
            {
                this.containingItems[1] = resultItemStack.copy();
            }
            else if (this.containingItems[1].isItemEqual(resultItemStack))
            {
                if (this.containingItems[1].stackSize + resultItemStack.stackSize > 64)
                {
                    for (int i = 0; i < this.containingItems[1].stackSize + resultItemStack.stackSize - 64; i++)
                    {
                        float var = 0.7F;
                        double dx = this.worldObj.rand.nextFloat() * var + (1.0F - var) * 0.5D;
                        double dy = this.worldObj.rand.nextFloat() * var + (1.0F - var) * 0.5D;
                        double dz = this.worldObj.rand.nextFloat() * var + (1.0F - var) * 0.5D;
                        EntityItem entityitem = new EntityItem(this.worldObj, this.xCoord + dx, this.yCoord + dy, this.zCoord + dz, new ItemStack(resultItemStack.getItem(), 1, resultItemStack.getItemDamage()));

                        entityitem.delayBeforeCanPickup = 10;

                        this.worldObj.spawnEntityInWorld(entityitem);
                    }
                }

                this.containingItems[1].stackSize += resultItemStack.stackSize;
            }

            for (int i = 0; i < this.compressingCraftMatrix.getSizeInventory(); i++)
            {
                this.compressingCraftMatrix.decrStackSize(i, 1);
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound par1NBTTagCompound)
    {
        super.readFromNBT(par1NBTTagCompound);
        this.processTicks = par1NBTTagCompound.getInteger("smeltingTicks");
        NBTTagList var2 = par1NBTTagCompound.getTagList("Items");
        this.containingItems = new ItemStack[this.getSizeInventory() - this.compressingCraftMatrix.getSizeInventory()];

        for (int var3 = 0; var3 < var2.tagCount(); ++var3)
        {
            NBTTagCompound var4 = (NBTTagCompound) var2.tagAt(var3);
            byte var5 = var4.getByte("Slot");

            if (var5 >= 0 && var5 < this.containingItems.length)
            {
                this.containingItems[var5] = ItemStack.loadItemStackFromNBT(var4);
            }
            else if (var5 < this.containingItems.length + this.compressingCraftMatrix.getSizeInventory())
            {
                this.compressingCraftMatrix.setInventorySlotContents(var5 - this.containingItems.length, ItemStack.loadItemStackFromNBT(var4));
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound par1NBTTagCompound)
    {
        super.writeToNBT(par1NBTTagCompound);
        par1NBTTagCompound.setInteger("smeltingTicks", this.processTicks);
        NBTTagList var2 = new NBTTagList();
        int var3;

        for (var3 = 0; var3 < this.containingItems.length; ++var3)
        {
            if (this.containingItems[var3] != null)
            {
                NBTTagCompound var4 = new NBTTagCompound();
                var4.setByte("Slot", (byte) var3);
                this.containingItems[var3].writeToNBT(var4);
                var2.appendTag(var4);
            }
        }

        for (var3 = 0; var3 < this.compressingCraftMatrix.getSizeInventory(); ++var3)
        {
            if (this.compressingCraftMatrix.getStackInSlot(var3) != null)
            {
                NBTTagCompound var4 = new NBTTagCompound();
                var4.setByte("Slot", (byte) (var3 + this.containingItems.length));
                this.compressingCraftMatrix.getStackInSlot(var3).writeToNBT(var4);
                var2.appendTag(var4);
            }
        }

        par1NBTTagCompound.setTag("Items", var2);
    }

    @Override
    public int getSizeInventory()
    {
        return this.containingItems.length + this.compressingCraftMatrix.getSizeInventory();
    }

    @Override
    public ItemStack getStackInSlot(int par1)
    {
        if (par1 >= this.containingItems.length)
        {
            return this.compressingCraftMatrix.getStackInSlot(par1 - this.containingItems.length);
        }

        return this.containingItems[par1];
    }

    @Override
    public ItemStack decrStackSize(int par1, int par2)
    {
        if (par1 >= this.containingItems.length)
        {
            return this.compressingCraftMatrix.decrStackSize(par1 - this.containingItems.length, par2);
        }

        if (this.containingItems[par1] != null)
        {
            ItemStack var3;

            if (this.containingItems[par1].stackSize <= par2)
            {
                var3 = this.containingItems[par1];
                this.containingItems[par1] = null;
                return var3;
            }
            else
            {
                var3 = this.containingItems[par1].splitStack(par2);

                if (this.containingItems[par1].stackSize == 0)
                {
                    this.containingItems[par1] = null;
                }

                return var3;
            }
        }
        else
        {
            return null;
        }
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int par1)
    {
        if (par1 >= this.containingItems.length)
        {
            return this.compressingCraftMatrix.getStackInSlotOnClosing(par1 - this.containingItems.length);
        }

        if (this.containingItems[par1] != null)
        {
            ItemStack var2 = this.containingItems[par1];
            this.containingItems[par1] = null;
            return var2;
        }
        else
        {
            return null;
        }
    }

    @Override
    public void setInventorySlotContents(int par1, ItemStack par2ItemStack)
    {
        if (par1 >= this.containingItems.length)
        {
            this.compressingCraftMatrix.setInventorySlotContents(par1 - this.containingItems.length, par2ItemStack);
        }
        else
        {
            this.containingItems[par1] = par2ItemStack;

            if (par2ItemStack != null && par2ItemStack.stackSize > this.getInventoryStackLimit())
            {
                par2ItemStack.stackSize = this.getInventoryStackLimit();
            }
        }
    }

    @Override
    public String getInvName()
    {
        return StatCollector.translateToLocal("tile.machine.3.name");
    }

    @Override
    public int getInventoryStackLimit()
    {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer par1EntityPlayer)
    {
        return this.worldObj.getBlockTileEntity(this.xCoord, this.yCoord, this.zCoord) != this ? false : par1EntityPlayer.getDistanceSq(this.xCoord + 0.5D, this.yCoord + 0.5D, this.zCoord + 0.5D) <= 64.0D;
    }

    @Override
    public boolean isInvNameLocalized()
    {
        return true;
    }

    @Override
    public boolean isItemValidForSlot(int slotID, ItemStack itemStack)
    {
        return slotID == 1 ? FurnaceRecipes.smelting().getSmeltingResult(itemStack) != null : slotID == 0 ? itemStack.getItem() instanceof IItemElectric : false;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side)
    {
        return side == 0 ? new int[] { 2 } : side == 1 ? new int[] { 0, 1 } : new int[] { 0 };
    }

    @Override
    public boolean canInsertItem(int slotID, ItemStack par2ItemStack, int par3)
    {
        return this.isItemValidForSlot(slotID, par2ItemStack);
    }

    @Override
    public boolean canExtractItem(int slotID, ItemStack par2ItemStack, int par3)
    {
        return slotID == 2;
    }
}
