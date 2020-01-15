package xyz.monkeytong.hongbao.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcelable;
import android.graphics.Path;
import android.widget.Toast;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import java.util.Iterator;
import java.util.ArrayList;

import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.DisplayMetrics;

import android.service.notification.NotificationListenerService;

import xyz.monkeytong.hongbao.utils.HongbaoSignature;
import xyz.monkeytong.hongbao.utils.PowerUtil;
import xyz.monkeytong.hongbao.utils.Logger;

import java.util.List;

public class HongbaoService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "RedPackageAccessbility";
    private static final String WECHAT_DETAILS_EN = "Details";
    private static final String WECHAT_DETAILS_CH = "红包详情";
    private static final String WECHAT_BETTER_LUCK_EN = "Better luck next time!";
    private static final String WECHAT_BETTER_LUCK_CH = "手慢了";
    private static final String WECHAT_EXPIRES_CH = "已超过24小时";
    private static final String WECHAT_VIEW_SELF_CH = "查看红包";
    private static final String WECHAT_VIEW_OTHERS_CH = "领取红包";
    private static final String WECHAT_ALREADY_NONE = "已被领完";
    private static final String WECHAT_ALREADY_DONE = "已领取";

    private static final String WECHAT_LUCKMONEY_RECEIVE_ACTIVITY = ".plugin.luckymoney.ui";
    private static final String WECHAT_LUCKMONEY_DETAIL_ACTIVITY = "LuckyMoneyDetailUI";
    private static final String WECHAT_LUCKMONEY_GENERAL_ACTIVITY = "LauncherUI";
    private static final String WECHAT_LUCKMONEY_CHATTING_ACTIVITY = "ChattingUI";

    public static final String WECHAT_CHAT_CLASS_NAME = "com.tencent.mm.ui.LauncherUI";
    public static final String WECHAT_OPEN_CLASS_NAME = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI";
    public static final String NORMAL_PACKAGE_RESOURCE = "com.tencent.mm:id/bal";
    public static final String WECHAT_PACKAGE_NAME = "com.tencent.mm";
    public static final String WECHAT_PACKAGE_UNPACK_TEXT2 = "[微信红包]";
    public static final String WECHAT_PACKAGE_UNPACK_TEXT1 = "微信红包";
    public static final String WECHAT_SURE_TRANSFOR_CLASS_NAME = "com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI";
    public static final String WECHAT_UNPAKCAGE_CLASS_NAME = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNotHookReceiveUI";
    public static final String PAY_TEXT1 = "[转账]请你确认收款";
    public static final String PAY_TEXT2 = "转账给你";
    public static final String SURE_PAY_TEXT = "确认收账";
    public static final String BUTTON_CLASS_NAME = "android.widget.Button";

    public static final String DINGDING_CHAT_LIST_ID = "com.alibaba.android.rimet:id/session_content_tv";
    public static final String DINGDING_OPEN_CLASS_NAME = "com.alibaba.android.dingtalk.redpackets.activities.FestivalRedPacketsPickActivity";
    public static final String DINGDING_PACKAGE_NAME = "com.alibaba.android.rimet";
    public static final String DINGDING_PACKAGE_RESOURCE = "com.alibaba.android.rimet:id/redpackets_desc";
    public static final String DINGDING_PACKAGE_UNPACK = "拼手气红包";
    public static final String DINGDING_PACKAGE_UNPACK2 = "个人红包";
    public static final String DINGDING_PACKAGE_VIEW = "查看红包";
    public static final String DINGDING_TEXT = "[红包]";
    public static final String DINGDING_UNPACK_RESOURCE_ID = "com.alibaba.android.rimet:id/iv_pick_bottom";

    private String currentActivityName = WECHAT_LUCKMONEY_GENERAL_ACTIVITY;

    private AccessibilityNodeInfo rootNodeInfo, mReceiveNode, mUnpackNode;
    private boolean mLuckyMoneyPicked, mLuckyMoneyReceived;
    private int mUnpackCount = 0;
    private boolean mMutex = false, mListMutex = false, mChatMutex = false;
    private HongbaoSignature signature = new HongbaoSignature();

    private PowerUtil powerUtil;
    private SharedPreferences sharedPreferences;

    /**
     * AccessibilityEvent
     *
     * @param event 事件
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (sharedPreferences == null) return;
        setCurrentActivityName(event);

        int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            if (sharedPreferences.getBoolean("pref_watch_notification", false) && watchNotifications(event))
                return;
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            handleContentChanged(event);
            return;
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleStateChanged(event);
        }
    }

    private void setCurrentActivityName(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        try {
            ComponentName componentName = new ComponentName(
                    event.getPackageName().toString(),
                    event.getClassName().toString()
            );

            getPackageManager().getActivityInfo(componentName, 0);
            currentActivityName = componentName.flattenToShortString();
        } catch (PackageManager.NameNotFoundException e) {
            currentActivityName = WECHAT_LUCKMONEY_GENERAL_ACTIVITY;
        }
    }

    private boolean watchNotifications(AccessibilityEvent event) {
        // Not a notification
        if (event.getEventType() != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED)
            return false;

        // Not a hongbao
        String tip = event.getText().toString();
        Logger.d(TAG, "通知字符" + tip);
        if (!tip.contains(WECHAT_PACKAGE_UNPACK_TEXT2)) return true;

        Parcelable parcelable = event.getParcelableData();
        if (parcelable instanceof Notification) {
            Notification notification = (Notification) parcelable;
            try {
                // 清除signature,避免进入会话后误判
                signature.cleanSignature();
                notification.contentIntent.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }
        }
        return true;
    }


    private void handleStateChanged(AccessibilityEvent paramAccessibilityEvent) {
        Logger.d(TAG, "窗口状态发生变化");
        CharSequence charSequence = paramAccessibilityEvent.getClassName();
        Logger.d(TAG, charSequence.toString());

        if (charSequence.equals(WECHAT_OPEN_CLASS_NAME)) {
            Logger.d(TAG, "微信红包已经领取");
            return;
        }

        if (charSequence.equals(WECHAT_UNPAKCAGE_CLASS_NAME)) {
            Logger.d(TAG, "未领取红包");

            if (paramAccessibilityEvent.getSource() != null) {
                Logger.d(TAG, "paramAccessibilityEvent not Null");
                openWeChatPackageByText(paramAccessibilityEvent);
                return;
            } else {
                int delayFlag = sharedPreferences.getInt("pref_open_delay", 0) * 100;
                Logger.d(TAG, "PostDelayed");
                new android.os.Handler().postDelayed(
                        new Runnable() {
                            public void run() {
                                try {
                                    Logger.d(TAG, "openWeChatPackageByMetrics");
                                    openWeChatPackageByMetrics();
                                } catch (Exception e) {
                                    Logger.e(TAG, "Something Wrong");
                                    e.printStackTrace();
                                    mUnpackCount = 0;
                                }
                            }
                        },
                        delayFlag);
            }
            return;
        }

        if (charSequence.equals(WECHAT_SURE_TRANSFOR_CLASS_NAME) && sharedPreferences.getBoolean("pref_watch_trans", false)) {
            Logger.d(TAG, "确认收账");
            if (paramAccessibilityEvent.getSource() != null) {
                sureTransfor(paramAccessibilityEvent);
            } else {
                int delayFlag = sharedPreferences.getInt("pref_open_delay", 0) * 100;
                new android.os.Handler().postDelayed(
                        new Runnable() {
                            public void run() {
                                try {
                                    sureTransforNew();
                                } catch (Exception e) {
                                    Logger.e(TAG, "Something Wrong");
                                    e.printStackTrace();
                                }
                            }
                        }, delayFlag);
            }
            return;
        }

        if (charSequence.equals(DINGDING_OPEN_CLASS_NAME) && sharedPreferences.getBoolean("pref_dingding_watch", false)) {
            Logger.d(TAG, "打开钉钉红包");
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                openDingDingPackageByViewId(paramAccessibilityEvent);
                return;
            }
        }
    }

    private void sureTransforNew() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float dpi = metrics.densityDpi;
        if (android.os.Build.VERSION.SDK_INT > 23) {
            Path path = new Path();
            final int x = metrics.widthPixels / 2;
            // TODO 目测比例大法 -。- 效果还行
            final int y = metrics.heightPixels * 7 / 13;
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            path.moveTo(x, y);
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 200, 50));

            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    Logger.d(TAG, "onCompleted");
                    mMutex = true;
                    super.onCompleted(gestureDescription);
                    SystemClock.sleep(400L);
                    checkMutex();
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    Logger.d(TAG, "onCancelled");
                    // mMutex = false;
                    super.onCancelled(gestureDescription);
                }
            }, null);
        }
    }

    private void checkMutex() {
        if (mMutex) {
            Logger.d(TAG, "CheckMutex");
            SystemClock.sleep(300L);
            mMutex = false;
            goBack(this);
        }
    }

    private void openDingDingPackageByViewId(AccessibilityEvent paramAccessibilityEvent) {
        AccessibilityNodeInfo accessibilityNodeInfo = paramAccessibilityEvent.getSource();
        try {
            accessibilityNodeInfo = (AccessibilityNodeInfo) accessibilityNodeInfo.findAccessibilityNodeInfosByViewId(DINGDING_UNPACK_RESOURCE_ID).get(0);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(" ClassName: ");
            stringBuilder.append(paramAccessibilityEvent.getClassName());
            Logger.d(TAG, stringBuilder.toString());
            AccessibilityNodeInfo accessibilityNodeInfo1 = accessibilityNodeInfo.getParent();
            if (accessibilityNodeInfo1 != null && accessibilityNodeInfo1.getChildCount() == 10) {
                Logger.d(TAG, "点击钉钉红包");
                Rect rect = new Rect();
                accessibilityNodeInfo.getBoundsInScreen(rect);
                int i = rect.centerX();
                int j = rect.centerY();
                StringBuilder stringBuilder1 = new StringBuilder();
                stringBuilder1.append("X:");
                stringBuilder1.append(i);
                stringBuilder1.append(" Y:");
                stringBuilder1.append(j);
                Logger.d(TAG, stringBuilder1.toString());
                Path path = new Path();
                path.moveTo(i, j);
                int delayFlag = sharedPreferences.getInt("pref_open_delay", 0) * 100;
                SystemClock.sleep(delayFlag);
                Logger.d(TAG, "手势构建完成");
                boolean bool = dispatchGesture((new GestureDescription.Builder()).addStroke(new GestureDescription.StrokeDescription(path, 200L, 200L)).build(), new AccessibilityService.GestureResultCallback() {
                    public void onCompleted(GestureDescription param1GestureDescription) {
                        Logger.d(TAG, "手势点击完成");
                        super.onCompleted(param1GestureDescription);
                    }
                }, null);
                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append("是否点击: ");
                stringBuilder2.append(bool);
                Logger.d(TAG, stringBuilder2.toString());
                SystemClock.sleep(300L);
                goBack(this);
            }
            return;
        } catch (IndexOutOfBoundsException ex) {
            Logger.e("RedPackageAccessbility", "钉钉红包获取节点索引异常");
            return;
        }
    }

    public void sureTransfor(AccessibilityEvent paramAccessibilityEvent) {
        AccessibilityNodeInfo accessibilityNodeInfo = paramAccessibilityEvent.getSource();
        Logger.d(TAG, "进入确认收账");
        if (accessibilityNodeInfo != null) {
            Iterator iterator = accessibilityNodeInfo.findAccessibilityNodeInfosByText(SURE_PAY_TEXT).iterator();
            while (iterator.hasNext()) {
                ((AccessibilityNodeInfo) iterator.next()).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                goBack(this);
            }
        }
    }

    public void openWeChatPackageByText(AccessibilityEvent accessibilityEvent) {
        AccessibilityNodeInfo accessibilityNodeInfo = accessibilityEvent.getSource();
        if (accessibilityNodeInfo != null && accessibilityNodeInfo.getChild(2) != null && (accessibilityNodeInfo.getChildCount() == 4 || accessibilityNodeInfo.getChildCount() == 5) && accessibilityNodeInfo.getChild(2).getClassName().toString().equals(BUTTON_CLASS_NAME)) {
            accessibilityNodeInfo.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Logger.d(TAG, "打开微信红包按钮");
            SystemClock.sleep(300L);
            goBack(this);
            return;
        }
    }

    public void openWeChatPackageByMetrics() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float dpi = metrics.densityDpi;
        Logger.d(TAG, "mUnpackCount Numbers: " + mUnpackCount);
        if (android.os.Build.VERSION.SDK_INT > 23 && mUnpackCount > 0) {
            Path path = new Path();
            final int x = metrics.widthPixels / 2;
            final int y = metrics.heightPixels * 13 / 20;
            path.moveTo(x, y);
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            Logger.d(TAG, "Get Path: " + path.toString());
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 200, 50));

            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    Logger.d(TAG, "onCompleted");
                    mMutex = true;

                    super.onCompleted(gestureDescription);
                    SystemClock.sleep(200);
                    checkMutex();
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    Logger.d(TAG, "onCancelled");
                    // mMutex = false;
                    super.onCancelled(gestureDescription);
                }
            }, null);

            mUnpackCount = mUnpackCount - 1;
            Logger.d(TAG, "mUnpackCount Numbers:" + mUnpackCount);

        }
    }

    private void goBack(AccessibilityService accessibilityService) {
        if (accessibilityService == null)
            return;
        SystemClock.sleep(400L);
        accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
    }

    private void handleContentChanged(AccessibilityEvent paramAccessibilityEvent) {
        AccessibilityNodeInfo accessibilityNodeInfo = paramAccessibilityEvent.getSource();
        // Logger.d(TAG, accessibilityNodeInfo.getClassName().toString());
        if (accessibilityNodeInfo != null && accessibilityNodeInfo.getPackageName().equals(WECHAT_PACKAGE_NAME)) {
            if (sharedPreferences.getBoolean("pref_watch_list", false) && WeChatList(accessibilityNodeInfo)) {
                Logger.d(TAG, "处理 content 变化 -- 微信好友列表");
                return;
            }

            if (sharedPreferences.getBoolean("pref_watch_chat", false)) {
                Logger.d(TAG, "处理 content 变化 -- 微信聊天");
                if (WeChatPackage(accessibilityNodeInfo))
                    return;
            }

            if (sharedPreferences.getBoolean("pref_watch_trans", false) && isTransfor(accessibilityNodeInfo)) {
                Logger.d(TAG, "处理 content 变化 -- 自动收账");
                openTransfor(accessibilityNodeInfo);
                return;
            }
            return;
        }

        if (accessibilityNodeInfo != null && accessibilityNodeInfo.getPackageName().equals(DINGDING_PACKAGE_NAME)) {
            if (sharedPreferences.getBoolean("pref_watch_dingding_list", false) && DingDingList(accessibilityNodeInfo)) {
                // 钉钉好友列表
                Logger.d(TAG, "处理 content 变化 -- 钉钉好友列表");
                return;
            }

            if (sharedPreferences.getBoolean("pref_dingding_watch", false)) {
                // 聊天界面
                Logger.d(TAG, "处理 content 变化 -- 钉钉");
                DingDingPackageByViewId(accessibilityNodeInfo);
                return;
            }
        }
    }

    private Boolean DingDingList(AccessibilityNodeInfo AccessibilityNodeInfo) {
        for (AccessibilityNodeInfo accessibilityNodeInfo : AccessibilityNodeInfo.findAccessibilityNodeInfosByViewId(DINGDING_CHAT_LIST_ID)) {
            if ((accessibilityNodeInfo.getText().toString().contains(DINGDING_TEXT)) && accessibilityNodeInfo.getParent().isClickable()) {
                Logger.d(TAG, "发现钉钉聊天列表红包");
                accessibilityNodeInfo.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }
        }
        return false;
    }

    private void DingDingPackageByViewId(AccessibilityNodeInfo paramAccessibilityNodeInfo) {

        Iterator iterator = paramAccessibilityNodeInfo.findAccessibilityNodeInfosByViewId(DINGDING_PACKAGE_RESOURCE).iterator();
        while (iterator.hasNext()) {
            AccessibilityNodeInfo accessibilityNodeInfo = ((AccessibilityNodeInfo) iterator.next()).getParent();
            // 查看红包
            // accessibilityNodeInfo.getChild(1).getText().toString()
            if (accessibilityNodeInfo != null && accessibilityNodeInfo.getChildCount() == 3 && (accessibilityNodeInfo.getChild(2).getText().toString().equals(DINGDING_PACKAGE_UNPACK) || accessibilityNodeInfo.getChild(2).getText().toString().equals(DINGDING_PACKAGE_UNPACK2))) {
                String excludeWords = sharedPreferences.getString("pref_watch_exclude_words", "");
                String[] excludeWordsArray = excludeWords.split(" +");
                String hongbaoContent = accessibilityNodeInfo.getChild(0).getText().toString();
                for (String word : excludeWordsArray) {
                    if (word.length() > 0 && hongbaoContent.contains(word))
                        return;
                    Logger.d(TAG, "发现钉钉红包");
                    accessibilityNodeInfo.getChild(0).getParent().performAction(16);
                    SystemClock.sleep(200L);
                }
            }
        }
    }

    public boolean WeChatList(AccessibilityNodeInfo AccessibilityNodeInfo) {
        Logger.d(TAG, "进入微信观察聊天列表");
        for (AccessibilityNodeInfo accessibilityNodeInfo : AccessibilityNodeInfo.findAccessibilityNodeInfosByViewId(NORMAL_PACKAGE_RESOURCE)) {
            if ((accessibilityNodeInfo.getText().toString().contains(PAY_TEXT1) || accessibilityNodeInfo.getText().toString().contains(WECHAT_PACKAGE_UNPACK_TEXT2)) && accessibilityNodeInfo.getParent().isClickable()) {
                Logger.d(TAG, "发现微信聊天列表红包");
                accessibilityNodeInfo.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }
        }
        return false;
    }

    private boolean WeChatPackage(AccessibilityNodeInfo AccessibilityNodeInfo) {
//        mUnpackCount = 0;
        List<android.view.accessibility.AccessibilityNodeInfo> UnPackage = AccessibilityNodeInfo.findAccessibilityNodeInfosByText(WECHAT_PACKAGE_UNPACK_TEXT1);
        for (int i = 0; i < UnPackage.size(); i++) {
            AccessibilityNodeInfo accessibilityNodeInfo = ((AccessibilityNodeInfo) UnPackage.get(i)).getParent();
            if (accessibilityNodeInfo != null && (accessibilityNodeInfo.getChildCount() == 3 || accessibilityNodeInfo.getChildCount() == 2 || accessibilityNodeInfo.getChildCount() == 4)) {
                String excludeWords = sharedPreferences.getString("pref_watch_exclude_words", "");
                if ((accessibilityNodeInfo.getChild(1).getText().toString().contains(WECHAT_ALREADY_DONE) || accessibilityNodeInfo.getChild(1).getText().toString().contains(WECHAT_ALREADY_NONE))) {
                    if (i == UnPackage.size() - 1) {
                        return false;
                    } else {
                        continue;
                    }
                } else {
                    mUnpackCount += 1;
                    Logger.d(TAG, "mUnpackCount Numbers: " + mUnpackCount);
                    String[] excludeWordsArray = excludeWords.split(" +");
                    String hongbaoContent = accessibilityNodeInfo.getChild(0).getText().toString();
                    for (String word : excludeWordsArray) {
                        if (word.length() > 0 && hongbaoContent.contains(word)) return false;
                    }
                    accessibilityNodeInfo.getChild(0).getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Logger.d(TAG, "处理 content 变化 -- 微信红包");
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isTransfor(AccessibilityNodeInfo paramAccessibilityNodeInfo) {
        ArrayList arrayList = new ArrayList();
        Logger.d(TAG, "进入istransfor");
        arrayList.addAll(paramAccessibilityNodeInfo.findAccessibilityNodeInfosByText(PAY_TEXT2));
        Logger.d(TAG, arrayList.toString());
        return (arrayList.size() > 0);
    }

    public void openTransfor(AccessibilityNodeInfo paramAccessibilityNodeInfo) {
        Logger.d(TAG, "进入微信转账");
        Iterator iterator = paramAccessibilityNodeInfo.findAccessibilityNodeInfosByText(PAY_TEXT2).iterator();
        while (iterator.hasNext()) {
            AccessibilityNodeInfo accessibilityNodeInfo = ((AccessibilityNodeInfo) iterator.next()).getParent();
            if (accessibilityNodeInfo.getChildCount() == 3) {
                accessibilityNodeInfo.getChild(0).getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                // Clock ? PostDelay ?
                SystemClock.sleep(200L);
            }
        }
    }

    /*
    private void watchChat(AccessibilityEvent event) {
        this.rootNodeInfo = getRootInActiveWindow();

        if (rootNodeInfo == null) return;

        mReceiveNode = null;
        mUnpackNode = null;

        checkNodeInfo(event.getEventType());

        Logger.d(TAG, "watchChat mLuckyMoneyReceived:" + mLuckyMoneyReceived + " mLuckyMoneyPicked:" + mLuckyMoneyPicked + " mReceiveNode:" + mReceiveNode);
        if (mLuckyMoneyReceived && (mReceiveNode != null)) {
            mMutex = true;

            mReceiveNode.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
            mLuckyMoneyReceived = false;
            mLuckyMoneyPicked = true;
        }
        // 如果戳开但还未领取
        Logger.d(TAG, "戳开红包！" + " mUnpackCount: " + mUnpackCount + " mUnpackNode: " + mUnpackNode);
        if (mUnpackCount >= 1 && (mUnpackNode != null)) {
            int delayFlag = sharedPreferences.getInt("pref_open_delay", 0) * 1000;
            new android.os.Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        try {
                            openPacket();
                        } catch (Exception e) {
                            mMutex = false;
                            mLuckyMoneyPicked = false;
                            mUnpackCount = 0;
                        }
                    }
                },
                delayFlag);
        }
    }
    private boolean watchList(AccessibilityEvent event) {
        if (mListMutex) return false;
        mListMutex = true;
        AccessibilityNodeInfo eventSource = event.getSource();
        // Not a message
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || eventSource == null)
            return false;

        List<AccessibilityNodeInfo> nodes = eventSource.findAccessibilityNodeInfosByText(WECHAT_PACKAGE_UNPACK_TEXT2);
        // 增加条件判断currentActivityName.contains(WECHAT_LUCKMONEY_GENERAL_ACTIVITY)
        // 避免当订阅号中出现标题为“[微信红包]拜年红包”（其实并非红包）的信息时误判
        if (!nodes.isEmpty() && currentActivityName.contains(WECHAT_LUCKMONEY_GENERAL_ACTIVITY)) {
            AccessibilityNodeInfo nodeToClick = nodes.get(0);
            if (nodeToClick == null) return false;
            CharSequence contentDescription = nodeToClick.getContentDescription();
            if (contentDescription != null && !signature.getContentDescription().equals(contentDescription)) {
                nodeToClick.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                signature.setContentDescription(contentDescription.toString());
                return true;
            }
        }
        return false;
    }

    private void checkNodeInfo(int eventType) {
        if (this.rootNodeInfo == null) return;

        if (signature.commentString != null) {
            sendComment();
            signature.commentString = null;
        }

        // 聊天会话窗口，遍历节点匹配“领取红包”和"查看红包"
        AccessibilityNodeInfo node1 = (sharedPreferences.getBoolean("pref_watch_self", false)) ?
                this.getTheLastNode(WECHAT_VIEW_OTHERS_CH, WECHAT_VIEW_SELF_CH) : this.getTheLastNode(WECHAT_VIEW_OTHERS_CH);
        if (node1 != null &&
                (currentActivityName.contains(WECHAT_LUCKMONEY_CHATTING_ACTIVITY)
                        || currentActivityName.contains(WECHAT_LUCKMONEY_GENERAL_ACTIVITY))) {
            String excludeWords = sharedPreferences.getString("pref_watch_exclude_words", "");
            if (this.signature.generateSignature(node1, excludeWords)) {
                mLuckyMoneyReceived = true;
                mReceiveNode = node1;
                Logger.d("sig", this.signature.toString());
            }
            return;
        }

        // 戳开红包，红包还没抢完，遍历节点匹配“拆红包”
        AccessibilityNodeInfo node2 = findOpenButton(this.rootNodeInfo);
        Logger.d(TAG, "checkNodeInfo  node2 " + node2);
        if (node2 != null && "android.widget.Button".equals(node2.getClassName()) && currentActivityName.contains(WECHAT_LUCKMONEY_RECEIVE_ACTIVITY)
                && (mUnpackNode == null || mUnpackNode != null && !mUnpackNode.equals(node2))) {
            mUnpackNode = node2;
            mUnpackCount += 1;
            return;
        }

        // 戳开红包，红包已被抢完，遍历节点匹配“红包详情”和“手慢了”
        boolean hasNodes = this.hasOneOfThoseNodes(
                WECHAT_BETTER_LUCK_CH, WECHAT_DETAILS_CH,
                WECHAT_BETTER_LUCK_EN, WECHAT_DETAILS_EN, WECHAT_EXPIRES_CH);
        Logger.d(TAG, "checkNodeInfo  hasNodes:" + hasNodes + " mMutex:" + mMutex);
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && hasNodes
                && (currentActivityName.contains(WECHAT_LUCKMONEY_DETAIL_ACTIVITY)
                || currentActivityName.contains(WECHAT_LUCKMONEY_RECEIVE_ACTIVITY))) {
            mMutex = false;
            mLuckyMoneyPicked = false;
            mUnpackCount = 0;
            performGlobalAction(GLOBAL_ACTION_BACK);
            signature.commentString = generateCommentString();
        }
    }

    private void sendComment() {
        try {
            AccessibilityNodeInfo outNode =
                    getRootInActiveWindow().getChild(0).getChild(0);
            AccessibilityNodeInfo nodeToInput = outNode.getChild(outNode.getChildCount() - 1).getChild(0).getChild(1);

            if ("android.widget.EditText".equals(nodeToInput.getClassName())) {
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo
                        .ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, signature.commentString);
                nodeToInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            }
        } catch (Exception e) {
            // Not supported
        }
    }


    private boolean hasOneOfThoseNodes(String... texts) {
        List<AccessibilityNodeInfo> nodes;
        for (String text : texts) {
            if (text == null) continue;

            nodes = this.rootNodeInfo.findAccessibilityNodeInfosByText(text);

            if (nodes != null && !nodes.isEmpty()) return true;
        }
        return false;
    }

    private AccessibilityNodeInfo getTheLastNode(String... texts) {
        int bottom = 0;
        AccessibilityNodeInfo lastNode = null, tempNode;
        List<AccessibilityNodeInfo> nodes;

        for (String text : texts) {
            if (text == null) continue;

            nodes = this.rootNodeInfo.findAccessibilityNodeInfosByText(text);

            if (nodes != null && !nodes.isEmpty()) {
                tempNode = nodes.get(nodes.size() - 1);
                if (tempNode == null) return null;
                Rect bounds = new Rect();
                tempNode.getBoundsInScreen(bounds);
                if (bounds.bottom > bottom) {
                    bottom = bounds.bottom;
                    lastNode = tempNode;
                    signature.others = text.equals(WECHAT_VIEW_OTHERS_CH);
                }
            }
        }
        return lastNode;
    }
    */

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Toast.makeText(getApplicationContext(), "抢红包服务开启", Toast.LENGTH_SHORT).show();
        this.watchFlagsFromPreference();
    }

    @Override
    public void onCreate() {
        Logger.d(TAG, "抢红包服务创建");
        super.onCreate();
    }

    @Override
    public void onInterrupt() {
        Logger.d(TAG, "抢红包服务阻断");
        Toast.makeText(getApplicationContext(), "抢红包服务阻断", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRebind(Intent paramIntent) {
        Logger.d(TAG, "抢红包服务重绑定");
        super.onRebind(paramIntent);
    }

    private void watchFlagsFromPreference() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        // this.powerUtil = new PowerUtil(this);
        // Boolean watchOnLockFlag = sharedPreferences.getBoolean("pref_watch_on_lock", false);
        // this.powerUtil.handleWakeLock(watchOnLockFlag);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("pref_watch_on_lock")) {
            Boolean changedValue = sharedPreferences.getBoolean(key, false);
            // this.powerUtil.handleWakeLock(changedValue);
        }
    }

    @Override
    public void onDestroy() {
        Toast.makeText(getApplicationContext(), "抢红包服务已经销毁", Toast.LENGTH_LONG).show();
        Logger.d(TAG, "抢红包服务已经销毁");
        // this.powerUtil.handleWakeLock(false);
        super.onDestroy();
    }

    private String generateCommentString() {
        if (!signature.others) return null;

        Boolean needComment = sharedPreferences.getBoolean("pref_comment_switch", false);
        if (!needComment) return null;

        String[] wordsArray = sharedPreferences.getString("pref_comment_words", "").split(" +");
        if (wordsArray.length == 0) return null;

        Boolean atSender = sharedPreferences.getBoolean("pref_comment_at", false);
        if (atSender) {
            return "@" + signature.sender + " " + wordsArray[(int) (Math.random() * wordsArray.length)];
        } else {
            return wordsArray[(int) (Math.random() * wordsArray.length)];
        }
    }
}