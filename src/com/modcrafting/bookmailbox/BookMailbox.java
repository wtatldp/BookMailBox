package com.modcrafting.bookmailbox;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Logger;
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

public class BookMailbox extends JavaPlugin implements Listener
{
	private static HashMap<String, Location>
		MailChests = new HashMap<String, Location>();

	/**
	 * Called by Bukkit when this plugin is disabled
	 */
	public void onDisable ()
	{
		this.saveMailchestsYml();
		MailChests.clear();
		return;
	}


	/**
	 * Called by Bukkit when this plugin is enabled
	 */
	public void onEnable ()
	{
		File plugin_folder = this.getDataFolder();
		// isn't this a Bukkit option?
		if (!plugin_folder.exists())
		{
			if (! plugin_folder.mkdir())
			{
				this.getLogger().warning("Couldn't create plugin folder '<plugin_folder>'."); // FIXME
			}
		}

		this.loadMailchestsYml();
		this.getServer().getPluginManager().registerEvents(this, this);
		return;
	}


	/**
	 * Event Handler
	 * <p>
	 * The main event handler for interaction with physical mailbox components.
	 *
	 * @param PlayerInteraction
	 *		the PlayerInteractEvent as passed by Bukkit
	 */
	@EventHandler
	public void onPlayerInteract (
		PlayerInteractEvent PlayerInteraction)
	{
		if (! PlayerInteraction.getAction().equals(Action.RIGHT_CLICK_BLOCK))
		{
			return;
		}

		BlockState Candidate = PlayerInteraction.getClickedBlock().getState();
		if (Candidate instanceof Sign)
		{
			this.onRightClickSign(PlayerInteraction, (Sign) Candidate);
		}
		else if (Candidate.getType().equals(Material.CHEST))
		{
			this.onRightClickChest(PlayerInteraction, (Chest) Candidate);
		}
		return;
	}


	/**
	 * Event Handler, delegated
	 * <p>
	 * If a player clicks on a plain [Mailbox] sign, it will be converted into
	 * a magic [Mailbox] sign managed by this plugin.
	 *
	 * @param PlayerInteraction
	 *		the PlayerInteractEvent as passed by Bukkit
	 * @param ClickedSign
	 *		the clicked sign (BlockState)
	 */
	public void onRightClickSign (
		PlayerInteractEvent PlayerInteraction,
		Sign ClickedSign)
	{
		// sign magic must be present
		if (! ClickedSign.getLine(0).equalsIgnoreCase("[Mailbox]"))
		{
			return;
		}
		// a chest must be below the magic sign
		BlockState MailChest = ClickedSign.getBlock().getRelative(BlockFace.DOWN).getState();
		if (! MailChest.getType().equals(Material.CHEST))
		{
			ClickedSign.setLine(1, "Put a chest");
			ClickedSign.setLine(2, "below this sign");
			ClickedSign.setLine(3, "R-click again.");
			ClickedSign.update();
			return;
		}

		String PlayerId = PlayerInteraction.getPlayer().getUniqueId().toString();
		String PlayerName = PlayerInteraction.getPlayer().getPlayerListName();

		// deactivate a potentially old sign
		// FIXME: there should be a warning about this situation, but not in chat
		if (MailChests.containsKey(PlayerId))
		{
			BlockState Candidate = MailChests.get(PlayerId)
				.getBlock().getRelative(BlockFace.UP).getState();
			if (Candidate instanceof Sign)
			{
				Sign FormerSign = (Sign) Candidate;
				// add a hint "now at <location>"
				FormerSign.setLine(1, "of");
				FormerSign.setLine(2, PlayerName);
				FormerSign.setLine(3, "moved.");
				FormerSign.update();
			}
		}

		ClickedSign.setLine(0, ChatColor.DARK_BLUE + "[Mailbox]");
		// .getPlayerListName() returns 16 characters max.
		ClickedSign.setLine(1, ChatColor.DARK_GREEN + PlayerName);
		ClickedSign.setLine(2, "");
		ClickedSign.setLine(3, ChatColor.DARK_GRAY+"0 of 27");
		ClickedSign.update();

		Location ChestXYZ = MailChest.getLocation();
		MailChests.put(PlayerId, ChestXYZ);

		this.saveMailchestsYml(); // FIXME: periodically save MailChests instead
		return;
	}


