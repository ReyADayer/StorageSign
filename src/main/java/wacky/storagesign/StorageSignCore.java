package wacky.storagesign;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionData;

public class StorageSignCore extends JavaPlugin implements Listener{

	FileConfiguration config;

	@Override
	public void onEnable() {
		config = this.getConfig();
		config.options().copyDefaults(true);
		config.options().header("StorageSign Configuration");
		this.saveConfig();

		ShapedRecipe storageSignRecipe = new ShapedRecipe(StorageSign.emptySign());
		storageSignRecipe.shape("CCC","CSC","CHC");
		storageSignRecipe.setIngredient('C', Material.CHEST);
		storageSignRecipe.setIngredient('S', Material.SIGN);
		if (config.getBoolean("hardrecipe")) storageSignRecipe.setIngredient('H', Material.ENDER_CHEST);
		else storageSignRecipe.setIngredient('H', Material.CHEST);
		getServer().addRecipe(storageSignRecipe);

		getServer().getPluginManager().registerEvents(this, this);
		if(config.getBoolean("no-bud")) new SignPhysicsEvent(this);
	}

	@Override
	public void onDisable(){}

	public boolean isStorageSign(ItemStack item) {
		if (item == null) return false;
		if (item.getType() != Material.SIGN) return false;
		if (!item.getItemMeta().hasDisplayName()) return false;
		if (!item.getItemMeta().getDisplayName().matches("StorageSign")) return false;
		return item.getItemMeta().hasLore();
	}

	public boolean isStorageSign(Block block) {
		if (block.getType() == Material.SIGN_POST || block.getType() == Material.WALL_SIGN) {
			Sign sign = (Sign) block.getState();
			if (sign.getLine(0).matches("StorageSign")) return true;
		}
		return false;
	}

