package com.jaiselrahman.dfchatbot;

import android.Manifest;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.sip.SipSession;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.cloud.TransportOptions;
import com.jaiselrahman.dfchatbot.adapter.ChatsAdapter;
import com.jaiselrahman.dfchatbot.adapter.QuickRepliesAdapter;
import com.jaiselrahman.dfchatbot.model.Cards;
import com.jaiselrahman.dfchatbot.model.Message;
import com.jaiselrahman.dfchatbot.model.MessageType;
import com.jaiselrahman.dfchatbot.model.Quick;
import com.jaiselrahman.dfchatbot.model.Status;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.dialogflow.v2.DetectIntentResponse;
import com.google.cloud.dialogflow.v2.Intent;
import com.google.cloud.dialogflow.v2.QueryInput;
import com.google.cloud.dialogflow.v2.SessionName;
import com.google.cloud.dialogflow.v2.SessionsClient;
import com.google.cloud.dialogflow.v2.SessionsSettings;
import com.google.cloud.dialogflow.v2.TextInput;
import com.jaiselrahman.dfchatbot.stt.CloudSpeechService;
import com.jaiselrahman.dfchatbot.stt.VoiceRecorder;
import com.jaiselrahman.dfchatbot.stt.VoiceView;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.MotionEvent.TOOL_TYPE_ERASER;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Vector;


public class ChatBotActivity extends AppCompatActivity implements VoiceView.OnRecordListener {

    private static final String TAG = ChatBotActivity.class.getSimpleName();
    private static Vector<Message> chatMessages = new Vector<>();
    private static ChatsAdapter chatsAdapter;
    private static RecyclerView chatList;
    private static TextView messageText;//텍스트 입력창
    private static ImageView send;//보내기 버튼
    private static Message currentMessage;
    private static SessionsClient sessionsClient;
    private static SessionName sessionName;
    private UUID uuid = UUID.randomUUID();

    private static String checking="t";

    //보이스 관련 코드
    private static TextToSpeech assistant_voice=null;
    private ImageView voiceBtn;// 보이스 입력 버튼
    private String query;

    //sst코드
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1;
    private TextView mUserSpeechText, mSpeechRecogText;
    private static VoiceView mStartStopBtn;
    private CloudSpeechService mCloudSpeechService;
    private static VoiceRecorder mVoiceRecorder;
    private boolean mIsRecording = false;

    // Resource caches
    private int mColorHearing;
    private int mColorNotHearing;
    private TextView mStatus;
    private Handler mHandler;

    private String mSavedText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        if (getIntent().getAction() != null && getIntent().getAction().equals("com.google.android.gms.actions.SEARCH_ACTION")) {
            query = getIntent().getStringExtra(SearchManager.QUERY);
            Log.e("Query:",query);   //query is the search word
        }