	/**
	 * Event Handler, delegated
	 * <p>
	 * Two checks, positive and negative. One, checks whether player is allowed
	 * to open a chest. Two, checks whether a player isn't allowed, because it's
	 * the chest of somebody else.
	 *
	 * @param PlayerInteraction
	 *		the PlayerInteractEvent as passed by Bukkit
	 * @param ClickedChest
	 *		the clicked chest (BlockState)
	 */
	public void onRightClickChest (
		PlayerInteractEvent PlayerInteraction,
		Chest ClickedChest)
	{
		String PlayerId = PlayerInteraction.getPlayer().getUniqueId().toString();

		Location ChestFoundXYZ = ClickedChest.getLocation();
		Location ChestKnownXYZ = MailChests.get(PlayerId);

		// player must be owner of mailbox or administrator
		if (PlayerInteraction.getPlayer().hasPermission("bookmailbox.admin")
		    || ((ChestKnownXYZ != null) // contains it above
			&& this.isSameLocation(ChestFoundXYZ, ChestKnownXYZ)))
		{
			// If sign does not exist, do not try to change it but allow access
			BlockState Candidate = PlayerInteraction.getClickedBlock().getRelative(BlockFace.UP).getState();
			if (Candidate instanceof Sign) {
				// FIXME: why is this update required?
				this.UpdateChestSign( ClickedChest, (Sign) Candidate);
			}
			return;
		}

		// FIXME: a resource hog? Could be converted to simple string matching
		// e.g. X100Y200Z300 in hashmaps for each world, then .containsValue()

		// Check if chest is mailbox
		for ( String OwnerId : MailChests.keySet())
		{
			if ( this.isSameLocation(
				ChestFoundXYZ,
				(Location) MailChests.get(OwnerId)))
			{
				// Chest is a mailbox, not owned by player - deny access
				PlayerInteraction.setCancelled(true);
				return;
			}
		}
		return;
	}

	/**
	 * Event Handler
	 * <p>
	 * Checks whether block break affects a known mailbox and whether the
	 * block break should be allowed.
	 *
	 * @param PlayerInteraction
	 *		the BlockBreakEvent as passed by Bukkit
	 */
	@EventHandler
	public void onBlockBreak (
		BlockBreakEvent PlayerInteraction)
	{
		String PlayerId = PlayerInteraction.getPlayer().getUniqueId().toString();

		for (BlockFace face: BlockFace.values())
		{
			// FIXME: might require a different check than .containsValue
			// (but see above about stringification - then it would be ok again)
			if (MailChests.containsValue(PlayerInteraction.getBlock().getRelative(face))
			&& !MailChests.containsKey(PlayerId))
			{
				if (!PlayerInteraction.getPlayer().hasPermission("bookmailbox.admin"))

				// FIXME: add check for player.isOp?
				{
					PlayerInteraction.setCancelled(true);
				}
			} else {
				if (MailChests.containsKey(PlayerId))
				{
					MailChests.remove(PlayerId);
				}
			}
		}
		return;
	}


