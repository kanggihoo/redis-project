package com.example.wepay.service;

import com.example.wepay.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SessionDemoServiceTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Test
    @DisplayName("[Session] 세션에 값 저장 후 동일 세션으로 조회하면 값이 일치한다")
    void setAndGet_returnsSameValue() throws Exception {
        // 세션에 값 저장
        MvcTestResult setResult = mvc.post().uri("/api/session/username")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"Alice\"}")
                .exchange();

        assertThat(setResult).hasStatus(HttpStatus.OK);

        // 세션 쿠키 추출
        MockHttpServletResponse mockResponse = setResult.getResponse();
        Cookie sessionCookie = mockResponse.getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();

        // 동일 세션으로 조회
        assertThat(mvc.get().uri("/api/session/username")
                .cookie(sessionCookie))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.value")
                .isEqualTo("Alice");
    }

    @Test
    @DisplayName("[Session Redis] 세션 저장 후 Redis에 spring:session 키가 생성된다")
    void session_createsRedisKey() throws Exception {
        // 세션에 값 저장
        mvc.post().uri("/api/session/testkey")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"testvalue\"}")
                .exchange();

        // Redis에 spring:session 관련 키 확인
        var keys = stringRedisTemplate.keys("spring:session:*");
        assertThat(keys).isNotEmpty();
    }
}
