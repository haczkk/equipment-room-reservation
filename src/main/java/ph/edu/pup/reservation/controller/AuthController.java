package ph.edu.pup.reservation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ph.edu.pup.reservation.dto.ChangePasswordDTO;
import ph.edu.pup.reservation.dto.LoginRequestDTO;
import ph.edu.pup.reservation.dto.PresidentRegisterDTO;
import ph.edu.pup.reservation.service.AuthService;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register-president")
    public ResponseEntity<?> registerPresident(@jakarta.validation.Valid @RequestBody PresidentRegisterDTO dto) {
        String message = authService.registerPresident(dto);
        return ResponseEntity.ok(Map.of("success", true, "message", message));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO dto) {
        Map<String, Object> user = authService.login(dto);
        return ResponseEntity.ok(Map.of("success", true, "user", user));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordDTO dto) {
        String message = authService.changePassword(dto);
        return ResponseEntity.ok(Map.of("success", true, "message", message));
    }
}