	/**
	 * Command Handler
	 * <p>
	 * Implements the /mail command.
	 *
	 * @param sender
	 *		The player issuing the command.
	 * @param command
	 *		The issued command (first word).
	 * @param commandLabel
	 *		The issued command (first word).
	 * @param args
	 *		The arguments following the command.
	 */
	public boolean onCommand(
		CommandSender sender,
		Command command,
		String commandLabel,
		String[] args)
	{
		// command sender must be a player
		if (!(sender instanceof Player))
		{
			sender.sendMessage(
				ChatColor.RED
				+ "Only players may send mail.");
			return true;
		}

		// command must have exactly one argument
		if (args.length != 1)
		{
			sender.sendMessage(
				ChatColor.RED
				+ "Usage: /mail <playername>");
			return false;
		}

		// player must have permission to send mail
		Player player = (Player) sender;
		if (!player.hasPermission("bookmailbox.command"))
		{
			sender.sendMessage(
				ChatColor.RED
				+"You do not have the permission to issue /mail.");
			return true;
		}

		// player must hold a book
		if (!player.getItemInHand().getType().equals(Material.WRITTEN_BOOK))
		{
			sender.sendMessage(
				ChatColor.RED
				+"For /mail you must have a written book in your hand.");
			return true;
		}

		int Deliveries = 0;
		if (args[0].equals(String.valueOf('*')))
		{
			if (! (player.hasPermission("bookmailbox.admin")
			    || player.isOp()))
			{
				sender.sendMessage(
					ChatColor.RED
					+"You do not have the permission to issue /mail *.");
			    return true;
			}

			for ( String PlayerId : MailChests.keySet() ) {
				Deliveries += this.DeliverMailByID(sender, PlayerId);
			}
			sender.sendMessage(ChatColor.AQUA +String.valueOf(Deliveries) +" mail(s) sent.");
		}
		else
		{
			Deliveries += this.DeliverMailByName(sender, args[0]);
			if (Deliveries > 0) {
				sender.sendMessage(ChatColor.AQUA+"Mail sent.");
			}
		}

		if (Deliveries > 0)
		{
			// Remove item from sender
			player.setItemInHand(null);
		}

		return true;
	}


	/**
	 * Obtains an Id for the player in /mail &lt;playername&gt; and delegates
	 * to DeliverMailById.
	 *
	 * @param sender
	 *		The player issuing the command.
	 * @param RecipientName
	 *		The recipient of the book.
	 */
	private int DeliverMailByName (
		CommandSender sender,
		String RecipientName)
	{
		@SuppressWarnings("deprecation")
		OfflinePlayer Recipient = this.getServer().getOfflinePlayer(RecipientName);
		if (Recipient == null)
		{
			sender.sendMessage(
				ChatColor.RED
				+ "Player '"+ RecipientName +"' not found.");
			return 0;
		}

		String RecipientId = Recipient.getUniqueId().toString();

		// Check if destination mailbox exists
		if (!MailChests.containsKey(RecipientId))
		{
			sender.sendMessage(
				ChatColor.RED
				+ "Player '"+ RecipientName +"' has no mailbox.");
			return 0;
		}

		return this.DeliverMailByID(sender, RecipientId);
	}

	/**
	 * Delivers to the mailbox of the recipient player id.
	 *
	 * @param sender
	 *		The player issuing the command.
	 * @param RecipientId
	 *		The recipient of the book.
	 */
	private int DeliverMailByID (
		CommandSender sender,
		String RecipientId)
	{
		UUID RecipientUuid = UUID.fromString(RecipientId);
		OfflinePlayer Recipient = this.getServer().getOfflinePlayer(RecipientUuid);
		String RecipientName = Recipient.getName();

		// chest for mailbox must physically exist
		Location MailboxLocation = MailChests.get(RecipientId);
		if (!(MailboxLocation.getBlock().getState() instanceof Chest))
		{
			MailChests.remove(RecipientId);
			sender.sendMessage(
				ChatColor.YELLOW
				+ "The chest with the mailbox of player '"+ RecipientName +"' no longer exists.");
			return 0;
		}

		// chest must have sign above
		BlockState AboveChest = MailboxLocation.getBlock().getRelative(BlockFace.UP).getState();
		if (!(AboveChest instanceof Sign))
		{
			MailChests.remove(RecipientId);
			sender.sendMessage(
				ChatColor.RED
				+ "The mailbox chest of player '"+ RecipientName +"' lacks a mailbox sign.");
			return 0;
		}

		// sign above chest must be a mailbox sign
		Sign ChestSign = (Sign) AboveChest;
		if (!ChestSign.getLine(0).contains("[Mailbox]"))
		{
			MailChests.remove(RecipientId);
			sender.sendMessage(
				ChatColor.RED
				+ "The mailbox sign of player '"+ RecipientName +"' is invalid. (missing [Mailbox])");
			return 0;
		}

		// destination player (recipient) must own the mailbox
		if (!(ChestSign.getLine(1) + ChestSign.getLine(2)).contains(RecipientName))
		{
			MailChests.remove(RecipientId);
			sender.sendMessage(
				ChatColor.RED
				+ "Mailbox sign of Player '"+ RecipientName +"' invalid. (ownership)");
			return 0;
		}

		// Checks complete


		// Send book to player
		Chest MailChest = (Chest) MailboxLocation.getBlock().getState();
		Player player = (Player) sender; // FIXME: a better name than "player"
		HashMap<Integer,ItemStack> NotDelivered =
			MailChest.getBlockInventory().addItem(player.getItemInHand());
		if (NotDelivered.size() > 0)
		{
			sender.sendMessage(
				ChatColor.RED
				+ "Mailbox of recipient player '"+ RecipientName +"' is full.");
			return 0;
		}

		// Check if destination player still exists
		if (Recipient.isOnline())
		{
			Recipient.getPlayer().sendMessage(
				ChatColor.LIGHT_PURPLE
				+"You've got mail from '"+ sender.getName() +"'.");
		}

		this.UpdateChestSign(MailChest, ChestSign);

		return 1;
	}


