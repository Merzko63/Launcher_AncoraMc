package net.minecraft;

import java.applet.Applet;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PrivilegedExceptionAction;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;

import SevenZip.LzmaAlone;

public class GameUpdater implements Runnable {
  public static final int STATE_INIT = 1;
  public static final int STATE_DETERMINING_PACKAGES = 2;
  public static final int STATE_CHECKING_CACHE = 3;
  public static final int STATE_DOWNLOADING = 4;
  public static final int STATE_EXTRACTING_PACKAGES = 5;
  public static final int STATE_UPDATING_CLASSPATH = 6;
  public static final int STATE_SWITCHING_APPLET = 7;
  public static final int STATE_INITIALIZE_REAL_APPLET = 8;
  public static final int STATE_START_REAL_APPLET = 9;
  public static final int STATE_DONE = 10;

  public int percentage;
  public int currentSizeDownload;
  public int totalSizeDownload;
  public int currentSizeExtract;
  public int totalSizeExtract;
  protected URL[] urlList;
  private static ClassLoader classLoader;
  protected Thread loaderThread;
  protected Thread animationThread;
  public boolean fatalError;
  public String fatalErrorDescription;
  protected String subtaskMessage = "";
  protected int state = 1;

  protected boolean lzmaSupported = false;
  protected boolean pack200Supported = false;

  protected String[] genericErrorMessage = {
          "An error occured while loading the applet.", "Please contact support to resolve this issue.", "<placeholder for error message>" };
  protected boolean certificateRefused;
  protected String[] certificateRefusedMessage = {
          "Permissions for Applet Refused.", "Please accept the permissions dialog to allow", "the applet to continue the loading process." };

  protected static boolean natives_loaded = false;
  public static boolean forceUpdate = false;
  private String latestVersion;
  private String mainGameUrl;
  public boolean pauseAskUpdate;
  public boolean shouldUpdate;
  public BufferedImage playerSkin;
  public String username;

  public GameUpdater(String latestVersion, String mainGameUrl) {
    this.latestVersion = latestVersion;
    this.mainGameUrl = mainGameUrl;
  }

  public void init() {
    state = 1;
    try {
      Class.forName("LZMA.LzmaInputStream");
      lzmaSupported = true;
    } catch (Throwable localThrowable) {
    }
    try {
      Pack200.class.getSimpleName();
      pack200Supported = true;
    } catch (Throwable localThrowable1) {
    }
  }

  private String generateStacktrace(Exception exception) {
    Writer result = new StringWriter();
    PrintWriter printWriter = new PrintWriter(result);
    exception.printStackTrace(printWriter);
    return result.toString();
  }

  protected String getDescriptionForState() {
    switch (state) {
      case 1:
        return "Initializing updater";
      case 2:
        return "Determining packages";
      case 3:
        return "Checking cache";
      case 4:
        return "Downloading files";
      case 5:
        return "Extracting packages";
      case 6:
        return "Updating classpath";
      case 7:
        return "Switching applet";
      case 8:
        return "Initializing real applet";
      case 9:
        return "Starting real applet";
      case 10:
        return "Done";
    }
    return "Unknown state";
  }

  protected String trimExtensionByCapabilities(String file) {
    if (!pack200Supported) {
      file = file.replaceAll(".pack", "");
    }
    if (!lzmaSupported) {
      file = file.replaceAll(".lzma", "");
    }
    return file;
  }

