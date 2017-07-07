
package com.sillelien.dollar.relproxy.gproxy;

import com.sillelien.dollar.relproxy.RelProxyOnReloadListener;
import org.jetbrains.annotations.NotNull;

/**
 * Interface implemented by the configuration object needed to initialize <code>GProxy</code>.
 *
 * @author Jose Maria Arranz Santamaria
 * @see GProxy#init(GProxyConfig)
 */
public interface GProxyConfig {
    /**
     * Sets whether automatic detection of source code changes is enabled.
     * <p>
     * <p>If set to false other configuration parameters are ignored, there is no automatic source code change detection/reload and original objects are returned
     * instead of proxies, performance penalty is zero. Setting to false is recommended in production whether source code change detection/reload is not required.</p>
     *
     * @param enabled whether automatic source code change detection and reload is enabled. By default is true.
     * @return this object for flow API use.
     */
    @NotNull
    public GProxyConfig setEnabled(boolean enabled);

    /**
     * Sets the class reload listener.
     *
     * @param relListener the class reload listener. By default is null.
     * @return this object for flow API use.
     */
    @NotNull
    public GProxyConfig setRelProxyOnReloadListener(RelProxyOnReloadListener relListener);

    /**
     * Sets the object implementing the <code>GroovyScriptEngine</code> wrapper used to reload Groovy classes.
     * <p>
     * <p>This parameter is required otherwise there is no bridge between RelProxy and Groovy because there is no explicit Groovy dependency in RelProxy.
     *
     * @param engine the <code>GroovyScriptEngine</code> wrapper.
     * @return this object for flow API use.
     */
    @NotNull
    public GProxyConfig setGProxyGroovyScriptEngine(GProxyGroovyScriptEngine engine);
}