package cn.kchen.chat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import cn.kchen.chat.util.MyTool;

public class LoginActivity extends AppCompatActivity {

    private AutoCompleteTextView mIPView;
    private EditText mNickView;

    private SharedPreferences mSp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mSp = getSharedPreferences("Login", MODE_PRIVATE);

        mIPView = (AutoCompleteTextView) findViewById(R.id.ip);

        mNickView = (EditText) findViewById(R.id.password);
        mNickView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.login || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        populateAutoComplete();

        Button mEmailSignInButton = (Button) findViewById(R.id.email_sign_in_button);
        mEmailSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });
    }


    private void toast (String text){
        Toast.makeText(this, text.trim(), Toast.LENGTH_SHORT).show();
    }

    private long exitTime = 0;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK &&
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

    // 自动填充
    private void populateAutoComplete() {
        String ip = mSp.getString("ip", "");
        mIPView.setText(ip);
        String nick = mSp.getString("nick", "");
        if (!TextUtils.isEmpty(nick)) {
            mNickView.setText(nick);
        }
    }

    private void attemptLogin() {
        // 重置错误信息
        mIPView.setError(null);
        mNickView.setError(null);

        // 获取 IP 和 昵称
        String ip = mIPView.getText().toString();
        String nick = mNickView.getText().toString();

        // 检查 IP 是否合法
        if (isIPValid(ip)) {
            Config.IP = ip;
        }
        if (TextUtils.isEmpty(nick)) {
            nick = MyTool.getRandName();
            Log.i("LoginActivity", nick);
        }
        Config.NAME = nick;

        saveIPAndNick(ip, nick);

        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        Log.i("LoginActivity", "登录成功");
        finish();
    }

    // 保存 ip 和 昵称 至 SharedPreferences
    private void saveIPAndNick(String ip, String nick) {
        Editor editor = mSp.edit();
        editor.putString("ip", ip);
        editor.putString("nick", nick);
        editor.apply();
    }

    // 检查ip是否合法
    private boolean isIPValid(String ip) {
        return !TextUtils.isEmpty(ip);
    }

}

