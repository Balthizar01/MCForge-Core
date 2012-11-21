/*******************************************************************************
 * Copyright (c) 2012 GamezGalaxy.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package net.mcforge.iomodel;

import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import net.mcforge.API.ClassicExtension;

import net.mcforge.API.level.PlayerJoinedLevel;
import net.mcforge.API.player.PlayerBanRequestEvent;
import net.mcforge.API.player.PlayerBlockChangeEvent;
import net.mcforge.API.player.PlayerChatEvent;
import net.mcforge.API.player.PlayerCommandEvent;
import net.mcforge.API.player.PlayerDisconnectEvent;
import net.mcforge.chat.ChatColor;
import net.mcforge.chat.Messages;
import net.mcforge.groups.Group;
import net.mcforge.networking.ClientType;
import net.mcforge.networking.IOClient;
import net.mcforge.networking.packets.Packet;
import net.mcforge.networking.packets.PacketManager;
import net.mcforge.networking.packets.minecraft.GlobalPosUpdate;
import net.mcforge.networking.packets.minecraft.TP;
import net.mcforge.server.Server;
import net.mcforge.server.Tick;
import net.mcforge.sql.MySQL;
import net.mcforge.world.Block;
import net.mcforge.world.Level;
import net.mcforge.world.PlaceMode;
import net.mcforge.API.CommandExecutor;

public class Player extends IOClient implements CommandExecutor {
	protected short X;
	protected short Y;
	protected short Z;
	protected byte ID;
	protected byte block;
	protected boolean showprefix;
	protected String prefix;
	protected ArrayList<ClassicExtension> extend = new ArrayList<ClassicExtension>();
	protected Level level;
	protected int money;
	protected Thread levelsender;
	protected Messages chat;
	protected String clientName = "Minecraft";
	protected ArrayList<Player> seeable = new ArrayList<Player>();
	protected Ping tick = new Ping(this);
	protected ChatColor color = ChatColor.White;
	private HashMap<String, Object> extra = new HashMap<String, Object>();
	/**
	 * Weather or not the player is logged in
	 */
	public boolean isLoggedin;
	/**
	 * The reason why the player was kicked
	 */
	public String kickreason;
	/**
	 * The username of the player
	 */
	public String username;
	/**
	 * The world the player is currently in
	 */
	public String world;
	/**
	 * The mppass the user used to login
	 */
	public String mppass;
	/**
	 * The message the player last send
	 */
	public String message;
	/**
	 * Weather or not the player is connected
	 */
	public boolean isConnected;
	/**
	 * Weather or not the player can use color codes
	 */
	public boolean cc = true; //Can Player use color codes
	/**
	 * What type of client the player is using.
	 */
	public ClientType client;
	/**
	 * The last X pos of the player
	 */
	public short oldX;
	/**
	 * The last Y pos of the player
	 */
	public short oldY;
	/**
	 * The last Z pos of the player
	 */
	public short oldZ;
	/**
	 * The current yaw of the player
	 */
	public byte yaw;
	/**
	 * The current pitch of the player
	 */
	public byte pitch;
	/**
	 * The old yaw of the player
	 */
	public byte oldyaw;
	/**
	 * The old pitch of the player
	 */
	public byte oldpitch;

	public static MessageDigest digest;
	/**
	 * Create a new Player object
	 * @param client The socket the player used to connect
	 * @param pm The PacketManager the player connected to
	 */

	/**
	 * Local variable referencing to the Server class.
	 */
	private Server server;

	/**
	 * The activity of the player.
	 */
	private boolean afk;

	/**
	 * This is the last byte in the Connect Packet
	 * sent by the client.
	 * This value should be 0 if on a normal client.
	 */
	public byte opID;

	/**
	 * Most recent player this player has pm'd with.
	 */
	public Player lastCommunication;

	public Player(Socket client, PacketManager pm, Server server) {
		this(client, pm, (byte) 255, server);
	}
	public Player(Socket client, PacketManager pm, byte opCode, Server server) {
		super(client, pm);
		if (digest == null) {
			try {
				digest = MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException e1) {
				e1.printStackTrace();
			}
		}
		this.server = server;
		ID = getFreeID();
		this.chat = new Messages(pm.server);
		if (opCode != 255) {
			Packet packet = pm.getPacket(opCode);
			if (packet == null) {
				pm.server.Log("Client sent " + opCode);
				pm.server.Log("How do..?");
			} else {
				byte[] message = new byte[packet.length];
				try {
					if(reader.read(message) != message.length) pm.server.Log("Bad packet: "+opCode);
				} catch (IOException e) {
					e.printStackTrace();
				}
				packet.Handle(message, pm.server, this);
			}
		}

		afk = false;

		Listen();
		pm.server.Add(tick);
	}



	/**
	 * Get the name of the client the player is using.
	 * Browser/Normal client = Minecraft
	 * WoM client = WoM
	 * Extended Classic compatible client = X (vary's on client)
	 * @return
	 *        The name of the client
	 */
	public String getClientName() {
		return clientName;
	}

	/**
	 * Set the name of the client the player is using
	 * @param name
	 *            The name of the client
	 */
	public void setClientName(String name) {
		this.clientName = name;
	}

	/**
	 * Add an extension this player can use.
	 * If the player is not using {@link ClientType#Extend_Classic} protocol, nothing will
	 * be added.
	 * @param ext
	 *           The Extension to add.
	 */
	public void addExtension(ClassicExtension ext) {
		if (client != ClientType.Extend_Classic)
			return;
		extend.add(ext);
	}

	/**
	 * Get a list of extensions this player can use.
	 * @return
	 *        An {@link ArrayList} of {@link ClassicExtension}'s
	 */
	public final ArrayList<ClassicExtension> getExtensions() {
		return extend;
	}

	/**
	 * Check to see if a player has the ability to use an Extension.
	 * @param name
	 *           The name of the extension
	 * @return
	 *        returns true if the player can use it, otherwise returns false.
	 */
	public boolean hasExtension(String name) {
		for (ClassicExtension c : getExtensions()) {
			if (c.extName().equals(name))
				return true;
		}
		return false;
	}

	/**
	 * Ban this player.
	 * This method will execute a BanRequest Event. If the end-user does not
	 * have a ban plugin, then the player wont be banned.
	 * @param banner
	 *             The client who is doing the banning
	 * @param reason
	 *              The reason for banning this player
	 * @param kick
	 *            Weather this player should be kicked as well.
	 * @param banip
	 *             Weather to ban the player's IP as well.
	 */
	public void ban(CommandExecutor banner, String reason, boolean kick, boolean banip) {
		PlayerBanRequestEvent pbre = new PlayerBanRequestEvent(this, reason, kick, banner, banip);
		this.server.getEventSystem().callEvent(pbre);
		if (kick)
			kick("Banned: " + reason);
	}

	/**
	 * Ban this player.
	 * This method will execute a BanRequest Event. If the end-user does not
	 * have a ban plugin, then the player wont be banned.
	 * @param banner
	 *              The client who is doing the banning
	 * @param reason
	 *              The reason for the ban
	 */
	public void ban(CommandExecutor banner, String reason) {
		ban(banner, reason, false, false);
	}

	/**
	 * Ban this player.
	 * This method will execute a BanRequest Event. If the end-user does not
	 * have a ban plugin, then the player wont be banned.
	 * The reason provided will be <b>"No reason given"</b>
	 * @param banner
	 *              The client doing the banning
	 */
	public void ban(CommandExecutor banner) {
		ban(banner, "No reason given", false, false);
	}

	/**
	 * Ban and kick this player.
	 * This method will execute a BanRequest Event. If the end-user does not
	 * have a ban plugin, then the player wont be banned.
	 * @param banner
	 *              The client who is doing the banning
	 * @param reason
	 *              The reason for the ban
	 */
	public void kickBan(CommandExecutor banner, String reason) {
		ban(banner, reason, true, false);
	}

	/**
	 * Ban and kick this player.
	 * This method will execute a BanRequest Event. If the end-user does not
	 * have a ban plugin, then the player wont be banned.
	 * The reason provided will be <b>"No reason given"</b>
	 * @param banner
	 *              The client doing the banning
	 */
	public void kickBan(CommandExecutor banner) {
		ban(banner, "No reason given", true, false);
	}

	/**
	 * IP Ban this player.
	 * A IP Ban will kick the player as well.
	 * This method will execute a BanRequest Event. If the end-user does not
	 * have a ban plugin, then the player wont be banned.
	 * @param banner
	 *              The client who is doing the banning
	 * @param reason
	 *              The reason for the ban
	 */
	public void ipBan(CommandExecutor banner, String reason) {
		ban(banner, reason, true, true);
	}

	/**
	 * IP Ban this player.
	 * A IP Ban will kick the player as well
	 * This method will execute a BanRequest Event. If the end-user does not
	 * have a ban plugin, then the player wont be banned.
	 * The reason provided will be <b>"No reason given"</b>
	 * @param banner
	 *              The client doing the banning
	 */
	public void ipBan(CommandExecutor banner) {
		ban(banner, "No reason given", true, true);
	}

	/**
	 * Check to see if a player has the ability to use an Extension.
	 * @param class_
	 *              The class of Extension
	 * @return
	 *        True if the player can use it.
	 *        Returns false if the player can't use it <b>OR</b> if the class provided was not a {@link ClassicExtension}
	 */
	public boolean hasExtension(Class<?> class_) {
		ClassicExtension c = null;
		if ((c = class_.getAnnotation(ClassicExtension.class)) != null)
			return hasExtension(c.extName());
		return false;
	}

	/**
	 * Get the current block the client is holding.
	 * If the client supports the ClassicExtension protocol, then
	 * this value will be updated frequently.
	 * If the client does not support the ClassicExtension protocol, then
	 * this value will update every time the client makes a block change.
	 * @return
	 *        The current block the client is holding
	 */
	public byte getBlockHolding() {
		return block;
	}

	/**
	 * Set the current block the client is holding.
	 * @param block
	 *             The current block the client is holding
	 */
	public void setHoldingBlock(byte iD2) {
		this.block = iD2;
	}

	/**
	 * Check to see if a player has the ability to use an Extension.
	 * @param object
	 *              The Extension object
	 * @return
	 *        True if the player can use it.
	 *        False if the player can't use it <b>OR</b> if the object provided was not a
	 *        {@link ClassicExtension}
	 */
	public boolean hasExtension(Object object) {
		return hasExtension(object.getClass());
	}

	/**
	 * Weather the user is on wom
	 * @return
	 *        True if the user is using the WoM client
	 */
	public boolean isOnWom() {
		return client == ClientType.WoM;
	}


	/**
	 * Get the username the client will see above the player's head
	 * @return 
	 *        The username with the color at the beginning.
	 */
	public String getDisplayName() {
		return (isShowingPrefix() ? prefix : "") + color.toString() + username; 
	}

	/**
	 * Get the color of the player's username
	 * @return
	 *        The color
	 */
	public ChatColor getDisplayColor() {
		return color;
	}

	/**
	 * Get the string that is added before the user's username.
	 * @return
	 *        The prefix
	 */
	public String getPrefix() {
		return prefix;
	}

	/**
	 * Set something to go before the user's username.
	 * This can be a title or a star.
	 * This prefix wont appear above the players head, unless
	 * {@link Player#isShowingPrefix()} is true.
	 * @param prefix The prefix to set.
	 */
	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	/**
	 * Weather the user is showing there prefix
	 * above there player.
	 * @return
	 *        True if they are, false if they are not.
	 */
	public boolean isShowingPrefix() {
		if (getPrefix() == null || getPrefix().equals(""))
			return false;
		return showprefix;
	}

	/**
	 * Set weather or not the user should show there prefix above
	 * there player's head.
	 * This method will respawn the player using {@link Player#respawn()}
	 * @param value
	 *             True if they should, false if they should not.
	 */
	public void setShowPrefix(boolean value) {
		this.showprefix = value;
		respawn();
	}

	/**
	 * Set the color for this player
	 * @param color 
	 *             The color
	 */
	public void setDisplayColor(ChatColor color) {
		this.color = color;
		respawn();
	}

	/**
	 * Respawn this player.
	 */
	public void respawn() {
		for (Player p : getServer().players) {
			p.Despawn(this);
			p.spawnPlayer(this);
		}
	}

	/**
	 * Verify the player is using a valid account
	 * @return Returns true if the account is valid, otherwise it will return false
	 */
	public boolean VerifyLogin() {
		return server.VerifyNames ? mppass.equals(getRealmppass()) : true;
	}

	/**
	 * Get the mppass the user <b>SHOULD</b> have.
	 * @return
	 *        The mppass the user should have
	 */
	public String getRealmppass() {
		try {
			digest.update((String.valueOf(server.getSalt()) + username).getBytes());
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		return new BigInteger(1, digest.digest()).toString(16);
	}

	public int getMoney() {
		return money;
	}

	public void setMoney(int amount) {
		this.money = amount;
	}

	/**
	 * Returns extra data stored in the player
	 * @param key 
	 *           The name of the data
	 * @return 
	 *        The data that was stored. 
	 * @throws SQLException
	 *                     If there was a problem executing the SQL statement to retieve the object
	 *                     from the Database. 
	 * @throws IOException 
	 *                    If there was a problem reading the object from the SQL Database.
	 * @throws ClassNotFoundException 
	 *                               If there was a problem casting the object.
	 */
	@SuppressWarnings("unchecked")
	public <T> T getValue(String key) {
		if (!extra.containsKey(key)) {
			T value = null;
			ResultSet r = server.getSQL().fillData("SELECT count(*) FROM " + server.getSQL().getPrefix() + "_extra WHERE name='" + username + "' AND setting='" + key + "'");
			int size = 0;
			try {
				if (server.getSQL() instanceof MySQL)
					r.next();
				size = r.getInt(1);
			} catch (SQLException e) {
				e.printStackTrace();
				return null;
			}
			if (size == 0)
				return null;
			else {
				r = server.getSQL().fillData("SELECT * FROM " + server.getSQL().getPrefix() + "_extra WHERE name='" + username + "' AND setting='" + key + "'");
				try {
					if (server.getSQL() instanceof MySQL) {
						r.next();
						ByteArrayInputStream bais;
						ObjectInputStream ins;
						bais = new ByteArrayInputStream(r.getBytes("value"));
						ins = new ObjectInputStream(bais);
						value = (T)ins.readObject();
						ins.close();
					}
					else
						value = (T)r.getObject("value");
				} catch (SQLException e) {
					e.printStackTrace();
					return null;
				} catch (IOException e) {
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
				extra.put(key, value);
				try {
					r.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
				return value;
			}
		}
		return (T)extra.get(key);
	}

	public boolean hasValue(String key) {
		if (extra.containsKey(key))
			return true;
		else {
			ResultSet r = server.getSQL().fillData("SELECT count(*) FROM " + server.getSQL().getPrefix() + "_extra WHERE name='" + username + "' AND setting='" + key + "'");
			int size = 0;
			try {
				if (server.getSQL() instanceof MySQL)
					r.next();
				size = r.getInt(1);
			} catch (SQLException e) {
				e.printStackTrace();
				return false;
			}
			if (size == 0)
				return false;
			return true;
		}
	}

	/**
	 * Store extra data into the player, you can get this data back by
	 * using the {@link Player#getValue(String)} method
	 * @param key 
	 *           The name of the data
	 * @param object 
	 *              The object to save
	 */
	public void setValue(String key, Object object) {
		if (extra.containsKey(key))
			extra.remove(key);
		extra.put(key, object);
	}

	/**
	 * Save the value <b>key</b> to the database.
	 * The object <b>key</b> represents will be serialized to the
	 * database.
	 * @param key
	 *           The name of the data to save
	 * @throws SQLException
	 *                     If there was a problem executing the SQL statement to update/insert
	 *                     the object
	 * @throws IOException 
	 *                    If there was a problem writing the object to the SQL server.
	 * @throw NotSerializableException
	 *                                
	 */
	public void saveValue(String key) throws SQLException, IOException, NotSerializableException {
		if (!extra.containsKey(key))
			return;
		if (extra.get(key) instanceof Serializable) {
			ResultSet r = server.getSQL().fillData("SElECT count(*) FROM " + server.getSQL().getPrefix() + "_extra WHERE name='" + username + "' AND setting='" + key + "'");
			int size = 0;
			if (server.getSQL() instanceof MySQL)
				r.next();
			size = r.getInt(1);
			if (server.getSQL() instanceof MySQL) {
				saveToMySQL(key, extra.get(key), size == 0);
				return;
			}
			PreparedStatement pstmt = null;
			if (size == 0) {
				pstmt = server.getSQL().getConnection().prepareStatement("INSERT INTO " + server.getSQL().getPrefix() + "_extra(name, setting, value) VALUES (?, ?, ?)");
				pstmt.setString(1, username);
				pstmt.setString(2, key);
				pstmt.setObject(3, extra.get(key));
				pstmt.executeUpdate();
			}
			else {
				pstmt = server.getSQL().getConnection().prepareStatement("UPDATE " + server.getSQL().getPrefix() + "_extra SET value = ? WHERE name = ? AND setting = ?");
				pstmt.setObject(1, extra.get(key));
				pstmt.setString(2, username);
				pstmt.setString(3, key);
				pstmt.executeUpdate();
			}
			pstmt.close();
		}
		else
			throw new NotSerializableException("The object that was stored in ExtraData cant be saved because it doesnt implement Serializable!");
	}
	
	private <T> void saveToMySQL(String key, T o, boolean add) throws IOException, SQLException {
		PreparedStatement ps=null;
		String sql=null;
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bos);
		oos.writeObject(o);
		oos.flush();
		oos.close();
		bos.close();
		
		byte[] data = bos.toByteArray();
		if (add) {
			sql = "INSERT INTO " + server.getSQL().getPrefix() + "_extra(name, setting, value) VALUES (?, ?, ?)";
			ps = getServer().getSQL().getConnection().prepareStatement(sql);
			ps.setString(1, username);
			ps.setString(2, key);
			ps.setObject(3, data);
		}
		else {
			sql = "UPDATE " + server.getSQL().getPrefix() + "_extra SET value = ? WHERE name = ? AND setting = ?";
			ps = getServer().getSQL().getConnection().prepareStatement(sql);
			ps.setObject(1, data);
			ps.setString(2, username);
			ps.setString(3, key);
		}
		ps.executeUpdate();
		
	}

	/**
	 * Login the player
	 * @throws InterruptedException 
	 */
	public void login() throws InterruptedException {
		if (isLoggedin)
			return;
		for (int i = 0; i < getServer().players.size(); i++) {
			Player p = getServer().players.get(i);
			if ((username.equals(p.username)) && (!p.equals(this))) {
				p.kick("Someone logged in as you!");
			}
		}
		if (Group.getGroup(this) == null)
			setGroup(Group.getDefault());
		SendWelcome();
		setLevel(pm.server.getLevelHandler().findLevel(pm.server.MainLevel));
		levelsender.join(); //Wait for finish
		X = (short)((0.5 + level.spawnx) * 32);
		Y = (short)((1 + level.spawny) * 32);
		Z = (short)((0.5 + level.spawnz) * 32);
		oldX = X;
		oldY = Y;
		oldZ = Z;
		pm.server.Log(this.username + " has joined the server.");
		chat.serverBroadcast(this.username + " has joined the server.");
		spawnPlayer(this);
		updateAllLists(); //Update me in your list
		setPos((short)((0.5 + level.spawnx) * 32), (short)((1 + level.spawny) * 32), (short)((0.5 + level.spawnz) * 32));
		for (Player p : pm.server.players) {
			if (this.hasExtension("ExtAddPlayerName"))
				pm.getPacket((byte)0x33).Write(this, server, p); //Update mine for you
			if (p.level == level) {
				spawnPlayer(p); //Spawn p for me
				p.spawnPlayer(this); //Spawn me for p
			}
		}
		setPos((short)((0.5 + level.spawnx) * 32), (short)((1 + level.spawny) * 32), (short)((0.5 + level.spawnz) * 32));
		isLoggedin = true;
		sendMessage(ChatColor.Dark_Red + "You're now dead." + ChatColor.Yellow + " Wait until the next round to build and play!");
	}

	/**
	 * Get the current group the player is in
	 * @return The group
	 */
	public Group getGroup() {
		return Group.getGroup(this);
	}

	/**
	 * Change the group the player is in
	 */
	public void setGroup(Group newgroup) {
		Group old = Group.getGroup(this);
		if (Group.getGroup(this) != null)
			Group.getGroup(this).removePlayer(this);
		newgroup.addPlayer(this);
		if (!this.isLoggedin)
			return;
		if ((old != null && old.isOP && !newgroup.isOP) || (old != null && !old.isOP && newgroup.isOP) || old == null) {
			Packet p = pm.getPacket((byte)0x0f);
			p.Write(this, server);
		}
		updateAllLists();
	}

	/**
	 * Change this player in all ext player's lists.
	 */
	private void updateAllLists() {
		for (Player p : server.players) {
			if (p.hasExtension("ExtAddPlayerName"))
				pm.getPacket((byte)0x33).Write(p, server, this);
		}
	}

	/**
	 * Handle the block change packet
	 * @param X The X pos where the client modified
	 * @param Y The Y pos where the client modified
	 * @param Z The Z pos where the client modifed
	 * @param type The type of action it did
	 * @param holding What the client was holding
	 */
	public void HandleBlockChange(short X, short Y, short Z, PlaceMode type, byte holding) {
		Block getOrginal = level.getTile(X, Y, Z);
		//TODO Call event
		//TODO Check other stuff
		if (holding > 49) {
			kick("Hack Client detected!");
			return;
		}
		PlayerBlockChangeEvent event = new PlayerBlockChangeEvent(this, X, Y, Z, Block.getBlock(holding), level, pm.server, type);
		pm.server.getEventSystem().callEvent(event);
		if (event.isCancelled()) {
			SendBlockChange(X, Y, Z, getOrginal);
			return;
		}
		Block place = event.getBlock();
		if (type == PlaceMode.PLACE)
			GlobalBlockChange(X, Y, Z, place, level, pm.server);
		else if (type == PlaceMode.BREAK)
			GlobalBlockChange(X, Y, Z, Block.getBlock((byte)0), level, pm.server);
	}

	/**
	 * Send a block update to all the players on level "l" in server "s"
	 * @param X The X pos of the udpate
	 * @param Y The Y pos of the update
	 * @param Z The Z pos of the update
	 * @param block The block to send
	 * @param l The level the update happened in
	 * @param s The server the update happened in
	 * @param updateLevel Weather the level should be updated
	 */
	public static void GlobalBlockChange(short X, short Y, short Z, Block block, Level l, Server s, boolean updateLevel) {
		if (updateLevel)
			l.setTile(block, X, Y, Z, s);
		if (s == null)
			return;
		//Do this way to save on packet overhead
		Packet sb = s.getPacketManager().getPacket((byte)0x05);
		for (Player p : s.players)
			if (p.level == l)
				sb.Write(p, s, X, Y, Z, block.getVisibleBlock());
	}

	/**
	 * Send a block to this player
	 * @param X The X coord of the block
	 * @param Y The Y coord of the block
	 * @param Z The Z coord of the block
	 * @param block The block to send
	 */
	public void SendBlockChange(short X, short Y, short Z, Block block) {
		server.getPacketManager().getPacket((byte)0x05).Write(this, server, X, Y, Z, block.getVisibleBlock());
	}

	/**
	 * Send a block update to all the players on level "l" in server "s"
	 * @param X The X pos of the udpate
	 * @param Y The Y pos of the update
	 * @param Z The Z pos of the update
	 * @param block The block to send
	 * @param l The level the update happened in
	 * @param s The server the update happened in
	 */
	public static void GlobalBlockChange(short X, short Y, short Z, Block block, Level l, Server s) {
		GlobalBlockChange(X, Y, Z, block, l, s, true);
	}

	public static Player find(Server server, String name)
	{
		return server.findPlayer(name);
	}

	/**
	 * Get a list of who the player can see
	 * @return An ArrayList of players
	 */
	public ArrayList<Player> getSeeable() {
		return seeable;
	}

	/**
	 * Send the player an MoTD screen
	 * You can have the user hang on the MoTD screen when the player first joins
	 * @param topline The top line of the MoTD screen
	 * @param bottomline The bottom line of the MoTD screen
	 */
	public void sendMoTD(String topline, String bottomline) {
		pm.getPacket("MOTD").Write(this, pm.server, topline, bottomline);
	}

	public void UpdatePos() throws IOException {
		if (!isLoggedin)
			return;

		TP t = (TP)(pm.getPacket("TP"));
		GlobalPosUpdate gps = (GlobalPosUpdate)(pm.getPacket("GlobalPosUpdate"));
		byte[] tosend;
		if (Math.abs(getX() - oldX) >= 127 || Math.abs(getY() - oldY) >= 127 || Math.abs(getZ() - oldZ) >= 127)
			tosend = t.toSend(this);
		else
			tosend = gps.toSend(this);
		for (Player p : pm.server.players) {
			if (p == this)
				continue;
			if (p.level == level)
				p.WriteData(tosend);
		}
	}

	/**
	 * Spawn a new player for this player
	 * @param p The player to spawn
	 */
	public void spawnPlayer(Player p) {
		if (seeable.contains(p))
			return;
		pm.getPacket((byte)0x07).Write(this, server, p);
		if (this.hasExtension("ExtPlayer") && p.client == ClientType.Extend_Classic)
			pm.getPacket((byte)0x39).Write(this, server, p);
		seeable.add(p);
	}

	/**
	 * Get the level this player is currently on
	 * @return The level
	 */
	public Level getLevel() {
		return level;
	}

	/**
	 * Send a new level to the player
	 * @param level The level to send
	 */
	private void setLevel(Level level) {
		if (this.level == level)
			return;
		this.level = level;
		server.Log(username + " moved to " + level.name);
		levelsender = new SendLevel(this);
		levelsender.start();
	}

	/**
	 * Weather or not the player is loading the level
	 * @return True if the player is loading the level, false if the player is not
	 */
	public boolean isLoading() {
		return levelsender != null;
	}

	/**
	 * Wait for the player to finish loading the level
	 * The method blocks until the player finishes loading the level 
	 * This will block 
	 * @throws InterruptedException
	 */
	public void waitForLoaded() throws InterruptedException {
		if (levelsender == null)
			return;
		levelsender.join();
	}


	protected void SendWelcome() {
		pm.getPacket("Welcome").Write(this, pm.server);
	}

	/**
	 * Kick the player from the server
	 * @param reason The reason why he was kicked
	 */
	public void kick(String reason) {
		if (reason.equals(""))
			reason = "You have been kicked!";
		else
			chat.serverBroadcast(username + " has been kicked (" + reason + ")");
		Packet p = pm.getPacket("Kick");
		this.kickreason = reason;
		server.players.remove(this);
		p.Write(this, pm.server);
	}

	private byte getFreeID() {
		boolean found = true;
		byte toreturn = 0;
		for (int i = 0; i < 255; i++) {
			found = true;
			for (Player p : pm.server.players) {
				if (p.ID == i) {
					found = false;
					break;
				}
			}
			if (found) {
				toreturn = (byte)i;
				break;
			}
		}
		return toreturn;
	}

	/**
	 * Get the X cord. of the player
	 * This is NOT in block cord.
	 * @return The value
	 */
	public short getX() {
		return X;
	}

	/**
	 * Get the X cord. of the player on the level
	 * This is in block cord.
	 * @return The value
	 */
	public int getBlockX() {
		return X / 32;
	}
	/**
	 * Get the X cord. of the player
	 * This is NOT in block cord.
	 * @return The value
	 */
	public short getY() {
		return Y;
	}
	/**
	 * Get the X cord. of the player on the level
	 * This is in block cord.
	 * @return The value
	 */
	public int getBlockY() {
		return Y / 32;
	}
	/**
	 * Get the ID of the player
	 * @return The value
	 */
	public byte getID() {
		return ID;
	}
	/**
	 * Get the X cord. of the player
	 * This is NOT in block cord.
	 * @return The value
	 */
	public short getZ() {
		return Z;
	}
	/**
	 * Get the X cord. of the player on the level
	 * This is in block cord.
	 * @return The value
	 */
	public int getBlockZ() {
		return Z / 32;
	}
	/**
	 * Set the current X pos of the player
	 * This will NOT teleport the player
	 * To teleport the player, use {@link #setPos(short, short, short)}
	 * @param value
	 */
	public void setX(short value) {
		oldX = X;
		X = value;
	}
	/**
	 * Set the current Y pos of the player
	 * This will NOT teleport the player
	 * To teleport the player, use {@link #setPos(short, short, short)}
	 * @param value
	 */
	public void setY(short value) {
		oldY = Y;
		Y = value;
	}
	/**
	 * Set the current Z pos of the player
	 * This will NOT teleport the player
	 * To teleport the player, use {@link #setPos(short, short, short)}
	 * @param value
	 */
	public void setZ(short value) {
		oldZ = Z;
		Z = value;
	}
	/**
	 * Teleport the player
	 * @param x The X pos to teleport to
	 * @param y The Y pos to teleport to
	 * @param z The Z pos to teleport to
	 * @param yaw The new yaw
	 * @param pitch The new pitch
	 */
	public void setPos(short x, short y, short z, byte yaw, byte pitch) {
		setX(x);
		setY(y);
		setZ(z);
		oldyaw = this.yaw;
		this.yaw = yaw;
		oldpitch = this.pitch;
		this.pitch = pitch;
		TP();
	}
	/**
	 * Teleport the player
	 * @param x The X pos to teleport to
	 * @param y The Y pos to teleport to
	 * @param z The Z pos to teleport to
	 */
	public void setPos(short x, short y, short z) {
		setPos(x, y, z, yaw, pitch);
	}

	protected void TP() {
		Packet p = pm.getPacket("TP");
		for (Player pp : pm.server.players) {
			if (pp.level == level)
				p.Write(pp, pp.pm.server, this, ID); //Tell all the other players as well...
		}
	}

	/**
	 * Sends a message to the player.
	 * If the message is longer than 64 chars, then it will not send.
	 * 
	 * @param message
	 *               The message to send
	 */
	@Override
	public void sendMessage(String message){
		Packet p = pm.getPacket("Message");
		String[] messages = chat.split(message);
		for (String m : messages) {
			this.message = m;
			p.Write(this, pm.server);
		}
	}
	/**
	 * Sends a wom message to the client if it uses wom.
	 * 
	 * @param message
	 *              The message to put in detail spot.
	 */
	public void sendWoMMessage(String message)
	{
		if (client == ClientType.WoM) {
			sendMessage("^detail.user=" + message);
		}
	}
	/**
	 * Change the level the player is currently in. This method will
	 * call {@link #changeLevel(Level, boolean)} with threading being false.
	 * So this method will block until the level sending is finished.
	 * @param level The new level the player will be moved to.
	 */
	public void changeLevel(Level level) {
		changeLevel(level, false);
	}
	/**
	 * Change the level the player is currently in. This method will
	 * block if threading is false. If threading is true, level sending will
	 * begin in a separate thread and this method wont block.
	 * @param level The new level the player will be moved to.
	 * @param threading Weather to make this call in a separate thread or not.
	 */
	public void changeLevel(Level level, boolean threading)
	{
		if (!threading) {
			setLevel(level);
			if (levelsender == null)
				return;
			try {
				levelsender.join(); //Wait for finish
			} catch (InterruptedException e) { } 
			X = (short)((0.5 + level.spawnx) * 32);
			Y = (short)((1 + level.spawny) * 32);
			Z = (short)((0.5 + level.spawnz) * 32);
			oldX = X;
			oldY = Y;
			oldZ = Z;
			spawnPlayer(this);
			setPos((short)((0.5 + level.spawnx) * 32), (short)((1 + level.spawny) * 32), (short)((0.5 + level.spawnz) * 32));
			for (Player p : pm.server.players) {
				if (p.level == level) {
					spawnPlayer(p); //Spawn p for me
					p.spawnPlayer(this); //Spawn me for p
				}
			}
			setPos((short) ((0.5 + level.spawnx) * 32), (short) ((1 + level.spawny) * 32), (short) ((0.5 + level.spawnz) * 32));
			PlayerJoinedLevel event = new PlayerJoinedLevel(this, this.level);
			server.getEventSystem().callEvent(event);
		}
		else {
			Thread aynct = new asyncLevel(level);
			aynct.start();
		}
	}

	/**
	 * This clears the chat screen for the client
	 * by sending 20 blank messages
	 */
	public void clearChatScreen() {
		for (int i = 0; i < 20; i++)
			sendMessage("");
	}

	public void processCommand(String message)
	{
		message = message.substring(1); //Get rid of the / at the beginning
		if (message.split("\\ ").length > 1)
			server.getCommandHandler().execute(this, message.split("\\ ")[0], message.substring(message.indexOf(message.split("\\ ")[1])));
		else
			server.getCommandHandler().execute(this, message, "");
	}

	/**
	 * Handles the messages a player sends to the server, could be used in the future for run command as player
	 * 
	 * @param message
	 * @return void
	 */
	public void recieveMessage(String message){
		if(message.startsWith("/"))
		{
			if (message.indexOf("/womid") != -1) {
				setClientName("WoM");
				client = ClientType.WoM;
				return;
			}
			PlayerCommandEvent event = new PlayerCommandEvent(this, message);
			pm.server.getEventSystem().callEvent(event);
			if (event.isCancelled())
				return;
			processCommand(message);
		}else{		
			String formattedMessage = message;
			if(this.cc)
				formattedMessage = ChatColor.convertColorCodes(message);
			
			PlayerChatEvent event = new PlayerChatEvent(this, message);
			pm.server.getEventSystem().callEvent(event);
			if (event.isCancelled())
				return;
			pm.server.Log("User "+ this.username + " sent: " + message);
			chat.serverBroadcast(this.getDisplayName() + ChatColor.White + ": " + formattedMessage);
		}
	}

	/**
	 * Get the current server object the player is in
	 * @return
	 *        The server
	 */
	@Override
	public Server getServer() {
		return server;
	}

	/**
	 * Despawn a player for this player
	 * @param p The player to despawn
	 */
	public void Despawn(Player p) {
		if (!seeable.contains(p))
			return;
		pm.getPacket((byte)0x0c).Write(this, pm.server, p.ID);
		seeable.remove(p);
	}

	/**
	 * Close the connection of this client
	 * If you want to kick the player, use {@link #kick(String)}
	 */
	@Override
	public void CloseConnection() {
		if (pm.server.players.contains(this))
			pm.server.players.remove(this);
		if(this.username != null)
		{
			pm.server.Log(this.username + " has left the server.");
			chat.serverBroadcast(this.username + " has left the server.");		
		}
		for (Player p : pm.server.players) {
			p.Despawn(this);
			if (p.hasExtension("ExtRemovePlayerName"))
				pm.getPacket((byte)0x35).Write(p, server, this);
		}
		PlayerDisconnectEvent event = new PlayerDisconnectEvent(this);
		server.getEventSystem().callEvent(event);
		super.CloseConnection();
		pm.server.Remove(tick); //Do this last as this takes a while to remove
	}

	protected void finishLevel() {
		levelsender = null;
	}

	protected class Ping implements Tick {

		Player p;
		public Ping(Player p) { this.p = p; }
		@Override
		public void tick() {
			Packet pa;
			pa = pm.getPacket((byte)0x01);
			pa.Write(p, pm.server);
			pa = null;
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	protected class asyncLevel extends Thread {

		Level level;

		public asyncLevel(Level level) { this.level = level; }
		@Override
		public void run() {
			changeLevel(level, false);
		}
	}

	protected class SendLevel extends Thread {

		Player p;
		public SendLevel(Player p) { this.p = p; }
		@Override
		public void run() {
			long startTime = System.nanoTime();
			Packet pa;
			pa = pm.getPacket((byte)0x02);
			pa.Write(p, pm.server);
			pa = null;
			pa = pm.getPacket((byte)0x03);
			pa.Write(p, pm.server);
			pa = null;
			pa = pm.getPacket((byte)0x04);
			pa.Write(p, pm.server);
			pa = null;
			long endTime = System.nanoTime();
			long duration = endTime - startTime;
			server.Log("Loading took: " + duration + "ms");
			p.finishLevel();
		}
	}

	public Messages getChat()
	{
		return chat;
	}

	public boolean isAfk()
	{
		return afk;
	}

	public void setAfk(boolean afk)
	{
		this.afk = afk;
	}
	@Override
	public String getName() {
		return username;
	}
}

