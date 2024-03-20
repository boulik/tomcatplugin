/*
 * The MIT License (c) Copyright Sysdeo SA 2001-2002 (c) Copyright Eclipse Tomcat Plugin 2014-2016
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package net.sf.eclipse.tomcat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.JavaRuntime;

import net.sf.eclipse.tomcat.editors.ProjectListElement;

/**
 * Start and stop Tomcat Subclasses contains all information specific to a Tomcat Version
 */
public abstract class TomcatBootstrap {

  private static final String WEBAPP_CLASSPATH_FILENAME = ".#webclasspath";
  private static final int RUN = 1;
  private static final int LOG = 2;
  private static final int ADD_LAUNCH = 3;

  private final String label;

  public abstract String[] getClasspath();

  public abstract String[] getVmArgs();

  public abstract String[] getPrgArgs(String command);

  public abstract String getStartCommand();

  public abstract String getStopCommand();

  public abstract String getMainClass();

  abstract public String getContextWorkDir(String workFolder);

  abstract public IPath getServletJarPath();

  abstract public IPath getJasperJarPath();

  abstract public IPath getJSPJarPath();

  TomcatBootstrap(String label) {
	  this.label = label;
  }

  public Collection<IClasspathEntry> getTomcatJars() {
    IPath tomcatHomePath = TomcatLauncherPlugin.getDefault().getTomcatIPath();
    ArrayList<IClasspathEntry> jars = new ArrayList<IClasspathEntry>();

    if (this.getServletJarPath() != null) {
      jars.add(JavaCore.newVariableEntry(tomcatHomePath.append(this.getServletJarPath()), null, null));
    }

    if (this.getJasperJarPath() != null) {
      jars.add(JavaCore.newVariableEntry(tomcatHomePath.append(this.getJasperJarPath()), null, null));
    }

    if (this.getJSPJarPath() != null) {
      jars.add(JavaCore.newVariableEntry(tomcatHomePath.append(this.getJSPJarPath()), null, null));
    }

    return jars;
  }

  /**
   * Return the tag that will be used to find where context definition should be added in server.xml
   */
  public abstract String getXMLTagAfterContextDefinition();

  /**
   * See %TOMCAT_HOME%/bin/startup.bat
   */
  public void start() throws CoreException {
    this.runTomcatBootstrap(getStartCommand(), true, RUN, false);
  }

  /**
   * See %TOMCAT_HOME%/bin/shutdown.bat
   */
  public void stop() throws CoreException {
    this.runTomcatBootstrap(getStopCommand(), false, RUN, false);
  }

  /**
   * Simply stop and start
   */
  public void restart() throws CoreException {
    this.stop();

    // Hack, need more testings
    try {
      Thread.sleep(5000);
    } catch (InterruptedException ex) {
      // ignore exception
    }

    this.start();
  }

  /**
   * Write tomcat launch configuration to .metadata/.log
   */
  public void logConfig() throws CoreException {
    this.runTomcatBootstrap(getStartCommand(), true, LOG, false);
  }

  /**
   * Create an Eclipse launch configuration
   */
  public void addLaunch() throws CoreException {
    this.runTomcatBootstrap(getStartCommand(), true, ADD_LAUNCH, true);
  }

