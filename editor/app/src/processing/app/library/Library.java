/*
 Library.java - Library System for Wiring
 Copyright (c) 2006 Nicholas Zambetti.  All right reserved.

 This library is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 2.1 of the License, or (at your option) any later version.

 This library is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Lesser General Public License for more details.

 You should have received a copy of the GNU Lesser General Public
 License along with this library; if not, write to the Free Software
 Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package processing.app.library;

import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import processing.app.Base;
import processing.app.Preferences;
import processing.app.debug.Compiler;
import processing.app.debug.MessageConsumer;
import processing.app.debug.MessageSiphon;
import processing.app.debug.RunnerException;

/*
 * Provides information about and builds a library
 */
public class Library implements MessageConsumer{

  private final File libFolder;
  private File utilityFolder;
  private final LibraryManager libManager;
  RunnerException exception;

  static final String BUGS_URL = "https://developer.berlios.de/bugs/?group_id=3590";
  static final String SUPER_BADNESS = "Compiler error, please submit this code to " + BUGS_URL;

  /*
   * Create a Library
   */
  public Library(LibraryManager manager, File folder)
  {
    libFolder = folder;
    libManager = manager;
    utilityFolder = getUtilityFolder();

    // for debug output
    /*
		 System.out.println("library: " + getName());
		 System.out.println("folder: " + getFolder());
		 System.out.println("utility: " + utilityFolder);
		 System.out.println("built: " + isBuilt());
		 System.out.println("buildable: " + isBuildable());
		 System.out.println("o files: " + getObjectFiles().length);
		 System.out.println("c files: " + getCSourceFiles().length);
		 System.out.println("cpp files: " + getCPPSourceFiles().length);
     */
  }

  /*
   * Directory of library
   * @return File object of library's folder
   */
  public File getFolder()
  {
    return libFolder;
  }

  /*
   * The name of library
   * @return String with library name, derived from folder
   * note: this will be eventually taken from xml description file
   */
  public String getName()
  {
    return libFolder.getName();
  }

  /*
   * Tests if library is built
   * @return True if library has .o files, false otherwise
   */
  public boolean isBuilt()
  {
    File cFiles[] = getCSourceFiles();
    File cppFiles[] = getCPPSourceFiles();
    for (int i = 0; i < cFiles.length; i++) {
      if (!isFresh(cFiles[i], "\\.c")) {
        return false;
      }
    }
    for (int i = 0; i < cppFiles.length; i++) {
      if (!isFresh(cppFiles[i], "\\.cpp")) {
        return false;
      }
    }

    return true;
  }

  public boolean isFresh(File sourceFile, String ending) {
    String name = sourceFile.getPath();
    String objName = name.replaceAll(ending, ".avr.o");
    File objFile = new File(objName);

    if (objFile.exists() && (objFile.lastModified() < sourceFile.lastModified())) {
      return false;
    }
    if (!objFile.exists()) {
      return false;
    }
    return true;
  }

  /*
   * Tests if library is buildable
   * @return True if library has source files, false otherwise
   */
  public boolean isBuildable()
  {
    if(0 < (getCSourceFiles().length + getCPPSourceFiles().length)){
      return true;
    }
    return false;
  }

  /*
   * Tests if library is unbuilt but buildable
   * @return True if library has .cpp files but no .o files, false otherwise
   */
  public boolean isUnbuiltBuildable()
  {
    if(isBuildable()){
      if(!isBuilt()){
        return true;
      }
    }
    return false;
  }

  /*
   * Scans for library "utility" folder
   * @return File object of library's "utility" folder, or null
   */
  private File getUtilityFolder()
  {
    FileFilter filter = new FileFilter() {
      public boolean accept(File file) {
        if(file.isDirectory()){
          if((file.getName()).equalsIgnoreCase("utility")){
            return true;
          }
        }
        return false;
      }
    };
    File[] files = libFolder.listFiles(filter);
    if(files.length > 0){
      return files[0];
    }
    return null;
  }

  /*
   * Finds examples folder 
   * @return "examples" folder as file object or null
   */
  private File getExamplesFolder()
  {
    FileFilter filter = new FileFilter() {
      public boolean accept(File file) {
        if(file.isDirectory()){
          if((file.getName()).equalsIgnoreCase("examples")){
            return true;
          }
        }
        return false;
      }
    };
    File[] files = libFolder.listFiles(filter);
    if(files.length > 0){
      return files[0];
    }
    return null;
  }

  /*
   * Populates example menu or submenu with files
   */
  private void populateWithExamples(File folder, JMenu menu, ActionListener listener) {
    FileFilter onlyfolders = new FileFilter() {
      public boolean accept(File file) {
        return file.isDirectory();
      }
    };
    File[] folders = folder.listFiles(onlyfolders);
    File file;
    JMenu submenu;
    JMenuItem item;
    for(int i = 0; i < folders.length; ++i){
      file = new File(folders[i], folders[i].getName() + ".pde");
      if(file.exists()){
        item = new JMenuItem(folders[i].getName());
        item.setActionCommand(file.getAbsolutePath());
        item.addActionListener(listener);
        menu.add(item);
      }else{
        submenu = new JMenu(folders[i].getName());
        populateWithExamples(folders[i], submenu, listener);
        menu.add(submenu);
      }
    }
  }

