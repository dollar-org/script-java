/*
 *    Copyright (c) 2014-2017 Neil Ellis
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.sillelien.jas.impl.jproxy.core.clsmgr.comp;

import com.sillelien.jas.RelProxyException;
import com.sillelien.jas.impl.FileExt;
import com.sillelien.jas.impl.jproxy.core.clsmgr.FolderSourceList;
import com.sillelien.jas.impl.jproxy.core.clsmgr.cldesc.ClassDescriptor;
import com.sillelien.jas.impl.jproxy.core.clsmgr.comp.jfo.JavaFileObjectInputClassInFile;
import com.sillelien.jas.impl.jproxy.core.clsmgr.comp.jfo.JavaFileObjectInputClassInFileSystem;
import com.sillelien.jas.impl.jproxy.core.clsmgr.comp.jfo.JavaFileObjectInputClassInJar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * http://atamur.blogspot.com.es/2009/10/using-built-in-javacompiler-with-custom.html
 *
 * @author jmarranz
 */
public class JavaFileObjectInputClassFinderByClassLoader {
    @NotNull
    private static final String CLASS_FILE_EXTENSION = ".class";

    @NotNull
    private final ClassLoader classLoader;
    @Nullable
    private final FolderSourceList requiredExtraJarPaths;

    public JavaFileObjectInputClassFinderByClassLoader(@NotNull ClassLoader classLoader, @Nullable FolderSourceList requiredExtraJarPaths) {
        this.classLoader = classLoader;
        this.requiredExtraJarPaths = requiredExtraJarPaths;
    }

    @NotNull
    public List<JavaFileObjectInputClassInFileSystem> find(@NotNull String packageName) throws IOException {
        // http://www.dzone.com/snippets/get-all-classes-within-package
        // http://sourceforge.net/p/scannotation/code/HEAD/tree/scannotation/src/main/java/org/scannotation/ClasspathUrlFinder.java#l124

        String packagePath = packageName.replaceAll("\\.", "/");

        List<JavaFileObjectInputClassInFileSystem> result = new ArrayList<>();

        Enumeration<URL> urlEnumeration = classLoader.getResources(packagePath);
        if (urlEnumeration.hasMoreElements()) {
            while (urlEnumeration.hasMoreElements()) { // one URL for each jar on the classpath that has the given package
                URL packageFolderURL = urlEnumeration.nextElement();
                listUnder(packageName, packageFolderURL, result);
            }
        } else {
            // Enumeration vacía, chungo, esto nos ha ocurrido con el jar lib/ext/portlet.jar del Tomcat 6.2 del bundle liferay-portal-6.2-ce-ga3
            // daba un error de de javax.portlet.PortletRquest not found.
            // En teoría debería responder a la búsqueda classLoader.getResources("javax/portlet") devolviendo el jar, pero devuelve un Enumeration vacío
            // quizás es porque el jar no tiene un "Name: javax/portlet" en el META-INF/MANIFEST.MF (otros jar no tienen Name y funcionan no tengo claro el criterio, 
            // el problema debe estar en una opción del MANIFEST.MF que confunde al compilador), añadiendo el "Name: javax/portlet" funciona
            // pero en plan majo permitimos al usuario que nos indique los paths de los jars "conflictivos" obtenemos los .class hijos directos por fuerza bruta del package
            // requerido en el parámetro de este método find() y le evitamos modificar un jar de infraestructura que queda muy feo e inmantenible respecto a una solución
            // basada en configuración del usuario.

            if (requiredExtraJarPaths != null) {
                FileExt[] jarFileList = requiredExtraJarPaths.getArray();
                if (jarFileList != null) {
                    for (FileExt jarFile : jarFileList) {
                        listUnderJarCustom(packagePath, jarFile, result);
                    }
                }
            }
        }

        return result;
    }


    private void listUnder(@NotNull String packageName, @NotNull URL packageFolderURL, @NotNull Collection<JavaFileObjectInputClassInFileSystem> result) {
        String pkgPath = packageFolderURL.toExternalForm(); //packageFolderURL.getFile(); El problema de getFile es que tambiÃ©n estÃ¡ URL-encoded (un espacio es %20) lo cual no es compatible con File

        if (pkgPath.startsWith("file:")) {
            listUnderDir(packageName, pkgPath, result);
        } else { // browse a jar file
            listUnderJar(packageFolderURL, result);
        } // maybe there can be something else for more involved class loaders
    }