  /**
   * Launch a new JVM running Tomcat Main class Set classpath, bootclasspath and environment
   * variable
   */
  private void runTomcatBootstrap(String tomcatBootOption, boolean showInDebugger, int action, boolean saveConfig) throws CoreException {
	  String[] prgArgs = this.getPrgArgs(tomcatBootOption);

	  IProject[] projects = TomcatLauncherPlugin.getWorkspace().getRoot().getProjects();

	  for (int i = 0; i < projects.length; i++) {
		  if (!projects[i].isOpen()) {
			  continue;
		  }
		  TomcatProject tomcatProject = (TomcatProject) projects[i].getNature(TomcatLauncherPlugin.NATURE_ID);
		  if (tomcatProject != null) {
			  ArrayList webappClasspathFile = new ArrayList();
			  ArrayList visitedProjects = new ArrayList(); /*IMC*/
			  			  
  			  if (projects[i].hasNature(JavaCore.NATURE_ID)) {
  				  IJavaProject javaProject = JavaCore.create(projects[i]);
  				  WebClassPathEntries entries = tomcatProject.getWebClassPathEntries();
  	
  				  IFile file = null;
  				  if (tomcatProject.getRootDirFolder() == null) {
  					  file = projects[i].getFile(new Path(WEBAPP_CLASSPATH_FILENAME));
  				  } else {
  					  file = tomcatProject.getRootDirFolder().getFile(new Path(WEBAPP_CLASSPATH_FILENAME));
				  }

  				  File cpFile = file.getLocation().makeAbsolute().toFile();
  				  if (cpFile.exists()) {
  					  cpFile.delete();
  				  }
  				  
  				  if (entries != null) {
  					  getClassPathEntries(javaProject, webappClasspathFile, entries.getList(), visitedProjects);
  					  
  					  if (tomcatProject.getMavenClasspath()) {
  						  collectMavenDependencies(javaProject, webappClasspathFile, new ArrayList());
  					  }
  					  
  					  if (!webappClasspathFile.isEmpty()) {
  						  
  						  try {
  							  if (cpFile.createNewFile()) {
  								  PrintWriter pw = new PrintWriter(new FileOutputStream(cpFile));
  								  
  								  for (int j = 0; j < webappClasspathFile.size(); j++) {
  									  //TODO
  									  pw.println(webappClasspathFile.get(j));
  								  }
  								  pw.close();
  							  }
  						  } catch (IOException e) {
  							  e.printStackTrace();
						  }
					  }
				  }
			  }
		  }
	  }

	  String[] classpath = new String[0];
	  classpath = addPreferenceJvmToClasspath(classpath);
	  classpath = addPreferenceProjectListToClasspath(classpath);
	  classpath = StringUtil.concatUniq(classpath, this.getClasspath());

	  String[] vmArgs = this.getVmArgs();
	  vmArgs = addPreferenceParameters(vmArgs);

	  String[] bootClasspath = addPreferenceJvmToBootClasspath(new String[0]);

	  StringBuffer programArguments = new StringBuffer();
	  for (String prgArg : prgArgs) {
		  programArguments.append(" " + prgArg);
	  }

	  StringBuffer jvmArguments = new StringBuffer();
	  for (String vmArg : vmArgs) {
		  jvmArguments.append(" " + vmArg);
	  }

	  if (action == RUN) {
		  VMLauncherUtility.runVM(getLabel(), getMainClass(), classpath, bootClasspath, jvmArguments.toString(), programArguments.toString(), isDebugMode(), showInDebugger, saveConfig);
	  }
	  if (action == LOG) {
		  VMLauncherUtility.log(getLabel(), getMainClass(), classpath, bootClasspath, jvmArguments.toString(), programArguments.toString(), isDebugMode(), showInDebugger);
	  }
	  if (action == ADD_LAUNCH) {
		  VMLauncherUtility.createConfig(getLabel(), getMainClass(), classpath, bootClasspath, jvmArguments.toString(), programArguments.toString(), isDebugMode(), showInDebugger, true);
	  }

  }

  private void add(ArrayList data, IPath entry) {
    IPath myEntry = entry;
    if (!myEntry.isAbsolute()) {
      myEntry = myEntry.makeAbsolute();
    }
    String tmp = myEntry.toFile().toString();
    if (!data.contains(tmp)) {
      data.add(tmp);
    }
  }

  private void add(ArrayList data, IResource con) {
    if (con == null) {
      return;
    }
    add(data, con.getLocation());
  }

  private void add(List data, IResource findMember) {
    add(new ArrayList(data), findMember);
  }

  private void add(List data, IPath entry) {
    add(new ArrayList(data), entry);
  }

  private void getClassPathEntries(IJavaProject prj, ArrayList data, List selectedPaths, ArrayList visitedProjects) {
    IClasspathEntry[] entries = null;

    IPath outputPath = null;
    try {
      outputPath = prj.getOutputLocation();
      if (selectedPaths.contains(outputPath.toFile().toString().replace('\\', '/'))) {
        add(data, prj.getProject().getWorkspace().getRoot().findMember(outputPath));
      }
      entries = prj.getRawClasspath();
    } catch (JavaModelException e) {
      TomcatLauncherPlugin.log(e);
    }

    if (entries != null) {
      getClassPathEntries(entries, prj, data, selectedPaths, visitedProjects, outputPath);
    }
  }

  private void getClassPathEntries(IClasspathEntry[] entries, IJavaProject prj, ArrayList data, List selectedPaths, ArrayList visitedProjects, IPath outputPath) {
    for (IClasspathEntry entrie : entries) {
      IClasspathEntry entry = entrie;
      IPath path = entry.getPath();
      if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
        path = entry.getOutputLocation();
        if (path == null) {
          continue;
        }
      }
      if (entry.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
        String prjName = entry.getPath().lastSegment();
        if (!visitedProjects.contains(prjName)) {
          visitedProjects.add(prjName);
          getClassPathEntries(prj.getJavaModel().getJavaProject(prjName), data, selectedPaths, visitedProjects);
        }
        continue;
      } else if (!selectedPaths.contains(path.toFile().toString().replace('\\', '/'))) {
        if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER && !entry.getPath().toString().equals("org.eclipse.jdt.launching.JRE_CONTAINER")) {

          // entires in container are only processed individually
          // if container itself is not selected

          IClasspathContainer container;
          try {
            container = JavaCore.getClasspathContainer(path, prj);
          } catch (JavaModelException e1) {
            TomcatLauncherPlugin.log(e1);
            container = null;
          }

          if (container != null) {
            getClassPathEntries(container.getClasspathEntries(), prj, data, selectedPaths, visitedProjects, outputPath);
          }
        }
        continue;
      }

