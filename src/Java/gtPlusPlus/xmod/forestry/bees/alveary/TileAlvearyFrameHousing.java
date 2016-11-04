package gtPlusPlus.xmod.forestry.bees.alveary;

import java.util.*;

import forestry.api.apiculture.*;
import forestry.api.arboriculture.EnumGermlingType;
import forestry.api.genetics.*;
import forestry.api.multiblock.IAlvearyComponent;
import forestry.apiculture.AlvearyBeeModifier;
import forestry.apiculture.multiblock.MultiblockLogicAlveary;
import forestry.apiculture.network.packets.PacketActiveUpdate;
import forestry.apiculture.worldgen.*;
import forestry.core.inventory.IInventoryAdapter;
import forestry.core.inventory.wrappers.IInvSlot;
import forestry.core.inventory.wrappers.InventoryIterator;
import forestry.core.proxy.Proxies;
import forestry.core.tiles.IActivatable;
import forestry.core.utils.ItemStackUtil;
import gtPlusPlus.core.util.Utils;
import gtPlusPlus.xmod.forestry.bees.alveary.gui.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public class TileAlvearyFrameHousing
extends FR_TileAlveary
implements ISidedFrameWearingInventory, IActivatable, IAlvearyComponent.Active, IAlvearyComponent.BeeModifier, IAlvearyFrameHousing, IAlvearyComponent.BeeListener
{
	private final InventoryFrameHousing inventory;
	private final IBeeListener beeListener;
	private final Stack<ItemStack> pendingSpawns = new Stack<ItemStack>();
	private boolean active;

	public TileAlvearyFrameHousing()
	{
		super(FR_BlockAlveary.Type.FRAME);
		this.inventory = new InventoryFrameHousing(this);
		this.beeListener = new AlvearyFrameHousingBeeListener(this.inventory);

	}

	@Override
	public IInventoryAdapter getInternalInventory()
	{
		return this.inventory;
	}

	@Override
	public boolean allowsAutomation()
	{
		return true;
	}

	@Override
	public void updateServer(int tickCount)
	{

		if (getInternalInventory() == null) {
			return;
		}

		if (this.inventory.getStackInSlot(0) != null)
		{
			if (((MultiblockLogicAlveary)getMultiblockLogic()).getController().getBeekeepingLogic().canWork()){
				setActive(true);
				if (tickCount % 1000 == 0) {
					wearOutFrames(this, 1);
				}
			}
			else {
				Utils.LOG_INFO("Cannot work - Probably no queen alive.");
			}

		}
		else
		{
			setActive(false);
		}
		if (tickCount % 500 != 0) {
			return;
		}

	}

	@Override
	public void updateClient(int tickCount) {}

	private ItemStack getPrincessStack()
	{
		ItemStack princessStack = ((MultiblockLogicAlveary)getMultiblockLogic()).getController().getBeeInventory().getQueen();
		if (BeeManager.beeRoot.isMated(princessStack)) {
			return princessStack;
		}
		return null;
	}

	private int consumeInducerAndGetChance()
	{
		if (getInternalInventory() == null) {
			return 0;
		}
		for (Iterator<?> i$ = InventoryIterator.getIterable(getInternalInventory()).iterator(); i$.hasNext();)
		{
			IInvSlot slot = (IInvSlot)i$.next();
			ItemStack stack = slot.getStackInSlot();
			for (Map.Entry<ItemStack, Integer> entry : BeeManager.inducers.entrySet()) {
				if (ItemStackUtil.isIdenticalItem((ItemStack)entry.getKey(), stack))
				{
					slot.decreaseStackInSlot();
					return ((Integer)entry.getValue()).intValue();
				}
			}
		}
		IInvSlot slot;
		ItemStack stack;
		return 0;
	}

	private void trySpawnSwarm()
	{
		ItemStack toSpawn = (ItemStack)this.pendingSpawns.peek();
		HiveDescriptionSwarmer hiveDescription = new HiveDescriptionSwarmer(new ItemStack[] { toSpawn });
		Hive hive = new Hive(hiveDescription);

		int chunkX = (this.xCoord + this.worldObj.rand.nextInt(80) - 40) / 16;
		int chunkZ = (this.zCoord + this.worldObj.rand.nextInt(80) - 40) / 16;
		if (HiveDecorator.genHive(this.worldObj, this.worldObj.rand, chunkX, chunkZ, hive)) {
			this.pendingSpawns.pop();
		}
	}

	@Override
	protected void encodeDescriptionPacket(NBTTagCompound packetData)
	{
		super.encodeDescriptionPacket(packetData);
		packetData.setBoolean("Active", this.active);
	}

	@Override
	protected void decodeDescriptionPacket(NBTTagCompound packetData)
	{
		super.decodeDescriptionPacket(packetData);
		setActive(packetData.getBoolean("Active"));
	}

	@Override
	public int getIcon(int side)
	{
		if ((side == 0) || (side == 1)) {
			return 2;
		}
		if (this.active) {
			return 6;
		}
		return 5;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound)
	{
		super.readFromNBT(nbttagcompound);
		setActive(nbttagcompound.getBoolean("Active"));

		NBTTagList nbttaglist = nbttagcompound.getTagList("PendingSpawns", 10);
		for (int i = 0; i < nbttaglist.tagCount(); i++)
		{
			NBTTagCompound nbttagcompound1 = nbttaglist.getCompoundTagAt(i);
			this.pendingSpawns.add(ItemStack.loadItemStackFromNBT(nbttagcompound1));
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound)
	{
		super.writeToNBT(nbttagcompound);
		nbttagcompound.setBoolean("Active", this.active);

		NBTTagList nbttaglist = new NBTTagList();
		ItemStack[] offspring = (ItemStack[])this.pendingSpawns.toArray(new ItemStack[this.pendingSpawns.size()]);
		for (int i = 0; i < offspring.length; i++) {
			if (offspring[i] != null)
			{
				NBTTagCompound nbttagcompound1 = new NBTTagCompound();
				nbttagcompound1.setByte("Slot", (byte)i);
				offspring[i].writeToNBT(nbttagcompound1);
				nbttaglist.appendTag(nbttagcompound1);
			}
		}
		nbttagcompound.setTag("PendingSpawns", nbttaglist);
	}

	@Override
	public boolean isActive()
	{
		return this.active;
	}

	@Override
	public void setActive(boolean active)
	{
		if (this.active == active) {
			return;
		}
		this.active = active;
		if (!this.worldObj.isRemote) {
			Proxies.net.sendNetworkPacket(new PacketActiveUpdate(this), this.worldObj);
		}
	}

	@Override
	public Object getGui(EntityPlayer player, int data)
	{
		return new GUI_FrameHousing(this, player);
	}

	@Override
	public Object getContainer(EntityPlayer player, int data)
	{
		return new CONTAINER_FrameHousing(this, player);
	}

	private final IBeeModifier beeModifier = new AlvearyBeeModifier();
	//private final IBeeListener beeListener = new AlvearyBeeListener(this);	
	private final Iterable<IBeeListener> beeListenerList = ((MultiblockLogicAlveary)getMultiblockLogic()).getController().getBeeListeners();	

	@Override
	public Collection<IBeeModifier> getBeeModifiers()
	{
		List<IBeeModifier> beeModifiers = new ArrayList<IBeeModifier>();

		beeModifiers.add(this.beeModifier);
		for (IHiveFrame frame : getFrames(this.inventory)) {
			beeModifiers.add(frame.getBeeModifier());
		}
		return beeModifiers;
	}

	public Collection<IHiveFrame> getFrames(IInventory inventory)
	{
		Collection<IHiveFrame> hiveFrames = new ArrayList<IHiveFrame>(inventory.getSizeInventory());
		for (int i = 0; i < inventory.getSizeInventory(); i++)
		{
			ItemStack stackInSlot = getStackInSlot(i);
			if (stackInSlot != null)
			{
				Item itemInSlot = stackInSlot.getItem();
				if ((itemInSlot instanceof IHiveFrame)) {
					hiveFrames.add((IHiveFrame)itemInSlot);
				}
			}
		}
		return hiveFrames;
	}

	@Override
	public IBeeModifier getBeeModifier() {
		List<IBeeModifier> beeModifiers = new ArrayList<IBeeModifier>();

		//beeModifiers.add(this.beeModifier);
		for (IHiveFrame frame : getFrames(this.inventory)) {
			beeModifiers.add(frame.getBeeModifier());
		}
		return beeModifiers.get(0);
	}

	private ItemStack getQueenStack()
	{
		ItemStack queenStack = ((MultiblockLogicAlveary)getMultiblockLogic()).getController().getBeeInventory().getQueen();
		return queenStack;
	}

	@Override
	public void wearOutFrames(IBeeHousing beeHousing, int amount)
	{
		IBeekeepingMode beekeepingMode = BeeManager.beeRoot.getBeekeepingMode(beeHousing.getWorld());
		int wear = Math.round(amount * beekeepingMode.getWearModifier());
		for (int i = 0; i < this.inventory.getSizeInventory(); i++)
		{
			ItemStack hiveFrameStack = getStackInSlot(i);
			if (hiveFrameStack != null)
			{
				Item hiveFrameItem = hiveFrameStack.getItem();
				if ((hiveFrameItem instanceof IHiveFrame))
				{
					IHiveFrame hiveFrame = (IHiveFrame)hiveFrameItem;
					Utils.LOG_INFO("Wearing out frame by "+amount);
					ItemStack queenStack = getQueenStack();
					IBee queen = BeeManager.beeRoot.getMember(queenStack);
					ItemStack usedFrame = hiveFrame.frameUsed(beeHousing, hiveFrameStack, queen, wear);

					//((MultiblockLogicAlveary)getMultiblockLogic()).getController().getBeeListeners().
					
					setInventorySlotContents(i, usedFrame);
				}
			}
		}
	}

	@Override
	public InventoryFrameHousing getAlvearyInventory() {
		return inventory;
	}

	@Override
	public IBeeListener getBeeListener() {
		return beeListener;
	}
	
	static class AlvearyFrameHousingBeeListener
    extends DefaultBeeListener
  {
    private final InventoryFrameHousing inventory;
    
    public AlvearyFrameHousingBeeListener(InventoryFrameHousing inventory)
    {
      this.inventory = inventory;
    }
    
    @Override
	public boolean onPollenRetrieved(IIndividual pollen)
    {
      /*if (!((Object) this.inventory).canStorePollen()) {
        return false;
      }*/
      ISpeciesRoot speciesRoot = AlleleManager.alleleRegistry.getSpeciesRoot(pollen.getClass());
      
      ItemStack pollenStack = speciesRoot.getMemberStack(pollen, EnumGermlingType.POLLEN.ordinal());
      if (pollenStack != null)
      {
       // ((Object) this.inventory).storePollenStack(pollenStack);
        return true;
      }
      return false;
    }
  }
	
	
	
}
