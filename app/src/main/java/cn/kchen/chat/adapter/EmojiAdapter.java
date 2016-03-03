package cn.kchen.chat.adapter;

import android.widget.AbsListView;
import android.widget.TextView;

import cn.kchen.chat.R;
import cn.kchen.chat.bean.Emojicon;
import org.kymjs.kjframe.widget.AdapterHolder;
import org.kymjs.kjframe.widget.KJAdapter;

import java.util.Collection;

/**
 * emoji表情界面gridview适配器
 *
 * @author kymjs (http://www.kymjs.com/) on 6/8/15.
 */
public class EmojiAdapter extends KJAdapter<Emojicon> {

    public EmojiAdapter(AbsListView view, Collection<Emojicon> mDatas) {
        super(view, mDatas, R.layout.chat_item_emoji);
    }

    @Override
    public void convert(AdapterHolder adapterHolder, Emojicon emojicon, boolean b) {
        TextView itemTvEmoji = adapterHolder.getView(R.id.itemEmoji);
        itemTvEmoji.setText(emojicon.getValue());
    }
}
