package net.minecraft;

import java.applet.Applet;
import java.applet.AppletStub;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.io.DataOutputStream;
import java.io.File;
import java.awt.Image;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public class Launcher extends Applet implements Runnable, AppletStub, MouseListener {
  private static final long serialVersionUID = 1L;
  public Map<String, String> customParameters = new HashMap<String, String>();
  private GameUpdater gameUpdater;
  private boolean gameUpdaterStarted = false;
  private Applet applet;
  private Image bgImage;
  private boolean active = false;
  private int context = 0;
  private boolean hasMouseListener = false;
  private VolatileImage img;
  private String username;
  private BufferedImage playerSkin;
  private BufferedImage playerCape;
  private boolean forceServerJoin = false;
  private String serverIP = null;
  private String serverPort = "25565";
  private boolean skinLoaded = false;

  public boolean isActive() {
    if (context == 0) {
      context = -1;
      try {
        if (getAppletContext() != null)
          context = 1;
      } catch (Exception localException) {
      }
    }
    if (context == -1)
      return active;
    return super.isActive();
  }

  public void init(String userName, String latestVersion, String downloadTicket, String sessionId) {
    try {
      bgImage = ImageIO.read(LoginForm.class.getResource("dirt.png")).getScaledInstance(32, 32, 16);
    } catch (IOException e) {
      e.printStackTrace();
    }

    this.username = userName;
    customParameters.put("username", userName);
    customParameters.put("sessionid", sessionId);
    customParameters.put("minecraft.skin.username", userName);

    System.setProperty("fml.ignoreInvalidCert", "true");
    System.setProperty("fml.ignoreDownloadErrors", "true");
    System.setProperty("fml.skipModLoader", "true");
    System.setProperty("fml.skipClassLoader", "true");
    System.setProperty("fml.noForge", "true");
    System.setProperty("fml.loadedMods", "none");

    gameUpdater = new GameUpdater(latestVersion, "minecraft.jar?user=" + userName + "&ticket=" + downloadTicket);
    gameUpdater.username = userName;

    serverIP = customParameters.get("server");
    if (customParameters.containsKey("port")) {
      serverPort = customParameters.get("port");
    }
    if (serverIP != null && !serverIP.isEmpty()) {
      forceServerJoin = true;
      System.out.println("Will join server after launch: " + serverIP + ":" + serverPort);
    }

    loadSkin();
  }

  private void loadSkin() {
    new Thread() {
      public void run() {
        try {
          String name = username != null ? username : "Player";
          if (name != null && !name.isEmpty()) {
            URL skinUrl = new URL("https://skinsystem.ely.by/skins/" + name + ".png");
            HttpURLConnection connection = (HttpURLConnection) skinUrl.openConnection();
            connection.setRequestProperty("User-Agent", "Minecraft Launcher");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.connect();
            if (connection.getResponseCode() == 200) {
              BufferedImage fullSkin = ImageIO.read(connection.getInputStream());
              if (fullSkin != null) {
                playerSkin = cropSkinHead(fullSkin);
                skinLoaded = true;
                System.out.println("Skin loaded for: " + name);
              }
            } else {
              System.out.println("Skin not found for: " + name);
            }
            connection.disconnect();
          }
        } catch (Exception e) {
          System.out.println("Failed to load skin: " + e.getMessage());
        }
      }
    }.start();
  }

  private BufferedImage cropSkinHead(BufferedImage fullSkin) {
    try {
      int headSize = 8;
      BufferedImage head = fullSkin.getSubimage(8, 8, headSize, headSize);

      if (fullSkin.getWidth() == 64 && fullSkin.getHeight() == 64) {
        BufferedImage hat = fullSkin.getSubimage(40, 8, 8, 8);
        BufferedImage combined = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics g = combined.getGraphics();
        g.drawImage(head, 0, 0, null);
        g.drawImage(hat, 0, 0, null);
        g.dispose();
        return combined;
      }

      return head;
    } catch (Exception e) {
      return null;
    }
  }

  public boolean canPlayOffline() {
    return gameUpdater.canPlayOffline();
  }

  public void init() {
    if (applet != null) {
      applet.init();
      return;
    }
    init(getParameter("userName"), getParameter("latestVersion"), getParameter("downloadTicket"),
            getParameter("sessionId"));
  }

  public void start() {
    if (applet != null) {
      applet.start();
      return;
    }
    if (gameUpdaterStarted)
      return;

    Thread t = new Thread() {
      public void run() {
        gameUpdater.run();
        try {
          if (!gameUpdater.fatalError) {
            if (gameUpdater.playerSkin != null) {
              playerSkin = cropSkinHead(gameUpdater.playerSkin);
              skinLoaded = true;
            }
            replace(gameUpdater.createApplet());
          }
        } catch (ClassNotFoundException e) {
          e.printStackTrace();
        } catch (InstantiationException e) {
          e.printStackTrace();
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
    };
    t.setDaemon(true);
    t.start();

    t = new Thread() {
      public void run() {
        while (applet == null) {
          repaint();
          try {
            Thread.sleep(10L);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    };
    t.setDaemon(true);
    t.start();

    gameUpdaterStarted = true;
  }

  public void stop() {
    if (applet != null) {
      active = false;
      applet.stop();
      return;
    }
  }

  public void destroy() {
    if (applet != null) {
      applet.destroy();
      return;
    }
  }

  public void replace(Applet applet) {
    try {
      boolean hasFML = false;
      try {
        Class.forName("cpw.mods.fml.relauncher.FMLRelauncher");
        hasFML = true;
      } catch (ClassNotFoundException e) {
      }

      if (hasFML) {
        System.out.println("FML detected, applying fix...");
        System.setProperty("fml.ignoreInvalidCert", "true");
        System.setProperty("fml.ignoreDownloadErrors", "true");
        System.setProperty("fml.skipModLoader", "true");
        System.setProperty("fml.skipClassLoader", "true");
        System.setProperty("fml.noForge", "true");
        System.setProperty("fml.loadedMods", "none");

        try {
          Class<?> fmlClass = Class.forName("cpw.mods.fml.relauncher.FMLRelauncher");
          try {
            java.lang.reflect.Field ignoreField = fmlClass.getDeclaredField("ignoreInvalidCert");
            ignoreField.setAccessible(true);
            ignoreField.setBoolean(null, true);
          } catch (Exception e) {}

          try {
            java.lang.reflect.Field libField = fmlClass.getDeclaredField("libraryDownloadDisabled");
            libField.setAccessible(true);
            libField.setBoolean(null, true);
          } catch (Exception e) {}

          System.out.println("FML disabled via reflection!");
        } catch (Exception e) {
          System.err.println("Could not disable FML: " + e.getMessage());
        }
      }

      this.applet = applet;
      applet.setStub(this);
      applet.setSize(getWidth(), getHeight());

      setLayout(new BorderLayout());
      add(applet, "Center");

      if (forceServerJoin && serverIP != null && !serverIP.isEmpty()) {
        System.out.println("Force joining server: " + serverIP + ":" + serverPort);

        System.setProperty("minecraft.singleplayer", "false");
        System.setProperty("minecraft.multiplayer", "true");
        System.setProperty("minecraft.mp", "true");
        System.setProperty("minecraft.server", serverIP);
        System.setProperty("minecraft.server.port", serverPort);

        injectServerParameters(applet, serverIP, serverPort);

        try {
          String workingDir = Util.getWorkingDirectory().getAbsolutePath();
          File serversFile = new File(workingDir, "servers.dat");

          if (serversFile.exists()) {
            serversFile.delete();
            System.out.println("Deleted old servers.dat");
          }

          DataOutputStream dos = new DataOutputStream(new FileOutputStream(serversFile));
          dos.writeInt(1);
          dos.writeUTF(serverIP + ":" + serverPort);
          dos.writeUTF("AeternaMc");
          dos.writeUTF("");
          dos.writeUTF("");
          dos.writeBoolean(true);
          dos.close();

          System.out.println("Created servers.dat with server: " + serverIP + ":" + serverPort);
        } catch (Exception e) {
          System.out.println("Could not create servers.dat: " + e.getMessage());
        }
      } else {
        System.out.println("No server parameters found, starting in singleplayer mode");
        System.setProperty("minecraft.singleplayer", "true");
        System.setProperty("minecraft.multiplayer", "false");
      }

      applet.init();
      active = true;
      applet.start();
      validate();

      System.out.println("Applet started successfully!");

    } catch (Exception e) {
      System.err.println("Failed to start applet: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void injectServerParameters(Applet applet, String serverIP, String serverPort) {
    try {
      Class<?> appletClass = applet.getClass();
      java.lang.reflect.Field[] fields = appletClass.getDeclaredFields();

      for (java.lang.reflect.Field field : fields) {
        if (java.util.Map.class.isAssignableFrom(field.getType())) {
          field.setAccessible(true);
          @SuppressWarnings("unchecked")
          java.util.Map<String, Object> params = (java.util.Map<String, Object>) field.get(applet);
          if (params != null) {
            params.put("server", serverIP);
            params.put("port", serverPort);
            params.put("mp", "true");
            params.put("singleplayer", "false");
            params.put("multiplayer", "true");
            params.put("server-address", serverIP + ":" + serverPort);
            System.out.println("Injected server parameters via field: " + field.getName());
            return;
          }
        }
      }

      try {
        java.lang.reflect.Field paramField = appletClass.getDeclaredField("parameters");
        paramField.setAccessible(true);
        Object params = paramField.get(applet);
        if (params instanceof java.util.Map) {
          @SuppressWarnings("unchecked")
          java.util.Map<String, String[]> mapParams = (java.util.Map<String, String[]>) params;
          mapParams.put("server", new String[]{serverIP});
          mapParams.put("port", new String[]{serverPort});
          mapParams.put("mp", new String[]{"true"});
          mapParams.put("singleplayer", new String[]{"false"});
          mapParams.put("multiplayer", new String[]{"true"});
          System.out.println("Injected server parameters via parameters field");
          return;
        }
      } catch (NoSuchFieldException e) {
      }

      try {
        java.lang.reflect.Method setParamMethod = appletClass.getMethod("setParameter", String.class, String.class);
        setParamMethod.invoke(applet, "server", serverIP);
        setParamMethod.invoke(applet, "port", serverPort);
        setParamMethod.invoke(applet, "mp", "true");
        System.out.println("Set server parameters via setParameter method");
        return;
      } catch (NoSuchMethodException e) {
      }

      try {
        java.lang.reflect.Method putParamMethod = appletClass.getMethod("putParameter", String.class, String.class);
        putParamMethod.invoke(applet, "server", serverIP);
        putParamMethod.invoke(applet, "port", serverPort);
        putParamMethod.invoke(applet, "mp", "true");
        System.out.println("Set server parameters via putParameter method");
        return;
      } catch (NoSuchMethodException e) {
      }

      System.out.println("Could not inject server parameters - no suitable field or method found");

    } catch (Exception e) {
      System.out.println("Failed to inject server parameters: " + e.getMessage());
    }
  }

  public void update(Graphics g) {
    paint(g);
  }

  public BufferedImage getPlayerSkin() {
    return playerSkin;
  }

  public BufferedImage getPlayerCape() {
    return playerCape;
  }

  public String getUsername() {
    return username;
  }

  private void drawSkinPreview(Graphics g, int x, int y, int size) {
    if (playerSkin == null) return;

    try {
      g.drawImage(playerSkin, x, y, size, size, null);
    } catch (Exception e) {
    }
  }

  private void drawLoadingHead(Graphics g, int w, int h) {
    int headSize = 80;
    int centerX = w / 2 - headSize / 2;
    int centerY = h / 2 - 100;

    if (playerSkin != null) {
      try {
        g.drawImage(playerSkin, centerX, centerY, headSize, headSize, null);
      } catch (Exception e) {
        drawDefaultHead(g, centerX, centerY, headSize);
      }
    } else {
      drawDefaultHead(g, centerX, centerY, headSize);
    }

    g.setColor(Color.WHITE);
    g.setFont(new Font("Arial", Font.BOLD, 18));
    FontMetrics fm = g.getFontMetrics();
    String name = username != null ? username : "Player";
    int textX = w / 2 - fm.stringWidth(name) / 2;
    int textY = centerY + headSize + 30;
    g.drawString(name, textX, textY);
  }

  private void drawDefaultHead(Graphics g, int x, int y, int size) {
    g.setColor(new Color(150, 150, 150));
    g.fillRect(x, y, size, size);
    g.setColor(new Color(100, 100, 100));
    g.fillRect(x + size / 6, y + size / 4, size / 5, size / 5);
    g.fillRect(x + size - size / 6 - size / 5, y + size / 4, size / 5, size / 5);
    g.setColor(new Color(80, 80, 255));
    g.fillRect(x + size / 4 + size / 12, y + size / 3 + size / 12, size / 12, size / 12);
    g.fillRect(x + size - size / 4 - size / 12 - size / 12, y + size / 3 + size / 12, size / 12, size / 12);
  }

  public void paint(Graphics g2) {
    if (applet != null)
      return;

    int w = getWidth() / 2;
    int h = getHeight() / 2;
    if ((img == null) || (img.getWidth() != w) || (img.getHeight() != h)) {
      img = createVolatileImage(w, h);
    }

    Graphics g = img.getGraphics();
    for (int x = 0; x <= w / 32; x++) {
      for (int y = 0; y <= h / 32; y++)
        g.drawImage(bgImage, x * 32, y * 32, null);
    }

    drawLoadingHead(g, w, h);

    if (gameUpdater.pauseAskUpdate) {
      if (!hasMouseListener) {
        hasMouseListener = true;
        addMouseListener(this);
      }
      g.setColor(Color.LIGHT_GRAY);
      String msg = "Обнаружено обновление";
      g.setFont(new Font(null, 1, 20));
      FontMetrics fm = g.getFontMetrics();
      int yOffset = 140;
      g.drawString(msg, w / 2 - fm.stringWidth(msg) / 2, h / 2 + yOffset - fm.getHeight() * 2);

      g.setFont(new Font(null, 0, 12));
      fm = g.getFontMetrics();

      g.fill3DRect(w / 2 - 56 - 8, h / 2 + yOffset, 56, 20, true);
      g.fill3DRect(w / 2 + 8, h / 2 + yOffset, 56, 20, true);

      msg = "Обновить клиент?";
      g.drawString(msg, w / 2 - fm.stringWidth(msg) / 2, h / 2 + yOffset - 8);

      g.setColor(Color.BLACK);
      msg = "Нет";
      g.drawString(msg, w / 2 - 56 - 8 - fm.stringWidth(msg) / 2 + 28, h / 2 + yOffset + 14);
      msg = "Да";
      g.drawString(msg, w / 2 + 8 - fm.stringWidth(msg) / 2 + 28, h / 2 + yOffset + 14);
    } else {
      g.setColor(Color.LIGHT_GRAY);

      String msg = "Updating Minecraft";
      if (gameUpdater.fatalError) {
        msg = "Failed to launch";
      }

      g.setFont(new Font(null, 1, 20));
      FontMetrics fm = g.getFontMetrics();
      int yOffset = 140;
      g.drawString(msg, w / 2 - fm.stringWidth(msg) / 2, h / 2 + yOffset - fm.getHeight() * 2);

      g.setFont(new Font(null, 0, 12));
      fm = g.getFontMetrics();
      msg = gameUpdater.getDescriptionForState();
      if (gameUpdater.fatalError) {
        msg = gameUpdater.fatalErrorDescription;
      }

      g.drawString(msg, w / 2 - fm.stringWidth(msg) / 2, h / 2 + yOffset + fm.getHeight() * 1);
      msg = gameUpdater.subtaskMessage;
      g.drawString(msg, w / 2 - fm.stringWidth(msg) / 2, h / 2 + yOffset + fm.getHeight() * 2);

      if (!gameUpdater.fatalError) {
        g.setColor(Color.black);
        g.fillRect(64, h / 2 + yOffset + 40, w - 128 + 1, 5);
        g.setColor(new Color(32768));
        g.fillRect(64, h / 2 + yOffset + 40, gameUpdater.percentage * (w - 128) / 100, 4);
        g.setColor(new Color(2138144));
        g.fillRect(65, h / 2 + yOffset + 41, gameUpdater.percentage * (w - 128) / 100 - 2, 1);
      }
    }

    g.dispose();
    g2.drawImage(img, 0, 0, w * 2, h * 2, null);
  }

  public void run() {
  }

  public String getParameter(String name) {
    String custom = (String) customParameters.get(name);
    if (custom != null)
      return custom;
    try {
      return super.getParameter(name);
    } catch (Exception e) {
      customParameters.put(name, null);
    }
    return null;
  }

  public void appletResize(int width, int height) {
  }

  public URL getDocumentBase() {
    try {
      return new URL("http://www.minecraft.net/game/");
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }
    return null;
  }

  public void mouseClicked(MouseEvent arg0) {
  }

  public void mouseEntered(MouseEvent arg0) {
  }

  public void mouseExited(MouseEvent arg0) {
  }

  public void mousePressed(MouseEvent me) {
    int x = me.getX() / 2;
    int y = me.getY() / 2;
    int w = getWidth() / 2;
    int h = getHeight() / 2;
    int yOffset = 140;

    if (contains(x, y, w / 2 - 56 - 8, h / 2 + yOffset, 56, 20)) {
      removeMouseListener(this);
      gameUpdater.shouldUpdate = true;
      gameUpdater.pauseAskUpdate = false;
      hasMouseListener = false;
    }
    if (contains(x, y, w / 2 + 8, h / 2 + yOffset, 56, 20)) {
      removeMouseListener(this);
      gameUpdater.shouldUpdate = false;
      gameUpdater.pauseAskUpdate = false;
      hasMouseListener = false;
    }
  }

  private boolean contains(int x, int y, int xx, int yy, int w, int h) {
    return (x >= xx) && (y >= yy) && (x < xx + w) && (y < yy + h);
  }

  public void mouseReleased(MouseEvent arg0) {
  }
}