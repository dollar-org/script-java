package com.innowhere.relproxy.impl.jproxy.clsmgr.comp;

import com.innowhere.relproxy.RelProxyException;
import com.innowhere.relproxy.impl.jproxy.clsmgr.ClassDescriptor;
import com.innowhere.relproxy.impl.jproxy.clsmgr.ClassDescriptorInner;
import com.innowhere.relproxy.impl.jproxy.clsmgr.ClassDescriptorSourceFile;
import com.innowhere.relproxy.impl.jproxy.clsmgr.ClassDescriptorSourceFileJava;
import com.innowhere.relproxy.impl.jproxy.clsmgr.ClassDescriptorSourceFileRegistry;
import com.innowhere.relproxy.impl.jproxy.clsmgr.ClassDescriptorSourceFileScript;
import com.innowhere.relproxy.impl.jproxy.clsmgr.JProxyClassLoader;
import com.innowhere.relproxy.impl.jproxy.clsmgr.JProxyEngine;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 *
 * @author jmarranz
 */
public class JProxyCompilerInMemory
{
    protected JProxyEngine engine;
    protected JavaCompiler compiler;
    protected Iterable<String> compilationOptions; // puede ser null
    protected DiagnosticCollector<JavaFileObject> diagnostics; // puede ser null
    protected boolean outDefaultDiagnostics = false;
            
    public JProxyCompilerInMemory(JProxyEngine engine,Iterable<String> compilationOptions,DiagnosticCollector<JavaFileObject> diagnostics)
    {
        this.engine = engine;
        this.compilationOptions = compilationOptions;
        this.diagnostics = diagnostics;
        this.compiler = ToolProvider.getSystemJavaCompiler();
        
        if (diagnostics == null)
        {
            this.diagnostics = new DiagnosticCollector<JavaFileObject>();
            this.outDefaultDiagnostics = true;
        }        
    }
    
    public JProxyCompilerContext createJProxyCompilerContext() 
    {
        StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(diagnostics, null, null);   
        return new JProxyCompilerContext(standardFileManager);
    }
    
    public void compileSourceFile(ClassDescriptorSourceFile sourceFileDesc,JProxyCompilerContext context,JProxyClassLoader customClassLoader,ClassDescriptorSourceFileRegistry sourceRegistry)
    {
        //File sourceFile = sourceFileDesc.getSourceFile();
        LinkedList<JavaFileObjectOutputClass> outClassList = compile(sourceFileDesc,context,customClassLoader,sourceRegistry);
        
        if (outClassList == null) 
            throw new RelProxyException("Cannot reload class: " + sourceFileDesc.getClassName());
        
        String className = sourceFileDesc.getClassName();        
        
        // Puede haber más de un resultado cuando hay inner classes y/o clase privada en el mismo archivo o bien simplemente clases dependientes
        for(JavaFileObjectOutputClass outClass : outClassList)
        {
            String currClassName = outClass.binaryName();
            byte[] classBytes = outClass.getBytes();            
            if (className.equals(currClassName))            
            {
                sourceFileDesc.setClassBytes(classBytes); 
            }
            else
            {
                ClassDescriptorInner innerClass = sourceFileDesc.getInnerClassDescriptor(currClassName,true);
                if (innerClass != null)
                {            
                    innerClass.setClassBytes(classBytes);                       
                }
                else
                {
                    // Lo mismo es un archivo dependiente e incluso una inner class pero de otra clase que está siendo usada en el archivo compilado
                    ClassDescriptor dependentClass = sourceRegistry.getClassDescriptor(currClassName);
                    if (dependentClass != null)
                    {
                        dependentClass.setClassBytes(classBytes); 
                    }
                    else
                    {
                        // Seguramente es debido a que el archivo java tiene una clase privada autónoma declarada en el mismo archivo .java, no permitimos estas clases porque sólo podemos
                        // detectarlas cuando cambiamos el código fuente, pero no si el código fuente no se ha tocado, por ejemplo no tenemos
                        // forma de conseguir que se recarguen de forma determinista y si posteriormente se cargara via ClassLoader al usarse no podemos reconocer que es una clase
                        // "hot reloadable" (quizás a través del package respecto a las demás clases hot pero no es muy determinista pues nada impide la mezcla de hot y no hot en el mismo package)
                        // Es una limitación mínima.
                        throw new RelProxyException("Unexpected class when compiling: " + currClassName + " maybe it is an autonomous private class declared in the same java file of the principal class, this kind of classes are not supported in hot reload");
                    }
                }
            }
        }
    }        
    