  /*
   * Builds and returns an examples menu
   * @return JMenu object with example files, or null if none
   */
  public JMenu getExamplesMenu(ActionListener listener) {
    JMenu submenu;
    File examplesFolder = getExamplesFolder();
    if(null != examplesFolder){
      submenu = new JMenu("Library-" + getName());
      populateWithExamples(examplesFolder, submenu, listener);
      return submenu; 
    }
    return null;
  }

  /*
   * List of object files for linking
   * @return Array of object files as File objects
   */
  private Collection<File> getObjectFiles(File folder)
  {
    FileFilter onlyObjectFiles = new FileFilter() {
      public boolean accept(File file) {
        return (file.getName()).endsWith(".avr.o"); 
      }
    };
    File res[] = folder.listFiles(onlyObjectFiles);
    ArrayList<File> result = new ArrayList();
    for (File f : res) {
      result.add(f);
    }
    return result;
  }

  public Collection<File> getObjectFiles()
  {
    if(null == utilityFolder){
      return getObjectFiles(libFolder);
    }
    ArrayList<File> result = new ArrayList();
    
    result.addAll(getObjectFiles(libFolder));
    result.addAll(getObjectFiles(utilityFolder));
    return result;
  }

  /*
   * List of header source files for inclusion
   * @return Array of header source files as File objects
   */
  public File[] getHeaderFiles()
  {
    FileFilter onlyHFiles = new FileFilter() {
      public boolean accept(File file) {
        return (file.getName()).endsWith(".h");
      }
    };
    return libFolder.listFiles(onlyHFiles);
  }

  public String[] getDepends() {
    java.util.List lines = new ArrayList();
    File dependFile = new File(libFolder, ".depends");
    if (dependFile.exists()) {
      try {
        FileReader fileReader = new FileReader(dependFile);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        String line = null;
        while ((line = bufferedReader.readLine()) != null) {
          line = line.trim();
          if (!line.equals("")) {
            lines.add(line);
          }
        }
        bufferedReader.close();
      } catch (Exception e) {
      }
    }
    return (String [])lines.toArray(new String[lines.size()]);		
  }

  /*
   * List of library's C source files for compiling
   * @return Array of C source files as File objects
   */
  private File[] getCSourceFiles(File folder)
  {
    FileFilter onlyCFiles = new FileFilter() {
      public boolean accept(File file) {
        return (file.getName()).endsWith(".c");
      }
    };
    return folder.listFiles(onlyCFiles);
  }

  private File[] getCSourceFiles()
  {
    if(null == utilityFolder){
      return getCSourceFiles(libFolder);
    }
    File[] librarySources = getCSourceFiles(libFolder);
    File[] utilitySources = getCSourceFiles(utilityFolder);
    File[] sources = new File[librarySources.length + utilitySources.length];
    System.arraycopy(librarySources, 0, sources, 0, librarySources.length);
    System.arraycopy(utilitySources, 0, sources, librarySources.length, utilitySources.length);
    return sources;
  }

  /*
   * List of C++ source files for compiling
   * @return Array of C++ source files as File objects
   */
  private File[] getCPPSourceFiles(File folder)
  {
    FileFilter onlyCPPFiles = new FileFilter() {
      public boolean accept(File file) {
        return (file.getName()).endsWith(".cpp");
      }
    };
    return folder.listFiles(onlyCPPFiles);
  }
  private File[] getCPPSourceFiles()
  {
    if(null == utilityFolder){
      return getCPPSourceFiles(libFolder);
    }
    File[] librarySources = getCPPSourceFiles(libFolder);
    File[] utilitySources = getCPPSourceFiles(utilityFolder);
    File[] sources = new File[librarySources.length + utilitySources.length];
    System.arraycopy(librarySources, 0, sources, 0, librarySources.length);
    System.arraycopy(utilitySources, 0, sources, librarySources.length, utilitySources.length);
    return sources;
  }

