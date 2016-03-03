package cn.kchen.chat.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.rockerhieu.emojicon.EmojiconTextView;

import org.kymjs.kjframe.widget.RoundImageView;

import java.lang.reflect.Field;
import java.util.List;

import cn.kchen.chat.R;
import cn.kchen.chat.bean.Message;
import cn.kchen.chat.util.MyTool;


public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    private List<Message> mMessages;
    private int[] mUsernameColors;


    public MessageAdapter(Context context, List<Message> messages) {
        mMessages = messages;
        mUsernameColors = context.getResources().getIntArray(R.array.username_colors);
    }

    static final int HEAD_ICON_NUM = 150;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        int layout = -1;
        switch (viewType) {
            case Message.TYPE_MESSAGE:
                layout = R.layout.chat_item_list_left;
                break;
            case Message.TYPE_LOG:
                layout = R.layout.item_log;
                break;
            case Message.TYPE_ACTION:
                layout = R.layout.chat_item_list_left;
                break;
            case Message.TYPE_SELF_MESSAGE:
                layout = R.layout.chat_item_list_right;
                break;
            case Message.TYPE_PHOTO:
                layout = R.layout.chat_item_list_right;
                break;
            case Message.TYPE_MESSAGE_PHOTO:
                layout = R.layout.chat_item_list_left;
                break;
        }
        View v = LayoutInflater
                .from(parent.getContext())
                .inflate(layout, parent, false);
        return new ViewHolder(v, viewType);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int position) {
        Message message = mMessages.get(position);
        viewHolder.setUsername(message.getUsername());
        viewHolder.setMessage(message.getMessage());
    }

    @Override
    public int getItemCount() {
        return mMessages.size();
    }

    @Override
    public int getItemViewType(int position) {
        return mMessages.get(position).getType();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private TextView mUsernameView = null;
        private TextView mMessageView = null;
        private EmojiconTextView mEmojiMessageView = null;
        private ImageView mChatImage = null;
        private RelativeLayout mLayoutContent = null;
        private RoundImageView mHeadIcon = null;

        private int type;
        private Bitmap mBitmap;
        private Handler updateImgHandler = new Handler();
        private String mMessage;

        public ViewHolder(View itemView, int viewType) {
            super(itemView);
            type = viewType;
            mEmojiMessageView = (EmojiconTextView) itemView.findViewById(R.id.chat_item_content_text);
            mUsernameView = (TextView) itemView.findViewById(R.id.username);
            mMessageView = (TextView) itemView.findViewById(R.id.message);
            mChatImage = (ImageView) itemView.findViewById(R.id.chat_item_content_image);

            mLayoutContent = (RelativeLayout) itemView.findViewById(R.id.chat_item_layout_content);


            mHeadIcon = (RoundImageView) itemView.findViewById(R.id.chat_item_avatar);

            if (type == Message.TYPE_SELF_MESSAGE || type == Message.TYPE_MESSAGE) { // 文本消息
                mChatImage.setVisibility(View.GONE);
                mEmojiMessageView.setVisibility(View.VISIBLE);
            } else if (type == Message.TYPE_PHOTO || type == Message.TYPE_MESSAGE_PHOTO) { // 图片消息
                mEmojiMessageView.setVisibility(View.GONE);
                mChatImage.setVisibility(View.VISIBLE);

                mChatImage.setImageResource(R.drawable.default_image);
                mLayoutContent.setBackgroundResource(android.R.color.transparent);
            } else if (type == Message.TYPE_ACTION) {
                mEmojiMessageView.setText("正在输入");
                mLayoutContent.setBackgroundResource(android.R.color.transparent);
            }
        }

        public void setUsername(String username) {
            if (null == mUsernameView) return;
            mUsernameView.setText(username);
            mUsernameView.setTextColor(getUsernameColor(username));
            String name = "h" + getHeadIcon(username);
            int r_id = getResourceByReflect(name);
            mHeadIcon.setImageResource(r_id);
        }

        public int getResourceByReflect(String imageName){
            int r_id = R.drawable.default_head;
            try {
                Class drawable = R.drawable.class;
                Field field = drawable.getField(imageName);
                r_id = field.getInt(field.getName());
            } catch (Exception e) {
                Log.e("ERROR", "PICTURE NOT FOUND！");
            }
            return r_id;
        }

        public void setMessage(String message) {
            if (type == Message.TYPE_SELF_MESSAGE || type == Message.TYPE_MESSAGE) {
                mEmojiMessageView.setText(message);
            } else if (type == Message.TYPE_PHOTO) { // 图片消息
                Bitmap bm = MyTool.getimage(message);
                mChatImage.setImageBitmap(bm);
                return ;
            } else if (type == Message.TYPE_MESSAGE_PHOTO) { // 网络图片
                mMessage = message;
                new Thread(){
                    @Override
                    public void run() {
                        String url = "http://7xpf1b.com1.z0.glb.clouddn.com/" + mMessage;
                        mBitmap = MyTool.getHttpBitmap(url);
                        updateImgHandler.post(updateImg);
                    }
                }.start();
                return ;
            }
            if (null == mMessageView) return;
            mMessageView.setText(message);
        }

        private int getUsernameColor(String username) {
            int hash = 7;
            for (int i = 0, len = username.length(); i < len; i++) {
                hash = username.codePointAt(i) + (hash << 5) - hash;
            }
            int index = Math.abs(hash % mUsernameColors.length);
            return mUsernameColors[index];
        }

        private int getHeadIcon(String username) {
            int hash = 1219;
            for (int i = 0, len = username.length(); i < len; i++) {
                hash = username.codePointAt(i) + (hash << 5) - hash;
            }
            return Math.abs(hash % HEAD_ICON_NUM);
        }

        private Runnable updateImg =  new Runnable() {
            @Override
            public void run() {
                //显示
                mChatImage.setImageBitmap(mBitmap);
            }
        };


    }
}
