/*******************************************************************************
 * Copyright (c) 2012 MCForge.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package net.mcforge.API.plugin;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import net.mcforge.server.Server;

public class PluginHandler {
	private ArrayList<Plugin> plugins = new ArrayList<Plugin>();

	private ArrayList<Game> games = new ArrayList<Game>();

	/**
	 * Unload a plugin from memory.
	 * @param p
	 *         The plugin to unload
	 */
	public void unload(Plugin p) {
		if (plugins.contains(p)) {
			p.onUnload();
			plugins.remove(p);
		}
	}
	private File[] checkrequires(Server server)
	{
		ArrayList<File> correctfiles = new ArrayList<File>();
		ArrayList<String> pluginnames = new ArrayList<String>();
		File pluginFolder = new File("plugins/");
		File[] pluginFiles = pluginFolder.listFiles();
		for(int i = 0; i < pluginFiles.length; i++)
		{
			if(pluginFiles[i].isFile() && pluginFiles[i].getName().endsWith(".jar"))
			{
				JarFile file = null;
				try {
					file = new JarFile(pluginFiles[i]);
				} catch (IOException e) {
					e.printStackTrace();
				}
				if(file != null)
				{
					Enumeration<JarEntry> entries = file.entries();
					if(entries != null)
					{
						while(entries.hasMoreElements())
						{
							JarEntry fileName = entries.nextElement();
							if(fileName.getName().endsWith(".config"))
							{
								Properties properties = new Properties();
								try {
									properties.load(file.getInputStream(fileName));
								} catch (IOException e) {
									e.printStackTrace();
								}
								new File("plugins/" + pluginFiles[i].getName());
								DataInputStream in = null;
								try {
									in = new DataInputStream(file.getInputStream(fileName));
								} catch (IOException e2) {
									e2.printStackTrace();
								}
								BufferedReader br = new BufferedReader(new InputStreamReader(in));
								String strLine;
								try {
									while ((strLine = br.readLine()) != null) {
										String key = strLine.split("\\=")[0].trim();
										String value = strLine.split("\\=")[1].trim();
										if (key.equals("name")) {
											pluginnames.add(value.trim().toLowerCase());
										}
									}
								} catch (IOException e1) {
									e1.printStackTrace();
								}
							}
						}
					}
					try {
						file.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		for(int i = 0; i < pluginFiles.length; i++)
		{
			if(pluginFiles[i].isFile() && pluginFiles[i].getName().endsWith(".jar"))
			{
				JarFile file = null;
				try {
					file = new JarFile(pluginFiles[i]);
				} catch (IOException e) {
					e.printStackTrace();
				}
				if(file != null)
				{
					Enumeration<JarEntry> entries = file.entries();
					if(entries != null)
					{
						while(entries.hasMoreElements())
						{
							JarEntry fileName = entries.nextElement();
							if(fileName.getName().endsWith(".config"))
							{
								Properties properties = new Properties();
								try {
									properties.load(file.getInputStream(fileName));
								} catch (IOException e) {
									e.printStackTrace();
								}
								new File("plugins/" + pluginFiles[i].getName());
								DataInputStream in = null;
								try {
									in = new DataInputStream(file.getInputStream(fileName));
								} catch (IOException e2) {
									e2.printStackTrace();
								}
								BufferedReader br = new BufferedReader(new InputStreamReader(in));
								String strLine;
								try {
									boolean canadd = true;
									while ((strLine = br.readLine()) != null) {
										String key = strLine.split("\\=")[0].trim();
										String value = strLine.split("\\=")[1].trim();
										if (key.equals("required-plugins")) {
											String[] required = value.split(",");
											for (int z=0;z<required.length;z++)
											{
												if (!pluginnames.contains(required[z].trim().toLowerCase()))
													canadd = false;
											}
										}
										else
										{
										    correctfiles.add(pluginFiles[i]);
										}
									}
									if (canadd) { correctfiles.add(pluginFiles[i]); }
								} catch (IOException e1) {
									e1.printStackTrace();
								}
							}
						}
					}
					try {
						file.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return correctfiles.toArray(new File[correctfiles.size()]);
	}

	public void loadPlugin(Server server, File arg0) {
		JarFile file = null;
		try {
			file = new JarFile(arg0);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if(file != null)
		{
			Enumeration<JarEntry> entries = file.entries();
			if(entries != null)
			{
				while(entries.hasMoreElements())
				{
					JarEntry fileName = entries.nextElement();
					if(fileName.getName().endsWith(".config"))
					{
						Properties properties = new Properties();
						try {
							properties.load(file.getInputStream(fileName));
						} catch (IOException e) {
							e.printStackTrace();
						}
						File pluginFile = new File("plugins/" + arg0.getName());
						String[] args = new String[] { "-normal" };
						DataInputStream in = null;
						try {
							in = new DataInputStream(file.getInputStream(fileName));
						} catch (IOException e2) {
							e2.printStackTrace();
						}
						BufferedReader br = new BufferedReader(new InputStreamReader(in));
						String strLine;
						try {
							while ((strLine = br.readLine()) != null) {
								String key = strLine.split("\\=")[0].trim();
								String value = strLine.split("\\=")[1].trim();
								if (key.equals("main-class")) {
									Plugin plugin = getType(pluginFile, value, Plugin.class, server);
									for (Plugin p : plugins) {
										if (p.equals(plugin))
											continue;
									}
									plugin.setProperties(properties);
									plugin.onLoad(args);
									if(plugin instanceof Game)
									{
										games.add((Game)plugin);
										server.Add((Game)plugin);
									} else {
										plugins.add(plugin);
									}
								}
								if (key.equals("command-path")) {
									Command c = getType(pluginFile, value, Command.class);
									server.getCommandHandler().addCommand(c);
								}
							}
						} catch (IOException e1) {
							e1.printStackTrace();
						}
						try {
							addPath(pluginFile);
						} catch (MalformedURLException e) {
							e.printStackTrace();
						} catch (NoSuchMethodException e) {
							e.printStackTrace();
						} catch (SecurityException e) {
							e.printStackTrace();
						} catch (IllegalAccessException e) {
							e.printStackTrace();
						} catch (IllegalArgumentException e) {
							e.printStackTrace();
						} catch (InvocationTargetException e) {
							e.printStackTrace();
						}
						try {
							in.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
			try {
				file.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	public void loadplugins(Server server)
	{
		File pluginFolder = new File("plugins/");
		if(!pluginFolder.exists())
		{
			System.out.println("Mods folder does not exist.");
			pluginFolder.mkdir();
			return;
		}
		File[] pluginFiles = checkrequires(server);
		for(int i = 0; i < pluginFiles.length; i++)
		{
			if(pluginFiles[i].isFile() && pluginFiles[i].getName().endsWith(".jar"))
				loadPlugin(server, pluginFiles[i]);
		}
	}

	@SuppressWarnings("deprecation")
	public <T> T getType(File file, String classpath, Class<? extends T> type, Object...parma) {
		try {
			URL[] urls = new URL[] { file.toURL() };
			ClassLoader loader = URLClassLoader.newInstance(urls, getClass().getClassLoader());
			Class<?> class_ = Class.forName(classpath, true, loader);
			Class<? extends T> runclass = class_.asSubclass(type);
			Class<?>[] constructparma = new Class<?>[parma.length];
			for (int i = 0; i < parma.length; i++) {
				constructparma[i] = parma[i].getClass();
			}
			Constructor<? extends T> construct = runclass.getConstructor(constructparma);
			T toreturn = construct.newInstance(parma);
			return toreturn;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Adds a jar file to the System Classpath at runtime
	 * @param f
	 *         The file to add
	 * @throws MalformedURLException
	 *                              If the file path is not in the correct format
	 * @throws NoSuchMethodException
	 *                              If there was a problem adding the jar to the classpath
	 * @throws SecurityException
	 *                           If there was a problem adding the jar to the classpath
	 * @throws IllegalAccessException
	 *                                If there was a problem adding the jar to the classpath
	 * @throws IllegalArgumentException
	 *                                  If there was a problem adding the jar to the classpath
	 * @throws InvocationTargetException
	 *                                    If there was a problem adding the jar to the classpath
	 */
	@SuppressWarnings({ "rawtypes", "unchecked", "deprecation" })
	public void addPath(File f) throws MalformedURLException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		URL u = f.toURL();
		URLClassLoader urlClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
		Class urlClass = URLClassLoader.class;
		Method method = urlClass.getDeclaredMethod("addURL", new Class[]{URL.class});
		method.setAccessible(true);
		method.invoke(urlClassLoader, new Object[]{u});

	}
}