    private LinkedList<JavaFileObjectOutputClass> compile(ClassDescriptorSourceFile sourceFileDesc,JProxyCompilerContext context,ClassLoader classLoader,ClassDescriptorSourceFileRegistry sourceRegistry)
    {
        // http://stackoverflow.com/questions/12173294/compiling-fully-in-memory-with-javax-tools-javacompiler
        // http://www.accordess.com/wpblog/an-overview-of-java-compilation-api-jsr-199/
        // http://grepcode.com/file/repository.grepcode.com/java/root/jdk/openjdk/6-b14/com/sun/tools/javac/util/JavacFileManager.java?av=h#JavacFileManager
        // http://grepcode.com/file/repository.grepcode.com/java/root/jdk/openjdk/7-b147/javax/tools/StandardLocation.java
        // http://books.brainysoftware.com/java6_sample/chapter2.pdf
        // http://atamur.blogspot.com.es/2009/10/using-built-in-javacompiler-with-custom.html
        // http://www.javablogging.com/dynamic-in-memory-compilation/ Si no queremos generar archivos
        // http://atamur.blogspot.com.es/2009/10/using-built-in-javacompiler-with-custom.html
        // http://stackoverflow.com/questions/264828/controlling-the-classpath-in-a-servlet?rq=1
        // http://stackoverflow.com/questions/1563909/how-to-set-classpath-when-i-use-javax-tools-javacompiler-compile-the-source
        // http://stackoverflow.com/questions/10767048/javacompiler-with-custom-classloader-and-filemanager


        StandardJavaFileManager standardFileManager = context.getStandardFileManager(); // recuerda que el StandardJavaFileManager puede reutilizarse entre varias compilaciones consecutivas mientras se cierre al final
     
        Iterable<? extends JavaFileObject> compilationUnits;

        if (sourceFileDesc instanceof ClassDescriptorSourceFileJava)
        {
            List<File> sourceFileList = new ArrayList<File>();
            sourceFileList.add(sourceFileDesc.getSourceFile());            
            compilationUnits = standardFileManager.getJavaFileObjectsFromFiles(sourceFileList);
        }
        else if (sourceFileDesc instanceof ClassDescriptorSourceFileScript)
        {
            ClassDescriptorSourceFileScript sourceFileDescScript = (ClassDescriptorSourceFileScript)sourceFileDesc;
            LinkedList<JavaFileObject> compilationUnitsList = new LinkedList<JavaFileObject>();            
            String code = sourceFileDescScript.getSourceCode();
            compilationUnitsList.add(new JavaFileObjectInputSourceInMemory(sourceFileDescScript.getClassName(),code,sourceFileDescScript.getEncoding(),sourceFileDescScript.getTimestamp()));            
            compilationUnits = compilationUnitsList;                
        }
        else
        {
            throw new RelProxyException("Internal error");
        }

        JavaFileManagerInMemory fileManagerInMemory = new JavaFileManagerInMemory(standardFileManager,classLoader,sourceRegistry);

        boolean success = compile(compilationUnits,fileManagerInMemory);
        if (!success) return null;

        LinkedList<JavaFileObjectOutputClass> classObj = fileManagerInMemory.getJavaFileObjectOutputClassList();
        return classObj;

    }

    private boolean compile(Iterable<? extends JavaFileObject> compilationUnits,JavaFileManager fileManager)
    {
        /*
        String systemClassPath = System.getProperty("java.class.path");
        */

        LinkedList<String> finalCompilationOptions = new LinkedList<String>();
        if (compilationOptions != null)        
            for(String option : compilationOptions) finalCompilationOptions.add(option);
        finalCompilationOptions.add("-classpath");
        finalCompilationOptions.add(engine.getFolderSources().getAbsolutePath());        
        
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, finalCompilationOptions,null, compilationUnits);
        boolean success = task.call();

        if (outDefaultDiagnostics)
        {
            List<Diagnostic<? extends JavaFileObject>> diagList = diagnostics.getDiagnostics();
            if (!diagList.isEmpty())
            {
                System.err.println("Problems compiling: " + compilationUnits);
                int i = 1;
                for (Diagnostic diagnostic : diagList)
                {
                   System.err.println(" Diagnostic " + i);
                   System.err.println("  code: " + diagnostic.getCode());
                   System.err.println("  kind: " + diagnostic.getKind());
                   System.err.println("  position: " + diagnostic.getPosition());
                   System.err.println("  start position: " + diagnostic.getStartPosition());
                   System.err.println("  end position: " + diagnostic.getEndPosition());
                   System.err.println("  source: " + diagnostic.getSource());
                   System.err.println("  message: " + diagnostic.getMessage(null));
                   i++;
                }
            }
        }

        return success;
    }


}