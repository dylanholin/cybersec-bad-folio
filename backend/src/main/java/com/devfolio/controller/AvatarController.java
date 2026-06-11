package com.devfolio.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URL;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class AvatarController {

    @PostMapping("/avatar")
    // 🔴 A10-01 : SSRF — le serveur fetch n'importe quelle URL
    // Exemples d'attaque :
    //   ?url=http://169.254.169.254/latest/meta-data/  (AWS metadata)
    //   ?url=http://localhost:8080/actuator/env         (services internes)
    //   ?url=file:///etc/passwd                         (lecture fichiers locaux)
    public ResponseEntity<?> setAvatarFromUrl(
            @RequestParam String url,
            @RequestHeader(value = "Authorization", required = false) String token) throws Exception {

        // Aucune validation du domaine, du protocole, ou de la plage d'IP
        URL avatarUrl = new URL(url);
        byte[] imageData = avatarUrl.openStream().readAllBytes();

        // Sauvegarde et retourne l'image fetchée
        String base64 = Base64.getEncoder().encodeToString(imageData);
        return ResponseEntity.ok(Map.of("avatar", "data:image/png;base64," + base64));
    }
}