    private void listUnderDir(@NotNull String packageName, @NotNull String pkgPath, @NotNull Collection<JavaFileObjectInputClassInFileSystem> result) {
        pkgPath = pkgPath.substring("file:".length());

        try {
            pkgPath = URLDecoder.decode(pkgPath, "UTF-8");
        } // Detecté el problema con Vaadin en un path con "Documents%20and%20Settings" con %20 obviamente no es un path correcto, deben ser espacios
        catch (UnsupportedEncodingException ex) {
            throw new RelProxyException(ex);
        }

        File directory = new File(pkgPath);
        if (!directory.isDirectory())
            throw new RelProxyException("Internal Error:" + pkgPath);

        // browse local .class files - useful for local execution

        File[] childFiles = directory.listFiles();
        assert childFiles != null;
        for (File childFile : childFiles) {
            if (!childFile.isFile()) continue;

            // We only want the .class files.
            String name = childFile.getName();
            if (name.endsWith(CLASS_FILE_EXTENSION)) {
                String binaryName = ClassDescriptor.getClassNameFromPackageAndClassFileName(packageName, name);
                result.add(new JavaFileObjectInputClassInFile(childFile, binaryName, childFile.toURI()));
            }
        }
    }

    private void listUnderJar(@NotNull URL packageFolderURL, @NotNull Collection<JavaFileObjectInputClassInFileSystem> result) {
        try {
            String jarUri = packageFolderURL.toExternalForm().split("!")[0];

            JarURLConnection jarConn = (JarURLConnection) packageFolderURL.openConnection();
            String rootEntryName = jarConn.getEntryName();
            assert rootEntryName != null;
            int rootEnd = rootEntryName.length() + 1;

            JarFile jarFile = jarConn.getJarFile();
            assert jarFile != null;
            Enumeration<JarEntry> entryEnum = jarFile.entries();
            while (entryEnum.hasMoreElements()) {
                JarEntry jarEntry = entryEnum.nextElement();
                String name = jarEntry.getName();
                // Empieza por packagePath y no hay más folders siguientes, terminando en un .class (una clase concreta)                                
                assert name != null;
                if (name.startsWith(rootEntryName) && (name.indexOf('/', rootEnd) == -1) && name.endsWith(CLASS_FILE_EXTENSION)) {
                    URI uri = URI.create(jarUri + "!/" + name);
                    String binaryName = ClassDescriptor.getClassNameFromRelativeClassFilePath(name);
                    result.add(new JavaFileObjectInputClassInJar(binaryName, uri, jarEntry.getTime()));
                }
            }
        } catch (Exception e) {
            throw new RelProxyException("Wasn't able to open " + packageFolderURL + " as a jar file", e);
        }
    }


    private void listUnderJarCustom(@NotNull String packagePath, @NotNull FileExt jarFile, @NotNull Collection<JavaFileObjectInputClassInFileSystem> result) {
        String normalizedPath = jarFile.getCanonicalPath();
        if (normalizedPath.contains("\\")) // Windows
        {
            // No estoy seguro de que sea necesario normalizar pero por si acaso
            normalizedPath = normalizedPath.replace("\\", "/"); // "C:/folder"
            normalizedPath = "/" + normalizedPath; //  "/C:/folder"
        }

        String urlPath = "file:" + normalizedPath;

        URL packageFolderURL;
        try {
            packageFolderURL = new URL(urlPath);
        } catch (MalformedURLException ex) {
            throw new RelProxyException(ex);
        }

        String jarUri = "jar:" + packageFolderURL.toExternalForm();

        int posEnd = packagePath.length() + 1;

        ZipInputStream zip = null;

        try {
            InputStream in = packageFolderURL.openStream();
            assert in != null;
            zip = new ZipInputStream(in);
            ZipEntry zipEntry = zip.getNextEntry();
            while (zipEntry != null) {
                String name = zipEntry.getName();
                // Empieza por packagePath y no hay más folders siguientes, terminando en un .class (una clase concreta)
                assert name != null;
                if (name.startsWith(packagePath) && (name.indexOf('/', posEnd) == -1) && name.endsWith(CLASS_FILE_EXTENSION)) {
                    URI uri = URI.create(jarUri + "!/" + name);
                    String binaryName = ClassDescriptor.getClassNameFromRelativeClassFilePath(name);
                    result.add(new JavaFileObjectInputClassInJar(binaryName, uri, zipEntry.getTime()));
                }

                zipEntry = zip.getNextEntry();
            }
        } catch (IOException ex) {
            throw new RelProxyException(ex);
        } finally {
            if (zip != null) try {
                zip.close();
            } catch (IOException ex) {
                throw new RelProxyException(ex);
            }
        }
    }


}
