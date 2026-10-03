package com.baresudoku;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

final class BoardNodes extends AccessibilityNodeProvider {
    final BoardView view;
    int focused = -1;

    BoardNodes(BoardView view) {
        this.view = view;
    }

    public AccessibilityNodeInfo createAccessibilityNodeInfo(int id) {
        if (id == AccessibilityNodeProvider.HOST_VIEW_ID) {
            AccessibilityNodeInfo host = AccessibilityNodeInfo.obtain(view);
            view.onInitializeAccessibilityNodeInfo(host);
            int[] ids = view.axTargets();
            for (int i = 0; i < ids.length; i++) host.addChild(view, ids[i]);
            return host;
        }
        AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain(view, id);
        node.setPackageName(view.getContext().getPackageName());
        node.setClassName(view.axClass(id));
        node.setParent(view);
        node.setVisibleToUser(true);
        node.setContentDescription(view.axLabel(id));
        boolean clickable = view.axClickable(id);
        node.setClickable(clickable);
        if (clickable) node.addAction(AccessibilityNodeInfo.ACTION_CLICK);
        node.setEnabled(view.axEnabled(id));
        node.setSelected(view.axSelected(id));
        node.setFocusable(true);
        node.setAccessibilityFocused(id == focused);
        node.addAction(id == focused ? AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS : AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        Rect bounds = view.axRect(id);
        node.setBoundsInParent(bounds);
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        bounds.offset(location[0], location[1]);
        node.setBoundsInScreen(bounds);
        return node;
    }

    public boolean performAction(int id, int action, Bundle args) {
        if (id == AccessibilityNodeProvider.HOST_VIEW_ID) return view.performAccessibilityAction(action, args);
        if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
            focused = id;
            view.axEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
            if (focused == id) focused = -1;
            view.axEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_CLICK) {
            view.activate(id);
            view.axEvent(id, AccessibilityEvent.TYPE_VIEW_CLICKED);
            return true;
        }
        return false;
    }

    public AccessibilityNodeInfo findFocus(int focus) {
        if (focus != AccessibilityNodeInfo.FOCUS_ACCESSIBILITY || focused < 0) return null;
        return createAccessibilityNodeInfo(focused);
    }
}
