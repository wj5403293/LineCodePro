package cn.lineai.service;

/**
 * Provides accessibility service state information.
 * Decouples data layer from app layer's LineCodeAccessibilityService.
 */
public interface AccessibilityStateProvider {
    boolean isAccessibilityEnabled();
}