  protected void loadJarURLs() throws Exception {
    state = 2;
    System.out.println("Loading JAR URLs...");

    String jarList = "lwjgl.jar, jinput.jar, lwjgl_util.jar, " + mainGameUrl;
    jarList = trimExtensionByCapabilities(jarList);
    System.out.println("JAR list: " + jarList);

    StringTokenizer jar = new StringTokenizer(jarList, ", ");
    int jarCount = jar.countTokens() + 1;
    urlList = new URL[jarCount];

    URL path = new URL("https://AncoraMc.pro/Launch/");
    System.out.println("Base URL: " + path);

    for (int i = 0; i < jarCount - 1; i++) {
      String jarName = jar.nextToken();
      urlList[i] = new URL(path, jarName);
      System.out.println("Added JAR URL[" + i + "]: " + urlList[i]);
    }

    String osName = System.getProperty("os.name");
    String nativeJar = null;

    if (osName.startsWith("Win"))
      nativeJar = "windows_natives.jar";
    else if (osName.startsWith("Linux"))
      nativeJar = "linux_natives.jar";
    else if (osName.startsWith("Mac"))
      nativeJar = "macosx_natives.jar";
    else if ((osName.startsWith("Solaris")) || (osName.startsWith("SunOS")))
      nativeJar = "solaris_natives.jar";
    else {
      fatalErrorOccured("OS (" + osName + ") is not supported", null);
    }

    if (nativeJar == null) {
      fatalErrorOccured("lwjgl natives not found", null);
    } else {
      nativeJar = trimExtensionByCapabilities(nativeJar);
      urlList[jarCount - 1] = new URL(path, nativeJar);
      System.out.println("Added natives URL: " + urlList[jarCount - 1]);
    }
  }

  public void run() {
    init();
    state = 3;
    percentage = 5;
    try {
      loadJarURLs();

      String path = (String) AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
        public Object run() throws Exception {
          return Util.getWorkingDirectory() + File.separator + "bin" + File.separator;
        }
      });
      File dir = new File(path);
      System.out.println("Working directory: " + path);

      if (!dir.exists()) {
        dir.mkdirs();
        System.out.println("Created directory: " + path);
      }

      System.out.println("Force update: " + forceUpdate);

      if (!forceUpdate && validateCache(dir)) {
        try {
          File versionFile = new File(dir, "version");
          if (versionFile.exists()) {
            String cachedVersion = readVersionFile(versionFile);
            if (cachedVersion != null && cachedVersion.equals(latestVersion)) {
              shouldUpdate = false;
              System.out.println("Cache is valid and up to date, skipping download");
              updateClassPath(dir);
              state = 10;
              System.out.println("Update completed successfully!");
              return;
            }
          }
        } catch (Exception e) {
          System.out.println("Error reading version, will update: " + e.getMessage());
        }
      }

      shouldUpdate = true;

      downloadJars(path);
      extractJars(path);
      extractNatives(path);

      if (latestVersion != null) {
        percentage = 90;
        File versionFile = new File(dir, "version");
        writeVersionFile(versionFile, latestVersion);
      }

