package com.devfolio.controller;

import com.devfolio.util.UrlValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class AvatarController {

    @PostMapping("/avatar")
    public ResponseEntity<?> setAvatarFromUrl(@RequestParam String url) {
        try {
            try (InputStream in = UrlValidator.fetchContent(url)) {
                byte[] imageData = in.readNBytes((int) UrlValidator.getMaxFetchSize());
                String base64 = Base64.getEncoder().encodeToString(imageData);
                return ResponseEntity.ok(Map.of("avatar", "data:image/png;base64," + base64));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Impossible de récupérer l'image"));
        }
    }
}
