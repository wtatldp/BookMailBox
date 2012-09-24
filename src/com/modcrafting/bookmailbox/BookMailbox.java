package com.modcrafting.bookmailbox;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class BookMailbox extends JavaPlugin implements Listener{
	HashMap<String, Location> box = new HashMap<String, Location>();
	public void onDisable(){
		YamlConfiguration config = (YamlConfiguration) this.getConfig();
		for(String key: config.getKeys(false)){
			if(!box.containsKey(key)){
				config.set(key, null);
			}
		}
		this.saveConfig();
		box.clear();
	}
	public void onEnable(){
		this.getServer().getPluginManager().registerEvents(this, this);
		this.getDataFolder().mkdir();
		File actual = new File(getDataFolder(), "config.yml");
		if (!actual.exists()){
			try {
				actual.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}	
		}		
		loadMap();
	}
	private void loadMap() {
        this.reloadConfig();
		YamlConfiguration config = (YamlConfiguration) this.getConfig();
		for(String s : config.getKeys(false)){
			box.put(s, new Location(this.getServer().getWorld(config.getString(s+".World")), config.getDouble(s+".X"), config.getDouble(s+".Y"), config.getDouble(s+".Z")));
		}
		
	}
	@EventHandler
	public void onSignPlace(PlayerInteractEvent event){
		if(event.getAction().equals(Action.RIGHT_CLICK_BLOCK)){
			if(event.getClickedBlock().getState() instanceof Sign){
				Sign sign = (Sign) event.getClickedBlock().getState();
				if(sign.getLine(0).equalsIgnoreCase("[Mailbox]") && sign.getBlock().getRelative(BlockFace.DOWN).getType().equals(Material.CHEST)){
					if(box.containsKey(event.getPlayer().getName())){
						BlockState state = box.get(event.getPlayer().getName()).getBlock().getRelative(BlockFace.UP).getState();
						if(state instanceof Sign){
							Sign s = (Sign) state;
							s.setLine(0, "[Mailbox]");
							s.setLine(1, "");
							s.setLine(2, "");
							s.setLine(3, "");
							s.update();
						}
					}
					sign.setLine(0, ChatColor.DARK_BLUE+"[Mailbox]");
					sign.setLine(1, ChatColor.BLUE+event.getPlayer().getName());
					sign.setLine(3, ChatColor.DARK_GRAY+"0 of 27");
					sign.update();
					String name = event.getPlayer().getName();
					Location loc = sign.getBlock().getRelative(BlockFace.DOWN).getLocation();
					box.put(event.getPlayer().getName(), loc);
					YamlConfiguration config = (YamlConfiguration) this.getConfig();
					config.set(name+".World", loc.getWorld().getName());
					config.set(name+".X", loc.getX());
					config.set(name+".Y", loc.getY());
					config.set(name+".Z", loc.getZ());
					this.saveConfig();
				}
			}
			if(event.getClickedBlock().getType().equals(Material.CHEST)){
				if(event.getClickedBlock().getRelative(BlockFace.UP).getState() instanceof Sign){
					Sign sign = (Sign) event.getClickedBlock().getRelative(BlockFace.UP).getState();
					if(sign.getLine(0).contains("[Mailbox]")){
						if(sign.getLine(1).contains(event.getPlayer().getName())||event.getPlayer().hasPermission("bookmailbox.admin")){
							int count = 0;
							for(ItemStack item:((Chest)event.getClickedBlock().getState()).getInventory().getContents()){
								if(item!=null&&item.getType().equals(Material.WRITTEN_BOOK)) count = count+1;
							}
							sign.setLine(3, ChatColor.DARK_GRAY+String.valueOf(count)+" of 27");
							sign.update();
						}else{
							event.setCancelled(true);
							return;
						}
					}
				}
			}
		}
	}
	@EventHandler
	public void onBlockBreak(BlockBreakEvent event){
		for(BlockFace face: BlockFace.values()){
			if(box.containsValue(event.getBlock().getRelative(face))&&!box.containsKey(event.getPlayer().getName())){
				if(!event.getPlayer().hasPermission("bookmailbox.admin")) event.setCancelled(true);				
			}else{
				if(box.containsKey(event.getPlayer().getName())) box.remove(event.getPlayer());
			}
		}
			
	}
	public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
		if(sender instanceof Player){
			Player player = (Player) sender;
			if(args.length<1) return false;
			if(!player.hasPermission("bookmailbox.command")){
				sender.sendMessage(ChatColor.RED+"You do not have the required permissions.");
				return true;
			}
			OfflinePlayer t = this.getServer().getOfflinePlayer(args[0]);
			if(player.getItemInHand().getType().equals(Material.WRITTEN_BOOK)){
				if(t!=null){
					String rec = t.getName();
					if(box.containsKey(rec)){
						Location loc = box.get(rec);
						if(loc.getBlock().getState() instanceof Chest){
							Chest chest = (Chest) loc.getBlock().getState();
							BlockState s = loc.getBlock().getRelative(BlockFace.UP).getState();
							if(s instanceof Sign){
								Sign sign = (Sign) s;
								if(sign.getLine(0).contains("[Mailbox]")&&sign.getLine(1).contains(rec)){
									chest.getBlockInventory().addItem(player.getItemInHand());
									int count = 0;
									for(ItemStack item:chest.getInventory().getContents()){
										if(item!=null&&item.getType().equals(Material.WRITTEN_BOOK)) count = count+1;
									}
									sign.setLine(3, ChatColor.DARK_GRAY+String.valueOf(count)+" of 27");
									sign.update();
									player.setItemInHand(null);
									sender.sendMessage(ChatColor.AQUA+"Mail Sent.");
									if(t.isOnline()) t.getPlayer().sendMessage(ChatColor.LIGHT_PURPLE+"You've got mail");
								}else{
									box.remove(rec);
									sender.sendMessage(ChatColor.RED+"Mailbox not found.");
								}
							}else{
								box.remove(rec);
								sender.sendMessage(ChatColor.RED+"Mailbox not found.");
							}
						}else{
							box.remove(rec);
							sender.sendMessage(ChatColor.RED+"Mailbox not found.");
						}
					}else{
						sender.sendMessage(ChatColor.RED+"Mailbox not found.");
					}
				}else{
					sender.sendMessage(ChatColor.RED+"Player not found.");
				}
			}else{
				sender.sendMessage(ChatColor.RED+"You must have a book in your hand");
			}
			
		}
		return true;
	}
}
