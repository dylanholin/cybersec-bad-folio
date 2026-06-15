package com.devfolio.service;

import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blacklist de tokens JWT pour l'invalidation côté serveur (logout, compromission).
 * <p>
 * Stocke le hash du token jusqu'à son expiration naturelle, puis le nettoie.
 * Limitation : en mémoire uniquement, non distribué en cluster.
 * Pour la production : utiliser Redis avec TTL.
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);

    /**
     * Map : hash du token → timestamp d'expiration (ms).
     * Un token blacklisté dont l'expiration est dépassée est considéré comme naturellement invalidé.
     */
    private final ConcurrentHashMap<String, Long> blacklistedTokens = new ConcurrentHashMap<>();

    /**
     * Ajoute un token à la blacklist.
     *
     * @param token   le token JWT brut
     * @param claims  les claims du token (pour récupérer l'expiration)
     */
    public void blacklist(final String token, final Claims claims) {
        final String tokenHash = hashToken(token);
        final long expiration = claims.getExpiration().getTime();
        blacklistedTokens.put(tokenHash, expiration);
        log.info("Token blacklisté pour l'utilisateur {} jusqu'à {}", claims.getSubject(), expiration);
        cleanup();
    }

    /**
     * Vérifie si un token est blacklisté.
     *
     * @param token le token JWT brut
     * @return true si le token est dans la blacklist et non expiré
     */
    public boolean isBlacklisted(final String token) {
        final String tokenHash = hashToken(token);
        final Long expiration = blacklistedTokens.get(tokenHash);
        if (expiration == null) {
            return false;
        }
        // Si le token est expiré naturellement, le retirer de la blacklist
        if (System.currentTimeMillis() > expiration) {
            blacklistedTokens.remove(tokenHash);
            return false;
        }
        return true;
    }

    /**
     * Nettoie les entrées expirées de la blacklist.
     */
    private void cleanup() {
        final long now = System.currentTimeMillis();
        blacklistedTokens.entrySet().removeIf(entry -> now > entry.getValue());
    }

    /**
     * Hash le token en SHA-256 pour ne pas stocker le token brut en mémoire.
     * SHA-256 évite les collisions possibles avec hashCode().
     */
    private String hashToken(final String token) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(2 * hash.length);
            for (final byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (final java.security.NoSuchAlgorithmException e) {
            // SHA-256 est garanti disponible sur toute JVM
            throw new IllegalStateException("SHA-256 non disponible", e);
        }
    }
}
