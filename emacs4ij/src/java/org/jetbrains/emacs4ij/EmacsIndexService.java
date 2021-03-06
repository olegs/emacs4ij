package org.jetbrains.emacs4ij;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.emacs4ij.jelisp.DefinitionIndex;

/**
 * Created with IntelliJ IDEA.
 * User: kate
 * Date: 7/1/12
 * Time: 2:05 PM
 *
 * This service provides DefinitionIndex persistence.
 */

@State(
        name="EmacsIndex",
        storages = @Storage(id="other", file = "$APP_CONFIG$/other.xml"),
        reloadable = true,
        roamingType = RoamingType.DISABLED
)

public class EmacsIndexService implements PersistentStateComponent<EmacsIndexService> {
    protected DefinitionIndex myEmacsIndex = new DefinitionIndex();

    public DefinitionIndex getEmacsIndex() {
        return myEmacsIndex;
    }

    public void setEmacsIndex(DefinitionIndex index) {
        myEmacsIndex = index;
    }

    @Override
    public EmacsIndexService getState() {
        return myEmacsIndex.isEmpty() ? null : this;
    }

    @Override
    public void loadState(EmacsIndexService state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
