// IAccessibilityService.aidl
package service.accessibility.bridge;

import service.accessibility.bridge.ViewNode;
import service.accessibility.bridge.Finger;

interface IAccessibilityService {
    ViewNode getCurrentScreen();
    boolean performClick(int x, int y, long duration);
    boolean performTextType(String text);
    boolean performSwipe(in Finger finger);
    boolean performMultiTouch(in Finger[] fingers);
    boolean performSystemAction(int action);
}