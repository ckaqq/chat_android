package cn.kchen.chat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.qiniu.android.http.ResponseInfo;
import com.qiniu.android.storage.UpCompletionHandler;
import com.qiniu.android.storage.UploadManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.kymjs.kjframe.utils.FileUtils;
import org.kymjs.kjframe.utils.StringUtils;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import cn.kchen.chat.adapter.MessageAdapter;
import cn.kchen.chat.bean.Emojicon;
import cn.kchen.chat.bean.Message;
import cn.kchen.chat.widget.DisplayRules;
import cn.kchen.chat.listener.OnOperationListener;
import cn.kchen.chat.util.HttpUtil;
import cn.kchen.chat.util.MyTool;
import cn.kchen.chat.widget.KJChatKeyboard;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MainActivity extends AppCompatActivity {

    private Socket mSocket;

    private RecyclerView mMessagesView;
    private List<Message> mMessages = new ArrayList<>();
    private RecyclerView.Adapter mAdapter;
    private Handler welcomeHandler = new Handler();
    private int userNum;

    private boolean mTyping;
    private Handler mTypingHandler = new Handler();
    private boolean mHasLogin = false;
    private KJChatKeyboard box;
    private String mFileName;

    private UploadManager uploadManager = new UploadManager();

    public static final int REQUEST_CODE_GETIMAGE_BYSDCARD = 0x1;
    public static final int REQUEST_CODE_CAMERA_BYSDCARD = 0x2;
    private static final int TYPING_TIMER_LENGTH = 600;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 聊天内容
        mAdapter = new MessageAdapter(this, mMessages);
        mMessagesView = (RecyclerView)findViewById(R.id.messages);
        mMessagesView.setLayoutManager(new LinearLayoutManager(this));
        mMessagesView.setAdapter(mAdapter);
        mMessagesView.setOnTouchListener(getOnTouchListener());

        // 底部栏
        box = (KJChatKeyboard) findViewById(R.id.chat_msg_input_box);
        initMessageInputToolBox();

        // 连接 Socket.io
        try {
            mSocket = IO.socket("http://" + Config.IP);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        mSocket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
        mSocket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
        mSocket.on("login", onLogin);
        mSocket.on("new message", onNewMessage);
        mSocket.on("new photo", onNewPhoto);
        mSocket.on("user joined", onUserJoined);
        mSocket.on("user left", onUserLeft);
        mSocket.on("typing", onTyping);
        mSocket.on("stop typing", onStopTyping);
        mSocket.connect();

        // 登录
        mSocket.emit("add user", Config.NAME);

        showProgress(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mSocket.disconnect();
        mSocket.off(Socket.EVENT_CONNECT_ERROR, onConnectError);
        mSocket.off(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
        mSocket.off("new message", onNewMessage);
        mSocket.off("new photo", onNewPhoto);
        mSocket.off("user joined", onUserJoined);
        mSocket.off("user left", onUserLeft);
        mSocket.off("typing", onTyping);
        mSocket.off("stop typing", onStopTyping);
        mSocket.off("login", onLogin);
    }

    private void toast (String text){
        Toast.makeText(this, text.trim(),Toast.LENGTH_SHORT).show();
    }

    private long exitTime = 0;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && box.isShow()) {
            box.hideLayout();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BACK &&
                event.getAction() == KeyEvent.ACTION_DOWN) {
            if ((System.currentTimeMillis()-exitTime) > 2000) {
                toast("再按一次退出聊天室");
                exitTime = System.currentTimeMillis();
            } else {
                finish();
                System.exit(0);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void initMessageInputToolBox() {
        box.setOnOperationListener(new OnOperationListener() {
            @Override
            public void send(String content) {
                //Log.i("Send", content);
                if (TextUtils.isEmpty(content))
                    return;
                attemptSend(content);
            }

            @Override
            public void selectedEmoji(Emojicon emoji) {
                box.getEditTextBox().append(emoji.getValue());
            }

            @Override
            public void selectedBackSpace(Emojicon back) {
                DisplayRules.backspace(box.getEditTextBox());
            }

            @Override
            public void selectedFunction(int index) {
                switch (index) {
                    case 0:
                        goToAlbum();
                        break;
                    case 1:
                        goToCamera();
                        break;
                }
            }

            @Override
            public void roll() {
                scrollToBottom();
            }

            @Override
            public void typing() {
                if (!mTyping) {
                    mTyping = true;
                    mSocket.emit("typing");
                }
                mTypingHandler.removeCallbacks(onTypingTimeout);
                mTypingHandler.postDelayed(onTypingTimeout, TYPING_TIMER_LENGTH);
            }

        });

        List<String> faceCagegory = new ArrayList<>();
//        File faceList = FileUtils.getSaveFolder("chat");
        File faceList = new File("");
        if (faceList.isDirectory()) {
            File[] faceFolderArray = faceList.listFiles();
            for (File folder : faceFolderArray) {
                if (!folder.isHidden()) {
                    faceCagegory.add(folder.getAbsolutePath());
                }
            }
        }

        box.setFaceData(faceCagegory);
    }

    /**
     * 跳转到选择相册界面
     */
    private void goToAlbum() {
        Intent intent;
        if (Build.VERSION.SDK_INT < 19) {
            intent = new Intent();
            intent.setAction(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "选择图片"),
                    REQUEST_CODE_GETIMAGE_BYSDCARD);
        } else {
            intent = new Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "选择图片"),
                    REQUEST_CODE_GETIMAGE_BYSDCARD);
        }
    }

    private void goToCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, REQUEST_CODE_CAMERA_BYSDCARD);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i("onActivityResult", requestCode + "");
        if (resultCode != Activity.RESULT_OK) {
            return;
        }
        if (requestCode == REQUEST_CODE_GETIMAGE_BYSDCARD) {
            Uri dataUri = data.getData();
            String filePath;
            if (dataUri != null) {
                try {
                    File file = FileUtils.uri2File(this, dataUri);
                    filePath =  file.getAbsolutePath();
                } catch(Exception e) {
                    filePath = dataUri.toString().replace("file://", "");
                }
                sendImage(filePath);
            }
        } else if (requestCode == REQUEST_CODE_CAMERA_BYSDCARD) {
            Bitmap bitmap = (Bitmap) data.getExtras().get("data");
            String name = StringUtils.getDataTime("yyyyMMdd-HHmmss") + ".jpeg";
            boolean result = MyTool.writeToSDCard(Config.PATH, name, bitmap);
            if (result) {
                sendImage(Config.PATH + name);
            } else {
                toast("保存图片失败，请检查sd卡是否正常");
            }
        }
    }


    /**
     * 若软键盘或表情键盘弹起，点击上端空白处应该隐藏输入法键盘
     *
     * @return 会隐藏输入法键盘的触摸事件监听器
     */
    private View.OnTouchListener getOnTouchListener() {
        return new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                box.hideLayout();
                box.hideKeyboard(MainActivity.this);
                return false;
            }
        };
    }

    private void addLog(String message) {
        mMessages.add(new Message.Builder(Message.TYPE_LOG)
                .message(message).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    // 滚动到底部
    private void scrollToBottom() {
        mMessagesView.scrollToPosition(mAdapter.getItemCount() - 1);
    }

    private void addParticipantsLog(int numUsers) {
        addLog(getResources().getQuantityString(R.plurals.message_participants, numUsers, numUsers));
    }

    private void addMessage(String username, String message, boolean self) {
        int type;
        if (self) {
            type = Message.TYPE_SELF_MESSAGE;
        } else {
            type = Message.TYPE_MESSAGE;
        }
        String time =  StringUtils.getDataTime("yyyy-MM-dd HH:mm:ss");
        mMessages.add(new Message.Builder(Message.TYPE_LOG)
                .message(time).build());
        mMessages.add(new Message.Builder(type).username(username).message(message).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void addPhoto(String username, String message) {
        String time =  StringUtils.getDataTime("yyyy-MM-dd HH:mm:ss");
        mMessages.add(new Message.Builder(Message.TYPE_LOG).message(time).build());
        mMessages.add(new Message.Builder(Message.TYPE_MESSAGE_PHOTO).username(username).message(message).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void addTyping(String username) {
        mMessages.add(new Message.Builder(Message.TYPE_ACTION).username(username).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void removeTyping(String username) {
        for (int i = mMessages.size() - 1; i >= 0; i--) {
            Message message = mMessages.get(i);
            if (message.getType() == Message.TYPE_ACTION && message.getUsername().equals(username)) {
                mMessages.remove(i);
                mAdapter.notifyItemRemoved(i);
            }
        }
    }

    private void attemptSend(String message) {
        if (null == Config.NAME) return;
        if (!mSocket.connected()) return;

        addMessage(Config.NAME, message, true);

        // perform the sending message attempt.
        mSocket.emit("new message", message);
    }

    private Emitter.Listener onLogin = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            JSONObject data = (JSONObject) args[0];
            // TODO 尚未解决
            if (mHasLogin) {
                mSocket.emit("disconnect");
            }
            int numUsers;
            try {
                numUsers = data.getInt("numUsers");
            } catch (JSONException e) {
                return;
            }
            userNum = numUsers;
            welcomeHandler.post(welcome);
            mHasLogin = true;
        }
    };

    private Emitter.Listener onConnectError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.e("MainActivity", "已掉线");
                    toast(getString(R.string.error_connect));
                    Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                    startActivity(intent);
                    finish();
                }
            });
        }
    };

    private Emitter.Listener onNewMessage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    String message;
                    try {
                        username = data.getString("username");
                        message = data.getString("message");
                    } catch (JSONException e) {
                        return;
                    }

                    removeTyping(username);
                    addMessage(username, message, false);
                }
            });
        }
    };

    private Emitter.Listener onNewPhoto = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    String message;
                    try {
                        username = data.getString("username");
                        message = data.getString("message");
                    } catch (JSONException e) {
                        return;
                    }

                    Log.i("New Photo", message);
                    removeTyping(username);
                    addPhoto(username, message);
                }
            });
        }
    };

    private Emitter.Listener onUserJoined = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    int numUsers;
                    try {
                        username = data.getString("username");
                        numUsers = data.getInt("numUsers");
                    } catch (JSONException e) {
                        return;
                    }

                    addLog(getResources().getString(R.string.message_user_joined, username));
                    addParticipantsLog(numUsers);
                }
            });
        }
    };

    private Emitter.Listener onUserLeft = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    int numUsers;
                    try {
                        username = data.getString("username");
                        numUsers = data.getInt("numUsers");
                    } catch (JSONException e) {
                        return;
                    }

                    addLog(getString(R.string.message_user_left, username));
                    addParticipantsLog(numUsers);
                    removeTyping(username);
                }
            });
        }
    };

    private Emitter.Listener onTyping = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    try {
                        username = data.getString("username");
                    } catch (JSONException e) {
                        return;
                    }
                    addTyping(username);
                }
            });
        }
    };

    private Emitter.Listener onStopTyping = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    try {
                        username = data.getString("username");
                    } catch (JSONException e) {
                        return;
                    }
                    removeTyping(username);
                }
            });
        }
    };

    private Runnable onTypingTimeout = new Runnable() {
        @Override
        public void run() {
            if (!mTyping) return;

            mTyping = false;
            mSocket.emit("stop typing");
        }
    };

    private Runnable welcome =  new Runnable() {
        @Override
        public void run() {
            addLog(getString(R.string.message_user_joined, Config.NAME));
            addParticipantsLog(userNum);
            showProgress(false);
        }
    };

    // 发送图片
    private void sendImage(String filePath)
    {
        mFileName = filePath;
        String time =  StringUtils.getDataTime("yyyy-MM-dd HH:mm:ss");
        mMessages.add(new Message.Builder(Message.TYPE_LOG).message(time).build());
        mMessages.add(new Message.Builder(Message.TYPE_PHOTO).username(Config.NAME).message(filePath).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
        new Thread(){
            @Override
            public void run() {
                Bitmap bm = MyTool.getimage(mFileName);
                mFileName = getFileName(mFileName);
                upload(bm);
            }
        }.start();
    }

    private String getFileName(String path) {
        String result = StringUtils.getDataTime("yyyyMMddHHmmss-");
        result += path.substring(path.lastIndexOf("/") + 1);
        return result;
    }

    private void upload(Bitmap bm)
    {
        String token;
        try {
            token = HttpUtil.getRequest(Config.TOKEN_URL);
        } catch (Exception e) {
            return;
        }
        uploadManager.put(MyTool.Bitmap2Bytes(bm), mFileName, token,
                new UpCompletionHandler() {
                    @Override
                    public void complete(String key, ResponseInfo info, JSONObject res) {
                        //  res 包含hash、key等信息，具体字段取决于上传策略的设置。
                        Log.i("qiniu", key + ",\r\n " + info + ",\r\n " + res);
                        mSocket.emit("new photo", mFileName);
                    }
                }, null);
    }

    View mProgressView;
    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        mProgressView = findViewById(R.id.login_progress);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
}