      IClasspathEntry[] tmpEntry = null;
      if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
        try {
          tmpEntry = JavaCore.getClasspathContainer(path, prj).getClasspathEntries();
        } catch (JavaModelException e1) {
          TomcatLauncherPlugin.log(e1);
          continue;
        }
      } else {
        tmpEntry = new IClasspathEntry[1];
        tmpEntry[0] = JavaCore.getResolvedClasspathEntry(entry);
      }

      for (IClasspathEntry element : tmpEntry) {
        if (entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
          IResource res = prj.getProject().getWorkspace().getRoot().findMember(element.getPath());
          if (res != null) {
            add(data, res);
          } else {
            add(data, element.getPath());
          }
        } else if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
          IPath srcPath = entry.getOutputLocation();
          if (srcPath != null && !srcPath.equals(outputPath)) {
            add(data, prj.getProject().getWorkspace().getRoot().findMember(srcPath));
          }
        } else {
          TomcatLauncherPlugin.log(">>> " + element);
          if (element.getPath() != null) {
            add(data, element.getPath());
          }
        }
      }
    }
  }

  private void collectMavenDependencies(IJavaProject prj, List data, List visitedProjects) {
    IClasspathEntry[] entries = null;
    try {
      add(data, prj.getProject().getWorkspace().getRoot().findMember(prj.getOutputLocation()));
      entries = prj.getRawClasspath();
    } catch (JavaModelException e) {
      TomcatLauncherPlugin.log(e);
    }

    if (entries != null) {
      collectMavenDependencies(entries, prj, data, visitedProjects);
    }
  }

  private void collectMavenDependencies(IClasspathEntry[] entries, IJavaProject prj, List data, List visitedProjects) {
    for (int i = 0; i < entries.length; i++) {
      IClasspathEntry entry = entries[i];
      IPath path = entry.getPath();
      if ((entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) && entry.getPath().toString().endsWith("MAVEN2_CLASSPATH_CONTAINER")) {
        IClasspathEntry[] tmpEntry = null;
        try {
          tmpEntry = JavaCore.getClasspathContainer(path, prj).getClasspathEntries();
        } catch (JavaModelException e1) {
          TomcatLauncherPlugin.log(e1);
          continue;
        }
        for (int j = 0; j < tmpEntry.length; j++) {
          if (tmpEntry[j].getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
            if (tmpEntry[j].getPath().lastSegment().matches(".*servlet-api[^\\/]{0,10}\\.jar$")) {
              continue;
            }
            if (tmpEntry[j].getPath().lastSegment().matches(".*jasper[^\\/]{0,10}\\.jar$")) {
              continue;
            }
            if (tmpEntry[j].getPath().lastSegment().matches(".*annotations-api[^\\/]{0,10}.\\.jar$")) {
              continue;
            }
            if (tmpEntry[j].getPath().lastSegment().matches(".*el-api[^\\/]{0,10}\\.jar$")) {
              continue;
            }
            if (tmpEntry[j].getPath().lastSegment().matches(".*jsp-api[^\\/]{0,10}\\.jar$")) {
              continue;
            }
            IResource res = prj.getProject().getWorkspace().getRoot().findMember(tmpEntry[j].getPath());
            if (res != null) {
              add(data, res);
            } else {
              add(data, tmpEntry[j].getPath());
            }
          } else if (tmpEntry[j].getEntryKind() == IClasspathEntry.CPE_PROJECT) {
            String prjName = tmpEntry[j].getPath().lastSegment();
            IJavaProject subPrj = prj.getJavaModel().getJavaProject(prjName);

            try {
              add(data, prj.getProject().getWorkspace().getRoot().findMember(subPrj.getOutputLocation()));
            } catch (JavaModelException e1) {
              TomcatLauncherPlugin.log(e1);
              continue;
            }
            if (!visitedProjects.contains(prjName)) {
              visitedProjects.add(prjName);
              collectMavenDependencies(subPrj, data, visitedProjects);
            }
            continue;
          } else {
            TomcatLauncherPlugin.log(">>> " + tmpEntry[j]);
            if (tmpEntry[j].getPath() != null) {
              add(data, tmpEntry[j].getPath());
            }
          }
        }
      }
    }
  }

  private boolean isDebugMode() {
    return TomcatLauncherPlugin.getDefault().isDebugMode();
  }

  protected String getTomcatDir() {
    return TomcatLauncherPlugin.getDefault().getTomcatDir();
  }

  protected String getTomcatBase() {
    return TomcatLauncherPlugin.getDefault().getTomcatBase();
  }

  private String[] addPreferenceProjectListToClasspath(String[] previouscp) {
    List projectsList = TomcatLauncherPlugin.getDefault().getProjectsInCP();
    String[] result = previouscp; // default in case there are no projects to add or an error occurs
    Iterator it = projectsList.iterator();
    while (it.hasNext()) {
   	  ProjectListElement ple = null;
      try {
    	// Add project libraries to Tomcats system classpath, filter jars also contained in tomcats lib folder
        ple = (ProjectListElement) it.next();
        IJavaProject jproject = JavaCore.create(ple.getProject());
        String[] cp2 = this.addProjectToClasspath(previouscp, jproject);

        // Add Tomcat libs to Tomcats system classpath. This is required because some of the project libraries may need
        // them, but won't be able to see the common classpath anymore, where the Tomcat libs are normally placed.
        File libFolder = new File(getTomcatBase() + File.separator + "lib");
        result = addJarsOfDirectory(cp2, libFolder);
      } catch (Exception e) {
        TomcatLauncherPlugin.log("Adding project " + ple + " to runtime classpath failed: " + e);
        // nothing will be added to classpath
      }
    }
    TomcatLauncherPlugin.log("Runtime classpath after adding projects: " + Arrays.toString(result));

    return result;
  }

  private String[] addProjectToClasspath(String[] previouscp, IJavaProject project) throws CoreException {
    if ((project != null) && project.exists() && project.isOpen()) {
      String[] projectcp = JavaRuntime.computeDefaultRuntimeClassPath(project);
      String[] filteredProjectCp = removeTomcatJars(projectcp);
      return StringUtil.concatUniq(filteredProjectCp, previouscp);
    } else {
      return previouscp;
    }
  }
  
  /**
   * removes jar files that are part of tomcat from the project classpath list.
   * The project may contain an older version of servlet-api in order to be compatible with older versions of Tomcat.
   * But adding an incompatible version to the classpath of Tomcat will prevent server start.
   *
   * @param projectcp project classpath
   * @return filtered classpath
   */
  private String[] removeTomcatJars(String[] projectcp) {
	List<String> res = new LinkedList<String>();
	for (int i = 0; i < projectcp.length; i++) {
		String entry = projectcp[i];
		if (!entry.contains("servlet-api") && !entry.contains("el-api") && !entry.contains("jasper-el") && !entry.contains("jsp-api")) {
			res.add(entry);
		}
	}
	return res.toArray(new String[res.size()]);
}

  /**
   * Add all jar files of directory dir to previous array
   */
  protected String[] addJarsOfDirectory(String[] previous, File dir) {
      if((dir != null) && (dir.isDirectory())) {
          // Filter for .jar files
          FilenameFilter filter = new FilenameFilter() {
              public boolean accept(File directory, String filename) {
                  return filename.endsWith(".jar");
              }
          };

          String[] jars = null;

          File[] files = dir.listFiles(filter);
          jars = new String[files.length];
          for(int i=0; i<files.length; i++) {
              jars[i] = files[i].getAbsolutePath();
          }

          return StringUtil.concat(previous, jars);
      } else {
          return previous;
      }
  }

private String[] addPreferenceParameters(String[] previous) {
    String[] prefParams = StringUtil.cutString(TomcatLauncherPlugin.getDefault().getJvmParamaters(), TomcatPluginResources.PREF_PAGE_LIST_SEPARATOR);
    return StringUtil.concat(previous, prefParams);
  }

  private String[] addPreferenceJvmToClasspath(String[] previous) {
    String[] prefClasspath = StringUtil.cutString(TomcatLauncherPlugin.getDefault().getJvmClasspath(), TomcatPluginResources.PREF_PAGE_LIST_SEPARATOR);
    return StringUtil.concatUniq(previous, prefClasspath);
  }

  private String[] addPreferenceJvmToBootClasspath(String[] previous) {
    String[] prefBootClasspath = StringUtil.cutString(TomcatLauncherPlugin.getDefault().getJvmBootClasspath(), TomcatPluginResources.PREF_PAGE_LIST_SEPARATOR);
    return StringUtil.concatUniq(previous, prefBootClasspath);
  }

  public final String getLabel() {
    return this.label;
  }

}
