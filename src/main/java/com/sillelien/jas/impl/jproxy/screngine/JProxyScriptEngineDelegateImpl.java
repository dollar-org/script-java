package com.sillelien.jas.impl.jproxy.screngine;

import com.sillelien.jas.RelProxyException;
import com.sillelien.jas.impl.jproxy.JProxyConfigImpl;
import com.sillelien.jas.impl.jproxy.core.JProxyImpl;
import com.sillelien.jas.impl.jproxy.core.clsmgr.JProxyEngine;
import com.sillelien.jas.impl.jproxy.core.clsmgr.cldesc.ClassDescriptorSourceScript;
import com.sillelien.jas.impl.jproxy.core.clsmgr.comp.JProxyCompilationException;
import com.sillelien.jas.impl.jproxy.core.clsmgr.srcunit.SourceScriptRoot;
import com.sillelien.jas.impl.jproxy.core.clsmgr.srcunit.SourceScriptRootInMemory;
import com.sillelien.jas.impl.jproxy.shell.JProxyShellClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import java.io.File;

/**
 * @author jmarranz
 */
public class JProxyScriptEngineDelegateImpl extends JProxyImpl {
    protected JProxyScriptEngineImpl parent;
    @Nullable
    protected ClassDescriptorSourceScript classDescSourceScript;
    protected long codeBufferModTimestamp = 0;
    protected long lastCodeCompiledTimestamp = 0;

    public JProxyScriptEngineDelegateImpl(JProxyScriptEngineImpl parent) {
        this.parent = parent;
    }

    @Nullable
    @Override
    public ClassDescriptorSourceScript init(@NotNull JProxyConfigImpl config) {
        SourceScriptRoot sourceFileScript = SourceScriptRootInMemory.createSourceScriptInMemory("");

        JProxyShellClassLoader classLoader = null;
        String classFolder = config.getClassFolder();
        if (classFolder != null) {
            ClassLoader defaultClassLoader = getDefaultClassLoader();
            classLoader = new JProxyShellClassLoader(defaultClassLoader, new File(classFolder));
        }

        this.classDescSourceScript = init(config, sourceFileScript, classLoader);
        return classDescSourceScript;
    }

    @NotNull
    @Override
    public Class getMainParamClass() {
        return ScriptContext.class;
    }

    @NotNull
    private SourceScriptRootInMemory getSourceScriptInMemory() {
        ClassDescriptorSourceScript classDescSourceScript = this.classDescSourceScript;
        assert classDescSourceScript != null;
        return (SourceScriptRootInMemory) classDescSourceScript.getSourceScript();
    }

    @Nullable
    public Object execute(@NotNull String code, @NotNull ScriptContext context) throws ScriptException {
        Class scriptClass;
        JProxyEngine jproxyEngine = getJProxyEngine();
        assert jproxyEngine != null;
        Object monitor = jproxyEngine.getMonitor();
        synchronized (monitor) {
            ClassDescriptorSourceScript classDescSourceScript = this.classDescSourceScript;
            if (!code.equals(getSourceScriptInMemory().getScriptCode())) {
                this.codeBufferModTimestamp = System.currentTimeMillis();

                getSourceScriptInMemory().setScriptCode(code, codeBufferModTimestamp);
                // Recuerda que cada vez que se obtiene el timestamp se llama a System.currentTimeMillis(), es imposible que el usuario haga algo en menos de 1ms

                ClassDescriptorSourceScript classDescSourceScript2 = null;
                try {
                    classDescSourceScript2 = jproxyEngine.detectChangesInSourcesAndReload();
                } catch (JProxyCompilationException ex) {
                    throw new ScriptException(ex);
                }

                if (classDescSourceScript2 != classDescSourceScript)
                    throw new RelProxyException("Internal Error");

                this.lastCodeCompiledTimestamp = System.currentTimeMillis();
                if (lastCodeCompiledTimestamp == codeBufferModTimestamp) // Demasiado rápido compilando
                {
                    // Aseguramos que el siguiente código se ejecuta si o si con un codeBufferModTimestamp mayor que el timestamp de la compilación
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ex) {
                        throw new RelProxyException(ex);
                    }
                }
            }

            assert classDescSourceScript != null;
            scriptClass = classDescSourceScript.getLastLoadedClass();
            assert scriptClass != null;
        }

        try {
            JProxyScriptEngineImpl parent = this.parent;
            assert parent != null;
            return ClassDescriptorSourceScript.callMainMethod(scriptClass, parent, context);
        } catch (Throwable ex) {
            Exception ex2 = (ex instanceof Exception) ? (Exception) ex : new RelProxyException(ex);
            throw new ScriptException(ex2);
        }
    }
}
