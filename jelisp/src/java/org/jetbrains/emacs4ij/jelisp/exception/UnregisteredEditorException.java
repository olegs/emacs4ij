package org.jetbrains.emacs4ij.jelisp.exception;

import org.jetbrains.emacs4ij.jelisp.JelispBundle;

/**
 * Created with IntelliJ IDEA.
 * User: kate
 * Date: 4/2/12
 * Time: 12:43 PM
 * To change this template use File | Settings | File Templates.
 */
public class UnregisteredEditorException extends LispException {
    public UnregisteredEditorException () {
        super(JelispBundle.message("unregistered.editor"));
    }
}