      updateClassPath(dir);
      state = 10;
      System.out.println("Update completed successfully!");
    } catch (AccessControlException ace) {
      fatalErrorOccured("Недостаточно прав для записи в папку .minecraft! Запустите лаунчер от имени администратора.", ace);
      certificateRefused = true;
    } catch (Exception e) {
      fatalErrorOccured(e.getMessage(), e);
    } finally {
      loaderThread = null;
    }
  }

  private boolean validateCache(File dir) {
    try {
      File versionFile = new File(dir, "version");
      if (!versionFile.exists()) return false;

      String[] requiredJars = {"lwjgl.jar", "jinput.jar", "lwjgl_util.jar", "minecraft.jar"};
      for (String jar : requiredJars) {
        File jarFile = new File(dir, jar);
        if (!jarFile.exists() || jarFile.length() < 1000) {
          System.out.println("Missing or corrupted: " + jar);
          return false;
        }
      }

      File nativesDir = new File(dir, "natives");
      if (!nativesDir.exists() || nativesDir.list().length == 0) {
        return false;
      }

      String version = readVersionFile(versionFile);
      return version != null && version.length() > 0;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  private void checkShouldUpdate() {
    pauseAskUpdate = true;
    while (pauseAskUpdate)
      try {
        Thread.sleep(1000L);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
  }

  protected String readVersionFile(File file) throws Exception {
    DataInputStream dis = new DataInputStream(new FileInputStream(file));
    String version = dis.readUTF();
    dis.close();
    return version;
  }

  protected void writeVersionFile(File file, String version) throws Exception {
    DataOutputStream dos = new DataOutputStream(new FileOutputStream(file));
    dos.writeUTF(version);
    dos.close();
  }

  protected void updateClassPath(File dir) throws Exception {
    state = 6;
    percentage = 95;
    System.out.println("Updating classpath...");

    if (username == null || username.isEmpty()) {
      username = System.getProperty("minecraft.skin.username");
      if (username == null || username.isEmpty()) {
        username = "Player";
      }
    }
    System.setProperty("minecraft.skin.username", username);
    System.out.println("Username for skins: " + username);

    disableForge();

    System.setProperty("minecraft.skin.url.pattern", "https://skinsystem.ely.by/skins/%s.png");
    System.setProperty("minecraft.cape.url.pattern", "https://skinsystem.ely.by/cloaks/%s.png");
    System.setProperty("skins.minecraft.net", "skinsystem.ely.by");
    System.setProperty("session.minecraft.net", "skinsystem.ely.by");
    System.setProperty("minecraft.skins.enabled", "true");
    System.setProperty("minecraft.skins.provider", "elyby");

    File[] jarFiles = dir.listFiles(new java.io.FilenameFilter() {
      public boolean accept(File dir, String name) {
        return name.endsWith(".jar") && !name.contains("natives");
      }
    });

    if (jarFiles == null || jarFiles.length == 0) {
      throw new Exception("No jar files found in " + dir.getAbsolutePath());
    }

    for (File jarFile : jarFiles) {
      System.out.println("Checking: " + jarFile.getName() + " size: " + jarFile.length());
      try {
        JarFile jf = new JarFile(jarFile);
        Enumeration<JarEntry> entries = jf.entries();
        int count = 0;
        while (entries.hasMoreElements() && count < 10) {
          String name = entries.nextElement().getName();
          if (name.endsWith(".class")) {
            System.out.println("  Contains: " + name);
            count++;
          }
        }
        jf.close();
      } catch (Exception e) {
        System.err.println("Error reading " + jarFile.getName() + ": " + e.getMessage());
      }
    }

    java.util.ArrayList<URL> urlList = new java.util.ArrayList<>();

    File lwjglJar = new File(dir, "lwjgl.jar");
    if (lwjglJar.exists()) {
      urlList.add(lwjglJar.toURI().toURL());
      System.out.println("Added lwjgl.jar first");
    }

    for (File jarFile : jarFiles) {
      if (!jarFile.getName().equals("lwjgl.jar")) {
        urlList.add(jarFile.toURI().toURL());
        System.out.println("Added: " + jarFile.getName());
      }
    }

    URL[] urls = urlList.toArray(new URL[0]);

    classLoader = new URLClassLoader(urls, ClassLoader.getSystemClassLoader().getParent()) {
      @Override
      protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (name.contains("Skin") || name.contains("Cape") || name.contains("Texture")) {
          System.out.println("Loading skin related class: " + name);
        }
        return super.findClass(name);
      }

      @Override
      public URL getResource(String name) {
        URL url = super.getResource(name);
        if (url != null && name.contains("skin")) {
          System.out.println("Found skin resource: " + name + " -> " + url);
        }
        return url;
      }
    };

    try {
      Class<?> lwjglClass = classLoader.loadClass("org.lwjgl.LWJGLException");
      System.out.println("LWJGLException loaded successfully!");
    } catch (ClassNotFoundException e) {
      System.err.println("LWJGLException NOT found in classpath!");
      System.err.println("Available URLs in classloader:");
      for (URL url : urls) {
        System.err.println("  " + url);
      }
      throw e;
    }

    try {
      Class<?> testClass = classLoader.loadClass("net.minecraft.client.MinecraftApplet");
      System.out.println("MinecraftApplet loaded successfully!");
    } catch (ClassNotFoundException e) {
      System.err.println("MinecraftApplet NOT found!");
      throw e;
    }

    System.setProperty("minecraft.player.username", username);
    System.setProperty("user.name", username);

    String path = dir.getAbsolutePath();
    if (!path.endsWith(File.separator)) path = path + File.separator;

    System.setProperty("org.lwjgl.librarypath", path + "natives");
    System.setProperty("net.java.games.input.librarypath", path + "natives");

    System.setProperty("minecraft.skins.enabled", "true");
    System.setProperty("minecraft.skins.provider", "elyby");
    System.setProperty("minecraft.skins.baseurl", "https://skinsystem.ely.by/");

    Thread.currentThread().setContextClassLoader(classLoader);
    System.out.println("Context ClassLoader set to URLClassLoader");

    natives_loaded = true;
    System.out.println("Classpath updated successfully with Ely.by skin support!");

    loadPlayerSkin();
  }

  private void disableForge() {
    System.setProperty("fml.ignoreInvalidCert", "true");
    System.setProperty("fml.ignoreDownloadErrors", "true");
    System.setProperty("fml.skipModLoader", "true");
    System.setProperty("fml.skipClassLoader", "true");
    System.setProperty("fml.noForge", "true");
    System.setProperty("fml.loadedMods", "none");
    System.setProperty("forge.ignoreInvalidMinecraftCertificates", "true");
    System.setProperty("forge.ignoreInvalidMods", "true");
    System.setProperty("forge.offline", "true");
    System.setProperty("forge.noMods", "true");
    System.setProperty("fml.forceNoMods", "true");
    System.setProperty("fml.modStates", "");
    System.setProperty("fml.mods", "");
  }

  private void loadPlayerSkin() {
    try {
      if (username != null && !username.isEmpty()) {
        for (int attempt = 0; attempt < 3; attempt++) {
          try {
            URL skinUrl = new URL("https://skinsystem.ely.by/skins/" + username + ".png");
            HttpURLConnection connection = (HttpURLConnection) skinUrl.openConnection();
            connection.setRequestProperty("User-Agent", "Minecraft Launcher");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.connect();

            if (connection.getResponseCode() == 200) {
              playerSkin = javax.imageio.ImageIO.read(connection.getInputStream());
              System.out.println("Player skin loaded for: " + username);
              connection.disconnect();
              break;
            }
            connection.disconnect();
            Thread.sleep(1000);
          } catch (Exception e) {
            System.out.println("Skin attempt " + (attempt + 1) + " failed: " + e.getMessage());
            if (attempt == 2) {
              playerSkin = createDefaultSkin();
            }
          }
        }
      }
    } catch (Exception e) {
      System.out.println("Failed to load player skin: " + e.getMessage());
      playerSkin = createDefaultSkin();
    }
  }

  private BufferedImage createDefaultSkin() {
    BufferedImage defaultSkin = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
    java.awt.Graphics g = defaultSkin.getGraphics();
    g.setColor(new java.awt.Color(150, 150, 150));
    g.fillRect(0, 0, 8, 8);
    g.setColor(new java.awt.Color(100, 100, 100));
    g.fillRect(2, 2, 2, 2);
    g.fillRect(4, 2, 2, 2);
    g.setColor(new java.awt.Color(80, 80, 255));
    g.fillRect(3, 3, 2, 2);
    g.dispose();
    return defaultSkin;
  }

  public Applet createApplet() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
    Class<?> appletClass = classLoader.loadClass("net.minecraft.client.MinecraftApplet");
    return (Applet) appletClass.newInstance();
  }

  protected void downloadJars(String path) throws Exception {
    state = 4;
    System.out.println("Starting download...");

    byte[] buffer = new byte[65536];
    totalSizeDownload = 0;
    currentSizeDownload = 0;

    for (int i = 0; i < urlList.length; i++) {
      String currentFile = getFileName(urlList[i]);
      System.out.println("Downloading: " + currentFile + " from " + urlList[i]);

      File outputFile = new File(path + currentFile);

      if (outputFile.exists()) {
        outputFile.delete();
        System.out.println("Deleted old file: " + outputFile.getAbsolutePath());
      }

      URLConnection urlconnection = urlList[i].openConnection();
      urlconnection.setRequestProperty("Cache-Control", "no-cache");
      urlconnection.setConnectTimeout(10000);
      urlconnection.setReadTimeout(30000);
      urlconnection.connect();

      int fileSize = urlconnection.getContentLength();
      System.out.println("File size: " + fileSize + " bytes");

      if (fileSize <= 0) {
        System.err.println("Warning: File size is 0 or unknown for " + currentFile);
      }

      totalSizeDownload += fileSize;
    }

    int initialPercentage = 10;
    percentage = initialPercentage;

    for (int i = 0; i < urlList.length; i++) {
      String currentFile = getFileName(urlList[i]);
      boolean downloaded = false;

      for (int attempt = 0; attempt < 3 && !downloaded; attempt++) {
        try {
          System.out.println("Downloading file " + (i + 1) + "/" + urlList.length + ": " + currentFile + " (attempt " + (attempt + 1) + ")");

          URLConnection urlconnection = urlList[i].openConnection();
          urlconnection.setRequestProperty("Cache-Control", "no-cache");
          urlconnection.setConnectTimeout(10000);
          urlconnection.setReadTimeout(30000);
          urlconnection.connect();

          InputStream inputstream = urlconnection.getInputStream();
          FileOutputStream fos = new FileOutputStream(path + currentFile);

          int fileSize = urlconnection.getContentLength();
          int downloadedBytes = 0;
          int bytesRead;

          while ((bytesRead = inputstream.read(buffer, 0, buffer.length)) != -1) {
            fos.write(buffer, 0, bytesRead);
            downloadedBytes += bytesRead;
            currentSizeDownload += bytesRead;

            if (fileSize > 0) {
              percentage = initialPercentage + (currentSizeDownload * 45 / totalSizeDownload);
              int progress = downloadedBytes * 100 / fileSize;
              subtaskMessage = "Downloading: " + currentFile + " " + progress + "%";
              if (progress % 10 == 0) {
                System.out.println(subtaskMessage);
              }
            }
          }

          inputstream.close();
          fos.close();

          File downloadedFile = new File(path + currentFile);
          System.out.println("Downloaded: " + currentFile + " (" + downloadedFile.length() + " bytes)");

          if (downloadedFile.length() == 0) {
            throw new Exception("Failed to download " + currentFile + " - file is empty");
          }

          downloaded = true;
        } catch (IOException e) {
          System.err.println("Download failed: " + e.getMessage());
          if (attempt == 2) throw e;
          Thread.sleep(2000);
        }
      }
    }

    subtaskMessage = "";
    System.out.println("All files downloaded successfully!");
  }

  protected InputStream getJarInputStream(String currentFile, final URLConnection urlconnection) throws Exception {
    final InputStream[] is = new InputStream[1];

    for (int j = 0; (j < 3) && (is[0] == null); j++) {
      Thread t = new Thread() {
        public void run() {
          try {
            is[0] = urlconnection.getInputStream();
          } catch (IOException localIOException) {
          }
        }
      };
      t.setName("JarInputStreamThread");
      t.start();

      int iterationCount = 0;
      while ((is[0] == null) && (iterationCount++ < 5)) {
        try {
          t.join(1000L);
        } catch (InterruptedException localInterruptedException) {
        }
      }
      if (is[0] != null) continue;
      try {
        t.interrupt();
        t.join();
      } catch (InterruptedException localInterruptedException1) {
      }
    }

    if (is[0] == null) {
      if (currentFile.equals("minecraft.jar")) {
        throw new Exception("Unable to download " + currentFile);
      }
      throw new Exception("Unable to download " + currentFile);
    }

    return is[0];
  }

  protected void extractLZMA(String in, String out) throws Exception {
    File f = new File(in);
    if (!f.exists()) {
      System.out.println("LZMA file not found: " + in);
      return;
    }
    File fout = new File(out);
    System.out.println("Extracting LZMA: " + in + " -> " + out);
    LzmaAlone.decompress(f, fout);
    f.delete();
    System.out.println("LZMA extraction complete");
  }

  protected void extractPack(String in, String out) throws Exception {
    File f = new File(in);
    if (!f.exists()) {
      System.out.println("PACK file not found: " + in);
      return;
    }

    System.out.println("Extracting PACK: " + in + " -> " + out);
    FileOutputStream fostream = new FileOutputStream(out);
    JarOutputStream jostream = new JarOutputStream(fostream);

    Pack200.Unpacker unpacker = Pack200.newUnpacker();
    unpacker.unpack(f, jostream);
    jostream.close();
    f.delete();
    System.out.println("PACK extraction complete");
  }

  protected void extractJars(String path) throws Exception {
    state = 5;
    System.out.println("Extracting JARs...");

    float increment = 10.0F / urlList.length;

    for (int i = 0; i < urlList.length; i++) {
      percentage = (55 + (int) (increment * (i + 1)));
      String filename = getFileName(urlList[i]);
      System.out.println("Processing: " + filename);

      if (filename.endsWith(".pack.lzma")) {
        subtaskMessage = ("Extracting: " + filename + " to " + filename.replaceAll(".lzma", ""));
        extractLZMA(path + filename, path + filename.replaceAll(".lzma", ""));
        subtaskMessage = ("Extracting: " + filename.replaceAll(".lzma", "") + " to " + filename.replaceAll(".pack.lzma", ""));
        extractPack(path + filename.replaceAll(".lzma", ""), path + filename.replaceAll(".pack.lzma", ""));
      } else if (filename.endsWith(".pack")) {
        subtaskMessage = ("Extracting: " + filename + " to " + filename.replace(".pack", ""));
        extractPack(path + filename, path + filename.replace(".pack", ""));
      } else if (filename.endsWith(".lzma")) {
        subtaskMessage = ("Extracting: " + filename + " to " + filename.replace(".lzma", ""));
        extractLZMA(path + filename, path + filename.replace(".lzma", ""));
      }
    }
    System.out.println("JAR extraction complete");
  }

  protected void extractNatives(String path) throws Exception {
    state = 5;
    System.out.println("Extracting natives...");

    int initialPercentage = percentage;

    String nativeJar = getJarName(urlList[(urlList.length - 1)]);
    System.out.println("Native JAR: " + nativeJar);

    Certificate[] certificate = Launcher.class.getProtectionDomain().getCodeSource().getCertificates();

    if (certificate == null) {
      URL location = Launcher.class.getProtectionDomain().getCodeSource().getLocation();
      JarURLConnection jurl = (JarURLConnection) new URL("jar:" + location.toString() + "!/net/minecraft/Launcher.class").openConnection();
      jurl.setDefaultUseCaches(true);
      try {
        certificate = jurl.getCertificates();
      } catch (Exception localException) {
      }
    }
    File nativeFolder = new File(path + "natives");
    if (!nativeFolder.exists()) {
      nativeFolder.mkdir();
      System.out.println("Created natives folder: " + nativeFolder.getAbsolutePath());
    }

    File file = new File(path + nativeJar);
    if (!file.exists()) {
      System.out.println("Native JAR not found: " + file.getAbsolutePath());
      return;
    }

    System.out.println("Extracting natives from: " + file.getAbsolutePath());
    JarFile jarFile = new JarFile(file, true);
    Enumeration<?> entities = jarFile.entries();

    totalSizeExtract = 0;
    while (entities.hasMoreElements()) {
      JarEntry entry = (JarEntry) entities.nextElement();
      if ((entry.isDirectory()) || (entry.getName().indexOf('/') != -1)) {
        continue;
      }
      totalSizeExtract = (int) (totalSizeExtract + entry.getSize());
    }

    currentSizeExtract = 0;
    entities = jarFile.entries();

    while (entities.hasMoreElements()) {
      JarEntry entry = (JarEntry) entities.nextElement();
      if ((entry.isDirectory()) || (entry.getName().indexOf('/') != -1)) {
        continue;
      }
      File f = new File(path + "natives" + File.separator + entry.getName());
      if (f.exists()) {
        f.delete();
      }

      InputStream in = jarFile.getInputStream(jarFile.getEntry(entry.getName()));
      OutputStream out = new FileOutputStream(path + "natives" + File.separator + entry.getName());

      byte[] buffer = new byte[65536];
      int bufferSize;
      while ((bufferSize = in.read(buffer, 0, buffer.length)) != -1) {
        out.write(buffer, 0, bufferSize);
        currentSizeExtract += bufferSize;
        if (totalSizeExtract > 0) {
          percentage = (initialPercentage + currentSizeExtract * 20 / totalSizeExtract);
          subtaskMessage = ("Extracting: " + entry.getName() + " " + currentSizeExtract * 100 / totalSizeExtract + "%");
        }
      }

      validateCertificateChain(certificate, entry.getCertificates());
      in.close();
      out.close();
      System.out.println("Extracted: " + entry.getName());
    }
    subtaskMessage = "";
    jarFile.close();

    File f = new File(path + nativeJar);
    f.delete();
    System.out.println("Natives extraction complete");
  }

  protected static void validateCertificateChain(Certificate[] ownCerts, Certificate[] native_certs) throws Exception {
    if (ownCerts == null) return;
    if (native_certs == null) throw new Exception("Unable to validate certificate chain. Native entry did not have a certificate chain at all");

    if (ownCerts.length != native_certs.length) throw new Exception("Unable to validate certificate chain. Chain differs in length [" + ownCerts.length + " vs " + native_certs.length + "]");

    for (int i = 0; i < ownCerts.length; i++)
      if (!ownCerts[i].equals(native_certs[i]))
        throw new Exception("Certificate mismatch: " + ownCerts[i] + " != " + native_certs[i]);
  }

  protected String getJarName(URL url) {
    String fileName = url.getFile();
    if (fileName.contains("?")) {
      fileName = fileName.substring(0, fileName.indexOf("?"));
    }
    if (fileName.endsWith(".pack.lzma"))
      fileName = fileName.replaceAll(".pack.lzma", "");
    else if (fileName.endsWith(".pack"))
      fileName = fileName.replaceAll(".pack", "");
    else if (fileName.endsWith(".lzma")) {
      fileName = fileName.replaceAll(".lzma", "");
    }
    return fileName.substring(fileName.lastIndexOf('/') + 1);
  }

  protected String getFileName(URL url) {
    String fileName = url.getFile();
    if (fileName.contains("?")) {
      fileName = fileName.substring(0, fileName.indexOf("?"));
    }
    return fileName.substring(fileName.lastIndexOf('/') + 1);
  }

  protected void fatalErrorOccured(String error, Exception e) {
    if (e != null) {
      e.printStackTrace();
    }
    fatalError = true;
    fatalErrorDescription = ("Fatal error occured (" + state + "): " + error);
    System.out.println(fatalErrorDescription);
    if (e != null) {
      System.out.println(generateStacktrace(e));
    }
  }

  public boolean canPlayOffline() {
    try {
      String path = (String) AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
        public Object run() throws Exception {
          return Util.getWorkingDirectory() + File.separator + "bin" + File.separator;
        }
      });
      File dir = new File(path);
      if (!dir.exists()) return false;

      dir = new File(dir, "version");
      if (!dir.exists()) return false;

      if (dir.exists()) {
        String version = readVersionFile(dir);
        if ((version != null) && (version.length() > 0))
          return true;
      }
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
    return false;
  }
}