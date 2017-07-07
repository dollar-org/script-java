package com.sillelien.dollar.relproxy.jproxy;

import com.sillelien.dollar.relproxy.RelProxyOnReloadListener;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static com.sillelien.dollar.relproxy.jproxy.util.JProxyTestUtil.RESOURCES_FOLDER;
import static com.sillelien.dollar.relproxy.jproxy.util.JProxyTestUtil.getProjectFolder;
import static org.junit.Assert.*;

/**
 *
 * @author jmarranz
 */
public class JProxyJavaScriptEngineNoManagerTest
{
   
    public JProxyJavaScriptEngineNoManagerTest()
    {
    }
    
    @BeforeClass
    public static void setUpClass()
    {
    }
    
    @AfterClass
    public static void tearDownClass()
    {
    }
    
    @Before
    public void setUp()
    {
       
    }
    
    @After
    public void tearDown()
    {
       
    }

     
     @Test
     public void test_java_script_engine() 
     {
         File projectFolder = getProjectFolder();
         
         File inputFolderFile = new File(projectFolder,RESOURCES_FOLDER);
         // File classFolderFile = new File(projectFolder,"tmp/java_shell_test_classes");
        String inputPath = inputFolderFile.getAbsolutePath();
        String classFolder = null; // Optional
        Iterable<String> compilationOptions = Arrays.asList(new String[]{"-source","1.6","-target","1.6"});
        long scanPeriod = 300;  
        
        RelProxyOnReloadListener proxyListener = new RelProxyOnReloadListener() {
            @Override
            public void onReload(Object objOld, Object objNew, Object proxy, Method method, Object[] args) {
                System.out.println("Reloaded " + objNew + " Calling method: " + method);
            }
        };

        JProxyCompilerListener compilerListener = new JProxyCompilerListener(){
            @Override
            public void beforeCompile(File file)
            {
                System.out.println("Before compile: " + file);
            }

            @Override
            public void afterCompile(File file)
            {
                System.out.println("After compile: " + file);
            } 
        };           
        
        JProxyDiagnosticsListener diagnosticsListener = new JProxyDiagnosticsListener()
        {
            @Override
            public void onDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics)
            {
                List<Diagnostic<? extends JavaFileObject>> diagList = diagnostics.getDiagnostics();
                int i = 1;
                for (Diagnostic diagnostic : diagList)
                {
                   System.err.println("Diagnostic " + i);
                   System.err.println("  code: " + diagnostic.getCode());
                   System.err.println("  kind: " + diagnostic.getKind());
                   System.err.println("  line number: " + diagnostic.getLineNumber());
                   System.err.println("  column number: " + diagnostic.getColumnNumber());
                   System.err.println("  start position: " + diagnostic.getStartPosition());
                   System.err.println("  position: " + diagnostic.getPosition());
                   System.err.println("  end position: " + diagnostic.getEndPosition());
                   System.err.println("  source: " + diagnostic.getSource());
                   System.err.println("  message: " + diagnostic.getMessage(null));
                   i++;
                }
            }
        };

        JProxyConfig jpConfig = JProxy.createJProxyConfig();
        jpConfig.setEnabled(true)
                .setRelProxyOnReloadListener(proxyListener)
                .setInputPath(inputPath)
                .setJProxyInputSourceFileExcludedListener(null)
                .setJProxyCompilerListener(compilerListener)
                .setScanPeriod(scanPeriod)
                .setClassFolder(classFolder)
                .setCompilationOptions(compilationOptions)
                .setJProxyDiagnosticsListener(diagnosticsListener);

        JProxyScriptEngineFactory factory = JProxyScriptEngineFactory.create();

        ScriptEngine engine = factory.getScriptEngine();

        ((JProxyScriptEngine)engine).init(jpConfig);
        
        assertNotNull(engine);

        try
        {

            // El javax.script.ScriptContext.GLOBAL_SCOPE no está disponible porque no hemos usado el ScriptEngineManager para obtener el JProxyScriptEngine
            // pero si podemos crear un javax.script.ScriptContext.ENGINE_SCOPE:
            
            Bindings bindings = engine.createBindings();
            bindings.put("msg","HELLO ENGINE SCOPE WORLD!");


            StringBuilder code = new StringBuilder();        
            code.append( " javax.script.Bindings bindings = context.getBindings(javax.script.ScriptContext.ENGINE_SCOPE); \n");
            code.append( " String msg = (String)bindings.get(\"msg\"); \n");
            code.append( " System.out.println(msg); \n");                          
            code.append( " example.javashellex.JProxyShellExample.exec(engine); \n");
            code.append( " return \"SUCCESS\";");

            String result = (String)engine.eval( code.toString() , bindings);
            assertEquals("SUCCESS",result);

            bindings = engine.createBindings();
            bindings.put("msg","HELLO ENGINE SCOPE WORLD 2!");

            code = new StringBuilder();
            code.append( "public class _jproxyMainClass_ { \n");                 
            code.append( "  public static Object main(javax.script.ScriptEngine engine,javax.script.ScriptContext context) {  \n");           
            code.append( "   javax.script.Bindings bindings = context.getBindings(javax.script.ScriptContext.ENGINE_SCOPE); \n");
            code.append( "   String msg = (String)bindings.get(\"msg\"); \n");
            code.append( "   System.out.println(msg); \n");               
            code.append( "   example.javashellex.JProxyShellExample.exec(engine); \n");
            code.append( "   return \"SUCCESS 2\";");            
            code.append( "  }");   
            code.append( "}");             

            result = (String)engine.eval( code.toString() , bindings);
            assertEquals("SUCCESS 2",result);
        }
        catch(ScriptException ex)
        {
            ex.printStackTrace();
            assertTrue(false);
        }
        finally
        {
            boolean res = ((JProxyScriptEngine)engine).stop(); // Necessary if scanPeriod > 0 was defined                     
            assertTrue(res);
        }
     }
}