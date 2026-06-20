// LauncherFrame.java
package net.minecraft;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.UIManager;

public class LauncherFrame extends Frame
{
  public static final int VERSION = 13;
  private static final long serialVersionUID = 1L;
  public Map<String, String> customParameters = new HashMap<String, String>();
  public Launcher launcher;
  public LoginForm loginForm;
  private boolean shouldJoinServer = false;
  private String joinServerIP = null;
  private String joinServerPort = "25565";

  public LauncherFrame()
  {
    super("Minecraft Launcher");

    setServerJoin("AeternaMc.pro", "25565");

    setBackground(Color.BLACK);
    loginForm = new LoginForm(this);
    JPanel p = new JPanel();
    p.setLayout(new BorderLayout());
    p.add(loginForm, "Center");

    p.setPreferredSize(new Dimension(854, 480));

    setLayout(new BorderLayout());
    add(p, "Center");

    pack();
    setLocationRelativeTo(null);
    try
    {
      setIconImage(ImageIO.read(LauncherFrame.class.getResource("favicon.png")));
    } catch (IOException e1) {
      e1.printStackTrace();
    }

    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent arg0) {
        new Thread() {
          public void run() {
            try {
              Thread.sleep(30000L);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
            System.out.println("FORCING EXIT!");
            System.exit(0);
          }
        }
                .start();
        if (launcher != null) {
          launcher.stop();
          launcher.destroy();
        }
        System.exit(0);
      } } );

    downloadServersDat();
  }

  private void downloadServersDat() {
    new Thread() {
      public void run() {
        try {
          String workingDir = Util.getWorkingDirectory().getAbsolutePath();
          File serversFile = new File(workingDir, "servers.dat");

          URL url = new URL("http://87.255.8.103/servers.dat");
          HttpURLConnection connection = (HttpURLConnection) url.openConnection();
          connection.setRequestProperty("User-Agent", "Minecraft Launcher");
          connection.setConnectTimeout(10000);
          connection.setReadTimeout(10000);
          connection.connect();

          if (connection.getResponseCode() == 200) {
            java.io.InputStream is = connection.getInputStream();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(serversFile);
            byte[] buffer = new byte[65536];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
              fos.write(buffer, 0, bytesRead);
            }
            fos.close();
            is.close();
            System.out.println("Downloaded servers.dat from server");
          } else {
            System.out.println("Could not download servers.dat, using default");
            createDefaultServersDat();
          }
          connection.disconnect();
        } catch (Exception e) {
          System.out.println("Failed to download servers.dat: " + e.getMessage());
          createDefaultServersDat();
        }
      }
    }.start();
  }

  private void createDefaultServersDat() {
    try {
      String workingDir = Util.getWorkingDirectory().getAbsolutePath();
      File serversFile = new File(workingDir, "servers.dat");

      DataOutputStream dos = new DataOutputStream(new FileOutputStream(serversFile));
      dos.writeInt(1);
      dos.writeUTF("AeternaMc.pro:25565");
      dos.writeUTF("AeternaMc");
      dos.writeUTF("");
      dos.writeUTF("");
      dos.writeBoolean(true);
      dos.close();

      System.out.println("Created default servers.dat with AeternaMc.pro");
    } catch (Exception e) {
      System.out.println("Failed to create servers.dat: " + e.getMessage());
    }
  }

  public void setServerJoin(String ip, String port) {
    this.shouldJoinServer = true;
    this.joinServerIP = ip;
    this.joinServerPort = port;
    customParameters.put("server", ip);
    customParameters.put("port", port);
    System.out.println("Will join server after launch: " + ip + ":" + port);
  }

  public void playCached(String userName) {
    try {
      if ((userName == null) || (userName.length() <= 0)) {
        userName = "Player";
      }
      launcher = new Launcher();
      launcher.customParameters.putAll(customParameters);
      launcher.customParameters.put("userName", userName);
      if (shouldJoinServer && joinServerIP != null) {
        launcher.customParameters.put("server", joinServerIP);
        launcher.customParameters.put("port", joinServerPort);
      }
      launcher.init();
      removeAll();
      add(launcher, "Center");
      validate();
      launcher.start();
      loginForm = null;
      setTitle("Minecraft");
    } catch (Exception e) {
      e.printStackTrace();
      showError(e.toString());
    }
  }

  public void login(String userName, String password) {
    try {
      String url = "https://authserver.ely.by/auth/authenticate";
      String jsonBody = "{\"username\":\"" + userName + "\",\"password\":\"" + password
              + "\",\"clientToken\":\"" + java.util.UUID.randomUUID().toString()
              + "\",\"requestUser\":true}";
      String result = Util.excutePostJson(url, jsonBody);

      if (result == null || result.trim().isEmpty()) {
        showError("Сервер не отвечает!");
        loginForm.setNoNetwork();
        return;
      }

      String accessToken = extractJsonValue(result, "accessToken");
      String username = extractJsonValue(result, "username");
      String clientToken = extractJsonValue(result, "clientToken");
      String uuid = extractJsonValue(result, "id");
      if (uuid == null) uuid = extractJsonValue(result, "uuid");

      if (accessToken == null || accessToken.isEmpty()) {
        String errorMsg = extractJsonValue(result, "error");
        if (errorMsg == null || errorMsg.isEmpty()) errorMsg = extractJsonValue(result, "errorMessage");
        if (errorMsg == null || errorMsg.isEmpty()) errorMsg = "Неизвестная ошибка";
        showError(errorMsg);
        loginForm.setNoNetwork();
        return;
      }

      launcher = new Launcher();
      launcher.customParameters.putAll(customParameters);
      launcher.customParameters.put("userName", username);
      launcher.customParameters.put("latestVersion", "1.5.2");
      launcher.customParameters.put("downloadTicket", accessToken);
      launcher.customParameters.put("sessionId", accessToken);
      launcher.customParameters.put("clientToken", clientToken);
      launcher.customParameters.put("uuid", uuid);
      launcher.customParameters.put("minecraft.skin.username", username);

      if (shouldJoinServer && joinServerIP != null) {
        launcher.customParameters.put("server", joinServerIP);
        launcher.customParameters.put("port", joinServerPort);
        System.out.println("Login: Will join server " + joinServerIP + ":" + joinServerPort);
      }

      launcher.init(username, "1.5.2", accessToken, accessToken);

      removeAll();
      add(launcher, "Center");
      validate();
      launcher.start();
      loginForm.loginOk();
      loginForm = null;
      setTitle("Minecraft");

    } catch (Exception e) {
      e.printStackTrace();
      showError("Ошибка: " + e.getMessage());
      loginForm.setNoNetwork();
    }
  }

  private String extractJsonValue(String json, String key) {
    String search = "\"" + key + "\":\"";
    int start = json.indexOf(search);
    if (start == -1) return null;
    start += search.length();
    int end = json.indexOf("\"", start);
    if (end == -1) return null;
    return json.substring(start, end);
  }

  private void showError(String error) {
    removeAll();
    add(loginForm);
    loginForm.setError(error);
    validate();
  }

  public boolean canPlayOffline(String userName) {
    Launcher launcher = new Launcher();
    launcher.customParameters.putAll(customParameters);
    launcher.init(userName, null, null, null);
    return launcher.canPlayOffline();
  }

  public static void main(String[] args) {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    }
    catch (Exception localException) {
    }
    LauncherFrame launcherFrame = new LauncherFrame();
    launcherFrame.setVisible(true);
    launcherFrame.customParameters.put("stand-alone", "true");

    if (args.length >= 3) {
      String ip = args[2];
      String port = "25565";
      if (ip.contains(":")) {
        String[] parts = ip.split(":");
        ip = parts[0];
        port = parts[1];
      }
      launcherFrame.setServerJoin(ip, port);
    }

    if (args.length >= 1) {
      launcherFrame.loginForm.userName.setText(args[0]);
      if (args.length >= 2) {
        launcherFrame.loginForm.password.setText(args[1]);
        launcherFrame.loginForm.doLogin();
      }
    }
  }
}