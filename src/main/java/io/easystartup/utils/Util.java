package io.easystartup.utils;

/*
 * @author indianBond
 */
public class Util {

    public static void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
//                e.printStackTrace();
        }
    }
}