  /*
   * Attempt to build library
   * @return true on successful build, false otherwise
   */
  public boolean build() throws RunnerException
  {
    // fail if library is not buildable (contains no sources)
    if(!isBuildable()){
      return false;
    }

    String userdir = System.getProperty("user.dir") + File.separator;
    String avrBasePath = Base.getAvrBasePath();

    java.util.List includePaths = new ArrayList();
    includePaths.add(libManager.getTarget().getPath());
    includePaths.add(getFolder().getPath());

    // use built lib directories in include paths when searching for headers
    // this allows libs to use other libs easily
    utilityFolder = getUtilityFolder(); // refresh status of utility folder
    if(null != utilityFolder){
      includePaths.add(utilityFolder.getPath());
    }
    String[] libDirs = libManager.getFolderPaths();

    for (int i = 0; i < libDirs.length; ++i) {
      includePaths.add(libDirs[i]);
    }

    File[] sourcesC = getCSourceFiles();
    File[] sourcesCPP = getCPPSourceFiles();

    // execute the compiler, and create threads to deal
    // with the input and error streams
    //
    int result = 0;
    try {
      String pathSansExtension;
      Process process;
      boolean compiling = true;

      // compile c sources
      for(int i = 0; i < sourcesC.length; ++i) {
        if (isFresh(sourcesC[i], ".c"))
          continue;

        pathSansExtension = sourcesC[i].getPath();
        pathSansExtension = pathSansExtension.substring(0, pathSansExtension.length() - 2); // -2 because ".c"

        File objFile = new File(pathSansExtension + ".avr.o");
        objFile.delete();

        java.util.List commandList = Compiler.getCommandCompilerC(avrBasePath, includePaths, sourcesC[i].getPath(), pathSansExtension + ".avr.o");
        if (execAsynchronously(commandList) != 0) {
          return false;
        }
      }

      // compile c++ sources
      for(int i = 0; i < sourcesCPP.length; ++i) {
        if (isFresh(sourcesCPP[i], ".cpp"))
          continue;

        pathSansExtension = sourcesCPP[i].getPath();
        pathSansExtension = pathSansExtension.substring(0, pathSansExtension.length() - 4); // -4 because ".cpp"

        java.util.List commandList = Compiler.getCommandCompilerCPP(avrBasePath, includePaths, sourcesCPP[i].getPath(), pathSansExtension + ".avr.o");
        if (execAsynchronously(commandList) != 0) {
          return false;
        }
      }
    } catch (Exception e) {
      String msg = e.getMessage();
      if ((msg != null) && (msg.indexOf("avr-gcc: not found") != -1)) {
        Base.showWarning("Compiler error",
                         "Could not find the compiler.\n" +
                         "avr-gcc is missing from your PATH,\n" +
                         "see readme.txt for help.", null);
        return false;

      } else if ((msg != null) && (msg.indexOf("avr-g++: not found") != -1)) {
        Base.showWarning("Compiler error",
                         "Could not find the compiler.\n" +
                         "avr-g++ is missing from your PATH,\n" +
                         "see readme.txt for help.", null);
        return false;

      } else {
        e.printStackTrace();
        result = -1;
      }
    }

    // an error was queued up by message()
    if (exception != null)  {
      throw exception;
    }

    if (result != 0 && result != 1 ) {
      Base.openURL(BUGS_URL);
      throw new RunnerException(SUPER_BADNESS);
    }

    // success would mean that 'result' is set to zero
    return (result == 0); // ? true : false;
  }

  public int execAsynchronously(java.util.List commandList)
  throws RunnerException, IOException {
    String[] command = new String[commandList.size()];
    commandList.toArray(command);
    int result = 0;

    if (Preferences.getBoolean("build.verbose")) {
      for(int j = 0; j < command.length; j++) {
        System.out.print(command[j] + " ");
      }
      System.out.println();
    }

    Process process = Runtime.getRuntime().exec(command);

    new MessageSiphon(process.getInputStream(), this);
    new MessageSiphon(process.getErrorStream(), this);

    // wait for the process to finish.  if interrupted
    // before waitFor returns, continue waiting
    boolean compiling = true;
    while (compiling) {
      try {
        result = process.waitFor();
        compiling = false;
      } catch (InterruptedException ignored) { }
    }

    if (exception != null)  {
      throw exception;
    }

    return result;
  }


  /**
   * Part of the MessageConsumer interface, this is called
   * whenever a piece (usually a line) of error message is spewed
   * out from the compiler. The errors are parsed for their contents
   * and line number, which is then reported back to Editor.
   */
  public void message(String inString) {
    // This receives messages as full lines, so a newline needs
    // to be added as they're printed to the console.

    // always print all compilation output for library writers!
    String outString = "";

    // shorten file paths so that they are friendlier
    int start = 0;
    int end = 0;
    String substring = libFolder.getPath() + File.separator;
    StringBuffer result = new StringBuffer();
    while ((end = inString.indexOf(substring, start)) >= 0) {
      result.append(inString.substring(start, end));
      start = end + substring.length();
    }
    result.append(inString.substring(start));
    outString = result.toString();

    System.err.print(outString);

    // prepare error for throwing
    if (inString.indexOf("error") != -1){
      exception = new RunnerException("Error building library \"" + getName() + "\"");
    }
  }

  public boolean hasHeaderFile(String fileName) {
    for (File headerFile : getHeaderFiles()) {
      if (headerFile.getName().equals(fileName))
        return true;
    }
    return false;
  }
}
