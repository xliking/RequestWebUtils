package xlike.top.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Administrator
 */
@Controller
@RequestMapping("/oauth2")
public class OAuth2Controller {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2Controller.class);

    @Value("${linux-do.oauth2.client.registration.client-id}")
    private String clientId;

    @Value("${linux-do.oauth2.client.registration.client-secret}")
    private String clientSecret;

    @Value("${linux-do.oauth2.client.registration.redirect-uri}")
    private String redirectUri;

    @Value("${linux-do.oauth2.client.provider.authorization-uri}")
    private String authorizationEndpoint;

    @Value("${linux-do.oauth2.client.provider.token-uri}")
    private String tokenEndpoint;

    @Value("${linux-do.oauth2.client.provider.user-info-uri}")
    private String userEndpoint;

    @GetMapping("/initiate")
    public void initiateAuth(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String state = new BigInteger(130, new SecureRandom()).toString(32);
        request.getSession().setAttribute("oauth2State", state);
        String authUrl = String.format("%s?client_id=%s&response_type=code&redirect_uri=%s&scope=%s&state=%s",
                authorizationEndpoint, clientId, redirectUri, "read,write", state);
        response.sendRedirect(authUrl);
    }

    @GetMapping("/callback")
    public String handleAuthorizationCode(@RequestParam("code") String code, @RequestParam("state") String state, HttpServletRequest request,HttpServletResponse HttpResponse) {
        try {
            String sessionState = (String) request.getSession().getAttribute("oauth2State");
            logger.info("Received code: {}, state: {}, sessionState: {}", code, state, sessionState);
            if (sessionState == null || !sessionState.equals(state)) {
                logger.error("State mismatch: expected {}, got {}", sessionState, state);
                return "redirect:/error";
            }
            // 获取 access_token
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            String auth = clientId + ":" + clientSecret;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            headers.add("Authorization", "Basic " + encodedAuth);

            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("grant_type", "authorization_code");
            requestBody.add("code", code);
            requestBody.add("redirect_uri", redirectUri);

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response;
            try {
                response = restTemplate.postForEntity(tokenEndpoint, requestEntity, Map.class);
            } catch (Exception e) {
                logger.error("Failed to obtain access token: {}", e.getMessage());
                return "redirect:/error";
            }

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !responseBody.containsKey("access_token")) {
                logger.error("No access_token in response: {}", responseBody);
                return "redirect:/error";
            }

            // 获取用户信息
            HttpHeaders userHeaders = new HttpHeaders();
            userHeaders.setBearerAuth(responseBody.get("access_token").toString());
            HttpEntity<String> entity = new HttpEntity<>(userHeaders);
            ResponseEntity<Map> userResponse;
            try {
                userResponse = restTemplate.exchange(userEndpoint, HttpMethod.GET, entity, Map.class);
            } catch (Exception e) {
                return "redirect:/error";
            }

            Map<String, Object> userResBody = userResponse.getBody();
            if (userResBody == null) {
                return "redirect:/error";
            }

            String userData = userResBody.toString();
            Map<String, Object> userMap = parseToMap(userData);
            // 转为 JSON 字符串
            String jsonString = JSON.toJSONString(userMap);
            JSONObject jsonObject = JSONObject.parseObject(jsonString);
            Integer trustLevel = jsonObject.getInteger("trust_level");
            Integer id = jsonObject.getInteger("id");
            if (trustLevel >= 1) {
                Cookie cookie = new Cookie("userId", String.valueOf(id));
                cookie.setPath("/");
                cookie.setMaxAge(60 * 60 * 24);
                cookie.setHttpOnly(true);
                StpUtil.login(id);
                HttpResponse.addCookie(cookie);
                return "redirect:/index";
            } else {
                return "redirect:/error";
            }
        } catch (Exception e) {
            logger.error("Failed to handle authorization code: {}", e.getMessage());
            return "redirect:/error";
        }
    }

    private static Map<String, Object> parseToMap(String userData) {
        Map<String, Object> map = new HashMap<>();
        // 移除首尾大括号并按逗号分割
        String[] pairs = userData.substring(1, userData.length() - 1).split(",\\s*");

        for (String pair : pairs) {
            // 分割为 key 和 value
            String[] keyValue = pair.split("=", 2);
            String key = keyValue[0].trim();
            String value = keyValue[1].trim();

            // 根据值类型存储到 Map
            if ("true".equals(value)) {
                map.put(key, true);
            } else if ("false".equals(value)) {
                map.put(key, false);
            } else if ("null".equals(value)) {
                map.put(key, null);
                // 判断是否为数字
            } else if (value.matches("-?\\d+")) {
                map.put(key, Integer.parseInt(value));
            } else {
                // 字符串直接存储
                map.put(key, value);
            }
        }
        return map;
    }
}