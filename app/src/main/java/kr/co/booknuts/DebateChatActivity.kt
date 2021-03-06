package kr.co.booknuts

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.ScrollView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.*
import kotlinx.coroutines.NonCancellable.isActive
import kr.co.booknuts.adapter.ChatAdapter
import kr.co.booknuts.data.Chat
import kr.co.booknuts.data.DebateJoinDTO
import kr.co.booknuts.data.UserInfo
import kr.co.booknuts.databinding.ActivityDebateChatBinding
import kr.co.booknuts.retrofit.RetrofitBuilder
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DebateChatActivity : AppCompatActivity() {

    val binding by lazy { ActivityDebateChatBinding.inflate(layoutInflater) }

    // 토론 상태 변수
    var isActive = false
    var count = 0

    // 어댑터 변수
    val adapter = ChatAdapter()

    // 파이어베이스 데이터베이스 인스턴스 연결
    private val firebaseDatabase: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val databaseReference: DatabaseReference = firebaseDatabase.getReference()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // 토론장 ID 받아오기
        val roomId = intent.getStringExtra("roomId")
        val opinion = intent.getBooleanExtra("opinion", false)
        val topic = intent.getStringExtra("topic")
        var title = intent.getStringExtra("title")
        isActive = intent.getBooleanExtra("active", true)
        Log.d("DEBATE_CHAT", "토론장 상태 : ${isActive}")

        val idx = roomId?.toInt()?.rem(2)
        when (idx) {
            0 -> binding.imgDebateCover.setImageResource(R.drawable.img_debate1)
            1 -> binding.imgDebateCover.setImageResource(R.drawable.img_debate2)
        }
        binding.imgDebateCover.clipToOutline = true
        binding.textTopic.text = topic
        binding.textToolbarTitle.text = "'${title}' 토론장"

        // background 적용 및 컴포넌트 앞으로 가져오기
        binding.imgDebateCover.clipToOutline = true
        binding.textTopic.bringToFront()

        // EditText 입력 중 외부 터치 시 키보드 숨기기
        binding.layout.setOnClickListener { hideKeyboard() }
        binding.toolbar.setOnClickListener { hideKeyboard() }
        binding.linearChat.setOnClickListener { hideKeyboard() }

        // X 버튼 클릭 시 액티비티 종료
        binding.btnExit.setOnClickListener { finish() }

        // 토론장 입장
        if (roomId != null) {
            openChat(roomId)
        }

        // 어댑터 연결
        binding.recyclerMsg.adapter = adapter
        binding.recyclerMsg.layoutManager = LinearLayoutManager(this)

        // 전송 버튼 클릭 시 메시지 저장
        binding.btnSend.setOnClickListener {
            Log.d("DEBATE_CHAT", "토론장 상태 : ${isActive}")
            hideKeyboard()
            // ★★★ 토론장 상태가 대기중이면 메시지 전송 불가능 !!!
            if (binding.editChat.text.isEmpty()) {
//                Toast.makeText(this, "메시지를 입력하세요.", Toast.LENGTH_SHORT).show()
            } else if (!isActive) {
                Toast.makeText(this, "토론장이 대기 중입니다.", Toast.LENGTH_SHORT).show()
            }
            else {
                var username = ""
                val state = opinion
                val message = binding.editChat.text.toString()
                // ★★★ 파이어베이스에서 토론장 상태 가져오기 !!!

                val pref = this.getSharedPreferences("authToken", AppCompatActivity.MODE_PRIVATE)
                val token = pref?.getString("Token", "")

                // 유저 정보 조회해서 닉네임 가져오기
                RetrofitBuilder.api.getUserInfo(token).enqueue(object : Callback<UserInfo> {
                    override fun onResponse(call: Call<UserInfo>, response: Response<UserInfo>) {
                        username = response.body()?.nickname.toString()

                        val chat = Chat(username, message, state)

                        // 파이어베이스에 메시지 저장
                        if (roomId != null) {
                            databaseReference.child(roomId).child("message").push().setValue(chat)
                        }

                        binding.editChat.setText("")
                    }
                    override fun onFailure(call: Call<UserInfo>, t: Throwable) { }
                })
            }
        }
    }
    fun openChat(roomId: String) {
        // 새로운 메시지가 전송되어 저장될 때마다 화면에 메시지 출력
        databaseReference.child(roomId).child("message").addChildEventListener(object: ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val chat = snapshot.getValue(Chat::class.java)
                if (chat != null) {
                    adapter.listData.add(chat)
                    binding.recyclerMsg.adapter = adapter
                    binding.recyclerMsg.layoutManager = LinearLayoutManager(this@DebateChatActivity)
                    binding.scrollChat.fullScroll(ScrollView.FOCUS_DOWN)
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) { }
            override fun onChildRemoved(snapshot: DataSnapshot) { }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) { }
            override fun onCancelled(error: DatabaseError) { }
        })

        // 토론장 인원이 증가할 때마다 토론장 상태 검사 : 대기 중 -> 진행 중
        databaseReference.child(roomId).child("user").addChildEventListener(object : ChildEventListener {
            // Add or Changed?
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val pref = this@DebateChatActivity.getSharedPreferences("authToken", AppCompatActivity.MODE_PRIVATE)
                val token = pref?.getString("Token", "")
                count++

                if (token != null && count > 1 && !isActive) {
                    RetrofitBuilder.debateApi.activate(token, roomId.toLong(), 1).enqueue(object: Callback<DebateJoinDTO> {
                        override fun onResponse(call: Call<DebateJoinDTO>, response: Response<DebateJoinDTO>) {
                            databaseReference.child(roomId.toString()).child("state").setValue(true)
                            isActive = true
                            Toast.makeText(this@DebateChatActivity, "토론이 시작되었습니다.", Toast.LENGTH_SHORT).show();
                        }

                        override fun onFailure(call: Call<DebateJoinDTO>, t: Throwable) {
                            Toast.makeText(this@DebateChatActivity, "진행 오류가 발생하였습니다.", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {  }
            override fun onChildRemoved(snapshot: DataSnapshot) {  }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {  }
            override fun onCancelled(error: DatabaseError) {  }
        })
    }

    // 키보드 비활성화 함수
    fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.editChat.windowToken, 0);
    }
}