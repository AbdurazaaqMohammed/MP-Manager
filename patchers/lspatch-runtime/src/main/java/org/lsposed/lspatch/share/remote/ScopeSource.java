package org.lsposed.lspatch.share.remote;

import java.util.List;

/**
 * Where a module's scope comes from — the one thing {@code getScope} answers differently between the
 * two modes. Embedded mode is the fixed host package; manager mode is the set of apps patched with the
 * module ({@code ScopeDao.getAppsForModule}). Everything else about the service is mode-independent, so
 * this is injected rather than branched on.
 */
public interface ScopeSource {
    List<String> getScope(String modulePackageName);
}
