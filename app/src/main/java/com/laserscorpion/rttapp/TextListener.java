package com.laserscorpion.rttapp;

import java.util.EventListener;

/**
 * Created by joel on 2/4/16.
 */
public interface TextListener extends EventListener {

    void ControlMessageReceived(String message);
    void RTTextReceived(String text);

}