	public boolean isHorseEgg(ItemStack item){
		if(item.getType() != Material.MONSTER_EGG) return false;
		if(item.getItemMeta().hasLore()) return true;
		return false;
	}


	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		Block block;
		//手持ちがブロックだと叩いた看板を取得できないことがあるとか
		if (event.isCancelled() && event.getAction() == Action.RIGHT_CLICK_AIR) {
			try {
				block = player.getTargetBlock((Set) null, 3);
			} catch (IllegalStateException ex) {
				return;
			}
		} else {
			block = event.getClickedBlock();
		}
		if (block == null) return;
		if(player.getGameMode() == GameMode.SPECTATOR) return;
		if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {

			if (!isStorageSign(block)) return;
			if(event.getHand() == EquipmentSlot.OFF_HAND) return;//一応
			event.setUseItemInHand(Result.DENY);
			event.setUseInteractedBlock(Result.DENY);
			if (!player.hasPermission("storagesign.use")) {
				player.sendMessage(ChatColor.RED + config.getString("no-permisson"));
				event.setCancelled(true);
				return;
			}
			Sign sign = (Sign) block.getState();
			StorageSign storageSign = new StorageSign(sign);
			ItemStack itemMainHand = event.getItem();
			Material mat;


			//アイテム登録
			if (storageSign.getMaterial() == null || storageSign.getMaterial() == Material.AIR) {
				mat = itemMainHand.getType();
				if (mat == Material.AIR) return;
				else if (isStorageSign(itemMainHand)) storageSign.setMaterial(Material.PORTAL);
				else if (isHorseEgg(itemMainHand)){
					storageSign.setMaterial(Material.PORTAL);
					storageSign.setDamage((short) 1);
				}
				else if (mat == Material.POTION || mat == Material.SPLASH_POTION || mat == Material.LINGERING_POTION)
				{
					storageSign.setMaterial(mat);
					PotionMeta potionMeta = (PotionMeta)itemMainHand.getItemMeta();
					PotionData potion = potionMeta.getBasePotionData();
					if(potion.isExtended()) storageSign.setDamage((short) 1);
					if(potion.isUpgraded()) storageSign.setDamage((short) 2);
					storageSign.setPotion(potion.getType());
				}
				else if (mat == Material.ENCHANTED_BOOK)
				{
					EnchantmentStorageMeta enchantMeta = (EnchantmentStorageMeta)itemMainHand.getItemMeta();
					if(enchantMeta.getStoredEnchants().size() == 1) {
						Enchantment ench = enchantMeta.getStoredEnchants().keySet().toArray(new Enchantment[0])[0];
						storageSign.setMaterial(mat);
						storageSign.setDamage((short) enchantMeta.getStoredEnchantLevel(ench));
						storageSign.setEnchant(ench);
					}
				}
				else
				{
					storageSign.setMaterial(mat);
					storageSign.setDamage(itemMainHand.getDurability());
				}

				for (int i=0; i<4; i++) sign.setLine(i, storageSign.getSigntext(i));
				sign.update();
				return;
			}

			if (isStorageSign(itemMainHand)) {
				//看板合成
				StorageSign itemSign = new StorageSign(itemMainHand);
				if (storageSign.getContents().isSimilar(itemSign.getContents()) && config.getBoolean("manual-import")) {
					storageSign.addAmount(itemSign.getAmount() * itemSign.getStackSize());
					itemSign.setAmount(0);
					player.getInventory().setItemInMainHand(itemSign.getStorageSign());
				}//空看板収納
				else if (itemSign.isEmpty() && storageSign.getMaterial() == Material.PORTAL && storageSign.getDamage() == 0 && config.getBoolean("manual-import")) {
					if (player.isSneaking()) {
						storageSign.addAmount(itemMainHand.getAmount());
						player.getInventory().clear(player.getInventory().getHeldItemSlot());
					} else for (int i=0; i<player.getInventory().getSize(); i++) {
						ItemStack item = player.getInventory().getItem(i);
						if (storageSign.isSimilar(item)) {
							storageSign.addAmount(item.getAmount());
							player.getInventory().clear(i);
						}
					}
				}//中身分割機能
				else if (itemSign.isEmpty() && storageSign.getAmount() > itemMainHand.getAmount() && config.getBoolean("manual-export")) {
					itemSign.setMaterial(storageSign.getMaterial());
					itemSign.setDamage(storageSign.getDamage());
					itemSign.setEnchant(storageSign.getEnchant());
					itemSign.setPotion(storageSign.getPotion());

					int limit = config.getInt("divide-limit");

					if (limit > 0 && storageSign.getAmount() > limit * (itemSign.getStackSize() + 1)) itemSign.setAmount(limit);
					else itemSign.setAmount(storageSign.getAmount() / (itemSign.getStackSize() + 1));
					player.getInventory().setItemInMainHand(itemSign.getStorageSign());
					storageSign.setAmount(storageSign.getAmount() - (itemSign.getStackSize() * itemSign.getAmount()));//余りは看板に引き受けてもらう
				}
				for (int i=0; i<4; i++) sign.setLine(i, storageSign.getSigntext(i));
				sign.update();
				return;
			}

            //ここから搬入
            if (storageSign.isSimilar(itemMainHand)) {
                if (!config.getBoolean("manual-import")) return;
                if (player.isSneaking()) {
                    storageSign.addAmount(itemMainHand.getAmount());
                    player.getInventory().clear(player.getInventory().getHeldItemSlot());
                } else for (int i=0; i<player.getInventory().getSize(); i++) {
                    ItemStack item = player.getInventory().getItem(i);
                    if (storageSign.isSimilar(item)) {
                        storageSign.addAmount(item.getAmount());
                        player.getInventory().clear(i);
                    }
                }

                player.updateInventory();
            } else if (config.getBoolean("manual-export"))/*放出*/ {
                if (storageSign.isEmpty()) return;
                ItemStack item = storageSign.getContents();

                int max = item.getMaxStackSize();

                if (player.isSneaking()) storageSign.addAmount(-1);
                else if (storageSign.getAmount() > max) {
                    item.setAmount(max);
                    storageSign.addAmount(-max);
                } else {
                    item.setAmount(storageSign.getAmount());
                    storageSign.setAmount(0);
                }

                Location loc = player.getLocation();
                loc.setY(loc.getY() + 0.5);
                player.getWorld().dropItem(loc, item);
            }

            for (int i=0; i<4; i++) sign.setLine(i, storageSign.getSigntext(i));
            sign.update();
        }
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        if (event.isCancelled())return;
        Sign sign = (Sign) event.getBlock().getState();

