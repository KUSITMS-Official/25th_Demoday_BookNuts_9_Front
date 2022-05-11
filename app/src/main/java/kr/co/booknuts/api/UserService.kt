package kr.co.booknuts.api

import kr.co.booknuts.data.*
import retrofit2.Call
import retrofit2.http.*

interface UserService {
    // 로그인
    // request body - 이메일, 비밀번호
    @POST("/auth/login")
    fun doLogin(@Body loginInfo: LoginRequestDTO): Call<Token>

    // 회원가입
    // request body - 아이디, 비밀번호, 이름, 닉네임, 이메일
    @POST("/auth/join")
    fun createUser(@Body joinInfo : JoinRequestDTO): Call<User>

    // 책 검색
    // request body - 책 제목
    @GET("https://openapi.naver.com/v1/search/book.json")
    fun searchBook(@Body searchInfo: BookSearchRequestDTO): Call<BookSearchInfo>

    // 유저 닉네임 중복 체크
    // request body - 닉네임
    @GET("/auth/checkNickname/{nickname}")
    fun checkNickname(@Path("nickname") nickname: String): Call<String>

    // 유저 아이디 중복 체크
    // request body - 아이디
    @GET("/auth/checkLoginId/{loginId}")
    fun checkId(@Path("loginId") loginId: String): Call<String>
}