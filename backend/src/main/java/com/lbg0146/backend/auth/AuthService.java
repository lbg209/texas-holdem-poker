package com.lbg0146.backend.auth;

import com.lbg0146.backend.exception.DuplicateUsernameException;
import com.lbg0146.backend.exception.InvalidCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 로그인 토큰은 서버 메모리에만 보관한다(서버 재시작 시 전원 로그아웃) — Room/게임 상태도 어차피
// 서버 재시작하면 다 날아가는 구조라 일관성 있고, JWT 서명 키 관리 같은 복잡도를 피할 수 있다.
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Map<String, Long> tokenToUserId = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(String username, String rawPassword, String nickname) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUsernameException("이미 사용 중인 아이디입니다: " + username);
        }
        User user = new User(username, passwordEncoder.encode(rawPassword), nickname);
        return userRepository.save(user);
    }

    public LoginResult login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username)
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
                .orElseThrow(() -> new InvalidCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다."));
        String token = UUID.randomUUID().toString();
        tokenToUserId.put(token, user.getId());
        return new LoginResult(user.getId(), user.getUsername(), user.getNickname(), token);
    }

    // 토큰으로 로그인 사용자를 조회한다. 토큰이 없거나(게스트) 유효하지 않으면 빈 값을 반환해
    // 호출부가 "로그인 안 된 사용자"로 자연스럽게 처리하게 한다.
    public Optional<User> resolveUser(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tokenToUserId.get(token)).flatMap(userRepository::findById);
    }
}