        //여기는 json파일 연결하는 코드
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(getResources().openRawResource(R.raw.lee));
            String projectID = ((ServiceAccountCredentials) credentials).getProjectId();
            sessionsClient = SessionsClient.create(SessionsSettings.newBuilder().setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build());
            sessionName = SessionName.of(projectID, uuid.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

        sendWelcomeEvent();

        final FloatingActionButton fab = findViewById(R.id.move_to_down);//스크롤 올리면 맨 아래로 이동하는 버튼
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chatList.scrollToPosition(chatsAdapter.getItemCount() - 1);
            }
        });

        chatsAdapter = new ChatsAdapter(this, chatMessages);
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setStackFromEnd(true);

        chatList = findViewById(R.id.chat_list_view);
        chatList.setLayoutManager(linearLayoutManager);
        chatList.setAdapter(chatsAdapter);
        chatList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int position = linearLayoutManager.findLastCompletelyVisibleItemPosition();
                if (position != RecyclerView.NO_POSITION && position >= chatsAdapter.getItemCount() - 4) {
                    fab.hide();
                } else if (fab.getVisibility() != View.VISIBLE) {
                    fab.show();
                }
            }
        });

        messageText = findViewById(R.id.chat_edit_text1);
        send = findViewById(R.id.enter_chat1);
        send.setOnClickListener(this::sendMessageBtn);//메시지 보내기

        //보이스 입력 부분
        initvoice();// 마이크 초기화
        //voiceBtn=findViewById(R.id.voicebtn);
        //voiceBtn.setOnClickListener(this::voicesend);//보이스 입력

        android.content.Intent intent=getIntent();
        if(checking.equals(intent.getStringExtra("check")))
        {
            checking="f";
            messageText.setText("안녕");
            sendMessageBtn(send);
            Log.e("인텐트 값",intent.getStringExtra("check"));
        }

        //stt 코드
        initViews();
    }
    //stt
    private void initViews() {


        mStartStopBtn =findViewById(R.id.voicebtn);
        mStartStopBtn.setOnRecordListener(this);


        mSpeechRecogText = findViewById(R.id.chat_edit_text1);
        mStatus = findViewById(R.id.status);

        final Resources resources = getResources();
        final Resources.Theme theme = getTheme();
        mColorHearing = ResourcesCompat.getColor(resources, R.color.status_hearing, theme);
        mColorNotHearing = ResourcesCompat.getColor(resources, R.color.status_not_hearing, theme);

        mHandler = new Handler(Looper.getMainLooper());
    }
    private final CloudSpeechService.Listener mCloudSpeechServiceListener = new CloudSpeechService.Listener() {
        @Override
        public void onSpeechRecognized(final String text, final boolean isFinal) {//말을하면 text로 들어감
            if (isFinal) {
                mVoiceRecorder.dismiss();
            }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(isFinal) {
                                Toast.makeText(getApplicationContext(), "말씀 해주세요", Toast.LENGTH_SHORT).show();
                                messageText.setText(text);
                                sendMessageBtn(send);
                                Log.e("시간", messageText.getText().toString());
                        }
                    }
                });
            }
    };

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            mCloudSpeechService = CloudSpeechService.from(binder);
            mCloudSpeechService.addListener(mCloudSpeechServiceListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mCloudSpeechService = null;
            mStatus.setVisibility(View.GONE);
        }

    };

    private final VoiceRecorder.Callback mVoiceCallback = new VoiceRecorder.Callback()
    {

        @Override
        public void onVoiceStart() {
            showStatus(true);
            if (mCloudSpeechService != null) {
                mCloudSpeechService.startRecognizing(mVoiceRecorder.getSampleRate());
            }
        }

        @Override
        public void onVoice(final byte[] buffer, int size) {
            if (mCloudSpeechService != null) {
                mCloudSpeechService.recognize(buffer, size);
            }

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    int amplitude = (buffer[0] & 0xff) << 8 | buffer[1];
                    double amplitudeDb3 = 20 * Math.log10((double)Math.abs(amplitude) / 32768);
                    float radius2 = (float) Math.log10(Math.max(1, amplitudeDb3)) * dp2px(ChatBotActivity.this, 20);
                    Log.d("SUJIT","radius2 : " + radius2);
                    mStartStopBtn.animateRadius(radius2 * 10);
                }
            });
        }

        @Override
        public void onVoiceEnd() {
            showStatus(false);
            if (mCloudSpeechService != null) {
                mCloudSpeechService.finishRecognizing();
            }
        }

    };

    @Override
    public void onRecordStart() {
        startStopRecording();
        mStatus.setVisibility(View.VISIBLE);
    }

    @Override
    public void onRecordFinish() {
        startStopRecording();
        mStatus.setVisibility(View.GONE);
    }

    private void startStopRecording() {

        Log.d(TAG, "# startStopRecording # : " + mIsRecording);
        if (mIsRecording) {
            mStartStopBtn.changePlayButtonState(VoiceView.STATE_NORMAL);
            stopVoiceRecorder();
        } else {
            mStartStopBtn.changePlayButtonState(VoiceView.STATE_RECORDING);
            startVoiceRecorder();
        }
    }

    private void startVoiceRecorder() {
        Log.d(TAG, "# startVoiceRecorder #");
        mIsRecording = true;
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
        }
        mVoiceRecorder = new VoiceRecorder(mVoiceCallback);
        mVoiceRecorder.start();
    }

    private void stopVoiceRecorder() {

        Log.d(TAG, "# stopVoiceRecorder #");
        mIsRecording = false;
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
            mVoiceRecorder = null;
        }
    }

    @Override
    //채팅방 입장을 하면, 아래 메서드 실행되고, 음성이 허락이 돼 있으면 토스트 메시지 뜸
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (permissions.length == 1 && grantResults.length == 1
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,"음성 기능 사용 가능합니다",Toast.LENGTH_SHORT).show();
            } else {
                showPermissionMessageDialog();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void showPermissionMessageDialog() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage("This app needs to record audio and recognize your speech")
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                }).setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
            }
        }).create();

        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    private void showStatus(final boolean hearingVoice) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatus.setTextColor(hearingVoice ? mColorHearing : mColorNotHearing);
            }
        });
    }

    public static int dp2px(Context context, int dp) {
        int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context
                .getResources().getDisplayMetrics());
        return px;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Prepare Cloud Speech API
        bindService(new android.content.Intent(this, CloudSpeechService.class), mServiceConnection,
                BIND_AUTO_CREATE);

        // Start listening to voices
        /*
        앱을 시작하고, 음성 허락이 돼 있으면 바로 음성입력 시작
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecorder();
            }
            */
        //음성 허락 안되있으면 허락 요청 창 뜸
         if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.RECORD_AUDIO)) {
            showPermissionMessageDialog();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }

    @Override
    public void onStop() {

        // Stop listening to voice
        stopVoiceRecorder();

        // Stop Cloud Speech API
        if (mCloudSpeechService != null) {
            mCloudSpeechService.removeListener(mCloudSpeechServiceListener);
            unbindService(mServiceConnection);
            mCloudSpeechService = null;
        }

        super.onStop();
    }


    private void initvoice() {
        assistant_voice=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status!=TextToSpeech.ERROR)
                    assistant_voice.setLanguage(Locale.KOREA);
            }
        });
    }

    private void voicesend(View view) {
        //여기는 음성을 입력하고, 다 입력이 되면 아래 onActivityResult로 감
        android.content.Intent intent=new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,"말씀 해주세요");

        try{
            startActivityForResult(intent,2);
        }catch (ActivityNotFoundException e)
        {
            Toast.makeText(this,"다시 말씀 해주세요",Toast.LENGTH_LONG).show();
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        //바로 위 voicesend 메서드에서 받아옴
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==2 && resultCode==RESULT_OK)
        {
            ArrayList<String> result = data
                    .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            messageText.setText(Editable.Factory.getInstance().newEditable(result.get(0)));
            sendMessageBtn(send);

        }
    }

    //suggestion chip 답변을 가져옴
    public void getsuggestion(String String) {
        messageText.setText(String);
        sendMessageBtn(send);
        Log.e("가져온 스트링",messageText.getText().toString());
    }

    public void sendMessageBtn(View view){
        if (TextUtils.isEmpty(messageText.getText().toString().trim())) {
            Toast.makeText(this,"메시지를 입력해주세요",Toast.LENGTH_SHORT).show();
        }
        final Message message = new Message();
        message.setText(messageText.getText().toString());//텍스트 입력 창
        if(message.getText().equals(""))
        {
            Toast.makeText(this,"메시지를 입력해주세요",Toast.LENGTH_SHORT).show();
        }
        else {
            message.setStatus(Status.WAIT);
            message.setTimeStamp(new Date().getTime());
            message.setMessageType(MessageType.MINE);
            if(message.getText().equals("안녕")==false)// 처음 일때
                chatMessages.add(message);
            currentMessage = message;
            sendMessage(message.getText());
            chatsAdapter.notifyDataSetChanged();
            messageText.setText("");
            chatList.smoothScrollToPosition(chatsAdapter.getItemCount());
        }

    }

    void sendWelcomeEvent() {
        new RequestTask(this).execute();
    }

    private void sendMessage(String message) {
        new RequestTask(this).execute(message);
    }


    static class RequestTask extends AsyncTask<String, Void, DetectIntentResponse> {

        private WeakReference<ChatBotActivity> activity;
        private SessionsClient sessionsClient;

        RequestTask(ChatBotActivity activity) {
            this.activity = new WeakReference<>(activity);
            this.sessionsClient = activity.sessionsClient;
        }

        @Override
        protected DetectIntentResponse doInBackground(String... requests) {
            try {
                return sessionsClient.detectIntent(activity.get().sessionName,
                        QueryInput.newBuilder()
                                .setText(TextInput.newBuilder()
                                        .setText(requests[0])
                                        .setLanguageCode("ko")
                                        .build())
                                .build());
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        public WeakReference<ChatBotActivity> getActivity() {
            return activity;
        }

        public RequestTask() {
            super();
        }

        @Override
        //답변 메서드
        protected void onPostExecute(DetectIntentResponse response) {
            if (response != null) {//다이얼로그플로우에서 답변이 있으면
                if (activity.get().currentMessage != null) {
                    activity.get().currentMessage.setStatus(com.jaiselrahman.dfchatbot.model.Status.SENT);
                }
                ArrayList<Quick> quickrepliesses = null;
                Message QuickRepliesMessage = null;
                ArrayList<Cards> cards = null;
                Message cardMessage = null;

                List<Intent.Message> messages = response.getQueryResult().getFulfillmentMessagesList();
                for (Intent.Message m : messages) {

                    if (m.hasPayload()) {
                        boolean isEventsLists = m.getPayload().getFieldsMap().containsKey("EVENT_LISTS");
                        if (isEventsLists)
                            isEventsLists = m.getPayload().getFieldsMap().get("EVENT_LISTS").getBoolValue();
                        if (isEventsLists) {
                            new RequestTask(activity.get()).execute("technical events");
                            new RequestTask(activity.get()).execute("non technical events");
                            new RequestTask(activity.get()).execute("online events");
                            return;
                        }
                    } else if (m.hasCard()) {
                        super.onPostExecute(response);

                        if (cards == null) {

                            cards = new ArrayList<>();
                            cardMessage = new Message();
                            cardMessage.setTimeStamp(new Date().getTime());
                            cardMessage.setMessageType(MessageType.OTHER_CARDS);
                        }
                        Cards card = new Cards();
                        card.setTitle(m.getCard().getTitle());
                        card.setSubtitle(m.getCard().getSubtitle());
                        card.setImgUrl(m.getCard().getImageUri());
                        card.setButtons(m.getCard().getButtons(0));
                        cards.add(card);
                    } else if (m.hasText()) {//단순 텍스트 답변
                        Message msg = new Message();
                        msg.setTimeStamp(new Date().getTime());
                        msg.setMessageType(MessageType.OTHER);
                        msg.setText(m.getText().getText(0));
                        addMessage(msg);
                        //텍스트 메시지를 읽어줌
                        assistant_voice.speak(msg.getText(),TextToSpeech.QUEUE_FLUSH,null);
                    }else if (m.hasQuickReplies()){
                        super.onPostExecute(response);
                        if (quickrepliesses == null) {

                            quickrepliesses = new ArrayList<>();
                            QuickRepliesMessage = new Message();
                            QuickRepliesMessage.setMessageType(MessageType.QuickReplies);
                        }
                        Quick quickreplies = new Quick();
                        int count = m.getQuickReplies().getQuickRepliesCount();
                        Log.d("1234092183092183912",""+count);
                            quickreplies.setTitle1(m.getQuickReplies().getQuickReplies(0));
                            quickreplies.setTitle2(m.getQuickReplies().getQuickReplies(1));
                            quickreplies.setCount(2);
                        if(count==3 || count==4) {
                            quickreplies.setTitle3(m.getQuickReplies().getQuickReplies(2));
                            quickreplies.setCount(3);
                        }
                        if(count == 4){
                            quickreplies.setTitle4(m.getQuickReplies().getQuickReplies(3));
                            quickreplies.setCount(4);
                        }
                        quickrepliesses.add(quickreplies);

                    }
                }
                if (cardMessage != null) {
                    cardMessage.setCards(cards);
                    addMessage(cardMessage);
                }
                if(quickrepliesses !=null){

                    QuickRepliesMessage.setQuicks(quickrepliesses);

                    addMessage(QuickRepliesMessage);
                }
            } else {
                //답변이 없으면에러 인데 들어오면 바로 메시지를 보냈다고 실행이됨
                //Toast.makeText(activity.get(), "Oops! Something went wrong.\nPlease Check your Network.", Toast.LENGTH_SHORT).show();
            }
        }

        void addMessage(Message message) {
            activity.get().currentMessage.setStatus(com.jaiselrahman.dfchatbot.model.Status.SENT);
            activity.get().chatMessages.add(message);
            activity.get().chatsAdapter.notifyDataSetChanged();
            activity.get().chatList.smoothScrollToPosition(activity.get().chatsAdapter.getItemCount());
        }
    }

}