        if (sign.getLine(0).matches("StorageSign"))/*変更拒否*/ {
            event.setLine(0, sign.getLine(0));
            event.setLine(1, sign.getLine(1));
            event.setLine(2, sign.getLine(2));
            event.setLine(3, sign.getLine(3));
            sign.update();
        } else if (event.getLine(0).equalsIgnoreCase("storagesign"))/*書き込んで生成禁止*/ {
            if (event.getPlayer().hasPermission("storagesign.create")) {
                event.setLine(0, "StorageSign");
                sign.update();
            } else {
                event.getPlayer().sendMessage(ChatColor.RED + config.getString("no-permisson"));
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Block block = event.getBlock();
        Map<Location, StorageSign> breakSignMap = new HashMap<>();
        if (isStorageSign(block)) breakSignMap.put(block.getLocation(), new StorageSign((Sign)block.getState()));

        for (int i=0; i<5; i++) {
            int[] x = {0, 0, 0,-1, 1};
            int[] y = {1, 0, 0, 0, 0};
            int[] z = {0,-1, 1, 0, 0};
            block = event.getBlock().getRelative(x[i], y[i], z[i]);
            if (i==0 && block.getType() == Material.SIGN_POST && isStorageSign(block)) breakSignMap.put(block.getLocation(), new StorageSign((Sign)block.getState()));
            else if(block.getType() == Material.WALL_SIGN && block.getData() == i+1 && isStorageSign(block)) breakSignMap.put(block.getLocation(), new StorageSign((Sign)block.getState()));
        }
        if (breakSignMap.isEmpty()) return;
        if (!event.getPlayer().hasPermission("storagesign.break")) {
            event.getPlayer().sendMessage(ChatColor.RED + config.getString("no-permisson"));
            event.setCancelled(true);
            return;
        }

        for (Location loc : breakSignMap.keySet()) {
            StorageSign sign = breakSignMap.get(loc);
            Location loc2 = loc;
            loc2.add(0.5, 0.5, 0.5);//中心にドロップさせる
            loc.getWorld().dropItem(loc2, sign.getStorageSign());
            loc.getBlock().setType(Material.AIR);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled() || !isStorageSign(event.getItemInHand())) return;
        if (!event.getPlayer().hasPermission("storagesign.place")) {
            event.getPlayer().sendMessage(ChatColor.RED + config.getString("no-permisson"));
            event.setCancelled(true);
            return;
        }
        StorageSign storageSign = new StorageSign(event.getItemInHand());
        Sign sign = (Sign)event.getBlock().getState();
        for (int i=0; i<4; i++) sign.setLine(i, storageSign.getSigntext(i));
        sign.update();
        event.getPlayer().closeInventory();
    }

    @EventHandler
    public void onItemMove(InventoryMoveItemEvent event) {
        if (event.isCancelled()) return;
        BlockState[] blockInventory =new BlockState[2];
        Boolean flag = false;
        Sign sign = null;
        StorageSign storageSign = null;

        if (config.getBoolean("auto-import")) {
            if (event.getDestination().getHolder() instanceof Minecart);//何もしない
            else if (event.getDestination().getHolder() instanceof DoubleChest) {
                DoubleChest lc = (DoubleChest)event.getDestination().getHolder();
                blockInventory[0] = (BlockState) lc.getLeftSide();
                blockInventory[1] = (BlockState) lc.getRightSide();
            } else {
                blockInventory[0] = (BlockState) event.getDestination().getHolder();
            }

            importLoop:
                for (int j=0; j<2; j++) {
                    if (blockInventory[j] == null) break;
                    for (int i=0; i<5; i++) {
                        int[] x = {0, 0, 0,-1, 1};
                        int[] y = {1, 0, 0, 0, 0};
                        int[] z = {0,-1, 1, 0, 0};
                        Block block = blockInventory[j].getBlock().getRelative(x[i], y[i], z[i]);
                        if (i==0 && block.getType() == Material.SIGN_POST && isStorageSign(block)) {
                            sign = (Sign) block.getState();
                            storageSign = new StorageSign(sign);
                            if (storageSign.isSimilar(event.getItem())) {
                                flag = true;
                                break importLoop;
                            }
                        } else if (i != 0 && block.getType() == Material.WALL_SIGN && block.getData() == i+1 && isStorageSign(block)) {
                            sign = (Sign) block.getState();
                            storageSign = new StorageSign(sign);
                            if (storageSign.isSimilar(event.getItem())) {
                                flag = true;
                                break importLoop;
                            }
                        }
                    }
                }
            //搬入先が見つかった(搬入するとは言ってない)
            if (flag) importSign(sign, storageSign, event.getItem(), event.getDestination());
        }

        //搬出用にリセット
        if (config.getBoolean("auto-export")) {
            blockInventory[0] = null;
            blockInventory[1] = null;
            flag = false;
            if (event.getSource().getHolder() instanceof Minecart);
            else if (event.getSource().getHolder() instanceof DoubleChest) {
                DoubleChest lc = (DoubleChest)event.getSource().getHolder();
                blockInventory[0] = (BlockState) lc.getLeftSide();
                blockInventory[1] = (BlockState) lc.getRightSide();
            } else {
                blockInventory[0] = (BlockState) event.getSource().getHolder();
            }

            exportLoop:
                for (int j=0; j<2; j++) {
                    if (blockInventory[j] == null) break;
                    for (int i=0; i<5; i++) {
                        int[] x = {0, 0, 0,-1, 1};
                        int[] y = {1, 0, 0, 0, 0};
                        int[] z = {0,-1, 1, 0, 0};
                        Block block = blockInventory[j].getBlock().getRelative(x[i], y[i], z[i]);
                        if (i==0 && block.getType() == Material.SIGN_POST && isStorageSign(block)) {
                            sign = (Sign) block.getState();
                            storageSign = new StorageSign(sign);
                            if (storageSign.isSimilar(event.getItem())) {
                                flag = true;
                                break exportLoop;
                            }
                        } else if (i != 0 && block.getType() == Material.WALL_SIGN && block.getData() == i+1 && isStorageSign(block)) {
                            sign = (Sign) block.getState();
                            storageSign = new StorageSign(sign);
                            if (storageSign.isSimilar(event.getItem())) {
                                flag = true;
                                break exportLoop;
                            }
                        }
                    }
                }
            if (flag) exportSign(sign, storageSign, event.getItem(), event.getSource(), event.getDestination());
        }
    }

    private void importSign(Sign sign, StorageSign storageSign, ItemStack item, Inventory inv) {
        //搬入　条件　1スタック以上アイテムが入っている
        if (inv.containsAtLeast(item, item.getMaxStackSize())) {
            inv.removeItem(item);
            storageSign.addAmount(item.getAmount());
        }
        for (int i=0; i<4; i++) sign.setLine(i, storageSign.getSigntext(i));
        sign.update();
    }

    private void exportSign(Sign sign, StorageSign storageSign, ItemStack item, Inventory inv, Inventory dest) {
        if (!inv.containsAtLeast(item, item.getMaxStackSize()) && storageSign.getAmount() >= item.getAmount()) {
        	int stacks = 0;
        	int amount = 0;
        	ItemStack[] contents = dest.getContents();
        	for(int i=0; i< contents.length; i++){
        		if(item.isSimilar(contents[i])){
        			stacks++;
        			amount += contents[i].getAmount();
        		}
        	}
        	if(amount == stacks * item.getMaxStackSize() && dest.firstEmpty() == -1) return;
            inv.addItem(item);
            storageSign.addAmount(-item.getAmount());
        }
        for (int i=0; i<4; i++) sign.setLine(i, storageSign.getSigntext(i));
        sign.update();
    }

    @EventHandler
    public void onPlayerCraft(CraftItemEvent event) {
        if (isStorageSign(event.getCurrentItem()) && !event.getWhoClicked().hasPermission("storagesign.craft")) {
            ((CommandSender) event.getWhoClicked()).sendMessage(ChatColor.RED + config.getString("no-permisson"));
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (event.isCancelled() || !config.getBoolean("auto-import")) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BlockState) {
            Sign sign = null;
            StorageSign storageSign = null;
            boolean flag = false;
            for (int i=0; i<5; i++) {
                int[] x = {0, 0, 0,-1, 1};
                int[] y = {1, 0, 0, 0, 0};
                int[] z = {0,-1, 1, 0, 0};
                Block block = ((BlockState)holder).getBlock().getRelative(x[i], y[i], z[i]);
                if (i==0 && block.getType() == Material.SIGN_POST && isStorageSign(block)) {
                    sign = (Sign) block.getState();
                    storageSign = new StorageSign(sign);
                    if (storageSign.isSimilar(event.getItem().getItemStack())) {
                        flag = true;
                        break;
                    }
                } else if (i != 0 && block.getType() == Material.WALL_SIGN && block.getData() == i+1 && isStorageSign(block)) {
                    sign = (Sign) block.getState();
                    storageSign = new StorageSign(sign);
                    if (storageSign.isSimilar(event.getItem().getItemStack())) {
                        flag = true;
                        break;
                    }
                }
            }
            if (flag) importSign(sign, storageSign, event.getItem().getItemStack(), event.getInventory());
        }
    }

    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        if (event.isCancelled() || !config.getBoolean("autocollect")) return;
        Player player = event.getPlayer();
        PlayerInventory playerInv = player.getInventory();
        ItemStack item = event.getItem().getItemStack();
        StorageSign storagesign = null;
        //ここでは、エラーを出さずに無視する
        if(!player.hasPermission("storagesign.autocollect")) return;
        if(isStorageSign(playerInv.getItemInMainHand())){
        	storagesign = new StorageSign(playerInv.getItemInMainHand());
        	if(storagesign.getContents() != null){

        		if (storagesign.isSimilar(item) && playerInv.containsAtLeast(item, item.getMaxStackSize()) && storagesign.getStackSize() == 1) {
        			storagesign.addAmount(item.getAmount());

        			playerInv.removeItem(item);//1.9,10ではバグる？
        			playerInv.setItemInMainHand(storagesign.getStorageSign());
        			player.updateInventory();
        			//event.getItem().remove();
        			//player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.5f);
        			//event.setCancelled(true);
        			return;
        		}
        	}
        }if(isStorageSign(playerInv.getItemInOffHand())){//メインハンドで回収されなかった時
        	storagesign = new StorageSign(playerInv.getItemInOffHand());
        	if(storagesign.getContents() != null){

        		if (storagesign.isSimilar(item) && playerInv.containsAtLeast(item, item.getMaxStackSize()) && storagesign.getStackSize() == 1) {
        			storagesign.addAmount(item.getAmount());
        			playerInv.removeItem(item);
        			playerInv.setItemInOffHand(storagesign.getStorageSign());
        			player.updateInventory();
        			//event.getItem().remove();
        			//player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.5f);
        			//event.setCancelled(true);
        			return;
        		}
        	}
        }
    }
}