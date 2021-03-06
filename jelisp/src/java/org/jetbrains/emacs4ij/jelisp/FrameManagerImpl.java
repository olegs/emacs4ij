package org.jetbrains.emacs4ij.jelisp;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.emacs4ij.jelisp.elisp.LispSymbol;
import org.jetbrains.emacs4ij.jelisp.exception.DuplicateFrame;
import org.jetbrains.emacs4ij.jelisp.exception.NoLispFrameForIdeFrame;
import org.jetbrains.emacs4ij.jelisp.exception.NoOpenedFrameException;
import org.jetbrains.emacs4ij.jelisp.exception.UnregisteredFrameException;
import org.jetbrains.emacs4ij.jelisp.platform_dependent.LispFrame;
import org.jetbrains.emacs4ij.jelisp.subroutine.Predicate;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: kate
 * Date: 4/3/12
 * Time: 11:42 AM
 * To change this template use File | Settings | File Templates.
 */
final class FrameManagerImpl extends CyclicManager<LispFrame> implements FrameManager {
    @Override
    public void onFrameOpened(LispFrame frame) {
        define(frame);
    }

    @Override
    public void onFrameReleased (LispFrame frame) {
        remove(frame);
    }

    @Override
    public List<LispFrame> getVisibleFrames () {
        List<LispFrame> visibleFrames = new ArrayList<>();
        for (LispFrame frame: myData) {
            if (Predicate.frameVisibleP(GlobalEnvironment.INSTANCE, frame).equals(LispSymbol.ourT))
                visibleFrames.add(frame);
        }
        return visibleFrames;
    }

    @Override
    public List<LispFrame> getVisibleAndIconifiedFrames () {
        List<LispFrame> frames = new ArrayList<>();
        for (LispFrame frame: myData) {
            LispSymbol predicate = Predicate.frameVisibleP(GlobalEnvironment.INSTANCE, frame);
            if (predicate.equals(LispSymbol.ourT) || predicate.equals(new LispSymbol("icon")))
                frames.add(frame);
        }
        return frames;
    }

    @NotNull
    @Override
    public LispFrame getExistingFrame(final LispFrame newFrame) {
        for (LispFrame frame: myData) {
            if (frame.equals(newFrame))
                return frame;
        }
        throw new NoLispFrameForIdeFrame();
    }

    @Override
    protected void throwNoOpenedItem() {
        throw new NoOpenedFrameException();
    }

    @Override
    protected void throwItemIsNotInDataSet(LispFrame item) {
        throw new UnregisteredFrameException(item.toString());
    }

    @Override
    protected void throwDuplicateItem(LispFrame item) {
        throw new DuplicateFrame(item.toString());
    }
}
