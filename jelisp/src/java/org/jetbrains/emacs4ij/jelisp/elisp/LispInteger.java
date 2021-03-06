package org.jetbrains.emacs4ij.jelisp.elisp;

import org.jetbrains.emacs4ij.jelisp.Environment;
import org.jetbrains.emacs4ij.jelisp.exception.WrongTypeArgumentException;
import org.jetbrains.emacs4ij.jelisp.platform_dependent.LispBuffer;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina.Polishchuk
 * Date: 7/12/11
 * Time: 4:44 PM
 * To change this template use File | Settings | File Templates.
 *
 * elisp integer number = 13, 1355, -7979, etc
 */
public final class LispInteger extends LispNumber<Integer> implements MarkerOrInteger {
    public static final int MAX_CHAR   = 0x3FFFFF;

    public LispInteger(int data) {
        myData = data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LispInteger that = (LispInteger) o;
        return myData.equals(that.myData);
    }

    @Override
    public int hashCode() {
        return myData;
    }
    
    public String toCharacterString () {        
        if (!isCharacter())
            throw new WrongTypeArgumentException("characterp", this);
        if (myData < 32) {
            return "^" + (char)Character.toUpperCase(myData + 64);
        }
        if (myData > 127 && myData < 160) {
            return '\\' + Integer.toOctalString(myData);
        }
        return Character.toString((char)(int)myData);
    }

    @Override
    public LispBuffer getBuffer(Environment environment) {
        return environment.getBufferCurrentForEditing();
    }

    @Override
    public Integer getPosition() {
        return myData;
    }
    
    public boolean isCharacter () {
        return myData <= MAX_CHAR && myData > -1;
    }
}