	/**
	 * converts the data from config.yml into a hashed map for faster access
	 */
	private void loadMailchestsYml ()
	{
		File mailchests_yml = new File(
				getDataFolder(), "mailchests.yml");
		if (! mailchests_yml.exists())
		{
			return;
		}
		YamlConfiguration AsYml =
			YamlConfiguration.loadConfiguration(mailchests_yml);

		for (String PlayerId : AsYml.getKeys(false))
		{
			String[] WorldXYZ = AsYml.getStringList(PlayerId).toArray(new String[3]);
			MailChests.put(
				PlayerId,
				new Location(
					this.getServer().getWorld(WorldXYZ[0]),
					Double.valueOf(WorldXYZ[1]),
					Double.valueOf(WorldXYZ[2]),
					Double.valueOf(WorldXYZ[3])));
		}
		return;
	}


	/**
	 * converts the data from mailchests.yml into a hashed map for faster access
	 */
	private void saveMailchestsYml ()
	{
		YamlConfiguration AsYml = new YamlConfiguration();

		for ( String PlayerId : MailChests.keySet() )
		{
			Location PlayerLocation = MailChests.get(PlayerId);
			String[] WorldXYZ = {
				PlayerLocation.getWorld().getName(),
				String.valueOf(PlayerLocation.getX()),
				String.valueOf(PlayerLocation.getY()),
				String.valueOf(PlayerLocation.getZ())
			};
			AsYml.set(PlayerId, WorldXYZ);
		}

		File mailchests_yml = new File(getDataFolder(), "mailchests.yml");
		if(!mailchests_yml.exists()){
			mailchests_yml.getParentFile().mkdirs();
		}

		try {
			AsYml.save(mailchests_yml);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return;
	}


	/**
	 * Mechanics, utility
	 * <p>
	 * Count books in the mailbox chest and update chest sign accordingly.
	 *
	 * @param MailChest
	 *		The chest representing the mailbox. (BlockState)
	 * @param ChestSign
	 *		The sign above the chest. (BlockState)
	 */
	private void UpdateChestSign (
		Chest MailChest,
		Sign ChestSign)
	{
		// Count new chest book count and update sign
		int count = 0;
		for (ItemStack item: MailChest.getInventory().getContents())
		{
			if ( (item != null) && item.getType().equals(Material.WRITTEN_BOOK))
			{
				count++;
			}
		}
		ChestSign.setLine(3, ChatColor.DARK_GRAY +String.valueOf(count) +" of 27");
		ChestSign.update();
		return;
	}


	/**
	 * Bukkit Uncertainty
	 * <p>
	 * What Location1.equals(Location2) should do. Maybe it does, but not in
	 * all versions of Bukkit? Compares world and coordinates of two locations
	 * for equality.
	 *
	 * @param L1
	 *		first location
	 * @param L2
	 *		other location
	 */
	private boolean isSameLocation (
		Location L1,
		Location L2)
	{
		return ((L1.getWorld().getName() == L2.getWorld().getName())
			&& (L1.getX() == L2.getX())
			&& (L1.getY() == L2.getY())
			&& (L1.getZ() == L2.getZ()));
	}


}
