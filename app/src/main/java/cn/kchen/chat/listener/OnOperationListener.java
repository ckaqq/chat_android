package cn.kchen.chat.listener;

import cn.kchen.chat.bean.Emojicon;

/**
 * 表情栏顶部按钮的监听器
 *
 * @author kymjs (http://www.kymjs.com/)
 */
public interface OnOperationListener {

    void send(String content);

    void selectedEmoji(Emojicon content);
    
    void selectedBackSpace(Emojicon back);

    void selectedFunction(int index);

    void roll();

    void typing();
}
