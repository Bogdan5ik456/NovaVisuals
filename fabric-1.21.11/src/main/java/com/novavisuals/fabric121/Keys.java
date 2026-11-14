package com.novavisuals.fabric121;

import org.lwjgl.glfw.GLFW;

/** Клавіатура й миша напряму через GLFW (не залежить від змін у вхідному API Minecraft). */
public final class Keys {
    public static boolean down(int key) {
        long w = GLFW.glfwGetCurrentContext();
        return w != 0L && key > 0 && GLFW.glfwGetKey(w, key) == GLFW.GLFW_PRESS;
    }

    public static boolean rightShift() { return down(GLFW.GLFW_KEY_RIGHT_SHIFT); }

    public static boolean mouse(int button) {
        long w = GLFW.glfwGetCurrentContext();
        return w != 0L && GLFW.glfwGetMouseButton(w, button) == GLFW.GLFW_PRESS;
    }

    /** Перша натиснута клавіша (для призначення біндів), або -1. Esc = -2 (скасувати). */
    public static int anyPressed() {
        long w = GLFW.glfwGetCurrentContext();
        if (w == 0L) return -1;
        if (GLFW.glfwGetKey(w, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) return -2;
        for (int k = GLFW.GLFW_KEY_SPACE; k <= GLFW.GLFW_KEY_LAST; k++) {
            if (GLFW.glfwGetKey(w, k) == GLFW.GLFW_PRESS) return k;
        }
        return -1;
    }

    public static String name(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "R-Shift";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "L-Shift";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "R-Ctrl";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "L-Ctrl";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "R-Alt";
            case GLFW.GLFW_KEY_LEFT_ALT: return "L-Alt";
            case GLFW.GLFW_KEY_SPACE: return "Space";
            case GLFW.GLFW_KEY_TAB: return "Tab";
            case GLFW.GLFW_KEY_ENTER: return "Enter";
            case GLFW.GLFW_KEY_UP: return "Up";
            case GLFW.GLFW_KEY_DOWN: return "Down";
            case GLFW.GLFW_KEY_LEFT: return "Left";
            case GLFW.GLFW_KEY_RIGHT: return "Right";
            default: break;
        }
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        String n = GLFW.glfwGetKeyName(key, 0);
        return n != null ? n.toUpperCase() : "K" + key;
    }

    private Keys() { }
}
